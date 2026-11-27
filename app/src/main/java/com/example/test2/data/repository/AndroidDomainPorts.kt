package com.example.test2.data.repository

import android.content.Context
import android.net.Uri
import com.example.test2.PhotoApp
import com.example.test2.ai.CloudAiSyncNotifier
import com.example.test2.ai.OnDeviceAnalysis
import com.example.test2.ai.PhotoProcessingCoordinator
import com.example.test2.ai.RetroactiveCategorizer
import com.example.test2.ai.SemanticSearchCoordinator
import com.example.test2.ai.SmartHeuristicParser
import com.example.test2.ai.cards.CardIds
import com.example.test2.ai.cards.QualityFlags
import com.example.test2.data.local.dao.ActionCardDao
import com.example.test2.data.local.dao.FtsSearchDao
import com.example.test2.data.local.dao.ScreenshotQueueDao
import com.example.test2.data.local.dao.VectorDao
import com.example.test2.data.local.prefs.CloudAiPrefsDataSource
import com.example.test2.data.remote.GeminiApiService
import com.example.test2.security.DeviceAuthManager
import com.example.test2.security.IVaultManager
import com.example.test2.services.AgingJobScheduler
import com.example.test2.services.AgingMonitorManager
import com.example.test2.services.ScreenshotJobScheduler
import com.example.test2.shared.models.ActionCard
import com.example.test2.shared.models.ActionCardType
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Photo
import com.example.test2.shared.usecases.ActionCardPort
import com.example.test2.shared.usecases.AgingMonitorPort
import com.example.test2.shared.usecases.BackgroundSyncPort
import com.example.test2.shared.usecases.CloudAiConfigData
import com.example.test2.shared.usecases.CloudAiConfigPort
import com.example.test2.shared.usecases.DeviceScanOutput
import com.example.test2.shared.usecases.DeviceSecurityPort
import com.example.test2.shared.usecases.FtsIndexPort
import com.example.test2.shared.usecases.ImportedFileResult
import com.example.test2.shared.usecases.MediaObserverPort
import com.example.test2.shared.usecases.MediaScannerPort
import com.example.test2.shared.usecases.MetadataSyncPort
import com.example.test2.shared.usecases.PhotoImportPort
import com.example.test2.shared.usecases.PhotoProcessingPort
import com.example.test2.shared.usecases.PhotoStorageCleanerPort
import com.example.test2.shared.usecases.QueueSyncPort
import com.example.test2.shared.usecases.RestoredFileResult
import com.example.test2.shared.usecases.RetroactiveCategorizerPort
import com.example.test2.shared.usecases.SearchOutput
import com.example.test2.shared.usecases.SemanticSearchPort
import com.example.test2.shared.usecases.VaultPort
import com.example.test2.shared.usecases.VaultedFileResult
import com.example.test2.shared.usecases.WidgetPort
import com.example.test2.storage.AndroidPhotoStorage
import com.example.test2.storage.DeviceMediaObserver
import com.example.test2.storage.DeviceMediaScanner
import com.example.test2.util.AppLogger
import com.example.test2.widget.PhotoSearchWidgetProvider
import com.example.test2.widget.PhotoSearchWidgetUpdater
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

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
        val res = vaultManager.vaultAndEvictScreenshot(sourceUriString, sourcePath, originalFileName) ?: return null
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

    override fun updatePhotosCategoryInQueue(
        photoIds: Collection<String>,
        targetCategoryId: String?,
        lockCategory: Boolean
    ) = queueDao.updatePhotosCategoryInQueue(
        photoIds = photoIds,
        targetCategoryId = targetCategoryId,
        lockCategory = lockCategory
    )

    override fun clearCategoryInQueue(categoryId: String) =
        queueDao.clearCategoryInQueue(categoryId)
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
    private val queueDao: ScreenshotQueueDao,
    private val cloudAiPrefs: CloudAiPrefsDataSource,
    private val geminiApiService: GeminiApiService
) : RetroactiveCategorizerPort {
    override suspend fun findMatchingPhotosForCategory(category: Category, photos: List<Photo>): List<String> {
        val config = cloudAiPrefs.getConfig()
        return RetroactiveCategorizer.findMatchingPhotosForCategory(
            category = category,
            photos = photos,
            apiKey = config.apiKey,
            context = context,
            vectorDao = vectorDao,
            ftsDao = ftsDao,
            queueDao = queueDao,
            geminiApiService = geminiApiService,
            isCloudAvailable = config.isCloudReady()
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

    override suspend fun checkAgingIfDue(): Triple<Long, Int, Long>? {
        return AgingMonitorManager.checkAgingIfDue(context)?.let {
            AgingMonitorManager.getLastCheckedInfo(context)
        }
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

class AndroidActionCardAdapter(
    private val context: Context,
    private val actionCardDao: ActionCardDao
) : ActionCardPort {
    override fun getActionCardsForPhoto(photoId: String): List<ActionCard> =
        actionCardDao.getActionCardsForPhoto(photoId)

    override fun getPhotoIdsByActionCardType(type: ActionCardType): Set<String> =
        actionCardDao.getPhotoIdsByActionCardType(type)

    override suspend fun reextractCardsIfNeeded(photo: Photo): List<ActionCard>? {
        if (!QualityFlags.REEXTRACT_ON_OPEN) return null
        val existing = actionCardDao.getActionCardsForPhoto(photo.id)
        val needsExtraction = existing.isEmpty() || existing.any { card ->
            val ev = try {
                JSONObject(card.payloadJson).optInt("ev", 1)
            } catch (e: Exception) {
                AppLogger.w("AndroidActionCardAdapter", "Invalid card payload version for ${card.id}", e)
                1
            }
            ev < CardIds.EXTRACTOR_VERSION
        }
        if (!needsExtraction) return null

        val result = OnDeviceAnalysis.analyze(
            context = context,
            photoId = photo.id,
            filePath = photo.filePath,
            photoTitle = photo.title
        ) ?: return null

        actionCardDao.replaceActionCardsForPhoto(photo.id, result.cards)
        CloudAiSyncNotifier.notifyBatchCompleted(listOf(photo.id))
        return result.cards
    }

    override fun observeCardUpdates(): Flow<List<String>> = CloudAiSyncNotifier.syncEvents
}

class AndroidPhotoProcessingAdapter(
    private val processingCoordinator: PhotoProcessingCoordinator
) : PhotoProcessingPort {
    override suspend fun scanPhotoWithCloud(photo: Photo, targetFolderId: String?) {
        processingCoordinator.scanPhotoWithCloud(photo, targetFolderId)
    }
}

class AndroidWidgetAdapter(
    private val context: Context
) : WidgetPort {
    private val appContext = context.applicationContext

    override fun cacheIsProAndUpdate(isPro: Boolean) {
        PhotoSearchWidgetProvider.cacheIsPro(appContext, isPro)
        PhotoSearchWidgetUpdater.updateAll(appContext)
    }

    override fun updateAll() {
        PhotoSearchWidgetUpdater.updateAll(appContext)
    }
}

class AndroidDeviceSecurityAdapter(
    private val context: Context
) : DeviceSecurityPort {
    private val appContext = context.applicationContext
    override fun isDeviceSecure(): Boolean = DeviceAuthManager.isDeviceSecure(appContext)
}

class AndroidCloudAiConfigAdapter(
    private val prefsDataSource: CloudAiPrefsDataSource
) : CloudAiConfigPort {
    override fun getConfig(): CloudAiConfigData {
        val cfg = prefsDataSource.getConfig()
        return CloudAiConfigData(
            apiKey = cfg.apiKey,
            modelName = cfg.modelName,
            customEndpoint = cfg.customEndpoint,
            isEnabled = cfg.isEnabled,
            proxyBaseUrl = cfg.proxyBaseUrl
        )
    }

    override fun saveConfig(apiKey: String, modelName: String, customEndpoint: String, enabled: Boolean) {
        prefsDataSource.saveConfig(apiKey, modelName, customEndpoint, enabled)
    }
}

class AndroidBackgroundSyncAdapter(
    private val context: Context,
    private val queueDao: ScreenshotQueueDao
) : BackgroundSyncPort {
    private val appContext = context.applicationContext

    override fun scheduleBackgroundJobs() {
        AgingJobScheduler.schedule(appContext)
        ScreenshotJobScheduler.schedule(appContext)
    }

    override fun resetHangingQueueItems() {
        queueDao.resetHangingProcessingItems()
    }
}

class AndroidMediaObserverAdapter(
    private val context: Context,
    private val vaultManager: IVaultManager,
    onMediaChanged: () -> Unit
) : MediaObserverPort {
    private val observer = DeviceMediaObserver(context, vaultManager, onMediaChanged)
    override fun register() = observer.register()
    override fun unregister() = observer.unregister()
}

object AndroidMetadataSyncAdapter : MetadataSyncPort {
    override fun markMetadataDirty() = PhotoApp.markMetadataDirty()
    override fun consumeMetadataDirty(): Boolean = PhotoApp.consumeMetadataDirty()
}
