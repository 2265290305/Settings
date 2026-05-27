package com.android.tv.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.digitallife.iotsdk.api.ConnectCallback
import com.digitallife.iotsdk.api.IotSDK
import com.digitallife.iotsdk.api.OutRequest

private const val IOT_SDK_TAG = "IotSdkBridge"
private const val METHOD_DEV_QUERY = "DEV_QUERY"
private const val METHOD_DEV_OPT = "DEV_OPT"
private const val KEY_IOT_CONNECT_STATUS = "iotConnectStatus"
private val DEVICE_INFO_URI: Uri = Uri.parse("content://com.android.zshd.deviceinfo/device_info")
private val DEV_STAT_URI: Uri = Uri.parse("content://com.android.zshd.deviceinfo/devStat")

/**
 * Bridge for the real digitallife iotsdk.aar.
 *
 * This SDK does not use assets/config.txt. It is initialized with device identifiers
 * and reports connection/command callbacks through its Java API.
 */
object IotSdkBridge {
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true

        runCatching {
            val appContext = context.applicationContext
            val sdk = IotSDK.getInstance()

            sdk.setDebugEnvironment(false)
            sdk.setCtei(queryDeviceValue(appContext, "ctei").orEmpty())
            sdk.setPin(queryDeviceValue(appContext, "pin").orEmpty())
            sdk.setDevVersion(queryDeviceValue(appContext, "devVersion") ?: Build.DISPLAY.orEmpty())
            sdk.setDevModel(queryDeviceValue(appContext, "devModel") ?: Build.MODEL.orEmpty())
            sdk.setDevMac(
                queryDeviceValue(appContext, "devMac")
                    ?: queryDeviceValue(appContext, "mac")
                    ?: queryDeviceValue(appContext, "wifiMac")
                    ?: ""
            )

            sdk.setConnectCallback(object : ConnectCallback {
                override fun onConnected() {
                    Log.i(IOT_SDK_TAG, "iot connected")
                    updateProviderValue(appContext, KEY_IOT_CONNECT_STATUS, "1")
                }

                override fun onConnectLost() {
                    Log.i(IOT_SDK_TAG, "iot connection lost")
                    updateProviderValue(appContext, KEY_IOT_CONNECT_STATUS, "0")
                }
            })

            sdk.setIotCmdCB(OutRequest { request ->
                Log.i(IOT_SDK_TAG, "iot command request=$request")
            })

            sdk.init(appContext, false)
            sdk.iotStart()
            Log.i(IOT_SDK_TAG, "iot sdk started")
        }.onFailure {
            started = false
            Log.e(IOT_SDK_TAG, "start iot sdk failed", it)
        }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching {
            IotSDK.getInstance().iotStop()
        }.onFailure {
            Log.e(IOT_SDK_TAG, "stop iot sdk failed", it)
        }
    }

    private fun queryDeviceValue(context: Context, key: String): String? {
        return queryProviderValue(context, DEVICE_INFO_URI, key)
            ?: queryProviderValue(context, DEV_STAT_URI, key)
    }

    private fun queryProviderValue(context: Context, uri: Uri, key: String): String? {
        return runCatching {
            val extras = Bundle().apply {
                putString("key", key)
                putString(key, "")
            }
            val result = context.contentResolver.call(uri, METHOD_DEV_QUERY, null, extras)
            normalizeValue(result?.getString(key))
                ?: normalizeValue(result?.getString("value"))
                ?: normalizeValue(result?.getString("result"))
        }.onFailure {
            Log.d(IOT_SDK_TAG, "query $key failed uri=$uri: ${it.message}")
        }.getOrNull()
    }

    private fun updateProviderValue(context: Context, key: String, value: String): Boolean {
        return updateProviderValue(context, DEV_STAT_URI, key, value)
            || updateProviderValue(context, DEVICE_INFO_URI, key, value)
    }

    private fun updateProviderValue(context: Context, uri: Uri, key: String, value: String): Boolean {
        return runCatching {
            val extras = Bundle().apply {
                putString("key", key)
                putString("value", value)
                putString(key, value)
            }
            val result = context.contentResolver.call(uri, METHOD_DEV_OPT, null, extras)
            result?.getBoolean("success", false) == true ||
                result?.getBoolean("result", false) == true ||
                result?.getInt("code", -1) == 0
        }.onFailure {
            Log.d(IOT_SDK_TAG, "update $key failed uri=$uri: ${it.message}")
        }.getOrDefault(false)
    }

    private fun normalizeValue(value: String?): String? {
        val text = value?.trim().orEmpty()
        return text.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }
}
