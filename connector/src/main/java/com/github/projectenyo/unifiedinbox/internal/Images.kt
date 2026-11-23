package com.github.projectenyo.unifiedinbox.internal

import android.graphics.Bitmap
import android.os.Build
import com.github.projectenyo.unifiedinbox.Protocol
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Turns a connector's image into what SPEC.md allows: a WebP within the size limits, in its own shape. */
internal object Images {
    fun encode(bitmap: Bitmap): ByteArray? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        var edge = Protocol.MAX_IMAGE_PIXELS
        @Suppress("DEPRECATION")
        val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        // Quality first, then size, until the image fits.
        repeat(3) {
            val scale = minOf(1f, edge.toFloat() / max(bitmap.width, bitmap.height))
            val scaled = if (scale >= 1f) bitmap
                else Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt().coerceAtLeast(1), (bitmap.height * scale).roundToInt().coerceAtLeast(1), true)
            for (quality in intArrayOf(85, 70, 50)) {
                val bytes = ByteArrayOutputStream().use { out -> scaled.compress(format, quality, out); out.toByteArray() }
                if (bytes.size <= Protocol.MAX_IMAGE_BYTES) return bytes
            }
            edge = edge * 2 / 3
        }
        return null
    }
}
