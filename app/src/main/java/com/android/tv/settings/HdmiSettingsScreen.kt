package com.android.tv.settings

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

@Composable
fun HdmiSettingsScreen(modifier: Modifier = Modifier) {
    var hdmiAudioOutput by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    val screenOffTimeOptions = listOf("永不", "1分钟", "10分钟", "20分钟", "30分钟")
    var selectedScreenOffTime by remember { mutableStateOf("30分钟") }

    Column(
        modifier = modifier
            .fillMaxSize()
            //.padding(24.dp)
            //.background(colorResource(R.color.cardcolor), shape = RoundedCornerShape(16.dp))
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card 1: Auto screen off

            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "HDMI输出分辨率", fontSize = 16.sp, color = Color.Black)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = "1920 x 1080", fontSize = 14.sp, color = Color.Gray)
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
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "HDMI显示缩放", fontSize = 16.sp, color = Color.Black)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = "100%", fontSize = 14.sp, color = Color.Gray)
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
}

@Composable
fun AutoScreenOffDialog(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxWidth(1f), contentAlignment = Alignment.CenterEnd){
        Card(
            modifier = Modifier.fillMaxHeight(1f).fillMaxWidth(0.8f),
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
        }}
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF0F2F5, widthDp = 1200)
@Composable
fun HdmiSettingsScreenPreview() {
    设置Theme {
        HdmiSettingsScreen()
    }
}
