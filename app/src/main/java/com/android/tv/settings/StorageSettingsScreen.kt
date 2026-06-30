package com.android.tv.settings

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.Parcel
import android.os.UserHandle
import android.os.storage.StorageManager
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.tv.settings.ui.theme.设置Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class AppStorageItem(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val totalBytes: Long,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
)

private fun safeFsStats(): Pair<Long, Long> {
    // StatFs can crash in some preview/odd environments; keep this best-effort and safe.
    return runCatching {
        val path = Environment.getDataDirectory().absolutePath
        val stat = android.os.StatFs(path)
        stat.totalBytes to stat.availableBytes
    }.getOrElse { 0L to 0L }
}

private fun previewStorageItems(): List<AppStorageItem> {
    return listOf(
        AppStorageItem(
            packageName = "com.example.yunkankan",
            label = "云享看",
            icon = null,
            totalBytes = 356_240_000L,
            appBytes = 210_000_000L,
            dataBytes = 120_000_000L,
            cacheBytes = 26_240_000L,
        ),
        AppStorageItem(
            packageName = "com.qiyi.video",
            label = "爱奇艺",
            icon = null,
            totalBytes = 245_470_000L,
            appBytes = 180_000_000L,
            dataBytes = 50_000_000L,
            cacheBytes = 15_470_000L,
        ),
        AppStorageItem(
            packageName = "com.tencent.qqlive",
            label = "腾讯视频",
            icon = null,
            totalBytes = 145_060_000L,
            appBytes = 100_000_000L,
            dataBytes = 30_000_000L,
            cacheBytes = 15_060_000L,
        ),
        AppStorageItem(
            packageName = "com.hunantv.imgo.activity",
            label = "芒果TV",
            icon = null,
            totalBytes = 86_760_000L,
            appBytes = 60_000_000L,
            dataBytes = 20_000_000L,
            cacheBytes = 6_760_000L,
        ),
        AppStorageItem(
            packageName = "com.ctcc.study",
            label = "天翼学堂",
            icon = null,
            totalBytes = 53_520_000L,
            appBytes = 40_000_000L,
            dataBytes = 10_000_000L,
            cacheBytes = 3_520_000L,
        ),
    )
}

@Composable
fun StorageSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<AppStorageItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showClearCache by rememberSaveable { mutableStateOf(false) }
    var clearing by rememberSaveable { mutableStateOf(false) }
    var showClearCacheSuccessPopup by rememberSaveable { mutableStateOf(false) }
    var showNoClearableCachePopup by rememberSaveable { mutableStateOf(false) }
    var appInfoPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var showUninstallSuccessPopup by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (isPreview) {
            items = previewStorageItems()
            loading = false
        } else {
            loading = true
            items = withContext(Dispatchers.IO) { loadAppStorageItems(context) }
            loading = false
        }
    }

    LaunchedEffect(showClearCacheSuccessPopup) {
        if (showClearCacheSuccessPopup) {
            delay(1500)
            showClearCacheSuccessPopup = false
        }
    }

    LaunchedEffect(showNoClearableCachePopup) {
        if (showNoClearableCachePopup) {
            delay(1500)
            showNoClearableCachePopup = false
        }
    }

    LaunchedEffect(showUninstallSuccessPopup) {
        if (showUninstallSuccessPopup) {
            delay(1500)
            showUninstallSuccessPopup = false
        }
    }

    if (showClearCacheSuccessPopup) {
        CenterToastDialog(text = "清理缓存成功", onDismiss = { showClearCacheSuccessPopup = false })
    }

    if (showNoClearableCachePopup) {
        CenterToastDialog(text = "暂无可清理的缓存", onDismiss = { showNoClearableCachePopup = false })
    }

    if (showUninstallSuccessPopup) {
        CenterToastDialog(text = "卸载成功", onDismiss = { showUninstallSuccessPopup = false })
    }

    val appInfoItem = remember(appInfoPackage, items) {
        val pkg = appInfoPackage ?: return@remember null
        items.firstOrNull { it.packageName == pkg }
    }
    if (appInfoPackage != null && appInfoItem != null) {
        AppInfoScreen(
            item = appInfoItem,
            onBack = { appInfoPackage = null },
            onUninstall = {
                if (appInfoItem.packageName == context.packageName) {
                    Toast.makeText(context, "无法卸载当前应用", Toast.LENGTH_SHORT).show()
                    return@AppInfoScreen
                }
                scope.launch {
                    // Prefer silent uninstall on privileged/system builds; fallback to system confirm UI.
                    val ok = withContext(Dispatchers.IO) {
                        uninstallPackageSystemBestEffort(context, appInfoItem.packageName)
                    }
                    if (ok) {
                        appInfoPackage = null
                        showUninstallSuccessPopup = true
                        if (!isPreview) {
                            loading = true
                            items = withContext(Dispatchers.IO) { loadAppStorageItems(context) }
                            loading = false
                        }
                    } else {
                        requestUninstallUi(context, appInfoItem.packageName)
                    }
                }
            }
        )
        return
    } else if (appInfoPackage != null && appInfoItem == null && !loading) {
        // App might have been removed; return to the list.
        appInfoPackage = null
        Toast.makeText(context, "无法打开应用信息", Toast.LENGTH_SHORT).show()
    }

    if (showClearCache) {
        ClearCacheScreen(
            loading = loading,
            items = items,
            clearing = clearing,
            onBack = { showClearCache = false },
            onOneClickClear = { selectedPackages ->
                if (clearing) return@ClearCacheScreen
                scope.launch {
                    val estimatedClearable = items
                        .asSequence()
                        .filter { selectedPackages.contains(it.packageName) }
                        .sumOf { it.cacheBytes.coerceAtLeast(0L) }
                    if (selectedPackages.isNotEmpty() && estimatedClearable <= 0L) {
                        showNoClearableCachePopup = true
                        return@launch
                    }

                    clearing = true
                    val (clearedPkgs, failedPkgs) = withContext(Dispatchers.IO) {
                        clearCachesBestEffort(context, selectedPackages)
                    }
                    clearing = false

                    val clearedCount = clearedPkgs.size
                    val failedCount = failedPkgs.size
                    val msg = when {
                        clearedCount > 0 && failedCount == 0 -> "已清理 $clearedCount 个应用缓存"
                        clearedCount > 0 && failedCount > 0 -> "已清理 $clearedCount 个应用缓存，$failedCount 个应用清理失败"
                        clearedCount == 0 && failedCount > 0 -> "所选应用清理失败"
                        else -> "未选择应用"
                    }

                    val fullSuccess = clearedCount > 0 && failedCount == 0
                    if (fullSuccess) {
                        // Requirement: after successful clear, return and show a popup.
                        showClearCache = false
                        showClearCacheSuccessPopup = true
                    } else {
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }

                    // Refresh storage list so cache sizes are up-to-date.
                    if (!isPreview) {
                        loading = true
                        items = withContext(Dispatchers.IO) { loadAppStorageItems(context) }
                        loading = false
                    }

                    if (failedCount > 0) {
                        // Fallback: bring user to system storage/app management screen.
                        openManageStorage(context)
                    }
                }
            }
        )
        return
    }

    val (totalBytes, availBytes) = remember(isPreview) {
        if (isPreview) {
            // Android Studio preview doesn't have Android FS paths like /data.
            32L * 1024L * 1024L * 1024L to 21_900_000_000L
        } else {
            safeFsStats()
        }
    }
    val usedBytes = (totalBytes - availBytes).coerceAtLeast(0L)
    val usedFraction = if (totalBytes > 0) usedBytes.toFloat() / totalBytes.toFloat() else 0f

    val totalCache = remember(items) { items.sumOf { it.cacheBytes.coerceAtLeast(0L) } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            //.background(Color(0xFFF0F2F5))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = colorResource(R.color.cardcolor)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "存储空间",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorResource(R.color.textblack)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "总内存${formatGb(totalBytes)}  已使用${formatGb(usedBytes)}  可用${formatGb(availBytes)}",
                            fontSize = 13.sp,
                            color = Color(0xFF6B7280)
                        )
                    }

                    // Progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .background(Color(0xFFE5E7EB), RoundedCornerShape(20.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(usedFraction.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(Color(0xFF4C73FF), RoundedCornerShape(20.dp))
                        )
                    }

                    StorageActionRow(
                        title = "清理缓存数据",
                        value = if (loading) "可清理--" else "可清理${formatBytes(context, totalCache)}",
                        modifier = Modifier.entryFocus(),
                        onClick = {
                            showClearCache = true
                        }
                    )
                }
            }
        }

        item {
            Text(
                text = "本机应用",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorResource(R.color.textblack),
                modifier = Modifier.padding(horizontal = 18.dp)
            )
        }
        if (loading) {
            item {
                // 与 WiFi/蓝牙列表一致的骨架屏：读取应用占用较慢，用微光占位行预示即将出现的应用列表。
                SettingsLoadingIndicator(
                    appearDelayMillis = 0,
                    rows = 5,
                    rowHeight = 68.dp
                )
            }
        } else {
            itemsIndexed(
                items = items,
                key = { _, item -> item.packageName }
            ) { idx, item ->
                AppRow(
                    item = item,
                    onClick = { appInfoPackage = item.packageName }
                )
                if (idx != items.lastIndex) {
                    Divider(color = Color(0xFFE9EAEC), modifier = Modifier.padding(horizontal = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun StorageActionRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp)) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorResource(R.color.textblack),
                modifier = Modifier.align(Alignment.CenterStart)
            )
            Row(modifier = Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                Text(text = value, fontSize = 14.sp, color = Color(0xFF8B909A))
                Spacer(Modifier.width(10.dp))
                Icon(
                    painter = painterResource(R.drawable.arrow_right),
                    contentDescription = null,
                    tint = Color(0xFFADB3BD),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AppIcon(item: AppStorageItem, size: Int) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val isPreview = LocalInspectionMode.current
    var icon by remember(item.packageName) { mutableStateOf(item.icon) }

    LaunchedEffect(item.packageName) {
        if (icon != null || isPreview) return@LaunchedEffect
        val maxPx = with(density) { size.dp.toPx().toInt().coerceAtLeast(1) }
        icon = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(item.packageName).toImageBitmap(maxPx)
            }.getOrNull()
        }
    }

    if (icon != null) {
        Image(bitmap = icon!!, contentDescription = null, modifier = Modifier.size(size.dp))
    } else {
        Icon(
            painter = painterResource(R.drawable.account),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(size.dp)
        )
    }
}

@Composable
private fun AppRow(item: AppStorageItem, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(modifier = Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(item = item, size = 40)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = item.label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorResource(R.color.textblack)
                )
            }

            Row(modifier = Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                Text(text = formatBytes(LocalContext.current, item.totalBytes), fontSize = 14.sp, color = Color(0xFF8B909A))
                Spacer(Modifier.width(10.dp))
                Icon(
                    painter = painterResource(R.drawable.arrow_right),
                    contentDescription = null,
                    tint = Color(0xFFADB3BD),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AppInfoScreen(
    item: AppStorageItem,
    onBack: () -> Unit,
    onUninstall: () -> Unit,
) {
    val context = LocalContext.current
    val versionName = remember(item.packageName) { getVersionNameBestEffort(context, item.packageName) }
    val appSizeText = formatBytesAllowZero(context, item.appBytes)
    val cacheSizeText = formatBytesAllowZero(context, item.cacheBytes)
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F2F5))
            .padding(24.dp)
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .verticalScroll(scrollState)
                // Reserve space so the bottom CTA never overlaps content.
                .padding(bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Icon(
                    painter = painterResource(R.drawable.back),
                    contentDescription = "返回",
                    modifier = Modifier
                        .size(26.dp)
                        .align(Alignment.CenterStart)
                        .clickable { onBack() },
                    tint = colorResource(R.color.textblack)
                )
                Text(
                    text = "应用信息",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorResource(R.color.textblack),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Card(
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF6F7F9)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 26.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppIcon(item = item, size = 86)
                    Text(
                        text = item.label,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorResource(R.color.textblack)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        Text(text = "应用大小：$appSizeText", fontSize = 14.sp, color = Color(0xFF8B909A))
                        Text(text = "缓存数据：$cacheSizeText", fontSize = 14.sp, color = Color(0xFF8B909A))
                    }

                    Text(text = "版本号：$versionName", fontSize = 14.sp, color = Color(0xFF8B909A))
                }
            }
        }

        Card(
            shape = RoundedCornerShape(999.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF4C73FF)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .height(56.dp)
                .width(260.dp)
                .clickable { onUninstall() }
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "一键卸载",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun CenterToastDialog(
    text: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Box(modifier = Modifier.padding(horizontal = 54.dp, vertical = 22.dp)) {
                    Text(
                        text = text,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorResource(R.color.textblack),
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, bytes: Long) {
    val ctx = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, fontSize = 14.sp, color = Color(0xFF6B7280))
        Text(text = formatBytes(ctx, bytes), fontSize = 14.sp, color = colorResource(R.color.textblack), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ActionRow(text: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7F8)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = text, fontSize = 14.sp, color = colorResource(R.color.textblack), fontWeight = FontWeight.SemiBold)
            Icon(
                painter = painterResource(R.drawable.arrow_right),
                contentDescription = null,
                tint = Color(0xFFADB3BD),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun openManageStorage(context: Context) {
    val intents = listOf(
        // Use action strings directly to avoid compileSdk/API constant availability issues.
        Intent("android.settings.INTERNAL_STORAGE_SETTINGS"),
        Intent("android.settings.STORAGE_SETTINGS"),
        Intent("android.settings.MANAGE_STORAGE"),
    )
    val found = intents.firstOrNull { it.resolveActivity(context.packageManager) != null }
    if (found != null) {
        found.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(found)
    } else {
        Toast.makeText(context, "无法打开存储管理页面", Toast.LENGTH_SHORT).show()
    }
}

private fun requestUninstallUi(context: Context, packageName: String) {
    val intent = Intent(Intent.ACTION_DELETE).apply {
        data = Uri.parse("package:$packageName")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "无法卸载该应用", Toast.LENGTH_SHORT).show() }
}

private fun uninstallPackageSystemBestEffort(context: Context, packageName: String): Boolean {
    // System/privileged builds may be able to uninstall without user confirmation.
    // If missing permission, this fails and caller should fallback to UI uninstall.
    return uninstallPackageSystem(context, packageName)
}

private fun uninstallPackageSystem(context: Context, packageName: String): Boolean {
    return runCatching {
        val pm = context.packageManager
        val observerInterface = Class.forName("android.content.pm.IPackageDeleteObserver")

        // Try newer signature first: deletePackageAsUser(String, IPackageDeleteObserver, int, int)
        val method = pm.javaClass.methods.firstOrNull { m ->
            (m.name == "deletePackageAsUser" || m.name == "deletePackage") &&
                m.parameterTypes.isNotEmpty() &&
                m.parameterTypes[0] == String::class.java &&
                observerInterface.isAssignableFrom(m.parameterTypes[1])
        } ?: return@runCatching false

        val latch = CountDownLatch(1)
        val success = AtomicBoolean(false)

        val binder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                // TRANSACTION_packageDeleted is 1 on AOSP; keep tolerant.
                if (code == 1) {
                    runCatching {
                        data.enforceInterface("android.content.pm.IPackageDeleteObserver")
                        data.readString() // package name
                        val returnCode = data.readInt()
                        // On AOSP: PackageManager.DELETE_SUCCEEDED == 1
                        success.set(returnCode == 1)
                        latch.countDown()
                    }
                    return true
                }
                return super.onTransact(code, data, reply, flags)
            }
        }

        val observer = java.lang.reflect.Proxy.newProxyInstance(
            observerInterface.classLoader,
            arrayOf(observerInterface),
        ) { _, m, _ ->
            when (m.name) {
                "asBinder" -> binder as IBinder
                else -> null
            }
        }

        when (method.parameterTypes.size) {
            3 -> {
                // deletePackage(String, IPackageDeleteObserver, int)
                method.invoke(pm, packageName, observer, 0)
            }
            4 -> {
                // deletePackageAsUser(String, IPackageDeleteObserver, int, int)
                // Avoid hidden/SDK-variant APIs; userId is encoded in uid by PER_USER_RANGE (100000).
                val userId = android.os.Process.myUid() / 100000
                method.invoke(pm, packageName, observer, 0, userId)
            }
            else -> return@runCatching false
        }

        // Wait up to 4s; if no callback, treat as failure.
        latch.await(4, TimeUnit.SECONDS) && success.get()
    }.getOrDefault(false)
}

private fun formatBytes(context: Context, bytes: Long): String {
    if (bytes <= 0L) return "--"
    return Formatter.formatFileSize(context, bytes)
}

private fun formatBytesAllowZero(context: Context, bytes: Long): String {
    if (bytes < 0L) return "--"
    return Formatter.formatFileSize(context, bytes)
}

private fun getVersionNameBestEffort(context: Context, packageName: String): String {
    val pm = context.packageManager
    return runCatching {
        val pi = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
        }
        pi.versionName ?: "--"
    }.getOrDefault("--")
}

private fun formatGb(bytes: Long): String {
    if (bytes <= 0L) return "0.00GB"
    val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    return String.format("%.2fGB", gb)
}

private suspend fun loadAppStorageItems(context: Context): List<AppStorageItem> {
    val pm = context.packageManager
    val apps = pm.getInstalledApplications(0)
        .filter { it.packageName != context.packageName }

    val canReadStats = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    val statsMgr: StorageStatsManager? =
        if (canReadStats) context.getSystemService(StorageStatsManager::class.java) else null
    val userHandle: UserHandle? =
        if (canReadStats) android.os.Process.myUserHandle() else null

    val result = ArrayList<AppStorageItem>(apps.size)
    for (ai in apps) {
        val label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(ai.packageName)

        var appBytes = -1L
        var dataBytes = -1L
        var cacheBytes = -1L
        var totalBytes = -1L

        // Best-effort: if the app has access, show real stats; otherwise show "--".
        if (statsMgr != null && userHandle != null) {
            try {
                // storageUuid is API 26+
                val uuid: UUID = ai.storageUuid ?: StorageManager.UUID_DEFAULT
                val stats = statsMgr.queryStatsForPackage(uuid, ai.packageName, userHandle)
                appBytes = stats.appBytes
                dataBytes = stats.dataBytes
                cacheBytes = stats.cacheBytes
                totalBytes = appBytes + dataBytes + cacheBytes
            } catch (_: Throwable) {
                // SecurityException / NameNotFoundException etc.
            }
        }

        result.add(
            AppStorageItem(
                packageName = ai.packageName,
                label = label,
                icon = null,
                totalBytes = totalBytes,
                appBytes = appBytes,
                dataBytes = dataBytes,
                cacheBytes = cacheBytes,
            )
        )
    }

    // Sort by totalBytes desc; unknown sizes at the end.
    return result.sortedWith(
        compareByDescending<AppStorageItem> { it.totalBytes >= 0 }
            .thenByDescending { it.totalBytes }
            .thenBy { it.label }
    )
}

private fun android.graphics.drawable.Drawable.toImageBitmap(maxSizePx: Int): ImageBitmap {
    val width = intrinsicWidth.coerceAtLeast(1).coerceAtMost(maxSizePx)
    val height = intrinsicHeight.coerceAtLeast(1).coerceAtMost(maxSizePx)
    val bmp = Bitmap.createBitmap(
        width,
        height,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp.asImageBitmap()
}

@Composable
private fun ClearCacheScreen(
    loading: Boolean,
    items: List<AppStorageItem>,
    clearing: Boolean,
    onBack: () -> Unit,
    onOneClickClear: (selectedPackages: Set<String>) -> Unit
) {
    val context = LocalContext.current

    val candidates = remember(items) {
        // Only show apps with known cache bytes; if unknown, still show but "预计可清理--".
        items.sortedWith(
            compareByDescending<AppStorageItem> { it.cacheBytes >= 0 }
                .thenByDescending { it.cacheBytes }
                .thenBy { it.label }
        )
    }

    var selected by remember(items) {
        mutableStateOf(candidates.map { it.packageName }.toSet())
    }

    val totalSelectedCache = remember(selected, candidates) {
        candidates.filter { selected.contains(it.packageName) }
            .sumOf { it.cacheBytes.coerceAtLeast(0L) }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F2F5))
            .padding(24.dp)
    ) {
        val spacing = 18.dp
        val cardWidth = (maxWidth - spacing) / 2f
        val rows = (candidates.size + 1) / 2

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                // Top bar inside content area (app already has a global top bar outside).
                Box(modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        painter = painterResource(R.drawable.back),
                        contentDescription = "返回",
                        modifier = Modifier
                            .size(26.dp)
                            .align(Alignment.CenterStart)
                            .clickable { onBack() },
                        tint = colorResource(R.color.textblack)
                    )
                    Text(
                        text = "清除缓存数据",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorResource(R.color.textblack),
                        modifier = Modifier.align(Alignment.Center)
                    )

                    Card(
                        shape = RoundedCornerShape(999.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF4C73FF)),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .height(44.dp)
                            .clickable(enabled = !clearing && !loading) { onOneClickClear(selected) }
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (clearing) "清理中" else "一键清除",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "选择需要清理的应用",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorResource(R.color.textblack)
                )
            }

            if (loading) {
                item {
                    SettingsLoadingIndicator(
                        appearDelayMillis = 0,
                        rows = 4,
                        rowHeight = 68.dp
                    )
                }
            } else {
                items(
                    count = rows,
                    key = { row ->
                        val left = candidates.getOrNull(row * 2)?.packageName.orEmpty()
                        val right = candidates.getOrNull(row * 2 + 1)?.packageName.orEmpty()
                        "$left|$right"
                    }
                ) { r ->
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                        val left = candidates.getOrNull(r * 2)
                        val right = candidates.getOrNull(r * 2 + 1)
                        if (left != null) {
                            CacheAppCard(
                                item = left,
                                selected = selected.contains(left.packageName),
                                width = cardWidth,
                                onToggle = {
                                    selected = selected.toMutableSet().also { set ->
                                        if (!set.add(left.packageName)) set.remove(left.packageName)
                                    }.toSet()
                                }
                            )
                        } else {
                            Spacer(Modifier.width(cardWidth))
                        }
                        if (right != null) {
                            CacheAppCard(
                                item = right,
                                selected = selected.contains(right.packageName),
                                width = cardWidth,
                                onToggle = {
                                    selected = selected.toMutableSet().also { set ->
                                        if (!set.add(right.packageName)) set.remove(right.packageName)
                                    }.toSet()
                                }
                            )
                        } else {
                            Spacer(Modifier.width(cardWidth))
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "预计可清理 ${formatBytes(context, totalSelectedCache)}",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280)
                    )
                }
            }
        }
    }
}

@Composable
private fun CacheAppCard(
    item: AppStorageItem,
    selected: Boolean,
    width: androidx.compose.ui.unit.Dp,
    onToggle: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .width(width)
            .clickable(onClick = onToggle)
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp)) {
            Row(modifier = Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(item = item, size = 46)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = item.label,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorResource(R.color.textblack)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (item.cacheBytes >= 0) "预计可清理${formatBytes(LocalContext.current, item.cacheBytes)}"
                        else "预计可清理--",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(34.dp)
                    .background(
                        color = if (selected) Color(0xFF4C73FF) else Color(0xFFE5E7EB),
                        shape = RoundedCornerShape(999.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color.White else Color(0xFF9CA3AF)
                )
            }
        }
    }
}

private fun clearCachesBestEffort(context: Context, selectedPackages: Set<String>): Pair<Set<String>, Set<String>> {
    if (selectedPackages.isEmpty()) return emptySet<String>() to emptySet()

    val cleared = LinkedHashSet<String>()
    val failed = LinkedHashSet<String>()

    for (pkg in selectedPackages) {
        val ok = clearAppCacheSystem(context, pkg) || clearOwnCacheFallback(context, pkg)
        if (ok) cleared.add(pkg) else failed.add(pkg)
    }

    return cleared to failed
}

private fun clearOwnCacheFallback(context: Context, packageName: String): Boolean {
    if (packageName != context.packageName) return false
    return runCatching {
        context.cacheDir?.deleteRecursively()
        context.codeCacheDir?.deleteRecursively()
    }.isSuccess
}

private fun clearAppCacheSystem(context: Context, packageName: String): Boolean {
    // System/signed builds can clear other apps' cache via hidden PackageManager API.
    // We use reflection + a small Binder callback to avoid compileSdk constraints.
    return runCatching {
        val pm = context.packageManager
        val observerInterface = Class.forName("android.content.pm.IPackageDataObserver")
        val method = pm.javaClass.methods.firstOrNull { m ->
            m.name == "deleteApplicationCacheFiles" &&
                m.parameterTypes.size == 2 &&
                m.parameterTypes[0] == String::class.java &&
                observerInterface.isAssignableFrom(m.parameterTypes[1])
        } ?: return@runCatching false

        val latch = CountDownLatch(1)
        val success = AtomicBoolean(false)

        val binder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                // TRANSACTION_onRemoveCompleted is 1 on AOSP; keep tolerant.
                if (code == 1) {
                    runCatching {
                        data.enforceInterface("android.content.pm.IPackageDataObserver")
                        data.readString() // package name
                        val succeeded = data.readInt() != 0
                        success.set(succeeded)
                        latch.countDown()
                    }
                    return true
                }
                return super.onTransact(code, data, reply, flags)
            }
        }

        val observer = java.lang.reflect.Proxy.newProxyInstance(
            observerInterface.classLoader,
            arrayOf(observerInterface),
        ) { _, m, _ ->
            when (m.name) {
                "asBinder" -> binder as IBinder
                else -> null
            }
        }

        method.invoke(pm, packageName, observer)
        // Wait up to 2s; if no callback, treat as failure.
        latch.await(2, TimeUnit.SECONDS) && success.get()
    }.getOrDefault(false)
}

@Preview(showBackground = true, backgroundColor = 0xFFF0F2F5, widthDp = 1200)
@Composable
private fun StorageSettingsScreenPreview() {
    设置Theme {
        StorageSettingsScreen()
    }
}
