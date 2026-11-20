package com.example.capture

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.BatteryManager
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class BatteryTelemetry(
    val percent: Int,
    val charging: Boolean
)

object ImageUtils {
    fun readBatteryTelemetry(context: Context): BatteryTelemetry {
        val statusIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = statusIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = statusIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = statusIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).roundToInt().coerceIn(0, 100)
        } else {
            100
        }
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return BatteryTelemetry(percent = percent, charging = charging)
    }

    fun encodeBitmapToBase64(
        bitmap: Bitmap,
        maxDimension: Int = 400,
        quality: Int = 50,
        rotationDegrees: Int = 0
    ): String {
        val rotated = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }

        val largestSide = max(rotated.width, rotated.height)
        val scaled = if (largestSide > maxDimension) {
            val ratio = maxDimension.toFloat() / largestSide.toFloat()
            val targetW = (rotated.width * ratio).roundToInt().coerceAtLeast(1)
            val targetH = (rotated.height * ratio).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(rotated, targetW, targetH, true)
        } else {
            rotated
        }

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        val bytes = out.toByteArray()

        if (scaled !== rotated) scaled.recycle()
        if (rotated !== bitmap) rotated.recycle()

        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun decodeBase64ToImageBitmap(base64: String): ImageBitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}
