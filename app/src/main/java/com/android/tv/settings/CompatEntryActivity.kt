package com.android.tv.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle

abstract class CompatEntryActivity : Activity() {
    protected abstract val targetAction: String?

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val forwardIntent = Intent(this, MainActivity::class.java).apply {
            action = targetAction ?: intent?.action
            intent?.extras?.let { putExtras(it) }
            data = intent?.data
            type = intent?.type
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        startActivity(forwardIntent)
        finish()
        overridePendingTransition(0, 0)
    }
}
