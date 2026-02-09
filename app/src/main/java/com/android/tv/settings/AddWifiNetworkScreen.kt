package com.android.tv.settings

import android.app.Service
import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tv.settings.ui.theme.设置Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWifiNetworkScreen(onBack: () -> Unit) {
    var ssid by remember { mutableStateOf("") }
    var passwd by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val securityOptions = listOf("WPA/WPA2 PSK", "WEP",  "WPA3 PSK")
    var expanded by remember { mutableStateOf(false) }
    var selectedSecurity by remember { mutableStateOf(securityOptions[0]) }
    val wm = if (LocalInspectionMode.current) null else LocalContext.current.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    var enc by remember { mutableStateOf(0) }
    var color = TextFieldDefaults.colors(unfocusedContainerColor = Color.White,
        unfocusedIndicatorColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, focusedContainerColor = Color.Transparent)

    Column(modifier = Modifier.padding(24.dp)) {


        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White,)
        ) {
            Column(modifier = Modifier.padding(16.dp),) {
                Row(verticalAlignment = Alignment.CenterVertically){


                    Text("Wi-fi 名称",color =Color(0xFF131519))
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

                    Text("加密方式", color = Color(0xFF131519))
                    //Spacer(modifier = Modifier.weight(1f))
                    //var buttoncolor =
                    Button(contentPadding = PaddingValues(0.dp),onClick = {}, colors = ButtonColors(containerColor = Color.Unspecified, contentColor = colorResource(R.color.textgray), disabledContentColor = Color.Unspecified, disabledContainerColor = Color.Unspecified)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                            Spacer(modifier = Modifier.weight(2f))
                            Text(text = securityOptions[enc], textAlign = TextAlign.End)
                                //colors = color,
                            Spacer(Modifier.width(5.dp))

                            Icon(
                                painter = painterResource(R.drawable.path),
                                contentDescription = "wifipath"
                            )
                        }
                    }
                    //Icon(painter = painterResource(R.drawable.path), contentDescription = "wifipath")

                }
                Spacer(modifier = Modifier.padding(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically){
                    Text("密码", color = Color(0xFF131519))
                    Spacer(modifier = Modifier.weight(1f))
                    TextField(
                        placeholder = {Text(text = "请输入密码", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)},
                        colors = color,
                        value = password,
                        onValueChange = { password = it },

                        )
                }
            }
        }
        Spacer(modifier = Modifier.padding(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            var btc = ButtonDefaults.buttonColors(containerColor = Color.Transparent)

            Button(
                modifier = Modifier.background(brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF4ca8Ff),
                        Color(0xFF3e4cff)
                    )
                ), shape = RoundedCornerShape(size = 50.dp),),
                colors= btc,
               // modifier = Modifier.align(Alignment.CenterHorizontally),
                onClick = {
                    val conf = WifiConfiguration()
                    conf.SSID = "\"$ssid\""
                    conf.preSharedKey = "\"$password\""
                    onBack() // Navigate back after attempting to connect
                },

                ) {
                Text("连接网络")
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
