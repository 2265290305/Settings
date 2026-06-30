package com.android.tv.settings

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 进程级常驻的蓝牙遥控器自动配对/回连器。
 *
 * 解决三个问题（需求“设置”章节）：
 * 1. 第一次开机不打开设置无法配对蓝牙遥控器 —— 之前自动配对逻辑只在 BlueToothScreen 进入时才注册；
 *    本类挂在 Application.onCreate（persistent 系统 app 开机即拉进程），不依赖打开设置页。
 * 2. 蓝牙遥控器第一次连接无法使用 / 3. 多次连接才能正常 —— 配对成功后用多次重试发起连接，
 *    并对已配对的目标遥控器持续回连，避免 HID 尚未就绪导致的“连上却不可用”。
 *
 * 目标设备：名称包含 [AUTO_CONNECT_REMOTE_NAME] 的遥控器。
 */
@SuppressLint("MissingPermission")
object BluetoothRemoteAutoConnector {
    private const val TAG = "BtRemoteAutoConnect"
    private const val AUTO_CONNECT_REMOTE_NAME = "电信蓝牙遥控"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var started = false
    private var appContext: Context? = null
    private var adapter: BluetoothAdapter? = null

    // 本进程已尝试过自动配对的地址，避免反复 createBond。
    private val autoPairTried = mutableSetOf<String>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device?.let { maybeAutoHandle(it) }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                    if (state == BluetoothDevice.BOND_BONDED && device != null && isTargetRemote(device)) {
                        // 配对成功：多次重试连接，规避 HID 未就绪导致的首次连接不可用。
                        connectWithRetry(device)
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    // 目标遥控器意外断开时再尝试回连。
                    if (device != null && isTargetRemote(device) &&
                        device.bondState == BluetoothDevice.BOND_BONDED
                    ) {
                        connectWithRetry(device, attempts = 2, delayMs = 2_000L)
                    }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON) {
                        // 蓝牙打开后：回连已配对遥控器并重新扫描。
                        reconnectBondedRemotes()
                        startScan()
                    }
                }
            }
        }
    }

    fun start(context: Context) {
        if (started) return
        val ctx = context.applicationContext
        val manager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
        adapter = manager.adapter ?: return
        appContext = ctx
        started = true

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        runCatching { ctx.registerReceiver(receiver, filter) }

        // 开机/启动即生效：先回连已配对的目标遥控器，再扫描以便配对新遥控器。
        if (adapter?.isEnabled == true) {
            reconnectBondedRemotes()
            startScan()
        }
        Log.d(TAG, "auto connector started, bt enabled=${adapter?.isEnabled}")
    }

    private fun isTargetRemote(device: BluetoothDevice): Boolean {
        val name = runCatching { device.name }.getOrNull()?.trim().orEmpty()
        return name.contains(AUTO_CONNECT_REMOTE_NAME)
    }

    private fun startScan() {
        val a = adapter ?: return
        runCatching {
            if (a.isDiscovering) a.cancelDiscovery()
            a.startDiscovery()
        }
        // 限时扫描，避免长期占用射频。
        mainHandler.postDelayed({ runCatching { adapter?.cancelDiscovery() } }, 12_000L)
    }

    private fun reconnectBondedRemotes() {
        val a = adapter ?: return
        runCatching { a.bondedDevices }.getOrNull()
            ?.filter { isTargetRemote(it) }
            ?.forEach { connectWithRetry(it, attempts = 2, delayMs = 1_500L) }
    }

    private fun maybeAutoHandle(device: BluetoothDevice) {
        if (!isTargetRemote(device)) return
        when (device.bondState) {
            BluetoothDevice.BOND_BONDED -> connectWithRetry(device, attempts = 2, delayMs = 1_500L)
            BluetoothDevice.BOND_NONE -> {
                if (!autoPairTried.add(device.address)) return
                runCatching { adapter?.cancelDiscovery() }
                runCatching { device.createBond() }
            }
            else -> { /* BOND_BONDING：配对进行中 */ }
        }
    }

    /** 连接到设备：通过 HID_HOST profile 反射 connect。失败则按 attempts 重试。 */
    private fun connectWithRetry(device: BluetoothDevice, attempts: Int = 4, delayMs: Long = 1_800L) {
        if (attempts <= 0) return
        connectViaHidHost(device)
        mainHandler.postDelayed({
            // 仍未连接则继续重试。
            if (!isConnected(device)) {
                connectWithRetry(device, attempts - 1, delayMs)
            }
        }, delayMs)
    }

    private fun connectViaHidHost(device: BluetoothDevice) {
        val a = adapter ?: return
        // HID_HOST = 4（部分平台 BluetoothProfile.HID_HOST 为 @hide，用常量值）。
        val hidHostProfile = 4
        runCatching {
            a.getProfileProxy(appContext, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    runCatching {
                        val m = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                        m.isAccessible = true
                        m.invoke(proxy, device)
                    }
                    runCatching { a.closeProfileProxy(profile, proxy) }
                }

                override fun onServiceDisconnected(profile: Int) {}
            }, hidHostProfile)
        }
    }

    private fun isConnected(device: BluetoothDevice): Boolean {
        return runCatching {
            val m = BluetoothDevice::class.java.getMethod("isConnected")
            m.isAccessible = true
            m.invoke(device) as? Boolean ?: false
        }.getOrDefault(false)
    }
}
