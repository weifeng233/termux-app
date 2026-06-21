package com.termux.api;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.termux.app.TermuxService;

public class TermuxApiReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        Intent serviceIntent = new Intent(context, TermuxService.class)
            .setAction(intent.getAction())
            .putExtras(intent);
        context.startService(serviceIntent);
    }
}
