package com.android.tv.settings

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        Dialog(onDismissRequest = { /* Don't dismiss on outside click */ }) {
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
            conf.preSharedKey = "\"$password\""
            val netId = wifiManager?.addNetwork(conf)
            if (netId != -1) {
                wifiManager?.disconnect()
                netId?.let { wifiManager.enableNetwork(it, true) }
                wifiManager?.reconnect()
            }
            delay(2000)
            showDialog = false
            onBack()
        }
    }


    Column(modifier = Modifier.padding(24.dp)) {
        val black =Color(0xFF131519)
        val gray = colorResource(R.color.textgray)
        Text("网络设置", fontSize = 20.sp, color = black)
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Wi-Fi名称")
                    Spacer(modifier = Modifier.weight(1f))
                    Text(ssid, color = gray)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically){
                    val color = TextFieldDefaults.colors(unfocusedContainerColor = Color.White,
                        unfocusedIndicatorColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, focusedContainerColor = Color.Transparent)

                    Text("密码", color = Color(0xFF131519))
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

                            IconButton(onClick = {passwordVisible = !passwordVisible}){
                                Icon(painter = image, contentDescription = "Toggle password visibility")
                            }
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        val btc = ButtonDefaults.buttonColors(containerColor = Color.Transparent)

        Button(
            modifier = Modifier.align(Alignment.CenterHorizontally).background(brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF4ca8Ff),
                    Color(0xFF3e4cff)
                )
            ), shape = RoundedCornerShape(size = 50.dp),),
            colors= btc,
            onClick = {
                showDialog = true
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
