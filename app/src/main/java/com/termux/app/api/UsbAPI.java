package com.termux.app.api;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Looper;
import android.util.JsonWriter;
import android.util.SparseArray;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;

public class UsbAPI {

    private static final SparseArray<UsbDeviceConnection> openDevices = new SparseArray<>();

    public static void onReceive(final Context context, final Intent intent) {
        UsbDevice device;
        String action = intent.getAction();
        if (action == null) {
            ResultReturner.returnData(intent, out -> out.append("Missing action\n"));
        } else {
            switch (action) {
                case "list":
                    ResultReturner.returnData(intent, new ResultReturner.ResultJsonWriter() {
                        @Override
                        public void writeJson(JsonWriter out) throws Exception {
                            listDevices(context, out);
                        }
                    });
                    break;
                case "permission":
                    device = getDevice(context, intent);
                    if (device == null) return;
                    ResultReturner.returnData(intent, out -> {
                        boolean result = getPermission(device, context, intent);
                        out.append(result ? "yes\n" : "no\n");
                    });
                    break;
                case "open":
                    device = getDevice(context, intent);
                    if (device == null) return;
                    ResultReturner.returnData(intent, new ResultReturner.WithAncillaryFd() {
                                @Override
                                public void writeResult(PrintWriter out) {
                                    if (getPermission(device, context, intent)) {
                                        int result = open(device, context);
                                        if (result < 0) {
                                            out.append("Failed to open device\n");
                                        } else {
                                            this.sendFd(out, result);
                                        }
                                    } else out.append("No permission\n");
                                }
                            });

                    break;
                default:
                    ResultReturner.returnData(intent, out -> out.append("Invalid action\n"));
            }
        }

    }

    private static void listDevices(final Context context, JsonWriter out) throws IOException {
        final UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        Iterator<String> deviceIterator = deviceList.keySet().iterator();
        out.beginArray();
        while (deviceIterator.hasNext()) {
            out.value(deviceIterator.next());
        }
        out.endArray();
    }

    private static UsbDevice getDevice(final Context context, final Intent intent) {
        String deviceName = intent.getStringExtra("device");
        final UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        UsbDevice device = null;
        if (deviceName != null) {
            device = deviceList.get(deviceName);
        } else {
            int vendorId = getUsbIdExtra(intent, "vendorId");
            int productId = getUsbIdExtra(intent, "productId");
            if (vendorId >= 0 && productId >= 0) {
                for (UsbDevice candidate : deviceList.values()) {
                    if (candidate.getVendorId() == vendorId && candidate.getProductId() == productId) {
                        device = candidate;
                        break;
                    }
                }
            }
        }
        if (device == null) {
            ResultReturner.returnData(intent, out -> out.append("No such device\n"));
        }
        return device;
    }

    private static int getUsbIdExtra(final Intent intent, final String extraName) {
        String value = intent.getStringExtra(extraName);
        if (value == null || value.isEmpty()) return -1;
        try {
            return Integer.decode(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean hasPermission(final @NonNull UsbDevice device, final Context context) {
        final UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        return usbManager.hasPermission(device);
    }

    private static boolean requestPermission(final @NonNull UsbDevice device, final Context context) {
        Looper.prepare();
        Looper looper = Looper.myLooper();
        final boolean[] result = new boolean[1];

        final String ACTION_USB_PERMISSION = "com.termux.api.USB_PERMISSION";
        final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context usbContext, final Intent usbIntent) {
                String action = usbIntent.getAction();
                if (ACTION_USB_PERMISSION.equals(action)) {
                    synchronized (this) {
                        UsbDevice device = usbIntent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                        if (usbIntent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            if (device != null) {
                                result[0] = true;
                                if (looper != null) looper.quit();
                            }
                        } else {
                            result[0] = false;
                            if (looper != null) looper.quit();
                        }
                    }

                }
            }
        };

        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        Intent usbPermissionIntent = new Intent(ACTION_USB_PERMISSION).setPackage(context.getPackageName());
        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntentFlags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent permissionIntent = PendingIntent.getBroadcast(context, 0, usbPermissionIntent, pendingIntentFlags);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        boolean receiverRegistered = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getApplicationContext().registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED);
            receiverRegistered = true;
        } else {
            context.getApplicationContext().registerReceiver(usbReceiver, filter);
            receiverRegistered = true;
        }
        try {
            usbManager.requestPermission(device, permissionIntent);
            Looper.loop();
        } finally {
            if (receiverRegistered) {
                context.getApplicationContext().unregisterReceiver(usbReceiver);
            }
        }
        return result[0];
    }

    private static boolean getPermission(final @NonNull UsbDevice device, final Context context, final Intent intent) {
        boolean request = intent.getBooleanExtra("request", false);
        if (request) {
            return requestPermission(device, context);
        } else {
            return hasPermission(device, context);
        }
    }

    private static int open(final @NonNull UsbDevice device, final Context context) {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            return -2;
        }
        int fd = connection.getFileDescriptor();
        if (fd == -1) {
            connection.close();
            return -1;
        }
        openDevices.put(fd, connection);
        return fd;
    }

}
