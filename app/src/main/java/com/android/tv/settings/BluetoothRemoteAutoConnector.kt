package com.android.tv.settings

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

/**
 * 电信遥控器状态观察器。
 *
 * 自动配对/连接/扫描由系统内置 AutoBluetooth 负责；这里即使被启动，也只监听目标遥控器的
 * 发现、配对和连接广播，不再发起 startDiscovery/createBond/HID connect，避免和 AutoBluetooth 抢连接。
 */
@SuppressLint("MissingPermission")
object BluetoothRemoteAutoConnector {
    private const val TAG = "BtRemoteAutoConnect"
    private const val AUTO_CONNECT_REMOTE_NAME = "电信蓝牙遥控"

    private var started = false
    private var appContext: Context? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    // 解绑后蓝牙栈里没有名字记录（device.name 为 null），只有广播里的
                    // EXTRA_NAME 可用；不读它就认不出目标遥控器。
                    val extraName = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                    device?.let { maybeAutoHandle(it, extraName) }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                    if (device != null && isTargetRemote(device)) {
                        Log.d(TAG, "target remote bond state=$state address=${device.address}")
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null && isTargetRemote(device)) {
                        Log.d(TAG, "target remote acl disconnected address=${device.address}")
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null && isTargetRemote(device)) {
                        Log.d(TAG, "target remote acl connected address=${device.address}")
                    }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON) {
                        Log.d(TAG, "bluetooth state on, observe only")
                    }
                }
            }
        }
    }

    fun start(context: Context) {
        if (started) return
        val ctx = context.applicationContext
        appContext = ctx
        started = true

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        runCatching { ctx.registerReceiver(receiver, filter) }

        Log.d(TAG, "remote state observer started")
    }

    private fun isTargetRemote(device: BluetoothDevice, extraName: String? = null): Boolean {
        val name = runCatching { device.name }.getOrNull()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: extraName?.trim().orEmpty()
        return name.contains(AUTO_CONNECT_REMOTE_NAME)
    }

    /** 设置页“忽略此设备”名单。忽略后遥控器会自广播约 2 分钟（removeBond 副作用），
     * 无法与用户按键区分，因此本类对被忽略地址一律不自动配对/连接；解除忽略只由
     * 设置页（带自广播抑制窗口）或用户手动点击完成。 */
    private fun isIgnoredByUser(address: String): Boolean {
        val prefs = appContext?.getSharedPreferences(
            "ignored_bluetooth_devices", Context.MODE_PRIVATE
        ) ?: return false
        return prefs.getStringSet("ignored_addresses", emptySet()).orEmpty().contains(address)
    }

    private fun maybeAutoHandle(device: BluetoothDevice, extraName: String? = null) {
        if (!isTargetRemote(device, extraName)) return
        if (isIgnoredByUser(device.address)) return
        Log.d(TAG, "target remote found, state only address=${device.address} bond=${device.bondState}")
    }
}
