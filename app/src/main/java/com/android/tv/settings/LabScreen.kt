package com.android.tv.settings

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tv.settings.ui.theme.设置Theme

@Composable
fun LabScreen(modifier: Modifier = Modifier) {
    var distanceReminder by rememberSaveable { mutableStateOf(false) }
    var dialectRecognition by rememberSaveable { mutableStateOf(true) }
    var selectedDialect by rememberSaveable { mutableStateOf("普通话") }
    var gestureControl by rememberSaveable { mutableStateOf(true) }
    var quickCommands by rememberSaveable { mutableStateOf(true) }
    var continuousDialogue by rememberSaveable { mutableStateOf(false) }

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
            onCheckedChange = { distanceReminder = it }
        )

        DialectCard(
            enabled = dialectRecognition,
            onEnabledChange = { dialectRecognition = it },
            selectedDialect = selectedDialect,
            onDialectChange = { selectedDialect = it }
        )

        GestureCard(
            checked = gestureControl,
            onCheckedChange = { gestureControl = it }
        )

        QuickCommandsCard(
            checked = quickCommands,
            onCheckedChange = { quickCommands = it }
        )

        SwitchInfoCard(
            title = "连续对话",
            desc = "开启后，进行连续对话时无需重复唤醒一段时间，小翼将保持聆听状态",
            checked = continuousDialogue,
            onCheckedChange = { continuousDialogue = it }
        )
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
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

            val options = listOf("普通话", "上海话", "粤语", "西安话")
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
    val bg = when {
        !enabled -> Color(0xFFF2F3F5)
        selected -> Color(0xFFEAF0FF)
        else -> Color(0xFFF7F7F8)
    }
    val fg = when {
        !enabled -> Color(0xFF9CA3AF)
        selected -> Color(0xFF4C73FF)
        else -> Color(0xFF1A1D24)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        modifier = Modifier
            .height(44.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.account),
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fg)
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Text(text = "✓", fontSize = 14.sp, color = fg, fontWeight = FontWeight.Bold)
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
