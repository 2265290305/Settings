package com.android.tv.settings

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.android.tv.settings.ui.theme.设置Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

private val HEAVY_SETTINGS_PAGES = setOf(1, 2, 10, 12)

/**
 * 子页面用于标记“最上面的第一个控件”的 FocusRequester：右键进入子目录时聚焦到它。
 * 由 NavigationRailExample 按当前页下发（每个目标页一个，避免切页时同一 requester 重复绑定崩溃）。
 * 页面在其首个可聚焦控件上用 `Modifier.focusRequester(LocalEntryFocusRequester.current ?: ...)` 接入；
 * 未接入的页面右键会回退到系统 2D 方向焦点搜索。
 */
val LocalEntryFocusRequester = compositionLocalOf<FocusRequester?> { null }

/**
 * 把当前页“最上面的第一个控件”标记为右键进入时的聚焦目标。
 * 在每个子页面的首个可聚焦控件(Switch/可点击行/按钮/滑条等)的 Modifier 链上调用一次即可。
 */
@Composable
fun Modifier.entryFocus(): Modifier {
    val req = LocalEntryFocusRequester.current
    return if (req != null) this.focusRequester(req) else this
}

private const val LOGOUT_TAG = "LogoutCleanup"

// 天翼规范 8.15.4 / 6.8 退出登录：
private const val ACTION_LOG_OUT = "com.telecom.smartcloud.action.log_out"        // 执行退出登录
private const val ACTION_LOG_OUT_RESULT = "com.telecom.smartcloud.action.result"  // 退出登录结果
private const val KEY_LOG_OUT_RESULT = "log_out_result"                           // 结果 extra(布尔)
private const val ACTION_UNBIND = "com.ctcc.iotsdk.unbind_broadcast"              // 触发解绑
private const val ACTION_TV_SIGN_IN = "EAccount.ACTION_TV_SIGN_IN"
private const val ACTION_TV_SIGN_OUT = "EAccount.ACTION_TV_SIGN_OUT"
private const val METHOD_DEV_QUERY = "DEV_QUERY"
private const val METHOD_DEV_OPT = "DEV_OPT"
private const val PROFILE_TAG = "PersonalInfo"

// 外部深层跳转 action（引导入网/Launcher 调用，setPackage com.android.speaker.settings）。
private const val ACTION_PAGE_PERSONAL_CENTER = "android.settings.PERSONALCENTER"
private const val ACTION_PAGE_NETWORK = "android.settings.NETWORK"
private const val ACTION_PAGE_BLUETOOTH = "android.settings.BLUETOOTH"
private const val ACTION_PAGE_SOUND_DISPLAY = "android.settings.SOUNDDISPLAY"
private const val ACTION_PAGE_SCREENSAVER = "android.settings.SCREENSAVER"
private const val ACTION_PAGE_ENERGY_SAVING = "android.settings.ENERGY_SAVING"
private const val EXTRA_FROM_SYSTEMUI_ACTIVATION =
    "com.android.systemui.iot.extra.FROM_SYSTEMUI_ACTIVATION"
private const val KEY_BIND_STATUS = "bindStatus"

// 退出登录：轮询 devStat 的 bindStatus，==2 视为退出登录成功。
private const val BIND_STATUS_UNBOUND = "2"

// 解绑是异步的：unbind 触发后，bindStatus 需等平台/IotSDK 回写才会变 2，首次往往 >10s。
// 超时设短会导致“第一次失败、第二次才成功”，这里放宽到 30s 给足异步回写时间。
private const val LOGOUT_TIMEOUT_MS = 30_000L
private const val LOGOUT_POLL_INTERVAL_MS = 1_000L
private val PERSONAL_INFO_URI: Uri = Uri.parse("content://com.android.zshd.deviceinfo/personalinfo")
private val DEVICE_INFO_URI: Uri = Uri.parse("content://com.android.zshd.deviceinfo/device_info")
private val DEV_STAT_URI: Uri = Uri.parse("content://com.android.zshd.deviceinfo/devStat")
private val ACCOUNT_USERINFO_URI: Uri = Uri.parse("content://cn.com.chinatelecom.account.android/userinfo")

private fun buildPersonalInfoUriCandidates(context: Context): List<Uri> {
    return listOf(PERSONAL_INFO_URI)
}

private fun systemProperty(key: String, fallback: String = ""): String {
    return runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getDeclaredMethod("get", String::class.java, String::class.java)
        method.invoke(null, key, fallback) as? String ?: fallback
    }.getOrDefault(fallback)
}

private fun sendLogoutBroadcasts(context: Context) {
    // 天翼规范：设置 APK 退出登录时，发送退出登录广播 + 解绑广播，由 Iot Apk 执行解绑。
    // 本应用在 system 进程，需跨用户投递，否则 user 0 的 Iot Apk 收不到。
    context.sendBroadcastAllUsers(Intent(ACTION_LOG_OUT))
    context.sendBroadcastAllUsers(Intent(ACTION_UNBIND))
    // 对关键应用补发显式广播，提升部分版本上的送达率。
    listOf("com.chinatelecom.accloudbox", "cn.dlife.smartcloud.launcher").forEach { pkg ->
        runCatching { context.sendBroadcastAllUsers(Intent(ACTION_LOG_OUT).setPackage(pkg)) }
        runCatching { context.sendBroadcastAllUsers(Intent(ACTION_UNBIND).setPackage(pkg)) }
    }
}

/** 读取 devStat 的 bindStatus；==2 表示已解绑（与写入端 handleIotCommand 一致，走 DEV_QUERY）。 */
private fun isUnboundFromDevStat(context: Context): Boolean {
    return queryBindStatusForSystemUiActivation(context)?.trim() == "2"
}

private fun avatarKeyFromAsset(assetUrl: String): String {
    return assetUrl.substringAfterLast('/').substringBeforeLast('.')
}

private fun authTokenHeaderOrNull(): String? {
    val token = BuildConfig.ACCOUNT_AUTH_TOKEN.trim()
    return if (token.isNotEmpty()) "Bearer $token" else null
}

private data class BackendProfile(
    val account: String?,
    val nickname: String?,
    val avatarPath: String?
)

private fun configuredAccountProfileQueryUrlOrNull(): String? {
    val endpoint = BuildConfig.ACCOUNT_PROFILE_QUERY_URL.trim()
    if (endpoint.isBlank()) return null
    val host = runCatching { URL(endpoint).host }.getOrNull().orEmpty()
    if (host.equals("api.example.com", ignoreCase = true)) return null
    return endpoint
}

private fun parseBackendProfile(body: String): BackendProfile {
    val root = JSONObject(body)
    val data = when {
        root.has("data") && root.opt("data") is JSONObject -> root.getJSONObject("data")
        root.has("result") && root.opt("result") is JSONObject -> root.getJSONObject("result")
        else -> root
    }
    val account = listOf("account", "accountName", "phone", "mobile", "user", "username")
        .firstNotNullOfOrNull { k -> data.optString(k).takeIf { it.isNotBlank() } }
    val nickname = listOf("nickname", "nickName", "name")
        .firstNotNullOfOrNull { k -> data.optString(k).takeIf { it.isNotBlank() } }
    val avatarPath = listOf("avatarpath", "avatarPath", "avatar", "avatar_url", "avatarUrl")
        .firstNotNullOfOrNull { k -> data.optString(k).takeIf { it.isNotBlank() } }
    return BackendProfile(account = account, nickname = nickname, avatarPath = avatarPath)
}

private suspend fun fetchAccountProfileFromBackend(): Result<BackendProfile> = withContext(Dispatchers.IO) {
    runCatching {
        val endpoint = configuredAccountProfileQueryUrlOrNull()
            ?: throw IllegalStateException("未配置账号查询接口地址")
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            authTokenHeaderOrNull()?.let { setRequestProperty("Authorization", it) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
        connection.disconnect()
        if (code !in 200..299) {
            throw IllegalStateException("账号查询失败(code=$code) ${body.ifBlank { "服务端返回异常" }}")
        }
        parseBackendProfile(body)
    }
}

private fun findAvatarIndex(avatarAssets: List<String>, avatarPath: String): Int {
    val normalized = avatarPath.trim()
    if (normalized.isEmpty()) return -1
    // 1) 完整 asset url 精确匹配
    avatarAssets.indexOfFirst { it.equals(normalized, ignoreCase = true) }.takeIf { it >= 0 }?.let { return it }
    // 2) 按文件名(去扩展名)匹配，如 .../avatar_03.png
    val byName = normalized.substringAfterLast('/').substringBeforeLast('.')
    avatarAssets.indexOfFirst { asset -> asset.contains("/$byName.", ignoreCase = true) }
        .takeIf { it >= 0 }?.let { return it }
    // 3) 统一账号头像是编号集合(headPortraitNum)：从值/查询参数中解析头像编号映射到本地内置头像，
    //    这样能直接本地渲染对应头像，避免依赖远程图片加载(否则会显示成灰色空圈)。
    avatarNumberToIndex(normalized, avatarAssets.size)?.let { return it }
    return -1
}

/**
 * 从形如 "3"、".../xxx?headPortraitNum=3"、".../head_3.png" 的头像值中解析头像编号并映射到内置
 * 头像下标(0-based)。统一账号约定 headPortraitNum 为 1-based，这里同时兼容 0-based。无法解析或
 * 越界(说明不是内置编号头像，而是真正的远程自定义头像)时返回 null。
 */
private fun avatarNumberToIndex(value: String, count: Int): Int? {
    val num = Regex("headPortraitNum=(\\d+)", RegexOption.IGNORE_CASE)
        .find(value)?.groupValues?.get(1)?.toIntOrNull()
        ?: value.takeIf { it.all(Char::isDigit) }?.toIntOrNull()
        ?: Regex("(\\d+)(?=\\D*$)").find(value)?.groupValues?.get(1)?.toIntOrNull()
        ?: return null
    return when {
        num in 1..count -> num - 1
        num in 0 until count -> num
        else -> null
    }
}

private fun extractBundleString(bundle: Bundle?, keyHint: String): String? {
    if (bundle == null) return null
    val candidates = listOf(keyHint, "value", "VALUE", "result", "data", "msg")
    candidates.forEach { key ->
        val value = bundle.getString(key)
        if (!value.isNullOrBlank()) return value
    }
    return null
}

private fun dumpBundleKeysForDebug(tag: String, bundle: Bundle?) {
    if (bundle == null) {
        Log.d(PROFILE_TAG, "$tag bundle=null")
        return
    }
    val keys = bundle.keySet()
    if (keys.isEmpty()) {
        Log.d(PROFILE_TAG, "$tag bundle empty")
        return
    }
    keys.forEach { key ->
        Log.d(PROFILE_TAG, "$tag key=$key value=${bundle.get(key)}")
    }
}

private fun queryRawPersonalInfoBundle(context: Context): Bundle? {
    val resolver = context.contentResolver
    buildPersonalInfoUriCandidates(context).forEach { uri ->
        val result = runCatching {
            resolver.call(uri, METHOD_DEV_QUERY, null, Bundle())
        }.onFailure {
            Log.d(PROFILE_TAG, "queryRawPersonalInfoBundle uri=$uri failed: ${it.message}")
        }.getOrNull()
        if (result != null) {
            Log.d(PROFILE_TAG, "queryRawPersonalInfoBundle hit uri=$uri")
            return result
        }
    }
    return null
}

private fun isBundleSuccess(bundle: Bundle?): Boolean {
    if (bundle == null) return false
    if (bundle.getBoolean("success", false)) return true
    if (bundle.getBoolean("result", false)) return true
    if (bundle.getInt("code", -1) == 0) return true
    return false
}

private fun queryPersonalInfoValue(context: Context, key: String): String? {
    val resolver = context.contentResolver
    val uriCandidates = buildPersonalInfoUriCandidates(context)
    uriCandidates.forEach { uri ->
        val byCall = runCatching {
            val extras = Bundle().apply { putString("key", key) }
            val result = resolver.call(uri, METHOD_DEV_QUERY, null, extras)
            extractBundleString(result, key)
        }.getOrNull()
        if (!byCall.isNullOrBlank()) return byCall
    }

    uriCandidates.forEach { uri ->
        val fromCursor = runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(key)
                if (index >= 0) cursor.getString(index) else cursor.getString(0)
            }
        }.getOrNull()
        if (!fromCursor.isNullOrBlank()) return fromCursor
    }
    return null
}

private fun queryAccountFromPersonalInfo(context: Context): String? {
    val raw = queryRawPersonalInfoBundle(context)
    dumpBundleKeysForDebug("DEV_QUERY(raw)", raw)

    val resolver = context.contentResolver
    val uriCandidates = buildPersonalInfoUriCandidates(context)
    val fromRaw = raw?.let { bundle ->
        val orderedKeys = listOf(
            "account", "accountname", "ctaccount", "phone", "mobile", "user", "username",
            "tel", "bindAccount", "bindPhone", "number", "uid", "userId"
        )
        orderedKeys.firstNotNullOfOrNull { k ->
            bundle.get(k)?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        } ?: bundle.keySet().firstNotNullOfOrNull { k ->
            if (k.contains("account", true) || k.contains("phone", true) || k.contains(
                    "mobile",
                    true
                ) || k.contains("user", true)
            ) {
                bundle.get(k)?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            } else null
        }
    }
    if (!fromRaw.isNullOrBlank()) return fromRaw

    uriCandidates.forEach { uri ->
        val fromCursor = runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val cols = cursor.columnNames?.toList().orEmpty()
                Log.d(PROFILE_TAG, "query cursor uri=$uri columns=$cols")
                if (!cursor.moveToFirst()) return@use null
                val orderedCols = listOf(
                    "account", "accountname", "ctaccount", "phone", "mobile", "user", "username", "uid", "userId"
                )
                orderedCols.firstNotNullOfOrNull { col ->
                    val idx = cursor.getColumnIndex(col)
                    if (idx >= 0) cursor.getString(idx)?.takeIf { it.isNotBlank() } else null
                } ?: cols.firstNotNullOfOrNull { col ->
                    if (col.contains("account", true) || col.contains("phone", true) || col.contains(
                            "mobile",
                            true
                        ) || col.contains("user", true)
                    ) {
                        val idx = cursor.getColumnIndex(col)
                        if (idx >= 0) cursor.getString(idx)?.takeIf { it.isNotBlank() } else null
                    } else null
                }
            }
        }.onFailure {
            Log.d(PROFILE_TAG, "query cursor uri=$uri failed: ${it.message}")
        }.getOrNull()
        if (!fromCursor.isNullOrBlank()) return fromCursor
    }
    return null
}

private fun updatePersonalInfoByProvider(context: Context, nickname: String, avatarPath: String): Boolean {
    val resolver = context.contentResolver
    buildPersonalInfoUriCandidates(context).forEach { uri ->
        val direct = runCatching {
            val extras = Bundle().apply {
                putString("nickname", nickname)
                putString("avatarpath", avatarPath)
            }
            val result = resolver.call(uri, METHOD_DEV_OPT, null, extras)
            isBundleSuccess(result)
        }.getOrDefault(false)
        if (direct) {
            Log.d(PROFILE_TAG, "DEV_OPT direct success uri=$uri")
            return true
        }

        val updateNickname = runCatching {
            val extras = Bundle().apply {
                putString("key", "nickname")
                putString("value", nickname)
            }
            val result = resolver.call(uri, METHOD_DEV_OPT, null, extras)
            isBundleSuccess(result)
        }.getOrDefault(false)
        val updateAvatar = runCatching {
            val extras = Bundle().apply {
                putString("key", "avatarpath")
                putString("value", avatarPath)
            }
            val result = resolver.call(uri, METHOD_DEV_OPT, null, extras)
            isBundleSuccess(result)
        }.getOrDefault(false)
        if (updateNickname && updateAvatar) {
            Log.d(PROFILE_TAG, "DEV_OPT split success uri=$uri")
            return true
        }
    }
    return false
}

private fun updateUnifiedAccountInfoByProvider(
    context: Context,
    nickname: String,
    avatarPath: String
): Boolean {
    val resolver = context.contentResolver
    val candidateCalls = listOf(
        Bundle().apply {
            putString("nickname", nickname)
            putString("title", nickname)
            putString("avatarPath", avatarPath)
            putString("avatar", avatarPath)
            putString("icon", avatarPath)
        },
        Bundle().apply {
            putString("key", "nickname")
            putString("value", nickname)
            putString("avatar", avatarPath)
            putString("icon", avatarPath)
        }
    )
    candidateCalls.forEach { extras ->
        val updated = runCatching {
            val result = resolver.call(ACCOUNT_USERINFO_URI, METHOD_DEV_OPT, null, extras)
            isBundleSuccess(result)
        }.getOrDefault(false)
        if (updated) return true
    }

    val rows = runCatching {
        resolver.update(
            ACCOUNT_USERINFO_URI,
            ContentValues().apply {
                put("nickname", nickname)
                put("title", nickname)
                put("name", nickname)
                put("avatarPath", avatarPath)
                put("avatar", avatarPath)
                put("icon", avatarPath)
            },
            null,
            null
        )
    }.getOrDefault(0)
    return rows > 0
}

private fun logProviderDiscovery(context: Context) {
    val pm = context.packageManager
    val auth = PERSONAL_INFO_URI.authority.orEmpty()
    Log.d(PROFILE_TAG, "uri=${PERSONAL_INFO_URI}")
    Log.d(PROFILE_TAG, "authority=$auth exists=${pm.resolveContentProvider(auth, 0) != null}")
}

private suspend fun submitAccountProfileUpdate(
    context: Context,
    nickname: String,
    avatarAssetUrl: String
): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val personalInfoUpdated = updatePersonalInfoByProvider(context, nickname, avatarAssetUrl)
        val unifiedAccountUpdated = updateUnifiedAccountInfoByProvider(context, nickname, avatarAssetUrl)
        if (personalInfoUpdated || unifiedAccountUpdated) {
            runCatching { context.contentResolver.notifyChange(PERSONAL_INFO_URI, null) }
            runCatching { context.contentResolver.notifyChange(ACCOUNT_USERINFO_URI, null) }
            return@runCatching Unit
        }
        throw IllegalStateException("Provider 接口不可用，请切换到支持 com.android.zshd.deviceinfo 的设备")
    }
}

@Composable
private fun rememberSvgLoader(): ImageLoader {
    val context = LocalContext.current
    return remember {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }
}

// 内置头像用 drawable 资源渲染（painterResource 最可靠，绕开 Coil 从 assets 加载在部分设备上失败的问题，
// 避免头像变灰）。顺序与 avatarAssets 一一对应。
private val avatarDrawables = listOf(
    R.drawable.avatar_01, R.drawable.avatar_02, R.drawable.avatar_03, R.drawable.avatar_04,
    R.drawable.avatar_05, R.drawable.avatar_06, R.drawable.avatar_07, R.drawable.avatar_08,
    R.drawable.avatar_09, R.drawable.avatar_10, R.drawable.avatar_11
)

/**
 * 头像渲染：内置头像（model 为内置 url 或 null 时按 index）用 drawable；非内置（远程/自定义）才走 Coil。
 */
@Composable
private fun AvatarImage(
    model: String?,
    fallbackIndex: Int,
    avatarAssets: List<String>,
    imageLoader: ImageLoader,
    modifier: Modifier,
) {
    // model 已是上游解析后的最终来源：为 null 时用内置 fallbackIndex；非 null（远程 URL 或编号映射
    // 后的内置 asset path）一律按其本身渲染——不再二次做文件名匹配，否则会把“恰好叫 avatar_xx 的
    // 平台远程头像”错误替换成本地图（导致‘头像不是平台返回的’）。仅当其本身就是内置 asset 时走本地。
    val builtinIndex = when {
        model == null -> fallbackIndex
        else -> avatarAssets.indexOfFirst { it.equals(model, ignoreCase = true) }
    }
    when {
        builtinIndex in avatarDrawables.indices -> Image(
            painter = painterResource(avatarDrawables[builtinIndex]),
            contentDescription = "Avatar",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )

        model != null -> {
            val fallback = painterResource(
                avatarDrawables[fallbackIndex.coerceIn(avatarDrawables.indices)]
            )
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(model)
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = "Avatar",
                modifier = modifier,
                contentScale = ContentScale.Crop,
                // 远程头像加载中/失败时回退到内置头像，避免显示成灰色空圈。
                placeholder = fallback,
                error = fallback
            )
        }

        else -> Image(
            painter = painterResource(avatarDrawables[fallbackIndex.coerceIn(avatarDrawables.indices)]),
            contentDescription = "Avatar",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}

class MainActivity : ComponentActivity() {
    // 当前跳转目标；singleTask 复用实例时由 onNewIntent 更新，驱动 Compose 切到对应子页。
    private val startTargetState = androidx.compose.runtime.mutableStateOf(StartTarget())

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        startTargetState.value = resolveStartTarget(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 冷启动用带 logo 的闪屏主题遮住首帧组装时间，进入后随即切回正常主题。
        setTheme(R.style.Theme_设置)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val authority = "com.android.zshd.deviceinfo"
        val startTarget = resolveStartTarget(intent)
        startTargetState.value = startTarget
        if (intent?.getBooleanExtra(EXTRA_FROM_SYSTEMUI_ACTIVATION, false) == true &&
            isDeviceBoundForSystemUiActivation(this)
        ) {
            Log.d(PROFILE_TAG, "started by SystemUI and already bound; return to launcher")
            startActivity(Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
            finish()
            return
        }

        val providerInfo = packageManager.resolveContentProvider(authority, 0)

        if (providerInfo != null) {
            Log.d("PersonalINfo", "Provider 存在")
        } else {
            Log.d("PersonalINfo", "Provider 不存在")
        }
        val controller = WindowInsetsControllerCompat(window, window.decorView)

        // 隐藏状态栏
        controller.hide(WindowInsetsCompat.Type.statusBars())

        // 可选：下滑临时显示
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        enableEdgeToEdge()
        setContent {
            设置Theme {
                // 冷启动缩入动画：内容从 0.92 缩放 + 淡入进场，仅首次播放一次。
                var entered by rememberSaveable { mutableStateOf(false) }
                val enterScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (entered) 1f else 0.92f,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 320),
                    label = "settingsEnterScale"
                )
                val enterAlpha by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (entered) 1f else 0f,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 280),
                    label = "settingsEnterAlpha"
                )
                LaunchedEffect(Unit) { entered = true }
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.graphicsLayer {
                        scaleX = enterScale
                        scaleY = enterScale
                        alpha = enterAlpha
                    }
                ) {
                    NavigationRailExample(startTarget = startTargetState.value)
                }
            }
        }
        // 触摸不打乱 D-pad 焦点：进入 touch mode 时 ViewRootImpl 会对“当前真正持有焦点的 View”
        // (ComposeView 内部的 AndroidComposeView) 执行 clearFocusInTouchMode，导致焦点高亮丢失、
        // 下次按键从默认位置重入。只有让该 View 自身 isFocusableInTouchMode=true 才会在触摸下保留焦点
        // （设到 decorView 对后代无效）。故递归把内容视图树标记为 touch-mode 下可聚焦。
        // 注：touch-mode 是设备级行为，此方案需真机验证；不保证所有 ROM 生效。
        window.decorView.post {
            fun markFocusableInTouchMode(v: android.view.View) {
                v.isFocusableInTouchMode = true
                if (v is android.view.ViewGroup) {
                    for (i in 0 until v.childCount) markFocusableInTouchMode(v.getChildAt(i))
                }
            }
            runCatching { markFocusableInTouchMode(window.decorView) }
        }
        // iot sdk 启动含 provider IPC + sdk.init/iotStart，放后台线程避免阻塞首帧渲染。
        Thread({ IotSdkBridge.start(applicationContext) }, "iot-sdk-start").start()
    }
}

data class StartTarget(
    val selectedDestination: Int = 0,
    val wifiStartRoute: String = Destinations.WifiScreen.route,
    val fromSystemUiActivation: Boolean = false,
)

private fun resolveStartTarget(intent: Intent?): StartTarget {
    val fromSystemUiActivation =
        intent?.getBooleanExtra(EXTRA_FROM_SYSTEMUI_ACTIVATION, false) == true
    return when (intent?.action) {
        ACTION_PAGE_PERSONAL_CENTER -> StartTarget(
            selectedDestination = 0,
            fromSystemUiActivation = fromSystemUiActivation,
        )

        Settings.ACTION_WIFI_SETTINGS,
        ACTION_PAGE_NETWORK,
        ACTION_IOT_PAGE_NET_OPTION -> StartTarget(
            selectedDestination = 1,
            fromSystemUiActivation = fromSystemUiActivation,
        )

        Settings.ACTION_BLUETOOTH_SETTINGS,
        ACTION_PAGE_BLUETOOTH -> StartTarget(
            selectedDestination = 2,
            fromSystemUiActivation = fromSystemUiActivation,
        )

        ACTION_PAGE_SOUND_DISPLAY -> StartTarget(
            selectedDestination = 3,
            fromSystemUiActivation = fromSystemUiActivation,
        )

        Settings.ACTION_DREAM_SETTINGS,
        ACTION_PAGE_SCREENSAVER -> StartTarget(
            selectedDestination = 4,
            fromSystemUiActivation = fromSystemUiActivation,
        )

        ACTION_PAGE_ENERGY_SAVING -> StartTarget(
            selectedDestination = 6,
            fromSystemUiActivation = fromSystemUiActivation,
        )

        ACTION_IOT_PAGE_PRIVATE -> StartTarget(
            selectedDestination = 9,
            fromSystemUiActivation = fromSystemUiActivation,
        )

        else -> StartTarget(fromSystemUiActivation = fromSystemUiActivation)
    }
}

private fun isDeviceBoundForSystemUiActivation(context: Context): Boolean {
    return queryBindStatusForSystemUiActivation(context) == "1"
}

private fun queryBindStatusForSystemUiActivation(context: Context): String? {
    return queryProviderValueForSystemUiActivation(context, DEV_STAT_URI, KEY_BIND_STATUS)
        ?: queryProviderValueForSystemUiActivation(context, DEVICE_INFO_URI, KEY_BIND_STATUS)
}

private fun queryProviderValueForSystemUiActivation(
    context: Context,
    uri: Uri,
    key: String,
): String? {
    return runCatching {
        val extras = Bundle().apply {
            putString("key", key)
            putString(key, "")
        }
        val result = context.contentResolver.call(uri, METHOD_DEV_QUERY, null, extras)
        normalizeSystemUiActivationProviderValue(result?.getString(key))
            ?: normalizeSystemUiActivationProviderValue(result?.getString("value"))
            ?: normalizeSystemUiActivationProviderValue(result?.getString("result"))
    }.onFailure {
        Log.d(PROFILE_TAG, "query $key failed uri=$uri: ${it.message}")
    }.getOrNull()
}

private fun normalizeSystemUiActivationProviderValue(value: Any?): String? {
    val text = value?.toString()?.trim().orEmpty()
    return text.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}


sealed class Destinations(val route: String) {
    object Home : Destinations("home")
    object Detail : Destinations("detail")
    object WifiScreen : Destinations("wifi_screen")
    object AddWifiScreen : Destinations("add_wifi_screen")
    object WifiConnectScreen : Destinations("wifi_connect_screen/{ssid}/{security}") {
        fun createRoute(ssid: String, security: String): String {
            return "wifi_connect_screen/${Uri.encode(ssid.normalizedWifiSsid())}/${Uri.encode(security)}"
        }
    }

    object WifiDetailScreen : Destinations("wifi_detail_screen/{ssid}") {
        fun createRoute(ssid: String) = "wifi_detail_screen/${Uri.encode(ssid.normalizedWifiSsid())}"
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationRailExample(
    modifier: Modifier = Modifier,
    startTarget: StartTarget = StartTarget(),
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val navScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val names = stringArrayResource(R.array.docks)
    val navItems = remember(names) { names.toList() }
    //val icons = integerArrayResource(R.array.dockicons);
    var selectedDestination by rememberSaveable { mutableIntStateOf(startTarget.selectedDestination) }
    // singleTask 复用实例时 onNewIntent 会换 startTarget：跳过首次(初值已用)，之后切到新目标页。
    var lastHandledStartTarget by remember { mutableStateOf(startTarget) }
    LaunchedEffect(startTarget) {
        if (startTarget !== lastHandledStartTarget) {
            selectedDestination = startTarget.selectedDestination
            lastHandledStartTarget = startTarget
        }
    }
    val navItemFocusRequesters = remember(navItems.size) {
        List(navItems.size) { FocusRequester() }
    }
    // 内容区(子目录)焦点入口：菜单按右键时把焦点送入对应子目录。
    val contentFocusRequester = remember { FocusRequester() }
    // 每个目标页一个“顶部控件”FocusRequester：右键进入时聚焦该页首个控件。
    // 按页区分以避免切页过渡期同一 requester 被新旧两个页面同时绑定而崩溃。
    val entryFocusRequesters = remember(navItems.size) {
        List(navItems.size) { FocusRequester() }
    }
    // 焦点是否在内容区(子目录)：用于统一返回逻辑——在子目录返回先回菜单，焦点在菜单时再返回 launcher。
    // 必须用 remember 而非 rememberSaveable: 它反映“当前运行时焦点位置”, 持久化会在进程/配置重建后
    // 被恢复为陈旧 true(真实焦点其实在菜单), 导致首次 BACK 误判为“在内容区”而无反应。
    var focusInContent by remember { mutableStateOf(false) }

    LaunchedEffect(navItems.size) {
        navItemFocusRequesters.getOrNull(selectedDestination)?.requestFocus()
    }

    fun focusSelectedNavItem() {
        navItemFocusRequesters.getOrNull(selectedDestination)?.requestFocus()
        focusInContent = false
    }

    // 右键进入子目录并聚焦“最上面的第一个控件”。
    // 优先聚焦当前页用 LocalEntryFocusRequester 标记的顶部控件(requestFocus 针对具体控件，
    // 未挂载时会抛异常被吞掉、不会像 focusGroup 那样回退到窗口首个控件=个人中心)；
    // 重页面首帧延后挂载，跨帧重试；页面没标记时回退到系统 2D 右向搜索(此时内容已挂载，安全)。
    fun enterContent() {
        val req = entryFocusRequesters.getOrNull(selectedDestination) ?: return
        navScope.launch {
            var tries = 0
            while (!focusInContent && tries < 18) {
                runCatching { req.requestFocus() }
                withFrameNanos { }
                tries++
            }
            if (!focusInContent) focusManager.moveFocus(FocusDirection.Right)
        }
    }

    fun handleBackNavigation() {
        // 1) 仅当停留在 Wi-Fi 页(承载内嵌 NavHost)且其确有二级页时, 先在其内部回退。
        //    NavHost 只在 selectedDestination==1 时被组合; 不加此门控会在离开 Wi-Fi 页后
        //    对脱离组合的 navController 误调 popBackStack(其回退栈可能因左键/切页回菜单未清空而残留),
        //    把 BACK 吞掉, 既不回菜单也不回 launcher。
        if (selectedDestination == 1 && navController.previousBackStackEntry != null) {
            navController.popBackStack()
            return
        }
        // 2) 焦点在子目录：返回先回到左侧菜单。
        if (focusInContent) {
            focusSelectedNavItem()
            return
        }
        // 3) 焦点已在菜单：返回 launcher。
        context.launchSettingsExitTarget()
    }

    BackHandler(onBack = ::handleBackNavigation)

    val navRailWidth = 180.dp
    //val tintcolor = Color(0xFF4577FF)
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            //containerColor = colorResource(R.color.topbar),
            modifier = Modifier.fillMaxSize(),
            topBar = {
                CenterAlignedTopAppBar(
                    modifier = Modifier
                        .padding(0.dp),
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = colorResource(R.color.topbar)
                    ),
                    title = { Text("设置", fontSize = 17.sp) },
                    navigationIcon = {
                        IconButton(
                            // TV 上返回统一走 BACK 键(BackHandler)与触摸点击；该箭头不需要、也不应被
                            // D-pad 聚焦。canFocus=false 把它移出焦点遍历后，内容区顶部控件按上键时上方
                            // 再无可聚焦目标，焦点自然停留，不会越界跳到标题栏箭头。canFocus 只影响焦点
                            // 遍历，不影响 onClick/clickable，触摸点击与 BACK 键返回均不受影响。
                            modifier = Modifier.focusProperties { canFocus = false },
                            onClick = ::handleBackNavigation
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.back),
                                contentDescription = "返回"
                            )
                        }
                    }
                )
            }
        ) { contentPadding ->
            Row(Modifier.fillMaxSize().background(colorResource(R.color.topbar))) {
                Surface(
                    modifier = Modifier.fillMaxHeight().width(230.dp),
                    //color = NavigationRailDefaults.ContainerColor // 保持和 NavigationRail 一样的背景色
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .background(colorResource(R.color.topbar))
                            .fillMaxHeight()
                            .padding(top = contentPadding.calculateTopPadding())
                            .padding(vertical = 4.dp), // 给 item 上下加一点边距
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        itemsIndexed(
                            items = navItems,
                            key = { index, destination -> "$index-$destination" }
                        ) { index, destination ->
                            val isSelected = selectedDestination == index
                            // 菜单项选中态用瞬切（不做颜色过渡）：动画过渡会在蓝↔深色之间插出灰色中间帧，
                            // 表现为“文字变灰 + 切换留痕”，TV 焦点快速移动时尤其明显，故移除冗余动画。
                            val contentColor = if (isSelected) Color(0xFF4577FF) else Color(0xFF222222)
                            val backgroundColor = if (isSelected) Color.White else Color.Transparent
                            fun selectNavItem(target: Int) {
                                val bounded = target.coerceIn(0, navItems.lastIndex)
                                selectedDestination = bounded
                                navItemFocusRequesters.getOrNull(bounded)?.requestFocus()
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .focusRequester(navItemFocusRequesters[index])
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            selectedDestination = index
                                            // 焦点回到菜单：清除“焦点在子目录”标记。
                                            focusInContent = false
                                        }
                                    }
                                    .onKeyEvent {
                                        if (it.type != KeyEventType.KeyDown) {
                                            return@onKeyEvent false
                                        }
                                        when (it.key) {
                                            Key.DirectionDown -> {
                                                selectNavItem(index + 1)
                                                true
                                            }

                                            Key.DirectionUp -> {
                                                // 菜单顶部再按上不跳到标题栏退出箭头，停在第一项即可，
                                                // 避免焦点意外“跳出”菜单。
                                                if (index != 0) {
                                                    selectNavItem(index - 1)
                                                }
                                                true
                                            }
                                            // 右键进入子目录并聚焦该页最上面的第一个控件。
                                            Key.DirectionRight -> {
                                                enterContent()
                                                true
                                            }

                                            Key.DirectionCenter,
                                            Key.Enter -> {
                                                selectNavItem(index)
                                                enterContent()
                                                true
                                            }

                                            else -> false
                                        }
                                    }
                                    .padding(start = 20.dp, end = 40.dp)
                                    .height(53.dp)
                                    .clickable(
                                        onClick = { selectNavItem(index) },
                                        indication = null, // 1. 禁用默认的点击效果（波纹）
                                        interactionSource = remember { MutableInteractionSource() }
                                    )
                                    .background(
                                        color = backgroundColor,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .focusable()
                            ) {
                                Spacer(Modifier.width(25.dp))
                                Icon(
                                    painter = painterResource(R.drawable.account),
                                    contentDescription = "",
                                    tint = contentColor
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = destination,
                                    color = contentColor
                                )
                                Spacer(Modifier.width(80.dp))
                            }
                        }
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = colorResource(R.color.topbar)),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .focusRequester(contentFocusRequester)
                        .focusGroup()
                        // 内容区(子目录)是否持有焦点的真相来源：用于统一返回逻辑，
                        // 不依赖 requestFocus 是否成功，焦点无论经右键/确定键/默认搜索进入都能正确反映。
                        .onFocusChanged { focusInContent = it.hasFocus }
                        .onPreviewKeyEvent {
                            if (selectedDestination != 3 &&
                                it.type == KeyEventType.KeyDown &&
                                it.key == Key.DirectionLeft
                            ) {
                                // 左键从子目录返回左侧菜单。
                                focusSelectedNavItem()
                                true
                            } else {
                                false
                            }
                        }
                        //.fillMaxSize(1f)
                        .clip(shape = RoundedCornerShape(46.dp))
                        //.background(colorResource(R.color.black) ) // 内容区域背景色
                        .padding(top = contentPadding.calculateTopPadding(), end = 40.dp)
                ) {
                    AnimatedContent(
                        targetState = selectedDestination,
                        transitionSpec = {
                            // 纯交叉淡入淡出：不移动重内容，避免切页时逐帧重排造成的卡顿；
                            // 重页面（Wifi/蓝牙/存储/一键检测）用更短时长，尽快让出主线程做首帧组装。
                            val heavyPage = initialState in HEAVY_SETTINGS_PAGES ||
                                    targetState in HEAVY_SETTINGS_PAGES
                            val enterDuration = if (heavyPage) 140 else 200
                            (fadeIn(animationSpec = tween(enterDuration, easing = FastOutSlowInEasing)) togetherWith
                                    fadeOut(animationSpec = tween(120, easing = FastOutLinearInEasing)))
                                .using(SizeTransform(clip = false) { _, _ -> snap() })
                        },
                        label = "settings-content"
                    ) { destination ->
                        CompositionLocalProvider(
                            LocalEntryFocusRequester provides entryFocusRequesters.getOrNull(destination)
                        ) {
                            when (destination) {
                                0 -> SettingsPageEnterMotion { PersonalCenterScreen() }
                                1 -> {
                                    SettingsPageEnterMotion(heavy = true) {
                                        NavHost(
                                            navController = navController,
                                            startDestination = startTarget.wifiStartRoute
                                        ) {
                                            composable(Destinations.WifiScreen.route) {
                                                WifiManagerScreen(navController = navController)
                                            }
                                            composable(Destinations.AddWifiScreen.route) {
                                                AddWifiNetworkScreen(onBack = { navController.popBackStack() })
                                            }
                                            composable(
                                                Destinations.WifiConnectScreen.route,
                                                arguments = listOf(
                                                    navArgument("ssid") { type = NavType.StringType },
                                                    navArgument("security") { type = NavType.StringType }
                                                )
                                            ) {
                                                val ssid = Uri.decode(it.arguments?.getString("ssid") ?: "")
                                                val security =
                                                    Uri.decode(it.arguments?.getString("security") ?: SECURITY_WPA_PSK)
                                                WifiConnectScreen(
                                                    ssid = ssid,
                                                    security = security,
                                                    onBack = { navController.popBackStack() }
                                                )
                                            }
                                            composable(
                                                Destinations.WifiDetailScreen.route,
                                                arguments = listOf(navArgument("ssid") { type = NavType.StringType })
                                            ) {
                                                val ssid = Uri.decode(it.arguments?.getString("ssid") ?: "")
                                                WifiDetailScreen(ssid = ssid, onBack = { navController.popBackStack() })
                                            }
                                        }
                                    }
                                }

                                2 -> {
                                    SettingsPageEnterMotion(heavy = true) { BlueToothScreen(modifier, navController) }
                                }

                                3 -> {
                                    SettingsPageEnterMotion {
                                        SoundAndDisplayScreen(
                                            onExitLeft = {
                                                navItemFocusRequesters.getOrNull(selectedDestination)?.requestFocus()
                                            }
                                        )
                                    }
                                }

                                4 -> {
                                    SettingsPageEnterMotion { ScreenSaverSettingsScreen(modifier = modifier) }
                                }

                                5 -> {
                                    SettingsPageEnterMotion { HdmiSettingsScreen() }
                                }

                                6 -> {
                                    SettingsPageEnterMotion { RebootScreen { } }
                                }

                                7 -> {
                                    SettingsPageEnterMotion { LabScreen() }
                                }

                                8 -> {
                                    SettingsPageEnterMotion { DialectSettingsScreen() }
                                }

                                9 -> {
                                    SettingsPageEnterMotion { PrivacyScreen() }
                                }

                                10 -> {
                                    SettingsPageEnterMotion(heavy = true) { StorageSettingsScreen() }
                                }

                                11 -> {
                                    SettingsPageEnterMotion { LocalInfoScreen() }
                                }

                                12 -> {
                                    SettingsPageEnterMotion(heavy = true) {
                                        OneKeyCheckScreen(
                                            onOpenNetworkSettings = { selectedDestination = 1 }
                                        )
                                    }
                                }

                                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(text = "${names[destination]} 页面")
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}

private data class UnifiedAccountInfo(
    val nickname: String?,
    val account: String?,
    val avatarPath: String?
)

private fun readBundleString(bundle: Bundle?, keys: List<String>): String? {
    if (bundle == null) return null
    return keys.firstNotNullOfOrNull { key ->
        bundle.get(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
    }
}

private fun readCursorString(cursor: Cursor, keys: List<String>): String? {
    val hasRow = if (cursor.position >= 0) true else cursor.moveToFirst()
    if (!hasRow) return null
    return keys.firstNotNullOfOrNull { key ->
        val index = cursor.getColumnIndex(key)
        if (index >= 0) cursor.getString(index)?.trim()?.takeIf { it.isNotEmpty() } else null
    }
}

private fun mergeUnifiedAccountInfo(
    primary: UnifiedAccountInfo?,
    fallback: UnifiedAccountInfo?
): UnifiedAccountInfo? {
    val merged = UnifiedAccountInfo(
        nickname = primary?.nickname ?: fallback?.nickname,
        account = primary?.account ?: fallback?.account,
        avatarPath = primary?.avatarPath ?: fallback?.avatarPath
    )
    return if (merged.nickname == null && merged.account == null && merged.avatarPath == null) {
        null
    } else {
        merged
    }
}

private fun queryUnifiedAccountInfo(context: Context): UnifiedAccountInfo? {
    return runCatching {
        context.contentResolver.query(ACCOUNT_USERINFO_URI, null, null, null, null)?.use { cursor ->
            val bundle = cursor.extras
            val nicknameKeys = listOf("title", "nickname", "name")
            val accountKeys = listOf("summary", "account", "phone", "mobile", "userTags", "username")
            val avatarKeys = listOf("icon", "avatar", "avatarPath", "avatarpath", "avatar_url")
            val info = UnifiedAccountInfo(
                nickname = readBundleString(bundle, nicknameKeys) ?: readCursorString(cursor, nicknameKeys),
                account = readBundleString(bundle, accountKeys) ?: readCursorString(cursor, accountKeys),
                avatarPath = readBundleString(bundle, avatarKeys) ?: readCursorString(cursor, avatarKeys)
            )
            if (info.nickname == null && info.account == null && info.avatarPath == null) null else info
        }
    }.getOrNull()
}

@Composable
fun PersonalCenterScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    var logoutInProgress by remember { mutableStateOf(false) }
    var nickname by rememberSaveable { mutableStateOf("小翼8899") }
    var avatarIndex by rememberSaveable { mutableIntStateOf(0) }
    var avatarModel by rememberSaveable { mutableStateOf<String?>(null) }
    var showEditPage by rememberSaveable { mutableStateOf(false) }
    var profileSaving by remember { mutableStateOf(false) }
    var accountText by rememberSaveable { mutableStateOf("未获取账号") }
    var profileVersion by remember { mutableStateOf(0) }
    // 头像资源用 PNG：这些 SVG 实为 <pattern> 包内嵌位图，AndroidSVG 渲染 pattern 填充
    // 不可靠（会渲染成透明，叠在灰底上即一片灰色）。改用从 SVG 中抽出的真实 PNG。
    val avatarAssets = listOf(
        "file:///android_asset/avatars/avatar_01.png",
        "file:///android_asset/avatars/avatar_02.png",
        "file:///android_asset/avatars/avatar_03.png",
        "file:///android_asset/avatars/avatar_04.png",
        "file:///android_asset/avatars/avatar_05.png",
        "file:///android_asset/avatars/avatar_06.png",
        "file:///android_asset/avatars/avatar_07.png",
        "file:///android_asset/avatars/avatar_08.png",
        "file:///android_asset/avatars/avatar_09.png",
        "file:///android_asset/avatars/avatar_10.png",
        "file:///android_asset/avatars/avatar_11.png"
    )
    val svgLoader = rememberSvgLoader()

    // 修改头像/账号资料：拉起统一账号 App 的设置页（SetProfileActivity）并等待返回结果，
    // 等价于 startActivityForResult(intent, 100) + onActivityResult。返回 RESULT_OK 时
    // 说明用户在对端完成了设置，bump profileVersion 触发重新拉取头像/昵称/账号。
    val setProfileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // 回到本页即刷新头像/昵称：即使对端未回传 RESULT_OK（部分实现改完头像直接返回）也刷新；
        // 并延时多刷几次，兼容对端异步写入 provider、返回时数据尚未更新导致需要退出重进才生效的问题。
        Log.d(PROFILE_TAG, "setProfile returned resultCode=${result.resultCode}, refresh profile")
        profileVersion++
        scope.launch {
            repeat(3) {
                delay(700)
                profileVersion++
            }
        }
    }

    fun openSetProfileForResult() {
        val intent = Intent().apply {
            component = ComponentName(
                "com.chinatelecom.accloudbox",
                "cn.com.chinatelecom.account.tv.activity.SetProfileActivity"
            )
        }
        try {
            setProfileLauncher.launch(intent)
        } catch (e: Exception) {
            // 对方应用未安装或组件不存在
            Toast.makeText(appContext, "无法打开账号设置页", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(appContext) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                profileVersion++
            }
        }
        val accountReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_TV_SIGN_IN,
                    ACTION_TV_SIGN_OUT -> profileVersion++
                }
            }
        }
        listOf(PERSONAL_INFO_URI, ACCOUNT_USERINFO_URI, DEV_STAT_URI).forEach { uri ->
            runCatching { appContext.contentResolver.registerContentObserver(uri, true, observer) }
        }
        ContextCompat.registerReceiver(
            appContext,
            accountReceiver,
            IntentFilter().apply {
                addAction(ACTION_TV_SIGN_IN)
                addAction(ACTION_TV_SIGN_OUT)
            },
            ContextCompat.RECEIVER_EXPORTED
        )
        onDispose {
            runCatching { appContext.contentResolver.unregisterContentObserver(observer) }
            runCatching { appContext.unregisterReceiver(accountReceiver) }
        }
    }

    LaunchedEffect(appContext, profileVersion) {
        logProviderDiscovery(appContext)

        val accountInfo = queryUnifiedAccountInfo(appContext)
        val providerAvatar = queryPersonalInfoValue(appContext, "avatarpath")
        val providerNickname = queryPersonalInfoValue(appContext, "nickname")
        val providerAccount = queryAccountFromPersonalInfo(appContext)
        val backendAccountInfo = if (
            providerNickname.isNullOrBlank() ||
            providerAccount.isNullOrBlank() ||
            providerAvatar.isNullOrBlank() ||
            accountInfo == null
        ) {
            fetchAccountProfileFromBackend().getOrNull()?.let {
                UnifiedAccountInfo(
                    nickname = it.nickname,
                    account = it.account,
                    avatarPath = it.avatarPath
                )
            }
        } else {
            null
        }
        val mergedAccountInfo = mergeUnifiedAccountInfo(accountInfo, backendAccountInfo)
        val resolvedNickname = mergedAccountInfo?.nickname
            ?: providerNickname
        if (!resolvedNickname.isNullOrBlank()) {
            nickname = resolvedNickname
        }

        val resolvedAvatar = mergedAccountInfo?.avatarPath ?: providerAvatar
        Log.d(
            PROFILE_TAG, "resolved avatar='$resolvedAvatar' index=${
                resolvedAvatar?.let { findAvatarIndex(avatarAssets, it) } ?: -1
            }")
        if (!resolvedAvatar.isNullOrBlank()) {
            val accountAvatar = resolvedAvatar.trim()
            // 一律以平台返回为准：是真实 URL/路径(含 :// 或 /)就直接加载远程，不替换成本地内置图；
            // 只有平台返回的是纯编号(headPortraitNum，无图可加载)时才映射到对应内置头像渲染。
            val looksLikeUrl = accountAvatar.contains("://") || accountAvatar.contains('/')
            if (looksLikeUrl) {
                avatarModel = accountAvatar
            }
        }

        val fetched = mergedAccountInfo?.account
            ?: providerAccount
        if (!fetched.isNullOrBlank()) {
            accountText = fetched
        } else {
            Log.w(PROFILE_TAG, "account not found from provider")
            if (nickname.isNotBlank()) {
                accountText = nickname
            }
        }
    }

    if (showEditPage) {
        EditAccountInfoScreen(
            currentNickname = nickname,
            currentAvatarIndex = avatarIndex,
            avatarAssets = avatarAssets,
            saving = profileSaving,
            onBack = { showEditPage = false },
            onSave = { newNickname, newAvatarIndex ->
                if (profileSaving) return@EditAccountInfoScreen
                profileSaving = true
                val avatar = avatarAssets.getOrElse(newAvatarIndex) { avatarAssets.first() }
                scope.launch {
                    val result = submitAccountProfileUpdate(appContext, newNickname, avatar)
                    profileSaving = false
                    if (result.isSuccess) {
                        nickname = newNickname
                        avatarIndex = newAvatarIndex
                        avatarModel = avatar
                        showEditPage = false
                        Toast.makeText(appContext, "账号信息修改成功", Toast.LENGTH_SHORT).show()
                    } else {
                        val msg = result.exceptionOrNull()?.message ?: "账号信息修改失败，请稍后重试"
                        Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        return
    }

    // 退出登录成功/失败的统一收尾（成功信号可能来自结果广播、devStat 解绑状态或主动轮询，先到先处理）。
    val finishLogout = fun(success: Boolean) {
        if (!logoutInProgress) return
        Log.i(LOGOUT_TAG, "logout finished, success=$success")
        logoutInProgress = false
        Toast.makeText(
            appContext,
            if (success) "账号已被解除绑定" else "退出登录失败，请稍后再试",
            Toast.LENGTH_SHORT
        ).show()
    }

    DisposableEffect(appContext, logoutInProgress) {
        if (!logoutInProgress) {
            onDispose { }
        } else {
            // 1) 退出登录结果广播：com.telecom.smartcloud.action.result，log_out_result 布尔。
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action != ACTION_LOG_OUT_RESULT) return
                    val result = intent.getBooleanExtra(KEY_LOG_OUT_RESULT, false)
                    Log.i(LOGOUT_TAG, "recv log_out result broadcast, log_out_result=$result")
                    finishLogout(result)
                }
            }
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(ACTION_LOG_OUT_RESULT),
                ContextCompat.RECEIVER_EXPORTED
            )

            // 2) 解绑状态变化：监听 devStat，通知 URI 形如
            //    content://com.android.zshd.deviceinfo/devStat?bindStatus=2。
            //    bindStatus=2 未登录(解绑成功)，bindStatus=1 已登录。
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    val status = uri?.getQueryParameter(KEY_BIND_STATUS)
                    Log.i(LOGOUT_TAG, "devStat changed, uri=$uri bindStatus=$status")
                    val unbound = status?.trim() == "2" ||
                            (status == null && isUnboundFromDevStat(appContext))
                    Log.i(LOGOUT_TAG, "bindStatus=$status -> unbound=$unbound (2=已退出登录)")
                    if (unbound) finishLogout(true)
                }
            }
            runCatching {
                appContext.contentResolver.registerContentObserver(DEV_STAT_URI, true, observer)
            }

            onDispose {
                runCatching { appContext.unregisterReceiver(receiver) }
                runCatching { appContext.contentResolver.unregisterContentObserver(observer) }
            }
        }
    }

    // 退出登录期间主动轮询 devStat 的 bindStatus：读到 ==2 即视为退出登录成功；超时仍未解绑则失败。
    LaunchedEffect(logoutInProgress) {
        if (!logoutInProgress) return@LaunchedEffect
        val deadline = SystemClock.elapsedRealtime() + LOGOUT_TIMEOUT_MS
        while (logoutInProgress && SystemClock.elapsedRealtime() < deadline) {
            val status = withContext(Dispatchers.IO) {
                queryBindStatusForSystemUiActivation(appContext)
            }?.trim()
            Log.i(LOGOUT_TAG, "poll devStat bindStatus=$status (2=已退出登录)")
            if (status == BIND_STATUS_UNBOUND) {
                finishLogout(true)
                return@LaunchedEffect
            }
            delay(LOGOUT_POLL_INTERVAL_MS)
        }
        if (logoutInProgress) {
            finishLogout(false)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()

            .padding(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxHeight(0.5f),
            colors = CardDefaults.cardColors(containerColor = colorResource(R.color.cardcolor))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.padding(top = 30.dp).height(108.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(29.dp))
                                .background(Color(0xFFD8D8DC)),
                            contentAlignment = Alignment.Center
                        ) {
                            AvatarImage(
                                model = avatarModel,
                                fallbackIndex = avatarIndex,
                                avatarAssets = avatarAssets,
                                imageLoader = svgLoader,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(nickname, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color(0xFF23252B))
                        Spacer(Modifier.weight(1f))
                        Text(
                            "修改",
                            color = Color(0xFF8C9097),
                            fontSize = 15.sp,
                            modifier = Modifier.entryFocus().clickable { openSetProfileForResult() }
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            painter = painterResource(R.drawable.arrow_right),
                            contentDescription = "修改",
                            tint = Color(0xFFB5B8BE),
                            modifier = Modifier.clickable { openSetProfileForResult() }
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.padding(vertical = 18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "小翼管家账号",
                            fontSize = 17.sp,
                            color = Color(0xFF23252B),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(accountText, color = Color(0xFF8C9097), fontSize = 17.sp)
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(
                            onClick = {

                                logoutInProgress = true
                                Log.i(LOGOUT_TAG, "logout clicked, call unbind (DEV_OPT unbindStatus=2)")
                                // 通过 ZshdProvider DEV_OPT 写入 unbindStatus=2 触发解绑。
                                val unbindOk = IotSdkBridge.unbindDevice(appContext)
                                Log.i(LOGOUT_TAG, "iotsdk unbindDevice returned=$unbindOk, waiting bindStatus=2")
                                // 兼容：同时广播通知其他组件执行退出登录/解绑。
                                sendLogoutBroadcasts(appContext)
                            },
                            enabled = !logoutInProgress,
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFFFF5A5A)
                            ),
                            border = BorderStroke(1.dp, Color(0xFFFF7A7A))
                        ) {
                            Text(
                                if (logoutInProgress) "退出中..." else "退出登录",
                                color = Color(0xFFFF5A5A),
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = colorResource(R.color.cardcolor)),
            modifier = Modifier.fillMaxHeight(0.7F)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(1f)
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        // 二维码是底部“扫码下载”的静态大图，不需要参与首帧；延后一帧解码渲染，压低落地页首帧 draw。
                        DeferToNextFrame {
                            Image(painterResource(R.drawable.qrcode), contentDescription = "")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("扫码下载“小翼管家”", color = Color(0xFF7B808A), fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun EditAccountInfoScreen(
    currentNickname: String,
    currentAvatarIndex: Int,
    avatarAssets: List<String>,
    saving: Boolean,
    onBack: () -> Unit,
    onSave: (String, Int) -> Unit
) {
    val svgLoader = rememberSvgLoader()
    var draftNickname by remember(currentNickname) { mutableStateOf(currentNickname) }
    var draftAvatarIndex by remember(currentAvatarIndex) { mutableIntStateOf(currentAvatarIndex) }
    var showAvatarDialog by remember { mutableStateOf(false) }
    var showNicknameDialog by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(R.color.topbar))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()

            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(100.dp).background(colorResource(R.color.topbar))
                ) {
                    Icon(
                        painter = painterResource(R.drawable.back),
                        contentDescription = "返回",
                        modifier = Modifier.clickable { onBack() }
                    )
                    Spacer(Modifier.weight(1f))
                    Text("修改账号信息", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E2025))
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(36.dp))
                }

                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp).height(400.dp)
                ) {
                    Column(modifier = Modifier.padding(top = 45.dp)) {
                        Box(
                            modifier = Modifier
                                .width(250.dp)
                                .height(180.dp)
                                .align(Alignment.CenterHorizontally)
                                .clip(RoundedCornerShape(75.dp))
                                .background(colorResource(R.color.cardcolor)),
                            contentAlignment = Alignment.Center
                        ) {
                            AvatarImage(
                                model = null,
                                fallbackIndex = draftAvatarIndex,
                                avatarAssets = avatarAssets,
                                imageLoader = svgLoader,
                                modifier = Modifier.width(180.dp).height(180.dp)
                            )
                        }
                        Spacer(Modifier.height(45.dp))
                        Row(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .clickable {
                                    showAvatarDialog = true

                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("点击更换头像", color = Color(0xFF4A7CFF), fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                painter = painterResource(R.drawable.arrow_right),
                                contentDescription = "更换头像",
                                tint = Color(0xFF4A7CFF)
                            )
                        }

                        Spacer(Modifier.height(20.dp))
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showNicknameDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "昵称",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF20232A)
                                )
                                Spacer(Modifier.weight(1f))
                                Text(draftNickname, fontSize = 18.sp, color = Color(0xFF8D919A))
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    painter = painterResource(R.drawable.arrow_right),
                                    contentDescription = "修改昵称",
                                    tint = Color(0xFFB5B8BE)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    SecondaryPillButton(
                        text = "取消",
                        enabled = !saving,
                        onClick = { if (!saving) onBack() }
                    )
                    Spacer(Modifier.width(24.dp))
                    PrimaryPillButton(
                        text = if (saving) "提交中..." else "确定",
                        enabled = !saving,
                        onClick = {
                            if (!saving) onSave(draftNickname, draftAvatarIndex)
                        }
                    )
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }

    if (showAvatarDialog) {
        AvatarPickerDialog(
            avatars = avatarAssets,
            selectedIndex = draftAvatarIndex,
            onDismiss = { showAvatarDialog = false },
            onConfirm = {
                draftAvatarIndex = it
                showAvatarDialog = false
            }
        )
    }

    if (showNicknameDialog) {
        NicknameDialog(
            currentValue = draftNickname,
            onDismiss = { showNicknameDialog = false },
            onConfirm = {
                draftNickname = it
                showNicknameDialog = false
            }
        )
    }
}

@Composable
private fun AvatarPickerDialog(
    avatars: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val svgLoader = rememberSvgLoader()
    var localSelection by remember(selectedIndex) { mutableIntStateOf(selectedIndex) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
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
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxHeight(1f).fillMaxWidth(0.8f)
            ) {
                Column(modifier = Modifier.padding(horizontal = 30.dp, vertical = 24.dp)) {
                    Text(
                        "选择头像",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D2026),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))

                    for (row in 0 until 3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (col in 0 until 4) {
                                val index = row * 4 + col
                                if (index < avatars.size) {
                                    val avatar = avatars[index]
                                    AvatarItem(
                                        avatarAsset = avatar,
                                        avatarAssets = avatars,
                                        imageLoader = svgLoader,
                                        selected = index == localSelection,
                                        onClick = { localSelection = index }
                                    )
                                } else {
                                    Spacer(Modifier.size(118.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        SecondaryPillButton(text = "取消", onClick = onDismiss)
                        Spacer(Modifier.width(24.dp))
                        PrimaryPillButton(text = "确定", onClick = { onConfirm(localSelection) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarItem(
    avatarAsset: String,
    avatarAssets: List<String>,
    imageLoader: ImageLoader,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(118.dp)
            .clip(RoundedCornerShape(59.dp))
            .background(Color(0xFFE4E4E7))
            .border(
                if (selected) 3.dp else 0.dp,
                if (selected) Color(0xFF4B79FF) else Color.Transparent,
                RoundedCornerShape(59.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        AvatarImage(
            model = avatarAsset,
            fallbackIndex = 0,
            avatarAssets = avatarAssets,
            imageLoader = imageLoader,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun NicknameDialog(
    currentValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var input by remember(currentValue) { mutableStateOf(currentValue) }
    val maxLen = 12
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.width(560.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 26.dp, vertical = 24.dp)) {
                Text(
                    "修改昵称",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFF2F3F6))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = input,
                            onValueChange = { if (it.length <= maxLen) input = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 16.sp, color = Color(0xFF23252A))
                        )
                        Text("${input.length}/$maxLen", color = Color(0xFF8A8F99), fontSize = 16.sp)
                    }
                    if (input.isBlank()) {
                        Text("请输入昵称", color = Color(0xFFB2B6BF), fontSize = 16.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    SecondaryPillButton(text = "取消", onClick = onDismiss)
                    Spacer(Modifier.width(24.dp))
                    PrimaryPillButton(
                        text = "确定",
                        onClick = { onConfirm(input.ifBlank { currentValue }) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SecondaryPillButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(230.dp)
            .height(66.dp)
            .clip(RoundedCornerShape(33.dp))
            .background(if (enabled) Color(0xFFE9ECF2) else Color(0xFFF2F3F6))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) Color(0xFF4A79FF) else Color(0xFFB8C4E8),
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PrimaryPillButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(230.dp)
            .height(66.dp)
            .clip(RoundedCornerShape(33.dp))
            .background(
                Brush.horizontalGradient(
                    if (enabled) listOf(Color(0xFF57A7FF), Color(0xFF3F47F3))
                    else listOf(Color(0xFFBFD8FF), Color(0xFFAAB1F4))
                )
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    }
}

@Preview(
    showBackground = true, widthDp = 1280,
    heightDp = 720,
)
@Composable
fun WifiManagerScreenPreview() {
    设置Theme {
        NavigationRailExample()
    }
}
