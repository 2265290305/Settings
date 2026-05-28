package com.android.tv.settings

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tv.settings.ui.theme.设置Theme
import org.json.JSONArray

private const val METHOD_DEV_QUERY = "DEV_QUERY"
private const val METHOD_DEV_OPT = "DEV_OPT"
private const val DIALECT_SOURCE_TYPE_USER = "1"
private const val DIALECT_PROVIDER_TAG = "DialectScreenProvider"
private const val DEFAULT_DIALECT_RECOGNITION_DESC =
    "目前支持普通话、上海话、粤语、西安话、成都话、郑州话、厦门话、长沙话、合肥话识别，其他方言持续更新中，敬请期待"
private const val DEFAULT_DIALECT_WAKE_UP_DESC =
    "普通话唤醒已默认开启，您还能添加一种方言唤醒天翼智屏"

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
private val DIALECT_SWITCH_URIS: List<Uri> = contentUris("dialectSwitch", includeDeviceInfoFallback = true)
private val DIALECT_ID_URIS: List<Uri> = contentUris("dialectID", includeDeviceInfoFallback = true)
private val DIALECT_NAME_URIS: List<Uri> = contentUris("dialectName", includeDeviceInfoFallback = true)
private val DIALECT_WAKE_UP_SWITCH_URIS: List<Uri> = contentUris("dialectWakeUpSwitch", includeDeviceInfoFallback = true)
private val DIALECT_WAKE_UP_MODE_URIS: List<Uri> = contentUris("dialectWakeUpMode", includeDeviceInfoFallback = true)
private val DIALECT_WAKE_UP_DISPLAY_URIS: List<Uri> = contentUris("dialectWakeUpDisplay", includeDeviceInfoFallback = true)

private data class DialectOption(
    val label: String,
    val id: String?
)

private data class DeviceToneOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageRes: Int
)

private fun defaultDeviceToneOptions(): List<DeviceToneOption> = listOf(
    DeviceToneOption("energetic_girl", "元气少女", "女 | 青年 | 活力", R.drawable.doubao),
    DeviceToneOption("playful_girl", "俏皮少女", "女 | 青年 | 活泼", R.drawable.shanghai),
    DeviceToneOption("warm_service", "暖心客服", "女 | 青年 | 亲切", R.drawable.xiamen),
    DeviceToneOption("gentle_service", "温柔客服", "女 | 青年 | 亲切", R.drawable.xian),
    DeviceToneOption("cute_kid", "童趣萌娃", "女 | 少年 | 甜糯", R.drawable.changsha),
    DeviceToneOption("sleep_girl", "助眠少女", "女 | 青年 | 舒缓", R.drawable.chengdu),
    DeviceToneOption("sweet_girl", "清甜少女", "女 | 青年 | 清甜", R.drawable.zhengzhou),
    DeviceToneOption("smart_goddess", "知性女神", "女 | 青年 | 知性", R.drawable.yueyu)
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
        DIALECT_PROVIDER_TAG,
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
    fallbackOptions: List<DialectOption>
): List<DialectOption> {
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
                add(DialectOption(label = label, id = dialectId))
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

@Composable
fun DialectSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val fallbackDialectOptions = listOf(
        DialectOption(label = stringResource(R.string.Manchu_Chinese), id = "pth000"),
        DialectOption(label = stringResource(R.string.shenyang_cn), id = null),
        DialectOption(label = stringResource(R.string.xiamen_cn), id = null),
        DialectOption(label = stringResource(R.string.chengdu_cn), id = null),
        DialectOption(label = stringResource(R.string.changsha_cn), id = null),
        DialectOption(label = stringResource(R.string.zhengzhou_cn), id = null),
        DialectOption(label = stringResource(R.string.xian_cn), id = null),
        DialectOption(label = stringResource(R.string.yueyu_cn), id = null),
        DialectOption(label = stringResource(R.string.Elite_Chinese), id = null)

    )
    val deviceToneOptions = remember { defaultDeviceToneOptions() }

    var dialectSelectionEnabled by rememberSaveable { mutableStateOf(true) }
    var dialectWakeUpSupported by rememberSaveable { mutableStateOf(true) }
    var dialectWakeUpEnabled by rememberSaveable { mutableStateOf(false) }
    var selectedDialect by rememberSaveable { mutableStateOf(fallbackDialectOptions.first().label) }
    var selectedDeviceToneId by rememberSaveable { mutableStateOf(deviceToneOptions.first().id) }
    var pendingDeviceToneId by rememberSaveable { mutableStateOf(deviceToneOptions.first().id) }
    var dialectRecognitionDesc by rememberSaveable { mutableStateOf(DEFAULT_DIALECT_RECOGNITION_DESC) }
    var dialectWakeUpDesc by rememberSaveable { mutableStateOf(DEFAULT_DIALECT_WAKE_UP_DESC) }
    var dialectOptions by remember { mutableStateOf(fallbackDialectOptions) }
    var showDeviceTonePage by rememberSaveable { mutableStateOf(false) }
    var refreshVersion by remember { mutableStateOf(0) }

    val selectedDeviceTone = deviceToneOptions.firstOrNull { it.id == selectedDeviceToneId } ?: deviceToneOptions.first()

    BackHandler(enabled = showDeviceTonePage) {
        showDeviceTonePage = false
    }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                refreshVersion++
            }
        }
        val uris = (
            DEVICE_INFO_URIS +
                DIALECT_SWITCH_URIS +
                DIALECT_ID_URIS +
                DIALECT_NAME_URIS +
                DIALECT_WAKE_UP_SWITCH_URIS +
                DIALECT_WAKE_UP_MODE_URIS +
                DIALECT_WAKE_UP_DISPLAY_URIS
            ).distinct()
        uris.forEach { uri ->
            runCatching { context.contentResolver.registerContentObserver(uri, true, observer) }
        }
        onDispose {
            runCatching { context.contentResolver.unregisterContentObserver(observer) }
        }
    }

    LaunchedEffect(context, refreshVersion) {
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
    }

    AnimatedContent(
        targetState = showDeviceTonePage,
        transitionSpec = {
            val enter = slideInHorizontally(
                animationSpec = tween(280, easing = FastOutSlowInEasing),
                initialOffsetX = { width -> if (targetState) width / 3 else -width / 4 }
            ) + fadeIn(animationSpec = tween(180))
            val exit = slideOutHorizontally(
                animationSpec = tween(180, easing = FastOutLinearInEasing),
                targetOffsetX = { width -> if (targetState) -width / 4 else width / 3 }
            ) + fadeOut(animationSpec = tween(120))
            enter togetherWith exit
        },
        label = "dialect-device-tone-page"
    ) { showingDeviceTonePage ->
        if (showingDeviceTonePage) {
            DeviceToneSelectionScreen(
                modifier = modifier,
                options = deviceToneOptions,
                selectedToneId = pendingDeviceToneId,
                onBack = { showDeviceTonePage = false },
                onSelect = { pendingDeviceToneId = it },
                onCancel = { showDeviceTonePage = false },
                onConfirm = {
                    selectedDeviceToneId = pendingDeviceToneId
                    showDeviceTonePage = false
                }
            )
        } else {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(color = colorResource(R.color.cardcolor))
                    .clip(RoundedCornerShape(18.dp))
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DialectCard(
                    recognitionDesc = dialectRecognitionDesc,
                    dialectSelectionEnabled = dialectSelectionEnabled,
                    wakeUpSupported = dialectWakeUpSupported,
                    wakeUpEnabled = dialectWakeUpEnabled,
                    wakeUpDesc = dialectWakeUpDesc,
                    selectedDeviceToneTitle = selectedDeviceTone.title,
                    onOpenDeviceTone = {
                        pendingDeviceToneId = selectedDeviceToneId
                        showDeviceTonePage = true
                    },
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
            }
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
    selectedDeviceToneTitle: String,
    onOpenDeviceTone: () -> Unit,
    onWakeUpChange: (Boolean) -> Unit,
    dialectOptions: List<DialectOption>,
    selectedDialect: String,
    onDialectChange: (String) -> Unit
) {

        Column(modifier = Modifier
            .fillMaxWidth()
            ) {
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
                        fontSize = 16.sp,
                        color = colorResource(R.color.textgray),
                        lineHeight = 28.sp
                    )
                }
            }
            Spacer(Modifier.height(15.dp))
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
                        fontSize = 16.sp,
                        color = colorResource(R.color.textgray),
                        lineHeight = 28.sp
                    )
                }
                Text(text = selectedDialect, fontSize = 20.sp,
                    color = Color(0xFF6B7280),
                    lineHeight = 28.sp)
                Spacer(Modifier.width(10.dp))
                Image(painter = painterResource(R.drawable.path),
                    contentDescription = "path")

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
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .clickable(onClick = onOpenDeviceTone)
                    .padding(horizontal = 24.dp, vertical = 24.dp)) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = "设备音色",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorResource(R.color.textblack)
                )

                Text(text = selectedDeviceToneTitle, fontSize = 20.sp,
                    color = Color(0xFF6B7280),
                    lineHeight = 28.sp)
                Spacer(Modifier.width(10.dp))
                Image(painter = painterResource(R.drawable.path),
                    contentDescription = "path")
            }
        
    }
}

@Composable
private fun DeviceToneSelectionScreen(
    modifier: Modifier = Modifier,
    options: List<DeviceToneOption>,
    selectedToneId: String,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF2F3F6))
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.back),
                    contentDescription = "返回",
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = "设备音色",
                modifier = Modifier.align(Alignment.Center),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF171A21),
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            options.chunked(2).forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    rowOptions.forEach { option ->
                        DeviceToneOptionCard(
                            modifier = Modifier.weight(1f),
                            option = option,
                            selected = option.id == selectedToneId,
                            onClick = { onSelect(option.id) }
                        )
                    }
                    if (rowOptions.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            DeviceToneSecondaryButton(text = "取消", onClick = onCancel)
            Spacer(Modifier.width(28.dp))
            DeviceTonePrimaryButton(text = "确定", onClick = onConfirm)
        }
    }
}

@Composable
private fun DeviceToneOptionCard(
    modifier: Modifier = Modifier,
    option: DeviceToneOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 26.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(option.imageRes),
                contentDescription = option.title,
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(26.dp))
            )
            Spacer(Modifier.width(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.title,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF171A21)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = option.subtitle,
                    fontSize = 20.sp,
                    color = Color(0xFF8A8F99)
                )
            }
            DeviceToneSelectionIndicator(selected = selected)
        }
    }
}

@Composable
private fun DeviceToneSelectionIndicator(selected: Boolean) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) Color(0xFF4B79FF) else Color(0xFFE2E3E7),
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "device-tone-selected-color"
    )

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Text(
                text = "✓",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DeviceToneSecondaryButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(240.dp)
            .height(74.dp)
            .clip(RoundedCornerShape(37.dp))
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color(0xFF171A21),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DeviceTonePrimaryButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(240.dp)
            .height(74.dp)
            .clip(RoundedCornerShape(37.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF57A7FF), Color(0xFF3F47F3))
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
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
    val lanmap = mapOf<String, Int>(
        "普通话" to R.drawable.doubao,
        "沈阳话" to R.drawable.shenyang,
        "厦门话" to R.drawable.xiamen,
        "成都话" to R.drawable.chengdu,
        "长沙话" to R.drawable.changsha,
        "郑州话" to R.drawable.zhengzhou,
        "西安话" to R.drawable.xian,
        "粤语" to R.drawable.yueyu,
        "上海话" to R.drawable.shanghai
    )
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFDDDDDD)),
        modifier = modifier.clickable(enabled = enabled, onClick = onClick)
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
                lanmap.get(label)?.let {
                    Image(
                        painter = painterResource(it),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorResource(R.color.textgray)
                )
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

@Preview(showBackground = true, backgroundColor = 0xFFF0F2F5, widthDp = 1280, heightDp = 1200)
@Composable
private fun DialectSettingsScreenPreview() {
    设置Theme {
        DialectSettingsScreen()
    }
}
