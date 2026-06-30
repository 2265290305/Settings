package com.android.tv.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.android.tv.settings.ui.theme.设置Theme
import kotlin.math.roundToInt

@Composable
fun SoundAndDisplayScreen(
    modifier: Modifier = Modifier,
    onExitLeft: () -> Unit = {},
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val audioManager = if (isPreview) null else context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val isVolumeFixed = if (isPreview) false else audioManager!!.isVolumeFixed

    val maxSystemVolume = if (isPreview) 100f else audioManager!!.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
    var systemVolume by remember {
        mutableFloatStateOf(if (isPreview) 50f else audioManager!!.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat())
    }

    val maxAlarmVolume = if (isPreview) 100f else audioManager!!.getStreamMaxVolume(AudioManager.STREAM_ALARM).toFloat()
    var alarmVolume by remember {
        mutableFloatStateOf(if (isPreview) 50f else audioManager!!.getStreamVolume(AudioManager.STREAM_ALARM).toFloat())
    }

    var screenBrightness by remember {
        mutableFloatStateOf(
            if (isPreview) 128f else try {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS).toFloat()
            } catch (e: Settings.SettingNotFoundException) {
                128f
            }
        )
    }

    val focusRequesters = remember { List(3) { FocusRequester() } }
    var focusedRow by remember { mutableIntStateOf(0) }

    // 不在进入页面时自动抢焦点：否则在左侧菜单移动到“声音与显示”时，本页一组合就把焦点
    // 抢进页面，导致菜单无法继续上下浏览。焦点改由用户按右键/确定键进入（统一导航模型）。

    // 监听系统音量变化（遥控器/硬件键加减、其他应用调音量），实时同步滑条进度。
    DisposableEffect(audioManager) {
        if (isPreview || audioManager == null) {
            onDispose { }
        } else {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    systemVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                    alarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM).toFloat()
                }
            }
            // AudioService 在音量变化时发送的系统广播（隐藏 action，字符串稳定）。
            val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            onDispose { runCatching { context.unregisterReceiver(receiver) } }
        }
    }

    fun percentText(value: Float, maxValue: Float): String {
        val percent = if (maxValue <= 0f) 0 else ((value / maxValue) * 100f).roundToInt().coerceIn(0, 100)
        return "$percent%"
    }

    fun setSystemVolume(value: Float) {
        val bounded = value.coerceIn(0f, maxSystemVolume)
        systemVolume = bounded
        // 不带 FLAG_SHOW_UI：SystemUI 已有音量 toast，避免重复弹窗。
        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, bounded.roundToInt(), 0)
    }

    fun setAlarmVolume(value: Float) {
        val bounded = value.coerceIn(0f, maxAlarmVolume)
        alarmVolume = bounded
        // 不带 FLAG_SHOW_UI：SystemUI 已有音量 toast，避免重复弹窗。
        audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, bounded.roundToInt(), 0)
    }

    fun setBrightness(value: Float) {
        val bounded = value.coerceIn(0f, 255f)
        screenBrightness = bounded
        if (!isPreview) {
            runCatching {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, bounded.roundToInt())
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SoundDisplaySliderRow(
            title = "系统音量",
            iconRes = R.drawable.sysaulumn,
            contentDescription = "System Volume",
            value = systemVolume,
            maxValue = maxSystemVolume,
            enabled = !isVolumeFixed,
            step = 1f,
            percentText = percentText(systemVolume, maxSystemVolume),
            focused = focusedRow == 0,
            focusRequester = focusRequesters[0],
            extraModifier = Modifier.entryFocus(),
            onFocused = { focusedRow = 0 },
            onMoveUp = { focusRequesters[0].requestFocus() },
            onMoveDown = { focusRequesters[1].requestFocus() },
            onExitLeft = onExitLeft,
            onValueChange = ::setSystemVolume,
        )

        SoundDisplaySliderRow(
            title = "闹钟、倒计时音量",
            iconRes = R.drawable.amaolumn,
            contentDescription = "Alarm Volume",
            value = alarmVolume,
            maxValue = maxAlarmVolume,
            enabled = !isVolumeFixed,
            step = 1f,
            percentText = percentText(alarmVolume, maxAlarmVolume),
            focused = focusedRow == 1,
            focusRequester = focusRequesters[1],
            onFocused = { focusedRow = 1 },
            onMoveUp = { focusRequesters[0].requestFocus() },
            onMoveDown = { focusRequesters[2].requestFocus() },
            onExitLeft = onExitLeft,
            onValueChange = ::setAlarmVolume,
        )

        SoundDisplaySliderRow(
            title = "屏幕亮度",
            iconRes = R.drawable.nit,
            contentDescription = "Screen Brightness",
            value = screenBrightness,
            maxValue = 255f,
            enabled = true,
            step = 5f,
            percentText = percentText(screenBrightness, 255f),
            focused = focusedRow == 2,
            focusRequester = focusRequesters[2],
            onFocused = { focusedRow = 2 },
            onMoveUp = { focusRequesters[1].requestFocus() },
            onMoveDown = { focusRequesters[2].requestFocus() },
            onExitLeft = onExitLeft,
            onValueChange = ::setBrightness,
        )
    }
}

@Composable
private fun SoundDisplaySliderRow(
    title: String,
    iconRes: Int,
    contentDescription: String,
    value: Float,
    maxValue: Float,
    enabled: Boolean,
    step: Float,
    percentText: String,
    focused: Boolean,
    focusRequester: FocusRequester,
    extraModifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onExitLeft: () -> Unit,
    onValueChange: (Float) -> Unit,
) {
    val progress = if (maxValue <= 0f) 0f else (value / maxValue).coerceIn(0f, 1f)
    fun valueFromPosition(x: Float, width: Float): Float {
        if (width <= 0f || maxValue <= 0f) return 0f
        val raw = (x / width).coerceIn(0f, 1f) * maxValue
        return if (step > 0f) {
            (raw / step).roundToInt().toFloat() * step
        } else {
            raw
        }.coerceIn(0f, maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(extraModifier)
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (it.key) {
                    Key.DirectionUp -> {
                        onMoveUp()
                        true
                    }
                    Key.DirectionDown -> {
                        onMoveDown()
                        true
                    }
                    Key.DirectionLeft -> {
                        if (!enabled || value <= 0f) {
                            onExitLeft()
                        } else {
                            onValueChange(value - step)
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        if (enabled) onValueChange(value + step)
                        true
                    }
                    Key.DirectionCenter,
                    Key.Enter -> true
                    else -> false
                }
            }
            .focusable()
            .background(
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Text(title, fontSize = 16.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painter = painterResource(id = iconRes), contentDescription = contentDescription)
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .pointerInput(enabled, maxValue, step) {
                        if (!enabled) return@pointerInput
                        detectTapGestures { offset ->
                            onValueChange(valueFromPosition(offset.x, size.width.toFloat()))
                        }
                    }
                    .pointerInput(enabled, maxValue, step) {
                        if (!enabled) return@pointerInput
                        detectDragGestures(
                            onDragStart = { offset ->
                                onValueChange(valueFromPosition(offset.x, size.width.toFloat()))
                            },
                            onDrag = { change, _ ->
                                onValueChange(valueFromPosition(change.position.x, size.width.toFloat()))
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = Color(0xFFF0F0F0),
                        cornerRadius = CornerRadius(size.height / 2)
                    )
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(Color(0xFF6974FF), Color(0xFF4CA8FF))),
                        size = Size(size.width * progress, size.height),
                        cornerRadius = CornerRadius(size.height / 2)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(percentText, modifier = Modifier.width(52.dp))
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
