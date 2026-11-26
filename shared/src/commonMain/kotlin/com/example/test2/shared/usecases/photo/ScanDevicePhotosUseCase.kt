package com.example.test2.shared.usecases.photo

import com.example.test2.shared.repository.IPhotoRepository
import com.example.test2.shared.usecases.DeviceScanOutput
import com.example.test2.shared.usecases.MediaScannerPort
import com.example.test2.shared.usecases.QueueSyncPort
import com.example.test2.shared.usecases.VaultPort
import kotlinx.coroutines.delay

/**
 * Use case for scanning device MediaStore screenshots, auto-vaulting eligible items when
 * Privacy Vault is enabled, and synchronizing with [IPhotoRepository].
 */
class ScanDevicePhotosUseCase(
    private val mediaScanner: MediaScannerPort,
    private val repository: IPhotoRepository,
    private val vaultPort: VaultPort,
    private val queueSyncPort: QueueSyncPort
) {
    suspend operator fun invoke(): DeviceScanOutput {
        repository.awaitInitialLoad()
        val scanResult = mediaScanner.scanDeviceImages()
        if (scanResult.photos.isEmpty() && scanResult.folders.isEmpty()) {
            return scanResult
        }

        val processedPhotos = if (vaultPort.isVaultEnabled() && vaultPort.hasAllFilesAccess()) {
            vaultPort.beginInternalEviction()
            try {
                scanResult.photos.map { photo ->
                    if (photo.isScreenshot &&
                        !photo.filePath.endsWith(".enc") &&
                        vaultPort.shouldAutoVaultPhoto(photo.id, photo.dateAdded)
                    ) {
                        val uriStr = if (photo.filePath.startsWith("content://")) photo.filePath else null
                        val physicalPath = scanResult.physicalPaths[photo.id] ?: photo.filePath
                        val vaulted = vaultPort.vaultAndEvictScreenshot(
                            sourceUriString = uriStr,
                            sourcePath = physicalPath,
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
                            photo.copy(
                                filePath = vaulted.newFilePath,
                                fileName = vaulted.newFileName,
                                fileSizeBytes = vaulted.fileSizeBytes
                            )
                        } else {
                            photo
                        }
                    } else {
                        photo
                    }
                }
            } finally {
                delay(300)
                vaultPort.endInternalEviction()
            }
        } else {
            scanResult.photos
        }

        repository.syncDevicePhotos(processedPhotos, scanResult.folders)
        return scanResult.copy(photos = processedPhotos)
    }
}
