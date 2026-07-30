package com.android.tv.settings

import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration

fun fakeScanResult(
    ssid: String,
    level: Int,
    capabilities: String = "[WPA2-PSK-CCMP][ESS]"
): ScanResult {

    val clazz = ScanResult::class.java

    // 拿到无参构造
    val constructor = clazz.getDeclaredConstructor()
    constructor.isAccessible = true
    val scanResult = constructor.newInstance()

    // 通过反射设置字段
    clazz.getField("SSID").set(scanResult, ssid)
    clazz.getField("level").set(scanResult, level)
    clazz.getField("capabilities").set(scanResult, capabilities)
    clazz.getField("BSSID").set(scanResult, "00:11:22:33:44:${(10..99).random()}")

    return scanResult
}

fun fakeWifiConfig(
    ssid: String,
    isWpa2: Boolean = true
): WifiConfiguration {

    return WifiConfiguration().apply {
        SSID = "\"$ssid\""   // ⚠ 必须带双引号
        status = WifiConfiguration.Status.ENABLED
        priority = 1

        if (isWpa2) {
            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
        } else {
            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
        }
    }
}