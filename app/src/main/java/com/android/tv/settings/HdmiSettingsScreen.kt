package com.android.tv.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.tv.settings.ui.theme.设置Theme

@Composable
fun HdmiSettingsScreen(modifier: Modifier = Modifier) {
    var hdmiAudioOutput by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    val screenOffTimeOptions = listOf("永不", "1分钟", "10分钟", "20分钟", "30分钟")
    var selectedScreenOffTime by remember { mutableStateOf("30分钟") }

    var showResolutionDialog by remember { mutableStateOf(false) }
    val resolutionOptions = listOf( "1920 x 1080","1680 x 1050", "1600 x 1200","1600 x 900","1400 x 1080",)
    var selectedResolution by remember { mutableStateOf("1920 x 1080") }

    var showScalingScreen by rememberSaveable { mutableStateOf(false) }
    var selectedScalingPercent by rememberSaveable { mutableStateOf(100) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            //.background(colorResource(R.color.cardcolor), shape = RoundedCornerShape(16.dp))
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card 1: Auto screen off

            Row(
                modifier = Modifier.clickable(
                    onClick = {
                        showDialog = true
                    }
                )

                    .fillMaxWidth()
                    .background(Color.White,RoundedCornerShape(16.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(all=26.dp)) {
                    Text(text = "自动熄屏时间", fontSize = 16.sp, color = Color.Black)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "当连接HDMI线后，若智屏在设定时间内无任何操作将自动熄屏，HDMI输出端保持正常输出",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        lineHeight = 16.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = selectedScreenOffTime, fontSize = 14.sp, color = Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.arrow_right),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.Gray
                )
            }


        // Card 2: Connected device audio output

            Row(
                modifier = Modifier.background(Color.White,RoundedCornerShape(16.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(all = 26.dp)) {
                    Text(text = "被连接设备输出声音", fontSize = 16.sp, color = Color.Black)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "当连接HDMI线后，开启开关，声音将从被连接设备输出；关闭开关，声音仍由智屏输出",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        lineHeight = 16.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = hdmiAudioOutput,
                    onCheckedChange = { hdmiAudioOutput = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF4356B6),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFE0E0E0),
                        checkedBorderColor = Color.Transparent,
                        uncheckedBorderColor = Color.Transparent
                    )
                )
            }


        // Card 3: HDMI output resolution

            Row(
                modifier = Modifier
                    .clickable { showResolutionDialog = true }
                    .background(Color.White,RoundedCornerShape(16.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(all = 26.dp)) {
                    Text(text = "HDMI输出分辨率", fontSize = 16.sp, color = Color.Black)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = selectedResolution, fontSize = 14.sp, color = Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.arrow_right),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.Gray
                )
            }


        // Card 4: HDMI display scaling

            Row(
                modifier = Modifier
                    .clickable { showScalingScreen = true }
                    .background(Color.White, RoundedCornerShape(16.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(all=26.dp)) {
                    Text(text = "HDMI显示缩放", fontSize = 16.sp, color = Color.Black)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = "${selectedScalingPercent}%", fontSize = 14.sp, color = Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.arrow_right),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.Gray
                )
            }

    }

    if (showDialog ) {
        AutoScreenOffDialog(
            options = screenOffTimeOptions,
            selectedOption = selectedScreenOffTime,
            onOptionSelected = {
                selectedScreenOffTime = it
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }

    if (showResolutionDialog || LocalInspectionMode.current) {
        HdmiResolutionDialog(
            options = resolutionOptions,
            selectedOption = selectedResolution,
            onOptionSelected = {
                selectedResolution = it
                showResolutionDialog = false
            },
            onDismiss = { showResolutionDialog = false }
        )
    }

    // Use a window-level Dialog so it overlays the Scaffold top bar too,
    // matching AutoScreenOffDialog behavior.
    if (showScalingScreen ) {
        Dialog(
            onDismissRequest = { showScalingScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            HdmiDisplayScalingScreen(
                initialPercent = selectedScalingPercent,
                onCancel = { showScalingScreen = false },
                onConfirm = {
                    selectedScalingPercent = it
                    showScalingScreen = false
                }
            )
        }
    }
}

@Composable
fun AutoScreenOffDialog(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.CenterEnd
        ) {
            Card(
                modifier = Modifier
                    .fillMaxHeight(1f)
                    .fillMaxWidth(0.8f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* consume */ },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {



                Column (modifier = Modifier.padding(vertical = 16.dp)) {
                    Text(
                        text = "自动熄屏时间",
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
                                .clickable { onOptionSelected(option) }
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
                        if (index < 2 || index==3) {
                            Divider(modifier = Modifier.padding(horizontal = 24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HdmiResolutionDialog(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.CenterEnd
        ) {
            Card(
                modifier = Modifier
                    .fillMaxHeight(1f)
                    .fillMaxWidth(0.8f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* consume */ },
                // Consume clicks on the panel so only background taps dismiss.
                // (List rows still handle clicks inside the Card.)
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    Text(
                        text = "HDMI输出分辨率",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                    //Divider()
                    options.forEachIndexed { index, option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOptionSelected(option) }
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
                        if (index <2 || index==3) {
                            Divider(modifier = Modifier.padding(horizontal = 24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HdmiDisplayScalingScreen(
    initialPercent: Int,
    onCancel: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var percent by rememberSaveable(initialPercent) { mutableStateOf(initialPercent) }

    // Layout tuned for smaller phone screens:
    // - Content scrolls if height is tight
    // - Bottom actions stay large and readable
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F2F5))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    painter = painterResource(R.drawable.back),
                    contentDescription = "返回"
                )
            }
            Text(
                text = "HDMI显示缩放",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.size(48.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7F8))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "缩放比例",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1A1D24)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "${percent}%",
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4C73FF)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "调整时请关注被连接设备上的画面变化",
                        fontSize = 14.sp,
                        color = Color(0xFF6B7280)
                    )
                    Spacer(Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Placeholder icon; reusing an existing drawable to avoid adding new resources.
                        Icon(
                            painter = painterResource(R.drawable.account),
                            contentDescription = null,
                            tint = Color(0xFF4C73FF),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Slider(
                            value = percent.toFloat(),
                            onValueChange = { percent = it.toInt() },
                            valueRange = 80f..120f,
                            steps = 39,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF4C73FF),
                                activeTrackColor = Color(0xFF4C73FF),
                                inactiveTrackColor = Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .height(56.dp)
                    .weight(1f)
            ) {
                Text("取消", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            Card(
                modifier = Modifier
                    .height(56.dp)
                    .weight(1f)
                    .clickable { onConfirm(percent) },
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF4C73FF))
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("确定", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF0F2F5, widthDp = 1200)
@Composable
fun HdmiSettingsScreenPreview() {
    设置Theme {
        HdmiSettingsScreen()
    }
}
