package com.android.tv.settings

import android.app.Service
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.android.tv.settings.ui.theme.设置Theme
import kotlinx.coroutines.delay

@Composable
fun WifiConnectScreen(ssid: String, onBack: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val color = TextFieldDefaults.colors(unfocusedContainerColor = Color.White,
        unfocusedIndicatorColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, focusedContainerColor = Color.Transparent)
    var showConnectingDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val wifiManager = if (LocalInspectionMode.current) null else context.getSystemService(Service.WIFI_SERVICE) as WifiManager;
    //val config = wifiManager?.configuredNetworks?.find { it.SSID.trim('"') == ssid.trim('"') }

    if (showConnectingDialog) {
        Dialog(onDismissRequest = { showConnectingDialog=false}) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xE6212121))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("连接中...", color = Color.White)
                }
            }
        }
        LaunchedEffect(Unit) {
            val conf = WifiConfiguration()
            conf.SSID = "\"$ssid\""
            conf.preSharedKey = "\"$password\""
            @Suppress("DEPRECATION")
            val netId = wifiManager?.addNetwork(conf)
            if (netId != -1) {
                wifiManager?.disconnect()
                netId?.let { wifiManager?.enableNetwork(it, true) }
                wifiManager?.reconnect()
            }
            delay(2000) // Give time for connection to establish

            onBack()
        }
    }
    Column(modifier = Modifier.padding(24.dp)) {
        Text("网络设置", fontSize = 20.sp, color = Color.Black)
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.Center) {
                Row() {
                    Text("Wi-Fi名称")
                    Spacer(modifier = Modifier.weight(1f))
                    Text(ssid, color = colorResource(R.color.textgray))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically){

                    Text("密码")
                    Spacer(modifier = Modifier.weight(1f))
                    TextField(
                        placeholder = {Text(text = "请输入密码", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)},
                        colors = color,
                        value = password,
                        onValueChange = { password = it },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (passwordVisible)
                                painterResource(R.drawable.ic_visibility)
                            else
                                painterResource(R.drawable.ic_visibility)


                            Icon(painter = image, contentDescription = "Toggle password visibility", tint = Color(0xFF4CA8FF))

                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            ),

            modifier = Modifier
                .align(alignment = Alignment.CenterHorizontally)
                .width(140.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF4CA8FF),
                            Color(0xFF3E4CFF)
                        )
                    ),
                    shape = RoundedCornerShape(30.dp)
                ),
            onClick = {
                if (ssid.isNotBlank()) {
                    //config?.networkId?.let { wifiManager?.disableNetwork(it) }
                    //wifiManager?.disconnect()
                    showConnectingDialog = true
                }
            },
            content = {
                Text("连接网络",color = Color.White, modifier = Modifier.align(alignment = Alignment.CenterVertically))
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun WifiConnectScreenPreview() {
    设置Theme {
        WifiConnectScreen(ssid = "NJDL-6F1233", onBack = {})
    }
}
