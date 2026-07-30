package com.android.tv.settings

import android.provider.Settings

class MainSettings : CompatEntryActivity() {
    override val targetAction: String = Settings.ACTION_SETTINGS
}
