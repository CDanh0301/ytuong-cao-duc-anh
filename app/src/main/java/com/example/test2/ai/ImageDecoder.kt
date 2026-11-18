package com.example.test2.ai

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import com.example.test2.ai.cards.QualityFlags
import com.example.test2.storage.LocalCryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.math.roundToInt
import kotlin.math.sqrt

object ImageDecoder {

    data class Decoded(val bitmap: Bitmap, val rotationDegrees: Int)

    suspend fun decode(
        context: Context,
        path: String,
        maxPixels: Int = getEffectiveMaxPixels(context)
    ): Decoded? = withContext(Dispatchers.IO) {
        if (!QualityFlags.SHARED_DECODE) {
            return@withContext legacyDecode(context, path)
        }

        try {
            decodeInternal(context, path, maxPixels)
        } catch (oom: OutOfMemoryError) {
            android.util.Log.w("ImageDecoder", "OOM decoding $path with maxPixels=$maxPixels, retrying with half", oom)
            try {
                decodeInternal(context, path, maxPixels / 2)
            } catch (secondOom: OutOfMemoryError) {
                android.util.Log.e("ImageDecoder", "Failed to decode $path after OOM retry", secondOom)
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageDecoder", "Error decoding image at $path: ${e.message}", e)
            null
        }
    }

    private fun getEffectiveMaxPixels(context: Context): Int {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am != null && (am.isLowRamDevice || am.memoryClass < 192)) {
                2_500_000
            } else {
                QualityFlags.maxDecodePixels
            }
        } catch (_: Exception) {
            QualityFlags.maxDecodePixels
        }
    }

    private fun decodeInternal(context: Context, path: String, maxPixels: Int): Decoded? {
        android.util.Log.d("ImageDecoder", "decodeInternal starting for $path")
        val rotationDegrees = getRotationDegrees(context, path)

        // 1. Measure dimensions
        val bounds = measureBounds(context, path)
        if (bounds == null) {
            android.util.Log.e("ImageDecoder", "measureBounds returned null for $path")
            return null
        }
        val origW = bounds.first
        val origH = bounds.second
        if (origW <= 0 || origH <= 0) {
            android.util.Log.e("ImageDecoder", "measureBounds origW/origH invalid ($origW x $origH) for $path")
            return null
        }

        // 2. Compute inSampleSize (power of 2)
        var sampleSize = 1
        var curPixels = (origW.toLong() * origH.toLong())
        while (curPixels / (sampleSize.toLong() * sampleSize.toLong()) > (maxPixels * 1.35)) {
            sampleSize *= 2
        }

        // 3. Decode scaled down bitmap
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decodedBitmap = openStream(context, path)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        if (decodedBitmap == null) {
            android.util.Log.e("ImageDecoder", "decodeStream returned null for $path with sampleSize=$sampleSize")
            return null
        }

        // 4. Fine scale if still above maxPixels
        val actualPixels = decodedBitmap.width.toLong() * decodedBitmap.height.toLong()
        val finalBitmap = if (actualPixels > (maxPixels * 1.35)) {
            val scale = sqrt(maxPixels.toDouble() / actualPixels.toDouble())
            val targetW = (decodedBitmap.width * scale).roundToInt().coerceAtLeast(1)
            val targetH = (decodedBitmap.height * scale).roundToInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(decodedBitmap, targetW, targetH, true)
            if (scaled != decodedBitmap) {
                decodedBitmap.recycle()
            }
            scaled
        } else {
            decodedBitmap
        }

        android.util.Log.d("ImageDecoder", "decodeInternal SUCCESS for $path: ${finalBitmap.width}x${finalBitmap.height}")
        return Decoded(finalBitmap, rotationDegrees)
    }

    private fun measureBounds(context: Context, path: String): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        val stream = openStream(context, path) ?: return null
        try {
            stream.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageDecoder", "measureBounds exception for $path: ${e.message}")
            return null
        }

        if (options.outWidth <= 0 || options.outHeight <= 0) {
            android.util.Log.e("ImageDecoder", "measureBounds invalid dimensions (${options.outWidth} x ${options.outHeight}) for $path")
            return null
        }
        return Pair(options.outWidth, options.outHeight)
    }

    private fun getRotationDegrees(context: Context, path: String): Int {
        return try {
            val file = File(path)
            val orientation = if (file.exists() && !LocalCryptoManager.isEncrypted(file)) {
                val exif = ExifInterface(file.absolutePath)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } else {
                openStream(context, path)?.use { stream ->
                    val exif = ExifInterface(stream)
                    exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            }

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun openStream(context: Context, path: String): InputStream? {
        return try {
            val file = File(path)
            if (file.exists() && LocalCryptoManager.isEncrypted(file)) {
                LocalCryptoManager.openDecryptedInputStream(file)
            } else if (path.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(path))
            } else if (path.startsWith("file://")) {
                context.contentResolver.openInputStream(Uri.parse(path)) ?: file.inputStream()
            } else if (file.exists()) {
                file.inputStream()
            } else {
                context.contentResolver.openInputStream(Uri.fromFile(file))
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageDecoder", "openStream failed for $path: ${e.message}")
            null
        }
    }

    private fun legacyDecode(context: Context, path: String): Decoded? {
        val stream = openStream(context, path) ?: return null
        val bitmap = stream.use { BitmapFactory.decodeStream(it) } ?: return null
        return Decoded(bitmap, 0)
    }
}
