package com.termux.app.api;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

public class VibrateAPI {

    private static final String LOG_TAG = "VibrateAPI";

    public static void onReceive(Context context, Intent intent) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        int milliseconds = intent.getIntExtra("duration_ms", 1000);
        boolean force = intent.getBooleanExtra("force", false);

        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        // Do not vibrate if "Silent" ringer mode or "Do Not Disturb" is enabled and -f/--force option is not used.
        if (am.getRingerMode() != AudioManager.RINGER_MODE_SILENT || force) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE),
                        new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .build());
                } else {
                    vibrator.vibrate(milliseconds);
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "Failed to run vibrator", e);
            }
        }

        ResultReturner.noteDone(intent);
    }

}
