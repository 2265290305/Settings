package com.android.tv.settings

import android.app.Service
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWifiNetworkScreen(onBack: () -> Unit) {
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val securityOptions = listOf("WPA/WPA2 PSK", "WEP", )
    var selectedSecurity by remember { mutableStateOf(securityOptions[0]) }
    val context = LocalContext.current
    val wifiManager = if (LocalInspectionMode.current) null else context.getSystemService(Service.WIFI_SERVICE) as WifiManager;
    var showConnectingDialog by remember { mutableStateOf(false) }
    var showSecurityDialog by remember { mutableStateOf(false) }
   // val config = wifiManager?.configuredNetworks?.find { it.SSID.trim('"') == ssid.trim('"') }

    if (showConnectingDialog) {
        Dialog(onDismissRequest = { /* Prevent dismissal */ }) {
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
            when (selectedSecurity) {
                "None" -> {
                    conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                }
                "WEP" -> {
                    conf.wepKeys[0] = "\"$password\""
                    conf.wepTxKeyIndex = 0
                    conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
                }
                "WPA/WPA2 PSK" -> {
                    conf.preSharedKey = "\"$password\""
                }
            }
            @Suppress("DEPRECATION")
            val netId = wifiManager?.addNetwork(conf)
            if (netId != -1) {
                wifiManager?.disconnect()
                netId?.let { wifiManager?.enableNetwork(it, true) }
                wifiManager?.reconnect()
            }
            delay(2000) // Give time for connection to establish
            showConnectingDialog = false
            onBack()
        }
    }

    if (showSecurityDialog) {
        Dialog(onDismissRequest = { showSecurityDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("加密方式", fontSize = 20.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(modifier = Modifier.height(16.dp))
                    securityOptions.forEachIndexed { index, option ->
                        Text(
                            text = option,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSecurity = option
                                    showSecurityDialog = false
                                }
                                .padding(vertical = 12.dp),
                            textAlign = TextAlign.Center
                        )
                        if (index < securityOptions.size - 1) {
                            Divider(color = Color.LightGray, thickness = 1.dp)
                        }
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.padding(24.dp)) {
        Text("网络设置", fontSize = 20.sp, color = Color(0xFF131519))
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White,)
        ) {
            Column(modifier = Modifier.padding(16.dp),) {
                val color = TextFieldDefaults.colors(unfocusedContainerColor = Color.White,
                    unfocusedIndicatorColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, focusedContainerColor = Color.Transparent)

                Row(verticalAlignment = Alignment.CenterVertically){
                    Text("Wi-fi 名称")
                    Spacer(modifier = Modifier.weight(1f))
                    TextField(
                        placeholder = {Text(text = "请输入Wi-Fi名称", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)},
                        colors = color,
                        value = ssid,
                        onValueChange = { ssid = it },
                    )
                }

                Spacer(modifier = Modifier.padding(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showSecurityDialog = true },
                    verticalAlignment = Alignment.CenterVertically
                ){
                    Text("加密方式")
                    Spacer(modifier = Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(selectedSecurity, color = Color(0x99131519))
                        Icon(painter = painterResource(id = R.drawable.arrow_right), contentDescription = null, tint = Color.Gray)
                    }
                }
                //if (selectedSecurity != "None") {
                    Spacer(modifier = Modifier.padding(8.dp))
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

                                IconButton(onClick = {passwordVisible = !passwordVisible}){
                                    Icon(painter = image, contentDescription = "Toggle password visibility", tint = Color(0xFF4CA8FF))
                                }
                            }
                        )
                    //}
                }
            }
        }
        Spacer(modifier = Modifier.padding(16.dp))

        Button(
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
                , contentColor = Color.Transparent
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
                if (ssid.isNotBlank() && (selectedSecurity == "None" || password.isNotBlank())) {
                   // config?.networkId?.let { wifiManager?.disableNetwork(it) }
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
fun AddWifiNetworkScreenPreview() {
    设置Theme {
        AddWifiNetworkScreen(onBack = {})
    }
}
