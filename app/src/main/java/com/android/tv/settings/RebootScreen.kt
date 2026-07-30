package com.android.tv.settings

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.tv.settings.ui.theme.设置Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri

private const val REBOOT_METHOD_DEV_OPT = "DEV_OPT"
private const val REBOOT_METHOD_DEV_QUERY = "DEV_QUERY"
private val REBOOT_DEVICE_INFO_URI: Uri = Uri.parse("content://com.android.zshd.deviceinfo/device_info")

// 节能设置 provider 契约（ZshdProvider，已反编译核实）：
//  DEV_QUERY 用 KEY_PW_AUTO_INFO="PowerAutoInfo" 一次返回下列 6 个值；
//  DEV_OPT 用下列 6 个 key 分别写入（autoScreenCtrl/autoPowerCtrl 值须 "0"/"1"，时间非空）。
private const val KEY_PW_AUTO_INFO = "PowerAutoInfo"
private const val KEY_PW_AUTO_SCREEN = "autoScreenCtrl"
private const val KEY_PW_AUTO_SCREEN_OFF_TIME = "screen_off_timer"
private const val KEY_PW_AUTO_SCREEN_ON_TIME = "screen_on_timer"
private const val KEY_PW_AUTO_POWER = "autoPowerCtrl"
private const val KEY_PW_AUTO_POWER_ON_TIME = "power_on_timer"
private const val KEY_PW_AUTO_POWER_OFF_TIME = "power_off_timer"

/** 查询节能设置当前值（DEV_QUERY PowerAutoInfo）；旧 provider 不支持该 key 时返回 null，页面保持默认。 */
private fun queryPowerAutoInfo(context: Context): Bundle? {
    return runCatching {
        val extras = Bundle().apply { putString(KEY_PW_AUTO_INFO, "") }
        context.contentResolver.call(REBOOT_DEVICE_INFO_URI, REBOOT_METHOD_DEV_QUERY, null, extras)
    }.getOrNull()
}

private fun isRebootBundleSuccess(bundle: Bundle?): Boolean {
    if (bundle == null) return false
    if (bundle.getBoolean("success", false)) return true
    if (bundle.getBoolean("result", false)) return true
    if (bundle.getInt("code", -1) == 0) return true
    return false
}

private fun rebootNormalizeValue(value: Any?): String? {
    val normalized = value?.toString()?.trim()
    if (normalized.isNullOrEmpty()) return null
    if (normalized.equals("null", ignoreCase = true)) return null
    return normalized
}

private fun updateRebootValues(context: Context, values: Map<String, String>): Boolean {
    val resolver = context.contentResolver
    return runCatching {
        val extras = Bundle().apply {
            values.forEach { (key, value) ->
                putString(key, value)
            }
        }
        val result = resolver.call(REBOOT_DEVICE_INFO_URI, REBOOT_METHOD_DEV_OPT, null, extras)
        isRebootBundleSuccess(result)
                || values.all { (key, value) ->
            rebootNormalizeValue(result?.getString(key)) == value ||
                    rebootNormalizeValue(result?.getString("value")) == value
        }
    }.getOrDefault(false)
}

// Note: Despite the name, this file currently hosts the "节能设置" UI in this project.
@SuppressLint("MissingPermission")
@Composable
fun RebootScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    var timedScreenOffEnabled by rememberSaveable { mutableStateOf(false) }
    var timedScreenOffTime by rememberSaveable { mutableStateOf("00:00/00:00") }
    var autoPowerEnabled by rememberSaveable { mutableStateOf(false) }
    var autoPowerTime by rememberSaveable { mutableStateOf("00:00/00:00") }

    var showScreenOffPicker by rememberSaveable { mutableStateOf(false) }
    var showScreenOnPicker by rememberSaveable { mutableStateOf(false) }
    var showPowerOnPicker by rememberSaveable { mutableStateOf(false) }
    var showPowerOffPicker by rememberSaveable { mutableStateOf(false) }

    // 进入页面时查询设备当前节能设置（DEV_QUERY PowerAutoInfo），反映真实状态。
    LaunchedEffect(Unit) {
        val info = withContext(Dispatchers.IO) { queryPowerAutoInfo(context) } ?: return@LaunchedEffect
        rebootNormalizeValue(info.getString(KEY_PW_AUTO_SCREEN))?.let { timedScreenOffEnabled = it == "1" }
        val scrOff = rebootNormalizeValue(info.getString(KEY_PW_AUTO_SCREEN_OFF_TIME))
        val scrOn = rebootNormalizeValue(info.getString(KEY_PW_AUTO_SCREEN_ON_TIME))
        if (scrOff != null || scrOn != null) {
            timedScreenOffTime = "${scrOff ?: "00:00"}/${scrOn ?: "00:00"}"
        }
        rebootNormalizeValue(info.getString(KEY_PW_AUTO_POWER))?.let { autoPowerEnabled = it == "1" }
        val pwrOn = rebootNormalizeValue(info.getString(KEY_PW_AUTO_POWER_ON_TIME))
        val pwrOff = rebootNormalizeValue(info.getString(KEY_PW_AUTO_POWER_OFF_TIME))
        if (pwrOn != null || pwrOff != null) {
            autoPowerTime = "${pwrOn ?: "00:00"}/${pwrOff ?: "00:00"}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.cardcolor))
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SwitchCard(
            title = "定时关屏",
            desc = "开启后，设备将在指定时间完成熄屏和亮屏操作",
            checked = timedScreenOffEnabled,
            switchModifier = Modifier.entryFocus(),
            onCheckedChange = {
                val values = buildMap {
                    put("autoScreenCtrl", if (it) "1" else "0")
                    if (it) {
                        val times = parseTimeRange(timedScreenOffTime)
                        put("screen_off_timer", "${format2(times[0])}:${format2(times[1])}")
                        put("screen_on_timer", "${format2(times[2])}:${format2(times[3])}")
                    }
                }
                if (updateRebootValues(context, values)) {
                    timedScreenOffEnabled = it
                } else {
                    Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                }
            }
        )

        if (timedScreenOffEnabled) {
            // 拆成两行，各显示 hh:mm（点任一行打开同一个时间选择器）。
            ValueRowCard(
                title = "熄屏时间",
                value = rangeLeft(timedScreenOffTime),
                onClick = { showScreenOffPicker = true }
            )
            ValueRowCard(
                title = "亮屏时间",
                value = rangeRight(timedScreenOffTime),
                onClick = { showScreenOnPicker = true }
            )
        }

        SwitchCard(
            title = "自动开关机",
            desc = "开启后，设备插电的情况下将在指定时间自动开机和关机",
            checked = autoPowerEnabled,
            onCheckedChange = {
                val values = buildMap {
                    put("autoPowerCtrl", if (it) "1" else "0")
                    if (it) {
                        val times = parseTimeRange(autoPowerTime)
                        put("power_on_timer", "${format2(times[0])}:${format2(times[1])}")
                        put("power_off_timer", "${format2(times[2])}:${format2(times[3])}")
                    }
                }
                if (updateRebootValues(context, values)) {
                    autoPowerEnabled = it
                } else {
                    Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                }
            }
        )

        if (autoPowerEnabled) {
            // 拆成两行，各显示 hh:mm（点任一行打开同一个时间选择器）。
            ValueRowCard(
                title = "开机时间",
                value = rangeLeft(autoPowerTime),
                onClick = { showPowerOnPicker = true }
            )
            ValueRowCard(
                title = "关机时间",
                value = rangeRight(autoPowerTime),
                onClick = { showPowerOffPicker = true }
            )
        }
    }

    // 熄屏时间：独立单时间弹窗（只调熄屏，亮屏保持不变）。
    if (showScreenOffPicker) {
        val (offH, offM) = remember(timedScreenOffTime) { parseSingleTime(rangeLeft(timedScreenOffTime)) }
        SingleTimePickerDialog(
            title = "熄屏时间",
            initialHour = offH,
            initialMinute = offM,
            onConfirm = { h, m ->
                val newOff = "${format2(h)}:${format2(m)}"
                val onTime = rangeRight(timedScreenOffTime)
                val values = mapOf(
                    "screen_off_timer" to newOff,
                    "screen_on_timer" to onTime,
                    "autoScreenCtrl" to if (timedScreenOffEnabled) "1" else "0"
                )
                if (updateRebootValues(context, values)) {
                    timedScreenOffTime = "$newOff/$onTime"
                    showScreenOffPicker = false
                } else {
                    Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showScreenOffPicker = false }
        )
    }

    // 亮屏时间：独立单时间弹窗（只调亮屏，熄屏保持不变）。
    if (showScreenOnPicker) {
        val (onH, onM) = remember(timedScreenOffTime) { parseSingleTime(rangeRight(timedScreenOffTime)) }
        SingleTimePickerDialog(
            title = "亮屏时间",
            initialHour = onH,
            initialMinute = onM,
            onConfirm = { h, m ->
                val newOn = "${format2(h)}:${format2(m)}"
                val offTime = rangeLeft(timedScreenOffTime)
                val values = mapOf(
                    "screen_off_timer" to offTime,
                    "screen_on_timer" to newOn,
                    "autoScreenCtrl" to if (timedScreenOffEnabled) "1" else "0"
                )
                if (updateRebootValues(context, values)) {
                    timedScreenOffTime = "$offTime/$newOn"
                    showScreenOnPicker = false
                } else {
                    Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showScreenOnPicker = false }
        )
    }

    // 开机时间：独立单时间弹窗（只调开机，关机保持不变）。
    if (showPowerOnPicker) {
        val (onH, onM) = remember(autoPowerTime) { parseSingleTime(rangeLeft(autoPowerTime)) }
        SingleTimePickerDialog(
            title = "开机时间",
            initialHour = onH,
            initialMinute = onM,
            onConfirm = { h, m ->
                val newOn = "${format2(h)}:${format2(m)}"
                val offTime = rangeRight(autoPowerTime)
                val values = mapOf(
                    "power_on_timer" to newOn,
                    "power_off_timer" to offTime,
                    "autoPowerCtrl" to if (autoPowerEnabled) "1" else "0"
                )
                if (updateRebootValues(context, values)) {
                    autoPowerTime = "$newOn/$offTime"
                } else {
                    Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                }
                showPowerOnPicker = false
            },
            onDismiss = { showPowerOnPicker = false }
        )
    }

    // 关机时间：独立单时间弹窗（只调关机，开机保持不变）。
    if (showPowerOffPicker) {
        val (offH, offM) = remember(autoPowerTime) { parseSingleTime(rangeRight(autoPowerTime)) }
        SingleTimePickerDialog(
            title = "关机时间",
            initialHour = offH,
            initialMinute = offM,
            onConfirm = { h, m ->
                val newOff = "${format2(h)}:${format2(m)}"
                val onTime = rangeLeft(autoPowerTime)
                val values = mapOf(
                    "power_on_timer" to onTime,
                    "power_off_timer" to newOff,
                    "autoPowerCtrl" to if (autoPowerEnabled) "1" else "0"
                )
                if (updateRebootValues(context, values)) {
                    autoPowerTime = "$onTime/$newOff"
                    showPowerOffPicker = false
                } else {
                    Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showPowerOffPicker = false }
        )
    }
}

@Composable
private fun SwitchCard(
    title: String,
    desc: String,
    checked: Boolean,
    switchModifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit
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
            Spacer(Modifier.width(16.dp))
            Switch(
                modifier = switchModifier,
                checked = checked,
                onCheckedChange = onCheckedChange,
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
private fun ValueRowCard(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp),
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
                color = Color(0xFF8B909A)
            )
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

@Composable
private fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selectedOption: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxWidth(1f), contentAlignment = Alignment.CenterEnd) {
            Card(
                modifier = Modifier
                    .fillMaxHeight(1f)
                    .fillMaxWidth(0.8f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                    Divider()
                    options.forEachIndexed { index, option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(option) }
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = option, modifier = Modifier.weight(1f), fontSize = 16.sp)
                            if (option == selectedOption) {
                                Text(
                                    text = "✓",
                                    color = Color(0xFF4356B6),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                        }
                        if (index != options.lastIndex) {
                            Divider(modifier = Modifier.padding(horizontal = 24.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun format2(n: Int): String = n.toString().padStart(2, '0')

/** 取内部存储 "HH:MM/HH:MM" 的左半段(hh:mm)。 */
private fun rangeLeft(value: String): String = value.substringBefore('/', "00:00").ifBlank { "00:00" }

/** 取内部存储 "HH:MM/HH:MM" 的右半段(hh:mm)。 */
private fun rangeRight(value: String): String = value.substringAfter('/', "00:00").ifBlank { "00:00" }

/** 解析单个 "HH:MM" 成 [hour, minute]。 */
private fun parseSingleTime(value: String): IntArray {
    val parts = value.split(':')
    return intArrayOf(
        parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 0,
        parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0,
    )
}

private fun parseTimeRange(value: String): IntArray {
    // Expected: "HH:MM/HH:MM"
    return runCatching {
        val parts = value.split('/')
        val left = parts.getOrNull(0)?.split(':') ?: emptyList()
        val right = parts.getOrNull(1)?.split(':') ?: emptyList()
        intArrayOf(
            left.getOrNull(0)?.toIntOrNull() ?: 0,
            left.getOrNull(1)?.toIntOrNull() ?: 0,
            right.getOrNull(0)?.toIntOrNull() ?: 0,
            right.getOrNull(1)?.toIntOrNull() ?: 0,
        )
    }.getOrElse { intArrayOf(0, 0, 0, 0) }
}

@Composable
private fun SingleTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val hours = remember { (0..23).map { format2(it) } }
    val minutes = remember { (0..59).map { format2(it) } }

    var hour by rememberSaveable { mutableStateOf(initialHour.coerceIn(0, 23)) }
    var minute by rememberSaveable { mutableStateOf(initialMinute.coerceIn(0, 59)) }

    // 遥控器焦点：取消 | 时 | 分 | 确定，左右切换；时/分上下改值。进弹窗聚焦小时轮。
    val cancelFocus = remember { FocusRequester() }
    val hourFocus = remember { FocusRequester() }
    val minuteFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { hourFocus.requestFocus() } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxWidth(1f), contentAlignment = Alignment.CenterEnd) {
            Card(
                modifier = Modifier
                    .fillMaxHeight(1f)
                    .fillMaxWidth(0.8f),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 22.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DialogActionText(
                            text = "取消",
                            focusRequester = cancelFocus,
                            onClick = onDismiss,
                            onMoveRight = { hourFocus.requestFocus() }
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1A1D24)
                        )
                        Spacer(Modifier.weight(1f))
                        DialogActionText(
                            text = "确定",
                            focusRequester = confirmFocus,
                            onClick = { onConfirm(hour, minute) },
                            onMoveLeft = { minuteFocus.requestFocus() }
                        )
                    }

                    Spacer(Modifier.height(22.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WheelPicker(
                            values = hours,
                            initialIndex = hour,
                            onIndexChanged = { hour = it },
                            itemWidth = 104.dp,
                            focusRequester = hourFocus,
                            onMoveLeft = { cancelFocus.requestFocus() },
                            onMoveRight = { minuteFocus.requestFocus() }
                        )
                        Spacer(Modifier.width(24.dp))
                        WheelPicker(
                            values = minutes,
                            initialIndex = minute,
                            onIndexChanged = { minute = it },
                            itemWidth = 104.dp,
                            focusRequester = minuteFocus,
                            onMoveLeft = { hourFocus.requestFocus() },
                            onMoveRight = { confirmFocus.requestFocus() }
                        )
                    }
                }
            }
        }
    }
}

/** 弹窗里"取消/确定"这类可聚焦文字按钮：带焦点高亮 + 左右移焦回调。 */
@Composable
private fun DialogActionText(
    text: String,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onMoveLeft: (() -> Unit)? = null,
    onMoveRight: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text = text,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF4C73FF),
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { onMoveLeft?.invoke(); onMoveLeft != null }
                    Key.DirectionRight -> { onMoveRight?.invoke(); onMoveRight != null }
                    Key.DirectionCenter, Key.Enter -> { onClick(); true }
                    else -> false
                }
            }
            .focusable()
            .background(
                color = if (focused) Color(0xFFEAF0FF) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelPicker(
    values: List<String>,
    initialIndex: Int,
    onIndexChanged: (Int) -> Unit,
    itemWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onMoveLeft: () -> Unit = {},
    onMoveRight: () -> Unit = {},
) {
    val visibleCount = 7
    val itemHeight = 56.dp
    val padding = itemHeight * (visibleCount / 2).toFloat()

    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }

    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex.coerceIn(0, values.lastIndex))
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = state)

    val selectedIndex by remember(state) {
        derivedStateOf {
            val layoutInfo = state.layoutInfo
            val visible = layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) return@derivedStateOf state.firstVisibleItemIndex.coerceIn(0, values.lastIndex)
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            val closest = visible.minByOrNull { item ->
                kotlin.math.abs((item.offset + item.size / 2) - viewportCenter)
            }
            (closest?.index ?: state.firstVisibleItemIndex).coerceIn(0, values.lastIndex)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { selectedIndex }
            .distinctUntilChanged()
            .collect { onIndexChanged(it) }
    }

    fun scrollTo(target: Int) {
        val t = target.coerceIn(0, values.lastIndex)
        scope.launch { state.animateScrollToItem(t) }
    }

    Box(
        modifier = modifier
            .width(itemWidth)
            .height(itemHeight * visibleCount.toFloat())
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionUp -> { scrollTo(selectedIndex - 1); true }
                    Key.DirectionDown -> { scrollTo(selectedIndex + 1); true }
                    Key.DirectionLeft -> { onMoveLeft(); true }
                    Key.DirectionRight -> { onMoveRight(); true }
                    else -> false
                }
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        // Highlighted selection row（聚焦时加白色描边，明确遥控器当前所在的轮）。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(Color(0xFF4C73FF), RoundedCornerShape(12.dp))
                .then(
                    if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp))
                    else Modifier
                )
        )

        LazyColumn(
            state = state,
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = padding)
        ) {
            items(values.size) { index ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = values[index],
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) Color.White else Color(0xFF1A1D24)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF0F2F5, widthDp = 1200)
@Composable
private fun RebootScreenPreview() {
    设置Theme {
        RebootScreen(onBack = {})
    }
}
