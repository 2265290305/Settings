package com.cloudsteem.autobluetooth;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // 启动 Service，让 Service 管理蓝牙
            context.startService(new Intent(context, AutoBluetoothService.class));
        }
    }
}