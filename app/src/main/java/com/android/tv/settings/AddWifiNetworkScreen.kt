package com.android.tv.settings

import android.app.Service
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tv.settings.ui.theme.设置Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWifiNetworkScreen(onBack: () -> Unit) {
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val securityOptions = listOf("None", "WEP", "WPA/WPA2 PSK", "WPA3 PSK")
    var expanded by remember { mutableStateOf(false) }
    var selectedSecurity by remember { mutableStateOf(securityOptions[0]) }
    val wm = LocalContext.current.getSystemService(Service.WIFI_SERVICE) as WifiManager;

    Column(modifier = Modifier.padding(24.dp)) {


        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White,)
        ) {
            Column(modifier = Modifier.padding(16.dp),) {
                Row(verticalAlignment = Alignment.CenterVertically){

                    var color = TextFieldDefaults.colors(unfocusedContainerColor = Color.White,
                        unfocusedIndicatorColor = Color.Transparent,)

                    Text("Wi-fi 名称")
                    Spacer(modifier = Modifier.weight(1f))
                    Box(

                    ){
                        TextField(
                            placeholder = {Text(text = "请输入Wi-Fi名称", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)},
                            colors = color,
                            value = ssid,
                            onValueChange = { ssid = it },


                        )
                    }

                }

                Spacer(modifier = Modifier.padding(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically){

                    var color = TextFieldDefaults.colors(unfocusedContainerColor = Color.White,
                        unfocusedIndicatorColor = Color.Transparent,)

                    Text("加密方式")
                    Spacer(modifier = Modifier.weight(1f))
                    Box(

                    ){
                        val encmod = listOf<String>("OWP")
                        TextField(
                            placeholder = {Text(text = ""., modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)},
                            colors = color,
                            value = ssid,
                            onValueChange = { ssid = it },


                            )
                    }

                }
            }
        }
        Spacer(modifier = Modifier.padding(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("取消", modifier = Modifier.clickable { onBack() })
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = { /* TODO: Implement connection logic */ }) {
                Text("连接")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AddWifiNetworkScreenPreview() {
    设置Theme {
        AddWifiNetworkScreen(onBack = {})
    }
}
