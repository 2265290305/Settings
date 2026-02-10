package com.android.tv.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    Column(modifier = Modifier.padding(24.dp)) {
        Text("手动添加Wi-Fi网络", fontSize = 20.sp, color = Color.Black)
        Spacer(modifier = Modifier.padding(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                TextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    label = { Text("网络名称 (SSID)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.padding(8.dp))
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    TextField(
                        value = selectedSecurity,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("安全性") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        securityOptions.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption) },
                                onClick = {
                                    selectedSecurity = selectionOption
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                if (selectedSecurity != "None") {
                    Spacer(modifier = Modifier.padding(8.dp))
                    TextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码") },
                        modifier = Modifier.fillMaxWidth()
                    )
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
