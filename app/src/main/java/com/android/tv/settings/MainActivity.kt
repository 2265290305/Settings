package com.android.tv.settings

import android.content.BroadcastReceiver
import android.content.ComponentName
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private fun resolveRemoteAvatarModel(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isBlank()) return null
    if (value.startsWith("file:///android_asset/", ignoreCase = true)) return null
    if (value.all(Char::isDigit)) return null
    if (value.contains("headPortraitNum=", ignoreCase = true)) return null

    val direct = runCatching { URL(value) }.getOrNull()
    if (direct?.protocol.equals("http", ignoreCase = true) ||
        direct?.protocol.equals("https", ignoreCase = true)
    ) {
        return value
    }

    val endpoint = configuredAccountProfileQueryUrlOrNull() ?: return null
    val resolved = runCatching { URL(URL(endpoint), value).toString() }.getOrNull()
    return resolved?.takeIf {
        it.startsWith("http://", ignoreCase = true) ||
            it.startsWith("https://", ignoreCase = true)
    }
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
    // 昵称走 call(DEV_QUERY)：extra 名直接是字段名(nickname)，provider 以此为查询 key；
    // query() cursor 取列仅作兜底。
    uriCandidates.forEach { uri ->
        val byCall = runCatching {
            val extras = Bundle().apply { putString(key, "") }
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

private fun queryPersonalInfoAvatarWithDebug(context: Context): String? {
    val resolver = context.contentResolver
    val uriCandidates = buildPersonalInfoUriCandidates(context)
    val summary = StringBuilder()
    val avatarKeys = listOf(
        "avatarpath",
        "avatarPath",
        "avatar",
        "avatar_url",
        "avatarUrl",
        "icon",
        "headPortrait",
        "headPortraitUrl",
        "headPortraitPath"
    )

    // 头像走 call(DEV_QUERY)：ZshdProvider 把 extras 的“字段名”直接当查询 key，
    // 所以 extra 名要直接是 avatarpath（而非 putString("key","avatarpath")，那样它会把 "key" 当字段名报错）。
    uriCandidates.forEach { uri ->
        avatarKeys.forEach { key ->
            val byCall = runCatching {
                val extras = Bundle().apply { putString(key, "") }
                Log.d(PROFILE_TAG, "avatar provider call uri=$uri method=$METHOD_DEV_QUERY extras={$key}")
                val result = resolver.call(uri, METHOD_DEV_QUERY, null, extras)
                dumpBundleKeysForDebug("avatar provider call result key=$key", result)
                extractBundleString(result, key)
            }.onFailure {
                Log.d(PROFILE_TAG, "avatar provider call failed uri=$uri key=$key: ${it.message}")
                summary.append("call[$key]=ERR(${it.message}); ")
            }.getOrNull()
            Log.d(PROFILE_TAG, "avatar provider call value key=$key value='$byCall'")
            if (byCall.isNullOrBlank()) {
                summary.append("call[$key]=null; ")
            } else {
                summary.append("call[$key]=$byCall; ")
                Log.i(PROFILE_TAG, "avatar provider summary uri=$uri $summary")
                return byCall
            }
        }
    }

    // 兜底：call 拿不到时再尝试 query() cursor 取列（兼容以 cursor 暴露数据的机型）。
    uriCandidates.forEach { uri ->
        val fromCursor = runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val cols = cursor.columnNames?.toList().orEmpty()
                Log.d(PROFILE_TAG, "avatar provider query uri=$uri columns=$cols")
                if (!cursor.moveToFirst()) {
                    Log.d(PROFILE_TAG, "avatar provider query uri=$uri empty cursor")
                    return@use null
                }
                cols.forEach { col ->
                    val idx = cursor.getColumnIndex(col)
                    val value = if (idx >= 0) cursor.getString(idx) else null
                    Log.d(PROFILE_TAG, "avatar provider query row col=$col value='$value'")
                    if (col.contains("avatar", true) ||
                        col.contains("icon", true) ||
                        col.contains("head", true) ||
                        col.contains("portrait", true)
                    ) {
                        summary.append("queryCol[$col]=$value; ")
                    }
                }
                avatarKeys.firstNotNullOfOrNull { key ->
                    val idx = cursor.getColumnIndex(key)
                    val value = if (idx >= 0) cursor.getString(idx)?.trim() else null
                    Log.d(PROFILE_TAG, "avatar provider query candidate key=$key value='$value'")
                    summary.append("query[$key]=$value; ")
                    value?.takeIf { it.isNotEmpty() && it != "null" }
                }
            }
        }.onFailure {
            Log.d(PROFILE_TAG, "avatar provider query failed uri=$uri: ${it.message}")
            summary.append("query=ERR(${it.message}); ")
        }.getOrNull()
        if (!fromCursor.isNullOrBlank()) {
            Log.i(PROFILE_TAG, "avatar provider summary uri=$uri $summary")
            return fromCursor
        }
    }

    Log.i(PROFILE_TAG, "avatar provider summary value=null $summary")
    Log.d(PROFILE_TAG, "avatar provider value not found")
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

private fun logProviderDiscovery(context: Context) {
    val pm = context.packageManager
    val auth = PERSONAL_INFO_URI.authority.orEmpty()
    Log.d(PROFILE_TAG, "uri=${PERSONAL_INFO_URI}")
    Log.d(PROFILE_TAG, "authority=$auth exists=${pm.resolveContentProvider(auth, 0) != null}")
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

/**
 * 个人中心头像只渲染接口返回的远程图片；为空或失败时保留父容器灰底，禁止本地头像兜底。
 */
@Composable
private fun AvatarImage(
    model: String?,
    imageLoader: ImageLoader,
    modifier: Modifier
) {
    if (model == null) {
        Box(modifier = modifier)
        return
    }
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(model)
            .crossfade(true)
            .build(),
        imageLoader = imageLoader,
        contentDescription = "Avatar",
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}

class MainActivity : ComponentActivity() {
    // 当前跳转目标；singleTask 复用实例时由 onNewIntent 更新，驱动 Compose 切到对应子页。
    private val startTargetState = androidx.compose.runtime.mutableStateOf(StartTarget())
    private var restoreDpadFocus: ((force: Boolean) -> Unit)? = null
    private var touchMayHaveClearedDpadFocus = false
    private var lastTouchFocusRestoreAt = 0L

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == android.view.MotionEvent.ACTION_UP ||
            event.actionMasked == android.view.MotionEvent.ACTION_CANCEL
        ) {
            touchMayHaveClearedDpadFocus = true
            lastTouchFocusRestoreAt = SystemClock.uptimeMillis()
            window.decorView.postDelayed({
                // 触摸结束后恢复一次焦点；如果用户快速连续触摸，只响应最后一次。
                if (SystemClock.uptimeMillis() - lastTouchFocusRestoreAt >= 70L) {
                    restoreDpadFocus?.invoke(true)
                }
            }, 80L)
        }
        return handled
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val shouldRecoverFocus = event.action == android.view.KeyEvent.ACTION_DOWN &&
                when (event.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP,
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> true
                    else -> false
                }
        if (shouldRecoverFocus && touchMayHaveClearedDpadFocus) {
            touchMayHaveClearedDpadFocus = false
            restoreDpadFocus?.invoke(true)
        }
        val handled = super.dispatchKeyEvent(event)
        if (shouldRecoverFocus) {
            window.decorView.post { restoreDpadFocus?.invoke(!handled) }
        }
        return handled
    }

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
                    NavigationRailExample(
                        startTarget = startTargetState.value,
                        onDpadFocusRecoveryChanged = { restoreDpadFocus = it }
                    )
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
        // ZshdProvider 以 extras 的字段名作查询 key，故直接用字段名当 extra 名。
        val extras = Bundle().apply { putString(key, "") }
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

private enum class FocusArea {
    Sidebar,
    Content
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationRailExample(
    modifier: Modifier = Modifier,
    startTarget: StartTarget = StartTarget(),
    onDpadFocusRecoveryChanged: (((force: Boolean) -> Unit)?) -> Unit = {},
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
    var focusInSidebar by remember { mutableStateOf(false) }
    var lastFocusArea by remember { mutableStateOf(FocusArea.Sidebar) }
    var touchVersion by remember { mutableIntStateOf(0) }
    var restoredTouchVersion by remember { mutableIntStateOf(0) }

    LaunchedEffect(navItems.size) {
        navItemFocusRequesters.getOrNull(selectedDestination)?.requestFocus()
    }

    fun focusSelectedNavItem() {
        navItemFocusRequesters.getOrNull(selectedDestination)?.requestFocus()
        focusInSidebar = true
        focusInContent = false
        lastFocusArea = FocusArea.Sidebar
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

    fun recoverDpadFocusIfNeeded(force: Boolean = false) {
        val touchedSinceLastRecovery = touchVersion != restoredTouchVersion
        val noTrackedFocus = !focusInContent && !focusInSidebar
        if (!force && !touchedSinceLastRecovery && !noTrackedFocus) return
        restoredTouchVersion = touchVersion
        when (lastFocusArea) {
            FocusArea.Content -> enterContent()
            FocusArea.Sidebar -> focusSelectedNavItem()
        }
    }

    val latestDpadFocusRecovery by rememberUpdatedState(
        newValue = { force: Boolean -> recoverDpadFocusIfNeeded(force) }
    )
    DisposableEffect(Unit) {
        onDpadFocusRecoveryChanged { force -> latestDpadFocusRecovery(force) }
        onDispose { onDpadFocusRecoveryChanged(null) }
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
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        if (event.changes.any { it.changedToUpIgnoreConsumed() }) {
                            touchVersion++
                        }
                    }
                }
            }
    ) {
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
                                            focusInSidebar = true
                                            focusInContent = false
                                            lastFocusArea = FocusArea.Sidebar
                                        } else if (selectedDestination == index) {
                                            focusInSidebar = false
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
                        .onFocusChanged {
                            focusInContent = it.hasFocus
                            if (it.hasFocus) {
                                focusInSidebar = false
                                lastFocusArea = FocusArea.Content
                            }
                        }
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
    var avatarModel by rememberSaveable { mutableStateOf<String?>(null) }
    var accountText by rememberSaveable { mutableStateOf("未获取账号") }
    var profileVersion by remember { mutableStateOf(0) }
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
        val providerAvatar = queryPersonalInfoAvatarWithDebug(appContext)
        val providerNickname = queryPersonalInfoValue(appContext, "nickname")
        val providerAccount = queryAccountFromPersonalInfo(appContext)
        val backendAccountInfo = fetchAccountProfileFromBackend().getOrNull()?.let {
            UnifiedAccountInfo(
                nickname = it.nickname,
                account = it.account,
                avatarPath = it.avatarPath
            )
        }
        val mergedAccountInfo = mergeUnifiedAccountInfo(accountInfo, backendAccountInfo)
        val resolvedNickname = mergedAccountInfo?.nickname
            ?: providerNickname
        if (!resolvedNickname.isNullOrBlank()) {
            nickname = resolvedNickname
        }

        val resolvedAvatar = resolveRemoteAvatarModel(backendAccountInfo?.avatarPath)
            ?: resolveRemoteAvatarModel(accountInfo?.avatarPath)
            ?: resolveRemoteAvatarModel(providerAvatar)
        Log.d(
            PROFILE_TAG,
            "resolved remote avatar='$resolvedAvatar' backend='${backendAccountInfo?.avatarPath}' " +
                "account='${accountInfo?.avatarPath}' provider='$providerAvatar'"
        )
        avatarModel = resolvedAvatar

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
