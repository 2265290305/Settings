package com.android.tv.settings.accessories

import android.provider.Settings
import com.android.tv.settings.CompatEntryActivity

class AddAccessoryActivity : CompatEntryActivity() {
    override val targetAction: String = Settings.ACTION_BLUETOOTH_SETTINGS
}
