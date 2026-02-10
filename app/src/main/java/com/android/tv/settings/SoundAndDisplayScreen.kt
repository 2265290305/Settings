package com.android.tv.settings

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val isPreview = LocalInspectionMode.current

    val systemVolume = remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM).toFloat()) }
    val alarmVolume = remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_ALARM).toFloat()) }
    val screenBrightness = remember {
        mutableStateOf(
            try {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS).toFloat()
            } catch (e: Settings.SettingNotFoundException) {
                // In preview mode, screen_brightness setting is not available. Use a default value.
                128f
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("系统音量", fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painter = painterResource(id = R.drawable.sysaulumn), contentDescription = "System Volume")
                    Spacer(modifier = Modifier.width(16.dp))
                    Slider(
                        // FIX: Removed the background modifier with an empty Brush.linearGradient()
                        // that was causing the preview to crash. A linear gradient requires at least
                        // two colors.
                        modifier = Modifier.height(40.dp).padding(end = 16.dp),
                        value = if(isPreview) 20f else systemVolume.value,
                        onValueChange = {
                            systemVolume.value = it
                            audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, it.toInt(), AudioManager.FLAG_SHOW_UI)
                        },
                        steps = 0,
                        thumb = {},

                        valueRange = 0f..audioManager.getStreamMaxVolume(AudioManager.STREAM_SYSTEM).toFloat(),

                    )
                    //Spacer(modifier = Modifier.width(16.dp))
                    Text(systemVolume.value.toInt().toString())
                }
                Text("闹钟、倒计时音量", fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painter = painterResource(id = R.drawable.amaolumn), contentDescription = "Alarm Volume")
                    Spacer(modifier = Modifier.width(16.dp))
                    Slider(
                        value = alarmVolume.value,
                        onValueChange = {
                            alarmVolume.value = it
                            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, it.toInt(), AudioManager.FLAG_SHOW_UI)
                        },
                        valueRange = 0f..audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM).toFloat(),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(alarmVolume.value.toInt().toString())
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("屏幕亮度", fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painter = painterResource(id = R.drawable.nit), contentDescription = "Screen Brightness")
                    Spacer(modifier = Modifier.width(16.dp))
                    Slider(
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
}

@Preview(showBackground = true)
@Composable
fun SoundAndDisplayScreenPreview() {
    设置Theme {
        SoundAndDisplayScreen()
    }
}
