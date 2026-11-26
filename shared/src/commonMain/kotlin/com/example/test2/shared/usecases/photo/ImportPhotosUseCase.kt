package com.example.test2.shared.usecases.photo

import com.example.test2.shared.repository.IPhotoRepository
import com.example.test2.shared.usecases.PhotoImportPort
import com.example.test2.shared.usecases.QueueSyncPort

/**
 * Use case for importing external photos, encrypting them locally, and triggering AI processing.
 */
class ImportPhotosUseCase(
    private val repository: IPhotoRepository,
    private val importPort: PhotoImportPort,
    private val queueSyncPort: QueueSyncPort
) {
    suspend operator fun invoke(
        uriStrings: List<String>,
        targetFolderId: String?,
        targetCategoryIds: Set<String>,
        baseTimestampMillis: Long
    ) {
        if (uriStrings.isEmpty()) return

        for ((index, uriString) in uriStrings.withIndex()) {
            val stored = importPort.saveImageFromUriString(uriString)
            if (stored != null) {
                val title = stored.fileName.substringBeforeLast(".")
                val initialCategories = if (targetCategoryIds.isEmpty()) {
                    importPort.inferInitialCategories(targetFolderId, title)
                } else {
                    targetCategoryIds
                }
                val photo = repository.addPhoto(
                    fileName = stored.fileName,
                    filePath = stored.filePath,
                    title = title,
                    folderId = targetFolderId,
                    categoryIds = initialCategories,
                    isScreenshot = true,
                    dateAdded = baseTimestampMillis + index,
                    fileSizeBytes = stored.fileSizeBytes
                )

                val nowScan = baseTimestampMillis + index
                if (repository.canUseAiScan(nowScan) && !importPort.isJunkOrMeme(photo)) {
                    repository.incrementQuotaUsage(nowScan)
                    queueSyncPort.enqueueScreenshot(
                        photoId = photo.id,
                        filePath = photo.filePath,
                        dateAdded = photo.dateAdded,
                        isLiveCapture = false,
                        photoTitle = photo.title,
                        originalFileName = photo.fileName
                    )
                    importPort.processWithCloud(photo, targetFolderId)
                } else {
                    importPort.processOnDevice(photo, targetFolderId, targetCategoryIds)
                }
            }
        }
    }
}
