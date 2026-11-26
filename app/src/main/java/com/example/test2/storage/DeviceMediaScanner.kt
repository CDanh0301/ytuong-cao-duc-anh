package com.example.test2.storage

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.example.test2.shared.models.Folder
import com.example.test2.shared.models.Photo
import com.example.test2.shared.repository.PhotoRepository
import com.example.test2.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceScanResult(
    val photos: List<Photo>,
    val folders: List<Folder>,
    val physicalPaths: Map<String, String> = emptyMap()
)

class DeviceMediaScanner(private val context: Context) {

    companion object {
        fun resolveMediaTimestampMillis(
            dateTakenMillis: Long,
            dateAddedSec: Long,
            dateModifiedSec: Long,
            fallbackNowMillis: Long = System.currentTimeMillis()
        ): Long {
            return when {
                dateTakenMillis > 0L -> dateTakenMillis
                dateAddedSec > 0L -> dateAddedSec * 1000L
                dateModifiedSec > 0L -> dateModifiedSec * 1000L
                else -> fallbackNowMillis
            }
        }
    }

    suspend fun scanDeviceImages(): DeviceScanResult = withContext(Dispatchers.IO) {
        val photos = mutableListOf<Photo>()
        val folderNames = mutableSetOf<String>()
        val physicalPaths = mutableMapOf<String, String>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC"

        try {
            val query = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            query?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val dateTakenColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val dateModifiedColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val bucketColumn = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dataColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "IMG_$id.jpg"
                    val dateAddedSeconds = cursor.getLong(dateColumn)
                    val dateTakenMillis = if (dateTakenColumn >= 0 && !cursor.isNull(dateTakenColumn)) {
                        cursor.getLong(dateTakenColumn)
                    } else 0L
                    val dateModifiedSeconds = if (dateModifiedColumn >= 0 && !cursor.isNull(dateModifiedColumn)) {
                        cursor.getLong(dateModifiedColumn)
                    } else 0L
                    val dateAddedMillis = resolveMediaTimestampMillis(
                        dateTakenMillis = dateTakenMillis,
                        dateAddedSec = dateAddedSeconds,
                        dateModifiedSec = dateModifiedSeconds
                    )
                    val size = cursor.getLong(sizeColumn)
                    val bucketName = if (bucketColumn >= 0) cursor.getString(bucketColumn) else null
                    val cleanFolder = if (bucketName.isNullOrBlank()) "Ảnh chụp màn hình" else bucketName.trim()
                    val dataPath = if (dataColumn >= 0) cursor.getString(dataColumn) ?: "" else ""

                    // Phase 1: Only auto-scan and accept SCREENSHOT media from device storage
                    val isScreenshot = isScreenshotMedia(name, cleanFolder, dataPath)
                    if (!isScreenshot) {
                        continue
                    }

                    folderNames.add(cleanFolder)

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    val title = name.substringBeforeLast(".")
                    val photoId = "device_$id"
                    if (dataPath.isNotBlank()) {
                        physicalPaths[photoId] = dataPath
                    }

                    photos.add(
                        Photo(
                            id = photoId,
                            fileName = name,
                            filePath = contentUri,
                            title = title,
                            description = "",
                            folderId = cleanFolder, // mapped during sync
                            categoryIds = emptySet(),
                            isFavorite = false,
                            dateAdded = dateAddedMillis,
                            fileSizeBytes = size,
                            isScreenshot = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            AppLogger.w("DeviceMediaScanner", "Failed to scan device images", e)
        }

        // Generate Folder models from discovered album buckets
        val folders = folderNames.mapIndexed { index, name ->
            val icon = when {
                name.contains("camera", ignoreCase = true) -> "camera"
                isScreenshotFolderName(name) -> "folder"
                name.contains("download", ignoreCase = true) -> "folder"
                name.contains("favorite", ignoreCase = true) -> "heart"
                name.contains("travel", ignoreCase = true) -> "plane"
                name.contains("work", ignoreCase = true) || name.contains("doc", ignoreCase = true) -> "briefcase"
                else -> "folder"
            }
            val color = when (index % 6) {
                0 -> "#3B82F6"
                1 -> "#10B981"
                2 -> "#8B5CF6"
                3 -> "#F59E0B"
                4 -> "#EC4899"
                else -> "#06B6D4"
            }
            Folder(
                id = "folder_auto_${name.lowercase().replace(" ", "_")}",
                name = name,
                colorHex = color,
                iconName = icon,
                isDefault = isScreenshotFolderName(name) || name.equals("Cuộn camera", ignoreCase = true) || name.equals("Camera Roll", ignoreCase = true),
                createdAt = System.currentTimeMillis() - (index * 1000L)
            )
        }

        DeviceScanResult(
            photos = photos.sortedWith(PhotoRepository.chronologicalDescComparator),
            folders = folders,
            physicalPaths = physicalPaths
        )
    }

    private fun isScreenshotMedia(name: String, bucket: String, path: String): Boolean {
        val lower = "$name $bucket $path".lowercase()
        return lower.contains("screenshot") ||
                lower.contains("screencap") ||
                lower.contains("screen_shot") ||
                lower.contains("ảnh chụp màn hình") ||
                lower.contains("chụp màn hình") ||
                lower.contains("chup man hinh") ||
                lower.contains("capture")
    }

    private fun isScreenshotFolderName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("screenshot") ||
                lower.contains("ảnh chụp màn hình") ||
                lower.contains("chụp màn hình") ||
                lower.contains("chup man hinh")
    }
}
