package com.android.tv.settings

import android.annotation.SuppressLint
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tv.settings.ui.theme.设置Theme


@SuppressLint("MissingPermission")
@Composable
fun RebootScreen(onBack: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) { }

}

@Preview(showBackground = true)
@Composable
fun RebootScreenPreview() {
    设置Theme {
        RebootScreen (onBack = {})
    }
}
