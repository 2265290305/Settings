package com.android.tv.settings

import android.annotation.SuppressLint
import android.app.Service
import androidx.activity.compose.BackHandler
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val BLUE_SCREEN_TAG = "BlueScreen"
private const val IGNORED_BLUETOOTH_PREFS = "ignored_bluetooth_devices"
private const val IGNORED_BLUETOOTH_ADDRESSES = "ignored_addresses"
private const val DISCONNECTED_BLUETOOTH_ADDRESSES = "disconnected_addresses"

// 电信遥控器的自动配对/连接由系统内置 AutoBluetooth 处理。本页只读取配对和连接状态，
// 不再 force-stop 厂商服务，也不对电信遥控器发起 createBond/HID connect/GATT connect。

@SuppressLint("MissingPermission")
@Composable
fun BlueToothScreen(modifier: Modifier = Modifier, navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val pageEntryFocusRequester = LocalEntryFocusRequester.current
    var isScanning by remember { mutableStateOf(false) }
    var btname by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showBleRemoteDialog by remember { mutableStateOf(false) }
    var showBlePairingTip by remember { mutableStateOf(false) }
    // “添加蓝牙遥控器”页内，遥控器断开（含遥控器端退出配对模式主动断开）后的提示。
    var showBleDisconnectTip by remember { mutableStateOf(false) }
    var pendingPairDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var pendingBleConnectAddress by remember { mutableStateOf<String?>(null) }
    var scanTimeoutJob by remember { mutableStateOf<Job?>(null) }
    var activeGatt by remember { mutableStateOf<BluetoothGatt?>(null) }
    var bleConnectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var bleConnectingAddress by remember { mutableStateOf<String?>(null) }
    var autoConnectingAddress by remember { mutableStateOf<String?>(null) }
    var manualDisconnectAddress by remember { mutableStateOf<String?>(null) }
    // 遥控器“已连接”状态黏滞：电信遥控第二次连接时底层可能经过加密握手失败抖动
    //（log: encryption failed → reason=0x0005 断开 → 自动重连），实时状态会闪“连上→未连→连上”。
    // 记录最近确认已连接的地址与截止时间，在断开后的黏滞窗口内显示“连接中...”而非“未连接”，
    // 给底层重连留时间，避免 UI 抖动。手动断开时立即清零，不黏滞。
    var stickyConnectedRemoteAddress by remember { mutableStateOf<String?>(null) }
    var stickyConnectedUntilMs by remember { mutableStateOf(0L) }
    // 黏滞窗口内驱动重组的 tick（否则窗口结束后 UI 不会自动刷新成“未连接”）。
    var connectStateTick by remember { mutableStateOf(0) }
    // 常驻 HID_HOST profile proxy（厂商 AutoBluetooth 的 mService 同款）：页面存续期间只取一次，
    // 连接、断开、状态查询全用它；比每次临时 getProfileProxy + 2 秒后 close 稳定得多。
    var hidHostProxy by remember { mutableStateOf<BluetoothProfile?>(null) }
    val ignoredDevicePrefs = remember(context) {
        context.getSharedPreferences(IGNORED_BLUETOOTH_PREFS, Context.MODE_PRIVATE)
    }
    val forgettingAddresses = remember(context) {
        mutableStateMapOf<String, Boolean>().apply {
            ignoredDevicePrefs
                .getStringSet(IGNORED_BLUETOOTH_ADDRESSES, emptySet())
                .orEmpty()
                .forEach { address -> put(address, true) }
        }
    }
    val blockedAutoReconnectAddresses = remember(context) {
        mutableStateMapOf<String, Boolean>().apply {
            ignoredDevicePrefs
                .getStringSet(DISCONNECTED_BLUETOOTH_ADDRESSES, emptySet())
                .orEmpty()
                .forEach { address -> put(address, true) }
        }
    }
    var showDeviceOptionsDialog by remember { mutableStateOf(false) }
    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    // 用户主动点击“连接/配对”时展示的配对确认框（Just Works 机型底层不发 PAIRING_REQUEST，
    // 只能在 createBond 前主动弹框）。点“配对”后对该设备执行 startBondAndConnect。
    var interactivePairRequest by remember { mutableStateOf<BluetoothDevice?>(null) }

    val Blm = if (LocalInspectionMode.current) {
        null
    } else {
        context.getSystemService(Service.BLUETOOTH_SERVICE) as BluetoothManager
    }
    var isChecked by remember { mutableStateOf(Blm?.adapter?.isEnabled ?: true) }

    val pairedDevices = remember { mutableStateListOf<BluetoothDevice>() }
    val discoveredClassicDevices = remember { mutableStateListOf<BluetoothDevice>() }
    val cachedBleDevices = remember { mutableStateListOf<BluetoothDevice>() }
    val bleScanCallbackRef = remember { arrayOfNulls<ScanCallback>(1) }
    val deviceNameCache = remember { mutableStateMapOf<String, String>() }
    var connectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    val connectedAddresses = remember { mutableStateListOf<String>() }

    fun persistIgnoredAddresses() {
        ignoredDevicePrefs.edit()
            .putStringSet(IGNORED_BLUETOOTH_ADDRESSES, forgettingAddresses.keys.toSet())
            .apply()
    }

    fun persistBlockedAutoReconnectAddresses() {
        ignoredDevicePrefs.edit()
            .putStringSet(DISCONNECTED_BLUETOOTH_ADDRESSES, blockedAutoReconnectAddresses.keys.toSet())
            .apply()
    }

    fun isIgnoredAddress(address: String): Boolean {
        return forgettingAddresses.containsKey(address)
    }

    fun isAutoReconnectBlocked(address: String): Boolean {
        return blockedAutoReconnectAddresses.containsKey(address)
    }

    // “忽略”发生的时刻（elapsedRealtime）。仅存内存：进程重启后视为“很久以前”。
    // 实测（log 18:55:12→18:55:14）：removeBond 后遥控器约 2 秒内自己进入配对模式
    // 开始广播，并非用户按键；无线电上无法区分自广播和用户按两键，只能按时间窗抑制。
    val ignoredAtMs = remember { HashMap<String, Long>() }

    fun addIgnoredAddress(address: String) {
        forgettingAddresses[address] = true
        ignoredAtMs[address] = android.os.SystemClock.elapsedRealtime()
        persistIgnoredAddresses()
        Log.i(BLUE_SCREEN_TAG, "ignore bluetooth device address=$address")
    }

    fun clearIgnoredAddress(address: String) {
        if (forgettingAddresses.remove(address) != null) {
            persistIgnoredAddresses()
            Log.i(BLUE_SCREEN_TAG, "clear ignored bluetooth device address=$address")
        }
    }

    fun addAutoReconnectBlockedAddress(address: String) {
        blockedAutoReconnectAddresses[address] = true
        persistBlockedAutoReconnectAddresses()
        Log.i(BLUE_SCREEN_TAG, "block bluetooth auto reconnect address=$address")
    }

    fun clearAutoReconnectBlockedAddress(address: String) {
        if (blockedAutoReconnectAddresses.remove(address) != null) {
            persistBlockedAutoReconnectAddresses()
            Log.i(BLUE_SCREEN_TAG, "clear bluetooth auto reconnect block address=$address")
        }
    }

    fun restorePageFocus() {
        mainHandler.postDelayed({
            runCatching { pageEntryFocusRequester?.requestFocus() }
        }, 120)
    }

    fun cacheDeviceName(device: BluetoothDevice) {
        val name = device.name?.trim()
        if (!name.isNullOrEmpty()) {
            deviceNameCache[device.address] = name
        }
    }

    fun resolvedDeviceName(device: BluetoothDevice): String? {
        val cachedName = deviceNameCache[device.address]?.trim()
        if (cachedName.hasDisplayableDeviceName()) {
            return cachedName
        }
        val directName = device.name?.trim()
        if (directName.hasDisplayableDeviceName()) {
            return directName
        }
        return null
    }

    fun hasDisplayableName(device: BluetoothDevice): Boolean {
        return resolvedDeviceName(device).hasDisplayableDeviceName()
    }

    fun displayDeviceName(device: BluetoothDevice): String {
        return resolvedDeviceName(device)
            ?: "未知设备"
    }

    // 厂商 AutoBluetooth.judgeRC 同款白名单。电信遥控器只读状态，普通设备仍可手动连接。
    fun isTelecomRemote(device: BluetoothDevice): Boolean {
        val name = displayDeviceName(device)
        return name.contains("RC-01", ignoreCase = true) ||
                name.contains("RC-03", ignoreCase = true) ||
                name.contains("电信蓝牙遥控")
    }

    fun isDeviceConnectedNow(device: BluetoothDevice): Boolean {
        val address = device.address
        // 最可靠：常驻 HID proxy 的真实 profile 状态（BluetoothManager.getConnectedDevices
        // 只支持 GATT，查不到 HID；这正是“UI 显示已连接但遥控器无反应”的误报来源）。
        hidHostProxy?.let { proxy ->
            val state = runCatching { proxy.getConnectionState(device) }.getOrNull()
            if (state == BluetoothProfile.STATE_CONNECTED) return true
        }
        if (connectedAddresses.contains(address)) return true
        if (connectedDevice?.address == address) return true
        if (bleConnectedDevice?.address == address) return true

        val manager = Blm ?: return false

        // 尝试通过多个 Profile 检查
        val profiles = intArrayOf(
            BluetoothProfile.A2DP,
            BluetoothProfile.HEADSET,
            BluetoothProfile.GATT,
            4, // HID_HOST
            BluetoothProfile.GATT_SERVER
        )

        val connectedByManager = profiles.any { profile ->
            runCatching {
                manager.getConnectedDevices(profile).any { it.address == address }
            }.getOrDefault(false)
        }
        if (connectedByManager) return true

        // 终极手段：通过反射检查连接状态（ACTION_ACL_CONNECTED 可能漏掉初始状态）
        return runCatching {
            val isConnectedMethod = device.javaClass.getDeclaredMethod("isConnected")
            isConnectedMethod.invoke(device) as Boolean
        }.getOrDefault(false)
    }

    fun isDeviceConnectedForUi(device: BluetoothDevice): Boolean {
        val address = device.address
        if (isIgnoredAddress(address) || isAutoReconnectBlocked(address)) {
            return false
        }
        return isDeviceConnectedNow(device)
    }

    // 不带本地缓存的实时连接查询：只信 HID proxy 状态与 ACL（反射 isConnected）。
    // updatePairedDevices 的状态同步必须用它——isDeviceConnectedNow 会读 connectedAddresses
    // 缓存，而 markConnected 又写同一缓存，用它同步会自我维持，断开后“已连接”永远清不掉。
    fun isDeviceConnectedLive(device: BluetoothDevice): Boolean {
        hidHostProxy?.let { proxy ->
            val state = runCatching { proxy.getConnectionState(device) }.getOrNull()
            if (state == BluetoothProfile.STATE_CONNECTED) return true
        }
        return runCatching {
            val isConnectedMethod = device.javaClass.getDeclaredMethod("isConnected")
            isConnectedMethod.invoke(device) as Boolean
        }.getOrDefault(false)
    }

    fun markConnected(device: BluetoothDevice?) {
        device ?: return
        val address = device.address
        if (!connectedAddresses.contains(address)) {
            connectedAddresses.add(address)
        }
        if (autoConnectingAddress == address) {
            autoConnectingAddress = null
        }
        if (manualDisconnectAddress == address) {
            manualDisconnectAddress = null
        }
        // 电信遥控真连上：记为黏滞地址、清窗口（连着时不需要黏滞计时）。
        if (isTelecomRemote(device)) {
            stickyConnectedRemoteAddress = address
            stickyConnectedUntilMs = 0L
        }
    }

    fun markDisconnected(device: BluetoothDevice?) {
        device ?: return
        val address = device.address
        connectedAddresses.remove(address)
        if (connectedDevice?.address == address) connectedDevice = null
        if (bleConnectedDevice?.address == address) bleConnectedDevice = null
        if (autoConnectingAddress == address) {
            autoConnectingAddress = null
        }
        // 电信遥控断开：若非手动断开且此前是黏滞已连地址，启动 5 秒黏滞窗口——
                // 期间 UI 显示“连接中...”而非“未连接”，吸收加密握手失败导致的状态抖动。
        // 手动断开则立即失效黏滞。
        if (isTelecomRemote(device)) {
            if (address == manualDisconnectAddress || address != stickyConnectedRemoteAddress) {
                if (address == stickyConnectedRemoteAddress) {
                    stickyConnectedRemoteAddress = null
                    stickyConnectedUntilMs = 0L
                }
            } else {
                stickyConnectedUntilMs = android.os.SystemClock.elapsedRealtime() + 5_000L
                // 窗口到期后驱动一次重组，让 UI 从“连接中”落回“未连接”。
                mainHandler.postDelayed({ connectStateTick++ }, 5_200L)
            }
        }
    }

    fun isBleDevice(device: BluetoothDevice): Boolean {
        return device.type == BluetoothDevice.DEVICE_TYPE_LE ||
                device.type == BluetoothDevice.DEVICE_TYPE_DUAL
    }

    // platform 签名 + priv-app，直接调用 framework 的 @SystemApi（与 AOSP TvSettings
    // BluetoothDevicePairer.unpairDevice 完全一致），不再走反射。
    fun removeBondCompat(device: BluetoothDevice): Boolean {
        return runCatching { device.removeBond() }.getOrDefault(false)
    }

    fun cancelBondProcessCompat(device: BluetoothDevice): Boolean {
        return runCatching { device.cancelBondProcess() }.getOrDefault(false)
    }

    fun setConnectionPolicyCompat(device: BluetoothDevice, allowed: Boolean) {
        val policy = if (allowed) 100 else 0
        val adapter = Blm?.adapter ?: return
        intArrayOf(4, BluetoothProfile.A2DP, BluetoothProfile.HEADSET).forEach { profileId ->
            adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                @SuppressLint("DiscouragedPrivateApi")
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    runCatching {
                        val method = proxy.javaClass.getMethod(
                            "setConnectionPolicy",
                            BluetoothDevice::class.java,
                            Int::class.javaPrimitiveType
                        )
                        method.isAccessible = true
                        method.invoke(proxy, device, policy)
                    }.recoverCatching {
                        val method = proxy.javaClass.getMethod(
                            "setPriority",
                            BluetoothDevice::class.java,
                            Int::class.javaPrimitiveType
                        )
                        method.isAccessible = true
                        method.invoke(proxy, device, policy)
                    }.onFailure {
                        Log.d(
                            BLUE_SCREEN_TAG,
                            "set connection policy failed profile=$profileId address=${device.address}: ${it.message}"
                        )
                    }
                    mainHandler.postDelayed({ adapter.closeProfileProxy(profile, proxy) }, 1000)
                }

                override fun onServiceDisconnected(profile: Int) {}
            }, profileId)
        }
    }

    fun isLikelyRemote(device: BluetoothDevice): Boolean {
        val display = displayDeviceName(device).lowercase()
        val remoteKeywords = listOf(
            "remote",
            "remoter",
            "controller",
            "rc-01",
            "rc-03",
            "dlife-rc1002",
            "cmcc_voice_remote",
            "遥控",
            "蓝牙遥控",
            "电信蓝牙遥控"
        )
        if (remoteKeywords.any { display.contains(it) }) return true

        val btClass = device.bluetoothClass
        if (btClass != null) {
            val devClass = btClass.deviceClass
            // 0x0500 is PERIPHERAL class (keyboards, mice, remotes)
            if ((devClass and 0x0500) == 0x0500) return true
        }
        return false
    }

    fun addClassicDevice(device: BluetoothDevice) {
        if (!hasDisplayableName(device)) return
        val index = discoveredClassicDevices.indexOfFirst { it.address == device.address }
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            if (index >= 0) discoveredClassicDevices.removeAt(index)
            return
        }
        if (index >= 0) {
            discoveredClassicDevices[index] = device
        } else {
            discoveredClassicDevices.add(device)
        }
    }

    fun addBleDeviceToCache(device: BluetoothDevice) {
        if (!hasDisplayableName(device)) return
        val index = cachedBleDevices.indexOfFirst { it.address == device.address }
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            if (index >= 0) cachedBleDevices.removeAt(index)
            return
        }
        if (index >= 0) {
            cachedBleDevices[index] = device
        } else {
            cachedBleDevices.add(device)
        }
    }

    fun addDiscoveredDevice(device: BluetoothDevice) {
        if (isIgnoredAddress(device.address) && device.bondState != BluetoothDevice.BOND_NONE) {
            return
        }
        cacheDeviceName(device)
        if (isBleDevice(device)) {
            addBleDeviceToCache(device)
        } else {
            addClassicDevice(device)
        }
    }

    fun updatePairedDevices() {
        Blm?.adapter?.bondedDevices?.let {
            pairedDevices.clear()
            pairedDevices.addAll(it.filter { device ->
                !forgettingAddresses.containsKey(device.address) && hasDisplayableName(device)
            })
            // 同步已配对设备中的连接状态
            it.forEach { device ->
                if (isIgnoredAddress(device.address) || isAutoReconnectBlocked(device.address)) {
                    markDisconnected(device)
                } else if (isDeviceConnectedLive(device)) {
                    markConnected(device)
                } else {
                    // 实时未连接：主动清缓存，否则断开后 connectedAddresses 残留，
                    // “我的设备”会一直误显示“已连接”。
                    markDisconnected(device)
                }
            }
        }
    }

    fun updateConnectedDevice() {
        // 更新 A2DP
        Blm?.adapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.A2DP) {
                    proxy.connectedDevices.forEach { device ->
                        if (isIgnoredAddress(device.address) || isAutoReconnectBlocked(device.address)) {
                            markDisconnected(device)
                            return@forEach
                        }
                        connectedDevice = device
                        cacheDeviceName(device)
                        markConnected(device)
                    }
                    Blm.adapter.closeProfileProxy(profile, proxy)
                }
            }

            override fun onServiceDisconnected(profile: Int) {}
        }, BluetoothProfile.A2DP)

        // 特别更新 HID 遥控器状态
        Blm?.adapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                proxy.connectedDevices.forEach { device ->
                    if (isIgnoredAddress(device.address) || isAutoReconnectBlocked(device.address)) {
                        markDisconnected(device)
                        return@forEach
                    }
                    markConnected(device)
                }
                Blm.adapter.closeProfileProxy(profile, proxy)
            }

            override fun onServiceDisconnected(profile: Int) {}
        }, 4) // HID_HOST
    }

    fun disconnectDevice(device: BluetoothDevice, blockAutoReconnect: Boolean = true) {
        val address = device.address
        manualDisconnectAddress = address
        markDisconnected(device)
        if (connectedDevice?.address == address) connectedDevice = null
        if (bleConnectedDevice?.address == address) bleConnectedDevice = null
        if (autoConnectingAddress == address) autoConnectingAddress = null
        if (bleConnectingAddress == address) bleConnectingAddress = null
        // 普通设备取消连接不 removeBond，只断 profile 并禁止自动重连。
        if (blockAutoReconnect) {
            addAutoReconnectBlockedAddress(address)
            setConnectionPolicyCompat(device, allowed = false)
        }
        if (isLikelyRemote(device)) {
            Toast.makeText(context, "已断开连接", Toast.LENGTH_SHORT).show()
        }
        if (isBleDevice(device)) {
            if (bleConnectedDevice?.address == address || activeGatt?.device?.address == address) {
                activeGatt?.disconnect()
            }
        }
        val adapter = Blm?.adapter ?: return
        intArrayOf(4, BluetoothProfile.A2DP, BluetoothProfile.HEADSET).forEach { profileId ->
            adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                @SuppressLint("DiscouragedPrivateApi")
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    runCatching {
                        val method =
                            proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                        method.isAccessible = true
                        method.invoke(proxy, device)
                    }
                    mainHandler.postDelayed({ adapter.closeProfileProxy(profile, proxy) }, 1000)
                }

                override fun onServiceDisconnected(profile: Int) {}
            }, profileId)
        }
    }

    fun removeDeviceFromUi(device: BluetoothDevice) {
        val address = device.address
        pairedDevices.removeAll { it.address == address }
        discoveredClassicDevices.removeAll { it.address == address }
        cachedBleDevices.removeAll { it.address == address }
        connectedAddresses.removeAll { it == address }
        if (connectedDevice?.address == address) connectedDevice = null
        if (bleConnectedDevice?.address == address) bleConnectedDevice = null
        if (pendingPairDevice?.address == address) {
            pendingPairDevice = null
            pendingBleConnectAddress = null
            showBlePairingTip = false
        }
        if (autoConnectingAddress == address) autoConnectingAddress = null
        if (bleConnectingAddress == address) bleConnectingAddress = null
        if (manualDisconnectAddress == address) manualDisconnectAddress = null
    }

    fun cancelPairingState(device: BluetoothDevice) {
        val address = device.address
        if (pendingPairDevice?.address == address || pendingBleConnectAddress == address) {
            pendingPairDevice = null
            pendingBleConnectAddress = null
        }
        showBlePairingTip = false
        if (device.bondState == BluetoothDevice.BOND_BONDING) {
            val canceled = cancelBondProcessCompat(device)
            Log.i(BLUE_SCREEN_TAG, "cancel bond process address=$address result=$canceled")
        }
    }

    // 普通 HID 设备连接：常驻 HID Host proxy 反射 connect，连接前停止扫描。
    // connect() 的返回值只表示请求是否受理，最终以 profile state 为准；未连接则限次重试。
    fun connectHidProfile(device: BluetoothDevice, attempt: Int = 0) {
        if (isTelecomRemote(device)) {
            Log.i(
                BLUE_SCREEN_TAG,
                "skip hid connect for telecom remote address=${device.address}"
            )
            return
        }
        val adapter = Blm?.adapter ?: return
        if (isIgnoredAddress(device.address)) return
        val proxy = hidHostProxy
        if (proxy == null) {
            // proxy 尚未就绪（getProfileProxy 异步回调），稍后重试。
            if (attempt < 10) {
                mainHandler.postDelayed({ connectHidProfile(device, attempt + 1) }, 1000)
            }
            return
        }
        adapter.cancelDiscovery()
        val state = runCatching { proxy.getConnectionState(device) }.getOrNull()
        if (state == BluetoothProfile.STATE_CONNECTED) {
            if (bleConnectingAddress == device.address) {
                bleConnectingAddress = null
            }
            markConnected(device)
            return
        }
        if (state == BluetoothProfile.STATE_CONNECTING) {
            if (attempt < 10) {
                mainHandler.postDelayed({ connectHidProfile(device, attempt + 1) }, 1200)
            }
            return
        }
        val ok = runCatching {
            val method = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
            method.isAccessible = true
            method.invoke(proxy, device) as? Boolean ?: false
        }.getOrDefault(false)
        Log.i(BLUE_SCREEN_TAG, "hid connect address=${device.address} attempt=$attempt result=$ok")
        mainHandler.postDelayed({
            val connected = hidHostProxy?.let { currentProxy ->
                runCatching {
                    currentProxy.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED
                }.getOrDefault(false)
            } == true
            if (connected) {
                if (bleConnectingAddress == device.address) bleConnectingAddress = null
                markConnected(device)
                updatePairedDevices()
            } else if (attempt < 10 && !isIgnoredAddress(device.address)) {
                connectHidProfile(device, attempt + 1)
            } else {
                if (bleConnectingAddress == device.address) bleConnectingAddress = null
                markDisconnected(device)
                updatePairedDevices()
            }
        }, if (ok) 1200 else 1000)
    }

    val bleGattCallback = remember {
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                mainHandler.post {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            if (isIgnoredAddress(gatt.device.address)) {
                                Log.i(
                                    BLUE_SCREEN_TAG,
                                    "disconnect ignored gatt device address=${gatt.device.address}"
                                )
                                runCatching { gatt.disconnect() }
                                runCatching { gatt.close() }
                                if (activeGatt?.device?.address == gatt.device.address) {
                                    activeGatt = null
                                }
                                markDisconnected(gatt.device)
                                removeDeviceFromUi(gatt.device)
                                return@post
                            }
                            // 对 HID 遥控器，GATT 的 STATE_CONNECTED 只代表底层 ACL 通了，
                            // 不代表 HID/HOGP 真正连接、遥控器可用。这里只复核状态，不补发 HID connect。
                            if (isLikelyRemote(gatt.device)) {
                                gatt.discoverServices()
                                mainHandler.postDelayed({
                                    if (bleConnectingAddress == gatt.device.address) {
                                        bleConnectingAddress = null
                                    }
                                    if (isDeviceConnectedNow(gatt.device)) {
                                        markConnected(gatt.device)
                                    } else {
                                        markDisconnected(gatt.device)
                                    }
                                }, 1500)
                            } else {
                                // 非遥控器 BLE 设备，GATT 连上即视为连接。
                                if (bleConnectingAddress == gatt.device.address) {
                                    bleConnectingAddress = null
                                }
                                bleConnectedDevice = gatt.device
                                markConnected(gatt.device)
                                gatt.discoverServices()
                            }
                        }

                        BluetoothProfile.STATE_DISCONNECTED -> {
                            if (bleConnectedDevice?.address == gatt.device.address) bleConnectedDevice =
                                null
                            markDisconnected(gatt.device)
                            gatt.close()
                            if (manualDisconnectAddress == gatt.device.address) {
                                manualDisconnectAddress = null
                            }
                            // 被动模型：GATT 断开后不回连，等设备重新广播时由扫描回调连回。
                        }
                    }
                }
            }
        }
    }

    fun stopScan() {
        Blm?.adapter?.let { adapter ->
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            bleScanCallbackRef[0]?.let { adapter.bluetoothLeScanner?.stopScan(it) }
        }
        scanTimeoutJob?.cancel()
        isScanning = false
    }


    // 真正发起配对(createBond)并补连的动作。从 connectDevice 的 BOND_NONE 分支抽出，
    // 供“主动弹配对确认框后点配对”复用。
    fun startBondAndConnect(device: BluetoothDevice) {
        if (isTelecomRemote(device)) {
            Log.i(
                BLUE_SCREEN_TAG,
                "skip local bond/connect for telecom remote address=${device.address}"
            )
            updatePairedDevices()
            return
        }
        stopScan()
        pendingBleConnectAddress = device.address
        pendingPairDevice = device
        showBlePairingTip = true
        val started = device.createBond()
        if (!started) {
            Toast.makeText(context, "发起配对失败，请重试", Toast.LENGTH_SHORT).show()
            pendingBleConnectAddress = null
            pendingPairDevice = null
        } else if (isLikelyRemote(device) && !isTelecomRemote(device)) {
            // 厂商 AutoBluetooth 同款：createBond 后 500ms 主动补一次 HID connect
            //（失败会按 1 秒间隔重试），防止 BONDED 广播丢失导致“只配对不连接”。
            mainHandler.postDelayed({
                if (!isDeviceConnectedNow(device)) connectHidProfile(device)
            }, 500)
        }
    }

    // interactive=true：用户主动点击普通蓝牙设备时先弹确认框，用户点“配对”再走
    // startBondAndConnect。电信遥控器在函数入口直接返回，避免抢 AutoBluetooth。
    fun connectDevice(
        device: BluetoothDevice,
        clearIgnored: Boolean = false,
        interactive: Boolean = false
    ) {
        if (isTelecomRemote(device)) {
            Log.i(
                BLUE_SCREEN_TAG,
                "skip local connect for telecom remote address=${device.address}"
            )
            updatePairedDevices()
            return
        }
        if (clearIgnored) {
            clearAutoReconnectBlockedAddress(device.address)
            setConnectionPolicyCompat(device, allowed = true)
        }
        if (isIgnoredAddress(device.address)) {
            if (clearIgnored && device.bondState == BluetoothDevice.BOND_NONE) {
                clearIgnoredAddress(device.address)
            } else {
                Log.i(
                    BLUE_SCREEN_TAG,
                    "skip connect ignored bluetooth device address=${device.address}"
                )
                disconnectDevice(device)
                removeDeviceFromUi(device)
                return
            }
        }
        manualDisconnectAddress = null
        if (device.bondState == BluetoothDevice.BOND_NONE) {
            if (interactive) {
                // 主动弹配对确认框；确认后由 onConfirm 调 startBondAndConnect。
                stopScan()
                interactivePairRequest = device
                return
            }
            startBondAndConnect(device)
            return
        }
        stopScan()
        bleConnectingAddress = device.address
        if (isLikelyRemote(device)) {
            // HOGP 遥控由系统 HID Host 管理。另开 connectGatt 会与 HID profile 抢同一 ACL，
            // 容易出现 UI 已连接但按键不可用、认证抖动或重复断连。
            connectHidProfile(device)
            return
        }
        if (isBleDevice(device)) {
            activeGatt?.close()
            activeGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, bleGattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, bleGattCallback)
            }
        } else {
            mainHandler.postDelayed({
                if (bleConnectingAddress == device.address) {
                    bleConnectingAddress = null
                }
                if (isDeviceConnectedNow(device)) {
                    markConnected(device)
                }
            }, 1800)
        }
    }

    fun maybeAutoConnectRemote(device: BluetoothDevice) {
        if (!isTelecomRemote(device)) return
        cacheDeviceName(device)
        updatePairedDevices()
    }
    // 电信遥控器广播只用于发现和刷新 UI，配对/连接动作交给系统 AutoBluetooth。
    val autoRemoteAttemptAt = remember { HashMap<String, Long>() }
    fun autoConnectAdvertisingRemote(device: BluetoothDevice) {
        if (!isTelecomRemote(device)) return
        val address = device.address
        val now = android.os.SystemClock.elapsedRealtime()
        val last = autoRemoteAttemptAt[address]
        if (last != null && now - last < 6_000) return
        autoRemoteAttemptAt[address] = now
        Log.i(
            BLUE_SCREEN_TAG,
            "telecom remote advertising, refresh state only address=$address bond=${device.bondState}"
        )
        cacheDeviceName(device)
        updatePairedDevices()
    }

    val bleScanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                result.device?.let { scannedDevice ->
                    // 广播包里的名字必须先入缓存：遥控器被忽略/解绑后，蓝牙栈里的名字记录
                    // 一并被删（device.name 为 null），只有 scanRecord 里带名字；不读它就认
                    // 不出“电信蓝牙遥控”，isTelecomRemote 判 false，自动连接永远不触发。
                    val advName = result.scanRecord?.deviceName?.trim()?.takeIf { it.isNotEmpty() }
                    advName?.let { deviceNameCache[scannedDevice.address] = it }
                    // 诊断：带名字或带 HID 服务(0x1812)的扫描结果，打印名字来源，
                    // 用于定位“扫到了却认不出遥控器”的问题。
                    val hasHidUuid = result.scanRecord?.serviceUuids
                        ?.any { it.uuid.toString().startsWith("00001812") } == true
                    if (advName != null || hasHidUuid) {
                        Log.i(
                            BLUE_SCREEN_TAG,
                            "ble scan address=${scannedDevice.address} advName=$advName " +
                                    "stackName=${scannedDevice.name} hid=$hasHidUuid"
                        )
                    }
                    addDiscoveredDevice(scannedDevice)
                    autoConnectAdvertisingRemote(scannedDevice)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                // BLE 扫描失败是静默的（如注册失败/过于频繁），必须打出来，
                // 否则“扫描在跑”可能只是经典发现，BLE 名字通道早已失效。
                Log.w(BLUE_SCREEN_TAG, "ble scan failed error=$errorCode")
            }
        }
    }
    bleScanCallbackRef[0] = bleScanCallback

    fun startScan() {
        discoveredClassicDevices.clear()
        cachedBleDevices.clear()
        isScanning = true
        Blm?.adapter?.let { adapter ->
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            adapter.startDiscovery()
            adapter.bluetoothLeScanner?.startScan(
                null,
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                bleScanCallback
            )
        }
        scanTimeoutJob?.cancel()
        scanTimeoutJob = scope.launch {
            delay(8000)
            stopScan()
        }
    }

    fun forgetDevice(device: BluetoothDevice): Boolean {
        val address = device.address
        addIgnoredAddress(address)
        cancelPairingState(device)

        // 先关掉 BLE GATT 客户端连接（若有），避免它把 ACL 顶住导致遥控器感知不到断开。
        if (bleConnectedDevice?.address == address || activeGatt?.device?.address == address) {
            activeGatt?.let {
                runCatching { it.disconnect() }
                runCatching { it.close() }
            }
            activeGatt = null
        }

        // 关键：与 AOSP TvSettings unpairDevice 完全一致——不手动逐个 profile disconnect，
        // 直接 removeBond()。framework 的 removeBond 会原子地断开所有 profile(含 HID) + 删除 link key，
        // HID 遥控器 ACL 被正常断开后才会重新进入配对模式（否则它以为还连着，不会广播）。
        val bondState = device.bondState
        if (bondState == BluetoothDevice.BOND_BONDING) {
            cancelBondProcessCompat(device)
        }
        val started = if (bondState == BluetoothDevice.BOND_NONE) {
            true
        } else {
            removeBondCompat(device)
        }
        if (!started) {
            Log.w(BLUE_SCREEN_TAG, "removeBond returned false address=$address; keep ignored")
        }

        // 本地状态与 UI 立即清理（真正的解绑完成会由 ACTION_BOND_STATE_CHANGED → BOND_NONE 再确认一次）。
        markDisconnected(device)
        if (connectedDevice?.address == address) connectedDevice = null
        if (bleConnectedDevice?.address == address) bleConnectedDevice = null
        if (autoConnectingAddress == address) autoConnectingAddress = null
        if (bleConnectingAddress == address) bleConnectingAddress = null
        removeDeviceFromUi(device)
        updatePairedDevices()

        // 忽略后不要重启扫描！否则会立刻扫到刚忽略的遥控器并被自动连接/配对逻辑配回来，
        // 导致“忽略→秒配回来→又得再忽略”的死循环。只清缓存刷新列表即可。
        pendingBleConnectAddress = null
        pendingPairDevice = null
        discoveredClassicDevices.clear()
        cachedBleDevices.clear()
        mainHandler.postDelayed({ updatePairedDevices() }, 800)
        return started
    }

    val bluetoothReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        // 同 BLE 扫描：发现广播自带的名字先入缓存（解绑后栈里无名字记录）。
                        val extraName = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)?.trim()
                            ?.takeIf { it.isNotEmpty() }
                        if (extraName != null && device != null) {
                            deviceNameCache[device.address] = extraName
                        }
                        if (device != null) {
                            Log.i(
                                BLUE_SCREEN_TAG,
                                "classic found address=${device.address} " +
                                        "extraName=$extraName stackName=${device.name}"
                            )
                        }
                        device?.let {
                            addDiscoveredDevice(it)
                            autoConnectAdvertisingRemote(it)
                        }
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        if (scanTimeoutJob == null) isScanning = false
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                        isScanning = true
                    }

                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val device: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                        if (state == BluetoothDevice.BOND_NONE && device != null) {
                            removeDeviceFromUi(device)
                            updatePairedDevices()
                        } else if (state == BluetoothDevice.BOND_BONDING) {
                            if (device != null && isIgnoredAddress(device.address)) {
                                cancelPairingState(device)
                                removeDeviceFromUi(device)
                                updatePairedDevices()
                                return
                            }
                        } else if (state == BluetoothDevice.BOND_BONDED) {
                            if (device != null && isIgnoredAddress(device.address)) {
                                cancelPairingState(device)
                                setConnectionPolicyCompat(device, allowed = false)
                                disconnectDevice(device)
                                removeBondCompat(device)
                                removeDeviceFromUi(device)
                                updatePairedDevices()
                                return
                            }
                            updatePairedDevices()
                            // 双保险：已忽略/已 block 的设备即使 BONDED（可能被系统厂商进程重新配对回来），
                            // 也绝不触发 connectDevice/自动连，否则忽略会被立刻撤销。
                            if (device != null && (isIgnoredAddress(device.address) || isAutoReconnectBlocked(device.address))) {
                                // 什么都不做，交由上面的 isIgnored 分支或用户手动处理。
                            } else if (pendingBleConnectAddress == device?.address && device != null &&
                                !isTelecomRemote(device)
                            ) {
                                showBleDisconnectTip = false
                                showBlePairingTip = true
                                mainHandler.postDelayed({ showBlePairingTip = false }, 2200)
                                // 配对完成，清掉“配对中”状态，交棒给连接阶段
                                //（connectDevice 会设 bleConnectingAddress → UI 显示“连接中...”）。
                                pendingBleConnectAddress = null
                                pendingPairDevice = null
                                connectDevice(device, clearIgnored = true)
                            } else {
                                device?.let { maybeAutoConnectRemote(it) }
                            }
                        }
                    }

                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        val device: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        if (device != null && isIgnoredAddress(device.address)) {
                            setConnectionPolicyCompat(device, allowed = false)
                            disconnectDevice(device)
                            removeDeviceFromUi(device)
                            updatePairedDevices()
                            return
                        }
                        if (device != null && isAutoReconnectBlocked(device.address)) {
                            disconnectDevice(device)
                            removeDeviceFromUi(device)
                            updatePairedDevices()
                            return
                        }
                        if (device != null && bleConnectingAddress == device.address) {
                            bleConnectingAddress = null
                        }
                        // ACL 连上后延迟复核 HID/profile 真实连接状态，避免“连上 ACL 但 HID 没连”误报。
                        val connectedDeviceRef = device
                        if (connectedDeviceRef != null && isLikelyRemote(connectedDeviceRef)) {
                            mainHandler.postDelayed({
                                if (isDeviceConnectedNow(connectedDeviceRef)) {
                                    markConnected(connectedDeviceRef)
                                } else {
                                    markDisconnected(connectedDeviceRef)
                                }
                                updatePairedDevices()
                            }, 1200)
                        } else {
                            markConnected(device)
                        }
                        updatePairedDevices()
                    }

                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        val device: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        if (device?.address == manualDisconnectAddress) {
                            manualDisconnectAddress = null
                        }
                        // 在 markDisconnected 清缓存之前记录“此前确实连着”——配对/连接过程中的
                        // 瞬态 ACL 断开（如认证重试）发生时设备尚未被标记已连接，据此区分真断开。
                        val wasConnected =
                            device != null && connectedAddresses.contains(device.address)
                        markDisconnected(device)
                        updatePairedDevices()
                        // HID profile 状态比 ACL 断开事件滞后约 200ms，上面这次同步可能仍把
                        // 设备判成已连接；延迟 1.5s 再同步一次，以稳定后的真实状态为准。
                        mainHandler.postDelayed({ updatePairedDevices() }, 1500)
                        // “添加蓝牙遥控器”页开着时，遥控器“真断开”（此前已连接）才触发页内提示；
                        // 提示是否真正显示还要看当前实时状态（调用处以 connectedRemoteAddress 把关），
                        // 重连自愈后即使该标志还在也不会误显示“已断开”。
                        if (showBleRemoteDialog && device != null && wasConnected &&
                            isTelecomRemote(device)
                        ) {
                            showBlePairingTip = false
                            // 延迟 500ms 再置位：HID proxy 状态比 ACL 事件滞后约 200ms，立即置位
                            // 触发的重组会把实时状态误读成“还连着”，状态门将真断开的提示也拦掉。
                            mainHandler.postDelayed({
                                showBleDisconnectTip = true
                                mainHandler.postDelayed({ showBleDisconnectTip = false }, 2500)
                            }, 500)
                        }
                        // 被动模型：断开（含遥控器再按两键退出配对模式的主动断开）后不回连；
                        // 遥控器重新进入配对模式广播时由扫描回调 autoConnectAdvertisingRemote 连回。
                    }
                }
            }
        }
    }

    DisposableEffect(context) {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            // 配对确认交给系统/厂商 AutoBluetooth，设置页不再抢 ACTION_PAIRING_REQUEST。
        }
        context.registerReceiver(bluetoothReceiver, filter)
        // 常驻 HID proxy：页面存续期间取一次（厂商 AutoBluetooth 的 mService 同款）。
        Blm?.adapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                hidHostProxy = proxy
            }

            override fun onServiceDisconnected(profile: Int) {
                hidHostProxy = null
            }
        }, 4)
        onDispose {
            stopScan()
            context.unregisterReceiver(bluetoothReceiver)
            hidHostProxy?.let { Blm?.adapter?.closeProfileProxy(4, it) }
            hidHostProxy = null
        }
    }

    LaunchedEffect(isChecked) {
        if (isChecked) {
            btname = Blm?.adapter?.name ?: ""
            updatePairedDevices()
            updateConnectedDevice()
            // 这里只刷新列表和状态，不主动处理已绑定遥控器。
            startScan()
        }
    }

    // 遥控器页允许扫描发现列表，但发现到电信遥控器后只刷新状态，不主动配对/连接。
    // 配对和连接动作仍交给系统/AutoBluetooth，避免和厂商逻辑抢同一个 HID 连接。
    LaunchedEffect(showBleRemoteDialog) {
        if (!showBleRemoteDialog) return@LaunchedEffect
        startScan()
        while (true) {
            updatePairedDevices()
            updateConnectedDevice()
            delay(6_000)
        }
    }

    if (showRenameDialog) {
        RenameDialog(
            currentName = btname,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                if (Blm?.adapter?.setName(newName) == true) {
                    btname = newName
                } else {
                    Toast.makeText(context, "Failed to change name", Toast.LENGTH_SHORT).show()
                }
                showRenameDialog = false
            }
        )
    }

    // 添加蓝牙遥控器页显示时，主内容整体不渲染，只显示全屏子页（子页自带独立 verticalScroll，
    // 不受这里根 Column 的滚动约束，可用列表可正常滚动且不会撑大主页面 UI）。
    if (!showBleRemoteDialog) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colorResource(R.color.topbar))
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = colorResource(R.color.cardcolor))
            ) {
                Column(
                    modifier = Modifier.padding(30.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .height(78.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("蓝牙开关", fontSize = 16.sp)
                            Spacer(Modifier.weight(1f))
                            Switch(
                                modifier = Modifier.entryFocus(),
                                checked = isChecked,
                                onCheckedChange = { newCheckedState ->
                                    isChecked = newCheckedState
                                    if (isChecked) {
                                        Blm?.adapter?.enable()
                                    } else {
                                        Blm?.adapter?.disable()
                                    }
                                }
                            )
                        }
                    }

                    if (isChecked) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(78.dp)
                                    .clickable { showRenameDialog = true },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Spacer(Modifier.width(24.dp))
                                Text("本机蓝牙名称", fontSize = 16.sp)
                                Spacer(Modifier.weight(1f))
                                Text(btname.ifEmpty { "N/A" }, color = colorResource(R.color.textgray))
                                Icon(
                                    painter = painterResource(id = R.drawable.arrow_right),
                                    contentDescription = null,
                                    tint = Color.Gray
                                )
                                Spacer(Modifier.width(18.dp))
                            }
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(78.dp)
                                    .clickable(onClick = {
                                        showBleRemoteDialog = true
                                        updatePairedDevices()
                                        updateConnectedDevice()
                                        startScan()
                                    }),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Spacer(Modifier.width(24.dp))
                                Text("蓝牙遥控器", fontSize = 16.sp)
                                Spacer(Modifier.weight(1f))
                                Icon(
                                    painter = painterResource(id = R.drawable.arrow_right),
                                    contentDescription = null,
                                    tint = Color.Gray
                                )
                                Spacer(Modifier.width(18.dp))
                            }
                        }
                val myDevices by remember((pairedDevices + listOfNotNull(connectedDevice, bleConnectedDevice))) {
                    derivedStateOf {
                        (pairedDevices + listOfNotNull(connectedDevice, bleConnectedDevice))
                            .filter(::hasDisplayableName)
                            .sortedBy { !isIgnoredAddress(it.address) }
                            .distinctBy { it.address }
                    }
                }
                /*
                val myDevices = (pairedDevices + listOfNotNull(connectedDevice, bleConnectedDevice))
                    .filter(::hasDisplayableName)
                    .filter { !isIgnoredAddress(it.address) }
                    .distinctBy { it.address }
    */
                if (myDevices.isNotEmpty()) {
                    Text(
                        "我的设备",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                    ) {
                        Column {
                            myDevices.forEach { device ->
                                Log.d("address", device.address + ":" + connectedDevice?.address)
                                val isConnected = isDeviceConnectedForUi(device)
                                Row(
                                    modifier = Modifier
                                        .clickable {
                                            selectedDevice = device
                                            showDeviceOptionsDialog = true
                                        }
                                        .padding(horizontal = 16.dp, vertical = 20.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.bluetooth),
                                        contentDescription = "Bluetooth"
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(displayDeviceName(device), fontSize = 16.sp)
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        if (isConnected) "已连接" else "未连接",
                                        color = if (isConnected) Color(0xFF4577FF) else Color.Gray,
                                        fontSize = 14.sp
                                    )
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_info),
                                        contentDescription = "Info",
                                        tint = if (isConnected) Color(0xFF4577FF) else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }

                // 可用蓝牙：Classic + BLE 合并。电信遥控器由独立状态页展示，不在这里连接。
                val myDeviceAddresses = myDevices.map { it.address }.toSet()
                val availableDevices = (discoveredClassicDevices + cachedBleDevices)
                    .filter(::hasDisplayableName)
                    // 电信遥控器由独立的“蓝牙遥控器”页展示状态，连接/配对交给 AutoBluetooth。
                    .filter { !isTelecomRemote(it) }
                    // 忽略后已解绑(BOND_NONE)的设备算作全新可配对设备，允许重新出现在可用列表以便重连；
                    // 仅当仍处于忽略且未解绑时才隐藏。这样“扫到的遥控器”不会因残留 ignored 标记而不显示。
                    .filter { !isIgnoredAddress(it.address) || it.bondState == BluetoothDevice.BOND_NONE }
                    .filter { it.bondState != BluetoothDevice.BOND_BONDED }
                    .filter { it.address !in myDeviceAddresses }
                    .distinctBy { it.address }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("可用蓝牙", fontSize = 16.sp)
                    TextButton(onClick = { startScan() }) {
                        Text("刷新", color = Color(0xFF4577FF))
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    when {
                        isScanning && availableDevices.isEmpty() -> {
                            // 加载骨架匹配列表：默认只显示一条，行高与列表 item(64dp) 一致。
                            SettingsLoadingIndicator(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                rows = 1,
                                rowHeight = 64.dp
                            )
                        }

                        availableDevices.isEmpty() -> {
                            Text(
                                "No new devices found.",
                                modifier = Modifier.padding(16.dp),
                                color = Color.Gray
                            )
                        }

                        else -> {
                            // 外层根 Column 已是 verticalScroll，这里不能再放 LazyColumn/无界 Column，
                            // 否则要么撑大整页把上面 UI 顶走，要么嵌套滚动因高度无界崩溃。
                            // 方案：按设备数展示、最多露 4 个 item（封顶后在卡片内部滚动），
                            // 既能一眼看到多台设备，又不会随设备增多无限撑大页面。
                            val visibleRows = availableDevices.size.coerceAtMost(4)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp * visibleRows)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                availableDevices.forEach { device ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(64.dp)
                                            .clickable {
                                                connectDevice(device, clearIgnored = true, interactive = true)
                                            }
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.bluetooth),
                                            contentDescription = "Bluetooth"
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(displayDeviceName(device), fontSize = 16.sp)
                                        Spacer(Modifier.weight(1f))
                                        Text("点击连接", color = Color.Gray, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

                }
            }
        }
    }

    if (showBleRemoteDialog || LocalInspectionMode.current) {
        // 优先显示已配对电信遥控器；刷新按钮只做扫描发现，不对电信遥控器发起配对/连接。
        val allDiscovered = cachedBleDevices + discoveredClassicDevices +
                pairedDevices.filter { isTelecomRemote(it) }
        // 只显示“电信蓝牙遥控”，过滤掉其他所有蓝牙遥控器/BLE 设备。
        val remoteCandidates = allDiscovered
            .filter { isTelecomRemote(it) }
            .distinctBy { it.address }

        val pairingRemoteAddress = pendingBleConnectAddress
            ?: remoteCandidates.firstOrNull { it.bondState == BluetoothDevice.BOND_BONDING }?.address

        // “已连接”只认实时连接状态（HID proxy/ACL 直查，不走本地缓存），且必须排除配对中地址。
        val connectedRemoteAddress = remoteCandidates
            .firstOrNull { it.address != pairingRemoteAddress && isDeviceConnectedLive(it) }
            ?.address

        // 黏滞“连接中”：底层加密握手失败会瞬断，实时状态可能闪“已连接→未连→已连接”。
        // 若实时未连、但处于黏滞窗口内（markDisconnected 设的 5 秒），把该遥控器显示成“连接中...”
        // 而非“未连接”，吸收抖动。connectStateTick 参与读取以便窗口到期时重组重算。
        connectStateTick // 触发依赖：窗口过期后 tick++ 会让此处重算落回“未连接”
        val stickyReconnectingAddress = stickyConnectedRemoteAddress?.takeIf { addr ->
            addr != connectedRemoteAddress &&
                    addr != pairingRemoteAddress &&
                    stickyConnectedUntilMs > android.os.SystemClock.elapsedRealtime() &&
                    remoteCandidates.any { it.address == addr }
        }
        // “连接中”地址：真正发起连接中(bleConnectingAddress) 或 处于黏滞重连窗口。
        val effectiveConnectingAddress = bleConnectingAddress ?: stickyReconnectingAddress

        BleRemoteDialog(
            devices = remoteCandidates,
            isScanning = isScanning,
            pairingSuccessTipVisible = showBlePairingTip,
            // 断开提示以“当前遥控器实时状态”为准：只要有电信遥控真连着就绝不显示，
            // 重连过程/自愈后标志位残留也不会误报“已断开”。黏滞重连中也不弹“已断开”。
            disconnectTipVisible = showBleDisconnectTip &&
                    connectedRemoteAddress == null && stickyReconnectingAddress == null,
            displayName = ::displayDeviceName,
            connectingAddress = effectiveConnectingAddress,
            connectedAddress = connectedRemoteAddress,
            pairingAddress = pairingRemoteAddress,
            // 遥控器页只展示配对/连接状态，不发起连接或断开。
            onPairRequest = { device ->
                cacheDeviceName(device)
                updatePairedDevices()
            },
            onRefresh = {
                updatePairedDevices()
                updateConnectedDevice()
                startScan()
            },
            onDismiss = { showBleRemoteDialog = false }
        )
    }

    if (showDeviceOptionsDialog && selectedDevice != null) {
        val device = selectedDevice!!
        // 直接用实时连接状态：之前用的 deviceStates 是个永远空的 map，导致已连接设备
        // 按钮恒显示“连接设备”、点击也走 connectDevice 再连而非断开。connectStateTick
        // 参与读取，连接/断开后能刷新按钮文字。
        connectStateTick
        val isConnected = isDeviceConnectedForUi(device)
        ConnectedDeviceOptionsDialog(
            deviceName = displayDeviceName(device),
            isConnected = isConnected,
            showConnectAction = !isTelecomRemote(device),
            onDismiss = {
                showDeviceOptionsDialog = false
                selectedDevice = null
                restorePageFocus()
            },
            onConnectDisconnect = {
                // 点击时以实时状态重判（弹窗打开到点击之间状态可能变）。
                if (!isTelecomRemote(device)) {
                    if (isDeviceConnectedForUi(device)) {
                        disconnectDevice(device)
                    } else {
                        connectDevice(device, clearIgnored = true, interactive = true)
                    }
                }
                showDeviceOptionsDialog = false
                selectedDevice = null
                restorePageFocus()
            },
            onForget = {
                val removed = forgetDevice(device)
                if (!removed) {
                    Toast.makeText(context, "忽略失败，请重试", Toast.LENGTH_SHORT).show()
                }
                showDeviceOptionsDialog = false
                selectedDevice = null
                restorePageFocus()
            }
        )
    }

    // 用户主动发起配对时的确认框（Just Works 机型不发系统 PAIRING_REQUEST，靠这个补上）。
    // 点“配对”才真正 createBond；取消则什么都不做（未 createBond，无需 cancelBondProcess）。
    interactivePairRequest?.let { device ->
        BluetoothPairingDialog(
            deviceName = resolvedDeviceName(device) ?: displayDeviceName(device),
            passkey = null,
            needsInput = false,
            onConfirm = {
                interactivePairRequest = null
                startBondAndConnect(device)
            },
            onCancel = {
                interactivePairRequest = null
            }
        )
    }
}

@Composable
fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }
    val maxLength = 12

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.width(340.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "修改名称",
                    fontSize = 18.sp,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))
                BasicTextField(
                    value = newName,
                    onValueChange = {
                        if (it.toByteArray().size <= maxLength) {
                            newName = it
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF0F0F0), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    decorationBox = { innerTextField ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (newName.isEmpty()) {
                                    Text("请输入名称", color = Color.Gray)
                                }
                                innerTextField()
                            }
                            Text("${newName.toByteArray().size}/$maxLength", color = Color.Gray)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.textButtonColors(containerColor = Color(0xFFF0F0F0))
                    ) {
                        Text("取消", color = Color.Black)
                    }
                    Button(
                        onClick = { onConfirm(newName) },
                        enabled = newName.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4C8DFF))
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

@Composable
fun BleRemoteDialog(
    devices: List<BluetoothDevice>,
    isScanning: Boolean,
    pairingSuccessTipVisible: Boolean,
    disconnectTipVisible: Boolean,
    displayName: (BluetoothDevice) -> String,
    connectingAddress: String?,
    connectedAddress: String?,
    pairingAddress: String?,
    onPairRequest: (BluetoothDevice) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    // 用 Dialog(usePlatformDefaultWidth=false) 渲染到独立 window，真正全屏覆盖（含左侧导航栏），
    // 且天然拥有自己独立的 verticalScroll，不受主页面内容区范围与根 Column 滚动约束。
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFD8DBDF))
        ) {
            val compact = maxWidth < 1100.dp
            val titleSize = if (compact) 30.sp else 40.sp
            val headingSize = if (compact) 38.sp else 52.sp
            val bodySize = if (compact) 26.sp else 36.sp
            val tipsSize = if (compact) 20.sp else 28.sp
            // 显式行高（约 1.35 倍字号）：这些 Text 只覆盖了 fontSize，若继承到固定小行高，
            // 长句换行后两行文字会互相压叠、并与上方文字重叠。
            val headingLine = if (compact) 50.sp else 70.sp
            val bodyLine = if (compact) 36.sp else 49.sp
            val tipsLine = if (compact) 28.sp else 38.sp
            val horizontalPadding = if (compact) 24.dp else 48.dp
            val listPadding = if (compact) 20.dp else 80.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = horizontalPadding, vertical = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.back),
                        contentDescription = "返回",
                        modifier = Modifier.clickable { onDismiss() }
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "添加蓝牙遥控器",
                        fontSize = titleSize,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D2027)
                    )
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(40.dp))
                }

                Spacer(modifier = Modifier.height(if (compact) 20.dp else 34.dp))

                if (compact) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "遥控器蓝牙配对",
                            fontSize = headingSize,
                            lineHeight = headingLine,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF181C23)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "安装电池后，请将遥控器接近设备。",
                            fontSize = bodySize,
                            lineHeight = bodyLine,
                            color = Color(0xFF1F242C)
                        )
                        Text(
                            "同时按住“桌面”和“菜单”键 3 秒以上。",
                            fontSize = bodySize,
                            lineHeight = bodyLine,
                            color = Color(0xFF1F242C)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "已配对的遥控器可长按“桌面”和“菜单”键重新配对。",
                            fontSize = tipsSize,
                            lineHeight = tipsLine,
                            color = Color(0xFF2F343D)
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.lanya),
                                contentDescription = "蓝牙遥控器配对示意图"
                            )
                        }
                    }
                } else {
                    // 注意：这里不能用 weight —— 外层 Column 带 verticalScroll（高度无界），
                    // weight 在无界高度下度量异常，会把 Row 高度算塌，长文换行后溢出与相邻内容重叠。
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 40.dp, top = 30.dp)
                        ) {
                            Text(
                                "遥控器蓝牙配对",
                                fontSize = headingSize,
                                lineHeight = headingLine,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF181C23)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "安装电池后，请将遥控器接近设备。",
                                fontSize = bodySize,
                                lineHeight = bodyLine,
                                color = Color(0xFF1F242C)
                            )
                            Text(
                                "同时按住“桌面”和“菜单”键 3 秒以上。",
                                fontSize = bodySize,
                                lineHeight = bodyLine,
                                color = Color(0xFF1F242C)
                            )
                            Spacer(modifier = Modifier.height(52.dp))
                            Text(
                                "已配对的遥控器可长按“桌面”和“菜单”键重新配对。",
                                fontSize = tipsSize,
                                lineHeight = tipsLine,
                                color = Color(0xFF2F343D)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.lanya),
                                contentDescription = "蓝牙遥控器配对示意图"
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = listPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "遥控器列表",
                        fontSize = if (compact) 20.sp else 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF181C23)
                    )
                    TextButton(onClick = onRefresh) {
                        Text(
                            if (isScanning) "刷新中" else "刷新",
                            color = Color(0xFF4577FF),
                            fontSize = if (compact) 16.sp else 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                /*
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = listPadding),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    when {
                        isScanning && devices.isEmpty() -> {
                            // 加载骨架匹配列表：默认只显示一条，行高与设备 item 一致，不撑高卡片。
                            SettingsLoadingIndicator(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                rows = 1,
                                rowHeight = if (compact) 60.dp else 76.dp
                            )
                        }

                        devices.isEmpty() -> {
                            Text(
                                "未发现可配对遥控器，请重试",
                                modifier = Modifier.padding(24.dp),
                                color = Color(0xFF6C7482),
                                fontSize = if (compact) 18.sp else 24.sp
                            )
                        }

                        else -> {
                            // 列表固定为“一个 item 的高度”：不管发现多少设备，卡片高度都不变，
                            // 多出来的设备在卡片内部滑动查看，绝不撑大页面。
                            val rowHeight = if (compact) 60.dp else 76.dp
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(rowHeight)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                devices.forEach { device ->
                                    // 状态机：配对中(createBond 进行中) → 连接中(HID/GATT 连接) → 已连接。
                                    // “已连接”只在配对、连接全部完成后显示，避免配对阶段抢跳成已连接。
                                    val stateText = when (device.address) {
                                        pairingAddress -> "配对中..."
                                        connectingAddress -> "连接中..."
                                        connectedAddress -> "已连接"
                                        else -> if (device.bondState == BluetoothDevice.BOND_BONDED) {
                                            "未连接"
                                        } else {
                                            "未配对"
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(rowHeight)
                                            .clickable(enabled = false) { onPairRequest(device) }
                                            .padding(horizontal = 24.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.bluetooth),
                                            contentDescription = "Bluetooth"
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Text(
                                            displayName(device),
                                            fontSize = if (compact) 18.sp else 24.sp,
                                            color = Color(0xFF1A1F27)
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            stateText,
                                            fontSize = if (compact) 16.sp else 20.sp,
                                            color = if (stateText == "已连接") Color(0xFF4577FF) else Color(
                                                0xFF6C7482
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }*/
            }

            val tipText = when {
                pairingSuccessTipVisible -> "配对成功，正在发起连接..."
                disconnectTipVisible -> "遥控器已断开连接"
                else -> null
            }
            if (tipText != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 78.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.width(if (compact) 400.dp else 560.dp)
                    ) {
                        Text(
                            tipText,
                            modifier = Modifier.padding(horizontal = 34.dp, vertical = 20.dp),
                            fontSize = if (compact) 18.sp else 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF242830)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectedDeviceOptionsDialog(
    deviceName: String,
    isConnected: Boolean,
    showConnectAction: Boolean = true,
    onDismiss: () -> Unit,
    onConnectDisconnect: () -> Unit,
    onForget: () -> Unit
) {
    val primaryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { primaryFocus.requestFocus() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.width(520.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = deviceName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isConnected) "已连接" else "未连接",
                    fontSize = 14.sp,
                    color = Color(0x99000000)
                )
                Spacer(modifier = Modifier.height(24.dp))
                // 遥控器焦点高亮：聚焦时加明显的橙色粗边框，D-pad 用户一眼看清焦点位置。
                var forgetFocused by remember { mutableStateOf(false) }
                var primaryFocused by remember { mutableStateOf(false) }
                val focusBorderColor = Color(0xFFFF9500)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onForget,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .onFocusChanged { forgetFocused = it.isFocused }
                            .border(
                                width = if (forgetFocused) 3.dp else 0.dp,
                                color = if (forgetFocused) focusBorderColor else Color.Transparent,
                                shape = RoundedCornerShape(50.dp)
                            ),
                        shape = RoundedCornerShape(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (forgetFocused) Color(0xFFE3E7FF) else Color(0xFFF0F2F5)
                        )
                    ) {
                        Text("忽略此设备", color = Color(0xFF3E4CFF))
                    }
                    if (showConnectAction) {
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = onConnectDisconnect,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .focusRequester(primaryFocus)
                                .onFocusChanged { primaryFocused = it.isFocused }
                                .border(
                                    width = if (primaryFocused) 3.dp else 0.dp,
                                    color = if (primaryFocused) focusBorderColor else Color.Transparent,
                                    shape = RoundedCornerShape(50.dp)
                                )
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = if (primaryFocused)
                                            listOf(Color(0xFF6CBBFF), Color(0xFF5A66FF))
                                        else
                                            listOf(Color(0xFF4CA8FF), Color(0xFF3E4CFF))
                                    ),
                                    shape = RoundedCornerShape(50.dp)
                                ),
                            shape = RoundedCornerShape(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                        ) {
                            Text(if (isConnected) "取消连接" else "连接设备", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 用户主动点击普通蓝牙设备后的确认框。点“配对”后由调用方发起 createBond。
 */
@Composable
fun BluetoothPairingDialog(
    deviceName: String,
    passkey: String?,
    needsInput: Boolean,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { confirmFocus.requestFocus() }
    }

    Dialog(onDismissRequest = onCancel) {
        Card(
            modifier = Modifier.width(420.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "蓝牙配对请求",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "与“$deviceName”配对",
                    fontSize = 15.sp,
                    color = Color(0xCC000000),
                    textAlign = TextAlign.Center
                )

                if (needsInput) {
                    Spacer(Modifier.height(16.dp))
                    Text("请输入配对码（PIN）", fontSize = 14.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    BasicTextField(
                        value = pin,
                        onValueChange = { pin = it.filter { c -> c.isDigit() } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF0F0F0), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        decorationBox = { inner ->
                            Box {
                                if (pin.isEmpty()) Text("PIN", color = Color.Gray)
                                inner()
                            }
                        }
                    )
                } else if (!passkey.isNullOrEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("配对码", fontSize = 14.sp, color = Color.Gray)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        passkey,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3E4CFF)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("请确认遥控器上显示的配对码一致", fontSize = 13.sp, color = Color.Gray)
                }

                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.textButtonColors(containerColor = Color(0xFFF0F0F0))
                    ) {
                        Text("取消", color = Color.Black)
                    }
                    Button(
                        onClick = { onConfirm(pin) },
                        enabled = !needsInput || pin.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .focusRequester(confirmFocus),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4C8DFF))
                    ) {
                        Text("配对", color = Color.White)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BlueToothScreenPreview() {
    BlueToothScreen(navController = rememberNavController())
}
