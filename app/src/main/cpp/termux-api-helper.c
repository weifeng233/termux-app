/* termux-api helper for invoking the built-in Termux:API receiver. */
#define _POSIX_SOURCE
#define _GNU_SOURCE
#include <fcntl.h>
#include <pthread.h>
#include <signal.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/un.h>
#include <time.h>
#include <unistd.h>

#ifdef __ANDROID__
#include <android/api-level.h>
#endif

#define TERMUX_API_PACKAGE_VERSION "0.118.1-builtin-api"
#define TERMUX_API_RECEIVER "com.termux/.api.TermuxApiReceiver"

#ifndef PREFIX
# define PREFIX "/data/data/com.termux/files/usr"
#endif

#define LISTEN_SOCKET_ADDRESS "com.termux.api://listen"

static void exec_am_broadcast(int argc, char** argv, char* input_address_string, char* output_address_string);
static void exec_callback(int fd);
static void generate_uuid(char* str);
static void* transmit_stdin_to_socket(void* arg);
static int transmit_socket_to_stdout(int input_socket_fd);
static int run_api_command(int argc, char **argv);
static void contact_plugin(int argc, char** argv, char* input_address_string, char* output_address_string);

_Noreturn static void exec_am_broadcast(int argc, char** argv,
                                        char* input_address_string,
                                        char* output_address_string)
{
    close(STDOUT_FILENO);
    open("/dev/null", O_RDONLY);
    close(STDIN_FILENO);

    const int child_pre_argc = 14;
    const int child_post_argc = argc - 1;
    const int child_argc = child_pre_argc + child_post_argc;

    size_t child_argv_size = (sizeof(char*)) * (child_argc + 1);
    void* result = malloc(child_argv_size);
    if (result == NULL) {
        perror("malloc failed for am child args");
        exit(1);
    }

    char **child_argv = (char **) result;

    child_argv[0] = "am";
    child_argv[1] = "broadcast";
    child_argv[2] = "--user";
    child_argv[3] = "0";
    child_argv[4] = "-n";
    child_argv[5] = TERMUX_API_RECEIVER;
    child_argv[6] = "--es";
    child_argv[7] = "socket_input";
    child_argv[8] = output_address_string;
    child_argv[9] = "--es";
    child_argv[10] = "socket_output";
    child_argv[11] = input_address_string;
    child_argv[12] = "--es";
    child_argv[13] = "api_method";

    memcpy(child_argv + child_pre_argc, argv + 1, child_post_argc * sizeof(char*));
    child_argv[child_argc] = NULL;

    execv(PREFIX "/bin/am", child_argv);
    free(child_argv);
    perror("execv(\"" PREFIX "/bin/am\")");
    exit(1);
}

_Noreturn static void exec_callback(int fd)
{
    char *fds;
    if (asprintf(&fds, "%d", fd) == -1)
        perror("asprintf");

    char errmsg[256];
    char *export_to_env = getenv("TERMUX_EXPORT_FD");
    if (export_to_env && strncmp(export_to_env, "true", 4) == 0) {
        if (setenv("TERMUX_USB_FD", fds, true) == -1)
            perror("setenv");
        execl(PREFIX "/libexec/termux-callback", "termux-callback", NULL);
        sprintf(errmsg, "execl(\"" PREFIX "/libexec/termux-callback\")");
    } else {
        execl(PREFIX "/libexec/termux-callback", "termux-callback", fds, NULL);
        sprintf(errmsg, "execl(\"" PREFIX "/libexec/termux-callback\", %s)", fds);
    }
    perror(errmsg);
    exit(1);
}

static void generate_uuid(char* str) {
    sprintf(str, "%x%x-%x-%x-%x-%x%x%x",
            arc4random(), arc4random(),
            (uint32_t) getpid(),
            ((arc4random() & 0x0fff) | 0x4000),
            arc4random() % 0x3fff + 0x8000,
            arc4random(), arc4random(), arc4random());
}

static void* transmit_stdin_to_socket(void* arg) {
    int output_server_socket = *((int*) arg);
    struct sockaddr_un remote_addr;
    socklen_t addrlen = sizeof(remote_addr);
    int output_client_socket = accept(output_server_socket,
                                      (struct sockaddr*) &remote_addr,
                                      &addrlen);

    ssize_t len;
    char buffer[1024];
    while ((len = read(STDIN_FILENO, &buffer, sizeof(buffer))) > 0) {
        if (write(output_client_socket, buffer, len) < 0) break;
    }
    close(output_client_socket);
    return NULL;
}

static int transmit_socket_to_stdout(int input_socket_fd) {
    ssize_t len;
    char buffer[1024];
    char cbuf[256];
    struct iovec io = { .iov_base = buffer, .iov_len = sizeof(buffer) };
    struct msghdr msg = { 0 };
    int fd = -1;
    msg.msg_iov = &io;
    msg.msg_iovlen = 1;
    msg.msg_control = cbuf;
    msg.msg_controllen = sizeof(cbuf);
    while ((len = recvmsg(input_socket_fd, &msg, 0)) > 0) {
        struct cmsghdr * cmsg = CMSG_FIRSTHDR(&msg);
        if (cmsg && cmsg->cmsg_len == CMSG_LEN(sizeof(int))) {
            if (cmsg->cmsg_type == SCM_RIGHTS) {
                fd = *((int *) CMSG_DATA(cmsg));
            }
        }
        if (fd != -1 && len == 1 && buffer[0] == '@') { len = 0; }
        write(STDOUT_FILENO, buffer, len);
        msg.msg_controllen = sizeof(cbuf);
    }
    if (len < 0) perror("recvmsg()");
    return fd;
}

static void contact_plugin(int argc, char** argv,
                           char* input_address_string,
                           char* output_address_string)
{
    close(STDOUT_FILENO);
    open("/dev/null", O_RDONLY);
    close(STDIN_FILENO);

    struct sigaction sigpipe_action = {
        .sa_handler = SIG_IGN,
        .sa_flags = 0
    };
    sigaction(SIGPIPE, &sigpipe_action, NULL);

    int listenfd = -1;
#ifdef __ANDROID__
    if (android_get_device_api_level() < 34) {
        listenfd = socket(AF_UNIX, SOCK_STREAM|SOCK_CLOEXEC, 0);
    }
#endif
    if (listenfd != -1) {
        struct sockaddr_un listen_addr = { .sun_family = AF_UNIX };
        memcpy(listen_addr.sun_path+1, LISTEN_SOCKET_ADDRESS, strlen(LISTEN_SOCKET_ADDRESS));
        if (connect(listenfd, (struct sockaddr*) &listen_addr, sizeof(sa_family_t) + strlen(LISTEN_SOCKET_ADDRESS) + 1) == 0) {
            close(listenfd);
        }
    }

    exec_am_broadcast(argc, argv, input_address_string, output_address_string);
}

static int run_api_command(int argc, char **argv) {
    if (argc == 2 && strcmp(argv[1], "--version") == 0) {
        fprintf(stdout, "%s\n", TERMUX_API_PACKAGE_VERSION);
        fflush(stdout);
        exit(0);
    }
    if (argc < 2) {
        fprintf(stderr, "Usage: termux-api API_METHOD [flags]\n");
        return -1;
    }

    struct sigaction sigchld_action = {
        .sa_handler = SIG_DFL,
        .sa_flags = SA_RESTART | SA_NOCLDSTOP | SA_NOCLDWAIT
    };
    sigaction(SIGCHLD, &sigchld_action, NULL);

    char input_addr_str[100];
    char output_addr_str[100];

    generate_uuid(input_addr_str);
    generate_uuid(output_addr_str);

    struct sockaddr_un input_addr = { .sun_family = AF_UNIX };
    struct sockaddr_un output_addr = { .sun_family = AF_UNIX };
    strncpy(&input_addr.sun_path[1], input_addr_str, strlen(input_addr_str));
    strncpy(&output_addr.sun_path[1], output_addr_str, strlen(output_addr_str));

    int input_server_socket = socket(AF_UNIX, SOCK_STREAM|SOCK_CLOEXEC, 0);
    if (input_server_socket == -1) {
        perror("socket()");
        return -1;
    }
    int output_server_socket = socket(AF_UNIX, SOCK_STREAM|SOCK_CLOEXEC, 0);
    if (output_server_socket == -1) {
        perror("socket()");
        return -1;
    }

    int ret;
    ret = bind(input_server_socket, (struct sockaddr*) &input_addr,
               sizeof(sa_family_t) + strlen(input_addr_str) + 1);
    if (ret == -1) {
        perror("bind(input)");
        return ret;
    }

    ret = bind(output_server_socket, (struct sockaddr*) &output_addr,
               sizeof(sa_family_t) + strlen(output_addr_str) + 1);
    if (ret == -1) {
        perror("bind(output)");
        return ret;
    }

    if (listen(input_server_socket, 1) == -1) {
        perror("listen()");
        return -1;
    }

    if (listen(output_server_socket, 1) == -1) {
        perror("listen()");
        return -1;
    }

    pid_t fork_result = fork();
    if (fork_result == -1) {
        perror("fork()");
        return -1;
    } else if (fork_result == 0) {
        contact_plugin(argc, argv, input_addr_str, output_addr_str);
    }

    struct sockaddr_un remote_addr;
    socklen_t addrlen = sizeof(remote_addr);
    int input_client_socket = accept(input_server_socket,
                                     (struct sockaddr*) &remote_addr,
                                     &addrlen);

    pthread_t transmit_thread;
    pthread_create(&transmit_thread, NULL, transmit_stdin_to_socket,
                   &output_server_socket);

    int fd = transmit_socket_to_stdout(input_client_socket);
    close(input_client_socket);
    return fd;
}

int main(int argc, char** argv) {
    int fd = run_api_command(argc, argv);
    if (fd != -1) exec_callback(fd);
    return 0;
}
