package com.android.tv.settings

import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.runtime.mutableStateListOf
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

var discover = emptyList<BluetoothDevice>()
@Composable
fun BlueToothScreen(modifier: Modifier = Modifier, navController: NavController) {
    val context = LocalContext.current
    var isScanning by remember { mutableStateOf(false) }
    // In preview mode, we can't get the BluetoothManager.
    // We check if we are in inspection mode and provide a null manager instead.
    val Blm = if (LocalInspectionMode.current) {
        null
    } else {
        context.getSystemService(Service.BLUETOOTH_SERVICE) as BluetoothManager
    }
    var isChecked by remember { mutableStateOf(Blm?.adapter?.isEnabled ?: true) }
    val discoveredDevices = remember { mutableStateListOf<BluetoothDevice>() }
    if(discover.isNotEmpty()){
        discoveredDevices.addAll(discover)
    }
    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action: String? = intent.action
                if (BluetoothDevice.ACTION_FOUND == action) {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (it !in discoveredDevices && it.name != null) {
                            discoveredDevices.add(it)
                        }
                    }
                }
                isScanning = false
            }
        }
    }

    DisposableEffect(context, Blm) {
        Blm?.let {
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            Blm?.let {
                context.unregisterReceiver(receiver)
            }
        }
    }

    LaunchedEffect(isChecked, Blm) {
        if (isChecked) {
            Blm?.adapter?.let { adapter ->
                if (adapter.isDiscovering) {
                    adapter.cancelDiscovery()
                }
                adapter.startDiscovery()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
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
                Text("蓝牙开关", fontSize = 16.sp)
                Spacer(Modifier.weight(1f))

                Switch(
                    checked = isChecked,
                    onCheckedChange = @androidx.annotation.RequiresPermission(
                        android.Manifest.permission.BLUETOOTH_CONNECT
                    ) { newCheckedState ->
                        isChecked = newCheckedState
                        if (isChecked) {
                            Blm?.adapter?.enable()
                        } else {
                            Blm?.adapter?.disable()
                            discoveredDevices.clear()
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isChecked ) {
            //if (otherAvailableNetworks.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("可用Wi-Fi网络", fontSize = 16.sp)
                    TextButton(onClick = {

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
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Column {
                            if (LocalInspectionMode.current) {
                                Row(
                                    modifier = Modifier
                                        .clickable { }
                                        .padding(horizontal = 16.dp, vertical = 20.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Fake Device 1", fontSize = 16.sp)
                                    Spacer(Modifier.weight(1f))
                                    Icon(painter = painterResource(id = R.drawable.lock), contentDescription = "Secured")
                                }
                                Row(
                                    modifier = Modifier
                                        .clickable { }
                                        .padding(horizontal = 16.dp, vertical = 20.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Fake Device 2", fontSize = 16.sp)
                                    Spacer(Modifier.weight(1f))
                                    Icon(painter = painterResource(id = R.drawable.lock), contentDescription = "Secured")
                                }
                            } else {
                                discoveredDevices.forEach { result ->
                                    @Suppress("DEPRECATION")
                                    Row(
                                        modifier = Modifier
                                            .clickable {
                                                navController.navigate(Destinations.WifiConnectScreen.createRoute(result.name))
                                            }
                                            .padding(horizontal = 16.dp, vertical = 20.dp)
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(result.name, fontSize = 16.sp)
                                        Spacer(Modifier.weight(1f))

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
                                Icon(painter = painterResource(id = R.drawable.arrow_right), contentDescription = null, tint = Color.Gray)
                            }

                        }
                    }
                }
            //}

        }
    }
}

@Preview
@Composable
fun BLuetooth() {
    // In Compose Previews, we cannot instantiate Android framework classes like BluetoothDevice
    // as the underlying services are not available.
    // We'll use an empty list for the preview to avoid runtime errors.
    discover = emptyList()
    BlueToothScreen(navController = NavController(LocalContext.current));
}
