package com.termux.app.api;

import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.JsonWriter;
import android.util.Size;
import android.util.SizeF;

public class CameraInfoAPI {

    public static void onReceive(final Context context, Intent intent) {
        ResultReturner.returnData(intent, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

                out.beginArray();
                for (String cameraId : manager.getCameraIdList()) {
                    out.beginObject();
                    out.name("id").value(cameraId);

                    CameraCharacteristics camera = manager.getCameraCharacteristics(cameraId);

                    out.name("facing");
                    Integer lensFacing = camera.get(CameraCharacteristics.LENS_FACING);
                    out.value(getLensFacingString(lensFacing));

                    out.name("jpeg_output_sizes").beginArray();
                    StreamConfigurationMap map = camera.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if (map != null) {
                        for (Size size : map.getOutputSizes(ImageFormat.JPEG)) {
                            out.beginObject().name("width").value(size.getWidth()).name("height").value(size.getHeight()).endObject();
                        }
                    }
                    out.endArray();

                    out.name("focal_lengths").beginArray();
                    float[] lengths = camera.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    if (lengths != null) {
                        for (float f : lengths) {
                            out.value(f);
                        }
                    }
                    out.endArray();

                    out.name("auto_exposure_modes").beginArray();
                    int[] flashModeValues = camera.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES);
                    if (flashModeValues != null) {
                        for (int flashMode : flashModeValues) {
                            out.value(getAutoExposureModeString(flashMode));
                        }
                    }
                    out.endArray();

                    SizeF physicalSize = camera.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
                    if (physicalSize != null) {
                        out.name("physical_size").beginObject().name("width").value(physicalSize.getWidth()).name("height")
                            .value(physicalSize.getHeight()).endObject();
                    }

                    out.name("capabilities").beginArray();
                    int[] capabilities = camera.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                    if (capabilities != null) {
                        for (int capability : capabilities) {
                            out.value(getCapabilityString(capability));
                        }
                    }
                    out.endArray();

                    out.endObject();
                }
                out.endArray();
            }
        });
    }

    private static String getLensFacingString(Integer lensFacing) {
        if (lensFacing == null) return "null";
        switch (lensFacing) {
            case CameraMetadata.LENS_FACING_FRONT:
                return "front";
            case CameraMetadata.LENS_FACING_BACK:
                return "back";
            default:
                return lensFacing.toString();
        }
    }

    private static String getAutoExposureModeString(int flashMode) {
        switch (flashMode) {
            case CameraMetadata.CONTROL_AE_MODE_OFF:
                return "CONTROL_AE_MODE_OFF";
            case CameraMetadata.CONTROL_AE_MODE_ON:
                return "CONTROL_AE_MODE_ON";
            case CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH:
                return "CONTROL_AE_MODE_ON_ALWAYS_FLASH";
            case CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH:
                return "CONTROL_AE_MODE_ON_AUTO_FLASH";
            case CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE:
                return "CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE";
            case CameraMetadata.CONTROL_AE_MODE_ON_EXTERNAL_FLASH:
                return "CONTROL_AE_MODE_ON_EXTERNAL_FLASH";
            case CameraMetadata.CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY:
                return "CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY";
            default:
                return Integer.toString(flashMode);
        }
    }

    private static String getCapabilityString(int capability) {
        switch (capability) {
            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE:
                return "backward_compatible";
            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR:
                return "manual_sensor";
            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING:
                return "manual_post_processing";
            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW:
                return "raw";
            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING:
                return "private_reprocessing";
            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS:
                return "read_sensor_settings";
            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE:
                return "burst_capture";
            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING:
                return "yuv_reprocessing";
            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT:
                return "depth_output";
            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO:
                return "constrained_high_speed_video";
            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING:
                return "motion_tracking";
            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA:
                return "logical_multi_camera";
            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME:
                return "monochrome";
            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA:
                return "secure_image_data";
            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA:
                return "system_camera";
            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_OFFLINE_PROCESSING:
                return "offline_processing";
            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR:
                return "ultra_high_resolution_sensor";
            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_REMOSAIC_REPROCESSING:
                return "remosaic_reprocessing";
            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT:
                return "dynamic_range_ten_bit";
            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_STREAM_USE_CASE:
                return "stream_use_case";
            case CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_COLOR_SPACE_PROFILES:
                return "color_space_profiles";
            default:
                return Integer.toString(capability);
        }
    }

}
