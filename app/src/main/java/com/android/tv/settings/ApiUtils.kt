package com.android.tv.settings

import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun hdmiapi(): String {
    val contentResolver = LocalContext.current.contentResolver
    val resolver = contentResolver
    val uri = Uri.parse("content://com.android.zshd.deviceinfo/settings")

    val extras = Bundle().apply {
        putString("key", "value")
    }

    val result: Bundle? = resolver.call(
        uri,
        "METHOD_NAME_",
        null,
        extras
    )
    if (result?.containsKey("success") == true) {
        val resultData = result.getString("key");
        return resultData.toString()
    }
    return ""
}
