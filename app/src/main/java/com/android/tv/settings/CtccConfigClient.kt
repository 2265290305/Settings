package com.android.tv.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.digitallife.iotsdk.codec.DigestUtils
import com.digitallife.iotsdk.utils.AESCryptUtils
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 天翼智屏业务平台「配置中心」对接客户端。
 *
 * 流程（参考《方言设置配置获取》《天翼智屏业务平台配置中心接口规范》）：
 *  1. 通过 HTTPS 调用客户端「获取运营配置」接口（opsConfig/getConfigInfo），只取方言配置；
 *  2. 请求体 data = Base64(AES(参数, pinCode 前 16 位, pinCode 后 16 位))，
 *     sign = MD5(ctei + data + pinCode)（partnerId 为空方案，自包含，仅需 ctei + pinCode）；
 *  3. 响应 result 用 md5Hex(pinCode) 派生的 key/iv 解密；
 *  4. 解析出 dialectConfiguration，交由 [DialectSettingsScreen] 通过 DEV_OPT 下发到 ContentProvider。
 *
 * 注：AESCryptUtils / DigestUtils 来自 iotsdk.aar（爱加密加固），release 需整包 keep。
 */
object CtccConfigClient {
    private const val TAG = "CtccConfigClient"

    // 环境开关：true=生产(tenantId=13)，false=测试(tenantId=2，需白名单访问)。
    private const val IS_PRODUCTION = true

    private val TENANT_ID = if (IS_PRODUCTION) "13" else "2"
    private const val PRODUCT_ID = 1024
    private val BASE_URL =
        if (IS_PRODUCTION)
            "https://stpdevice.189smarthome.com:9036/stp-gateway-device/stp-config-api"
        else
            "https://smarthome-mini.189smarthome.com:9000/stp-gateway-device/stp-config-api"
    private val GET_CONFIG_INFO_URL = "$BASE_URL/opsConfig/getConfigInfo"

    private val DEVICE_INFO_URI: Uri = Uri.parse("content://com.android.zshd.deviceinfo/device_info")
    private val DEV_STAT_URI: Uri = Uri.parse("content://com.android.zshd.deviceinfo/devStat")

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 同步拉取并解析方言配置，需在 IO 线程调用；任何异常或非法返回都返回 null（不抛出）。
     */
    fun fetchDialectConfig(context: Context): CtccDialectConfig? {
        val appContext = context.applicationContext

        // provider 取不到时回退到系统属性（实测设备：ro.ctei / ro.product.cmctiot.pincode）。
        val ctei = queryDeviceValue(appContext, "ctei")
            ?: systemProp("ro.ctei")
            ?: systemProp("ro.product.ctei")
        val pinCode = queryDeviceValue(appContext, "pin")
            ?: systemProp("ro.product.cmctiot.pincode")
        if (ctei.isNullOrEmpty() || pinCode.isNullOrEmpty()) {
            Log.w(TAG, "missing ctei/pin (provider+prop), skip remote dialect config")
            return null
        }
        // 请求加密与响应解密同一套密钥：key/iv 取自 md5Hex(pinCode) 的前 16 / 后 16
        // （已用生产环境实测验证：用 pinCode 字面前16/后16 会被服务端报“请求数据解密失败”）。
        val cryptoKey = DigestUtils.md5Hex(pinCode)

        val transactionId = nextTransactionId()
        val romVersion = queryDeviceValue(appContext, "devVersion") ?: Build.DISPLAY.orEmpty()
        val model = queryDeviceValue(appContext, "devModel") ?: Build.MODEL.orEmpty()
        val mac = queryDeviceValue(appContext, "devMac")
            ?: queryDeviceValue(appContext, "mac")
            ?: queryDeviceValue(appContext, "wifiMac")
        val areaNo = queryDeviceValue(appContext, "areaNo")
            ?: queryDeviceValue(appContext, "region")
            ?: "000000"

        val params = JSONObject().apply {
            put("transactionId", transactionId)
            put("productId", PRODUCT_ID)
            put("ctei", ctei)
            put("romVersion", romVersion)
            put("areaNo", areaNo)
            put("model", model)
            put("configType", "dialect")
            if (!mac.isNullOrEmpty()) put("mac", mac)
        }

        return runCatching {
            // 请求体 data：AES(参数, key=md5Hex(pin)前16, iv=md5Hex(pin)后16) -> Base64
            val data = AESCryptUtils.encode(
                params.toString(),
                cryptoKey.substring(0, 16),
                cryptoKey.substring(16)
            ) ?: return null

            val sign = DigestUtils.md5Hex(ctei + data + pinCode)
            val bodyJson = JSONObject().apply {
                put("ctei", ctei)
                put("data", data)
            }.toString()

            val request = Request.Builder()
                .url(GET_CONFIG_INFO_URL)
                .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .addHeader("transactionId", transactionId)
                .addHeader("tenantId", TENANT_ID)
                .addHeader("timestamp", System.currentTimeMillis().toString())
                .addHeader("sign", sign)
                .build()

            val responseBody = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "getConfigInfo http=${response.code}")
                    return null
                }
                response.body?.string()
            }
            if (responseBody.isNullOrEmpty()) {
                Log.w(TAG, "getConfigInfo empty body")
                return null
            }

            val responseJson = JSONObject(responseBody)
            val code = responseJson.optString("code")
            if (code != "0") {
                Log.w(TAG, "getConfigInfo code=$code errors=${responseJson.opt("errors")}")
                return null
            }
            val encryptedResult = responseJson.optString("result")
            if (encryptedResult.isNullOrEmpty()) {
                Log.w(TAG, "getConfigInfo missing result")
                return null
            }

            // 响应解密：与请求同一套 key/iv = md5Hex(pinCode) 前 16 / 后 16
            val decrypted = AESCryptUtils.decode(
                encryptedResult,
                cryptoKey.substring(0, 16),
                cryptoKey.substring(16)
            )
            if (decrypted.isNullOrEmpty()) {
                Log.w(TAG, "getConfigInfo decrypt failed")
                return null
            }

            parseDialectConfig(JSONObject(decrypted))
        }.onFailure {
            Log.e(TAG, "fetchDialectConfig failed", it)
        }.getOrNull()
    }

    private fun parseDialectConfig(root: JSONObject): CtccDialectConfig? {
        val dialect = root.optJSONObject("dialectConfiguration") ?: run {
            Log.w(TAG, "no dialectConfiguration in response")
            return null
        }

        val supportDialect = if (dialect.has("supportDialect")) dialect.optInt("supportDialect", 1) == 1 else null
        val dialectListJson = dialect.optJSONArray("dialectList")?.toString()

        val defaultDialect = dialect.optJSONObject("defaultDialect")
        val defaultDialectName = defaultDialect?.optString("dialectName")?.ifBlank { null }
        val defaultDialectId = defaultDialect?.optString("dialectId")?.ifBlank { null }

        val configJson = dialect.optJSONObject("dialectConfigJson")
        val recognition = configJson?.optJSONObject("dialectRecognition")
        val recognitionDesc = recognition?.optString("desc")?.ifBlank { null }
        val mixedDialectDesc = recognition?.optString("mixedDialectDesc")?.ifBlank { null }

        val wakeUp = configJson?.optJSONObject("dialectWakeUp")
        val wakeUpSupport = if (wakeUp?.has("support") == true) wakeUp.optInt("support", 0) == 1 else null
        val wakeUpDesc = wakeUp?.optString("desc")?.ifBlank { null }

        Log.i(TAG, "dialect ok: support=$supportDialect list=$dialectListJson")
        return CtccDialectConfig(
            supportDialect = supportDialect,
            dialectListJson = dialectListJson,
            defaultDialectName = defaultDialectName,
            defaultDialectId = defaultDialectId,
            recognitionDesc = recognitionDesc,
            mixedDialectDesc = mixedDialectDesc,
            wakeUpSupport = wakeUpSupport,
            wakeUpDesc = wakeUpDesc
        )
    }

    /** transactionId：yyyyMMddHHmmssSSS(17) + 15 位随机数 = 32 位。 */
    private fun nextTransactionId(): String {
        val prefix = SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.US).format(Date())
        val random = buildString { repeat(15) { append((0..9).random()) } }
        return prefix + random
    }

    private fun queryDeviceValue(context: Context, key: String): String? {
        return queryProviderValue(context, DEVICE_INFO_URI, key)
            ?: queryProviderValue(context, DEV_STAT_URI, key)
    }

    private fun queryProviderValue(context: Context, uri: Uri, key: String): String? {
        return runCatching {
            val extras = Bundle().apply {
                putString(key, "")
            }
            val result = context.contentResolver.call(uri, "DEV_QUERY", null, extras)
            normalizeValue(result?.getString(key))
                ?: normalizeValue(result?.getString("value"))
                ?: normalizeValue(result?.getString("result"))
        }.getOrNull()
    }

    private fun normalizeValue(value: String?): String? {
        val text = value?.trim().orEmpty()
        return text.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }

    /** 读取系统属性（android.os.SystemProperties，hidden API，系统应用可反射调用）。 */
    private fun systemProp(key: String): String? = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java, String::class.java)
        normalizeValue(get.invoke(null, key, "") as String)
    }.getOrNull()
}

/** 平台下发的方言配置（已解析，null 表示该字段未返回）。 */
data class CtccDialectConfig(
    val supportDialect: Boolean?,
    val dialectListJson: String?,
    val defaultDialectName: String?,
    val defaultDialectId: String?,
    val recognitionDesc: String?,
    val mixedDialectDesc: String?,
    val wakeUpSupport: Boolean?,
    val wakeUpDesc: String?
)
