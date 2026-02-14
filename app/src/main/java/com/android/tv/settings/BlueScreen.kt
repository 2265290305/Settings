package com.android.tv.settings

import android.annotation.SuppressLint
import android.app.Service
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
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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

    val Blm = if (LocalInspectionMode.current) {
        null
    } else {
        context.getSystemService(Service.BLUETOOTH_SERVICE) as BluetoothManager
    }
    var isChecked by remember { mutableStateOf(Blm?.adapter?.isEnabled ?: true) }

    val pairedDevices = remember { mutableStateListOf<BluetoothDevice>() }
    val discoveredClassicDevices = remember { mutableStateListOf<BluetoothDevice>() }
    val cachedBleDevices = remember { mutableStateListOf<BluetoothDevice>() }
    val deviceNameCache = remember { mutableStateMapOf<String, String>() }
    var connectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    val connectedAddresses = remember { mutableStateListOf<String>() }

    fun cacheDeviceName(device: BluetoothDevice) {
        val name = device.name?.trim()
        if (!name.isNullOrEmpty()) {
            deviceNameCache[device.address] = name
        }
    }

    fun displayDeviceName(device: BluetoothDevice): String {
        return deviceNameCache[device.address]
            ?: device.name
            ?: device.address
    }

    fun isDeviceConnectedNow(device: BluetoothDevice): Boolean {
        val address = device.address
        if (connectedAddresses.contains(address)) return true
        if (connectedDevice?.address == address) return true
        if (bleConnectedDevice?.address == address) return true

        val manager = Blm ?: return false
        val profiles = intArrayOf(
            BluetoothProfile.A2DP,
            BluetoothProfile.HEADSET,
            BluetoothProfile.GATT,
            4 // HID_HOST
        )
        return profiles.any { profile ->
            runCatching {
                manager.getConnectedDevices(profile).any { it.address == address }
            }.getOrDefault(false)
        }
    }

    fun markConnected(device: BluetoothDevice?) {
        val address = device?.address ?: return
        if (!connectedAddresses.contains(address)) {
            connectedAddresses.add(address)
        }
    }

    fun markDisconnected(device: BluetoothDevice?) {
        val address = device?.address ?: return
        connectedAddresses.remove(address)
    }

    fun isBleDevice(device: BluetoothDevice): Boolean {
        return device.type == BluetoothDevice.DEVICE_TYPE_LE ||
            device.type == BluetoothDevice.DEVICE_TYPE_DUAL
    }

    fun isLikelyBleRemote(device: BluetoothDevice): Boolean {
        if (!isBleDevice(device)) return false
        val display = displayDeviceName(device).lowercase()
        val remoteKeywords = listOf("remote", "remoter", "controller", "rc", "遥控", "蓝牙遥控", "电信蓝牙遥控")
        return remoteKeywords.any { display.contains(it) }
    }

    fun addClassicDevice(device: BluetoothDevice) {
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
            pairedDevices.addAll(it)
        }
    }

    fun updateConnectedDevice() {
        Blm?.adapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.A2DP) {
                    val devices = proxy.connectedDevices
                    if (devices.isNotEmpty()) {
                        connectedDevice = devices[0]
                        cacheDeviceName(devices[0])
                        markConnected(devices[0])
                    }
                    Blm.adapter.closeProfileProxy(profile, proxy)
                }
            }

            override fun onServiceDisconnected(profile: Int) {}
        }, BluetoothProfile.A2DP)
    }

    val bleGattCallback = remember {
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                mainHandler.post {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            bleConnectedDevice = gatt.device
                            bleConnectingAddress = null
                            markConnected(gatt.device)
                            gatt.discoverServices()
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            if (bleConnectedDevice?.address == gatt.device.address) {
                                bleConnectedDevice = null
                            }
                            if (bleConnectingAddress == gatt.device.address) {
                                bleConnectingAddress = null
                            }
                            markDisconnected(gatt.device)
                            if (activeGatt === gatt) {
                                activeGatt = null
                            }
                            gatt.close()
                        }
                    }
                }
            }
        }
    }

    val bleScanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                result.device?.let { addDiscoveredDevice(it) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { it.device?.let { device -> addDiscoveredDevice(device) } }
            }
        }
    }

    fun stopScan() {
        Blm?.adapter?.let { adapter ->
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            adapter.bluetoothLeScanner?.stopScan(bleScanCallback)
        }
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        isScanning = false
    }

    fun connectBleDevice(device: BluetoothDevice) {
        if (device.bondState == BluetoothDevice.BOND_NONE) {
            pendingBleConnectAddress = device.address
            pendingPairDevice = device
            pairRequestCode = null
            showPairDialog = true
            return
        }
        stopScan()
        bleConnectingAddress = device.address
        bleConnectedDevice = null
        activeGatt?.close()
        activeGatt = null
        activeGatt = device.connectGatt(context, false, bleGattCallback)
    }

    val bluetoothReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                btname = Blm?.adapter?.name ?: ""

                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        device?.let {
                            addDiscoveredDevice(it)
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        if (scanTimeoutJob == null) {
                            isScanning = false
                        }
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                            BluetoothDevice.BOND_BONDED -> {
                                Toast.makeText(context, "Paired with ${device?.name}", Toast.LENGTH_SHORT).show()
                                device?.let {
                                    discoveredClassicDevices.removeAll { it.address == device.address }
                                    cachedBleDevices.removeAll { it.address == device.address }
                                }
                                if (pendingPairDevice?.address == device?.address) {
                                    pendingPairDevice = null
                                    pairRequestCode = null
                                    showPairDialog = false
                                }
                                if (pendingBleConnectAddress == device?.address) {
                                    showBlePairingTip = true
                                    mainHandler.postDelayed({ showBlePairingTip = false }, 2200)
                                    pendingBleConnectAddress = null
                                    device?.let { connectBleDevice(it) }
                                }
                                updatePairedDevices()
                            }
                            BluetoothDevice.BOND_NONE -> {
                                Toast.makeText(context, "Pairing failed with ${device?.name}", Toast.LENGTH_SHORT).show()
                                if (pendingPairDevice?.address == device?.address) {
                                    pendingPairDevice = null
                                    pairRequestCode = null
                                    showPairDialog = false
                                }
                                if (pendingBleConnectAddress == device?.address) {
                                    pendingBleConnectAddress = null
                                }
                                updatePairedDevices()
                            }
                        }
                    }
                    BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        if (device != null && !isLikelyBleRemote(device)) return
                        val pairingKey = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, -1)
                        pendingPairDevice = device
                        pendingBleConnectAddress = device?.address
                        pairRequestCode = if (pairingKey >= 0) pairingKey.toString() else null
                        showPairDialog = true
                    }
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        connectedDevice = device
                        device?.let { cacheDeviceName(it) }
                        markConnected(device)
                        updatePairedDevices()
                        updateConnectedDevice()
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        if (connectedDevice?.address == device?.address) {
                            connectedDevice = null
                        }
                        markDisconnected(device)
                        updatePairedDevices()
                    }
                }
            }
        }
    }

    fun startScan() {
        discoveredClassicDevices.clear()
        isScanning = true
        Blm?.adapter?.let { adapter ->
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            adapter.bluetoothLeScanner?.stopScan(bleScanCallback)
            adapter.startDiscovery()
            adapter.bluetoothLeScanner?.startScan(
                null,
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build(),
                bleScanCallback
            )
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
            addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        context.registerReceiver(bluetoothReceiver, filter)
        onDispose {
            stopScan()
            activeGatt?.close()
            activeGatt = null
            context.unregisterReceiver(bluetoothReceiver)
        }
    }

    LaunchedEffect(isChecked) {
        if (isChecked) {
            btname = Blm?.adapter?.name ?: ""
            updatePairedDevices()
            updateConnectedDevice()
            startScan()
        } else {
            stopScan()
            activeGatt?.close()
            activeGatt = null
            bleConnectedDevice = null
            bleConnectingAddress = null
            connectedAddresses.clear()
            pairedDevices.clear()
            discoveredClassicDevices.clear()
            cachedBleDevices.clear()
            deviceNameCache.clear()
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
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .background(color = Color.Unspecified, shape = RectangleShape)
            .verticalScroll(rememberScrollState()),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(9.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
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
                    Icon(painter = painterResource(id = R.drawable.arrow_right), contentDescription = null, tint = Color.Gray)
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
                    Icon(painter = painterResource(id = R.drawable.arrow_right), contentDescription = null, tint = Color.Gray)
                }
            }

            val myDevices = (pairedDevices + listOfNotNull(connectedDevice, bleConnectedDevice))
                .distinctBy { it.address }

            if (myDevices.isNotEmpty()) {
                Text("我的设备", fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Column {
                        myDevices.forEach { device ->
                            Log.d("address",device.address+":"+connectedDevice?.address)
                            val isConnected = isDeviceConnectedNow(device)
                            Row(
                                modifier = Modifier
                                    .clickable { /* TODO: Navigate to detail screen */ }
                                    .padding(horizontal = 16.dp, vertical = 20.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(painter = painterResource(R.drawable.bluetooth), contentDescription = "Bluetooth")
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(displayDeviceName(device), fontSize = 16.sp)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    if (isConnected) "已连接" else "未连接",
                                    color = if (isConnected) Color(0xFF4577FF) else Color.Gray,
                                    fontSize = 14.sp
                                )
                                Icon(painter = painterResource(id = R.drawable.ic_info), contentDescription = "Info", tint = if (isConnected) Color(0xFF4577FF) else Color.Gray)
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
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    discoveredClassicDevices.isEmpty() -> {
                         Text("No new devices found.", modifier = Modifier.padding(16.dp), color = Color.Gray)
                    }
                    else -> {
                        Column {
                            discoveredClassicDevices.forEach { device ->
                            Row(
                                modifier = Modifier
                                    .clickable {
                                        pendingPairDevice = device
                                        pairRequestCode = null
                                        showPairDialog = true
                                    }
                                    .padding(horizontal = 16.dp, vertical = 20.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                    Icon(painter = painterResource(R.drawable.bluetooth), contentDescription = "Bluetooth")
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

    if (showBleRemoteDialog) {
        BleRemoteDialog(
            devices = cachedBleDevices.filter { isLikelyBleRemote(it) },
            isScanning = isScanning,
            pairingSuccessTipVisible = showBlePairingTip,
            displayName = ::displayDeviceName,
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
                    target.setPairingConfirmation(true)
                    if (target.bondState != BluetoothDevice.BOND_BONDING && target.bondState != BluetoothDevice.BOND_BONDED) {
                        target.createBond()
                    }
                }
                showPairDialog = false
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFD8DBDF))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 48.dp, vertical = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.back),
                        contentDescription = "返回",
                        modifier = Modifier.clickable { onDismiss() }
                    )
                    Spacer(Modifier.weight(1f))
                    Text("添加蓝牙遥控器", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D2027))
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(40.dp))
                }

                Spacer(modifier = Modifier.height(40.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 80.dp, top = 80.dp)
                    ) {
                        Text("遥控器蓝牙配对", fontSize = 58.sp, fontWeight = FontWeight.Bold, color = Color(0xFF181C23))
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("安装电池后", fontSize = 44.sp, color = Color(0xFF1F242C))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("请将遥控器接近设备，", fontSize = 44.sp, color = Color(0xFF1F242C))
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("同时按住 ", fontSize = 44.sp, color = Color(0xFF1F242C))
                            Text("桌面", fontSize = 44.sp, color = Color(0xFF4A7BFF), fontWeight = FontWeight.Bold)
                            Text(" 和 ", fontSize = 44.sp, color = Color(0xFF1F242C))
                            Text("菜单", fontSize = 44.sp, color = Color(0xFF4A7BFF), fontWeight = FontWeight.Bold)
                            Text(" 键3秒以上", fontSize = 44.sp, color = Color(0xFF1F242C))
                        }

                        Spacer(modifier = Modifier.height(120.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("已配对的遥控器可长按 ", fontSize = 34.sp, color = Color(0xFF2F343D))
                            Text("桌面", fontSize = 34.sp, color = Color(0xFF4A7BFF), fontWeight = FontWeight.Bold)
                            Text(" 和 ", fontSize = 34.sp, color = Color(0xFF2F343D))
                            Text("菜单", fontSize = 34.sp, color = Color(0xFF4A7BFF), fontWeight = FontWeight.Bold)
                            Text(" 键接触配对", fontSize = 34.sp, color = Color(0xFF2F343D))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.lanya),
                            contentDescription = "蓝牙遥控器配对示意图"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))
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
                        modifier = Modifier.width(560.dp)
                    ) {
                        Text(
                            "配对成功，正在发起连接...",
                            modifier = Modifier.padding(horizontal = 34.dp, vertical = 20.dp),
                            fontSize = 24.sp,
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

@Preview(showBackground = true)
@Composable
fun BlueToothScreenPreview() {
    BlueToothScreen(navController = rememberNavController())
}
