package com.android.tv.settings

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.android.tv.settings.ui.theme.设置Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MIN_SAVED_WIFI_LOADING_MS = 800L
private const val MIN_AVAILABLE_WIFI_LOADING_MS = 1200L
private const val WIFI_SCAN_POLL_INTERVAL_MS = 400L
private const val WIFI_SCAN_TIMEOUT_MS = 6000L


@SuppressLint("MissingPermission")
@Composable
fun WifiManagerScreen(modifier: Modifier = Modifier, navController: NavController) {
    val context = LocalContext.current
    val wifiManager = if (LocalInspectionMode.current) null else context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val connectivityManager = if (LocalInspectionMode.current) null else context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    var isChecked by remember { mutableStateOf(wifiManager?.isWifiEnabled ?: true) }
    var rawScanResults by remember { mutableStateOf<List<ScanResult>>(emptyList()) }
    var rawSavedNetworks by remember { mutableStateOf<List<WifiConfiguration>>(emptyList()) }
    var isSavedNetworksLoading by remember { mutableStateOf(false) }
    // Wi-Fi 已开时进页即显示骨架，覆盖进场动画 + 首次扫描等待，避免列表区先空白再出现。
    var isAvailableNetworksLoading by remember { mutableStateOf(wifiManager?.isWifiEnabled == true) }
    var connectedSsid by remember { mutableStateOf<String?>(null) }
    var connectedRssi by remember { mutableStateOf<Int?>(null) }
    var scanStartedAtMs by remember { mutableStateOf(0L) }
    var loadingSession by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    // 仅读取当前缓存的扫描结果/已保存网络，不负责 loading 状态。
    suspend fun readWifiSnapshot() {
        val (scanResults, savedNetworks, rssi) = withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            val scans = wifiManager?.scanResults
                ?.filter { it.SSID.normalizedWifiSsid().isNotEmpty() }
                ?: emptyList()
            val saved = wifiManager?.configuredNetworks
                ?.filter { it.SSID.normalizedWifiSsid().isNotEmpty() }
                ?: emptyList()
            val signal = wifiManager?.connectionInfo?.rssi
            Triple(scans, saved, signal)
        }
        rawScanResults = scanResults
        rawSavedNetworks = savedNetworks
        connectedRssi = if (rssi == null || rssi == -127) null else rssi
    }

    fun requestWifiScan() {
        if (!isChecked || wifiManager == null) return
        loadingSession += 1
        scanStartedAtMs = SystemClock.elapsedRealtime()
        isSavedNetworksLoading = true
        isAvailableNetworksLoading = true
        val session = loadingSession
        scope.launch {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                wifiManager.startScan()
            }
            // startScan() 是异步的，且在 Android 8/9 之后经常被系统节流：被节流时它返回
            // false 且不会发送 SCAN_RESULTS_AVAILABLE 广播。因此不能只在调用后读一次缓存，
            // 否则首次开启 Wi-Fi（缓存为空）+ 被节流时就会一直扫描不到网络。
            // 这里在扫描窗口内轮询缓存结果，直到拿到网络或超时，确保即使没有广播也能补到
            // 系统后台扫描的结果。
            while (session == loadingSession && isChecked) {
                readWifiSnapshot()
                val elapsed = SystemClock.elapsedRealtime() - scanStartedAtMs
                if (elapsed >= MIN_SAVED_WIFI_LOADING_MS) {
                    isSavedNetworksLoading = false
                }
                val hasResults = rawScanResults.isNotEmpty()
                if (elapsed >= MIN_AVAILABLE_WIFI_LOADING_MS &&
                    (hasResults || elapsed >= WIFI_SCAN_TIMEOUT_MS)
                ) {
                    isAvailableNetworksLoading = false
                    break
                }
                delay(WIFI_SCAN_POLL_INTERVAL_MS)
            }
        }
    }

    val wifiScanReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    // 广播到达时刷新一次快照（用于非主动扫描时的后台结果更新）。
                    scope.launch { readWifiSnapshot() }
                }
            }
        }
    }

    // Process raw lists to get clean lists for UI
    var (savedNetworks, availableNetworks, scanBestLevelBySsid) = remember(rawSavedNetworks, rawScanResults, wifiManager) {
        val bestBySsid: Map<String, Int> = rawScanResults
            .filter { it.SSID.normalizedWifiSsid().isNotEmpty() }
            .groupBy { it.SSID.normalizedWifiSsid() }
            .mapNotNull { (ssid, group) ->
                group.maxOfOrNull { it.level }?.let { level -> ssid to level }
            }
            .toMap()

        // 我的网络：仅显示附近(扫描可见)的已保存网络，并按信号强度降序排序。
        val nearbySaved = rawSavedNetworks
            .distinctBy { it.SSID.normalizedWifiSsid() }
            .mapNotNull { cfg ->
                val ssid = cfg.SSID.normalizedWifiSsid()
                if (ssid.isEmpty()) return@mapNotNull null
                val level = bestBySsid[ssid] ?: return@mapNotNull null
                cfg to level
            }
            .sortedByDescending { it.second }
            .map { it.first }

        val nearbySavedSsids = nearbySaved.map { it.SSID.normalizedWifiSsid() }.toSet()

        // 可用网络：去除已保存网络后按信号强度降序。
        val available = rawScanResults
            .filter {
                val ssid = it.SSID.normalizedWifiSsid()
                ssid.isNotEmpty() && ssid !in nearbySavedSsids
            }
            .groupBy { it.SSID.normalizedWifiSsid() }
            .mapNotNull { (_, group) -> group.maxByOrNull { it.level } }
            .sortedByDescending { it.level }

        Triple(nearbySaved, available, bestBySsid)
    }




    val networkCallback = remember {
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val networkCapabilities = connectivityManager?.getNetworkCapabilities(network)
                if (networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    @Suppress("DEPRECATION")
                    val wifiInfo = wifiManager?.connectionInfo
                    connectedSsid = wifiInfo?.ssid
                    val rssi = wifiInfo?.rssi
                    connectedRssi = if (rssi == null || rssi == -127) null else rssi
                }
            }

            override fun onLost(network: Network) {
                connectedSsid = null
                connectedRssi = null
            }
        }
    }

    LaunchedEffect(isChecked) {
        if (isChecked) {
            delay(420)
            requestWifiScan()
        }
    }

    DisposableEffect(Unit) {
        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(wifiScanReceiver, intentFilter)

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager?.registerNetworkCallback(networkRequest, networkCallback)

        onDispose {
            runCatching { context.unregisterReceiver(wifiScanReceiver) }
            runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
        }
    }


    Column(
        modifier = modifier
            .fillMaxSize()

            .background(colorResource(R.color.topbar))
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = colorResource(R.color.cardcolor))
        ) {

            Row(
                modifier = Modifier
                    .padding(30.dp)
                    .height(78.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color = Color.White)
                    .fillMaxHeight(1f),
                verticalAlignment = Alignment.CenterVertically,

                ) {
                Spacer(Modifier.width(24.dp))
                Text("无线网络", fontSize = 20.sp)
                Spacer(Modifier.weight(1f))

                Switch(
                    modifier = Modifier.entryFocus(),
                    colors = SwitchDefaults.colors(
                        uncheckedTrackColor = colorResource(R.color.gray),
                        uncheckedThumbColor = colorResource(R.color.white),
                        checkedThumbColor = colorResource(R.color.white),
                        checkedTrackColor = colorResource(R.color.theme_blue)
                    ),
                    checked = isChecked,
                    onCheckedChange = { newCheckedState ->
                        isChecked = newCheckedState
                        if (!newCheckedState) {
                            isSavedNetworksLoading = false
                            isAvailableNetworksLoading = false
                        }
                        @Suppress("DEPRECATION")
                        wifiManager?.setWifiEnabled(newCheckedState)
                    }
                )
                Spacer(Modifier.width(24.dp))
            }


            if (isChecked) {
                if (LocalInspectionMode.current) {
                    savedNetworks = listOf(
                        fakeWifiConfig(ssid = "123", isWpa2 = true),
                        fakeWifiConfig(ssid = "1235", isWpa2 = true)
                    )
                }
                if ((isSavedNetworksLoading && !LocalInspectionMode.current) || savedNetworks.isNotEmpty()) {
                    Row(Modifier.padding(start = 30.dp)) {

                        Text(
                            "我的网络",
                            fontSize = 20.sp,
                            modifier = Modifier.padding( vertical = 12.dp)
                        )
                    }

                    if (isSavedNetworksLoading && !LocalInspectionMode.current) {
                        SettingsLoadingIndicator(
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                            savedNetworks.forEach { network ->
                                val savedSsid = network.SSID.normalizedWifiSsid()
                                if (savedSsid.isEmpty()) {
                                    return@forEach
                                }
                                val isConnected = connectedSsid.normalizedWifiSsid() == savedSsid
                                val signalLevel = scanBestLevelBySsid[savedSsid]
                                    ?: if (isConnected) connectedRssi else null
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 30.dp)
                                        .clickable {
                                            navController.navigate(
                                                Destinations.WifiDetailScreen.createRoute(savedSsid)
                                            )
                                        }

                                        .clip(RoundedCornerShape(10.dp))
                                        .height(60.dp)
                                        .background(Color.White)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Spacer(modifier = Modifier.width(24.dp))
                                    Icon(
                                        modifier = Modifier.size(20.dp),
                                        painter = painterResource(
                                            wifiSignalIconRes(
                                                signalLevel,
                                                isConnected
                                            )
                                        ),
                                        contentDescription = "wifi_signal"
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(savedSsid, fontSize = 20.sp)
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        if (isConnected) "已连接" else "已保存",
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
                        .padding(horizontal = 30.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("可用Wi-Fi网络", fontSize = 20.sp)
                    TextButton(onClick = {
                        requestWifiScan()
                    }) {
                        Text("刷新", color = Color(0xFF4577FF))
                        //Icon(painter = painterResource(R.drawable.refresh), contentDescription = "刷新", tint = Color(0xFF4577FF))
                    }
                }
                /*
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                //colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {

             */
                if (isAvailableNetworksLoading && !LocalInspectionMode.current) {
                    SettingsLoadingIndicator(
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                } else {
                    if (LocalInspectionMode.current) {
                        availableNetworks = listOf(
                            fakeScanResult(ssid = "123", level = 10),
                            fakeScanResult(ssid = "1234", level = 10)
                        )
                    }
                    Column(modifier = Modifier.padding(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        availableNetworks.forEach { result ->
                            val availableSsid = result.SSID.normalizedWifiSsid()
                            if (availableSsid.isEmpty()) {
                                return@forEach
                            }
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 30.dp)
                                    .clickable {
                                        navController.navigate(
                                            Destinations.WifiConnectScreen.createRoute(
                                                availableSsid,
                                                wifiSecurityFromCapabilities(result.capabilities)
                                            )
                                        )
                                    }

                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .height(60.dp)
                                    .background(Color.White),

                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Spacer(modifier = Modifier.width(24.dp))
                                Icon(
                                    modifier = Modifier.size(20.dp),
                                    painter = painterResource(
                                        wifiSignalIconRes(
                                            result.level,
                                            isConnected = false
                                        )
                                    ),
                                    contentDescription = "wifi_signal"
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(availableSsid, fontSize = 20.sp)
                                Spacer(Modifier.weight(1f))
                                if (result.capabilities.contains("WEP") || result.capabilities.contains(
                                        "WPA"
                                    )
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.lock),
                                        contentDescription = "Secured"
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))

                        Row(
                            modifier = Modifier
                                .padding(horizontal = 30.dp)
                                .clickable { navController.navigate(Destinations.AddWifiScreen.route) }
                                .clip(RoundedCornerShape(10.dp))

                                .fillMaxWidth()
                                .height(78.dp)
                                .background(Color.White),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(modifier = Modifier.width(24.dp))
                            Text("手动添加Wi-Fi网络", fontSize = 20.sp)
                            Spacer(Modifier.weight(1f))
                            Icon(
                                painter = painterResource(id = R.drawable.arrow_right),
                                contentDescription = null,
                                tint = Color.Gray
                            )
                        }

                    }
                }
                // }
            }
        }
    }
}

private fun wifiSignalIconRes(rssi: Int?, isConnected: Boolean): Int {
    val level = when {
        rssi == null -> 1
        rssi >= -50 -> 4
        rssi >= -60 -> 3
        rssi >= -75 -> 2
        else -> 1
    }
    return when (level) {
        4 -> if (isConnected) R.drawable.wifib4 else R.drawable.wifi4
        3 -> if (isConnected) R.drawable.wifib3 else R.drawable.wifi3
        2 -> if (isConnected) R.drawable.wifib2 else R.drawable.wifi2
        else -> if (isConnected) R.drawable.wifib1 else R.drawable.wifi1
    }
}

@Preview(
    name = "993dp x 851dp",
    showBackground = true,
    widthDp = 993,
    heightDp = 851
)
@Preview(showBackground = true)
@Composable
fun WifiListPreview() {
    设置Theme {
        WifiManagerScreen(navController = rememberNavController())
    }
}
