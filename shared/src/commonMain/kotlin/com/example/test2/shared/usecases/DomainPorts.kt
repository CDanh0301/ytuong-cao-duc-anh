package com.example.test2.shared.usecases

import com.example.test2.shared.models.ActionCard
import com.example.test2.shared.models.ActionCardType
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Folder
import com.example.test2.shared.models.Photo
import kotlinx.coroutines.flow.Flow

data class DeviceScanOutput(
    val photos: List<Photo>,
    val folders: List<Folder>,
    val physicalPaths: Map<String, String> = emptyMap()
)

data class VaultedFileResult(
    val newFilePath: String,
    val newFileName: String,
    val fileSizeBytes: Long,
    val isOriginalEvicted: Boolean
)

data class RestoredFileResult(
    val newUri: String,
    val newFilePath: String,
    val newFileName: String,
    val fileSizeBytes: Long,
    val newMediaId: Long
)

data class SearchOutput(
    val matchingIds: Set<String>?,
    val dateRange: LongRange?,
    val semanticMatchScores: Map<String, Float>
)

data class ImportedFileResult(
    val fileName: String,
    val filePath: String,
    val fileSizeBytes: Long
)

interface MediaScannerPort {
    suspend fun scanDeviceImages(): DeviceScanOutput
}

interface VaultPort {
    fun isVaultEnabled(): Boolean
    fun setVaultEnabled(enabled: Boolean)
    fun hasAllFilesAccess(): Boolean
    fun shouldAutoVaultPhoto(photoId: String, dateAddedMillis: Long): Boolean
    fun beginInternalEviction()
    fun endInternalEviction()
    fun markRestoredExempt(photoIdOrMediaId: String)
    fun clearRestoredExempt(photoIdOrMediaId: String)
    fun clearAllRestoredExempts()
    fun sanitizeRestoreFileName(rawTitle: String, encryptedFilePath: String, fallbackFileName: String): String
    suspend fun vaultAndEvictScreenshot(
        sourceUriString: String?,
        sourcePath: String,
        originalFileName: String
    ): VaultedFileResult?
    suspend fun restoreAndEvictScreenshot(
        vaultedFilePath: String,
        originalFileName: String,
        originalDateAddedMillis: Long? = null,
        markExemptOnRestore: Boolean = false
    ): RestoredFileResult?
    fun hasRemainingEncryptedFiles(): Boolean
}

interface QueueSyncPort {
    fun syncVaultedPathToQueue(
        photoId: String,
        newFilePath: String,
        dateAdded: Long,
        photoTitle: String?,
        originalFileName: String?
    ): Boolean

    fun migratePhotoRecords(
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
    )

    fun enqueueScreenshot(
        photoId: String,
        filePath: String,
        dateAdded: Long,
        isLiveCapture: Boolean,
        photoTitle: String?,
        originalFileName: String?
    ): Boolean

    fun updatePhotosCategoryInQueue(
        photoIds: Collection<String>,
        targetCategoryId: String?,
        lockCategory: Boolean = true
    )

    fun clearCategoryInQueue(categoryId: String)
}

interface PhotoStorageCleanerPort {
    suspend fun deletePhysicalFile(filePath: String): Boolean
    fun purgePhotoData(photoId: String)
}

interface SemanticSearchPort {
    suspend fun search(query: String): SearchOutput
    suspend fun ensureVectorsIndexed(forceReindex: Boolean = false)
}

interface RetroactiveCategorizerPort {
    suspend fun findMatchingPhotosForCategory(category: Category, photos: List<Photo>): List<String>
}

interface FtsIndexPort {
    fun indexPhotoFts(
        photoId: String,
        title: String,
        scrubbedText: String,
        intent: String,
        categoryNames: String,
        semanticTags: String = ""
    )
}

interface AgingMonitorPort {
    suspend fun checkAgingAndNotify(force: Boolean): Triple<Long, Int, Long>
    suspend fun checkAgingIfDue(): Triple<Long, Int, Long>? = null
    fun getLastCheckedInfo(): Triple<Long, Int, Long>
}

interface PhotoImportPort {
    suspend fun saveImageFromUriString(uriString: String): ImportedFileResult?
    fun inferInitialCategories(targetFolderId: String?, title: String): Set<String>
    fun isJunkOrMeme(photo: Photo): Boolean
    suspend fun processWithCloud(photo: Photo, targetFolderId: String?)
    suspend fun processOnDevice(photo: Photo, targetFolderId: String?, targetCategoryIds: Set<String>)
}

data class CloudAiConfigData(
    val apiKey: String,
    val modelName: String,
    val customEndpoint: String,
    val isEnabled: Boolean,
    val proxyBaseUrl: String = ""
) {
    fun isCloudReady(): Boolean = isEnabled &&
        (proxyBaseUrl.isNotBlank() || customEndpoint.isNotBlank() || apiKey.isNotBlank())
}

interface ActionCardPort {
    fun getActionCardsForPhoto(photoId: String): List<ActionCard>
    fun getPhotoIdsByActionCardType(type: ActionCardType): Set<String>
    suspend fun reextractCardsIfNeeded(photo: Photo): List<ActionCard>?
    fun observeCardUpdates(): Flow<List<String>>
}

interface PhotoProcessingPort {
    suspend fun scanPhotoWithCloud(photo: Photo, targetFolderId: String? = null)
}

interface WidgetPort {
    fun cacheIsProAndUpdate(isPro: Boolean)
    fun updateAll()
}

interface DeviceSecurityPort {
    fun isDeviceSecure(): Boolean
}

interface CloudAiConfigPort {
    fun getConfig(): CloudAiConfigData
    fun saveConfig(apiKey: String, modelName: String, customEndpoint: String, enabled: Boolean)
}

interface BackgroundSyncPort {
    fun scheduleBackgroundJobs()
    fun resetHangingQueueItems()
}

interface MediaObserverPort {
    fun register()
    fun unregister()
}

interface MetadataSyncPort {
    fun markMetadataDirty()
    fun consumeMetadataDirty(): Boolean
}
