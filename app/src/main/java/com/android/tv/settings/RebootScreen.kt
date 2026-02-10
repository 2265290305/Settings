package com.android.tv.settings

import android.annotation.SuppressLint
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tv.settings.ui.theme.设置Theme


@SuppressLint("MissingPermission")
@Composable
fun RebootScreen(onBack: () -> Unit) {
    Card(

    ) { }

}

@Preview(showBackground = true)
@Composable
fun RebootScreenPreview() {
    设置Theme {
        RebootScreen (onBack = {})
    }
}
