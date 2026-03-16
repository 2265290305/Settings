package com.android.tv.settings

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tv.settings.ui.theme.设置Theme

private const val METHOD_DEV_QUERY = "DEV_QUERY"
private const val METHOD_DEV_OPT = "DEV_OPT"
private val SETTINGS_URI: Uri = Uri.parse("content://com.android.ctcc.deviceinfo/settings")
private val DISTANCE_DETECT_URI: Uri = Uri.parse("content://com.android.ctcc.deviceinfo/distanceDectect")
private val DISTANCE_ALARM_URI: Uri = Uri.parse("content://com.android.ctcc.deviceinfo/distanceAlarm")
private val DIALECT_SWITCH_URI: Uri = Uri.parse("content://com.android.ctcc.deviceinfo/dialectSwitch")
private val DIALECT_ID_URI: Uri = Uri.parse("content://com.android.ctcc.deviceinfo/dialectID")
private val DIALECT_NAME_URI: Uri = Uri.parse("content://com.android.ctcc.deviceinfo/dialectName")
private val ONE_SHOT_SWITCH_URI: Uri = Uri.parse("content://com.android.ctcc.deviceinfo/oneShotSwitch")
private val SUPPORT_FULL_DUPLEX_URI: Uri = Uri.parse("content://com.android.ctcc.deviceinfo/supportFullDuplex")
private val FULL_DUPLEX_MODE_URI: Uri = Uri.parse("content://com.android.ctcc.deviceinfo/fullDuplexMode")

private data class LabDialectOption(
    val label: String,
    val id: String?
)

private fun normalizeProviderValue(value: Any?): String? {
    val normalized = value?.toString()?.trim()
    if (normalized.isNullOrEmpty()) return null
    if (normalized.equals("null", ignoreCase = true)) return null
    return normalized
}

private fun isBundleSuccess(bundle: Bundle?): Boolean {
    if (bundle == null) return false
    if (bundle.getBoolean("success", false)) return true
    if (bundle.getBoolean("result", false)) return true
    if (bundle.getInt("code", -1) == 0) return true
    return false
}

private fun queryProviderValue(context: Context, uri: Uri, key: String, defaultValue: String): String {
    val resolver = context.contentResolver

    val callResult = runCatching {
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
            normalizeProviderValue(result?.getString(key))
                ?: normalizeProviderValue(result?.getString("value"))
                ?: normalizeProviderValue(result?.getString("result"))
                ?: normalizeProviderValue(result?.getString("data"))
                ?: result?.keySet()?.firstNotNullOfOrNull { bundleKey ->
                    normalizeProviderValue(result.get(bundleKey))
                }
        }
    }.getOrNull()
    if (!callResult.isNullOrEmpty()) return callResult

    return runCatching {
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use defaultValue
            val index = cursor.getColumnIndex(key)
            val value = if (index >= 0) cursor.getString(index) else cursor.getString(0)
            normalizeProviderValue(value) ?: defaultValue
        } ?: defaultValue
    }.getOrDefault(defaultValue)
}

private fun queryProviderBool(context: Context, uri: Uri, key: String, defaultValue: Boolean): Boolean {
    return queryProviderValue(context, uri, key, if (defaultValue) "1" else "0") == "1"
}

private fun queryLegacySetting(context: Context, key: String, defaultValue: String): String {
    val resolver = context.contentResolver
    return runCatching {
        val extras = Bundle().apply { putString("key", key) }
        val result = resolver.call(SETTINGS_URI, METHOD_DEV_QUERY, null, extras)
        normalizeProviderValue(result?.getString("value"))
            ?: normalizeProviderValue(result?.getString(key))
            ?: defaultValue
    }.getOrDefault(defaultValue)
}

private fun updateProviderValues(context: Context, uri: Uri, values: Map<String, String>): Boolean {
    val resolver = context.contentResolver

    val directSuccess = runCatching {
        val extras = Bundle().apply {
            values.forEach { (key, value) -> putString(key, value) }
        }
        isBundleSuccess(resolver.call(uri, METHOD_DEV_OPT, null, extras))
    }.getOrDefault(false)
    if (directSuccess) return true

    return values.all { (key, value) ->
        runCatching {
            val extras = Bundle().apply {
                putString("key", key)
                putString("value", value)
            }
            isBundleSuccess(resolver.call(uri, METHOD_DEV_OPT, null, extras))
        }.getOrDefault(false)
    }
}

private fun updateLegacySetting(context: Context, key: String, value: String): Boolean {
    val resolver = context.contentResolver
    return runCatching {
        val extras = Bundle().apply {
            putString("key", key)
            putString("value", value)
        }
        isBundleSuccess(resolver.call(SETTINGS_URI, METHOD_DEV_OPT, null, extras))
    }.getOrDefault(false)
}

@Composable
fun LabScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val dialectOptions = listOf(
        LabDialectOption(label = stringResource(R.string.Manchu_Chinese), id = "pth000"),
        LabDialectOption(label = stringResource(R.string.Elite_Chinese), id = null),
        LabDialectOption(label = stringResource(R.string.Standard_Chinese), id = null)
    )
    
    var distanceReminder by rememberSaveable { mutableStateOf(false) }
    var dialectRecognition by rememberSaveable { mutableStateOf(true) }
    var selectedDialect by rememberSaveable { mutableStateOf(dialectOptions.first().label) }
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
        val uris = listOf(
            SETTINGS_URI,
            DISTANCE_DETECT_URI,
            DISTANCE_ALARM_URI,
            DIALECT_SWITCH_URI,
            DIALECT_ID_URI,
            DIALECT_NAME_URI,
            ONE_SHOT_SWITCH_URI,
            SUPPORT_FULL_DUPLEX_URI,
            FULL_DUPLEX_MODE_URI
        )
        uris.forEach { uri ->
            runCatching { context.contentResolver.registerContentObserver(uri, true, observer) }
        }
        onDispose {
            runCatching { context.contentResolver.unregisterContentObserver(observer) }
        }
    }

    LaunchedEffect(context, refreshVersion) {
        distanceReminder = queryProviderBool(context, DISTANCE_DETECT_URI, "distanceDectect", false)
        dialectRecognition = queryProviderBool(context, DIALECT_SWITCH_URI, "dialectSwitch", true)
        selectedDialect = queryProviderValue(
            context,
            DIALECT_NAME_URI,
            "dialectName",
            dialectOptions.first().label
        )
        gestureControl = queryLegacySetting(context, "gesture_control", "1") == "1"
        quickCommands = queryProviderBool(context, ONE_SHOT_SWITCH_URI, "oneShotSwitch", true)
        supportFullDuplex = queryProviderBool(context, SUPPORT_FULL_DUPLEX_URI, "supportFullDuplex", true)
        continuousDialogue = supportFullDuplex &&
            queryProviderBool(context, FULL_DUPLEX_MODE_URI, "fullDuplexMode", false)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(R.color.cardcolor))
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SwitchInfoCard(
            title = "距离过近提醒",
            desc = "儿童靠近时，设备将提醒您注意设备安全距离",
            checked = distanceReminder,
            onCheckedChange = { checked ->
                if (updateProviderValues(context, DISTANCE_DETECT_URI, mapOf("distanceDectect" to if (checked) "1" else "0"))) {
                    distanceReminder = checked
                } else {
                    Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                }
            }
        )

        DialectCard(
            enabled = dialectRecognition,
            onEnabledChange = { checked ->
                if (updateProviderValues(
                        context,
                        DIALECT_SWITCH_URI,
                        mapOf(
                            "dialectSwitch" to if (checked) "1" else "0",
                            "sourceType" to "1"
                        )
                    )
                ) {
                    dialectRecognition = checked
                } else {
                    Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                }
            },
            selectedDialect = selectedDialect,
            onDialectChange = { dialect ->
                val option = dialectOptions.firstOrNull { it.label == dialect }
                if (option != null) {
                    val updateValues = mutableMapOf(
                        "dialectName" to option.label,
                        "sourceType" to "1"
                    )
                    option.id?.let { updateValues["dialectID"] = it }

                    val updateNameOk = updateProviderValues(context, DIALECT_NAME_URI, updateValues)
                    val updateIdOk = option.id?.let { dialectId ->
                        updateProviderValues(
                            context,
                            DIALECT_ID_URI,
                            mapOf(
                                "dialectID" to dialectId,
                                "sourceType" to "1"
                            )
                        )
                    } ?: true

                    if (updateNameOk && updateIdOk) {
                        selectedDialect = dialect
                    } else {
                        Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                    }
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
                if (updateProviderValues(context, ONE_SHOT_SWITCH_URI, mapOf("oneShotSwitch" to if (checked) "1" else "0"))) {
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
                    if (updateProviderValues(context, FULL_DUPLEX_MODE_URI, mapOf("fullDuplexMode" to if (checked) "1" else "0"))) {
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
        colors = CardDefaults.cardColors(containerColor = colorResource(R.color.white)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 18.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(end = 84.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorResource(R.color.textblack)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = desc,
                    fontSize = 13.sp,
                    color = Color(0xFF6B7280),
                    lineHeight = 18.sp
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
private fun DialectCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    selectedDialect: String,
    onDialectChange: (String) -> Unit
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
                        text = "方言识别",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorResource(R.color.textblack)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "目前支持普通话、上海话、粤语、西安话、成都话、郑州话、厦门话、长沙话。自定义可在设置中，敬请期待",
                        fontSize = 13.sp,
                        color = Color(0xFF6B7280),
                        lineHeight = 18.sp
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
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

            Text(
                text = "方言对话",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF6B7280)
            )
            Spacer(Modifier.height(10.dp))

            val options = listOf(
                stringResource(R.string.Manchu_Chinese),
                stringResource(R.string.Elite_Chinese),
                stringResource(R.string.Standard_Chinese),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                options.forEach { label ->
                    DialectChip(
                        label = label,
                        selected = label == selectedDialect,
                        enabled = enabled,
                        onClick = { onDialectChange(label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DialectChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {


    Card(
        shape = RoundedCornerShape(14.dp),

        modifier = Modifier
            .height(44.dp)
            .clickable(enabled = enabled, onClick = onClick)

    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.doubao),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, )
            Spacer(Modifier.width(8.dp))
            Image(
                painter = painterResource(
                    if (selected) R.drawable.lab_option_selected else R.drawable.lab_option_unselected
                ),
                contentDescription = null,
                modifier = Modifier.size(22.dp)
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
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorResource(R.color.textblack)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "开启后，在离屏幕0.25-2米范围内做出指定手势，即可在屏幕实现手势控制操作。小翼会根据手势动作学习。",
                        fontSize = 13.sp,
                        color = Color(0xFF6B7280),
                        lineHeight = 18.sp
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

            // Gesture gallery placeholders (project doesn't currently include hand assets).
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GestureTile(title = "点赞", hint = "一下", icon = R.drawable.ic_visibility)
                GestureTile(title = "滑动", hint = "上一页/下一页", icon = R.drawable.refresh)
                GestureTile(title = "握拳", hint = "确认", icon = R.drawable.lock)
                GestureTile(title = "比心", hint = "收藏", icon = R.drawable.path)
            }
        }
    }
}

@Composable
private fun GestureTile(title: String, hint: String, icon: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7F8)),
            modifier = Modifier.size(72.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = Color(0xFF4C73FF),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colorResource(R.color.textblack))
        Spacer(Modifier.height(2.dp))
        Text(text = hint, fontSize = 11.sp, color = Color(0xFF6B7280))
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
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorResource(R.color.textblack)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "开启后，可直接说“小翼管家”唤醒步骤，直接说“小翼管家”触发快捷指令",
                        fontSize = 13.sp,
                        color = Color(0xFF6B7280),
                        lineHeight = 18.sp
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

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val spacing = 12.dp
                val itemWidth = (maxWidth - spacing) / 2f
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    ChatPreviewCard(
                        title = "开启前",
                        bubbleLeft = "小翼管家",
                        bubbleRight = "今天天气",
                        icon = R.drawable.qrcode,
                        modifier = Modifier.width(itemWidth)
                    )
                    ChatPreviewCard(
                        title = "开启后",
                        bubbleLeft = "今天天气",
                        bubbleRight = "今天天气",
                        icon = R.drawable.qrcode,
                        modifier = Modifier.width(itemWidth)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            ValueRowLink(text = "关于小翼管家快捷指令")
        }
    }
}

@Composable
private fun ChatPreviewCard(
    title: String,
    bubbleLeft: String,
    bubbleRight: String,
    icon: Int,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7F8)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title, fontSize = 13.sp, color = Color(0xFF6B7280), fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(text = bubbleLeft, fontSize = 12.sp, color = colorResource(R.color.textblack))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF0FF)),
                ) {
                    Text(
                        text = bubbleRight,
                        fontSize = 12.sp,
                        color = Color(0xFF4C73FF),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
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
            Text(text = text, fontSize = 14.sp, color = colorResource(R.color.textblack), fontWeight = FontWeight.SemiBold)
            Icon(
                painter = painterResource(R.drawable.arrow_right),
                contentDescription = null,
                tint = Color(0xFFADB3BD),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF0F2F5, widthDp = 1200)
@Composable
private fun LabScreenPreview() {
    设置Theme {
        LabScreen()
    }
}
