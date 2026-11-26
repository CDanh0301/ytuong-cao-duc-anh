package com.example.test2.ai

import android.content.Context
import com.example.test2.data.local.dao.ActionCardDao
import com.example.test2.data.local.dao.FtsSearchDao
import com.example.test2.data.local.dao.ScreenshotQueueDao
import com.example.test2.database.ProcessedScreenshotRecord
import com.example.test2.database.QueueStatus
import com.example.test2.services.ScreenshotQueueWorker
import com.example.test2.shared.models.ActionCardType
import com.example.test2.shared.models.Photo
import com.example.test2.shared.repository.PhotoAiMetadataUpdate
import com.example.test2.shared.repository.PhotoRepository
import com.example.test2.shared.usecases.photo.ScanDevicePhotosUseCase
import com.example.test2.shared.usecases.search.SearchPhotosUseCase
import com.example.test2.storage.isEncryptedPath
import com.example.test2.widget.PhotoSearchWidgetProvider
import com.example.test2.widget.PhotoSearchWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coordinates device photo scanning, SQLite-to-Repository metadata reconciliation,
 * background on-device triage, and vector indexing without cluttering ViewModels.
 */
class ScreenshotSyncOrchestrator(
    private val context: Context,
    private val repository: PhotoRepository,
    private val queueDao: ScreenshotQueueDao,
    private val ftsDao: FtsSearchDao,
    private val actionCardDao: ActionCardDao,
    private val processingCoordinator: PhotoProcessingCoordinator,
    private val scanDevicePhotosUseCase: ScanDevicePhotosUseCase,
    private val searchPhotosUseCase: SearchPhotosUseCase,
    val scanLock: Mutex,
    private val isLocalScanning: MutableStateFlow<Boolean>,
    private val pendingAiCount: MutableStateFlow<Int>,
    private val isScanning: MutableStateFlow<Boolean>,
    private val isVectorIndexing: MutableStateFlow<Boolean>,
    private val actionCardPhotoCounts: MutableStateFlow<Map<ActionCardType, Int>>
) {
    private val appContext = context.applicationContext
    private val hasPendingScanRequest = AtomicBoolean(false)
    private var onDeviceTriageJob: Job? = null

    fun updateScanningState() {
        val pending = queueDao.getPendingQueueCount()
        pendingAiCount.value = pending
        isScanning.value = isLocalScanning.value || pending > 0
    }

    suspend fun refreshActionCardCounts() = withContext(Dispatchers.IO) {
        actionCardDao.cleanOrphanedActionCards()
        val counts = actionCardDao.getActionCardsPhotoCountByType()
        actionCardPhotoCounts.value = counts
        PhotoSearchWidgetProvider.cacheIsPro(appContext, repository.accountTier.value.isPro)
        PhotoSearchWidgetUpdater.updateAll(appContext)
    }

    fun observeCloudAiSyncEvents(scope: CoroutineScope) {
        scope.launch {
            CloudAiSyncNotifier.syncEvents.collect { completedIds ->
                repository.awaitInitialLoad()
                val records = queueDao.getProcessedScreenshotsByIds(completedIds)
                val currentPhotos = repository.photos.value
                val existingIds = currentPhotos.map { it.id }.toSet()

                val missingVaultedPhotos = mutableListOf<Photo>()
                val updates = mutableListOf<PhotoAiMetadataUpdate>()

                for (record in records) {
                    val isVaulted = record.filePath.isEncryptedPath()
                    if (record.photoId !in existingIds) {
                        if (isVaulted) {
                            val vaultedFile = File(record.filePath)
                            if (vaultedFile.exists() && vaultedFile.length() > 0L) {
                                missingVaultedPhotos.add(
                                    buildRecoveredVaultedPhoto(record, vaultedFile, isCompleted = true)
                                )
                            }
                        }
                    } else {
                        val resolvedCatId = record.targetCategoryId
                        if (!resolvedCatId.isNullOrBlank() || isVaulted) {
                            val vaultedFile = if (isVaulted) File(record.filePath) else null
                            updates.add(
                                PhotoAiMetadataUpdate(
                                    photoId = record.photoId,
                                    newFilePath = if (isVaulted) record.filePath else null,
                                    newFileName = if (isVaulted) record.filePath.substringAfterLast("/") else null,
                                    newFileSizeBytes = if (vaultedFile != null && vaultedFile.exists()) vaultedFile.length() else null,
                                    isScreenshot = true,
                                    piiScrubbedText = record.scrubbedText,
                                    detectedIntent = record.detectedIntent,
                                    suggestedEventTitle = record.eventTitle,
                                    suggestedEventDateMillis = record.eventDateMillis,
                                    targetCategoryId = resolvedCatId,
                                    isProcessed = true,
                                    isCloudEnriched = true,
                                    suggestedEventDescription = record.eventDescription,
                                    semanticTags = record.tags?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
                                )
                            )
                        }
                    }
                }

                if (missingVaultedPhotos.isNotEmpty()) {
                    repository.addVaultedPhotos(missingVaultedPhotos)
                }
                if (updates.isNotEmpty()) {
                    repository.updatePhotoAiMetadataBatch(updates)
                }
                updateScanningState()
                refreshActionCardCounts()
            }
        }
    }

    suspend fun syncCompletedScreenshotsFromDb() = withContext(Dispatchers.IO) {
        repository.awaitInitialLoad()
        val records = queueDao.getAllCompletedOrVaultedScreenshots()
        if (records.isNotEmpty()) {
            val recordMap = records.associateBy { it.photoId }
            val currentPhotos = repository.photos.value
            val existingIds = currentPhotos.map { it.id }.toSet()
            val updates = mutableListOf<PhotoAiMetadataUpdate>()
            val missingVaultedPhotos = mutableListOf<Photo>()

            for (record in records) {
                if (record.photoId !in existingIds && record.filePath.isEncryptedPath()) {
                    val vaultedFile = File(record.filePath)
                    if (vaultedFile.exists() && vaultedFile.length() > 0L) {
                        missingVaultedPhotos.add(
                            buildRecoveredVaultedPhoto(record, vaultedFile, isCompleted = record.status == QueueStatus.COMPLETED.name)
                        )
                    }
                }
            }

            if (missingVaultedPhotos.isNotEmpty()) {
                repository.addVaultedPhotos(missingVaultedPhotos)
            }

            for (photo in currentPhotos) {
                val record = recordMap[photo.id]
                if (record != null) {
                    val currentCatId = photo.categoryIds.firstOrNull {
                        it.startsWith("cat_") || it in repository.categories.value.map { c -> c.id }
                    }
                    val catNeedsUpdate = !record.targetCategoryId.isNullOrBlank() && currentCatId != record.targetCategoryId
                    val pathNeedsUpdate = record.filePath.isEncryptedPath() && !photo.filePath.isEncryptedPath()
                    val metaNeedsUpdate = !record.eventDescription.isNullOrBlank() && photo.suggestedEventDescription != record.eventDescription
                    val isCompleted = record.status == QueueStatus.COMPLETED.name

                    if (isCompleted && (catNeedsUpdate || pathNeedsUpdate || metaNeedsUpdate || !photo.isProcessed)) {
                        val vaultedFile = if (record.filePath.isEncryptedPath()) File(record.filePath) else null
                        updates.add(
                            PhotoAiMetadataUpdate(
                                photoId = record.photoId,
                                newFilePath = if (pathNeedsUpdate) record.filePath else null,
                                newFileName = if (pathNeedsUpdate) record.filePath.substringAfterLast("/") else null,
                                newFileSizeBytes = if (vaultedFile != null && vaultedFile.exists()) vaultedFile.length() else null,
                                isScreenshot = true,
                                piiScrubbedText = record.scrubbedText ?: photo.piiScrubbedText,
                                detectedIntent = record.detectedIntent ?: photo.detectedIntent,
                                suggestedEventTitle = record.eventTitle ?: photo.suggestedEventTitle,
                                suggestedEventDateMillis = record.eventDateMillis ?: photo.suggestedEventDateMillis,
                                targetCategoryId = record.targetCategoryId ?: currentCatId,
                                isProcessed = true,
                                suggestedEventDescription = record.eventDescription ?: photo.suggestedEventDescription,
                                semanticTags = record.tags?.split(" ")?.filter { it.isNotBlank() } ?: photo.semanticTags
                            )
                        )
                    } else if (pathNeedsUpdate) {
                        val vaultedFile = File(record.filePath)
                        if (vaultedFile.exists() && vaultedFile.length() > 0L) {
                            repository.updatePhotoStoragePath(
                                photoId = record.photoId,
                                newFilePath = record.filePath,
                                newFileName = record.filePath.substringAfterLast("/"),
                                newFileSizeBytes = vaultedFile.length()
                            )
                        }
                    }
                }
            }
            if (updates.isNotEmpty()) {
                repository.updatePhotoAiMetadataBatch(updates)
            }
        }
        updateScanningState()
        refreshActionCardCounts()
    }

    fun scanDevicePhotos(scope: CoroutineScope) {
        scope.launch {
            if (!scanLock.tryLock()) {
                hasPendingScanRequest.set(true)
                return@launch
            }

            try {
                do {
                    hasPendingScanRequest.set(false)
                    performScanDevicePhotosInternal(scope)
                } while (hasPendingScanRequest.getAndSet(false))
            } finally {
                scanLock.unlock()
            }
        }
    }

    private suspend fun performScanDevicePhotosInternal(scope: CoroutineScope) {
        isLocalScanning.value = true
        updateScanningState()
        val scanResult = scanDevicePhotosUseCase()
        if (scanResult.photos.isNotEmpty() || scanResult.folders.isNotEmpty()) {
            isLocalScanning.value = false
            updateScanningState()
            syncCompletedScreenshotsFromDb()
            ensureVectorsIndexed(forceReindex = false)
            launchOnDeviceTriage(scope)
        } else {
            isLocalScanning.value = false
            updateScanningState()
        }
    }

    suspend fun ensureVectorsIndexed(forceReindex: Boolean = false) = withContext(Dispatchers.IO) {
        isVectorIndexing.value = true
        try {
            searchPhotosUseCase.ensureVectorsIndexed(forceReindex)
        } finally {
            isVectorIndexing.value = false
        }
    }

    private fun launchOnDeviceTriage(scope: CoroutineScope) {
        onDeviceTriageJob?.cancel()
        onDeviceTriageJob = scope.launch(Dispatchers.IO) {
            processNewScreenshots()
            ScreenshotQueueWorker.enqueue(appContext)
        }
    }

    private suspend fun processNewScreenshots() {
        val currentPhotos = repository.photos.value
        for (photo in currentPhotos) {
            if (photo.isCloudEnriched) continue

            val cached = queueDao.getProcessedScreenshot(photo.id)
            if (cached != null && !cached.targetCategoryId.isNullOrBlank()) {
                repository.updatePhotoAiMetadata(
                    photoId = photo.id,
                    isScreenshot = true,
                    piiScrubbedText = cached.scrubbedText ?: photo.piiScrubbedText,
                    detectedIntent = cached.detectedIntent,
                    suggestedEventTitle = cached.eventTitle,
                    suggestedEventDateMillis = cached.eventDateMillis,
                    targetCategoryId = cached.targetCategoryId,
                    isProcessed = true,
                    suggestedEventDescription = cached.eventDescription,
                    semanticTags = cached.tags?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
                )
                continue
            }

            val isCandidate = photo.isScreenshot || SmartHeuristicParser.isScreenshotPhoto(photo)
            val needsOcr = photo.piiScrubbedText.isNullOrBlank()

            if (isCandidate && needsOcr) {
                if (cached != null && !cached.scrubbedText.isNullOrBlank()) {
                    val resolvedCatId = processingCoordinator.recheckWithCustomCategories(photo, cached)
                    repository.updatePhotoAiMetadata(
                        photoId = photo.id,
                        isScreenshot = true,
                        piiScrubbedText = cached.scrubbedText,
                        detectedIntent = cached.detectedIntent,
                        suggestedEventTitle = cached.eventTitle,
                        suggestedEventDateMillis = cached.eventDateMillis,
                        targetCategoryId = resolvedCatId,
                        isProcessed = true,
                        suggestedEventDescription = cached.eventDescription
                    )
                    continue
                }
                processingCoordinator.runOnDevicePipeline(photo)
            } else if (!photo.piiScrubbedText.isNullOrBlank()) {
                val scrubbed = photo.piiScrubbedText ?: ""
                if (photo.isScreenshot && scrubbed.isNotBlank() && actionCardDao.getActionCardsForPhoto(photo.id).isEmpty()) {
                    val cards = LocalActionCardExtractor.extractCardsFromText(
                        photoId = photo.id,
                        rawOcrText = scrubbed,
                        photoTitle = photo.title
                    )
                    if (cards.isNotEmpty()) {
                        actionCardDao.replaceActionCardsForPhoto(photo.id, cards)
                    }
                }
                val categoryNames = repository.categories.value
                    .filter { it.id in photo.categoryIds }
                    .joinToString(" ") { it.name }
                ftsDao.indexPhotoFts(
                    photoId = photo.id,
                    title = photo.title,
                    scrubbedText = photo.piiScrubbedText ?: "",
                    intent = photo.detectedIntent ?: "",
                    categoryNames = categoryNames
                )
            }
        }
        refreshActionCardCounts()
    }

    private fun buildRecoveredVaultedPhoto(
        record: ProcessedScreenshotRecord,
        vaultedFile: File,
        isCompleted: Boolean
    ): Photo {
        val resolvedCatIds = if (!record.targetCategoryId.isNullOrBlank()) setOf(record.targetCategoryId) else emptySet()
        val resolvedTitle = record.photoTitle?.takeIf { it.isNotBlank() && !it.startsWith("secure_vault_") }
            ?: record.originalFileName?.substringBeforeLast(".")?.takeIf { it.isNotBlank() && !it.startsWith("secure_vault_") }
            ?: record.eventTitle?.takeIf { it.isNotBlank() }
            ?: record.filePath.substringAfterLast("/").removeSuffix(".enc").substringBeforeLast(".")
        val resolvedPhotoDate = if (record.dateAdded > 0L) {
            record.dateAdded
        } else if (vaultedFile.exists()) {
            vaultedFile.lastModified()
        } else {
            System.currentTimeMillis()
        }
        return Photo(
            id = record.photoId,
            fileName = record.filePath.substringAfterLast("/"),
            filePath = record.filePath,
            title = resolvedTitle.ifBlank { "Ảnh chụp màn hình" },
            description = record.summary ?: "",
            folderId = "folder_screenshots",
            categoryIds = resolvedCatIds,
            isFavorite = false,
            isScreenshot = true,
            dateAdded = resolvedPhotoDate,
            fileSizeBytes = vaultedFile.length(),
            isProcessed = isCompleted,
            isCloudEnriched = isCompleted,
            piiScrubbedText = record.scrubbedText,
            detectedIntent = record.detectedIntent,
            suggestedEventTitle = record.eventTitle,
            suggestedEventDateMillis = record.eventDateMillis,
            suggestedEventDescription = record.eventDescription,
            semanticTags = record.tags?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
        )
    }
}
