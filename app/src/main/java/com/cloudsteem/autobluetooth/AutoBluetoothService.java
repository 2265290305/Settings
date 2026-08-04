package com.cloudsteem.autobluetooth;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import java.util.List;

public class AutoBluetoothService extends Service {
    private static final String TAG = "AutoBluetoothService";
    private static final String CHANNEL_ID = "autobluetooth_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int RESTART_REQUEST_CODE = 1002;
    private AutoBlueToothManager mAutoBlueToothManager;

    public AutoBluetoothService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "##### AutoBluetoothService onCreate #####");
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        mAutoBlueToothManager = new AutoBlueToothManager(this);
        mAutoBlueToothManager.setDeviceCallback(new AutoBlueToothManager.DeviceCallback() {
            @Override
            public void onScanResult(BluetoothDevice device, int rssi) {
                publishStatus(device, false, false);
            }

            @Override
            public void onDeviceConnecting(BluetoothDevice device) {
                publishStatus(device, false, true);
            }

            @Override
            public void onDeviceConnected(BluetoothDevice device) {
                publishStatus(device, true, false);
            }

            @Override
            public void onDeviceDisconnected(BluetoothDevice device) {
                publishStatus(device, false, false);
            }

            @Override
            public void onBondRemoved(BluetoothDevice device) {
                publishStatus(device, false, false);
            }

            @Override
            public void onScanStarted() {
            }

            @Override
            public void onScanFinished() {
                publishCurrentStatus();
            }

            @Override
            public void onServiceReady() {
                publishCurrentStatus();
            }
        });
        mAutoBlueToothManager.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "##### AutoBluetoothService onStartCommand ##### ");
        if (mAutoBlueToothManager != null) {
            handleCommand(intent);
            // 如果服务已被重建但 manager 还在，确保继续扫描
            mAutoBlueToothManager.start();
            publishCurrentStatus();
        }
        return START_STICKY; // 确保 Service 被系统杀死后自动重启
    }

    private void handleCommand(Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        String address = intent.getStringExtra(AutoBluetoothStatusContract.EXTRA_ADDRESS);
        if (AutoBluetoothStatusContract.ACTION_DISCONNECT_REMOTE.equals(action)) {
            Log.i(TAG, "disconnect remote command address=" + address);
            mAutoBlueToothManager.disconnectRemote(address);
        } else if (AutoBluetoothStatusContract.ACTION_FORGET_REMOTE.equals(action)) {
            Log.i(TAG, "forget remote command address=" + address);
            mAutoBlueToothManager.forgetRemote(address);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "##### AutoBluetoothService onDestroy #####");
        if (mAutoBlueToothManager != null) {
            mAutoBlueToothManager.release();
            mAutoBlueToothManager = null;
        }
        scheduleRestart(this);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "##### AutoBluetoothService onTaskRemoved #####");
        scheduleRestart(this);
    }

    static void scheduleRestart(Context context) {
        Intent intent = new Intent(context, BootReceiver.class)
                .setAction(BootReceiver.ACTION_RESTART)
                .setPackage(context.getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                RESTART_REQUEST_CODE,
                intent,
                flags);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            long triggerAt = SystemClock.elapsedRealtime() + 3000L;
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent);
            Log.i(TAG, "AutoBluetoothService restart scheduled");
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "AutoBluetooth Service",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("保持蓝牙自动连接服务在后台运行");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent notificationIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (notificationIntent == null) {
            notificationIntent = new Intent();
        }
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, flags);

        Notification.Builder builder = new Notification.Builder(this)
                .setContentTitle("AutoBluetooth")
                .setContentText("蓝牙自动连接服务运行中...")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(pendingIntent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID);
        }
        return builder.build();
    }

    private void publishCurrentStatus() {
        if (mAutoBlueToothManager == null) {
            publishStatus(null, false, false);
            return;
        }
        List<BluetoothDevice> connectedDevices = mAutoBlueToothManager.getConnectedDevices();
        if (!connectedDevices.isEmpty()) {
            publishStatus(connectedDevices.get(0), true, false);
            return;
        }
        List<BluetoothDevice> bondedDevices = mAutoBlueToothManager.getBondedRCDevices();
        if (!bondedDevices.isEmpty()) {
            publishStatus(bondedDevices.get(0), false, false);
            return;
        }
        publishStatus(null, false, false);
    }

    private void publishStatus(BluetoothDevice device, boolean connected, boolean connecting) {
        Intent intent = new Intent(AutoBluetoothStatusContract.ACTION_STATUS_CHANGED)
                .setPackage("com.android.speaker.settings")
                .putExtra(AutoBluetoothStatusContract.EXTRA_CONNECTED, connected)
                .putExtra(AutoBluetoothStatusContract.EXTRA_CONNECTING, connecting);
        if (device != null) {
            intent.putExtra(AutoBluetoothStatusContract.EXTRA_ADDRESS, device.getAddress());
            intent.putExtra(AutoBluetoothStatusContract.EXTRA_NAME, device.getName());
            intent.putExtra(AutoBluetoothStatusContract.EXTRA_BOND_STATE, device.getBondState());
        } else {
            intent.putExtra(AutoBluetoothStatusContract.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
        }
        sendBroadcast(intent, AutoBluetoothStatusContract.PERMISSION_STATUS);
        Log.d(TAG, "publish status connected=" + connected
                + " connecting=" + connecting
                + " address=" + (device != null ? device.getAddress() : "null")
                + " name=" + (device != null ? device.getName() : "null"));
    }
}
