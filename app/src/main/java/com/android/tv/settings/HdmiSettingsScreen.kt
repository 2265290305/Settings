package com.android.tv.settings

import android.content.Context
import android.net.Uri
import android.os.Bundle
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
import org.json.JSONObject

private const val METHOD_DEV_QUERY = "DEV_QUERY"
private const val METHOD_DEV_OPT = "DEV_OPT"
private val HDMI_SETTINGS_URI: Uri = Uri.parse("content://com.android.zshd.deviceinfo/settings")

private data class HdmiScreenBorders(
    val top: Int,
    val down: Int,
    val left: Int,
    val right: Int
)

private fun hdmiNormalizeValue(value: Any?): String? {
    val normalized = value?.toString()?.trim()
    if (normalized.isNullOrEmpty()) return null
    if (normalized.equals("null", ignoreCase = true)) return null
    return normalized
}

private fun isHdmiBundleSuccess(bundle: Bundle?): Boolean {
    if (bundle == null) return false
    if (bundle.getBoolean("success", false)) return true
    if (bundle.getBoolean("result", false)) return true
    if (bundle.getInt("code", -1) == 0) return true
    return false
}

private fun hdmiProviderCall(context: Context, method: String, key: String, value: String? = null): String? {
    val resolver = context.contentResolver
    return runCatching {
        val extras = Bundle().apply {
            putString("key", key)
            if (value != null) {
                putString("value", value)
                putString(key, value)
            } else {
                putString(key, "")
            }
        }
        val result = resolver.call(HDMI_SETTINGS_URI, method, null, extras)
        hdmiNormalizeValue(result?.getString(key))
            ?: hdmiNormalizeValue(result?.getString("value"))
            ?: hdmiNormalizeValue(result?.getString("result"))
            ?: result?.keySet()?.firstNotNullOfOrNull { bundleKey ->
                hdmiNormalizeValue(result.get(bundleKey))
            }
    }.getOrNull()
}

private fun queryHdmiValue(context: Context, key: String, defaultValue: String): String {
    return hdmiProviderCall(context, METHOD_DEV_QUERY, key) ?: defaultValue
}

private fun updateHdmiValue(context: Context, key: String, value: String): Boolean {
    val resolver = context.contentResolver
    return runCatching {
        val extras = Bundle().apply {
            putString("key", key)
            putString("value", value)
            putString(key, value)
        }
        val result = resolver.call(HDMI_SETTINGS_URI, METHOD_DEV_OPT, null, extras)
        isHdmiBundleSuccess(result)
            || hdmiNormalizeValue(result?.getString(key))?.let { it.equals("true", ignoreCase = true) || it == "1" } == true
            || hdmiNormalizeValue(result?.getString("value"))?.let { it.equals("true", ignoreCase = true) || it == "1" } == true
            || hdmiNormalizeValue(result?.getString("result"))?.let { it.equals("true", ignoreCase = true) || it == "1" } == true
    }.getOrDefault(false)
}

private fun parseHdmiResolutionOptions(raw: String): List<String> {
    return raw.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

private fun parseHdmiScreenBorders(raw: String): HdmiScreenBorders? {
    return runCatching {
        val json = JSONObject(raw)
        HdmiScreenBorders(
            top = json.optString("top", "0").toInt(),
            down = json.optString("down", "0").toInt(),
            left = json.optString("left", "0").toInt(),
            right = json.optString("right", "0").toInt()
        )
    }.getOrNull()
}

private fun bordersToPercent(borders: HdmiScreenBorders): Int {
    val inset = maxOf(borders.top, borders.down, borders.left, borders.right).coerceIn(0, 20)
    return 100 - inset
}

private fun percentToBorders(percent: Int): HdmiScreenBorders {
    val inset = (100 - percent).coerceIn(0, 20)
    return HdmiScreenBorders(
        top = inset,
        down = inset,
        left = inset,
        right = inset
    )
}

@Composable
fun HdmiSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    var hdmiAudioOutput by rememberSaveable { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    val screenOffTimeOptions = listOf("永不", "1分钟", "10分钟", "20分钟", "30分钟")
    var selectedScreenOffTime by rememberSaveable { mutableStateOf("30分钟") }

    var showResolutionDialog by remember { mutableStateOf(false) }
    var resolutionOptions by rememberSaveable { mutableStateOf(listOf("Auto")) }
    var selectedResolution by rememberSaveable { mutableStateOf("Auto") }

    var showScalingScreen by rememberSaveable { mutableStateOf(false) }
    var selectedScalingPercent by rememberSaveable { mutableStateOf(100) }

    LaunchedEffect(context) {
        val availableResolutions = parseHdmiResolutionOptions(
            queryHdmiValue(context, "getAllResolutions", "Auto")
        )
        val currentResolutionOptions = availableResolutions.ifEmpty { resolutionOptions }
        resolutionOptions = currentResolutionOptions

        val resolutionIndex = queryHdmiValue(context, "getResolution", "1").toIntOrNull()
        selectedResolution = currentResolutionOptions.getOrNull((resolutionIndex ?: 1) - 1)
            ?: currentResolutionOptions.firstOrNull()
            ?: "Auto"

        hdmiAudioOutput = queryHdmiValue(context, "getCurAudioDevice", "0") == "1"

        parseHdmiScreenBorders(queryHdmiValue(context, "getScreenBorders", """{"top":"0","down":"0","left":"0","right":"0"}"""))
            ?.let { borders -> selectedScalingPercent = bordersToPercent(borders) }
    }

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
                    onCheckedChange = {
                        if (updateHdmiValue(context, "setCurAudioDevice", if (it) "1" else "0")) {
                            hdmiAudioOutput = it
                        }
                    },
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
                val targetIndex = resolutionOptions.indexOf(it) + 1
                if (targetIndex > 0 && updateHdmiValue(context, "setResolution", targetIndex.toString())) {
                    selectedResolution = it
                }
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
                    val borders = percentToBorders(it)
                    val value = JSONObject()
                        .put("top", borders.top.toString())
                        .put("down", borders.down.toString())
                        .put("left", borders.left.toString())
                        .put("right", borders.right.toString())
                        .toString()
                    if (updateHdmiValue(context, "setScreenBorders", value)) {
                        selectedScalingPercent = it
                    }
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
                            valueRange = 80f..100f,
                            steps = 19,
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
