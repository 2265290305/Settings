package com.android.tv.settings

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tv.settings.ui.theme.设置Theme

@SuppressLint("MissingPermission")
@Composable
fun WifiConnectScreen(ssid: String, onBack: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val wifiManager = if (LocalInspectionMode.current) null else context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    val conf by remember(ssid) {
        mutableStateOf(
            // Find the existing configuration for the given SSID, or create a new one.
            @Suppress("DEPRECATION")
            wifiManager?.configuredNetworks?.find { it.SSID.trim('"') == ssid } ?: WifiConfiguration().apply {
                this.SSID = "\"$ssid\""
            }
        )
    }


    Column(modifier = Modifier.padding(24.dp)) {
        Text("网络设置", fontSize = 20.sp, color = Color.Black)
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Wi-Fi名称")
                    Spacer(modifier = Modifier.weight(1f))
                    Text(ssid)
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("请输入密码") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible)
                            painterResource(id = R.drawable.ic_visibility)
                        else
                            painterResource(id = R.drawable.ic_visibility)

                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(painter = image, contentDescription = "Toggle password visibility")
                        }
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            onClick = {
                conf?.let {
                    // WPA/WPA2 passwords must be quoted
                    it.preSharedKey = "\"$password\""
                    val id = wifiManager?.addNetwork(it)
                    if (id != -1) {
                        wifiManager?.disconnect()
                        id?.let { netId -> wifiManager.enableNetwork(netId, true) }
                        wifiManager?.reconnect()
                    }
                    // Consider providing user feedback here (e.g., toast message)
                }
                onBack() // Navigate back after attempting to connect
            },

        ) {
            Text("连接网络")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WifiConnectScreenPreview() {
    设置Theme {
        WifiConnectScreen(ssid = "My Test WiFi", onBack = {})
    }
}
