package com.android.tv.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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

private val LOGOUT_CLEAR_PACKAGES = listOf(
    "com.chinatelecom.accloudbox",
    "cn.dlife.smartcloud.launcher",
    "com.chinatelecom.smartvoicebox",
    "com.ximalayaos.pad",
    "com.netease.cloudmusic.iot",
    "com.tatv.android.TMC",
    "com.yueme.itv",
    "com.to21cn.yjjk",
    "com.telecom.video",
    "com.dlife.aiassistant",
    "com.teleagi.xxc",
    "com.gitv.tv.gvp.qgdx",
    "cn.dlife.smartcloud.album",
    "com.ihome.android.ATmarket",
    "com.ctcc.motiondetect",
    "ctc.android.smart.terminal.voicectrl",
    "ctc.android.smart.terminal.voicekeepalive",
    "ctc.android.smart.terminal.skill",
    "com.ztestb.dlna",
    "com.bestv.chinanet",
    "com.zjkd..smartdrive",
    "com.zjkd.smartdrive",
    "com.bestv.ott.baseservices.zp.zjdx_ty"
)

private const val LOGOUT_TAG = "LogoutCleanup"
private const val ACTION_LOG_OUT = "com.telecom.smartcloud.action.log_out"
private const val ACTION_LOG_OUT_RESULT = "com.telecom.smartcloud.action.result"
private const val KEY_LOG_OUT_RESULT = "log_out_result"
private const val ACTION_UNBIND = "com.ctcc.iotsdk.unbind_broadcast"
private const val METHOD_DEV_QUERY = "DEV_QUERY"
private const val METHOD_DEV_OPT = "DEV_OPT"
private const val PROFILE_TAG = "PersonalInfo"
private val PERSONAL_INFO_URI: Uri = Uri.parse("content://com.android.ctcc.deviceinfo/personalinfo")

private fun buildPersonalInfoUriCandidates(context: Context): List<Uri> {
    return listOf(PERSONAL_INFO_URI)
}

private fun currentUserIdCompat(): Int {
    return runCatching {
        val method = Class.forName("android.os.UserHandle").getDeclaredMethod("myUserId")
        method.invoke(null) as Int
    }.getOrElse {
        // Fallback for SDKs where myUserId is hidden from compile-time stubs.
        0
    }
}

private fun clearPackageCacheBestEffort(context: Context, packageName: String): Boolean {
    val pm = context.packageManager
    val clearAsUserSucceeded = runCatching {
        val method = pm.javaClass.methods.firstOrNull { m ->
            m.name == "deleteApplicationCacheFilesAsUser" &&
                m.parameterTypes.size == 3 &&
                m.parameterTypes[0] == String::class.java &&
                m.parameterTypes[1] == Int::class.javaPrimitiveType
        } ?: return@runCatching false
        method.invoke(pm, packageName, currentUserIdCompat(), null)
        true
    }.getOrDefault(false)
    if (clearAsUserSucceeded) return true

    return runCatching {
        val method = pm.javaClass.methods.firstOrNull { m ->
            m.name == "deleteApplicationCacheFiles" &&
                m.parameterTypes.size == 2 &&
                m.parameterTypes[0] == String::class.java
        } ?: return@runCatching false
        method.invoke(pm, packageName, null)
        true
    }.getOrDefault(false)
}

private data class CacheClearResult(
    val packageName: String,
    val success: Boolean
)

private fun clearLogoutAppCaches(context: Context): List<CacheClearResult> {
    return LOGOUT_CLEAR_PACKAGES.map { pkg ->
        CacheClearResult(pkg, clearPackageCacheBestEffort(context, pkg))
    }
}

private fun sendLogoutBroadcasts(context: Context) {
    // Generic broadcasts
    context.sendBroadcast(Intent(ACTION_LOG_OUT))
    context.sendBroadcast(Intent(ACTION_UNBIND))
    // Explicit broadcasts for key apps to improve delivery on some builds.
    listOf("com.chinatelecom.accloudbox", "cn.dlife.smartcloud.launcher").forEach { pkg ->
        runCatching { context.sendBroadcast(Intent(ACTION_LOG_OUT).setPackage(pkg)) }
        runCatching { context.sendBroadcast(Intent(ACTION_UNBIND).setPackage(pkg)) }
    }
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
        val endpoint = BuildConfig.ACCOUNT_PROFILE_QUERY_URL.trim()
        if (endpoint.isBlank()) throw IllegalStateException("未配置账号查询接口地址")
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
    avatarAssets.indexOfFirst { it.equals(normalized, ignoreCase = true) }.takeIf { it >= 0 }?.let { return it }
    val byName = normalized.substringAfterLast('/').substringBeforeLast('.')
    avatarAssets.indexOfFirst { asset -> asset.contains("/$byName.", ignoreCase = true) }
        .takeIf { it >= 0 }?.let { return it }
    return -1
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
            if (k.contains("account", true) || k.contains("phone", true) || k.contains("mobile", true) || k.contains("user", true)) {
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
                    if (col.contains("account", true) || col.contains("phone", true) || col.contains("mobile", true) || col.contains("user", true)) {
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
        // Preferred path from CTCC spec: provider DEV_OPT
        if (updatePersonalInfoByProvider(context, nickname, avatarAssetUrl)) {
            return@runCatching Unit
        }
        throw IllegalStateException("Provider 接口不可用，请切换到支持 com.android.ctcc.deviceinfo 的设备")
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val authority = "com.android.ctcc.deviceinfo"

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
                NavigationRailExample()
            }
        }
    }
}


sealed class Destinations(val route: String) {
    object Home : Destinations("home")
    object Detail : Destinations("detail")
    object WifiScreen : Destinations("wifi_screen")
    object AddWifiScreen : Destinations("add_wifi_screen")
    object WifiConnectScreen : Destinations("wifi_connect_screen/{ssid}") {
        fun createRoute(ssid: String) = "wifi_connect_screen/$ssid"
    }
    object WifiDetailScreen : Destinations("wifi_detail_screen/{ssid}") {
        fun createRoute(ssid: String) = "wifi_detail_screen/$ssid"
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationRailExample(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    val names = stringArrayResource(R.array.docks)
    //val icons = integerArrayResource(R.array.dockicons);
    val startDestination = 8;
    var selectedDestination by rememberSaveable { mutableIntStateOf( startDestination) }

    val navRailWidth = 180.dp
    //val tintcolor = Color(0xFF4577FF)
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            //containerColor = colorResource(R.color.topbar),
            modifier = Modifier.fillMaxSize(),
            topBar = {
                CenterAlignedTopAppBar(
                    modifier = Modifier
                        .padding(0.dp)
                        .background(color = colorResource(R.color.topbar)),
                    title = { Text("设置", fontSize = 17.sp) },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (navController.previousBackStackEntry != null) {
                                Log.d("navback", navController.previousBackStackEntry!!.id)
                                navController.popBackStack()
                            } else {
                                // 已经是第一个页面了
                            }
                            /* 在这里处理返回事件 */
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.back),
                                contentDescription = "返回"
                            )
                        }
                    }
                )
            }
        ) { contentPadding ->
            Row(Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.fillMaxHeight(),
                    //color = NavigationRailDefaults.ContainerColor // 保持和 NavigationRail 一样的背景色
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(top = contentPadding.calculateTopPadding())
                            .padding(vertical = 4.dp) // 给 item 上下加一点边距
                    ) {
                        names.forEachIndexed { index, destination ->
                            val isSelected = selectedDestination == index
                            //val contentColor = if (isSelected) tintcolor else Color.Unspecified

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .width(navRailWidth)
                                    .height(53.dp)
                                    .clickable(
                                        onClick = { selectedDestination = index },
                                        indication = null, // 1. 禁用默认的点击效果（波纹）
                                        interactionSource = remember { MutableInteractionSource() }
                                    )
                                    .background(
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 16.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.account),
                                    contentDescription = "",
                                    //tint = contentColor // 2. 手动控制图标颜色
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = destination,
                                    //color = contentColor // 3. 手动控制文字颜色
                                )
                            }
                        }
                    }
                }
                Card (
                    colors = CardDefaults.cardColors(containerColor = colorResource(R.color.cardcolor)),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        //.fillMaxSize(1f)
                        .clip(shape = RoundedCornerShape(46.dp))
                        //.background(colorResource(R.color.black) ) // 内容区域背景色
                        .padding(top = contentPadding.calculateTopPadding())
                ) {
                    when (selectedDestination) {
                        0 -> PersonalCenterScreen()
                        1 -> {
                            NavHost(navController = navController, startDestination = Destinations.WifiScreen.route) {
                                composable(Destinations.WifiScreen.route) {
                                    WifiManagerScreen(navController = navController,)
                                }
                                composable(Destinations.AddWifiScreen.route) {
                                    AddWifiNetworkScreen(onBack = { navController.popBackStack() })
                                }
                                composable(
                                    Destinations.WifiConnectScreen.route,
                                    arguments = listOf(navArgument("ssid") { type = NavType.StringType })
                                ) {
                                    val ssid = it.arguments?.getString("ssid") ?: ""
                                    WifiConnectScreen(ssid = ssid, onBack = { navController.popBackStack() })
                                }
                                composable(
                                    Destinations.WifiDetailScreen.route,
                                    arguments = listOf(navArgument("ssid") { type = NavType.StringType })
                                ) {
                                    val ssid = it.arguments?.getString("ssid") ?: ""
                                    WifiDetailScreen (ssid = ssid, onBack = { navController.popBackStack() })
                                }
                            }
                        }
                        2->{
                            BlueToothScreen(modifier,navController)
                        }
                        3->{
                            SoundAndDisplayScreen()
                        }
                        4->{
                            ScreenSaverSettingsScreen(modifier =modifier)
                        }
                        5->{
                            HdmiSettingsScreen()
                        }
                        6->{
                            RebootScreen {  }
                        }
                        7->{
                            LabScreen()
                        }
                        8->{
                            PrivacyScreen()
                        }
                        9->{
                            StorageSettingsScreen()
                        }
                        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = "${names[selectedDestination]} 页面")
                        }
                    }
                }
            }
        }

    }
}

@Composable
fun PersonalCenterScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    var logoutInProgress by remember { mutableStateOf(false) }
    var nickname by rememberSaveable { mutableStateOf("小翼8899") }
    var avatarIndex by rememberSaveable { mutableIntStateOf(0) }
    var showEditPage by rememberSaveable { mutableStateOf(false) }
    var profileSaving by remember { mutableStateOf(false) }
    var accountText by rememberSaveable { mutableStateOf("未获取账号") }
    val avatarAssets = listOf(
        "file:///android_asset/avatars/avatar_01.svg",
        "file:///android_asset/avatars/avatar_02.svg",
        "file:///android_asset/avatars/avatar_03.svg",
        "file:///android_asset/avatars/avatar_04.svg",
        "file:///android_asset/avatars/avatar_05.svg",
        "file:///android_asset/avatars/avatar_06.svg",
        "file:///android_asset/avatars/avatar_07.svg",
        "file:///android_asset/avatars/avatar_08.svg",
        "file:///android_asset/avatars/avatar_09.svg",
        "file:///android_asset/avatars/avatar_10.svg",
        "file:///android_asset/avatars/avatar_11.svg"
    )
    val svgLoader = rememberSvgLoader()

    LaunchedEffect(Unit) {
        logProviderDiscovery(appContext)

        val providerNickname = queryPersonalInfoValue(appContext, "nickname")
        if (!providerNickname.isNullOrBlank()) {
            nickname = providerNickname
        }
        val providerAvatar = queryPersonalInfoValue(appContext, "avatarpath")
        if (!providerAvatar.isNullOrBlank()) {
            val idx = findAvatarIndex(avatarAssets, providerAvatar)
            if (idx >= 0) {
                avatarIndex = idx
            }
        }

        val fetched = queryAccountFromPersonalInfo(appContext)
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

    DisposableEffect(appContext, logoutInProgress) {
        if (!logoutInProgress) {
            onDispose { }
        } else {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action != ACTION_LOG_OUT_RESULT) return
                    val success = intent.getBooleanExtra(KEY_LOG_OUT_RESULT, false)
                    logoutInProgress = false
                    if (success) {
                        scope.launch(Dispatchers.IO) {
                            val results = clearLogoutAppCaches(appContext)
                            results.forEach {
                                Log.i(LOGOUT_TAG, "clearCache package=${it.packageName} success=${it.success}")
                            }
                            val ok = results.count { it.success }
                            Log.i(LOGOUT_TAG, "clearCache summary success=$ok total=${results.size}")
                        }
                    }
                    Toast.makeText(
                        appContext,
                        if (success) "账号已被解绑" else "退出登录失败，请稍后重试",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(ACTION_LOG_OUT_RESULT),
                ContextCompat.RECEIVER_EXPORTED
            )
            onDispose { appContext.unregisterReceiver(receiver) }
        }
    }

    LaunchedEffect(logoutInProgress) {
        if (logoutInProgress) {
            delay(10_000)
            if (logoutInProgress) {
                logoutInProgress = false
                Toast.makeText(appContext, "退出登录失败，请稍后重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFECECED))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(29.dp))
                                .background(Color(0xFFD8D8DC)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(avatarAssets.getOrElse(avatarIndex) { avatarAssets.first() })
                                    .build(),
                                imageLoader = svgLoader,
                                contentDescription = "Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(nickname, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color(0xFF23252B))
                        Spacer(Modifier.weight(1f))
                        Text(
                            "修改",
                            color = Color(0xFF8C9097),
                            fontSize = 15.sp,
                            modifier = Modifier.clickable { showEditPage = true }
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            painter = painterResource(R.drawable.arrow_right),
                            contentDescription = "修改",
                            tint = Color(0xFFB5B8BE),
                            modifier = Modifier.clickable { showEditPage = true }
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("小翼管家账号", fontSize = 17.sp, color = Color(0xFF23252B), fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(10.dp))
                        Text(accountText, color = Color(0xFF8C9097), fontSize = 17.sp)
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(
                            onClick = {
                                if (logoutInProgress) return@OutlinedButton
                                logoutInProgress = true
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFFECECED))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .padding(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 项目暂未接入二维码资源，先保留占位区
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFECF2FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("二维码", color = Color(0xFF3B63C9), fontSize = 28.sp, fontWeight = FontWeight.Bold)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.back),
                contentDescription = "返回",
                modifier = Modifier.clickable { onBack() }
            )
            Spacer(Modifier.weight(1f))
            Text("修改账号信息", fontSize = 44.sp / 2, fontWeight = FontWeight.Bold, color = Color(0xFF1E2025))
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(36.dp))
        }
        Spacer(Modifier.height(14.dp))
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFECECED)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp)) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(75.dp))
                        .background(Color(0xFFD8D8DC)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(avatarAssets.getOrElse(draftAvatarIndex) { avatarAssets.first() })
                            .build(),
                        imageLoader = svgLoader,
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { showAvatarDialog = true },
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
                        Text("昵称", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color(0xFF20232A))
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
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.width(820.dp)
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
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (col in 0 until 4) {
                            val index = row * 4 + col
                            if (index < avatars.size) {
                                val avatar = avatars[index]
                                AvatarItem(
                                    avatarAsset = avatar,
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

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    SecondaryPillButton(text = "取消", onClick = onDismiss)
                    Spacer(Modifier.width(24.dp))
                    PrimaryPillButton(text = "确定", onClick = { onConfirm(localSelection) })
                }
            }
        }
    }
}

@Composable
private fun AvatarItem(
    avatarAsset: String,
    imageLoader: ImageLoader,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(118.dp)
            .clip(RoundedCornerShape(59.dp))
            .background(Color(0xFFE4E4E7))
            .border(if (selected) 3.dp else 0.dp, if (selected) Color(0xFF4B79FF) else Color.Transparent, RoundedCornerShape(59.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(avatarAsset)
                .build(),
            imageLoader = imageLoader,
            contentDescription = "Avatar",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
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
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = Color(0xFF23252A))
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
        Text(text, color = if (enabled) Color(0xFF4A79FF) else Color(0xFFB8C4E8), fontSize = 21.sp, fontWeight = FontWeight.Bold)
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

@Preview(showBackground = true)
@Composable
fun WifiManagerScreenPreview() {
    设置Theme {
        NavigationRailExample()
    }
}
