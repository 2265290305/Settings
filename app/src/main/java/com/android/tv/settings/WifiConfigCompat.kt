package com.android.tv.settings

import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager

internal const val SECURITY_OPEN = "None"
internal const val SECURITY_WEP = "WEP"
internal const val SECURITY_WPA_PSK = "WPA/WPA2 PSK"
private val MAC_ADDRESS_PATTERN = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

private fun quote(value: String): String = "\"$value\""

internal fun String?.normalizedWifiSsid(): String {
    val raw = this?.trim().orEmpty().trim('"')
    if (raw.isEmpty()) return ""
    if (raw.equals("<unknown ssid>", ignoreCase = true)) return ""
    return raw
}

internal fun String?.hasDisplayableDeviceName(): Boolean {
    val value = this?.trim().orEmpty()
    if (value.isEmpty()) return false
    return !MAC_ADDRESS_PATTERN.matches(value)
}

internal fun wifiSecurityFromCapabilities(capabilities: String?): String {
    val raw = capabilities.orEmpty()
    return when {
        raw.contains("WEP", ignoreCase = true) -> SECURITY_WEP
        raw.contains("WPA", ignoreCase = true) -> SECURITY_WPA_PSK
        else -> SECURITY_OPEN
    }
}

internal fun wifiSecurityRequiresPassword(security: String): Boolean {
    return security != SECURITY_OPEN
}

private fun buildLegacyWifiConfiguration(
    ssid: String,
    password: String,
    security: String,
): WifiConfiguration {
    return WifiConfiguration().apply {
        SSID = quote(ssid)

        allowedAuthAlgorithms.clear()
        allowedProtocols.clear()
        allowedKeyManagement.clear()
        allowedPairwiseCiphers.clear()
        allowedGroupCiphers.clear()

        when (security) {
            SECURITY_OPEN -> {
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            }

            SECURITY_WEP -> {
                wepKeys[0] = quote(password)
                wepTxKeyIndex = 0
                allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED)
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
                allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104)
            }

            else -> {
                preSharedKey = quote(password)
                allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                allowedProtocols.set(WifiConfiguration.Protocol.RSN)
                allowedProtocols.set(WifiConfiguration.Protocol.WPA)
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
                allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
                allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
                allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
            }
        }
    }
}

@Suppress("DEPRECATION")
fun WifiManager.addOrUpdateLegacyNetwork(
    ssid: String,
    password: String,
    security: String = SECURITY_WPA_PSK,
): Int {
    val config = buildLegacyWifiConfiguration(ssid, password, security)
    val expectedSsid = config.SSID.normalizedWifiSsid()
    val existing = configuredNetworks?.firstOrNull { it.SSID.normalizedWifiSsid() == expectedSsid }
    return if (existing != null) {
        config.networkId = existing.networkId
        updateNetwork(config)
    } else {
        addNetwork(config)
    }
}

@Suppress("DEPRECATION")
fun WifiManager.connectLegacyNetwork(netId: Int): Boolean {
    if (netId < 0) return false
    return runCatching {
        disconnect()
        if (!enableNetwork(netId, true)) return@runCatching false
        reconnect()
    }.getOrDefault(false)
}

@Suppress("DEPRECATION")
fun WifiManager.clearFailedLegacyNetwork(netId: Int) {
    if (netId < 0) return
    runCatching { disableNetwork(netId) }
    runCatching { removeNetwork(netId) }
    runCatching { saveConfiguration() }
}
