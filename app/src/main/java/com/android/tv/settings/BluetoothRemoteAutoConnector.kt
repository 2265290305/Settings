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
                    // 解绑后蓝牙栈里没有名字记录（device.name 为 null），只有广播里的
                    // EXTRA_NAME 可用；不读它就认不出目标遥控器。
                    val extraName = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                    device?.let { maybeAutoHandle(it, extraName) }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                    // 配对流程结束（成功或失败/解绑）就允许下次进配对模式时重新 createBond；
                    // 进程常驻（persistent app），不清掉的话第二次配对永远不会自动发起。
                    if (state == BluetoothDevice.BOND_BONDED || state == BluetoothDevice.BOND_NONE) {
                        device?.let { autoPairTried.remove(it.address) }
                    }
                    if (state == BluetoothDevice.BOND_BONDED && device != null &&
                        isTargetRemote(device) && !isIgnoredByUser(device.address)
                    ) {
                        // 配对成功：多次重试连接，规避 HID 未就绪导致的首次连接不可用。
                        connectWithRetry(device)
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    // 被动连接模型（厂商 AutoBluetooth 同款：ACL_DISCONNECTED 只记录）：
                    // 遥控器主动断开（退出配对模式/休眠/用户取消连接）一律不强制回连，
                    // 否则会跟设置页“取消连接”打架，并对休眠遥控器造成假连接。
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null && isTargetRemote(device)) {
                        Log.d(TAG, "target remote acl disconnected, no auto reconnect address=${device.address}")
                    }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON) {
                        // 蓝牙打开后只重新扫描（厂商 STATE_ON 同款）。不主动回连已绑定
                        // 遥控器：对未在广播的休眠遥控器发起连接会产生“显示已连接但
                        // 遥控器无反应”的假连接。已绑定遥控器由它自己按键回连。
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

        // 开机/启动即生效：只扫描以便配对新遥控器（被动连接模型，不主动回连
        // 已绑定遥控器——那会对休眠遥控器造成假连接）。
        if (adapter?.isEnabled == true) {
            startScan()
        }
        Log.d(TAG, "auto connector started, bt enabled=${adapter?.isEnabled}")
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

    /**
     * 用户在设置页“取消连接”留下的黑名单（与 BlueToothScreen 共用同一 prefs）。
     * “取消连接”不解绑，遥控器不会自广播——广播被扫到必是用户按两键进配对模式，
     * 视为明确重连意图：清黑名单并恢复 connection policy——否则协议栈会拒绝
     * HID connect，设置页的 ACL_CONNECTED 守卫也会把刚连上的遥控器再踢掉。
     */
    private fun clearUserBlockForRepairing(device: BluetoothDevice) {
        val prefs = appContext?.getSharedPreferences(
            "ignored_bluetooth_devices", Context.MODE_PRIVATE
        ) ?: return
        val address = device.address
        val blocked = prefs.getStringSet("disconnected_addresses", emptySet()).orEmpty()
        if (address !in blocked) return
        prefs.edit()
            .putStringSet("disconnected_addresses", (blocked - address).toSet())
            .apply()
        allowConnectionPolicy(device)
        Log.d(TAG, "advertising remote was user-blocked, clear block address=$address")
    }

    /** 恢复 HID/A2DP/HEADSET 的 connection policy（“取消连接”时被设为 FORBIDDEN）。 */
    private fun allowConnectionPolicy(device: BluetoothDevice) {
        val a = adapter ?: return
        intArrayOf(4, BluetoothProfile.A2DP, BluetoothProfile.HEADSET).forEach { profileId ->
            runCatching {
                a.getProfileProxy(appContext, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        runCatching {
                            val m = proxy.javaClass.getMethod(
                                "setConnectionPolicy", BluetoothDevice::class.java, Int::class.java
                            )
                            m.isAccessible = true
                            m.invoke(proxy, device, 100) // CONNECTION_POLICY_ALLOWED
                        }.recoverCatching {
                            val m = proxy.javaClass.getMethod(
                                "setPriority", BluetoothDevice::class.java, Int::class.java
                            )
                            m.isAccessible = true
                            m.invoke(proxy, device, 100) // PRIORITY_ON
                        }
                        mainHandler.postDelayed(
                            { runCatching { a.closeProfileProxy(profile, proxy) } }, 1_000L
                        )
                    }

                    override fun onServiceDisconnected(profile: Int) {}
                }, profileId)
            }
        }
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

    private fun maybeAutoHandle(device: BluetoothDevice, extraName: String? = null) {
        if (!isTargetRemote(device, extraName)) return
        // 被忽略的设备不自动处理：忽略后遥控器的自广播和用户按键无法区分。
        if (isIgnoredByUser(device.address)) return
        // ACTION_FOUND = 遥控器正在广播 = 已进入配对模式（HOGP 遥控器只有配对模式才广播）。
        // 这是唯一允许 TV 端主动配对/连接的时机（被动连接模型）。
        clearUserBlockForRepairing(device)
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
