package com.android.tv.settings

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.android.tv.settings.ui.theme.设置Theme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

private const val SCREEN_SAVER_METHOD_DEV_QUERY = "DEV_QUERY"
private const val SCREEN_SAVER_METHOD_DEV_OPT = "DEV_OPT"
private val SCREEN_SAVER_DEVICE_INFO_URI: Uri = Uri.parse("content://com.android.zshd.deviceinfo/device_info")
private val SCREEN_SAVER_STATUS_URI: Uri = Uri.parse("content://com.android.zshd.deviceinfo/screenSaverStatus")
private const val SCREEN_SAVER_PREFS = "screen_saver_prefs"
private const val SCREEN_SAVER_IMAGE_URI_PREF_KEY = "screen_saver_image_uri"
private const val SCREEN_SAVER_IMAGE_NAME_PREF_KEY = "screen_saver_image_name"
private const val SCREEN_SAVER_IMAGE_PATH_PREF_KEY = "screen_saver_image_path"
private const val SCREEN_SAVER_IMAGE_SECURE_URI_KEY = "screensaver_image_uri"
private const val SCREEN_SAVER_IMAGE_SECURE_PATH_KEY = "screensaver_image_path"
private const val SCREEN_SAVER_IMAGE_COMPONENT =
    "com.android.speaker.settings/com.android.tv.settings.ImageScreenSaverDreamService"
private val SCREEN_SAVER_IMAGE_UPDATE_KEYS = listOf(
    "screenSaverImage",
    "screenSaverImageUri",
    "screenSaverImagePath",
    "screenSaverPic",
    "screenSaverPicPath",
    "screenSaverPicture",
    "screenSaverPicturePath",
    "dreamImage",
    "dreamImageUri",
    "dreamImagePath"
)
private val SCREEN_SAVER_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "bmp", "webp")

private data class ScreenSaverFileEntry(
    val file: File,
    val isDirectory: Boolean,
)

private fun screenSaverNormalizeValue(value: Any?): String? {
    val normalized = value?.toString()?.trim()
    if (normalized.isNullOrEmpty()) return null
    if (normalized.equals("null", ignoreCase = true)) return null
    return normalized
}

private fun isScreenSaverBundleSuccess(bundle: Bundle?): Boolean {
    if (bundle == null) return false
    if (bundle.getBoolean("success", false)) return true
    if (bundle.getBoolean("result", false)) return true
    if (bundle.getInt("code", -1) == 0) return true
    return false
}

private fun queryScreenSaverValue(context: Context, key: String, defaultValue: String): String {
    val resolver = context.contentResolver
    return runCatching {
        val extras = Bundle().apply {
            putString("key", key)
            putString(key, "")
        }
        val result = resolver.call(SCREEN_SAVER_DEVICE_INFO_URI, SCREEN_SAVER_METHOD_DEV_QUERY, null, extras)
        screenSaverNormalizeValue(result?.getString(key))
            ?: screenSaverNormalizeValue(result?.getString("value"))
            ?: screenSaverNormalizeValue(result?.getString("result"))
            ?: defaultValue
    }.getOrDefault(defaultValue)
}

private fun updateScreenSaverValue(context: Context, key: String, value: String): Boolean {
    val resolver = context.contentResolver
    return runCatching {
        val extras = Bundle().apply {
            putString("key", key)
            putString("value", value)
            putString(key, value)
        }
        val result = resolver.call(SCREEN_SAVER_DEVICE_INFO_URI, SCREEN_SAVER_METHOD_DEV_OPT, null, extras)
        isScreenSaverBundleSuccess(result)
            || screenSaverNormalizeValue(result?.getString(key)) == value
            || screenSaverNormalizeValue(result?.getString("value")) == value
            || screenSaverNormalizeValue(result?.getString("result"))?.equals("true", ignoreCase = true) == true
    }.getOrDefault(false)
}

private data class ScreenSaverTimeOption(
    val label: String,
    val millis: Int,
)

private val SCREEN_SAVER_TIME_OPTIONS = listOf(
    ScreenSaverTimeOption("永不", -1),
    ScreenSaverTimeOption("1分钟", 60_000),
    ScreenSaverTimeOption("10分钟", 600_000),
    ScreenSaverTimeOption("20分钟", 1_200_000),
    ScreenSaverTimeOption("30分钟", 1_800_000),
)

private val SCREEN_SAVER_TIMEOUT_QUERY_KEYS = listOf(
    "screenSaverTime",
    "screenSaverTimeout",
    "dreamTime",
    "screen_off_timeout",
    "sleep_timeout",
)

private val SCREEN_SAVER_TIMEOUT_UPDATE_KEYS = listOf(
    "screenSaverTime",
    "screenSaverTimeout",
    "dreamTime",
    "screen_off_timeout",
)

private const val SCREEN_SAVER_ENABLED_KEY = "screensaver_enabled"
private const val SCREEN_SAVER_ACTIVATE_ON_SLEEP_KEY = "screensaver_activate_on_sleep"

private fun parseScreenSaverTimeout(rawValue: String?): Int? {
    return rawValue?.trim()?.toIntOrNull()
}

private fun imageReadPermission(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
}

private fun hasImageReadPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, imageReadPermission()) == PackageManager.PERMISSION_GRANTED
}

private fun screenSaverPrefs(context: Context) =
    context.getSharedPreferences(SCREEN_SAVER_PREFS, Context.MODE_PRIVATE)

private fun getSelectedScreenSaverImageUri(context: Context): String {
    return screenSaverPrefs(context).getString(SCREEN_SAVER_IMAGE_URI_PREF_KEY, "").orEmpty()
}

private fun getSelectedScreenSaverImageName(context: Context): String {
    return screenSaverPrefs(context).getString(SCREEN_SAVER_IMAGE_NAME_PREF_KEY, "").orEmpty()
}

private fun friendlyStorageName(file: File): String {
    val internalStoragePath = runCatching { Environment.getExternalStorageDirectory().canonicalPath }.getOrNull()
    val filePath = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
    return when {
        internalStoragePath != null && filePath == internalStoragePath -> "内部存储"
        file.name.isBlank() -> file.absolutePath
        else -> file.name
    }
}

private fun isImageFile(file: File): Boolean {
    if (!file.isFile) return false
    val extension = file.extension.lowercase()
    return extension in SCREEN_SAVER_IMAGE_EXTENSIONS
}

private fun screenSaverRootDirectories(): List<File> {
    val roots = mutableListOf<File>()
    val storageRoot = File("/storage")
    storageRoot.listFiles()
        ?.filter { it.isDirectory && it.canRead() && it.name != "self" && !it.name.startsWith(".") }
        ?.let { roots += it }

    val externalStorage = Environment.getExternalStorageDirectory()
    if (externalStorage != null && externalStorage.exists() && externalStorage.canRead()) {
        roots += externalStorage
    }

    return roots
        .distinctBy { file -> runCatching { file.canonicalPath }.getOrElse { file.absolutePath } }
        .sortedBy { friendlyStorageName(it) }
}

private fun loadScreenSaverBrowserEntries(currentPath: String?): Pair<File?, List<ScreenSaverFileEntry>> {
    if (currentPath.isNullOrEmpty()) {
        val roots = screenSaverRootDirectories()
        return null to roots.map { ScreenSaverFileEntry(file = it, isDirectory = true) }
    }

    val currentDirectory = File(currentPath)
    if (!currentDirectory.exists() || !currentDirectory.isDirectory || !currentDirectory.canRead()) {
        return null to emptyList()
    }

    val entries = currentDirectory.listFiles()
        ?.filter { it.canRead() && !it.name.startsWith(".") && (it.isDirectory || isImageFile(it)) }
        ?.sortedWith(
            compareByDescending<File> { it.isDirectory }
                .thenBy { it.name.lowercase() }
        )
        ?.map { ScreenSaverFileEntry(file = it, isDirectory = it.isDirectory) }
        .orEmpty()
    return currentDirectory to entries
}

private fun persistSelectedScreenSaverImage(
    context: Context,
    file: File,
): Boolean {
    val fileUri = Uri.fromFile(file).toString()
    val filePath = file.absolutePath

    screenSaverPrefs(context).edit()
        .putString(SCREEN_SAVER_IMAGE_URI_PREF_KEY, fileUri)
        .putString(SCREEN_SAVER_IMAGE_NAME_PREF_KEY, file.name)
        .putString(SCREEN_SAVER_IMAGE_PATH_PREF_KEY, filePath)
        .apply()

    var updated = false
    SCREEN_SAVER_IMAGE_UPDATE_KEYS.forEach { key ->
        if (updateScreenSaverValue(context, key, filePath)) {
            updated = true
        }
        if (updateScreenSaverValue(context, key, fileUri)) {
            updated = true
        }
    }

    val secureUpdated = runCatching {
        Settings.Secure.putString(context.contentResolver, SCREEN_SAVER_IMAGE_SECURE_URI_KEY, fileUri)
        Settings.Secure.putString(context.contentResolver, SCREEN_SAVER_IMAGE_SECURE_PATH_KEY, filePath)
        Settings.Secure.putString(context.contentResolver, "screensaver_components", SCREEN_SAVER_IMAGE_COMPONENT)
        Settings.Secure.putString(context.contentResolver, "screensaver_default_component", SCREEN_SAVER_IMAGE_COMPONENT)
        Settings.Secure.putInt(context.contentResolver, SCREEN_SAVER_ENABLED_KEY, 1)
        Settings.Secure.putInt(context.contentResolver, SCREEN_SAVER_ACTIVATE_ON_SLEEP_KEY, 1)
        true
    }.getOrDefault(false)

    return updated || secureUpdated
}

private fun queryScreenSaverEnabled(context: Context): Boolean {
    listOf("screenSaverEnabled", SCREEN_SAVER_ENABLED_KEY).forEach { key ->
        val value = queryScreenSaverValue(context, key, "")
        when (value.lowercase()) {
            "1", "true" -> return true
            "0", "false" -> return false
        }
    }
    return runCatching {
        Settings.Secure.getInt(context.contentResolver, SCREEN_SAVER_ENABLED_KEY, 1) == 1
    }.getOrDefault(true)
}

private fun queryScreenSaverTimeoutMillis(context: Context): Int? {
    SCREEN_SAVER_TIMEOUT_QUERY_KEYS.forEach { key ->
        parseScreenSaverTimeout(queryScreenSaverValue(context, key, ""))?.let { return it }
    }
    return runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
    }.getOrNull()
}

private fun resolveScreenSaverTimeLabel(
    timeoutMillis: Int?,
    enabled: Boolean,
): String {
    if (!enabled || timeoutMillis == null || timeoutMillis < 0) {
        return SCREEN_SAVER_TIME_OPTIONS.first().label
    }
    return SCREEN_SAVER_TIME_OPTIONS
        .drop(1)
        .minByOrNull { abs(it.millis - timeoutMillis) }
        ?.label
        ?: SCREEN_SAVER_TIME_OPTIONS.last().label
}

private fun updateScreenSaverTimeBestEffort(
    context: Context,
    selectedLabel: String,
): Boolean {
    val option = SCREEN_SAVER_TIME_OPTIONS.firstOrNull { it.label == selectedLabel } ?: return false
    var updated = false

    SCREEN_SAVER_TIMEOUT_UPDATE_KEYS.forEach { key ->
        if (updateScreenSaverValue(context, key, option.millis.toString())) {
            updated = true
        }
    }
    listOf("screenSaverEnabled", SCREEN_SAVER_ENABLED_KEY).forEach { key ->
        if (updateScreenSaverValue(context, key, if (option.millis >= 0) "1" else "0")) {
            updated = true
        }
    }

    val canWriteSystemSettings = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context)
    if (canWriteSystemSettings) {
        val systemUpdated = runCatching {
            Settings.Secure.putInt(
                context.contentResolver,
                SCREEN_SAVER_ENABLED_KEY,
                if (option.millis >= 0) 1 else 0,
            )
            Settings.Secure.putInt(
                context.contentResolver,
                SCREEN_SAVER_ACTIVATE_ON_SLEEP_KEY,
                if (option.millis >= 0) 1 else 0,
            )
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                option.millis,
            )
            true
        }.getOrDefault(false)
        updated = updated || systemUpdated
    }

    return updated
}

@Composable
fun ScreenSaverSettingsScreen(modifier: Modifier = Modifier) {
    val rawContext = LocalContext.current
    val appContext = rawContext.applicationContext
    val options = remember { SCREEN_SAVER_TIME_OPTIONS.map(ScreenSaverTimeOption::label) }
    var selectedTime by remember { mutableStateOf(options.first()) }
    var showDialog by remember { mutableStateOf(false) }
    var showImagePickerDialog by remember { mutableStateOf(false) }
    var screenSaverActive by remember { mutableStateOf(false) }
    var selectedImageName by remember { mutableStateOf("") }
    var currentDirectory by remember { mutableStateOf<File?>(null) }
    var currentDirectoryPath by remember { mutableStateOf<String?>(null) }
    var browserEntries by remember { mutableStateOf(emptyList<ScreenSaverFileEntry>()) }
    var browserLoading by remember { mutableStateOf(false) }
    var refreshVersion by remember { mutableStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showImagePickerDialog = true
        } else {
            Toast.makeText(rawContext, "没有图片读取权限", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(appContext) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                refreshVersion++
            }
        }
        runCatching { appContext.contentResolver.registerContentObserver(SCREEN_SAVER_STATUS_URI, true, observer) }
        runCatching { appContext.contentResolver.registerContentObserver(SCREEN_SAVER_DEVICE_INFO_URI, true, observer) }
        onDispose {
            runCatching { appContext.contentResolver.unregisterContentObserver(observer) }
        }
    }

    LaunchedEffect(appContext, refreshVersion) {
        screenSaverActive = queryScreenSaverValue(appContext, "screenSaverStatus", "0") == "1"
        selectedTime = resolveScreenSaverTimeLabel(
            timeoutMillis = queryScreenSaverTimeoutMillis(appContext),
            enabled = queryScreenSaverEnabled(appContext),
        )
        selectedImageName = getSelectedScreenSaverImageName(appContext).ifBlank { "未选择" }
    }

    LaunchedEffect(showImagePickerDialog, currentDirectoryPath) {
        if (!showImagePickerDialog) return@LaunchedEffect
        browserLoading = true
        val (directory, entries) = withContext(Dispatchers.IO) {
            loadScreenSaverBrowserEntries(currentDirectoryPath)
        }
        currentDirectory = directory
        browserEntries = entries
        browserLoading = false
    }

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
                    .entryFocus()
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
                    .clickable {
                        if (hasImageReadPermission(rawContext)) {
                            currentDirectoryPath = null
                            showImagePickerDialog = true
                        } else {
                            permissionLauncher.launch(imageReadPermission())
                        }
                    }
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
                Text(
                    selectedImageName,
                    fontSize = 15.sp,
                    color = Color(0xFF8B909A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(320.dp)
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.arrow_right),
                    contentDescription = "选择屏保图片",
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
                    "当前屏保状态",
                    fontSize = 19.sp,
                    color = Color(0xFF131519),
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (screenSaverActive) "屏保中" else "未进入",
                    fontSize = 17.sp,
                    color = Color(0xFF8B909A)
                )
            }
        }
    }

    if (showDialog) {
        ScreenSaverTimeDialog(
            options = options,
            selectedOption = selectedTime,
            onOptionSelected = {
                if (updateScreenSaverTimeBestEffort(appContext, it)) {
                    selectedTime = it
                } else {
                    Toast.makeText(rawContext, "屏保时间保存失败", Toast.LENGTH_SHORT).show()
                }
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }

    if (showImagePickerDialog) {
        ScreenSaverImagePickerDialog(
            context = rawContext,
            currentDirectory = currentDirectory,
            entries = browserEntries,
            loading = browserLoading,
            onDismiss = { showImagePickerDialog = false },
            onNavigateUp = {
                currentDirectoryPath = currentDirectory?.parentFile?.absolutePath
            },
            onEntryClick = { entry ->
                if (entry.isDirectory) {
                    currentDirectoryPath = entry.file.absolutePath
                } else if (persistSelectedScreenSaverImage(appContext, entry.file)) {
                    selectedImageName = entry.file.name
                    showImagePickerDialog = false
                    Toast.makeText(rawContext, "屏保图片已更新", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(rawContext, "屏保图片保存失败", Toast.LENGTH_SHORT).show()
                }
            }
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

@Composable
private fun ScreenSaverImagePickerDialog(
    context: Context,
    currentDirectory: File?,
    entries: List<ScreenSaverFileEntry>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onNavigateUp: () -> Unit,
    onEntryClick: (ScreenSaverFileEntry) -> Unit,
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
                    .fillMaxWidth(0.82f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "选择屏保图片",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 8.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                    val locationLabel = currentDirectory?.absolutePath ?: "存储设备"
                    Text(
                        text = locationLabel,
                        fontSize = 13.sp,
                        color = Color(0xFF8B909A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                    )
                    Divider()

                    if (currentDirectory != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateUp() }
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("返回上一级", fontSize = 16.sp, color = Color(0xFF4356B6))
                        }
                        Divider(modifier = Modifier.padding(horizontal = 24.dp))
                    }

                    if (loading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("正在加载图片...", fontSize = 16.sp, color = Color(0xFF8B909A))
                        }
                    } else if (entries.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("当前目录没有可用图片", fontSize = 16.sp, color = Color(0xFF8B909A))
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            entries.forEachIndexed { index, entry ->
                                ScreenSaverFileRow(
                                    context = context,
                                    entry = entry,
                                    onClick = { onEntryClick(entry) }
                                )
                                if (index != entries.lastIndex) {
                                    Divider(modifier = Modifier.padding(horizontal = 24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenSaverFileRow(
    context: Context,
    entry: ScreenSaverFileEntry,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (entry.isDirectory) {
            Icon(
                painter = painterResource(R.drawable.arrow_right),
                contentDescription = "目录",
                tint = Color(0xFF4356B6),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friendlyStorageName(entry.file),
                    fontSize = 16.sp,
                    color = Color(0xFF131519),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.file.absolutePath,
                    fontSize = 12.sp,
                    color = Color(0xFF8B909A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            AsyncImage(
                model = entry.file,
                contentDescription = entry.file.name,
                modifier = Modifier
                    .size(72.dp)
                    .background(Color(0xFFF3F4F6), RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.file.name,
                    fontSize = 16.sp,
                    color = Color(0xFF131519),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = Formatter.formatFileSize(context, entry.file.length()),
                    fontSize = 12.sp,
                    color = Color(0xFF8B909A)
                )
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
