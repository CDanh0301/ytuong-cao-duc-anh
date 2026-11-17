package com.example.test2.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.test2.ai.CloudAiBackendClient
import com.example.test2.ai.CloudAiConfig
import com.example.test2.ai.CloudAiConfigManager
import com.example.test2.ai.CloudAiSyncNotifier
import com.example.test2.ai.LocalImageLabeler
import com.example.test2.ai.MlKitOcrEngine
import com.example.test2.ai.PiiScrubber
import com.example.test2.ai.RetroactiveCategorizer
import com.example.test2.ai.SmartHeuristicParser
import com.example.test2.ai.TemporalQueryParser
import com.example.test2.ai.VectorSemanticSearchEngine
import com.example.test2.database.AppDatabaseHelper
import com.example.test2.notifications.ScreenshotNotificationHelper
import com.example.test2.security.PrivacyVaultManager
import com.example.test2.services.AgingJobScheduler
import com.example.test2.services.AgingMonitorManager
import com.example.test2.services.ScreenshotDetectionManager
import com.example.test2.services.ScreenshotJobScheduler
import com.example.test2.services.ScreenshotQueueWorker
import com.example.test2.services.ScreenshotWatcherService
import com.example.test2.shared.models.AgingSettings
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Folder
import com.example.test2.shared.models.Photo
import com.example.test2.shared.models.PhotoFilter
import com.example.test2.shared.models.SortOption
import com.example.test2.shared.models.UserAccountTier
import com.example.test2.shared.repository.PhotoAiMetadataUpdate
import com.example.test2.shared.repository.PhotoRepository
import com.example.test2.storage.AndroidFilePersistenceDriver
import com.example.test2.storage.AndroidPhotoStorage
import com.example.test2.storage.DeviceMediaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppTab {
    PHOTOS,
    CATEGORIES,
    CLEANER // Housekeeping / Clutter Cleaner (IDEA.md)
}

enum class GalleryDisplayMode {
    BIG_PREVIEW,
    SMALL_PREVIEW,
    LIST
}

class PhotoAppViewModel(application: Application) : AndroidViewModel(application) {
    private val persistenceDriver = AndroidFilePersistenceDriver(application)
    val repository = PhotoRepository(persistenceDriver, viewModelScope)
    private val storage = AndroidPhotoStorage(application)
    private val dbHelper = AppDatabaseHelper.getInstance(application)
    private val notifiedScreenshotIds = mutableSetOf<String>()
    private val appStartTime = System.currentTimeMillis()
    private val mediaScanner = DeviceMediaScanner(application)

    val photos: StateFlow<List<Photo>> = repository.photos
    val filteredPhotos: StateFlow<List<Photo>> = repository.filteredPhotos
    val folders: StateFlow<List<Folder>> = repository.folders
    val categories: StateFlow<List<Category>> = repository.categories
    val currentFilter: StateFlow<PhotoFilter> = repository.currentFilter
    val accountTier: StateFlow<UserAccountTier> = repository.accountTier
    val agingSettings: StateFlow<AgingSettings> = repository.agingSettings

    private val _activeTab = MutableStateFlow(AppTab.PHOTOS)
    val activeTab: StateFlow<AppTab> = _activeTab.asStateFlow()

    // Display mode for photo gallery: Big Preview, Small Preview, List
    private val _displayMode = MutableStateFlow(GalleryDisplayMode.BIG_PREVIEW)
    val displayMode: StateFlow<GalleryDisplayMode> = _displayMode.asStateFlow()

    fun setDisplayMode(mode: GalleryDisplayMode) {
        _displayMode.value = mode
    }

    // Full-screen image viewer state
    // initialPhotoId tracks which photo was tapped to open the viewer
    private val _viewingPhotoId = MutableStateFlow<String?>(null)
    val viewingPhotoId: StateFlow<String?> = _viewingPhotoId.asStateFlow()

    // Photo settings sheet/dialog state (shown only when user clicks settings inside image view)
    private val _photoSettings = MutableStateFlow<Photo?>(null)
    val photoSettings: StateFlow<Photo?> = _photoSettings.asStateFlow()

    // Permission and Scanning state
    private val _hasStoragePermission = MutableStateFlow(checkInitialPermission())
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    // Privacy Vault & Security State
    private val _isVaultEnabled = MutableStateFlow(PrivacyVaultManager.isVaultEnabled(application))
    val isVaultEnabled: StateFlow<Boolean> = _isVaultEnabled.asStateFlow()

    private val _isAppUnlocked = MutableStateFlow(!PrivacyVaultManager.isVaultEnabled(application))
    val isAppUnlocked: StateFlow<Boolean> = _isAppUnlocked.asStateFlow()

    private val _showPrivacyConsentDialog = MutableStateFlow(false)
    val showPrivacyConsentDialog: StateFlow<Boolean> = _showPrivacyConsentDialog.asStateFlow()

    private val _showAllFilesAccessDialog = MutableStateFlow(false)
    val showAllFilesAccessDialog: StateFlow<Boolean> = _showAllFilesAccessDialog.asStateFlow()

    private val _showEnableVaultScopeDialog = MutableStateFlow(false)
    val showEnableVaultScopeDialog: StateFlow<Boolean> = _showEnableVaultScopeDialog.asStateFlow()

    private val _isRestoringPhotos = MutableStateFlow(false)
    val isRestoringPhotos: StateFlow<Boolean> = _isRestoringPhotos.asStateFlow()

    private val _restoreProgress = MutableStateFlow(Pair(0, 0))
    val restoreProgress: StateFlow<Pair<Int, Int>> = _restoreProgress.asStateFlow()

    private val _showDisableVaultDialog = MutableStateFlow(false)
    val showDisableVaultDialog: StateFlow<Boolean> = _showDisableVaultDialog.asStateFlow()

    private val _showRestoreSuccessBanner = MutableStateFlow<String?>(null)
    val showRestoreSuccessBanner: StateFlow<String?> = _showRestoreSuccessBanner.asStateFlow()

    private val _isLocalScanning = MutableStateFlow(false)
    private val _pendingAiCount = MutableStateFlow(0)
    val pendingAiCount: StateFlow<Int> = _pendingAiCount.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun updateScanningState() {
        val pending = dbHelper.getPendingQueueCount()
        _pendingAiCount.value = pending
        _isScanning.value = _isLocalScanning.value || pending > 0
    }

    // Dialog states
    private val _showCreateFolderDialog = MutableStateFlow(false)
    val showCreateFolderDialog: StateFlow<Boolean> = _showCreateFolderDialog.asStateFlow()

    private val _showCreateCategoryDialog = MutableStateFlow(false)
    val showCreateCategoryDialog: StateFlow<Boolean> = _showCreateCategoryDialog.asStateFlow()

    private val _folderToEdit = MutableStateFlow<Folder?>(null)
    val folderToEdit: StateFlow<Folder?> = _folderToEdit.asStateFlow()

    private val _categoryToEdit = MutableStateFlow<Category?>(null)
    val categoryToEdit: StateFlow<Category?> = _categoryToEdit.asStateFlow()

    private val _photoToDelete = MutableStateFlow<Photo?>(null)
    val photoToDelete: StateFlow<Photo?> = _photoToDelete.asStateFlow()

    private val _folderToDelete = MutableStateFlow<Folder?>(null)
    val folderToDelete: StateFlow<Folder?> = _folderToDelete.asStateFlow()

    private val _categoryToDelete = MutableStateFlow<Category?>(null)
    val categoryToDelete: StateFlow<Category?> = _categoryToDelete.asStateFlow()

    private val _retroactiveCategoryPrompt = MutableStateFlow<RetroactiveCategoryPrompt?>(null)
    val retroactiveCategoryPrompt: StateFlow<RetroactiveCategoryPrompt?> = _retroactiveCategoryPrompt.asStateFlow()

    private val _retroactiveScanningCategory = MutableStateFlow<Category?>(null)
    val retroactiveScanningCategory: StateFlow<Category?> = _retroactiveScanningCategory.asStateFlow()

    private val _noMatchesCategory = MutableStateFlow<Category?>(null)
    val noMatchesCategory: StateFlow<Category?> = _noMatchesCategory.asStateFlow()

    fun dismissNoMatchesDialog() {
        _noMatchesCategory.value = null
    }

    private val _cloudAiConfig = MutableStateFlow(CloudAiConfigManager.getConfig(application))
    val cloudAiConfig: StateFlow<CloudAiConfig> = _cloudAiConfig.asStateFlow()

    private val _semanticMatchScores = MutableStateFlow<Map<String, Float>>(emptyMap())
    val semanticMatchScores: StateFlow<Map<String, Float>> = _semanticMatchScores.asStateFlow()

    private val _isVectorIndexing = MutableStateFlow(false)
    val isVectorIndexing: StateFlow<Boolean> = _isVectorIndexing.asStateFlow()

    private val _agingMonitorInfo = MutableStateFlow(AgingMonitorManager.getLastCheckedInfo(application))
    val agingMonitorInfo: StateFlow<Triple<Long, Int, Long>> = _agingMonitorInfo.asStateFlow()

    private var contentObserver: ContentObserver? = null
    private var pendingVaultScanAfterPermission = false

    init {
        repository.checkMonthlyQuotaReset(System.currentTimeMillis())
        ensureVectorsIndexed()
        AgingJobScheduler.schedule(application)
        dbHelper.resetHangingProcessingItems()

        observeCloudAiSyncEvents()
        syncCompletedScreenshotsFromDb()

        if (_hasStoragePermission.value) {
            checkPrivacyConsentPrompt()
            ScreenshotJobScheduler.schedule(application)
            registerMediaObserver()
            scanDevicePhotos()
        }
    }

    private fun observeCloudAiSyncEvents() {
        viewModelScope.launch {
            CloudAiSyncNotifier.syncEvents.collect { completedIds ->
                val records = dbHelper.getProcessedScreenshotsByIds(completedIds)
                val updates = records.mapNotNull { record ->
                    val resolvedCatId = record.targetCategoryId
                    val isVaulted = record.filePath.endsWith(".enc")
                    if (!resolvedCatId.isNullOrBlank() || isVaulted) {
                        val vaultedFile = if (isVaulted) java.io.File(record.filePath) else null
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
                    } else null
                }
                if (updates.isNotEmpty()) {
                    repository.updatePhotoAiMetadataBatch(updates)
                }
                updateScanningState()
            }
        }
    }

    private fun syncCompletedScreenshotsFromDb() {
        viewModelScope.launch(Dispatchers.IO) {
            val records = dbHelper.getAllCompletedProcessedScreenshots()
            if (records.isNotEmpty()) {
                val recordMap = records.associateBy { it.photoId }
                val currentPhotos = repository.photos.value
                val updates = mutableListOf<PhotoAiMetadataUpdate>()
                for (photo in currentPhotos) {
                    val record = recordMap[photo.id]
                    if (record != null) {
                        val currentCatId = photo.categoryIds.firstOrNull { it.startsWith("cat_") || it in repository.categories.value.map { c -> c.id } }
                        val catNeedsUpdate = !record.targetCategoryId.isNullOrBlank() && currentCatId != record.targetCategoryId
                        val pathNeedsUpdate = record.filePath.endsWith(".enc") && !photo.filePath.endsWith(".enc")
                        val metaNeedsUpdate = !record.eventDescription.isNullOrBlank() && photo.suggestedEventDescription != record.eventDescription

                        if (catNeedsUpdate || pathNeedsUpdate || metaNeedsUpdate || !photo.isProcessed) {
                            val vaultedFile = if (record.filePath.endsWith(".enc")) java.io.File(record.filePath) else null
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
                        }
                    }
                }
                if (updates.isNotEmpty()) {
                    repository.updatePhotoAiMetadataBatch(updates)
                }
            }
            updateScanningState()
        }
    }

    private fun checkInitialPermission(): Boolean {
        val app = getApplication<Application>()
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun onPermissionResult(granted: Boolean) {
        _hasStoragePermission.value = granted
        if (granted) {
            val app = getApplication<Application>()
            ScreenshotJobScheduler.schedule(app)
            registerMediaObserver()
            if (!PrivacyVaultManager.hasPromptedConsent(app)) {
                _showPrivacyConsentDialog.value = true
            }
            scanDevicePhotos()
        }
    }

    fun checkPrivacyConsentPrompt() {
        val app = getApplication<Application>()
        if (_hasStoragePermission.value && !PrivacyVaultManager.hasPromptedConsent(app)) {
            _showPrivacyConsentDialog.value = true
        }
    }

    fun onEnableVault() {
        val app = getApplication<Application>()
        PrivacyVaultManager.setPromptedConsent(app, true)
        _showPrivacyConsentDialog.value = false
        confirmEnableVault(vaultAll = true)
    }

    fun onKeepStandard() {
        val app = getApplication<Application>()
        PrivacyVaultManager.setVaultEnabled(app, false)
        PrivacyVaultManager.setPromptedConsent(app, true)
        _isVaultEnabled.value = false
        _isAppUnlocked.value = true
        _showPrivacyConsentDialog.value = false
    }

    fun setVaultEnabled(enabled: Boolean) {
        if (!enabled) {
            requestDisableVault()
        } else {
            requestEnableVault()
        }
    }

    fun requestEnableVault() {
        _showEnableVaultScopeDialog.value = true
    }

    fun dismissEnableVaultScopeDialog() {
        _showEnableVaultScopeDialog.value = false
    }

    fun confirmEnableVault(vaultAll: Boolean) {
        val app = getApplication<Application>()
        val scope = if (vaultAll) PrivacyVaultManager.SCOPE_ALL else PrivacyVaultManager.SCOPE_NEW_ONLY
        PrivacyVaultManager.setVaultScope(app, scope)
        PrivacyVaultManager.setVaultEnabledSince(app, System.currentTimeMillis())
        PrivacyVaultManager.setVaultEnabled(app, true)
        _isVaultEnabled.value = true
        _isAppUnlocked.value = true
        _showEnableVaultScopeDialog.value = false
        if (!PrivacyVaultManager.hasAllFilesAccess(app)) {
            pendingVaultScanAfterPermission = true
            _showAllFilesAccessDialog.value = true
        } else {
            scanDevicePhotos()
        }
    }

    fun checkVaultPermissionResumed() {
        val app = getApplication<Application>()
        if (pendingVaultScanAfterPermission && PrivacyVaultManager.hasAllFilesAccess(app)) {
            pendingVaultScanAfterPermission = false
            scanDevicePhotos()
        }
    }

    fun requestDisableVault() {
        val app = getApplication<Application>()
        val vaultedCount = repository.photos.value.count {
            it.filePath.endsWith(".enc") || (it.isScreenshot && !it.filePath.startsWith("content://"))
        }
        if (vaultedCount > 0) {
            _showDisableVaultDialog.value = true
        } else {
            PrivacyVaultManager.setVaultEnabled(app, false)
            _isVaultEnabled.value = false
            _isAppUnlocked.value = true
            _showDisableVaultDialog.value = false
        }
    }

    fun dismissDisableVaultDialog() {
        _showDisableVaultDialog.value = false
    }

    fun restoreAllVaultedPhotos() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val currentPhotos = repository.photos.value
            val vaultedList = currentPhotos.filter {
                it.filePath.endsWith(".enc") || (it.isScreenshot && !it.filePath.startsWith("content://"))
            }

            if (vaultedList.isEmpty()) {
                PrivacyVaultManager.setVaultEnabled(app, false)
                _isVaultEnabled.value = false
                _isAppUnlocked.value = true
                _showDisableVaultDialog.value = false
                return@launch
            }

            _isRestoringPhotos.value = true
            _restoreProgress.value = Pair(0, vaultedList.size)

            var restoredCount = 0
            for (photo in vaultedList) {
                val result = PrivacyVaultManager.restoreAndEvictScreenshot(
                    context = app,
                    vaultedFilePath = photo.filePath,
                    originalFileName = photo.fileName
                )
                if (result != null) {
                    val newPhotoId = "device_${result.newMediaId}"
                    dbHelper.migratePhotoRecords(
                        oldPhotoId = photo.id,
                        newPhotoId = newPhotoId,
                        newFilePath = result.newUri,
                        dateAdded = photo.dateAdded,
                        scrubbedText = photo.piiScrubbedText,
                        detectedIntent = photo.detectedIntent,
                        eventTitle = photo.suggestedEventTitle,
                        eventDateMillis = photo.suggestedEventDateMillis,
                        targetCategoryId = photo.categoryIds.firstOrNull(),
                        eventDescription = photo.suggestedEventDescription
                    )
                    repository.updatePhotoStoragePath(
                        photoId = photo.id,
                        newFilePath = result.newUri,
                        newFileName = result.newFileName,
                        newFileSizeBytes = result.fileSizeBytes,
                        newPhotoId = newPhotoId
                    )
                }
                restoredCount++
                _restoreProgress.value = Pair(restoredCount, vaultedList.size)
            }

            PrivacyVaultManager.setVaultEnabled(app, false)
            _isVaultEnabled.value = false
            _isAppUnlocked.value = true
            _isRestoringPhotos.value = false
            _showDisableVaultDialog.value = false
            _showRestoreSuccessBanner.value = "Đã khôi phục thành công $restoredCount ảnh về Bộ sưu tập của máy"
            scanDevicePhotos()
        }
    }

    fun dismissRestoreSuccessBanner() {
        _showRestoreSuccessBanner.value = null
    }

    fun dismissAllFilesAccessDialog() {
        _showAllFilesAccessDialog.value = false
    }

    fun unlockApp() {
        _isAppUnlocked.value = true
    }

    fun lockApp() {
        if (_isVaultEnabled.value) {
            _isAppUnlocked.value = false
        }
    }

    fun registerMediaObserver() {
        if (contentObserver != null) return
        val handler = Handler(Looper.getMainLooper())
        contentObserver = object : ContentObserver(handler) {
            private var lastTriggerTime = 0L

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (PrivacyVaultManager.isInternalEvicting.get()) {
                    return // Ignore events generated by our own vault evictions
                }
                val now = System.currentTimeMillis()
                if (now - lastTriggerTime < 1500L) {
                    return // Debounce rapid bursts
                }
                lastTriggerTime = now

                // Real-time passive capture: scan device photos, vault, and triage sequentially
                scanDevicePhotos()
            }
        }
        try {
            getApplication<Application>().contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserver!!
            )
        } catch (_: Exception) {
        }
    }

    private fun unregisterMediaObserver() {
        contentObserver?.let {
            try {
                getApplication<Application>().contentResolver.unregisterContentObserver(it)
            } catch (_: Exception) {
            }
            contentObserver = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        unregisterMediaObserver()
    }

    fun scanDevicePhotos() {
        viewModelScope.launch {
            _isLocalScanning.value = true
            updateScanningState()
            val scanResult = mediaScanner.scanDeviceImages()
            if (scanResult.photos.isNotEmpty() || scanResult.folders.isNotEmpty()) {
                val app = getApplication<Application>()
                val processedPhotos = if (_isVaultEnabled.value && PrivacyVaultManager.hasAllFilesAccess(app)) {
                    withContext(Dispatchers.IO) {
                        scanResult.photos.map { photo ->
                            if (photo.isScreenshot && !photo.filePath.endsWith(".enc") && PrivacyVaultManager.shouldVaultScreenshot(app, photo.dateAdded)) {
                                val uri = if (photo.filePath.startsWith("content://")) Uri.parse(photo.filePath) else null
                                val vaulted = PrivacyVaultManager.vaultAndEvictScreenshot(
                                    app,
                                    uri,
                                    photo.filePath,
                                    photo.fileName
                                )
                                if (vaulted != null) {
                                    dbHelper.syncVaultedPathToQueue(photo.id, vaulted.newFilePath)
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
                    }
                } else {
                    scanResult.photos
                }
                repository.syncDevicePhotos(processedPhotos, scanResult.folders)
                _isLocalScanning.value = false
                updateScanningState()

                // Immediately bind any completed AI metadata from DB for already processed photos
                syncCompletedScreenshotsFromDb()

                ensureVectorsIndexed()

                // Launch On-Device Triage in background (UI is already visible and interactive)
                launchOnDeviceTriage()
            } else {
                _isLocalScanning.value = false
                updateScanningState()
            }
        }
    }

    private var onDeviceTriageJob: Job? = null

    private fun launchOnDeviceTriage() {
        onDeviceTriageJob?.cancel()
        onDeviceTriageJob = viewModelScope.launch(Dispatchers.IO) {
            processNewScreenshots()
            // After on-device triage completes processing all new screenshots,
            // trigger WorkManager to progressively enrich them via Cloud AI
            ScreenshotQueueWorker.enqueue(getApplication())
        }
    }

    // IDEA.md: Process unprocessed screenshots through ML Kit OCR, PII scrubber, Smart Heuristics & FTS5 indexing
    private suspend fun processNewScreenshots() {
        val currentPhotos = repository.photos.value
        val app = getApplication<Application>()
        for (photo in currentPhotos) {
            // Never overwrite cloud-enriched photos
            if (photo.isCloudEnriched) {
                continue
            }

            // First check if high-accuracy Gemini result already exists in database
            val cached = dbHelper.getProcessedScreenshot(photo.id)
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
                val cached = dbHelper.getProcessedScreenshot(photo.id)
                if (cached != null && !cached.scrubbedText.isNullOrBlank()) {
                    var resolvedCatId = cached.targetCategoryId
                    // Re-verify against user custom categories: prevent newly taken photos from being trapped
                    // in default categories when user-defined categories match
                    val customCats = repository.categories.value.filter { it.id !in SmartHeuristicParser.DEFAULT_CATEGORY_IDS }
                    if (customCats.isNotEmpty() && (resolvedCatId == null || resolvedCatId in SmartHeuristicParser.DEFAULT_CATEGORY_IDS)) {
                        val visualAnalysis = LocalImageLabeler.analyze(app, photo.filePath)
                        val recheck = SmartHeuristicParser.parse(
                            scrubbedText = cached.scrubbedText ?: "",
                            photoTitle = photo.title,
                            customCategories = repository.categories.value,
                            hasFace = visualAnalysis.isPerson,
                            visualLabels = visualAnalysis.rawLabels
                        )
                        if (recheck.targetCategoryId != null && recheck.targetCategoryId !in SmartHeuristicParser.DEFAULT_CATEGORY_IDS) {
                            resolvedCatId = recheck.targetCategoryId
                            dbHelper.recordProcessedScreenshot(
                                photoId = photo.id,
                                filePath = photo.filePath,
                                dateAdded = photo.dateAdded,
                                scrubbedText = cached.scrubbedText ?: "",
                                detectedIntent = cached.detectedIntent,
                                eventTitle = cached.eventTitle,
                                eventDateMillis = cached.eventDateMillis,
                                targetCategoryId = resolvedCatId,
                                eventDescription = cached.eventDescription
                            )
                        }
                    }

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

                // Step 2 in IDEA.md: Pre-filtering junk, memes, or blank icons without consuming quota
                if (SmartHeuristicParser.isJunkOrMeme(photo)) {
                    repository.updatePhotoAiMetadata(
                        photoId = photo.id,
                        isScreenshot = true,
                        piiScrubbedText = "",
                        detectedIntent = null,
                        suggestedEventTitle = null,
                        suggestedEventDateMillis = null,
                        targetCategoryId = null,
                        isProcessed = true
                    )
                    continue
                }

                // Step 2: Unified On-Device Vision Analysis (ML Kit Image Labeling)
                val visualAnalysis = LocalImageLabeler.analyze(app, photo.filePath)

                // Short-Circuit Optimization: Pure Nature, Landscape, Scenery & Animal photos without text
                if ((visualAnalysis.isNature || visualAnalysis.isAnimal) && !visualAnalysis.hasTextOrDoc) {
                    val targetCatId = "cat_nature"
                    val categoryNames = repository.categories.value
                        .filter { it.id == targetCatId }
                        .joinToString(" ") { it.name }

                    val semanticTags = visualAnalysis.vietnameseSemanticTags.ifEmpty {
                        if (visualAnalysis.isAnimal) listOf("động vật", "thú cưng") else listOf("thiên nhiên", "phong cảnh")
                    }
                    val semanticTagsStr = semanticTags.joinToString(" ")
                    val fullSemanticText = "${photo.title} Thiên nhiên phong cảnh $semanticTagsStr".trim()

                    dbHelper.indexPhotoFts(
                        photoId = photo.id,
                        title = photo.title,
                        scrubbedText = fullSemanticText,
                        intent = "OTHER",
                        categoryNames = categoryNames,
                        semanticTags = semanticTagsStr
                    )

                    if (repository.accountTier.value.isPro) {
                        val embedding = VectorSemanticSearchEngine.generateEmbedding(
                            text = fullSemanticText,
                            title = photo.title,
                            intent = "OTHER",
                            categoryNames = if (categoryNames.isNotBlank()) listOf(categoryNames) else emptyList()
                        )
                        dbHelper.savePhotoVector(photo.id, embedding, "OTHER")
                        dbHelper.enqueueScreenshot(
                            photoId = photo.id,
                            filePath = photo.filePath,
                            dateAdded = photo.dateAdded,
                            isLiveCapture = false,
                            photoTitle = photo.title,
                            originalFileName = photo.fileName
                        )
                    }

                    repository.updatePhotoAiMetadata(
                        photoId = photo.id,
                        isScreenshot = true,
                        piiScrubbedText = "",
                        detectedIntent = null,
                        suggestedEventTitle = null,
                        suggestedEventDateMillis = null,
                        targetCategoryId = targetCatId,
                        isProcessed = true,
                        isCloudEnriched = false,
                        suggestedEventDescription = null,
                        semanticTags = semanticTags
                    )
                    continue
                }

                // Step 3 in IDEA.md: Local OCR & Vision Analysis on device
                val ocrText = MlKitOcrEngine.recognizeText(app, photo.filePath)
                val hasFace = visualAnalysis.isPerson
                if (ocrText.isBlank() || ocrText.trim().length < 4) {
                    // Blank lock-screens or images without useful text: check filename/folder cues, face and visual labels
                    val heuristic = SmartHeuristicParser.parse(
                        scrubbedText = "",
                        photoTitle = "${photo.folderId} ${photo.title}",
                        hasFace = hasFace,
                        visualLabels = visualAnalysis.rawLabels
                    )
                    val targetCatId = heuristic.targetCategoryId
                    val categoryNames = repository.categories.value
                        .filter { it.id == targetCatId }
                        .joinToString(" ") { it.name }

                    val semanticTags = heuristic.semanticTags.ifEmpty { visualAnalysis.vietnameseSemanticTags }
                    val semanticTagsStr = semanticTags.joinToString(" ")
                    val fullSemanticText = "${photo.title} $semanticTagsStr".trim()

                    dbHelper.indexPhotoFts(
                        photoId = photo.id,
                        title = photo.title,
                        scrubbedText = fullSemanticText,
                        intent = heuristic.detectedIntent,
                        categoryNames = categoryNames,
                        semanticTags = semanticTagsStr
                    )

                    if (repository.accountTier.value.isPro) {
                        val embedding = VectorSemanticSearchEngine.generateEmbedding(
                            text = "${photo.folderId ?: ""} $categoryNames $semanticTagsStr",
                            title = photo.title,
                            intent = heuristic.detectedIntent,
                            categoryNames = if (categoryNames.isNotBlank()) listOf(categoryNames) else emptyList()
                        )
                        dbHelper.savePhotoVector(photo.id, embedding, heuristic.detectedIntent)
                        if (fullSemanticText.isNotBlank()) {
                            dbHelper.enqueueScreenshot(
                                photoId = photo.id,
                                filePath = photo.filePath,
                                dateAdded = photo.dateAdded,
                                isLiveCapture = false,
                                photoTitle = photo.title,
                                originalFileName = photo.fileName
                            )
                        }
                    }

                    // Check if photo still has informative visual content (e.g. receipt/doc/ticket/invoice)
                    val hasInformativeVisual = visualAnalysis.isPerson || visualAnalysis.hasTextOrDoc ||
                        visualAnalysis.rawLabels.any {
                            it.equals("Receipt", ignoreCase = true) ||
                            it.equals("Document", ignoreCase = true) ||
                            it.equals("Paper", ignoreCase = true) ||
                            it.equals("Invoice", ignoreCase = true) ||
                            it.equals("Identity document", ignoreCase = true) ||
                            it.equals("Ticket", ignoreCase = true) ||
                            it.equals("Poster", ignoreCase = true)
                        }
                    if (hasInformativeVisual) {
                        dbHelper.enqueueScreenshot(
                            photoId = photo.id,
                            filePath = photo.filePath,
                            dateAdded = photo.dateAdded,
                            isLiveCapture = false,
                            scrubbedText = "",
                            photoTitle = photo.title,
                            originalFileName = photo.fileName
                        )
                    }

                    repository.updatePhotoAiMetadata(
                        photoId = photo.id,
                        isScreenshot = true,
                        piiScrubbedText = "",
                        detectedIntent = heuristic.detectedIntent.takeIf { it != "OTHER" },
                        suggestedEventTitle = heuristic.suggestedEventTitle.takeIf { heuristic.detectedIntent == "EVENT" || heuristic.detectedIntent == "SHOPPING" }
                            ?: photo.title.takeIf { targetCatId == "cat_tobuy" },
                        suggestedEventDateMillis = heuristic.suggestedEventDateMillis,
                        targetCategoryId = targetCatId,
                        isProcessed = true,
                        isCloudEnriched = false,
                        suggestedEventDescription = heuristic.suggestedEventDescription,
                        semanticTags = semanticTags
                    )
                    continue
                }

                // Step 3 in IDEA.md: Local PII Scrubbing on device
                val piiResult = PiiScrubber.scrub(ocrText)

                // Step 4: Instant Local-First Processing (ML Kit Vision + OCR + SmartHeuristics + FTS5 + 128D Vector)
                // Guarantees zero latency UX (< 2s for all photos) and zero quota consumed on initial UI load.
                val heuristic = SmartHeuristicParser.parse(
                    scrubbedText = piiResult.scrubbedText,
                    photoTitle = photo.title,
                    customCategories = repository.categories.value,
                    hasFace = hasFace,
                    visualLabels = visualAnalysis.rawLabels
                )
                val targetCatId = heuristic.targetCategoryId
                val categoryNames = repository.categories.value
                    .filter { it.id == targetCatId }
                    .joinToString(" ") { it.name }

                val semanticTags = (heuristic.semanticTags + visualAnalysis.vietnameseSemanticTags).distinct()
                val semanticTagsStr = semanticTags.joinToString(" ")
                val fullSemanticText = buildString {
                    append(piiResult.scrubbedText)
                    if (!heuristic.suggestedEventTitle.isNullOrBlank()) append(" ").append(heuristic.suggestedEventTitle)
                    if (!heuristic.suggestedEventDescription.isNullOrBlank()) append(" ").append(heuristic.suggestedEventDescription)
                    if (semanticTagsStr.isNotBlank()) append(" ").append(semanticTagsStr)
                }.trim()

                dbHelper.indexPhotoFts(
                    photoId = photo.id,
                    title = photo.title,
                    scrubbedText = fullSemanticText,
                    intent = heuristic.detectedIntent,
                    categoryNames = categoryNames,
                    semanticTags = semanticTagsStr
                )

                if (repository.accountTier.value.isPro) {
                    val embedding = VectorSemanticSearchEngine.generateEmbedding(
                        text = fullSemanticText,
                        title = heuristic.suggestedEventTitle ?: photo.title,
                        intent = heuristic.detectedIntent,
                        categoryNames = if (categoryNames.isNotBlank()) listOf(categoryNames) else emptyList(),
                        customCategories = repository.categories.value
                    )
                    dbHelper.savePhotoVector(photo.id, embedding, heuristic.detectedIntent)
                }

                repository.updatePhotoAiMetadata(
                    photoId = photo.id,
                    isScreenshot = true,
                    piiScrubbedText = piiResult.scrubbedText,
                    detectedIntent = heuristic.detectedIntent.takeIf { it != "OTHER" },
                    suggestedEventTitle = heuristic.suggestedEventTitle.takeIf { heuristic.detectedIntent == "EVENT" || heuristic.detectedIntent == "SHOPPING" }
                        ?: photo.title.takeIf { targetCatId == "cat_tobuy" },
                    suggestedEventDateMillis = heuristic.suggestedEventDateMillis,
                    targetCategoryId = targetCatId,
                    isProcessed = true,
                    isCloudEnriched = false,
                    suggestedEventDescription = heuristic.suggestedEventDescription,
                    semanticTags = semanticTags
                )

                // Enqueue to background queue for progressive Cloud enrichment (Gemini batching & neural vectors)
                dbHelper.enqueueScreenshot(
                    photoId = photo.id,
                    filePath = photo.filePath,
                    dateAdded = photo.dateAdded,
                    isLiveCapture = false,
                    scrubbedText = piiResult.scrubbedText,
                    photoTitle = photo.title,
                    originalFileName = photo.fileName
                )
            } else if (!photo.piiScrubbedText.isNullOrBlank()) {
                // Keep FTS updated for previously processed photos
                val categoryNames = repository.categories.value
                    .filter { it.id in photo.categoryIds }
                    .joinToString(" ") { it.name }
                dbHelper.indexPhotoFts(
                    photoId = photo.id,
                    title = photo.title,
                    scrubbedText = photo.piiScrubbedText ?: "",
                    intent = photo.detectedIntent ?: "",
                    categoryNames = categoryNames
                )
            }
        }
    }

    fun setActiveTab(tab: AppTab) {
        _activeTab.value = tab
    }

    // Opens pure image view at selected photo
    fun viewPhoto(photo: Photo?) {
        _viewingPhotoId.value = photo?.id
    }

    fun viewPhotoById(photoId: String) {
        _viewingPhotoId.value = photoId
        _activeTab.value = AppTab.PHOTOS
    }

    fun closePhotoViewer() {
        _viewingPhotoId.value = null
        _photoSettings.value = null
    }

    // Opens settings from within the image view
    fun openPhotoSettings(photo: Photo) {
        _photoSettings.value = photo
    }

    fun closePhotoSettings() {
        _photoSettings.value = null
    }

    fun scanSinglePhoto(photoId: String) {
        val photo = repository.photos.value.firstOrNull { it.id == photoId } ?: return
        viewModelScope.launch {
            _isScanning.value = true
            val app = getApplication<Application>()
            val visualAnalysis = LocalImageLabeler.analyze(app, photo.filePath)
            val hasFace = visualAnalysis.isPerson
            val ocrText = if ((visualAnalysis.isNature || visualAnalysis.isAnimal) && !visualAnalysis.hasTextOrDoc) "" else MlKitOcrEngine.recognizeText(app, photo.filePath)
            val piiResult = PiiScrubber.scrub(ocrText)
            val parsed = CloudAiBackendClient.analyzeWithFallback(
                context = app,
                scrubbedText = piiResult.scrubbedText,
                photoTitle = photo.title,
                categories = repository.categories.value,
                isPro = repository.accountTier.value.isPro,
                hasFace = hasFace,
                visualLabels = visualAnalysis.rawLabels
            )

            val categoryNames = repository.categories.value
                .filter { it.id == parsed.targetCategoryId }
                .joinToString(" ") { it.name }

            dbHelper.indexPhotoFts(
                photoId = photo.id,
                title = photo.title,
                scrubbedText = piiResult.scrubbedText,
                intent = parsed.detectedIntent,
                categoryNames = categoryNames
            )

            // Index in Vector Semantic Search (IDEA.md Part 3 & Step 6 - Pro Tier)
            val embedding = VectorSemanticSearchEngine.generateEmbedding(
                text = piiResult.scrubbedText,
                title = photo.title,
                intent = parsed.detectedIntent,
                categoryNames = if (categoryNames.isNotBlank()) listOf(categoryNames) else emptyList(),
                customCategories = repository.categories.value
            )
            dbHelper.savePhotoVector(photo.id, embedding, parsed.detectedIntent)

            if (repository.accountTier.value.isPro && piiResult.scrubbedText.isNotBlank()) {
                val apiKey = CloudAiConfigManager.getApiKey(app)
                if (apiKey.isNotBlank()) {
                    val neuralVec = CloudAiBackendClient.getEmbedding(piiResult.scrubbedText, apiKey)
                    if (neuralVec != null) {
                        dbHelper.savePhotoNeuralVector(photo.id, neuralVec)
                    }
                }
            }

            dbHelper.markQueueItemProcessed(photo.id, "COMPLETED")

            dbHelper.recordProcessedScreenshot(
                photoId = photo.id,
                filePath = photo.filePath,
                dateAdded = photo.dateAdded,
                scrubbedText = piiResult.scrubbedText,
                detectedIntent = parsed.detectedIntent,
                eventTitle = parsed.suggestedEventTitle,
                eventDateMillis = parsed.suggestedEventDateMillis,
                targetCategoryId = parsed.targetCategoryId
            )

            repository.updatePhotoAiMetadata(
                photoId = photo.id,
                isScreenshot = true,
                piiScrubbedText = piiResult.scrubbedText,
                detectedIntent = parsed.detectedIntent,
                suggestedEventTitle = parsed.suggestedEventTitle,
                suggestedEventDateMillis = parsed.suggestedEventDateMillis,
                targetCategoryId = parsed.targetCategoryId,
                isProcessed = true
            )

            // Update photoSettings modal state if open
            if (_photoSettings.value?.id == photoId) {
                _photoSettings.value = repository.photos.value.firstOrNull { it.id == photoId }
            }
            _isScanning.value = false
        }
    }

    fun openCreateFolderDialog() {
        _showCreateFolderDialog.value = true
    }

    fun closeCreateFolderDialog() {
        _showCreateFolderDialog.value = false
    }

    fun openCreateCategoryDialog() {
        _showCreateCategoryDialog.value = true
    }

    fun closeCreateCategoryDialog() {
        _showCreateCategoryDialog.value = false
    }

    fun setFolderToEdit(folder: Folder?) {
        _folderToEdit.value = folder
    }

    fun setCategoryToEdit(category: Category?) {
        _categoryToEdit.value = category
    }

    fun setPhotoToDelete(photo: Photo?) {
        _photoToDelete.value = photo
    }

    fun setFolderToDelete(folder: Folder?) {
        _folderToDelete.value = folder
    }

    fun setCategoryToDelete(category: Category?) {
        _categoryToDelete.value = category
    }

    fun importPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isScanning.value = true
            val now = System.currentTimeMillis()
            val targetFolderId = currentFilter.value.folderId
            val targetCategoryIds = if (currentFilter.value.categoryId != null) {
                setOf(currentFilter.value.categoryId!!)
            } else {
                emptySet()
            }
            val app = getApplication<Application>()

            for ((index, uri) in uris.withIndex()) {
                val stored = storage.saveImageFromUri(uri)
                if (stored != null) {
                    val title = stored.fileName.substringBeforeLast(".")
                    val initialCategories = if (targetCategoryIds.isEmpty()) {
                        SmartHeuristicParser.parse("", "${targetFolderId ?: ""} $title").targetCategoryId?.let { setOf(it) } ?: emptySet()
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
                        dateAdded = now + index,
                        fileSizeBytes = stored.fileSizeBytes
                    )

                    if (SmartHeuristicParser.isJunkOrMeme(photo)) {
                        // Junk or meme: do not consume quota
                        repository.updatePhotoAiMetadata(
                            photoId = photo.id,
                            isScreenshot = true,
                            piiScrubbedText = "",
                            detectedIntent = null,
                            suggestedEventTitle = null,
                            suggestedEventDateMillis = null,
                            targetCategoryId = null,
                            isProcessed = true
                        )
                    } else {
                        val visualAnalysis = LocalImageLabeler.analyze(app, photo.filePath)
                        val hasFace = visualAnalysis.isPerson
                        val ocrText = if ((visualAnalysis.isNature || visualAnalysis.isAnimal) && !visualAnalysis.hasTextOrDoc) "" else MlKitOcrEngine.recognizeText(app, photo.filePath)
                        if (ocrText.isBlank() || ocrText.trim().length < 4) {
                            val heuristic = SmartHeuristicParser.parse(
                                scrubbedText = "",
                                photoTitle = "${photo.folderId} ${photo.title}",
                                hasFace = hasFace,
                                visualLabels = visualAnalysis.rawLabels
                            )
                            val targetCatId = heuristic.targetCategoryId
                            val categoryNames = repository.categories.value
                                .filter { it.id == targetCatId }
                                .joinToString(" ") { it.name }

                            dbHelper.indexPhotoFts(
                                photoId = photo.id,
                                title = photo.title,
                                scrubbedText = "",
                                intent = heuristic.detectedIntent,
                                categoryNames = categoryNames
                            )
                            val embedding = VectorSemanticSearchEngine.generateEmbedding(
                                text = "${photo.folderId ?: ""} $categoryNames",
                                title = photo.title,
                                intent = heuristic.detectedIntent,
                                categoryNames = if (categoryNames.isNotBlank()) listOf(categoryNames) else emptyList()
                            )
                            dbHelper.savePhotoVector(photo.id, embedding, heuristic.detectedIntent)

                            repository.updatePhotoAiMetadata(
                                photoId = photo.id,
                                isScreenshot = true,
                                piiScrubbedText = "",
                                detectedIntent = heuristic.detectedIntent.takeIf { it != "OTHER" },
                                suggestedEventTitle = heuristic.suggestedEventTitle.takeIf { heuristic.detectedIntent == "EVENT" },
                                suggestedEventDateMillis = heuristic.suggestedEventDateMillis,
                                targetCategoryId = targetCatId,
                                isProcessed = true
                            )
                        } else {
                            val piiResult = PiiScrubber.scrub(ocrText)
                            val nowScan = System.currentTimeMillis()
                            if (repository.canUseAiScan(nowScan)) {
                                repository.incrementQuotaUsage(nowScan)
                                dbHelper.enqueueScreenshot(
                                    photoId = photo.id,
                                    filePath = photo.filePath,
                                    dateAdded = photo.dateAdded,
                                    isLiveCapture = false,
                                    photoTitle = photo.title,
                                    originalFileName = photo.fileName
                                )
                                val parsed = CloudAiBackendClient.analyzeWithFallback(
                                    context = app,
                                    scrubbedText = piiResult.scrubbedText,
                                    photoTitle = photo.title,
                                    categories = repository.categories.value,
                                    isPro = repository.accountTier.value.isPro,
                                    hasFace = hasFace
                                )
                                val categoryNames = repository.categories.value
                                    .filter { it.id == parsed.targetCategoryId }
                                    .joinToString(" ") { it.name }

                                dbHelper.indexPhotoFts(
                                    photoId = photo.id,
                                    title = photo.title,
                                    scrubbedText = piiResult.scrubbedText,
                                    intent = parsed.detectedIntent,
                                    categoryNames = categoryNames
                                )
                                dbHelper.markQueueItemProcessed(photo.id, "COMPLETED")

                                // Index in Vector Semantic Search (IDEA.md Part 3 & Step 6 - Pro Tier)
                                val embedding = VectorSemanticSearchEngine.generateEmbedding(
                                    text = "${targetFolderId ?: ""} ${piiResult.scrubbedText}",
                                    title = photo.title,
                                    intent = parsed.detectedIntent,
                                    categoryNames = if (categoryNames.isNotBlank()) listOf(categoryNames) else emptyList(),
                                    customCategories = repository.categories.value
                                )
                                dbHelper.savePhotoVector(photo.id, embedding, parsed.detectedIntent)

                                dbHelper.recordProcessedScreenshot(
                                    photoId = photo.id,
                                    filePath = photo.filePath,
                                    dateAdded = photo.dateAdded,
                                    scrubbedText = piiResult.scrubbedText,
                                    detectedIntent = parsed.detectedIntent,
                                    eventTitle = parsed.suggestedEventTitle,
                                    eventDateMillis = parsed.suggestedEventDateMillis,
                                    targetCategoryId = parsed.targetCategoryId
                                )

                                repository.updatePhotoAiMetadata(
                                    photoId = photo.id,
                                    isScreenshot = true,
                                    piiScrubbedText = piiResult.scrubbedText,
                                    detectedIntent = parsed.detectedIntent,
                                    suggestedEventTitle = parsed.suggestedEventTitle,
                                    suggestedEventDateMillis = parsed.suggestedEventDateMillis,
                                    targetCategoryId = parsed.targetCategoryId,
                                    isProcessed = true
                                )
                            } else {
                                // Graceful Degradation: Free user out of quota -> use deterministic SmartHeuristicParser on device
                                val heuristic = SmartHeuristicParser.parse(
                                    scrubbedText = piiResult.scrubbedText,
                                    photoTitle = photo.title,
                                    customCategories = repository.categories.value,
                                    hasFace = hasFace
                                )
                                val categoryNames = repository.categories.value
                                    .filter { it.id == heuristic.targetCategoryId }
                                    .joinToString(" ") { it.name }

                                dbHelper.indexPhotoFts(
                                    photoId = photo.id,
                                    title = photo.title,
                                    scrubbedText = piiResult.scrubbedText,
                                    intent = heuristic.detectedIntent,
                                    categoryNames = categoryNames
                                )
                                val embedding = VectorSemanticSearchEngine.generateEmbedding(
                                    text = "${targetFolderId ?: ""} ${piiResult.scrubbedText}",
                                    title = photo.title,
                                    intent = heuristic.detectedIntent,
                                    categoryNames = if (categoryNames.isNotBlank()) listOf(categoryNames) else emptyList(),
                                    customCategories = repository.categories.value
                                )
                                dbHelper.savePhotoVector(photo.id, embedding, heuristic.detectedIntent)

                                repository.updatePhotoAiMetadata(
                                    photoId = photo.id,
                                    isScreenshot = true,
                                    piiScrubbedText = piiResult.scrubbedText,
                                    detectedIntent = heuristic.detectedIntent.takeIf { it != "OTHER" },
                                    suggestedEventTitle = heuristic.suggestedEventTitle.takeIf { heuristic.detectedIntent == "EVENT" },
                                    suggestedEventDateMillis = heuristic.suggestedEventDateMillis,
                                    targetCategoryId = heuristic.targetCategoryId,
                                    isProcessed = true
                                )
                            }
                        }
                    }
                }
            }
            _isScanning.value = false
        }
    }

    fun toggleFavorite(photoId: String) {
        val newState = repository.toggleFavorite(photoId)
        if (_photoSettings.value?.id == photoId) {
            _photoSettings.value = _photoSettings.value?.copy(isFavorite = newState)
        }
    }

    fun toggleFavoriteFilter() {
        repository.toggleFavoriteFilter()
    }

    fun toggleScreenshotsFilter() {
        repository.toggleScreenshotsFilter()
    }

    fun toggleUserTier(isPro: Boolean) {
        repository.toggleUserTier(isPro)
        val query = repository.currentFilter.value.searchQuery
        if (query.isNotBlank()) {
            setSearchQuery(query)
        }
    }

    fun markReminderCreated(photoId: String) {
        repository.markReminderCreated(photoId)
    }

    fun getAgingScreenshots(daysOld: Int = agingSettings.value.retentionDays): List<Photo> {
        return repository.getAgingPhotos(daysOld, System.currentTimeMillis())
    }

    fun cleanAgingScreenshots(photosToClean: List<Photo>) {
        if (photosToClean.isEmpty()) return
        viewModelScope.launch {
            for (p in photosToClean) {
                if (!p.filePath.startsWith("content://")) {
                    storage.deletePhysicalFile(p.filePath)
                }
                dbHelper.removePhotoFts(p.id)
                dbHelper.deletePhotoVector(p.id)
            }
            repository.batchDeletePhotos(photosToClean.map { it.id })
            _agingMonitorInfo.value = AgingMonitorManager.getLastCheckedInfo(getApplication())
        }
    }

    fun setCategoryRetentionRule(categoryId: String, retentionDays: Int) {
        repository.setCategoryRetentionRule(categoryId, retentionDays)
    }

    fun createFolder(name: String, colorHex: String = "#3B82F6", iconName: String = "folder") {
        if (name.isNotBlank()) {
            repository.createFolder(name, colorHex, iconName, System.currentTimeMillis())
        }
        closeCreateFolderDialog()
    }

    fun renameFolder(folderId: String, newName: String, colorHex: String, iconName: String) {
        if (newName.isNotBlank()) {
            repository.renameFolder(folderId, newName, colorHex, iconName)
        }
        setFolderToEdit(null)
    }

    fun deleteFolder(folderId: String) {
        repository.deleteFolder(folderId)
        setFolderToDelete(null)
    }

    fun createCategory(name: String, colorHex: String = "#6366F1", iconName: String = "tag"): Boolean {
        if (!accountTier.value.isPro) {
            closeCreateCategoryDialog()
            return false
        }
        val created = if (name.isNotBlank()) {
            repository.createCategory(name, colorHex, iconName, System.currentTimeMillis())
        } else {
            null
        }
        closeCreateCategoryDialog()
        if (created != null) {
            // Retroactive Auto-Categorization: Scan existing gallery photos for semantic matches with sequential indexing & UI loading
            triggerRetroactiveCategoryScan(created, forceReindex = true)
        }
        return created != null
    }

    /**
     * Scans existing gallery photos retroactively to find matches for a given category.
     * Uses 768D Neural embeddings if online, 128D on-device vectors, FTS5 and semantic tags.
     */
    fun triggerRetroactiveCategoryScan(
        category: Category,
        forceReindex: Boolean = false,
        onNoMatchesFound: (() -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _retroactiveScanningCategory.value = category
            try {
                // Ensure vector indexing completes completely before retroactive scanning to prevent race conditions
                ensureVectorsIndexedSuspend(forceReindex = forceReindex)

                val app = getApplication<Application>()
                val apiKey = CloudAiConfigManager.getApiKey(app)
                val photos = repository.photos.value

                val matchedPhotoIds = RetroactiveCategorizer.findMatchingPhotosForCategory(
                    category = category,
                    photos = photos,
                    dbHelper = dbHelper,
                    apiKey = apiKey,
                    context = app
                )

                if (matchedPhotoIds.isNotEmpty()) {
                    val matchedPhotos = photos.filter { it.id in matchedPhotoIds }
                    _retroactiveCategoryPrompt.value = RetroactiveCategoryPrompt(
                        category = category,
                        matchingPhotoIds = matchedPhotoIds,
                        previewPhotos = matchedPhotos.take(4)
                    )
                } else {
                    _noMatchesCategory.value = category
                    onNoMatchesFound?.invoke()
                }
            } finally {
                _retroactiveScanningCategory.value = null
            }
        }
    }

    /**
     * Applies retroactive category assignment after user confirmation.
     * Moves photos exclusively into the target category to eliminate clutter,
     * while retaining full calendar and shopping scheduling capabilities.
     */
    fun confirmRetroactiveCategoryMatch() {
        val prompt = _retroactiveCategoryPrompt.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val count = repository.reassignPhotosToCategory(prompt.matchingPhotoIds, prompt.category.id)
            _retroactiveCategoryPrompt.value = null
            if (count > 0) {
                // Re-index FTS5 for moved photos so search directly indexes the new category name
                val updatedPhotos = repository.photos.value.filter { it.id in prompt.matchingPhotoIds }
                for (p in updatedPhotos) {
                    dbHelper.indexPhotoFts(
                        photoId = p.id,
                        title = p.title,
                        scrubbedText = p.piiScrubbedText ?: p.description,
                        intent = p.detectedIntent ?: "",
                        categoryNames = prompt.category.name,
                        semanticTags = p.semanticTags.joinToString(" ")
                    )
                }
                ensureVectorsIndexed(forceReindex = true)
            }
        }
    }

    /**
     * Dismisses retroactive categorization prompt dialog.
     */
    fun dismissRetroactiveCategoryPrompt() {
        _retroactiveCategoryPrompt.value = null
    }

    fun getQuotaRemainingDays(): Int {
        val tier = accountTier.value
        if (tier.lastQuotaResetMillis <= 0L) return 30
        val elapsed = System.currentTimeMillis() - tier.lastQuotaResetMillis
        val thirtyDays = 30L * 24 * 60 * 60 * 1000L
        val remaining = thirtyDays - elapsed
        return if (remaining > 0) (remaining / (24 * 60 * 60 * 1000L)).toInt().coerceIn(1, 30) else 1
    }

    fun renameCategory(categoryId: String, newName: String, colorHex: String, iconName: String) {
        if (newName.isNotBlank()) {
            repository.renameCategory(categoryId, newName, colorHex, iconName)
            ensureVectorsIndexed(forceReindex = true)
        }
        setCategoryToEdit(null)
    }

    fun deleteCategory(categoryId: String) {
        repository.deleteCategory(categoryId)
        ensureVectorsIndexed(forceReindex = true)
        setCategoryToDelete(null)
    }

    fun updatePhotoDetails(
        photoId: String,
        title: String,
        description: String,
        folderId: String,
        categoryIds: Set<String>,
        isFavorite: Boolean? = null
    ) {
        val updated = repository.updatePhoto(photoId, title, description, folderId, categoryIds, isFavorite)
        if (_photoSettings.value?.id == photoId) {
            _photoSettings.value = updated
        }
        updated?.let { p ->
            val catNames = repository.categories.value
                .filter { it.id in p.categoryIds }
                .joinToString(" ") { it.name }
            dbHelper.indexPhotoFts(
                photoId = p.id,
                title = p.title,
                scrubbedText = p.piiScrubbedText ?: p.description,
                intent = p.detectedIntent ?: "",
                categoryNames = catNames
            )
        }
    }

    fun deletePhotoConfirmed(photo: Photo) {
        viewModelScope.launch {
            if (!photo.filePath.startsWith("content://")) {
                storage.deletePhysicalFile(photo.filePath)
            }
            dbHelper.removePhotoFts(photo.id)
            dbHelper.deletePhotoVector(photo.id)
            repository.deletePhoto(photo.id)
            if (_viewingPhotoId.value == photo.id) {
                _viewingPhotoId.value = null
            }
            if (_photoSettings.value?.id == photo.id) {
                _photoSettings.value = null
            }
            _photoToDelete.value = null
        }
    }

    fun filterByFolder(folderId: String?) {
        repository.setFolderFilter(folderId)
        _activeTab.value = AppTab.PHOTOS
    }

    fun filterByCategory(categoryId: String?) {
        repository.setCategoryFilter(categoryId)
        _activeTab.value = AppTab.PHOTOS
    }

    private var searchJob: Job? = null
    // Phase 3: In-memory cache for query neural embeddings to prevent redundant network calls
    private val queryEmbeddingCache = java.util.concurrent.ConcurrentHashMap<String, FloatArray>()

    fun setSearchQuery(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                _semanticMatchScores.value = emptyMap()
                repository.setSearchQuery("", null, null)
                return@launch
            }

            // Phase 3: Debounce typing by ~400ms to avoid firing heavy cloud requests on every keystroke
            kotlinx.coroutines.delay(400)

            val parsedQuery = TemporalQueryParser.parse(trimmed)
            val cleanQuery = parsedQuery.cleanTextQuery
            val dateRange = parsedQuery.dateRangeMillis

            // Phase 5: Privacy hardening - Scrub query of any phone, card, account, or email numbers before cloud
            val scrubbedQuery = PiiScrubber.scrub(cleanQuery).scrubbedText

            // 1. FTS5 BM25 Ranked keyword search (Phase 2 & 3: AND falling back to OR with column weights)
            val ftsRankedList = if (cleanQuery.isNotBlank()) {
                dbHelper.searchPhotosFtsRanked(cleanQuery)
            } else emptyList()

            val ftsIds = ftsRankedList.map { it.first }

            val isPro = repository.accountTier.value.isPro
            if (isPro) {
                val app = getApplication<Application>()
                val apiKey = CloudAiConfigManager.getApiKey(app)
                val neuralVectors = dbHelper.getAllPhotoNeuralVectors()

                // Phase 3: Pro cloud semantic embedding with RETRIEVAL_QUERY taskType and in-memory cache
                var vectorRankedList: List<Pair<String, Float>> = emptyList()

                if (apiKey.isNotBlank() && scrubbedQuery.isNotBlank() && neuralVectors.isNotEmpty()) {
                    var queryNeuralVec = queryEmbeddingCache[scrubbedQuery]
                    if (queryNeuralVec == null) {
                        queryNeuralVec = CloudAiBackendClient.getEmbedding(
                            text = scrubbedQuery,
                            apiKey = apiKey,
                            modelName = CloudAiConfigManager.DEFAULT_EMBEDDING_MODEL,
                            taskType = "RETRIEVAL_QUERY"
                        )
                        if (queryNeuralVec != null) {
                            queryEmbeddingCache[scrubbedQuery] = queryNeuralVec
                        }
                    }

                    if (queryNeuralVec != null) {
                        vectorRankedList = VectorSemanticSearchEngine.searchWithQueryVector(
                            queryVector = queryNeuralVec,
                            indexedVectors = neuralVectors,
                            minSimilarity = 0.30f
                        )
                    }
                }

                // Fallback to on-device 128D vector engine if cloud neural embedding wasn't available / offline
                if (vectorRankedList.isEmpty() && cleanQuery.isNotBlank()) {
                    val allVectors = dbHelper.getAllPhotoVectors()
                    val customCategories = repository.categories.value
                    vectorRankedList = VectorSemanticSearchEngine.search(
                        query = cleanQuery,
                        indexedVectors = allVectors,
                        customCategories = customCategories,
                        minSimilarity = 0.25f
                    )
                }

                // Phase 3: Reciprocal Rank Fusion (RRF) to merge keyword (BM25) and vector cosine similarity ranks
                val k = 60.0
                val allCandidateIds = (ftsIds + vectorRankedList.map { it.first }).distinct()

                val ftsRankMap = ftsIds.withIndex().associate { it.value to it.index + 1 }
                val vecRankMap = vectorRankedList.map { it.first }.withIndex().associate { it.value to it.index + 1 }

                val rrfScores = mutableListOf<Pair<String, Double>>()
                for (id in allCandidateIds) {
                    val rFts = ftsRankMap[id]
                    val rVec = vecRankMap[id]
                    val ftsScorePart = if (rFts != null) 1.0 / (k + rFts) else 0.0
                    val vecScorePart = if (rVec != null) 1.2 / (k + rVec) else 0.0
                    val totalRrf = ftsScorePart + vecScorePart
                    rrfScores.add(id to totalRrf)
                }

                val sortedRrf = rrfScores.sortedByDescending { it.second }

                // Dynamic cutoff relative to top score (cut off at 35% of top result)
                val topScore = sortedRrf.firstOrNull()?.second ?: 0.0
                val cutoff = topScore * 0.35
                val filteredRrf = sortedRrf.filter { it.second >= cutoff }

                val finalMatchingIds = filteredRrf.map { it.first }.toSet()

                // Normalize scores to [0.0..1.0] for UI badge
                val maxRrf = if (topScore > 0.0) topScore else 1.0
                val displayScores = filteredRrf.associate {
                    it.first to (it.second / maxRrf).toFloat()
                }
                _semanticMatchScores.value = displayScores

                repository.setSearchQuery(query, finalMatchingIds, dateRange = dateRange)
            } else {
                // Free Tier: FTS5 multi-term matching with BM25 ranking (no cloud quota cost)
                _semanticMatchScores.value = emptyMap()
                val finalMatchingIds = ftsIds.toSet()
                repository.setSearchQuery(query, if (cleanQuery.isBlank() && dateRange != null) null else finalMatchingIds, dateRange = dateRange)
            }
        }
    }

    suspend fun ensureVectorsIndexedSuspend(forceReindex: Boolean = false) = withContext(Dispatchers.IO) {
        val isPro = repository.accountTier.value.isPro
        if (!isPro) return@withContext

        _isVectorIndexing.value = true
        try {
            val app = getApplication<Application>()
            val prefs = app.getSharedPreferences("vector_prefs", Context.MODE_PRIVATE)
            val currentClusterVersion = prefs.getInt("vector_cluster_version", 0)
            // Cluster version 6 cleans boilerplate text (folder names, template descriptions) from vector embeddings
            val mustReindex = forceReindex || currentClusterVersion < 6

            val allVectors = if (mustReindex) emptyMap() else dbHelper.getAllPhotoVectors()
            val photos = repository.photos.value
            val customCategories = repository.categories.value

            for (p in photos) {
                if (mustReindex || !allVectors.containsKey(p.id)) {
                    val catNames = customCategories
                        .filter { it.id in p.categoryIds }
                        .map { it.name }
                    val semanticTagsStr = p.semanticTags.joinToString(" ")
                    val fullText = buildString {
                        append(p.piiScrubbedText ?: p.description)
                        if (!p.suggestedEventTitle.isNullOrBlank()) append(" ").append(p.suggestedEventTitle)
                        if (semanticTagsStr.isNotBlank()) append(" ").append(semanticTagsStr)
                    }.trim()

                    val embedding = VectorSemanticSearchEngine.generateEmbedding(
                        text = fullText,
                        title = p.suggestedEventTitle ?: p.title,
                        intent = p.detectedIntent ?: "",
                        categoryNames = catNames,
                        customCategories = customCategories
                    )
                    dbHelper.savePhotoVector(p.id, embedding, p.detectedIntent ?: "")
                }
            }

            if (mustReindex) {
                prefs.edit().putInt("vector_cluster_version", 6).apply()
            }

            // Phase 2: Backfill neural vectors on model version 1.0
            val missingBackfill = dbHelper.getPhotosNeedingNeuralVectorBackfill("1.0")
            if (missingBackfill.isNotEmpty()) {
                com.example.test2.services.CloudUpgradeWorker.enqueue(app)
            }
        } finally {
            _isVectorIndexing.value = false
        }
    }

    fun ensureVectorsIndexed(forceReindex: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            ensureVectorsIndexedSuspend(forceReindex)
        }
    }

    fun runAgingScanNow() {
        viewModelScope.launch(Dispatchers.IO) {
            AgingMonitorManager.checkAgingAndNotify(getApplication(), force = true)
            _agingMonitorInfo.value = AgingMonitorManager.getLastCheckedInfo(getApplication())
        }
    }

    fun setSortOption(sortOption: SortOption) {
        repository.setSortOption(sortOption)
    }

    fun clearFilters() {
        repository.clearFilters()
    }

    fun saveCloudAiConfig(apiKey: String, modelName: String, customEndpoint: String, enabled: Boolean) {
        CloudAiConfigManager.saveConfig(getApplication(), apiKey, modelName, customEndpoint, enabled)
        _cloudAiConfig.value = CloudAiConfigManager.getConfig(getApplication())
    }
}

data class RetroactiveCategoryPrompt(
    val category: Category,
    val matchingPhotoIds: List<String>,
    val previewPhotos: List<Photo> = emptyList()
)
