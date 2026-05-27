package com.android.tv.settings

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tv.settings.ui.theme.设置Theme
import org.json.JSONArray

private const val METHOD_DEV_QUERY = "DEV_QUERY"
private const val METHOD_DEV_OPT = "DEV_OPT"
private const val DIALECT_SOURCE_TYPE_USER = "1"
private const val LAB_PROVIDER_TAG = "LabScreenProvider"
private const val DEFAULT_DIALECT_RECOGNITION_DESC =
    "目前支持普通话、上海话、粤语、西安话、成都话、郑州话、厦门话、长沙话。自定义可在设置中，敬请期待"
private const val DEFAULT_DIALECT_WAKE_UP_DESC =
    "开关开启后，可以通过普通话、上海话、粤语对“小翼管家”进行唤醒"

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
private val DIALECT_SWITCH_URIS: List<Uri> = contentUris("dialectSwitch", includeDeviceInfoFallback = true)
private val DIALECT_ID_URIS: List<Uri> = contentUris("dialectID", includeDeviceInfoFallback = true)
private val DIALECT_NAME_URIS: List<Uri> = contentUris("dialectName", includeDeviceInfoFallback = true)
private val DIALECT_WAKE_UP_SWITCH_URIS: List<Uri> = contentUris("dialectWakeUpSwitch", includeDeviceInfoFallback = true)
private val DIALECT_WAKE_UP_MODE_URIS: List<Uri> = contentUris("dialectWakeUpMode", includeDeviceInfoFallback = true)
private val DIALECT_WAKE_UP_DISPLAY_URIS: List<Uri> = contentUris("dialectWakeUpDisplay", includeDeviceInfoFallback = true)
private val ONE_SHOT_SWITCH_URIS: List<Uri> = contentUris("oneShotSwitch", includeDeviceInfoFallback = true)
private val SUPPORT_FULL_DUPLEX_URIS: List<Uri> = contentUris("supportFullDuplex", includeDeviceInfoFallback = true)
private val FULL_DUPLEX_MODE_URIS: List<Uri> = contentUris("fullDuplexMode", includeDeviceInfoFallback = true)

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

private fun normalizeDisplayText(value: String?): String? {
    val normalized = normalizeProviderValue(value) ?: return null
    if (normalized.equals("true", ignoreCase = true)) return null
    if (normalized.equals("false", ignoreCase = true)) return null
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

private fun parseDialectOptions(
    dialectListJson: String,
    fallbackOptions: List<LabDialectOption>
): List<LabDialectOption> {
    val parsedOptions = runCatching {
        val array = JSONArray(dialectListJson)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val label = item.optString("dialectName").trim()
                if (label.isEmpty()) continue
                val rawId = item.optString("dialectId").trim()
                val dialectId = rawId.ifEmpty {
                    if (label == "普通话") "pth000" else ""
                }.ifEmpty { null }
                add(LabDialectOption(label = label, id = dialectId))
            }
        }
    }.getOrDefault(emptyList())
    return parsedOptions.distinctBy { option -> option.label }.ifEmpty { fallbackOptions }
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
    val fallbackDialectOptions = listOf(
        LabDialectOption(label = stringResource(R.string.Manchu_Chinese), id = "pth000"),
        LabDialectOption(label = stringResource(R.string.Elite_Chinese), id = null),
        LabDialectOption(label = stringResource(R.string.Standard_Chinese), id = null)
    )

    var distanceReminder by rememberSaveable { mutableStateOf(false) }
    var dialectSelectionEnabled by rememberSaveable { mutableStateOf(true) }
    var dialectWakeUpSupported by rememberSaveable { mutableStateOf(true) }
    var dialectWakeUpEnabled by rememberSaveable { mutableStateOf(false) }
    var selectedDialect by rememberSaveable { mutableStateOf(fallbackDialectOptions.first().label) }
    var dialectRecognitionDesc by rememberSaveable { mutableStateOf(DEFAULT_DIALECT_RECOGNITION_DESC) }
    var dialectWakeUpDesc by rememberSaveable { mutableStateOf(DEFAULT_DIALECT_WAKE_UP_DESC) }
    var gestureControl by rememberSaveable { mutableStateOf(true) }
    var quickCommands by rememberSaveable { mutableStateOf(true) }
    var continuousDialogue by rememberSaveable { mutableStateOf(false) }
    var supportFullDuplex by rememberSaveable { mutableStateOf(true) }
    var dialectOptions by remember { mutableStateOf(fallbackDialectOptions) }
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
                DIALECT_SWITCH_URIS +
                DIALECT_ID_URIS +
                DIALECT_NAME_URIS +
                DIALECT_WAKE_UP_SWITCH_URIS +
                DIALECT_WAKE_UP_MODE_URIS +
                DIALECT_WAKE_UP_DISPLAY_URIS +
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
        dialectSelectionEnabled = queryProviderBool(context, DIALECT_SWITCH_URIS, "dialectSwitch", true)
        dialectWakeUpSupported = queryProviderBool(context, DIALECT_WAKE_UP_SWITCH_URIS, "dialectWakeUpSwitch", true)
        queryProviderValue(context, DIALECT_WAKE_UP_DISPLAY_URIS, "dialectWakeUpDisplay", "0")
        dialectWakeUpEnabled = queryProviderValue(
            context,
            DIALECT_WAKE_UP_MODE_URIS,
            "dialectWakeUpMode",
            "0"
        ) == "1"
        dialectRecognitionDesc = normalizeDisplayText(
            queryProviderValue(
                context,
                DEVICE_INFO_URIS,
                "dialectRecognitionDesc",
                DEFAULT_DIALECT_RECOGNITION_DESC
            )
        ) ?: DEFAULT_DIALECT_RECOGNITION_DESC
        dialectWakeUpDesc = normalizeDisplayText(
            queryProviderValue(
                context,
                DEVICE_INFO_URIS,
                "dialectWakeUpDesc",
                DEFAULT_DIALECT_WAKE_UP_DESC
            )
        ) ?: DEFAULT_DIALECT_WAKE_UP_DESC
        val refreshedOptions = parseDialectOptions(
            dialectListJson = queryProviderValue(context, DEVICE_INFO_URIS, "dialectList", ""),
            fallbackOptions = fallbackDialectOptions
        )
        dialectOptions = refreshedOptions
        val selectedDialectName = queryProviderValue(
            context,
            DIALECT_NAME_URIS,
            "dialectName",
            refreshedOptions.firstOrNull()?.label ?: fallbackDialectOptions.first().label
        )
        val selectedDialectId = queryProviderValue(context, DIALECT_ID_URIS, "dialectID", "")
        selectedDialect = refreshedOptions.firstOrNull { option ->
            option.label == selectedDialectName || (selectedDialectId.isNotBlank() && option.id == selectedDialectId)
        }?.label ?: normalizeDisplayText(selectedDialectName) ?: refreshedOptions.firstOrNull()?.label.orEmpty()
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

        DialectCard(
            recognitionDesc = dialectRecognitionDesc,
            dialectSelectionEnabled = dialectSelectionEnabled,
            wakeUpSupported = dialectWakeUpSupported,
            wakeUpEnabled = dialectWakeUpEnabled,
            wakeUpDesc = dialectWakeUpDesc,
            onWakeUpChange = { checked ->
                if (updateProviderValues(
                        context,
                        DIALECT_WAKE_UP_MODE_URIS,
                        mapOf(
                            "dialectWakeUpMode" to if (checked) "1" else "0",
                            "sourceType" to DIALECT_SOURCE_TYPE_USER
                        )
                    )
                ) {
                    dialectWakeUpEnabled = checked
                } else {
                    Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                }
            },
            dialectOptions = dialectOptions,
            selectedDialect = selectedDialect,
            onDialectChange = { dialect ->
                val option = dialectOptions.firstOrNull { it.label == dialect }
                if (option != null) {
                    val updateNameOk = updateProviderValues(
                        context,
                        DIALECT_NAME_URIS,
                        mapOf(
                            "dialectName" to option.label,
                            "sourceType" to DIALECT_SOURCE_TYPE_USER
                        )
                    )
                    val updateIdOk = option.id?.let { dialectId ->
                        updateProviderValues(
                            context,
                            DIALECT_ID_URIS,
                            mapOf(
                                "dialectID" to dialectId,
                                "sourceType" to DIALECT_SOURCE_TYPE_USER
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
private fun DialectCard(
    recognitionDesc: String,
    dialectSelectionEnabled: Boolean,
    wakeUpSupported: Boolean,
    wakeUpEnabled: Boolean,
    wakeUpDesc: String,
    onWakeUpChange: (Boolean) -> Unit,
    dialectOptions: List<LabDialectOption>,
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
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorResource(R.color.textblack)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = recognitionDesc,
                        fontSize = 20.sp,
                        color = Color(0xFF6B7280),
                        lineHeight = 28.sp
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 24.dp)
                ) {
                    Text(
                        text = "方言唤醒",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorResource(R.color.textblack)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = wakeUpDesc,
                        fontSize = 20.sp,
                        color = colorResource(R.color.textblack),
                        lineHeight = 28.sp
                    )
                }
                Switch(
                    checked = wakeUpEnabled,
                    onCheckedChange = onWakeUpChange,
                    enabled = wakeUpSupported,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF4C73FF),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFE0E0E0),
                        checkedBorderColor = Color.Transparent,
                        uncheckedBorderColor = Color.Transparent,
                        disabledCheckedThumbColor = Color.White,
                        disabledCheckedTrackColor = Color(0xFFB4C5FF),
                        disabledUncheckedThumbColor = Color.White,
                        disabledUncheckedTrackColor = Color(0xFFE0E0E0)
                    )
                )
            }

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(30.dp)
            ) {
                Text(
                    text = "方言对话",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorResource(R.color.textblack)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "小翼会使用您选定的语音与您对话",
                    fontSize = 20.sp,
                    color = colorResource(R.color.textblack),
                    lineHeight = 28.sp
                )
                val optionRows = dialectOptions.chunked(2)
                Column(modifier = Modifier.fillMaxWidth()) {
                    optionRows.forEachIndexed { index, rowOptions ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(77.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowOptions.forEach { option ->
                                DialectChip(
                                    modifier = Modifier
                                        .width(265.dp)
                                        .fillMaxHeight(),
                                    label = option.label,
                                    selected = option.label == selectedDialect,
                                    enabled = dialectSelectionEnabled,
                                    onClick = { onDialectChange(option.label) }
                                )
                            }
                            if (rowOptions.size == 1) {
                                Spacer(
                                    Modifier
                                        .width(265.dp)
                                        .fillMaxHeight()
                                )
                            }
                        }
                        if (index < optionRows.lastIndex) {
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialectChip(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = BorderStroke(1.dp, Color(0xFFDDDDDD)),
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)

    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxHeight()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.doubao),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(text = label, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = colorResource(R.color.textgray))
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
