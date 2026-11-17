package com.example.test2.services

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.example.test2.database.AppDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 1: Zero-Friction Passive Screenshot Capture (IDEA.md Step 1 & Step 4).
 * Only inspects MediaStore, enqueues the photo IDs into SQLite, triggers WorkManager and exits in < 10ms.
 * Immune to OS Termination / LMK, zero battery drain.
 */
object ScreenshotDetectionManager {

    private fun isScreenshotMedia(name: String, bucket: String, path: String): Boolean {
        val lowerName = name.lowercase()
        val lowerBucket = bucket.lowercase()
        val lowerPath = path.lowercase()

        return lowerName.contains("screenshot") ||
                lowerName.contains("screencap") ||
                lowerName.contains("screen_shot") ||
                lowerBucket.contains("screenshot") ||
                lowerBucket.contains("ảnh chụp màn hình") ||
                lowerBucket.contains("chụp màn hình") ||
                lowerPath.contains("screenshot") ||
                lowerPath.contains("screencap")
    }

    /**
     * Enqueues all new screenshots into SQLite with status 'PENDING'.
     * Catches everything newer than cutoff (up to 50 items) without processing heavy ML on the observer thread.
     * Drained asynchronously and reliably by ScreenshotQueueWorker.
     */
    suspend fun enqueueNewScreenshots(context: Context): Int = withContext(Dispatchers.IO) {
        val dbHelper = AppDatabaseHelper.getInstance(context)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val now = System.currentTimeMillis()
        // Window of 24 hours to catch screenshots taken while phone was off or app was suspended
        val cutoffMillis = now - (24 * 3600 * 1000L)

        var enqueuedCount = 0

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                var inspectCount = 0
                while (cursor.moveToNext() && inspectCount < 50) {
                    inspectCount++

                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: ""
                    val dateAddedSec = cursor.getLong(dateCol)
                    val dateAddedMillis = if (dateAddedSec > 0) dateAddedSec * 1000L else now
                    val bucket = if (bucketCol >= 0) cursor.getString(bucketCol) ?: "" else ""
                    val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""

                    val isScreenshot = isScreenshotMedia(name, bucket, path)
                    val isRecent = dateAddedMillis >= cutoffMillis
                    val photoId = "device_$id"

                    if (isScreenshot && isRecent && !dbHelper.isScreenshotProcessedOrNotified(photoId)) {
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        ).toString()

                        // Distinguish live real-time capture (within 3 minutes of now) vs historical 24h backfill
                        val isLive = kotlin.math.abs(now - dateAddedMillis) <= 3 * 60 * 1000L
                        val enqueued = dbHelper.enqueueScreenshot(
                            photoId = photoId,
                            filePath = contentUri,
                            dateAdded = dateAddedMillis,
                            isLiveCapture = isLive,
                            photoTitle = name.substringBeforeLast(".").ifBlank { null },
                            originalFileName = name.ifBlank { null }
                        )
                        if (enqueued) {
                            enqueuedCount++
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        if (enqueuedCount > 0) {
            ScreenshotQueueWorker.enqueue(context)
        }

        enqueuedCount
    }

    /**
     * Backward-compatible delegation: enqueues new screenshots and starts worker.
     */
    suspend fun checkAndProcessScreenshots(context: Context): Int {
        return enqueueNewScreenshots(context)
    }
}
