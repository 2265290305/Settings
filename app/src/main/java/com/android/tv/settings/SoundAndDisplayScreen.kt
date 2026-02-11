package com.android.tv.settings

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tv.settings.ui.theme.设置Theme


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundAndDisplayScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val audioManager = if (isPreview) null else context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val isVolumeFixed = if (isPreview) false else audioManager!!.isVolumeFixed

    // In preview mode, we use hardcoded values for volume and brightness.
    // FIX: On Android TV, the main volume control is STREAM_MUSIC, not STREAM_SYSTEM.
    val maxSystemVolume = if (isPreview) 100f else audioManager!!.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
    val systemVolume = remember {
        mutableStateOf(if (isPreview) 50f else audioManager!!.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat())
    }

    val maxAlarmVolume = if (isPreview) 100f else audioManager!!.getStreamMaxVolume(AudioManager.STREAM_ALARM).toFloat()
    val alarmVolume = remember {
        mutableStateOf(if (isPreview) 50f else audioManager!!.getStreamVolume(AudioManager.STREAM_ALARM).toFloat())
    }

    val screenBrightness = remember {
        mutableStateOf(
            if (isPreview) 128f else try {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS).toFloat()
            } catch (e: Settings.SettingNotFoundException) {
                // Should not happen on a real device
                128f
            }
        )
    }
    
    val sliderColor = SliderDefaults.colors(
        thumbColor = Color.Transparent
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            ,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

            Column(modifier = Modifier.padding(16.dp)) {
                Text("系统音量", fontSize = 16.sp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painter = painterResource(id = R.drawable.sysaulumn), contentDescription = "System Volume")
                    Spacer(modifier = Modifier.width(16.dp))

                    Slider(
                        colors = sliderColor,
                        track = {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                            ) {
                                val progress = it.value / it.valueRange.endInclusive

                                // inactive
                                drawRoundRect(
                                    color = Color(0xFFF0F0F0),
                                    cornerRadius = CornerRadius(size.height / 2)
                                )

                                // active (渐变)
                                drawRoundRect(
                                    brush = Brush.horizontalGradient(
                                        listOf(Color(0xFF6974FF),Color(0xFF4CA8FF), )
                                    ),
                                    size = Size(size.width * progress, size.height),
                                    cornerRadius = CornerRadius(size.height / 2)
                                )
                            }
                        },
                        value = systemVolume.value,
                        onValueChange = {
                            systemVolume.value = it
                            // FIX: On Android TV, the main volume control is STREAM_MUSIC, not STREAM_SYSTEM.
                            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), AudioManager.FLAG_SHOW_UI)
                        },
                        valueRange = 0f..maxSystemVolume,
                        enabled = !isVolumeFixed, // FIX: Disable slider if volume is fixed
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(systemVolume.value.toInt().toString())
                }
                Text("闹钟、倒计时音量", fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painter = painterResource(id = R.drawable.amaolumn), contentDescription = "Alarm Volume")
                    Spacer(modifier = Modifier.width(16.dp))
                    Slider(
                        colors = sliderColor,
                        track = {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                            ) {
                                val progress = it.value / it.valueRange.endInclusive

                                // inactive
                                drawRoundRect(
                                    color = Color(0xFFF0F0F0),
                                    cornerRadius = CornerRadius(size.height / 2)
                                )

                                // active (渐变)
                                drawRoundRect(
                                    brush = Brush.horizontalGradient(
                                        listOf(Color(0xFF6974FF),Color(0xFF4CA8FF), )
                                    ),
                                    size = Size(size.width * progress, size.height),
                                    cornerRadius = CornerRadius(size.height / 2)
                                )
                            }
                        },
                        value = alarmVolume.value,
                        onValueChange = {
                            alarmVolume.value = it
                            audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, it.toInt(), AudioManager.FLAG_SHOW_UI)
                        },
                        valueRange = 0f..maxAlarmVolume,
                        enabled = !isVolumeFixed, // FIX: Disable slider if volume is fixed
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(alarmVolume.value.toInt().toString())
                }
            }



            Column(modifier = Modifier.padding(16.dp)) {
                Text("屏幕亮度", fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painter = painterResource(id = R.drawable.nit), contentDescription = "Screen Brightness")
                    Spacer(modifier = Modifier.width(16.dp))
                    Slider(
                        colors = sliderColor,
                        track = {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                            ) {
                                val progress = it.value / it.valueRange.endInclusive

                                // inactive
                                drawRoundRect(
                                    color = Color(0xFFF0F0F0),
                                    cornerRadius = CornerRadius(size.height / 2)
                                )

                                // active (渐变)
                                drawRoundRect(
                                    brush = Brush.horizontalGradient(
                                        listOf(Color(0xFF6974FF),Color(0xFF4CA8FF), )
                                    ),
                                    size = Size(size.width * progress, size.height),
                                    cornerRadius = CornerRadius(size.height / 2)
                                )
                            }
                        },
                        value = screenBrightness.value,
                        onValueChange = {
                            screenBrightness.value = it
                            if (!isPreview) {
                                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, it.toInt())
                            }
                         },
                        valueRange = 0f..255f,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(screenBrightness.value.toInt().toString())
                }
            }

    }
}

@Preview(showBackground = true)
@Composable
fun SoundAndDisplayScreenPreview() {
    设置Theme {
        SoundAndDisplayScreen()
    }
}
