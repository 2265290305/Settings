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
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.android.tv.settings.ui.theme.设置Theme

fun fakeScanResult(
    ssid: String,
    bssid: String,
    level: Int,
    capabilities: String,
    frequency: Int
): ScanResult {
    val ctor = ScanResult::class.java
        .getDeclaredConstructor()
        .apply { isAccessible = true }

    val sr = ctor.newInstance()

    sr.SSID = ssid
    sr.BSSID = bssid
    sr.level = level
    sr.capabilities = capabilities
    sr.frequency = frequency

    return sr
}


var previewScanResults = emptyList<ScanResult>()

@SuppressLint("MissingPermission")
@Composable
fun WifiManagerScreen(modifier: Modifier = Modifier, navController: NavController) {
    val clipboardManager =
        LocalClipboardManager.current as androidx.compose.ui.platform.ClipboardManager

    val context = LocalContext.current
    val wifiManager =
        if (LocalInspectionMode.current) null else context.applicationContext.getSystemService(
            Context.WIFI_SERVICE
        ) as WifiManager
    val connectivityManager =
        if (LocalInspectionMode.current) null else context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    var isChecked by remember { mutableStateOf(wifiManager?.isWifiEnabled ?: true) }
    var scanResults by remember { mutableStateOf<List<ScanResult>>(emptyList()) }
    var savedNetworks by remember { mutableStateOf<List<WifiConfiguration>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var connectedSsid by remember { mutableStateOf<String?>(null) }

    val wifiScanReceiver = remember {
        object : BroadcastReceiver() {
            @Suppress("DEPRECATION")
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    scanResults =
                        wifiManager?.scanResults?.filter { it.SSID.isNotBlank() } ?: emptyList()
                    savedNetworks = wifiManager?.configuredNetworks?.filter { it.SSID.isNotBlank() }
                        ?: emptyList()
                    connectedSsid = wifiManager?.connectionInfo?.ssid
                    Log.d("wifis", wifiManager?.configuredNetworks.toString())
                    isScanning = false
                }
            }
        }
    }

    var (inRangeSavedNetworks, otherAvailableNetworks) = remember(savedNetworks, scanResults) {

        val inRange = savedNetworks
            .filter { savedNet ->
                scanResults.any { scan ->
                    scan.SSID == savedNet.SSID.trim('"')
                    scan.SSID != connectedSsid
                }
            }
            .distinctBy { it.SSID.trim('"') }

        val others = scanResults.filter { it.SSID.isNotBlank() }
            .groupBy { it.SSID }
            .map { (_, list) ->
                list.maxBy { it.level }   // 取信号最强的
            }
        inRange to others
    }
    if (previewScanResults.isNotEmpty()) {
        otherAvailableNetworks = remember { previewScanResults }
        isScanning = false
    }

    val networkCallback = remember {
        object : ConnectivityManager.NetworkCallback() {
            @SuppressLint("MissingPermission")
            override fun onAvailable(network: Network) {
                val networkCapabilities = connectivityManager?.getNetworkCapabilities(network)
                if (networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    @Suppress("DEPRECATION")
                    connectedSsid = wifiManager?.connectionInfo?.ssid
                }
            }

            @SuppressLint("MissingPermission")
            override fun onLost(network: Network) {
                @Suppress("DEPRECATION")
                connectedSsid = wifiManager?.connectionInfo?.ssid
            }
        }
    }

    LaunchedEffect(isChecked) {
        if (isChecked) {
            isScanning = true
            @Suppress("DEPRECATION")
            wifiManager?.startScan()
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
            context.unregisterReceiver(wifiScanReceiver)
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        }
    }


    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(9.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("无线网络", fontSize = 16.sp)
                Spacer(Modifier.weight(1f))

                Switch(
                    checked = isChecked,
                    onCheckedChange = { newCheckedState ->
                        isChecked = newCheckedState
                        @Suppress("DEPRECATION")
                        wifiManager?.setWifiEnabled(newCheckedState)
                    }
                )
            }
        }

        if (isChecked) {
            if (inRangeSavedNetworks.isNotEmpty()) {
                Text(
                    "我的网络",
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Column {
                        inRangeSavedNetworks.forEach { network ->
                            val isConnected = connectedSsid == "\"${network.SSID.trim('"')}\""
                            Row(
                                modifier = Modifier
                                    .clickable {

                                        navController.navigate(
                                            Destinations.WifiDetailScreen.createRoute(network.SSID)
                                        )


                                    }
                                    .padding(horizontal = 16.dp, vertical = 20.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.wifi4),
                                    contentDescription = "wifi4"
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(network.SSID.trim('"'), fontSize = 16.sp)
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
            println(otherAvailableNetworks.size)
            if (otherAvailableNetworks.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("可用Wi-Fi网络", fontSize = 16.sp)
                    TextButton(onClick = {
                        isScanning = true
                        wifiManager?.startScan()
                    }) {
                        Text("刷新", color = Color(0xFF4577FF))
                        //Icon(painter = painterResource(R.drawable.refresh), contentDescription = "刷新", tint = Color(0xFF4577FF))
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    if (isScanning) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp), contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Column {
                            otherAvailableNetworks.forEach { result ->
                                @Suppress("DEPRECATION")
                                Row(
                                    modifier = Modifier
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onLongPress = {
                                                    clipboardManager.setText(AnnotatedString(result.SSID))
                                                    // 长按触发
                                                    //onLongPress()
                                                }
                                            )
                                        }
                                        .clickable {
                                            navController.navigate(
                                                Destinations.WifiConnectScreen.createRoute(
                                                    result.SSID
                                                )
                                            )
                                        }
                                        .padding(horizontal = 16.dp, vertical = 20.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.wifi4),
                                        contentDescription = "wifi4"
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(result.SSID, fontSize = 16.sp)
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


                            Row(
                                modifier = Modifier
                                    .clickable { navController.navigate(Destinations.AddWifiScreen.route) }
                                    .padding(horizontal = 16.dp, vertical = 20.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("手动添加Wi-Fi网络", fontSize = 16.sp)
                                Spacer(Modifier.weight(1f))
                                Icon(
                                    painter = painterResource(id = R.drawable.arrow_right),
                                    contentDescription = null,
                                    tint = Color.Gray
                                )
                            }

                        }
                    }
                }
            }
        }
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
        previewScanResults = listOf(
            fakeScanResult(
                ssid = "Home_WiFi",
                bssid = "00:11:22:33:44:55",
                level = -45,
                capabilities = "[WPA2-PSK-CCMP]",
                frequency = 2412
            ),
            fakeScanResult(
                ssid = "Coffee_Free",
                bssid = "66:77:88:99:AA:BB",
                level = -70,
                capabilities = "[OPEN]",
                frequency = 2462
            ),
            fakeScanResult(
                ssid = "Office_5G",
                bssid = "CC:DD:EE:FF:00:11",
                level = -55,
                capabilities = "[WPA3-SAE-CCMP]",
                frequency = 5180
            )
        )
        WifiManagerScreen(navController = rememberNavController())
    }
}
