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
fun ScreenSaverSettingsScreen(modifier: Modifier = Modifier) {
    val options = remember { listOf("永不", "1分钟", "10分钟", "20分钟", "30分钟") }
    var selectedTime by remember { mutableStateOf(options.first()) }
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .clickable { showDialog = true }
                    .padding(horizontal = 18.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "进入屏保时间",
                    fontSize = 19.sp,
                    color = Color(0xFF131519),
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                Text(selectedTime, fontSize = 17.sp, color = Color(0xFF8B909A))
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.arrow_right),
                    contentDescription = "进入屏保时间",
                    tint = Color(0xFFADB3BD),
                    modifier = Modifier.size(20.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "选择屏保图片",
                    fontSize = 19.sp,
                    color = Color(0xFF131519),
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    painter = painterResource(R.drawable.arrow_right),
                    contentDescription = "选择屏保图片",
                    tint = Color(0xFFADB3BD),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showDialog) {
        ScreenSaverTimeDialog(
            options = options,
            selectedOption = selectedTime,
            onOptionSelected = {
                selectedTime = it
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

// Copy the interaction model from HdmiSettingsScreen's AutoScreenOffDialog:
// window-level Dialog, click outside dismisses via onDismissRequest.
@Composable
fun ScreenSaverTimeDialog(
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
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    Text(
                        text = "进入屏保时间",
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
private fun ScreenSaverSettingsScreenPreview() {
    设置Theme {
        ScreenSaverSettingsScreen()
    }
}
