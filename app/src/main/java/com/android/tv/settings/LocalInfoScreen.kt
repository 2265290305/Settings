package com.android.tv.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Build.VERSION_CODES
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface
import java.util.Locale

private const val DEVICE_INFO_TAG = "LocalInfo"
private const val METHOD_DEV_QUERY = "DEV_QUERY"
private const val METHOD_DEV_OPT = "DEV_OPT"
private val DEVICE_INFO_URI: Uri = Uri.parse("content://com.android.ctcc.deviceinfo/device_info")

private fun normalizeValue(value: String?): String? {
    val v = value?.trim()
    if (v.isNullOrEmpty()) return null
    if (v.equals("null", ignoreCase = true)) return null
    return v
}

private fun systemPropertyGet(key: String): String? {
    return runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java, String::class.java)
        normalizeValue(get.invoke(null, key, "") as String)
    }.getOrNull()
}

private fun queryProviderValue(context: Context, uri: Uri, key: String): String? {
    val resolver = context.contentResolver
    val byCall = runCatching {
        val extras = Bundle().apply { putString("key", key) }
        val result = resolver.call(uri, METHOD_DEV_QUERY, null, extras)
        normalizeValue(result?.getString(key))
            ?: normalizeValue(result?.getString("value"))
            ?: normalizeValue(result?.getString("result"))
            ?: normalizeValue(result?.getString("data"))
    }.getOrNull()
    if (!byCall.isNullOrEmpty()) return byCall

    val byCursor = runCatching {
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val idx = cursor.getColumnIndex(key)
            if (idx >= 0) normalizeValue(cursor.getString(idx)) else normalizeValue(cursor.getString(0))
        }
    }.getOrNull()
    if (!byCursor.isNullOrEmpty()) return byCursor

    return null
}

private fun queryDeviceInfoBestEffort(context: Context, keys: List<String>): String? {
    keys.forEach { key ->
        val v = queryProviderValue(context, DEVICE_INFO_URI, key)
        if (!v.isNullOrEmpty()) return v
    }
    return null
}

private fun updateDeviceNameBestEffort(context: Context, newName: String): Boolean {
    if (Build.VERSION.SDK_INT >= VERSION_CODES.M && !Settings.System.canWrite(context)) {
        // Matches the spec: Settings.Global.putString requires WRITE_SETTINGS for third-party apps.
        // For this Settings app it should usually be granted; if not, fail fast.
        return false
    }

    val resolver = context.contentResolver
    val candidates = listOf(
        Bundle().apply { putString("deviceName", newName) },
        Bundle().apply { putString("name", newName) },
        Bundle().apply {
            putString("key", "deviceName")
            putString("value", newName)
        },
        Bundle().apply {
            putString("key", "name")
            putString("value", newName)
        },
        Bundle().apply {
            putString("key", "device_name")
            putString("value", newName)
        }
    )
    candidates.forEach { extras ->
        val ok = runCatching {
            val result = resolver.call(DEVICE_INFO_URI, METHOD_DEV_OPT, null, extras)
            result?.getBoolean("success", false) == true ||
                result?.getBoolean("result", false) == true ||
                result?.getInt("code", -1) == 0
        }.getOrDefault(false)
        if (ok) return true
    }

    val settingsOk = runCatching {
        // Spec 6.8.2.3 uses Settings.Global.DEVICE_NAME; use the literal to avoid SDK constant mismatch.
        Settings.Global.putString(context.contentResolver, "device_name", newName)
    }.getOrDefault(false)
    if (settingsOk) return true

    return false
}

private fun formatMac(bytes: ByteArray?): String? {
    if (bytes == null || bytes.isEmpty()) return null
    return bytes.joinToString(":") { String.format(Locale.US, "%02X", it) }
}

private data class NetSnapshot(
    val mac: String?,
    val ipv4: String?,
    val ipv6: String?
)

private fun snapshotNetworkBestEffort(): NetSnapshot {
    val ifaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }.getOrDefault(emptyList())
    val up = ifaces
        .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
        .sortedBy { it.name }

    fun firstMac(): String? {
        // Prefer ethernet/wlan if present.
        val preferred = listOf("eth0", "wlan0", "en0")
        preferred.forEach { n ->
            up.firstOrNull { it.name.equals(n, ignoreCase = true) }?.let { ni ->
                formatMac(runCatching { ni.hardwareAddress }.getOrNull())?.let { return it }
            }
        }
        up.forEach { ni ->
            formatMac(runCatching { ni.hardwareAddress }.getOrNull())?.let { return it }
        }
        return null
    }

    fun firstIp4(): String? {
        up.forEach { ni ->
            val addrs = runCatching { ni.inetAddresses?.toList().orEmpty() }.getOrDefault(emptyList())
            addrs.forEach { addr ->
                if (addr is Inet4Address && !addr.isLoopbackAddress) return addr.hostAddress
            }
        }
        return null
    }

    fun firstIp6(): String? {
        up.forEach { ni ->
            val addrs = runCatching { ni.inetAddresses?.toList().orEmpty() }.getOrDefault(emptyList())
            addrs.forEach { addr ->
                if (addr is Inet6Address && !addr.isLoopbackAddress) return addr.hostAddress
            }
        }
        return null
    }

    return NetSnapshot(mac = firstMac(), ipv4 = firstIp4(), ipv6 = firstIp6())
}

@Composable
fun LocalInfoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext

    var deviceName by rememberSaveable { mutableStateOf("") }
    var deviceModel by rememberSaveable { mutableStateOf("") }
    var deviceSn by rememberSaveable { mutableStateOf("") }
    var deviceCtei by rememberSaveable { mutableStateOf("") }
    var deviceMac by rememberSaveable { mutableStateOf("") }
    var deviceIp by rememberSaveable { mutableStateOf("") }
    var deviceIpv6 by rememberSaveable { mutableStateOf("") }
    var deviceLocation by rememberSaveable { mutableStateOf("") }
    var systemVersion by rememberSaveable { mutableStateOf("") }

    var editNameOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val name = runCatching { Settings.Global.getString(context.contentResolver, "device_name") }.getOrNull()
            ?: runCatching { Settings.Secure.getString(context.contentResolver, "device_name") }.getOrNull()
            ?: queryDeviceInfoBestEffort(context, listOf("devName", "deviceName", "name", "device_name"))
            ?: systemPropertyGet("ro.product.name")
        deviceName = normalizeValue(name) ?: "未命名"

        deviceModel = normalizeValue(
            queryDeviceInfoBestEffort(context, listOf("model", "deviceModel", "device_model"))
                ?: systemPropertyGet("ro.product.model")
        ) ?: normalizeValue(Build.MODEL) ?: "未知"

        val serial = runCatching { Build.getSerial() }.getOrNull()
        deviceSn = normalizeValue(
            queryDeviceInfoBestEffort(context, listOf("deviceSn", "sn", "SN", "stbsn", "STBSN"))
                ?: normalizeValue(serial)
                ?: systemPropertyGet("persist.sys.devinfo.STBSN")
                ?: systemPropertyGet("ro.serialno")
        ) ?: "未知"

        deviceCtei = normalizeValue(
            queryDeviceInfoBestEffort(context, listOf("ctei", "CTEI", "deviceCtei"))
                ?: systemPropertyGet("ro.product.ctei")
        ) ?: "未知"

        val net = snapshotNetworkBestEffort()
        deviceMac = normalizeValue(
            queryDeviceInfoBestEffort(context, listOf("deviceMac", "mac", "MAC", "deviceMac"))
        ) ?: (net.mac ?: "未知")
        deviceIp = normalizeValue(queryDeviceInfoBestEffort(context, listOf("ip", "ipv4", "IP", "deviceIp"))) ?: (net.ipv4 ?: "未知")
        deviceIpv6 = normalizeValue(queryDeviceInfoBestEffort(context, listOf("ipv6", "IPv6", "deviceIpv6"))) ?: (net.ipv6 ?: "未知")

        deviceLocation = normalizeValue(
            queryDeviceInfoBestEffort(context, listOf("location", "province", "city", "area"))
        ) ?: "未知"

        systemVersion = normalizeValue(
            queryDeviceInfoBestEffort(context, listOf("swVersion", "version", "systemVersion", "romVersion"))
                ?: systemPropertyGet("ro.product.version")
        ) ?: normalizeValue(Build.VERSION.INCREMENTAL) ?: "未知"
    }

    if (editNameOpen) {
        EditDeviceNameDialog(
            initial = deviceName,
            onDismiss = { editNameOpen = false },
            onSave = { newName ->
                val trimmed = newName.trim()
                if (trimmed.isEmpty()) return@EditDeviceNameDialog
                val ok = updateDeviceNameBestEffort(context, trimmed)
                if (ok) {
                    deviceName = trimmed
                    Toast.makeText(context, "设备名称已更新", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "设备名称更新失败", Toast.LENGTH_SHORT).show()
                }
                editNameOpen = false
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            //.background(Color(0xFFF0F2F5))
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Card(
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = colorResource(R.color.cardcolor)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoRow(
                    title = "设备名称",
                    value = deviceName,
                    clickable = true,
                    onClick = { editNameOpen = true }
                )
                InfoRow(title = "设备型号", value = deviceModel)
                InfoRow(title = "设备SN", value = deviceSn)
                InfoRow(title = "设备CTEI", value = deviceCtei)
                InfoRow(title = "设备MAC", value = deviceMac)
                InfoRow(title = "设备IP地址", value = deviceIp)
                InfoRow(title = "设备IPv6地址", value = deviceIpv6)
                InfoRow(title = "位置信息", value = deviceLocation)
            }
        }

        Card(
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = colorResource(R.color.cardcolor)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "系统版本",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colorResource(R.color.textblack)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "版本：$systemVersion",
                                fontSize = 13.sp,
                                color = Color(0xFF6B7280),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                Toast.makeText(context, "检查更新（待接入）", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF4C73FF))
                        ) {
                            Text(text = "检查更新", fontSize = 14.sp)
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "日志上报",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colorResource(R.color.textblack)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "点击上传日志到服务器",
                                fontSize = 13.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                Toast.makeText(context, "日志上报（待接入）", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF4C73FF))
                        ) {
                            Text(text = "日志上报", fontSize = 14.sp)
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            Toast.makeText(context, "恢复出厂设置（待接入）", Toast.LENGTH_SHORT).show()
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "恢复出厂设置",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorResource(R.color.textblack)
                        )
                        Spacer(Modifier.weight(1f))
                        Icon(
                            painter = painterResource(R.drawable.arrow_right),
                            contentDescription = null,
                            tint = Color(0xFFADB3BD),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    title: String,
    value: String,
    clickable: Boolean = false,
    onClick: () -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorResource(R.color.textblack)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = value,
                fontSize = 16.sp,
                color = Color(0xFF8B909A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (clickable) {
                Spacer(Modifier.width(10.dp))
                Icon(
                    painter = painterResource(R.drawable.arrow_right),
                    contentDescription = null,
                    tint = Color(0xFFADB3BD),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditDeviceNameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by rememberSaveable(initial) { mutableStateOf(initial) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "修改设备名称",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorResource(R.color.textblack)
                )
                Divider(color = Color(0xFFE9EAEC))
                // Keep it simple: reuse the visual language already in the project.
                androidx.compose.material3.OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    placeholder = { Text("请输入设备名称") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onSave(value) }) { Text("保存") }
                }
            }
        }
    }
}
