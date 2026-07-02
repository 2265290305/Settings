package com.cloudsteem.autobluetooth;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class AutoBluetoothService extends Service {
    private static final String TAG = "AutoBluetoothService";
    private AutoBlueToothManager mAutoBlueToothManager;
    public AutoBluetoothService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
//        throw new UnsupportedOperationException("Not yet implemented");
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "##### AutoBluetoothService start #####");
        mAutoBlueToothManager = new AutoBlueToothManager(this);
        mAutoBlueToothManager.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "##### AutoBluetoothService onStartCommand ##### ");
        return START_STICKY; // 确保 Service 被系统杀死后自动重启
    }

}