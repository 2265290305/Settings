
package com.android.tv.settings

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.android.tv.settings.ui.theme.设置Theme

@SuppressLint("MissingPermission")
@Composable
fun WifiDetailScreen(ssid: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val wifiManager = if (LocalInspectionMode.current) null else context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    val connectionInfo by remember {
        mutableStateOf(wifiManager?.connectionInfo)
    }
    val scanResults by remember {
        mutableStateOf(wifiManager?.scanResults)
    }
    val targetSsid = ssid.normalizedWifiSsid()

    val currentScanResult = scanResults?.find { it.SSID.normalizedWifiSsid() == targetSsid }

    val signalStrength = when (currentScanResult?.level) {
        in -50..0 -> "极强"
        in -70..-51 -> "较强"
        in -80..-71 -> "一般"
        else -> "较弱"
    }

    val security = currentScanResult?.capabilities ?: "Unknown"
    val frequency = if (currentScanResult?.frequency ?: 0 > 5000) "5GHz" else "2.4GHz"
    val isConnected = targetSsid == connectionInfo?.ssid.normalizedWifiSsid()
    Log.d("wifiequal", "$targetSsid:${connectionInfo?.ssid}")
    var showForgetDialog by remember { mutableStateOf(false) }
    val config = wifiManager?.configuredNetworks?.firstOrNull { it.SSID.normalizedWifiSsid() == targetSsid }

    if (showForgetDialog) {
        Dialog(onDismissRequest = { showForgetDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("是否忽略此网络", fontSize = 20.sp, color = Color.Black)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "忽略此网络后, 设备将断开网络连接,\n再次连接需重新输入密码",
                        textAlign = TextAlign.Center,
                        color = Color(0x99000000)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { showForgetDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0F2F5))
                        ) {
                            Text("取消", color = Color(0xFF3e4cff))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = {
                                if (config != null) {
                                    wifiManager?.removeNetwork(config.networkId)
                                }
                                showForgetDialog = false
                                onBack()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF4ca8Ff), Color(0xFF3e4cff))
                                    ),
                                    shape = RoundedCornerShape(50.dp)
                                ),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        ) {
                            Text("确定", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    Column(modifier = Modifier
        .padding(24.dp)
        .verticalScroll(rememberScrollState())) {
        Text("网络设置", fontSize = 20.sp, color = Color(0xFF131519))
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("网络名称")
                    Spacer(modifier = Modifier.weight(1f))
                    Text(targetSsid, color = colorResource(R.color.textgray))
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("信号强度")
                    Spacer(modifier = Modifier.weight(1f))
                    Text(signalStrength, color = colorResource(R.color.textgray))
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("安全性")
                    Spacer(modifier = Modifier.weight(1f))
                    Text(security, color = colorResource(R.color.textgray))
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("信号频率")
                    Spacer(modifier = Modifier.weight(1f))
                    Text(frequency, color = colorResource(R.color.textgray))
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            val transparent = ButtonDefaults.buttonColors(containerColor = Color.Transparent)

            Button(
                onClick = {
                    showForgetDialog = true
                },
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            ) {
                Text("忽略网络", color = Color(0xFF4577FF))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                modifier = Modifier.background(brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF4ca8Ff),
                        Color(0xFF3e4cff)
                    )
                ), shape = RoundedCornerShape(size = 50.dp)),
                colors= transparent,
                onClick = {
                    if(isConnected){
                        config?.networkId?.let { runCatching { wifiManager?.disableNetwork(it) } }
                        runCatching { wifiManager?.disconnect() }

                    }else{
                        config?.networkId?.let { runCatching { wifiManager?.enableNetwork(it,true) } }
                        runCatching { wifiManager?.reconnect() }
                    }

                    onBack()
                },
            ) {
                Text(if( isConnected) "取消连接" else "连接网络")
            }
        }
    }
}



@Preview(showBackground = true)
@Composable
fun WifiDetailScreenPreview() {
    设置Theme {
        WifiDetailScreen(ssid = "TP-LINK_C7C2", onBack = {})
    }
}
