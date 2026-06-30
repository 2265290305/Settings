package com.android.tv.settings

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tv.settings.ui.theme.设置Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PRIVACY_METHOD_DEV_QUERY = "DEV_QUERY"
private const val PRIVACY_METHOD_DEV_OPT = "DEV_OPT"
private const val PRIVACY_VOICE_METHOD_INIT = "init"
private const val PRIVACY_VOICE_METHOD_SET_MUTE = "setMute"
private const val PRIVACY_VOICE_TAG = "PrivacyScreen"
private const val PRIVACY_VOICE_PREFS = "privacy_voice_prefs"
private const val PRIVACY_VOICE_WAKE_KEY = "voice_wake_enabled"
private const val PRIVACY_VOICE_AUDIO_SOURCE_TYPE = 2
private const val PRIVACY_VOICE_LEFT_VOICE_TIME = 11L
private const val PRIVACY_VOICE_RIGHT_VOICE_TIME = 500L
private val PRIVACY_MIC_CONTROL_URIS: List<Uri> = listOf(
    Uri.parse("content://com.android.zshd.deviceinfo/devStat"),
    Uri.parse("content://com.android.zshd.deviceinfo/device_info"),
    Uri.parse("content://com.android.zshd.deviceinfo/devStat"),
    Uri.parse("content://com.android.zshd.deviceinfo/device_info")
)
private val PRIVACY_VOICE_PROVIDER_URI: Uri = Uri.parse("content://com.android.ctcc.voice/settings")
private val PRIVACY_CAMERA_DISABLED_URI: Uri = Settings.Secure.getUriFor("camera_disabled")
// 按需求监听 micMute 变化的精确 URI：content://AUTHORITY/devStat?micMute=1
private val PRIVACY_MIC_MUTE_NOTIFY_URIS: List<Uri> = listOf(
    Uri.parse("content://com.android.zshd.deviceinfo/devStat?micMute=1"),
    Uri.parse("content://com.android.ctcc.deviceinfo/devStat?micMute=1")
)

private fun privacyNormalizeValue(value: Any?): String? {
    val normalized = value?.toString()?.trim()
    if (normalized.isNullOrEmpty()) return null
    if (normalized.equals("null", ignoreCase = true)) return null
    return normalized
}

private fun isPrivacyBundleSuccess(bundle: Bundle?): Boolean {
    if (bundle == null) return false
    if (bundle.getBoolean("success", false)) return true
    if (bundle.getBoolean("result", false)) return true
    if (bundle.getInt("code", -1) == 0) return true
    return false
}

private fun queryPrivacyValue(context: Context, key: String, defaultValue: String): String {
    val resolver = context.contentResolver
    return PRIVACY_MIC_CONTROL_URIS.firstNotNullOfOrNull { uri ->
        runCatching {
            val extras = Bundle().apply {
                putString("key", key)
                putString(key, "")
            }
            val result = resolver.call(uri, PRIVACY_METHOD_DEV_QUERY, null, extras)
            privacyNormalizeValue(result?.getString(key))
                ?: privacyNormalizeValue(result?.getString("value"))
                ?: privacyNormalizeValue(result?.getString("result"))
        }.getOrNull()
    } ?: defaultValue
}

private fun updatePrivacyValue(context: Context, key: String, value: String): Boolean {
    val resolver = context.contentResolver
    return PRIVACY_MIC_CONTROL_URIS.any { uri ->
        runCatching {
            val extras = Bundle().apply {
                putString("key", key)
                putString("value", value)
                putString(key, value)
            }
            val result = resolver.call(uri, PRIVACY_METHOD_DEV_OPT, null, extras)
            isPrivacyBundleSuccess(result)
                || privacyNormalizeValue(result?.getString(key)) == value
                || privacyNormalizeValue(result?.getString("value")) == value
                || privacyNormalizeValue(result?.getString("result"))?.equals("true", ignoreCase = true) == true
        }.getOrDefault(false)
    }
}

private fun readCachedVoiceWakeEnabled(context: Context): Boolean? {
    val prefs = context.getSharedPreferences(PRIVACY_VOICE_PREFS, Context.MODE_PRIVATE)
    return if (prefs.contains(PRIVACY_VOICE_WAKE_KEY)) {
        prefs.getBoolean(PRIVACY_VOICE_WAKE_KEY, true)
    } else {
        null
    }
}

private fun cacheVoiceWakeEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PRIVACY_VOICE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(PRIVACY_VOICE_WAKE_KEY, enabled)
        .apply()
}

private fun queryVoiceWakeEnabled(context: Context): Boolean {
    return when (queryPrivacyValue(context, "micMute", "")) {
        "1" -> true
        "2" -> false
        else -> readCachedVoiceWakeEnabled(context) ?: true
    }
}

private fun initializeVoiceProvider(context: Context): Boolean {
    return runCatching {
        val extras = Bundle().apply {
            putInt("audioSourceType", PRIVACY_VOICE_AUDIO_SOURCE_TYPE)
            putLong("leftVoiceTime", PRIVACY_VOICE_LEFT_VOICE_TIME)
            putLong("rightVoiceTime", PRIVACY_VOICE_RIGHT_VOICE_TIME)
        }
        val result = context.contentResolver.call(PRIVACY_VOICE_PROVIDER_URI, PRIVACY_VOICE_METHOD_INIT, null, extras)
        Log.d(PRIVACY_VOICE_TAG, "voice init result=$result")
        result != null
    }.onFailure {
        Log.e(PRIVACY_VOICE_TAG, "voice init failed", it)
    }.getOrDefault(false)
}

private fun updateVoiceProviderMute(context: Context, enabled: Boolean): Boolean {
    return runCatching {
        val extras = Bundle().apply {
            putBoolean("isMute", !enabled)
        }
        val result = context.contentResolver.call(PRIVACY_VOICE_PROVIDER_URI, PRIVACY_VOICE_METHOD_SET_MUTE, null, extras)
        Log.d(PRIVACY_VOICE_TAG, "voice setMute enabled=$enabled result=$result")
        result != null
    }.onFailure {
        Log.e(PRIVACY_VOICE_TAG, "voice setMute failed enabled=$enabled", it)
    }.getOrDefault(false)
}

private suspend fun applyVoiceWakeEnabled(context: Context, enabled: Boolean): Boolean {
    initializeVoiceProvider(context)
    var voiceProviderUpdated = false
    repeat(4) { attempt ->
        voiceProviderUpdated = updateVoiceProviderMute(context, enabled) || voiceProviderUpdated
        if (!voiceProviderUpdated && attempt < 3) {
            delay(400)
        }
    }
    val mirrored = updatePrivacyValue(context, "micMute", if (enabled) "1" else "2")
    if (voiceProviderUpdated || mirrored) {
        cacheVoiceWakeEnabled(context, enabled)
    }
    return voiceProviderUpdated || mirrored
}

private fun isCameraEnabled(context: Context): Boolean {
    return runCatching {
        Settings.Secure.getInt(context.contentResolver, "camera_disabled", 0) != 1
    }.getOrDefault(true)
}

private fun updateCameraEnabled(context: Context, enabled: Boolean): Boolean {
    return runCatching {
        Settings.Secure.putInt(context.contentResolver, "camera_disabled", if (enabled) 0 else 1)
        Toast.makeText(context, "摄像头已" + if (enabled) "启用" else "禁用", Toast.LENGTH_SHORT).show()
        true
    }.getOrElse {
        Toast.makeText(context, "操作失败", Toast.LENGTH_SHORT).show()
        false
    }
}

@Composable
fun PrivacyScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var voiceWake by rememberSaveable { mutableStateOf(false) }
    var voiceWakeUpdating by rememberSaveable { mutableStateOf(false) }
    var cameraEnabled by rememberSaveable { mutableStateOf(false) }
    var refreshVersion by remember { mutableStateOf(0) }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                refreshVersion++
            }
        }
        PRIVACY_MIC_CONTROL_URIS.forEach { uri ->
            runCatching { context.contentResolver.registerContentObserver(uri, true, observer) }
        }
        // 按需求显式监听 devStat?micMute=1，确保 micMute 变化能被回调刷新。
        PRIVACY_MIC_MUTE_NOTIFY_URIS.forEach { uri ->
            runCatching { context.contentResolver.registerContentObserver(uri, true, observer) }
        }
        runCatching { context.contentResolver.registerContentObserver(PRIVACY_CAMERA_DISABLED_URI, false, observer) }
        onDispose {
            runCatching { context.contentResolver.unregisterContentObserver(observer) }
        }
    }

    LaunchedEffect(context) {
        initializeVoiceProvider(context)
    }

    LaunchedEffect(context, refreshVersion) {
        voiceWake = queryVoiceWakeEnabled(context)
        cameraEnabled = isCameraEnabled(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            //.background(colorResource(R.color.cardcolor))
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Group 1: toggles
        Card(
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = colorResource(R.color.cardcolor)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ToggleItem(
                    title = "语音唤醒",
                    desc = "开启后，你可以通过语音来唤醒设备",
                    checked = voiceWake,
                    enabled = !voiceWakeUpdating,
                    switchModifier = Modifier.entryFocus(),
                    onCheckedChange = {
                        if (voiceWakeUpdating) return@ToggleItem
                        val previous = voiceWake
                        voiceWake = it
                        voiceWakeUpdating = true
                        scope.launch {
                            if (!applyVoiceWakeEnabled(context, it)) {
                                voiceWake = previous
                                Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                            }
                            voiceWakeUpdating = false
                        }
                    }
                )
                ToggleItem(
                    title = "摄像头",
                    desc = "开启后，你可以使用设备摄像头进行视频通话等功能",
                    checked = cameraEnabled,
                    onCheckedChange = {
                        if (updateCameraEnabled(context, it)) {
                            cameraEnabled = it
                        }
                    }
                )
            }
        }

        // Group 2: links
        Card(
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = colorResource(R.color.cardcolor)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                LinkRow(
                    text = "天翼智屏用户协议",
                    onClick = {
                        if (!context.launchUnifiedAccountProtocolDetailOrFallback(
                                PROTOCOL_TITLE_USER_AGREEMENT,
                                PROTOCOL_URL_USER_AGREEMENT
                            )
                        ) {
                            Toast.makeText(context, "无法打开用户协议", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                Divider(color = Color(0xFFE9EAEC), modifier = Modifier.padding(horizontal = 10.dp))
                LinkRow(
                    text = "天翼智屏隐私政策",
                    onClick = {
                        if (!context.launchUnifiedAccountProtocolDetailOrFallback(
                                PROTOCOL_TITLE_PRIVACY,
                                PROTOCOL_URL_PRIVACY
                            )
                        ) {
                            Toast.makeText(context, "无法打开隐私政策", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ToggleItem(
    title: String,
    desc: String,
    checked: Boolean,
    enabled: Boolean = true,
    switchModifier: Modifier = Modifier,
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
                    .padding(end = 88.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorResource(R.color.textblack)
                )
                Spacer(Modifier.width(1.dp))
                Text(
                    text = desc,
                    fontSize = 13.sp,
                    color = Color(0xFF6B7280),
                    lineHeight = 18.sp
                )
            }

            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.align(Alignment.CenterEnd).then(switchModifier),
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
private fun LinkRow(
    text: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Text(
                text = text,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorResource(R.color.textblack),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(end = 28.dp)
            )
            Icon(
                painter = painterResource(R.drawable.arrow_right),
                contentDescription = null,
                tint = Color(0xFFADB3BD),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(18.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF0F2F5, widthDp = 1200)
@Composable
private fun PrivacyScreenPreview() {
    设置Theme {
        PrivacyScreen()
    }
}
