package com.cloudsteem.autobluetooth;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "AutoBluetoothBootReceiver";
    public static final String ACTION_RESTART =
            "com.android.speaker.settings.action.RESTART_AUTOBLUETOOTH";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || ACTION_RESTART.equals(action)) {
            Log.i(TAG, action + " received, start AutoBluetoothService");
            // 启动前台 Service，让 Service 管理蓝牙
            Intent serviceIntent = new Intent(context, AutoBluetoothService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}
