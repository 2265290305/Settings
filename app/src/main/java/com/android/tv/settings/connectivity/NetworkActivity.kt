package com.android.tv.settings.connectivity

import android.provider.Settings
import com.android.tv.settings.CompatEntryActivity

class NetworkActivity : CompatEntryActivity() {
    override val targetAction: String = Settings.ACTION_WIFI_SETTINGS
}
