package com.android.tv.settings

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tv.settings.ui.theme.设置Theme

private const val METHOD_DEV_QUERY = "DEV_QUERY"
private const val METHOD_DEV_OPT = "DEV_OPT"
private const val LAB_PROVIDER_TAG = "LabScreenProvider"

private val DEVICEINFO_AUTHORITIES = listOf(
    "com.android.ctcc.deviceinfo",
    "com.android.zshd.deviceinfo"
)

private fun contentUris(path: String, includeDeviceInfoFallback: Boolean = false): List<Uri> {
    val directUris = DEVICEINFO_AUTHORITIES.map { authority ->
        Uri.parse("content://$authority/$path")
    }
    if (!includeDeviceInfoFallback || path == "device_info") {
        return directUris
    }
    val fallbackUris = DEVICEINFO_AUTHORITIES.map { authority ->
        Uri.parse("content://$authority/device_info")
    }
    return (directUris + fallbackUris).distinct()
}

private val DEVICE_INFO_URIS: List<Uri> = contentUris("device_info")
private val SETTINGS_URIS: List<Uri> = contentUris("settings")
private val DISTANCE_DETECT_URIS: List<Uri> = contentUris("distanceDectect", includeDeviceInfoFallback = true)
private val DISTANCE_ALARM_URIS: List<Uri> = contentUris("distanceAlarm", includeDeviceInfoFallback = true)
private val ONE_SHOT_SWITCH_URIS: List<Uri> = contentUris("oneShotSwitch", includeDeviceInfoFallback = true)
private val SUPPORT_FULL_DUPLEX_URIS: List<Uri> = contentUris("supportFullDuplex", includeDeviceInfoFallback = true)
private val FULL_DUPLEX_MODE_URIS: List<Uri> = contentUris("fullDuplexMode", includeDeviceInfoFallback = true)

private fun normalizeProviderValue(value: Any?): String? {
    val normalized = value?.toString()?.trim()
    if (normalized.isNullOrEmpty()) return null
    if (normalized.equals("null", ignoreCase = true)) return null
    return normalized
}

private fun readProviderResultValue(bundle: Bundle?, key: String): String? {
    if (bundle == null) return null
    return normalizeProviderValue(bundle.getString(key))
        ?: normalizeProviderValue(bundle.getString("value"))
        ?: normalizeProviderValue(bundle.getString("data"))
}

private fun logUnauthorizedCallerIfNeeded(
    uri: Uri,
    method: String,
    key: String,
    extras: Bundle?,
    result: Bundle?
) {
    if (uri.authority != "com.android.zshd.deviceinfo") return
    val error = result?.getString("error") ?: return
    if (!error.equals("Unauthorized caller", ignoreCase = true)) return
    val extrasSummary = extras?.keySet()?.joinToString(prefix = "{", postfix = "}") { bundleKey ->
        "$bundleKey=${extras.get(bundleKey)}"
    } ?: "{}"
    Log.w(
        LAB_PROVIDER_TAG,
        "provider unauthorized uri=$uri method=$method key=$key extras=$extrasSummary error=$error"
    )
}

private fun writeResultMatchesValues(bundle: Bundle?, values: Map<String, String>): Boolean {
    if (bundle == null || !isBundleSuccess(bundle)) return false
    if (values.isEmpty()) return false
    return values.all { (key, value) ->
        normalizeProviderValue(bundle.getString(key)) == value ||
            (values.size == 1 && normalizeProviderValue(bundle.getString("value")) == value)
    }
}

private fun isBundleSuccess(bundle: Bundle?): Boolean {
    if (bundle == null) return false
    if (bundle.getBoolean("success", false)) return true
    if (bundle.getBoolean("result", false)) return true
    if (bundle.getInt("code", -1) == 0) return true
    return false
}

private fun queryProviderValue(context: Context, uris: List<Uri>, key: String, defaultValue: String): String {
    val resolver = context.contentResolver

    val callResult = uris.firstNotNullOfOrNull { uri ->
        runCatching {
            val candidates = listOf(
                Bundle().apply { putString("key", key) },
                Bundle().apply {
                    putString("key", key)
                    putString(key, "")
                },
                Bundle()
            )
            candidates.firstNotNullOfOrNull { extras ->
                val result = resolver.call(uri, METHOD_DEV_QUERY, null, extras)
                logUnauthorizedCallerIfNeeded(uri, METHOD_DEV_QUERY, key, extras, result)
                readProviderResultValue(result, key)
            }
        }.getOrNull()
    }
    if (!callResult.isNullOrEmpty()) return callResult

    return uris.firstNotNullOfOrNull { uri ->
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(key)
                val value = if (index >= 0) cursor.getString(index) else cursor.getString(0)
                normalizeProviderValue(value)
            }
        }.getOrNull()
    } ?: defaultValue
}

private fun queryProviderBool(context: Context, uris: List<Uri>, key: String, defaultValue: Boolean): Boolean {
    return queryProviderValue(context, uris, key, if (defaultValue) "1" else "0") == "1"
}

private fun queryLegacySetting(context: Context, key: String, defaultValue: String): String {
    val resolver = context.contentResolver
    return SETTINGS_URIS.firstNotNullOfOrNull { uri ->
        runCatching {
            val extras = Bundle().apply { putString("key", key) }
            val result = resolver.call(uri, METHOD_DEV_QUERY, null, extras)
            logUnauthorizedCallerIfNeeded(uri, METHOD_DEV_QUERY, key, extras, result)
            normalizeProviderValue(result?.getString("value"))
                ?: normalizeProviderValue(result?.getString(key))
        }.getOrNull()
    } ?: defaultValue
}

private fun updateProviderValues(context: Context, uris: List<Uri>, values: Map<String, String>): Boolean {
    val resolver = context.contentResolver

    val writeSuccess = uris.any { uri ->
        runCatching {
            val extras = Bundle().apply {
                values.forEach { (key, value) ->
                    putString(key, value)
                }
                if (values.size == 1) {
                    val (key, value) = values.entries.first()
                    putString("key", key)
                    putString("value", value)
                }
            }
            val result = resolver.call(uri, METHOD_DEV_OPT, null, extras)
            logUnauthorizedCallerIfNeeded(uri, METHOD_DEV_OPT, values.keys.firstOrNull().orEmpty(), extras, result)
            writeResultMatchesValues(result, values)
        }.getOrDefault(false)
    }
    if (writeSuccess) return true

    val splitWriteSuccess = uris.any { uri ->
        values.all { (key, value) ->
            runCatching {
                val extras = Bundle().apply {
                    putString("key", key)
                    putString("value", value)
                    putString(key, value)
                }
                val result = resolver.call(uri, METHOD_DEV_OPT, null, extras)
                logUnauthorizedCallerIfNeeded(uri, METHOD_DEV_OPT, key, extras, result)
                writeResultMatchesValues(result, mapOf(key to value))
            }.getOrDefault(false)
        }
    }
    if (splitWriteSuccess) return true

    return values.all { (key, value) ->
        queryProviderValue(context, uris, key, "") == value
    }
}

private fun updateLegacySetting(context: Context, key: String, value: String): Boolean {
    val resolver = context.contentResolver
    return SETTINGS_URIS.any { uri ->
        runCatching {
            val extras = Bundle().apply {
                putString("key", key)
                putString("value", value)
            }
            val result = resolver.call(uri, METHOD_DEV_OPT, null, extras)
            logUnauthorizedCallerIfNeeded(uri, METHOD_DEV_OPT, key, extras, result)
            isBundleSuccess(result)
        }.getOrDefault(false)
    }
}

@Composable
fun LabScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext

    var distanceReminder by rememberSaveable { mutableStateOf(false) }
    var gestureControl by rememberSaveable { mutableStateOf(true) }
    var quickCommands by rememberSaveable { mutableStateOf(true) }
    var continuousDialogue by rememberSaveable { mutableStateOf(false) }
    var supportFullDuplex by rememberSaveable { mutableStateOf(true) }
    var refreshVersion by remember { mutableStateOf(0) }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                refreshVersion++
            }
        }
        val uris = (
            SETTINGS_URIS +
                DISTANCE_DETECT_URIS +
                DISTANCE_ALARM_URIS +
                DEVICE_INFO_URIS +
                ONE_SHOT_SWITCH_URIS +
                SUPPORT_FULL_DUPLEX_URIS +
                FULL_DUPLEX_MODE_URIS
            ).distinct()
        uris.forEach { uri ->
            runCatching { context.contentResolver.registerContentObserver(uri, true, observer) }
        }
        onDispose {
            runCatching { context.contentResolver.unregisterContentObserver(observer) }
        }
    }

    LaunchedEffect(context, refreshVersion) {
        distanceReminder = queryProviderBool(context, DISTANCE_DETECT_URIS, "distanceDectect", false)
        gestureControl = queryLegacySetting(context, "gesture_control", "1") == "1"
        quickCommands = queryProviderBool(context, ONE_SHOT_SWITCH_URIS, "oneShotSwitch", true)
        supportFullDuplex = queryProviderBool(context, SUPPORT_FULL_DUPLEX_URIS, "supportFullDuplex", true)
        continuousDialogue = supportFullDuplex &&
            queryProviderBool(context, FULL_DUPLEX_MODE_URIS, "fullDuplexMode", false)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(color = colorResource(R.color.cardcolor))
            .clip(RoundedCornerShape(18.dp))
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SwitchInfoCard(
            title = "距离过近提醒",
            desc = "儿童靠近时，设备将提醒您注意设备安全距离",
            checked = distanceReminder,
            onCheckedChange = { checked ->
                if (updateProviderValues(context, DISTANCE_DETECT_URIS, mapOf("distanceDectect" to if (checked) "1" else "0"))) {
                    distanceReminder = checked
                } else {
                    Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                }
            }
        )

        GestureCard(
            checked = gestureControl,
            onCheckedChange = { checked ->
                if (updateLegacySetting(context, "gesture_control", if (checked) "1" else "0")) {
                    gestureControl = checked
                } else {
                    Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                }
            }
        )

        QuickCommandsCard(
            checked = quickCommands,
            onCheckedChange = { checked ->
                if (updateProviderValues(context, ONE_SHOT_SWITCH_URIS, mapOf("oneShotSwitch" to if (checked) "1" else "0"))) {
                    quickCommands = checked
                } else {
                    Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                }
            }
        )

        if (supportFullDuplex) {
            SwitchInfoCard(
                title = "连续对话",
                desc = "开启后，进行连续对话时无需重复唤醒一段时间，小翼将保持聆听状态",
                checked = continuousDialogue,
                onCheckedChange = { checked ->
                    if (updateProviderValues(context, FULL_DUPLEX_MODE_URIS, mapOf("fullDuplexMode" to if (checked) "1" else "0"))) {
                        continuousDialogue = checked
                    } else {
                        Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

@Composable
private fun SwitchInfoCard(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colorResource(R.color.cardcolor)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(30.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(end = 96.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorResource(R.color.textblack)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = desc,
                    fontSize = 20.sp,
                    color = Color(0xFF6B7280),
                    lineHeight = 28.sp
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.align(Alignment.CenterEnd),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF4C73FF),
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFE0E0E0),
                    checkedBorderColor = Color.Transparent,
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
private fun GestureCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colorResource(R.color.cardcolor)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(22.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(end = 84.dp)
                ) {
                    Text(
                        text = "手势控制",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorResource(R.color.textblack)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "开启后，在离屏幕0.25-2米范围内做出指定手势，即可在屏幕实现手势控制操作。小翼会根据手势动作学习。",
                        fontSize = 20.sp,
                        color = Color(0xFF6B7280),
                        lineHeight = 28.sp,
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF4C73FF),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFE0E0E0),
                        checkedBorderColor = Color.Transparent,
                        uncheckedBorderColor = Color.Transparent
                    )
                )
            }

            Spacer(Modifier.height(20.dp))
            Divider(color = Color(0xFFE9EAEC))
            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GestureTile(title = "播放", hint = "一下", icon = R.drawable.play)
                GestureTile(title = "暂停", hint = "上一页/下一页", icon = R.drawable.pause)
                GestureTile(title = "上一首/上一集", hint = "确认", icon = R.drawable.pgup)
                GestureTile(title = "下一首/下一集", hint = "收藏", icon = R.drawable.pgdn)
            }
            Spacer(Modifier.height(20.dp))
            Text("适用范围：网易云音乐、喜马拉雅、天翼超高清等", color = colorResource(R.color.textgray))
        }
    }
}

@Composable
private fun GestureTile(title: String, hint: String, icon: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7F8)),
            modifier = Modifier.size(120.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(icon),
                    contentDescription = null,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = colorResource(R.color.textgray))
    }
}

@Composable
private fun QuickCommandsCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(22.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(end = 84.dp)
                ) {
                    Text(
                        text = "快捷指令",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorResource(R.color.textblack)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "开启后，可直接说“小翼管家”唤醒步骤，直接说“小翼管家”触发快捷指令",
                        fontSize = 20.sp,
                        color = Color(0xFF6B7280),
                        lineHeight = 28.sp
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF4C73FF),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFE0E0E0),
                        checkedBorderColor = Color.Transparent,
                        uncheckedBorderColor = Color.Transparent
                    )
                )
            }

            Spacer(Modifier.height(14.dp))
            Divider(color = Color(0xFFE9EAEC))
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.Center) {
                Image(painterResource(R.drawable.beopen), contentDescription = "beopen", modifier = Modifier.width(265.dp).height(420.dp))
                Spacer(modifier = Modifier.width(24.dp))
                Image(painterResource(R.drawable.afopen), contentDescription = "afopen", modifier = Modifier.width(265.dp).height(420.dp))
            }

            Spacer(Modifier.height(12.dp))
            ValueRowLink(text = "关于小翼管家快捷指令")
        }
    }
}

@Composable
private fun ValueRowLink(text: String) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = text, fontSize = 32.sp, color = colorResource(R.color.textblack), fontWeight = FontWeight.SemiBold)
            Icon(
                painter = painterResource(R.drawable.arrow_right),
                contentDescription = null,
                tint = Color(0xFFADB3BD),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF0F2F5, widthDp = 1280, heightDp = 1800)
@Composable
private fun LabScreenPreview() {
    设置Theme {
        LabScreen()
    }
}
