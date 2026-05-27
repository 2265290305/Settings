package com.android.tv.settings

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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

@SuppressLint("MissingPermission")
@Composable
fun BlueToothScreen(modifier: Modifier = Modifier, navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var isScanning by remember { mutableStateOf(false) }
    var btname by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showBleRemoteDialog by remember { mutableStateOf(false) }
    var showBlePairingTip by remember { mutableStateOf(false) }
    var showPairDialog by remember { mutableStateOf(false) }
    var pairRequestCode by remember { mutableStateOf<String?>(null) }
    var pendingPairDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var pendingBleConnectAddress by remember { mutableStateOf<String?>(null) }
    var scanTimeoutJob by remember { mutableStateOf<Job?>(null) }
    var activeGatt by remember { mutableStateOf<BluetoothGatt?>(null) }
    var bleConnectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var bleConnectingAddress by remember { mutableStateOf<String?>(null) }
    var autoConnectingAddress by remember { mutableStateOf<String?>(null) }
    var manualDisconnectAddress by remember { mutableStateOf<String?>(null) }
    var showDeviceOptionsDialog by remember { mutableStateOf(false) }
    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }

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

    fun isDeviceConnectedNow(device: BluetoothDevice): Boolean {
        val address = device.address
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

    fun markConnected(device: BluetoothDevice?) {
        val address = device?.address ?: return
        if (!connectedAddresses.contains(address)) {
            connectedAddresses.add(address)
        }
        if (autoConnectingAddress == address) {
            autoConnectingAddress = null
        }
        if (manualDisconnectAddress == address) {
            manualDisconnectAddress = null
        }
    }

    fun markDisconnected(device: BluetoothDevice?) {
        val address = device?.address ?: return
        connectedAddresses.remove(address)
        if (autoConnectingAddress == address) {
            autoConnectingAddress = null
        }
    }

    fun isBleDevice(device: BluetoothDevice): Boolean {
        return device.type == BluetoothDevice.DEVICE_TYPE_LE ||
            device.type == BluetoothDevice.DEVICE_TYPE_DUAL
    }

    fun removeBondCompat(device: BluetoothDevice): Boolean {
        return runCatching {
            val method = device.javaClass.getMethod("removeBond")
            method.invoke(device) as Boolean
        }.getOrDefault(false)
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
            pairedDevices.addAll(it.filter(::hasDisplayableName))
            // 同步已配对设备中的连接状态
            it.forEach { device ->
                if (isDeviceConnectedNow(device)) {
                    markConnected(device)
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
                    markConnected(device)
                }
                Blm.adapter.closeProfileProxy(profile, proxy)
            }
            override fun onServiceDisconnected(profile: Int) {}
        }, 4) // HID_HOST
    }

    lateinit var triggerRemoteReconnect: (BluetoothDevice) -> Unit

    val bleGattCallback = remember {
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                mainHandler.post {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            bleConnectedDevice = gatt.device
                            markConnected(gatt.device)
                            gatt.discoverServices()
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            if (bleConnectedDevice?.address == gatt.device.address) bleConnectedDevice = null
                            markDisconnected(gatt.device)
                            gatt.close()
                            val wasManualDisconnect = manualDisconnectAddress == gatt.device.address
                            if (wasManualDisconnect) {
                                manualDisconnectAddress = null
                            } else if (isChecked) {
                                triggerRemoteReconnect(gatt.device)
                            }
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

    fun connectHidProfile(device: BluetoothDevice) {
        val adapter = Blm?.adapter ?: return
        adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            @SuppressLint("DiscouragedPrivateApi")
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                runCatching {
                    val method = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                    method.isAccessible = true
                    method.invoke(proxy, device)
                }
                mainHandler.postDelayed({ adapter.closeProfileProxy(profile, proxy) }, 2000)
            }
            override fun onServiceDisconnected(profile: Int) {}
        }, 4)
    }

    fun connectDevice(device: BluetoothDevice) {
        manualDisconnectAddress = null
        if (device.bondState == BluetoothDevice.BOND_NONE) {
            pendingBleConnectAddress = device.address
            pendingPairDevice = device
            showPairDialog = true
            return
        }
        stopScan()
        if (isLikelyRemote(device)) connectHidProfile(device)
        if (isBleDevice(device)) {
            bleConnectingAddress = device.address
            activeGatt?.close()
            activeGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, bleGattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, bleGattCallback)
            }
        }
    }

    fun maybeAutoConnectRemote(device: BluetoothDevice) {
        if (!isLikelyRemote(device) || !hasDisplayableName(device)) return
        if (device.bondState != BluetoothDevice.BOND_BONDED) return
        if (isDeviceConnectedNow(device)) return

        val address = device.address
        if (autoConnectingAddress == address || bleConnectingAddress == address) {
            return
        }

        autoConnectingAddress = address
        mainHandler.post {
            connectDevice(device)
            mainHandler.postDelayed({
                if (autoConnectingAddress == address && !isDeviceConnectedNow(device)) {
                    autoConnectingAddress = null
                }
            }, 4_000)
        }
    }
    triggerRemoteReconnect = ::maybeAutoConnectRemote

    val bleScanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                result.device?.let { scannedDevice ->
                    addDiscoveredDevice(scannedDevice)
                    maybeAutoConnectRemote(scannedDevice)
                }
            }
        }
    }
    bleScanCallbackRef[0] = bleScanCallback

    fun disconnectDevice(device: BluetoothDevice) {
        manualDisconnectAddress = device.address
        if (isBleDevice(device)) {
            if (bleConnectedDevice?.address == device.address) {
                activeGatt?.disconnect()
            }
        }
        val adapter = Blm?.adapter ?: return
        intArrayOf(4, BluetoothProfile.A2DP).forEach { profileId ->
            adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                @SuppressLint("DiscouragedPrivateApi")
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    runCatching {
                        val method = proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                        method.isAccessible = true
                        method.invoke(proxy, device)
                    }
                    mainHandler.postDelayed({ adapter.closeProfileProxy(profile, proxy) }, 1000)
                }
                override fun onServiceDisconnected(profile: Int) {}
            }, profileId)
        }
    }

    val bluetoothReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        device?.let {
                            addDiscoveredDevice(it)
                            maybeAutoConnectRemote(it)
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        if (scanTimeoutJob == null) isScanning = false
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                        if (state == BluetoothDevice.BOND_BONDED) {
                            updatePairedDevices()
                            if (pendingBleConnectAddress == device?.address) {
                                showBlePairingTip = true
                                mainHandler.postDelayed({ showBlePairingTip = false }, 2200)
                                device?.let { connectDevice(it) }
                            } else {
                                device?.let { maybeAutoConnectRemote(it) }
                            }
                        }
                    }
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        markConnected(device)
                        updatePairedDevices()
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        val wasManualDisconnect = device?.address == manualDisconnectAddress
                        if (wasManualDisconnect) {
                            manualDisconnectAddress = null
                        }
                        markDisconnected(device)
                        updatePairedDevices()
                        if (!wasManualDisconnect && isChecked) {
                            device?.let(::maybeAutoConnectRemote)
                        }
                    }
                }
            }
        }
    }

    fun startScan() {
        discoveredClassicDevices.clear()
        isScanning = true
        Blm?.adapter?.let { adapter ->
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            adapter.startDiscovery()
            adapter.bluetoothLeScanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), bleScanCallback)
        }
        scanTimeoutJob?.cancel()
        scanTimeoutJob = scope.launch {
            delay(8_000)
            stopScan()
        }
    }

    DisposableEffect(context) {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        context.registerReceiver(bluetoothReceiver, filter)
        onDispose {
            stopScan()
            context.unregisterReceiver(bluetoothReceiver)
        }
    }

    LaunchedEffect(isChecked) {
        if (isChecked) {
            btname = Blm?.adapter?.name ?: ""
            updatePairedDevices()
            updateConnectedDevice()
            pairedDevices
                .filter { isLikelyRemote(it) && !isDeviceConnectedNow(it) }
                .forEach(::maybeAutoConnectRemote)
            startScan()
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(color = colorResource(R.color.cardcolor))
            .clip(RoundedCornerShape(18.dp))
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(30.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = colorResource(R.color.cardcolor))
        ) {
            Row(
                modifier = Modifier
                    .background(Color.White)
                    .padding(horizontal = 24.dp)

                    .height(70.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text("蓝牙开关", fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                Switch(
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


            Spacer(modifier = Modifier.height(16.dp))

            if (isChecked) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(9.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .height(70.dp)
                            .clickable { showRenameDialog = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("本机蓝牙名称", fontSize = 16.sp)
                        Spacer(Modifier.weight(1f))
                        Text(btname.ifEmpty { "N/A" }, color = colorResource(R.color.textgray))
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_right),
                            contentDescription = null,
                            tint = Color.Gray
                        )
                    }
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .height(70.dp)
                            .clickable(onClick = {
                                showBleRemoteDialog = true
                                startScan()
                            }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("蓝牙遥控器", fontSize = 16.sp)
                        Spacer(Modifier.weight(1f))
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_right),
                            contentDescription = null,
                            tint = Color.Gray
                        )
                    }
                }

                val myDevices = (pairedDevices + listOfNotNull(connectedDevice, bleConnectedDevice))
                    .filter(::hasDisplayableName)
                    .distinctBy { it.address }

                if (myDevices.isNotEmpty()) {
                    Text(
                        "我的设备",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                    ) {
                        Column {
                            myDevices.forEach { device ->
                                Log.d("address", device.address + ":" + connectedDevice?.address)
                                val isConnected = isDeviceConnectedNow(device)
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
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    when {
                        isScanning -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp), contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        discoveredClassicDevices.isEmpty() -> {
                            Text(
                                "No new devices found.",
                                modifier = Modifier.padding(16.dp),
                                color = Color.Gray
                            )
                        }

                        else -> {
                            Column {
                                discoveredClassicDevices.forEach { device ->
                                    Row(
                                        modifier = Modifier
                                            .clickable {
                                                pendingBleConnectAddress = device.address
                                                pendingPairDevice = device
                                                pairRequestCode = null
                                                showPairDialog = true
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
                                        Text("点击配对", color = Color.Gray, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBleRemoteDialog ) {
        val allDiscovered = cachedBleDevices + discoveredClassicDevices
        val remoteCandidates = allDiscovered
            .filter { isLikelyRemote(it) && hasDisplayableName(it) }
            .ifEmpty { cachedBleDevices.filter(::hasDisplayableName) }

        BleRemoteDialog(
            devices = remoteCandidates,
            isScanning = isScanning,
            pairingSuccessTipVisible = showBlePairingTip,
            displayName = ::displayDeviceName,
            connectingAddress = bleConnectingAddress,
            connectedAddress = if (bleConnectedDevice != null) bleConnectedDevice?.address else null,
            onPairRequest = { device -> connectDevice(device) },
            onRefresh = { startScan() },
            onDismiss = { showBleRemoteDialog = false }
        )
    }

    if (showPairDialog && pendingPairDevice != null) {
        PairConfirmDialog(
            deviceName = pendingPairDevice?.let { displayDeviceName(it) } ?: "未知设备",
            pairingCode = pairRequestCode,
            onDismiss = {
                pendingBleConnectAddress = null
                pendingPairDevice = null
                pairRequestCode = null
                showPairDialog = false
            },
            onConfirm = {
                val target = pendingPairDevice
                if (target != null) {
                    stopScan()
                    if (!pairRequestCode.isNullOrBlank()) {
                        runCatching { target.setPairingConfirmation(true) }
                    }
                    if (target.bondState == BluetoothDevice.BOND_NONE) {
                        val started = target.createBond()
                        if (!started) {
                            Toast.makeText(context, "发起配对失败，请重试", Toast.LENGTH_SHORT).show()
                            pendingBleConnectAddress = null
                        }
                    } else if (target.bondState == BluetoothDevice.BOND_BONDED) {
                        connectDevice(target)
                    }
                }
                showPairDialog = false
            }
        )
    }

    if (showDeviceOptionsDialog && selectedDevice != null) {
        val device = selectedDevice!!
        val isConnected = isDeviceConnectedNow(device)
        ConnectedDeviceOptionsDialog(
            deviceName = displayDeviceName(device),
            isConnected = isConnected,
            onDismiss = {
                showDeviceOptionsDialog = false
                selectedDevice = null
            },
            onConnectDisconnect = {

                if (isConnected) {
                    disconnectDevice(device)
                } else {
                    connectDevice(device)
                }
                showDeviceOptionsDialog = false
                selectedDevice = null
            },
            onForget = {
                if (isConnected) {
                    disconnectDevice(device)
                }
                val removed = removeBondCompat(device)
                if (!removed) {
                    Toast.makeText(context, "忽略失败，请重试", Toast.LENGTH_SHORT).show()
                }
                markDisconnected(device)
                updatePairedDevices()
                showDeviceOptionsDialog = false
                selectedDevice = null
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
                Text("修改名称", fontSize = 18.sp, color = Color.Black, fontWeight = FontWeight.Bold)
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
    displayName: (BluetoothDevice) -> String,
    connectingAddress: String?,
    connectedAddress: String?,
    onPairRequest: (BluetoothDevice) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) {
        onRefresh()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
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
                    Text("添加蓝牙遥控器", fontSize = titleSize, fontWeight = FontWeight.Bold, color = Color(0xFF1D2027))
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(40.dp))
                }

                Spacer(modifier = Modifier.height(if (compact) 20.dp else 34.dp))

                if (compact) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("遥控器蓝牙配对", fontSize = headingSize, fontWeight = FontWeight.Bold, color = Color(0xFF181C23))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("安装电池后，请将遥控器接近设备。", fontSize = bodySize, color = Color(0xFF1F242C))
                        Text("同时按住“桌面”和“菜单”键 3 秒以上。", fontSize = bodySize, color = Color(0xFF1F242C))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("已配对的遥控器可长按“桌面”和“菜单”键重新配对。", fontSize = tipsSize, color = Color(0xFF2F343D))
                        Spacer(modifier = Modifier.height(18.dp))
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(id = R.drawable.lanya),
                                contentDescription = "蓝牙遥控器配对示意图"
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 40.dp, top = 30.dp)
                        ) {
                            Text("遥控器蓝牙配对", fontSize = headingSize, fontWeight = FontWeight.Bold, color = Color(0xFF181C23))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("安装电池后，请将遥控器接近设备。", fontSize = bodySize, color = Color(0xFF1F242C))
                            Text("同时按住“桌面”和“菜单”键 3 秒以上。", fontSize = bodySize, color = Color(0xFF1F242C))
                            Spacer(modifier = Modifier.height(52.dp))
                            Text("已配对的遥控器可长按“桌面”和“菜单”键重新配对。", fontSize = tipsSize, color = Color(0xFF2F343D))
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

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = listPadding),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    when {
                        isScanning && devices.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
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
                            Column {
                                devices.forEach { device ->
                                    val stateText = when (device.address) {
                                        connectedAddress -> "已连接"
                                        connectingAddress -> "连接中..."
                                        else -> "点击配对"
                                    }
                                    val enabled = device.address != connectingAddress
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = enabled) { onPairRequest(device) }
                                            .padding(horizontal = 24.dp, vertical = if (compact) 14.dp else 20.dp),
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
                                            color = if (stateText == "已连接") Color(0xFF4577FF) else Color(0xFF6C7482)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (pairingSuccessTipVisible) {
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
                            "配对成功，正在发起连接...",
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
fun PairConfirmDialog(
    deviceName: String,
    pairingCode: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.width(560.dp),
            shape = RoundedCornerShape(36.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("蓝牙配对请求", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10131A))
                Spacer(modifier = Modifier.height(26.dp))
                if (!pairingCode.isNullOrBlank()) {
                    Text("密码：$pairingCode", fontSize = 24.sp, color = Color(0xFF1A1D24))
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Text("是否与${deviceName}配对？", fontSize = 22.sp, color = Color(0xFF1A1D24))
                Spacer(modifier = Modifier.height(36.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(70.dp),
                        shape = RoundedCornerShape(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8EBF1))
                    ) {
                        Text("取消", color = Color(0xFF4A78F0), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(70.dp),
                        shape = RoundedCornerShape(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4577FF))
                    ) {
                        Text("确定", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
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
    onDismiss: () -> Unit,
    onConnectDisconnect: () -> Unit,
    onForget: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.width(860.dp),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F4F4))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 44.dp, vertical = 28.dp),
            ) {
                Text(
                    text = deviceName,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(26.dp))
                Card(
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEDEDEE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConnectDisconnect() }
                                .padding(horizontal = 54.dp, vertical = 34.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (isConnected) "取消连接" else "连接设备", fontSize = 50.sp, color = Color(0xFF22252C), fontWeight = FontWeight.Bold)
                        }
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0xFFD6D8DC))
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onForget() }
                                .padding(horizontal = 54.dp, vertical = 34.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("忽略此设备", fontSize = 50.sp, color = Color(0xFF22252C), fontWeight = FontWeight.Bold)
                        }
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
