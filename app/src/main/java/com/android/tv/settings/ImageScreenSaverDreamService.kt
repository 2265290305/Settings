package com.android.tv.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.dreams.DreamService
import android.widget.FrameLayout
import android.widget.ImageView
import java.io.File

private const val DREAM_SCREEN_SAVER_PREFS = "screen_saver_prefs"
private const val DREAM_SCREEN_SAVER_IMAGE_URI_PREF_KEY = "screen_saver_image_uri"
private const val DREAM_SCREEN_SAVER_IMAGE_SECURE_URI_KEY = "screensaver_image_uri"

class ImageScreenSaverDreamService : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isInteractive = false
        setScreenBright(false)

        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        val imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.BLACK)
        }
        container.addView(
            imageView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(container)

        loadDreamBitmap()?.let(imageView::setImageBitmap)
    }

    private fun selectedImageUri(): Uri? {
        val sharedPrefs = getSharedPreferences(DREAM_SCREEN_SAVER_PREFS, MODE_PRIVATE)
        val rawValue = sharedPrefs.getString(DREAM_SCREEN_SAVER_IMAGE_URI_PREF_KEY, null)
            ?: Settings.Secure.getString(contentResolver, DREAM_SCREEN_SAVER_IMAGE_SECURE_URI_KEY)
        return rawValue?.takeIf { it.isNotBlank() }?.let(Uri::parse)
    }

    private fun loadDreamBitmap(): Bitmap? {
        val imageUri = selectedImageUri() ?: return null
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = when (imageUri.scheme) {
                    "file" -> ImageDecoder.createSource(File(imageUri.path.orEmpty()))
                    else -> ImageDecoder.createSource(contentResolver, imageUri)
                }
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val width = resources.displayMetrics.widthPixels.coerceAtLeast(1)
                    val height = resources.displayMetrics.heightPixels.coerceAtLeast(1)
                    decoder.setTargetSize(
                        info.size.width.coerceAtMost(width),
                        info.size.height.coerceAtMost(height)
                    )
                }
            } else {
                when (imageUri.scheme) {
                    "file" -> BitmapFactory.decodeFile(imageUri.path)
                    else -> contentResolver.openInputStream(imageUri)?.use(BitmapFactory::decodeStream)
                }
            }
        }.getOrNull()
    }
}
