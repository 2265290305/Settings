package com.android.tv.settings

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

private const val AUTO_BLUETOOTH_TAG = "AutoBluetoothKeeper"
private const val AUTO_BLUETOOTH_PACKAGE = "com.cloudsteem.autobluetooth"
private const val AUTO_BLUETOOTH_SERVICE = "com.cloudsteem.autobluetooth.AutoBluetoothService"
private const val CHECK_INTERVAL_MS = 30_000L
private const val KEEPER_CHANNEL_ID = "autobluetooth_keeper"
private const val KEEPER_NOTIFICATION_ID = 2001

class AutoBluetoothBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                AutoBluetoothKeeperService.start(context)
            }
        }
    }
}

class AutoBluetoothKeeperService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val checkRunnable = object : Runnable {
        override fun run() {
            ensureAutoBluetoothRunning()
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
            startForeground(KEEPER_NOTIFICATION_ID, buildNotification())
        }
        handler.post(checkRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureAutoBluetoothRunning()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(checkRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureAutoBluetoothRunning() {
        if (!isPackageInstalled(AUTO_BLUETOOTH_PACKAGE)) {
            Log.w(AUTO_BLUETOOTH_TAG, "AutoBluetooth package not installed: $AUTO_BLUETOOTH_PACKAGE")
            return
        }
        if (isAutoBluetoothProcessOrServiceRunning()) return

        val serviceIntent = Intent().setComponent(
            ComponentName(AUTO_BLUETOOTH_PACKAGE, AUTO_BLUETOOTH_SERVICE)
        )
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.i(AUTO_BLUETOOTH_TAG, "started $AUTO_BLUETOOTH_SERVICE")
        }.onFailure {
            Log.e(AUTO_BLUETOOTH_TAG, "failed to start $AUTO_BLUETOOTH_SERVICE", it)
            startAutoBluetoothLauncherFallback()
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return runCatching {
            packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    private fun isAutoBluetoothProcessOrServiceRunning(): Boolean {
        val activityManager = getSystemService(ActivityManager::class.java) ?: return false
        val serviceRunning = runCatching {
            @Suppress("DEPRECATION")
            activityManager.getRunningServices(Int.MAX_VALUE).any {
                it.service.packageName == AUTO_BLUETOOTH_PACKAGE ||
                        it.service.className == AUTO_BLUETOOTH_SERVICE
            }
        }.getOrDefault(false)
        if (serviceRunning) return true

        return runCatching {
            activityManager.runningAppProcesses?.any {
                it.processName == AUTO_BLUETOOTH_PACKAGE
            } == true
        }.getOrDefault(false)
    }

    private fun startAutoBluetoothLauncherFallback() {
        val launchIntent = packageManager.getLaunchIntentForPackage(AUTO_BLUETOOTH_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launchIntent == null) {
            Log.w(AUTO_BLUETOOTH_TAG, "no launch intent for $AUTO_BLUETOOTH_PACKAGE")
            return
        }
        runCatching {
            startActivity(launchIntent)
            Log.i(AUTO_BLUETOOTH_TAG, "launched $AUTO_BLUETOOTH_PACKAGE fallback activity")
        }.onFailure {
            Log.e(AUTO_BLUETOOTH_TAG, "failed to launch $AUTO_BLUETOOTH_PACKAGE fallback", it)
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            KEEPER_CHANNEL_ID,
            "AutoBluetooth Keeper",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val builder = Notification.Builder(this)
            .setContentTitle("AutoBluetooth")
            .setContentText("保持蓝牙遥控自动连接服务运行")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(KEEPER_CHANNEL_ID)
        }
        return builder.build()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, AutoBluetoothKeeperService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure {
                Log.e(AUTO_BLUETOOTH_TAG, "failed to start keeper service", it)
            }
        }
    }
}
