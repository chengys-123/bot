package com.example.myapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class CommonReceiverService extends BroadcastReceiver {

    private static final String TAG = "access";

    public void onReceive(Context paramContext, Intent paramIntent) {
        Log.i(TAG, "Service Stopped, restarting..");
        paramContext.startService(new Intent(paramContext, KasikornService.class));

    }
}