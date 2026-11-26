package com.example.test2.data.repository

import android.content.Context
import android.net.Uri
import com.example.test2.ai.CloudAiConfigManager
import com.example.test2.ai.PhotoProcessingCoordinator
import com.example.test2.ai.RetroactiveCategorizer
import com.example.test2.ai.SemanticSearchCoordinator
import com.example.test2.ai.SmartHeuristicParser
import com.example.test2.data.local.dao.FtsSearchDao
import com.example.test2.data.local.dao.ScreenshotQueueDao
import com.example.test2.data.local.dao.VectorDao
import com.example.test2.security.IVaultManager
import com.example.test2.services.AgingMonitorManager
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Photo
import com.example.test2.shared.usecases.AgingMonitorPort
import com.example.test2.shared.usecases.DeviceScanOutput
import com.example.test2.shared.usecases.FtsIndexPort
import com.example.test2.shared.usecases.ImportedFileResult
import com.example.test2.shared.usecases.MediaScannerPort
import com.example.test2.shared.usecases.PhotoImportPort
import com.example.test2.shared.usecases.PhotoStorageCleanerPort
import com.example.test2.shared.usecases.QueueSyncPort
import com.example.test2.shared.usecases.RestoredFileResult
import com.example.test2.shared.usecases.RetroactiveCategorizerPort
import com.example.test2.shared.usecases.SearchOutput
import com.example.test2.shared.usecases.SemanticSearchPort
import com.example.test2.shared.usecases.VaultPort
import com.example.test2.shared.usecases.VaultedFileResult
import com.example.test2.storage.AndroidPhotoStorage
import com.example.test2.storage.DeviceMediaScanner

class AndroidMediaScannerAdapter(
    private val scanner: DeviceMediaScanner
) : MediaScannerPort {
    override suspend fun scanDeviceImages(): DeviceScanOutput {
        val result = scanner.scanDeviceImages()
        return DeviceScanOutput(
            photos = result.photos,
            folders = result.folders,
            physicalPaths = result.physicalPaths
        )
    }
}

class AndroidVaultPortAdapter(
    private val vaultManager: IVaultManager
) : VaultPort {
    override fun isVaultEnabled(): Boolean = vaultManager.isVaultEnabled()
    override fun setVaultEnabled(enabled: Boolean) = vaultManager.setVaultEnabled(enabled)
    override fun hasAllFilesAccess(): Boolean = vaultManager.hasAllFilesAccess()
    override fun shouldAutoVaultPhoto(photoId: String, dateAddedMillis: Long): Boolean =
        vaultManager.shouldAutoVaultPhoto(photoId, dateAddedMillis)
    override fun beginInternalEviction() = vaultManager.beginInternalEviction()
    override fun endInternalEviction() = vaultManager.endInternalEviction()
    override fun markRestoredExempt(photoIdOrMediaId: String) = vaultManager.markRestoredExempt(photoIdOrMediaId)
    override fun clearRestoredExempt(photoIdOrMediaId: String) = vaultManager.clearRestoredExempt(photoIdOrMediaId)
    override fun clearAllRestoredExempts() = vaultManager.clearAllRestoredExempts()
    override fun sanitizeRestoreFileName(rawTitle: String, encryptedFilePath: String, fallbackFileName: String): String =
        vaultManager.sanitizeRestoreFileName(rawTitle, encryptedFilePath, fallbackFileName)

    override suspend fun vaultAndEvictScreenshot(
        sourceUriString: String?,
        sourcePath: String,
        originalFileName: String
    ): VaultedFileResult? {
        val uri = sourceUriString?.let { Uri.parse(it) }
        val res = vaultManager.vaultAndEvictScreenshot(uri, sourcePath, originalFileName) ?: return null
        return VaultedFileResult(
            newFilePath = res.newFilePath,
            newFileName = res.newFileName,
            fileSizeBytes = res.fileSizeBytes,
            isOriginalEvicted = res.isOriginalEvicted
        )
    }

    override suspend fun restoreAndEvictScreenshot(
        vaultedFilePath: String,
        originalFileName: String,
        originalDateAddedMillis: Long?,
        markExemptOnRestore: Boolean
    ): RestoredFileResult? {
        val res = vaultManager.restoreAndEvictScreenshot(
            vaultedFilePath = vaultedFilePath,
            originalFileName = originalFileName,
            originalDateAddedMillis = originalDateAddedMillis,
            markExemptOnRestore = markExemptOnRestore
        ) ?: return null
        return RestoredFileResult(
            newUri = res.newUri,
            newFilePath = res.newFilePath,
            newFileName = res.newFileName,
            fileSizeBytes = res.fileSizeBytes,
            newMediaId = res.newMediaId
        )
    }

    override fun hasRemainingEncryptedFiles(): Boolean = vaultManager.hasRemainingEncryptedFiles()
}

class AndroidQueueSyncAdapter(
    private val queueDao: ScreenshotQueueDao
) : QueueSyncPort {
    override fun syncVaultedPathToQueue(
        photoId: String,
        newFilePath: String,
        dateAdded: Long,
        photoTitle: String?,
        originalFileName: String?
    ): Boolean = queueDao.syncVaultedPathToQueue(photoId, newFilePath, dateAdded, photoTitle, originalFileName)

    override fun migratePhotoRecords(
        oldPhotoId: String,
        newPhotoId: String,
        newFilePath: String,
        dateAdded: Long,
        scrubbedText: String?,
        detectedIntent: String?,
        eventTitle: String?,
        eventDateMillis: Long?,
        targetCategoryId: String?,
        eventDescription: String?,
        photoTitle: String?,
        originalFileName: String?
    ) = queueDao.migratePhotoRecords(
        oldPhotoId = oldPhotoId,
        newPhotoId = newPhotoId,
        newFilePath = newFilePath,
        dateAdded = dateAdded,
        scrubbedText = scrubbedText,
        detectedIntent = detectedIntent,
        eventTitle = eventTitle,
        eventDateMillis = eventDateMillis,
        targetCategoryId = targetCategoryId,
        eventDescription = eventDescription,
        photoTitle = photoTitle,
        originalFileName = originalFileName
    )

    override fun enqueueScreenshot(
        photoId: String,
        filePath: String,
        dateAdded: Long,
        isLiveCapture: Boolean,
        photoTitle: String?,
        originalFileName: String?
    ): Boolean = queueDao.enqueueScreenshot(
        photoId = photoId,
        filePath = filePath,
        dateAdded = dateAdded,
        isLiveCapture = isLiveCapture,
        photoTitle = photoTitle,
        originalFileName = originalFileName
    )
}

class AndroidPhotoStorageCleanerAdapter(
    private val storage: AndroidPhotoStorage,
    private val queueDao: ScreenshotQueueDao
) : PhotoStorageCleanerPort {
    override suspend fun deletePhysicalFile(filePath: String): Boolean = storage.deletePhysicalFile(filePath)
    override fun purgePhotoData(photoId: String) = queueDao.purgePhotoData(photoId)
}

class AndroidSemanticSearchAdapter(
    private val coordinator: SemanticSearchCoordinator
) : SemanticSearchPort {
    override suspend fun search(query: String): SearchOutput {
        val result = coordinator.search(query)
        return SearchOutput(
            matchingIds = result.matchingIds,
            dateRange = result.dateRange,
            semanticMatchScores = result.semanticMatchScores
        )
    }

    override suspend fun ensureVectorsIndexed(forceReindex: Boolean) {
        coordinator.ensureVectorsIndexed(forceReindex)
    }
}

class AndroidRetroactiveCategorizerAdapter(
    private val context: Context,
    private val vectorDao: VectorDao,
    private val ftsDao: FtsSearchDao,
    private val queueDao: ScreenshotQueueDao
) : RetroactiveCategorizerPort {
    override suspend fun findMatchingPhotosForCategory(category: Category, photos: List<Photo>): List<String> {
        val apiKey = CloudAiConfigManager.getApiKey(context)
        return RetroactiveCategorizer.findMatchingPhotosForCategory(
            category = category,
            photos = photos,
            apiKey = apiKey,
            context = context,
            vectorDao = vectorDao,
            ftsDao = ftsDao,
            queueDao = queueDao
        )
    }
}

class AndroidFtsIndexAdapter(
    private val ftsDao: FtsSearchDao
) : FtsIndexPort {
    override fun indexPhotoFts(
        photoId: String,
        title: String,
        scrubbedText: String,
        intent: String,
        categoryNames: String,
        semanticTags: String
    ) {
        ftsDao.indexPhotoFts(
            photoId = photoId,
            title = title,
            scrubbedText = scrubbedText,
            intent = intent,
            categoryNames = categoryNames,
            semanticTags = semanticTags
        )
    }
}

class AndroidAgingMonitorAdapter(
    private val context: Context
) : AgingMonitorPort {
    override suspend fun checkAgingAndNotify(force: Boolean): Triple<Long, Int, Long> {
        AgingMonitorManager.checkAgingAndNotify(context, force)
        return AgingMonitorManager.getLastCheckedInfo(context)
    }

    override fun getLastCheckedInfo(): Triple<Long, Int, Long> =
        AgingMonitorManager.getLastCheckedInfo(context)
}

class AndroidPhotoImportAdapter(
    private val storage: AndroidPhotoStorage,
    private val processingCoordinator: PhotoProcessingCoordinator
) : PhotoImportPort {
    override suspend fun saveImageFromUriString(uriString: String): ImportedFileResult? {
        val stored = storage.saveImageFromUri(Uri.parse(uriString)) ?: return null
        return ImportedFileResult(
            fileName = stored.fileName,
            filePath = stored.filePath,
            fileSizeBytes = stored.fileSizeBytes
        )
    }

    override fun inferInitialCategories(targetFolderId: String?, title: String): Set<String> {
        return SmartHeuristicParser.parse("", "${targetFolderId ?: ""} $title").targetCategoryId?.let { setOf(it) } ?: emptySet()
    }

    override fun isJunkOrMeme(photo: Photo): Boolean = SmartHeuristicParser.isJunkOrMeme(photo)

    override suspend fun processWithCloud(photo: Photo, targetFolderId: String?) {
        processingCoordinator.scanPhotoWithCloud(photo, targetFolderId)
    }

    override suspend fun processOnDevice(photo: Photo, targetFolderId: String?, targetCategoryIds: Set<String>) {
        processingCoordinator.runOnDevicePipeline(photo, targetFolderId, targetCategoryIds)
    }
}
