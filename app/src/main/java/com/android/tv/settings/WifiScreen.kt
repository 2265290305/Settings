package com.android.tv.settings

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.android.tv.settings.ui.theme.设置Theme


@SuppressLint("MissingPermission")
@Composable
fun WifiManagerScreen(modifier: Modifier = Modifier, navController: NavController) {
    val context = LocalContext.current
    val wifiManager = if (LocalInspectionMode.current) null else context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    var isChecked by remember { mutableStateOf(wifiManager?.isWifiEnabled ?: true) }
    var scanResults by remember { mutableStateOf<List<ScanResult>>(emptyList()) }

    val wifiScanReceiver = remember {
        object : BroadcastReceiver() {
            @Suppress("DEPRECATION")
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    scanResults = wifiManager?.scanResults ?: emptyList()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(wifiScanReceiver, intentFilter)
        @Suppress("DEPRECATION")
        wifiManager?.startScan()

        onDispose {
            context.unregisterReceiver(wifiScanReceiver)
        }
    }


    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(9.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp,),
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("可用Wi-Fi网络", fontSize = 16.sp)
                TextButton(onClick = { wifiManager?.startScan() }) {
                    Text("刷新", color = Color(0xFF4577FF))
                    Icon(painter = painterResource(R.drawable.refresh), contentDescription = "刷新", tint = Color(0xFF4577FF))
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                LazyColumn {

                    items(scanResults) { result ->
                        @Suppress("DEPRECATION")
                        Row(
                            modifier = Modifier
                                .clickable { }
                                .padding(horizontal = 16.dp, vertical = 20.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(result.SSID, fontSize = 16.sp)
                            Spacer(Modifier.weight(1f))
                            if (result.capabilities.contains("WEP") || result.capabilities.contains("WPA")) {
                                Icon(painter = painterResource(id = R.drawable.lock), contentDescription = "Secured")
                            }
                        }
                    }
                    item {

                        Row(
                            modifier = Modifier
                                .clickable { navController.navigate(Destinations.AddWifiScreen.route) }
                                .padding(horizontal = 16.dp, vertical = 20.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("手动添加Wi-Fi网络", fontSize = 16.sp)
                            Spacer(Modifier.weight(1f))
                            Icon(painter = painterResource(id = R.drawable.arrow_right), contentDescription = null, tint = Color.Gray)
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
        WifiManagerScreen(navController = rememberNavController())
    }
}