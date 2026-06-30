package com.android.tv.settings

import android.content.Context
import android.util.Log
import com.digitallife.iotsdk.codec.DigestUtils
import com.ota.skillsdk.RecoverySystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * OTA 升级编排器（与 TMC AIDL 并存，分工不同）：
 *  - 检查/触发：通过 [RomUpgradeClient]（com.telecom.tmc AIDL）调 checkUpgrade + getRomUpdateInfo；
 *  - 本地安装：解析升级信息拿到包（本地路径优先，否则下载并 MD5 校验）后，
 *    交给 [RecoverySystem.installPackage] 重启进 recovery 刷机。
 *
 * 注意：getRomUpdateInfo 返回的 JSON 字段规范未给出，[parseUpdateInfo] 做了多候选字段名的容错解析；
 * 若实际字段不同，仅需在该函数里调整候选名。
 */
object RomUpgradeManager {
    private const val TAG = "RomUpgradeManager"
    private const val BIND_TIMEOUT_MS = 20_000L

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** 升级信息（已容错解析）。 */
    data class RomUpdateInfo(
        val hasUpdate: Boolean,
        val version: String?,
        val url: String?,
        val localPath: String?,
        val md5: String?,
        val raw: String?
    )

    /** 检查结果。 */
    sealed class CheckResult {
        /** 未找到 TMC 升级服务，调用方可回退到 intent 方式。 */
        object NoService : CheckResult()
        /** 已是最新版本。 */
        object UpToDate : CheckResult()
        /** 发现可安装的新版本。 */
        data class Available(val info: RomUpdateInfo) : CheckResult()
        /** 检查失败（超时/异常）。 */
        data class Failed(val message: String) : CheckResult()
    }

    private sealed class Bound<out T> {
        object NoService : Bound<Nothing>()
        data class Ok<T>(val value: T) : Bound<T>()
    }

    suspend fun checkForUpdate(context: Context): CheckResult = withContext(Dispatchers.IO) {
        // 1) 触发 TMC 检查（让其刷新/准备升级信息）；NoService 直接回退。
        val checked = withTimeoutOrNull(BIND_TIMEOUT_MS) { boundCheckUpgrade(context) }
        if (checked is Bound.NoService) return@withContext CheckResult.NoService

        // 2) 获取升级信息。
        when (val infoBound = withTimeoutOrNull(BIND_TIMEOUT_MS) { boundRomInfo(context) }) {
            null -> CheckResult.Failed("获取升级信息超时")
            is Bound.NoService -> CheckResult.NoService
            is Bound.Ok -> {
                val info = infoBound.value?.let(::parseUpdateInfo)
                when {
                    info != null && info.hasUpdate && (info.url != null || info.localPath != null) ->
                        CheckResult.Available(info)
                    else -> CheckResult.UpToDate
                }
            }
        }
    }

    /**
     * 解析升级包文件：本地路径优先，否则下载到 recovery 可访问的目录并按需做 MD5 校验。
     * 需在 IO 线程调用；[onProgress] 报告下载百分比（0..100，本地路径时直接 100）。
     */
    suspend fun resolvePackageFile(
        context: Context,
        info: RomUpdateInfo,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        info.localPath?.let { path ->
            val file = File(path)
            if (file.exists() && file.length() > 0) {
                onProgress(100)
                return@withContext file
            }
        }

        val url = info.url ?: throw IOException("无可用的升级包地址")
        val dest = File(downloadDir(context), "ota_update.zip")
        download(url, dest, onProgress)

        info.md5?.takeIf { it.isNotBlank() }?.let { expected ->
            val actual = fileMd5(dest)
            if (!actual.equals(expected, ignoreCase = true)) {
                dest.delete()
                throw IOException("升级包 MD5 校验失败")
            }
        }
        dest
    }

    /** 重启进 recovery 安装升级包（调用后设备重启；该方法正常不会返回）。 */
    fun install(context: Context, packageFile: File) {
        RecoverySystem.installPackage(context.applicationContext, packageFile)
    }

    private suspend fun boundCheckUpgrade(context: Context): Bound<Int> =
        suspendCancellableCoroutine { cont ->
            val started = RomUpgradeClient.checkUpgrade(context) { code ->
                if (cont.isActive) cont.resume(Bound.Ok(code))
            }
            if (!started && cont.isActive) cont.resume(Bound.NoService)
        }

    private suspend fun boundRomInfo(context: Context): Bound<String?> =
        suspendCancellableCoroutine { cont ->
            val started = RomUpgradeClient.getRomUpdateInfo(context) { info ->
                if (cont.isActive) cont.resume(Bound.Ok(info))
            }
            if (!started && cont.isActive) cont.resume(Bound.NoService)
        }

    /** 容错解析升级信息 JSON。字段名未规范，故对常见命名做多候选匹配。 */
    fun parseUpdateInfo(json: String): RomUpdateInfo? {
        return runCatching {
            val obj = JSONObject(json)
            val root = obj.optJSONObject("data") ?: obj.optJSONObject("result") ?: obj
            val url = root.firstString("url", "downloadUrl", "packageUrl", "otaUrl", "fileUrl")
            val localPath = root.firstString("localPath", "filePath", "path", "packagePath")
            val version = root.firstString("version", "newVersion", "targetVersion", "versionName")
            val md5 = root.firstString("md5", "fileMd5", "packageMd5")
            val explicit = root.firstBool("hasUpdate", "needUpdate", "hasNewVersion", "update")
            val hasUpdate = explicit ?: (url != null || localPath != null)
            RomUpdateInfo(hasUpdate, version, url, localPath, md5, json)
        }.onFailure { Log.w(TAG, "parseUpdateInfo failed", it) }.getOrNull()
    }

    /** recovery 可访问的下载目录：优先 /cache，其次应用 cacheDir（位于 /data，recovery 通常可读）。 */
    private fun downloadDir(context: Context): File {
        val cache = File("/cache")
        if (cache.isDirectory && cache.canWrite()) return cache
        return context.applicationContext.cacheDir
    }

    private fun download(url: String, dest: File, onProgress: (Int) -> Unit) {
        if (dest.exists()) dest.delete()
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("下载失败 http=${response.code}")
            val body = response.body ?: throw IOException("下载响应为空")
            val total = body.contentLength()
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val percent = (downloaded * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
        }
        onProgress(100)
    }

    private fun fileMd5(file: File): String {
        return FileInputStream(file).use { DigestUtils.md5Hex(it) }
    }

    private fun JSONObject.firstString(vararg keys: String): String? {
        for (key in keys) {
            val value = optString(key).trim()
            if (value.isNotEmpty() && !value.equals("null", ignoreCase = true)) return value
        }
        return null
    }

    private fun JSONObject.firstBool(vararg keys: String): Boolean? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            when (val value = opt(key)) {
                is Boolean -> return value
                is Number -> return value.toInt() == 1
                is String -> return value.equals("true", ignoreCase = true) || value == "1"
            }
        }
        return null
    }
}
