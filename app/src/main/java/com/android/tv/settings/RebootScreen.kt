package com.android.tv.settings

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.tv.settings.ui.theme.设置Theme
import kotlinx.coroutines.flow.distinctUntilChanged

// Note: Despite the name, this file currently hosts the "节能设置" UI in this project.
@SuppressLint("MissingPermission")
@Composable
fun RebootScreen(onBack: () -> Unit) {
    var timedScreenOffEnabled by rememberSaveable { mutableStateOf(false) }
    var timedScreenOffTime by rememberSaveable { mutableStateOf("00:00/00:00") }
    var autoPowerEnabled by rememberSaveable { mutableStateOf(false) }
    var autoPowerTime by rememberSaveable { mutableStateOf("00:00/00:00") }

    var showScreenOffDialog by rememberSaveable { mutableStateOf(false) }
    var showPowerDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F2F5))
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SwitchCard(
            title = "定时关屏",
            desc = "开启后，设备将在指定时间完成熄屏和亮屏操作",
            checked = timedScreenOffEnabled,
            onCheckedChange = { timedScreenOffEnabled = it }
        )

        if (timedScreenOffEnabled) {
            ValueRowCard(
                title = "定时关屏时间设置",
                value = timedScreenOffTime,
                onClick = { showScreenOffDialog = true }
            )
        }

        SwitchCard(
            title = "自动开关机",
            desc = "开启后，设备插电的情况下将在指定时间自动开机和关机",
            checked = autoPowerEnabled,
            onCheckedChange = { autoPowerEnabled = it }
        )

        if (autoPowerEnabled) {
            ValueRowCard(
                title = "定时开关机时间设置",
                value = autoPowerTime,
                onClick = { showPowerDialog = true }
            )
        }
    }

    if (showScreenOffDialog) {
        val (offH, offM, onH, onM) = remember(timedScreenOffTime) { parseTimeRange(timedScreenOffTime) }
        TimeRangePickerDialog(
            title = "定时关屏时间设置",
            leftLabel = "熄屏时间",
            rightLabel = "亮屏时间",
            initialLeftHour = offH,
            initialLeftMinute = offM,
            initialRightHour = onH,
            initialRightMinute = onM,
            onConfirm = { lh, lm, rh, rm ->
                timedScreenOffTime = formatTimeRange(lh, lm, rh, rm)
                showScreenOffDialog = false
            },
            onDismiss = { showScreenOffDialog = false }
        )
    }

    if (showPowerDialog) {
        val (onH, onM, offH, offM) = remember(autoPowerTime) { parseTimeRange(autoPowerTime) }
        TimeRangePickerDialog(
            title = "定时开关机时间设置",
            leftLabel = "开机时间",
            rightLabel = "关机时间",
            initialLeftHour = onH,
            initialLeftMinute = onM,
            initialRightHour = offH,
            initialRightMinute = offM,
            onConfirm = { lh, lm, rh, rm ->
                autoPowerTime = formatTimeRange(lh, lm, rh, rm)
                showPowerDialog = false
            },
            onDismiss = { showPowerDialog = false }
        )
    }
}

@Composable
private fun SwitchCard(
    title: String,
    desc: String,
    checked: Boolean,
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

private fun formatTimeRange(lh: Int, lm: Int, rh: Int, rm: Int): String {
    return "${format2(lh)}:${format2(lm)}/${format2(rh)}:${format2(rm)}"
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
private fun TimeRangePickerDialog(
    title: String,
    leftLabel: String,
    rightLabel: String,
    initialLeftHour: Int,
    initialLeftMinute: Int,
    initialRightHour: Int,
    initialRightMinute: Int,
    onConfirm: (leftHour: Int, leftMinute: Int, rightHour: Int, rightMinute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    // Values shown in the wheel (00-23, 00-59)
    val hours = remember { (0..23).map { format2(it) } }
    val minutes = remember { (0..59).map { format2(it) } }

    var leftHour by rememberSaveable { mutableStateOf(initialLeftHour.coerceIn(0, 23)) }
    var leftMinute by rememberSaveable { mutableStateOf(initialLeftMinute.coerceIn(0, 59)) }
    var rightHour by rememberSaveable { mutableStateOf(initialRightHour.coerceIn(0, 23)) }
    var rightMinute by rememberSaveable { mutableStateOf(initialRightMinute.coerceIn(0, 59)) }

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
                        Text(
                            text = "取消",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4C73FF),
                            modifier = Modifier.clickable { onDismiss() }
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1A1D24)
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "确定",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4C73FF),
                            modifier = Modifier.clickable {
                                onConfirm(leftHour, leftMinute, rightHour, rightMinute)
                            }
                        )
                    }

                    Spacer(Modifier.height(22.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 44.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = leftLabel, fontSize = 18.sp, color = Color(0xFF8B909A))
                            Spacer(Modifier.height(18.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                WheelPicker(
                                    values = hours,
                                    initialIndex = leftHour,
                                    onIndexChanged = { leftHour = it },
                                    itemWidth = 104.dp
                                )
                                Spacer(Modifier.width(24.dp))
                                WheelPicker(
                                    values = minutes,
                                    initialIndex = leftMinute,
                                    onIndexChanged = { leftMinute = it },
                                    itemWidth = 104.dp
                                )
                            }
                        }

                        Spacer(Modifier.width(28.dp))

                        Text(
                            text = "起   --   止",
                            fontSize = 18.sp,
                            color = Color(0xFF8B909A),
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )

                        Spacer(Modifier.width(28.dp))

                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = rightLabel, fontSize = 18.sp, color = Color(0xFF8B909A))
                            Spacer(Modifier.height(18.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                WheelPicker(
                                    values = hours,
                                    initialIndex = rightHour,
                                    onIndexChanged = { rightHour = it },
                                    itemWidth = 104.dp
                                )
                                Spacer(Modifier.width(24.dp))
                                WheelPicker(
                                    values = minutes,
                                    initialIndex = rightMinute,
                                    onIndexChanged = { rightMinute = it },
                                    itemWidth = 104.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelPicker(
    values: List<String>,
    initialIndex: Int,
    onIndexChanged: (Int) -> Unit,
    itemWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val visibleCount = 7
    val itemHeight = 56.dp
    val padding = itemHeight * (visibleCount / 2).toFloat()

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

    Box(
        modifier = modifier
            .width(itemWidth)
            .height(itemHeight * visibleCount.toFloat()),
        contentAlignment = Alignment.Center
    ) {
        // Highlighted selection row.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(Color(0xFF4C73FF), RoundedCornerShape(12.dp))
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
