package com.example.test2.shared.usecases.photo

import com.example.test2.shared.models.Photo
import com.example.test2.shared.models.PhotoComparators
import com.example.test2.shared.repository.IPhotoRepository
import com.example.test2.shared.usecases.QueueSyncPort
import com.example.test2.shared.usecases.VaultPort
import com.example.test2.shared.usecases.VaultedFileResult
import kotlinx.coroutines.delay

data class RestoreSingleResult(
    val newPhotoId: String,
    val vaultStillEnabled: Boolean
)

data class RestoreAllResult(
    val successCount: Int,
    val failedCount: Int,
    val vaultEnabled: Boolean
)

/**
 * Use case encapsulating single-photo and batch Privacy Vault encryption and restoration flows.
 */
class VaultPhotoUseCase(
    private val vaultPort: VaultPort,
    private val queueSyncPort: QueueSyncPort,
    private val repository: IPhotoRepository
) {
    suspend fun vaultPhoto(photo: Photo): Result<VaultedFileResult> {
        if (!vaultPort.hasAllFilesAccess()) {
            return Result.failure(IllegalStateException("All files access permission required"))
        }
        repository.awaitInitialLoad()
        vaultPort.beginInternalEviction()
        return try {
            vaultPort.clearRestoredExempt(photo.id)
            val uriStr = if (photo.filePath.startsWith("content://")) photo.filePath else null
            val vaulted = vaultPort.vaultAndEvictScreenshot(
                sourceUriString = uriStr,
                sourcePath = photo.filePath,
                originalFileName = photo.fileName
            )
            if (vaulted != null) {
                queueSyncPort.syncVaultedPathToQueue(
                    photoId = photo.id,
                    newFilePath = vaulted.newFilePath,
                    dateAdded = photo.dateAdded,
                    photoTitle = photo.title,
                    originalFileName = photo.fileName
                )
                repository.updatePhotoStoragePath(
                    photoId = photo.id,
                    newFilePath = vaulted.newFilePath,
                    newFileName = vaulted.newFileName,
                    newFileSizeBytes = vaulted.fileSizeBytes
                )
                vaultPort.setVaultEnabled(true)
                Result.success(vaulted)
            } else {
                Result.failure(IllegalStateException("Failed to vault photo"))
            }
        } finally {
            delay(300)
            vaultPort.endInternalEviction()
        }
    }

    suspend fun restorePhoto(photo: Photo): Result<RestoreSingleResult> {
        repository.awaitInitialLoad()
        vaultPort.beginInternalEviction()
        return try {
            val targetRestoreName = vaultPort.sanitizeRestoreFileName(
                rawTitle = photo.title,
                encryptedFilePath = photo.filePath,
                fallbackFileName = photo.fileName
            )
            val result = vaultPort.restoreAndEvictScreenshot(
                vaultedFilePath = photo.filePath,
                originalFileName = targetRestoreName,
                originalDateAddedMillis = photo.dateAdded,
                markExemptOnRestore = true
            ) ?: return Result.failure(IllegalStateException("Failed to restore photo"))

            val newPhotoId = "device_${result.newMediaId}"
            vaultPort.markRestoredExempt(newPhotoId)
            queueSyncPort.migratePhotoRecords(
                oldPhotoId = photo.id,
                newPhotoId = newPhotoId,
                newFilePath = result.newUri,
                dateAdded = photo.dateAdded,
                scrubbedText = photo.piiScrubbedText,
                detectedIntent = photo.detectedIntent,
                eventTitle = photo.suggestedEventTitle,
                eventDateMillis = photo.suggestedEventDateMillis,
                targetCategoryId = photo.categoryIds.firstOrNull(),
                eventDescription = photo.suggestedEventDescription,
                photoTitle = photo.title,
                originalFileName = result.newFileName
            )
            repository.updatePhotoStoragePath(
                photoId = photo.id,
                newFilePath = result.newUri,
                newFileName = result.newFileName,
                newFileSizeBytes = result.fileSizeBytes,
                newPhotoId = newPhotoId
            )

            val remainingVaulted = repository.photos.value.count {
                it.id != newPhotoId && it.id != photo.id &&
                    (it.filePath.endsWith(".enc") || (it.isScreenshot && !it.filePath.startsWith("content://")))
            }
            val hasRemainingEncFiles = vaultPort.hasRemainingEncryptedFiles()
            val vaultStillEnabled = if (remainingVaulted == 0 && !hasRemainingEncFiles) {
                vaultPort.setVaultEnabled(false)
                vaultPort.clearAllRestoredExempts()
                false
            } else {
                vaultPort.isVaultEnabled()
            }
            Result.success(RestoreSingleResult(newPhotoId = newPhotoId, vaultStillEnabled = vaultStillEnabled))
        } finally {
            delay(350)
            vaultPort.endInternalEviction()
        }
    }

    suspend fun restoreAllPhotos(onProgress: (current: Int, total: Int) -> Unit): Result<RestoreAllResult> {
        repository.awaitInitialLoad()
        val currentPhotos = repository.photos.value
        val vaultedList = currentPhotos.filter {
            it.filePath.endsWith(".enc") || (it.isScreenshot && !it.filePath.startsWith("content://"))
        }.sortedWith(PhotoComparators.chronologicalAscComparator)

        if (vaultedList.isEmpty()) {
            vaultPort.setVaultEnabled(false)
            vaultPort.clearAllRestoredExempts()
            return Result.success(RestoreAllResult(successCount = 0, failedCount = 0, vaultEnabled = false))
        }

        onProgress(0, vaultedList.size)

        val wasVaultEnabled = vaultPort.isVaultEnabled()
        vaultPort.setVaultEnabled(false)

        var successCount = 0
        var failedCount = 0
        val restoredNewIds = mutableListOf<String>()

        vaultPort.beginInternalEviction()
        try {
            for (photo in vaultedList) {
                val targetRestoreName = vaultPort.sanitizeRestoreFileName(
                    rawTitle = photo.title,
                    encryptedFilePath = photo.filePath,
                    fallbackFileName = photo.fileName
                )
                val result = vaultPort.restoreAndEvictScreenshot(
                    vaultedFilePath = photo.filePath,
                    originalFileName = targetRestoreName,
                    originalDateAddedMillis = photo.dateAdded,
                    markExemptOnRestore = false
                )
                if (result != null) {
                    val newPhotoId = "device_${result.newMediaId}"
                    restoredNewIds.add(newPhotoId)
                    queueSyncPort.migratePhotoRecords(
                        oldPhotoId = photo.id,
                        newPhotoId = newPhotoId,
                        newFilePath = result.newUri,
                        dateAdded = photo.dateAdded,
                        scrubbedText = photo.piiScrubbedText,
                        detectedIntent = photo.detectedIntent,
                        eventTitle = photo.suggestedEventTitle,
                        eventDateMillis = photo.suggestedEventDateMillis,
                        targetCategoryId = photo.categoryIds.firstOrNull(),
                        eventDescription = photo.suggestedEventDescription,
                        photoTitle = photo.title,
                        originalFileName = result.newFileName
                    )
                    repository.updatePhotoStoragePath(
                        photoId = photo.id,
                        newFilePath = result.newUri,
                        newFileName = result.newFileName,
                        newFileSizeBytes = result.fileSizeBytes,
                        newPhotoId = newPhotoId
                    )
                    successCount++
                } else {
                    failedCount++
                }
                onProgress(successCount + failedCount, vaultedList.size)
            }
        } finally {
            delay(350)
            vaultPort.endInternalEviction()
        }

        val finalVaultEnabled = if (failedCount == 0) {
            vaultPort.setVaultEnabled(false)
            vaultPort.clearAllRestoredExempts()
            false
        } else {
            for (restoredId in restoredNewIds) {
                vaultPort.markRestoredExempt(restoredId)
            }
            vaultPort.setVaultEnabled(wasVaultEnabled)
            wasVaultEnabled
        }

        return Result.success(
            RestoreAllResult(
                successCount = successCount,
                failedCount = failedCount,
                vaultEnabled = finalVaultEnabled
            )
        )
    }
}
