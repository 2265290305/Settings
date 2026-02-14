package com.android.tv.settings

import android.annotation.SuppressLint
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.tv.settings.ui.theme.设置Theme

// Note: Despite the name, this file currently hosts the "节能设置" UI in this project.
@SuppressLint("MissingPermission")
@Composable
fun RebootScreen(onBack: () -> Unit) {
    val screenOffTimeOptions = remember {
        listOf(
            "00:00/00:00",
            "22:00/06:00",
            "23:00/07:00",
            "00:00/08:00",
        )
    }
    val powerOnOffTimeOptions = remember {
        listOf(
            "00:00/00:00",
            "07:30/23:00",
            "08:00/22:30",
            "09:00/23:30",
        )
    }

    var timedScreenOffEnabled by rememberSaveable { mutableStateOf(false) }
    var timedScreenOffTime by rememberSaveable { mutableStateOf(screenOffTimeOptions.first()) }
    var autoPowerEnabled by rememberSaveable { mutableStateOf(false) }
    var autoPowerTime by rememberSaveable { mutableStateOf(powerOnOffTimeOptions.first()) }

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
        SingleChoiceDialog(
            title = "定时关屏时间设置",
            options = screenOffTimeOptions,
            selectedOption = timedScreenOffTime,
            onSelect = {
                timedScreenOffTime = it
                showScreenOffDialog = false
            },
            onDismiss = { showScreenOffDialog = false }
        )
    }

    if (showPowerDialog) {
        SingleChoiceDialog(
            title = "定时开关机时间设置",
            options = powerOnOffTimeOptions,
            selectedOption = autoPowerTime,
            onSelect = {
                autoPowerTime = it
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
                    color = Color(0xFF1A1D24)
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
                color = Color(0xFF1A1D24)
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

@Preview(showBackground = true, backgroundColor = 0xFFF0F2F5, widthDp = 1200)
@Composable
private fun RebootScreenPreview() {
    设置Theme {
        RebootScreen(onBack = {})
    }
}

