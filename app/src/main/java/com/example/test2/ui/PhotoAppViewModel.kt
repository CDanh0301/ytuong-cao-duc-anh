package com.example.test2.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
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
import com.example.test2.ai.PhotoProcessingCoordinator
import com.example.test2.ai.SemanticSearchCoordinator
import com.example.test2.ai.SmartHeuristicParser
import com.example.test2.ai.TemporalQueryParser
import com.example.test2.ai.VectorSemanticSearchEngine
import com.example.test2.database.AppDatabaseHelper
import com.example.test2.notifications.ScreenshotNotificationHelper
import com.example.test2.security.PrivacyVaultManager
import com.example.test2.security.DeviceAuthManager
import com.example.test2.storage.isEncryptedPath
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.test2.services.AgingJobScheduler
import com.example.test2.services.AgingMonitorManager
import com.example.test2.services.ScreenshotDetectionManager
import com.example.test2.services.ScreenshotJobScheduler
import com.example.test2.services.ScreenshotQueueWorker
import com.example.test2.services.ScreenshotWatcherService
import com.example.test2.shared.models.ActionCard
import com.example.test2.shared.models.ActionCardType
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
import kotlinx.coroutines.flow.flowOn
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
    private val app: Application = application
    private val persistenceDriver = AndroidFilePersistenceDriver(application)
    val repository = PhotoRepository(persistenceDriver, viewModelScope)
    private val storage = AndroidPhotoStorage(application)
    private val dbHelper = AppDatabaseHelper.getInstance(application)
    private val processingCoordinator = PhotoProcessingCoordinator(application, repository, dbHelper)
    private val searchCoordinator = SemanticSearchCoordinator(application, repository, dbHelper)
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

    private val _restoreProgress = MutableStateFlow(RestoreProgress(0, 0))
    val restoreProgress: StateFlow<RestoreProgress> = _restoreProgress.asStateFlow()

    private val _showDisableVaultDialog = MutableStateFlow(false)
    val showDisableVaultDialog: StateFlow<Boolean> = _showDisableVaultDialog.asStateFlow()

    private val _showRestoreSuccessBanner = MutableStateFlow<String?>(null)
    val showRestoreSuccessBanner: StateFlow<String?> = _showRestoreSuccessBanner.asStateFlow()

    private val _showRequireDeviceLockDialog = MutableStateFlow(false)
    val showRequireDeviceLockDialog: StateFlow<Boolean> = _showRequireDeviceLockDialog.asStateFlow()

    private val _showVaultPermissionDeniedBanner = MutableStateFlow<String?>(null)
    val showVaultPermissionDeniedBanner: StateFlow<String?> = _showVaultPermissionDeniedBanner.asStateFlow()

    fun dismissRequireDeviceLockDialog() {
        _showRequireDeviceLockDialog.value = false
    }

    fun dismissVaultPermissionDeniedBanner() {
        _showVaultPermissionDeniedBanner.value = null
    }

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

    // Smart Action Cards filtering state
    private val _activeActionCardType = MutableStateFlow(ActionCardType.ALL)
    val activeActionCardType: StateFlow<ActionCardType> = _activeActionCardType.asStateFlow()

    private val _actionCardPhotoCounts = MutableStateFlow<Map<ActionCardType, Int>>(emptyMap())
    val actionCardPhotoCounts: StateFlow<Map<ActionCardType, Int>> = _actionCardPhotoCounts.asStateFlow()

    fun refreshActionCardCounts() {
        viewModelScope.launch(Dispatchers.IO) {
            dbHelper.cleanOrphanedActionCards()
            val counts = dbHelper.getActionCardsPhotoCountByType()
            _actionCardPhotoCounts.value = counts
        }
    }

    private var contentObserver: ContentObserver? = null
    private var pendingVaultScanAfterPermission = false
    // Must be declared BEFORE init {} because init synchronously triggers scanDevicePhotos()
    // on Dispatchers.Main.immediate via viewModelScope.launch.
    private var lastBackgroundTimestamp = 0L
    private val scanLock = Mutex()
    private val hasPendingScanRequest = AtomicBoolean(false)
    private var onDeviceTriageJob: Job? = null
    private var searchJob: Job? = null

    init {
        repository.checkMonthlyQuotaReset(System.currentTimeMillis())
        ensureVectorsIndexed()
        AgingJobScheduler.schedule(application)
        dbHelper.resetHangingProcessingItems()

        observeCloudAiSyncEvents()
        syncCompletedScreenshotsFromDb()
        refreshActionCardCounts()

        // If OS restarted the process after user granted MANAGE_EXTERNAL_STORAGE in Settings,
        // immediately resume and activate the pending vault scope.
        val pendingScope = PrivacyVaultManager.getPendingVaultScope(app)
        if (pendingScope != null && PrivacyVaultManager.hasAllFilesAccess(app)) {
            PrivacyVaultManager.setPendingVaultScope(app, null)
            activateVaultNow(pendingScope)
        }

        if (_hasStoragePermission.value) {
            checkPrivacyConsentPrompt()
            ScreenshotJobScheduler.schedule(application)
            registerMediaObserver()
            scanDevicePhotos()
        }
    }

    private fun buildRecoveredVaultedPhoto(
        record: com.example.test2.database.ProcessedScreenshotRecord,
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

    private fun observeCloudAiSyncEvents() {
        viewModelScope.launch {
            CloudAiSyncNotifier.syncEvents.collect { completedIds ->
                repository.awaitInitialLoad()
                val records = dbHelper.getProcessedScreenshotsByIds(completedIds)
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

    private fun syncCompletedScreenshotsFromDb() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.awaitInitialLoad()
            val records = dbHelper.getAllCompletedOrVaultedScreenshots()
            if (records.isNotEmpty()) {
                val recordMap = records.associateBy { it.photoId }
                val currentPhotos = repository.photos.value
                val existingIds = currentPhotos.map { it.id }.toSet()
                val updates = mutableListOf<PhotoAiMetadataUpdate>()
                val missingVaultedPhotos = mutableListOf<Photo>()

                // 1. Reconciliation: recover any background-processed vaulted photos that were evicted from MediaStore
                for (record in records) {
                    if (record.photoId !in existingIds && record.filePath.isEncryptedPath()) {
                        val vaultedFile = File(record.filePath)
                        if (vaultedFile.exists() && vaultedFile.length() > 0L) {
                            missingVaultedPhotos.add(
                                buildRecoveredVaultedPhoto(record, vaultedFile, isCompleted = record.status == "COMPLETED")
                            )
                        }
                    }
                }

                if (missingVaultedPhotos.isNotEmpty()) {
                    repository.addVaultedPhotos(missingVaultedPhotos)
                }

                // 2. Synchronize metadata updates for photos currently in memory
                for (photo in currentPhotos) {
                    val record = recordMap[photo.id]
                    if (record != null) {
                        val currentCatId = photo.categoryIds.firstOrNull { it.startsWith("cat_") || it in repository.categories.value.map { c -> c.id } }
                        val catNeedsUpdate = !record.targetCategoryId.isNullOrBlank() && currentCatId != record.targetCategoryId
                        val pathNeedsUpdate = record.filePath.isEncryptedPath() && !photo.filePath.isEncryptedPath()
                        val metaNeedsUpdate = !record.eventDescription.isNullOrBlank() && photo.suggestedEventDescription != record.eventDescription
                        val isCompleted = record.status == "COMPLETED"

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
    }

    private fun checkInitialPermission(): Boolean {
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
            ScreenshotJobScheduler.schedule(app)
            registerMediaObserver()
            if (!PrivacyVaultManager.hasPromptedConsent(app)) {
                _showPrivacyConsentDialog.value = true
            }
            scanDevicePhotos()
        }
    }

    fun checkPrivacyConsentPrompt() {
        if (_hasStoragePermission.value && !PrivacyVaultManager.hasPromptedConsent(app)) {
            _showPrivacyConsentDialog.value = true
        }
    }

    fun onEnableVault() {
        PrivacyVaultManager.setPromptedConsent(app, true)
        _showPrivacyConsentDialog.value = false
        if (!DeviceAuthManager.isDeviceSecure(app)) {
            _showRequireDeviceLockDialog.value = true
            return
        }
        confirmEnableVault(vaultAll = true)
    }

    fun onKeepStandard() {
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
        if (!DeviceAuthManager.isDeviceSecure(app)) {
            _showRequireDeviceLockDialog.value = true
        } else {
            _showEnableVaultScopeDialog.value = true
        }
    }

    fun dismissEnableVaultScopeDialog() {
        _showEnableVaultScopeDialog.value = false
    }

    fun confirmEnableVault(vaultAll: Boolean) {
        val scope = if (vaultAll) PrivacyVaultManager.SCOPE_ALL else PrivacyVaultManager.SCOPE_NEW_ONLY
        _showEnableVaultScopeDialog.value = false
        if (!PrivacyVaultManager.hasAllFilesAccess(app)) {
            // Persist across process kill: Android kills the app when MANAGE_EXTERNAL_STORAGE changes in Settings
            PrivacyVaultManager.setPendingVaultScope(app, scope)
            _showAllFilesAccessDialog.value = true
        } else {
            PrivacyVaultManager.setPendingVaultScope(app, null)
            activateVaultNow(scope)
        }
    }

    private fun activateVaultNow(scope: String) {
        if (scope == PrivacyVaultManager.SCOPE_ALL) {
            PrivacyVaultManager.clearAllRestoredExempts(app)
        }
        PrivacyVaultManager.setVaultScope(app, scope)
        PrivacyVaultManager.setVaultEnabledSince(app, System.currentTimeMillis())
        PrivacyVaultManager.setVaultEnabled(app, true)
        _isVaultEnabled.value = true
        _isAppUnlocked.value = true
        scanDevicePhotos()
    }

    fun notifyAppBackgrounded() {
        lastBackgroundTimestamp = System.currentTimeMillis()
    }

    fun checkAppLockOnResume() {
        if (_isVaultEnabled.value) {
            val elapsed = System.currentTimeMillis() - lastBackgroundTimestamp
            val gracePeriodMillis = 60_000L // 60 seconds grace period
            if (lastBackgroundTimestamp > 0L && elapsed >= gracePeriodMillis) {
                _isAppUnlocked.value = false
            }
        }
    }

    fun checkVaultPermissionResumed() {
        val pendingScope = PrivacyVaultManager.getPendingVaultScope(app)
        if (pendingScope != null) {
            PrivacyVaultManager.setPendingVaultScope(app, null)
            if (PrivacyVaultManager.hasAllFilesAccess(app)) {
                activateVaultNow(pendingScope)
            } else {
                _showVaultPermissionDeniedBanner.value = "Két bảo mật chưa được bật do thiếu quyền Quản lý tất cả tệp"
            }
        }
    }

    fun onResumeScan() {
        checkAppLockOnResume()
        checkVaultPermissionResumed()
        scanDevicePhotos()
    }

    fun requestDisableVault() {
        val vaultedCount = repository.photos.value.count {
            it.filePath.isEncryptedPath() || (it.isScreenshot && !it.filePath.startsWith("content://"))
        }
        if (vaultedCount > 0) {
            _showDisableVaultDialog.value = true
        } else {
            PrivacyVaultManager.setVaultEnabled(app, false)
            PrivacyVaultManager.clearAllRestoredExempts(app)
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
            repository.awaitInitialLoad()
            _showDisableVaultDialog.value = false

            var restoredAny = false
            scanLock.withLock {
                val currentPhotos = repository.photos.value
                // Restore oldest -> newest so MediaStore _ID and filesystem order match natural chronology
                val vaultedList = currentPhotos.filter {
                    it.filePath.isEncryptedPath() || (it.isScreenshot && !it.filePath.startsWith("content://"))
                }.sortedWith(PhotoRepository.chronologicalAscComparator)

                if (vaultedList.isEmpty()) {
                    PrivacyVaultManager.setVaultEnabled(app, false)
                    PrivacyVaultManager.clearAllRestoredExempts(app)
                    _isVaultEnabled.value = false
                    _isAppUnlocked.value = true
                    _isRestoringPhotos.value = false
                    return@withLock
                }

                _isRestoringPhotos.value = true
                _restoreProgress.value = RestoreProgress(0, vaultedList.size)

                // Disable vault synchronously BEFORE the restore loop so neither ContentObserver scans
                // nor background ScreenshotQueueWorker can re-vault photos mid-restore.
                val wasVaultEnabled = PrivacyVaultManager.isVaultEnabled(app)
                PrivacyVaultManager.setVaultEnabled(app, false)
                _isVaultEnabled.value = false

                var successCount = 0
                var failedCount = 0
                val restoredNewIds = mutableListOf<String>()

                PrivacyVaultManager.beginInternalEviction()
                try {
                    for (photo in vaultedList) {
                        val targetRestoreName = PrivacyVaultManager.sanitizeRestoreFileName(
                            rawTitle = photo.title,
                            encryptedFilePath = photo.filePath,
                            fallbackFileName = photo.fileName
                        )
                        val result = PrivacyVaultManager.restoreAndEvictScreenshot(
                            context = app,
                            vaultedFilePath = photo.filePath,
                            originalFileName = targetRestoreName,
                            originalDateAddedMillis = photo.dateAdded,
                            markExemptOnRestore = false
                        )
                        if (result != null) {
                            val newPhotoId = "device_${result.newMediaId}"
                            restoredNewIds.add(newPhotoId)
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
                        _restoreProgress.value = RestoreProgress(successCount + failedCount, vaultedList.size)
                    }
                } finally {
                    delay(350)
                    PrivacyVaultManager.endInternalEviction()
                }

                _isRestoringPhotos.value = false
                restoredAny = successCount > 0

                if (failedCount == 0) {
                    PrivacyVaultManager.setVaultEnabled(app, false)
                    PrivacyVaultManager.clearAllRestoredExempts(app)
                    _isVaultEnabled.value = false
                    _isAppUnlocked.value = true
                    _showRestoreSuccessBanner.value = "Đã khôi phục thành công toàn bộ $successCount ảnh về Bộ sưu tập của máy"
                } else {
                    for (restoredId in restoredNewIds) {
                        PrivacyVaultManager.markRestoredExempt(app, restoredId)
                    }
                    PrivacyVaultManager.setVaultEnabled(app, wasVaultEnabled)
                    _isVaultEnabled.value = wasVaultEnabled
                    _showRestoreSuccessBanner.value = "Đã khôi phục $successCount ảnh. Còn $failedCount ảnh chưa thể xuất ra (vẫn được lưu an toàn trong Két). Vui lòng thử lại sau."
                }
            }

            refreshActionCardCounts()
            if (restoredAny) {
                scanDevicePhotos()
            }
        }
    }

    fun restoreSinglePhoto(photo: Photo) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.awaitInitialLoad()
            val restoredNewPhotoId: String? = scanLock.withLock {
                PrivacyVaultManager.beginInternalEviction()
                try {
                    val targetRestoreName = PrivacyVaultManager.sanitizeRestoreFileName(
                        rawTitle = photo.title,
                        encryptedFilePath = photo.filePath,
                        fallbackFileName = photo.fileName
                    )
                    val result = PrivacyVaultManager.restoreAndEvictScreenshot(
                        context = app,
                        vaultedFilePath = photo.filePath,
                        originalFileName = targetRestoreName,
                        originalDateAddedMillis = photo.dateAdded,
                        markExemptOnRestore = true
                    ) ?: return@withLock null

                    val newPhotoId = "device_${result.newMediaId}"
                    PrivacyVaultManager.markRestoredExempt(app, newPhotoId)
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
                            (it.filePath.isEncryptedPath() || (it.isScreenshot && !it.filePath.startsWith("content://")))
                    }
                    val hasRemainingEncFiles = File(app.filesDir, "photos")
                        .listFiles()
                        ?.any { it.isFile && it.name.endsWith(".enc") && it.length() > 0L } == true
                    if (remainingVaulted == 0 && !hasRemainingEncFiles) {
                        PrivacyVaultManager.setVaultEnabled(app, false)
                        PrivacyVaultManager.clearAllRestoredExempts(app)
                        _isVaultEnabled.value = false
                    }
                    newPhotoId
                } finally {
                    delay(350)
                    PrivacyVaultManager.endInternalEviction()
                }
            }

            if (restoredNewPhotoId != null) {
                refreshActionCardCounts()
                _showRestoreSuccessBanner.value = "Đã khôi phục ảnh '${photo.title}' về Bộ sưu tập của máy"
                if (_photoSettings.value?.id == photo.id) {
                    _photoSettings.value = repository.photos.value.firstOrNull { it.id == restoredNewPhotoId }
                }
                scanDevicePhotos()
            } else {
                _showRestoreSuccessBanner.value = "Không thể khôi phục ảnh '${photo.title}'. Vui lòng thử lại sau."
            }
        }
    }

    fun vaultSinglePhoto(photo: Photo) {
        if (!PrivacyVaultManager.hasAllFilesAccess(app)) {
            _showAllFilesAccessDialog.value = true
            return
        }
        if (!DeviceAuthManager.isDeviceSecure(app)) {
            _showRequireDeviceLockDialog.value = true
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.awaitInitialLoad()
            val success = scanLock.withLock {
                PrivacyVaultManager.beginInternalEviction()
                try {
                    PrivacyVaultManager.clearRestoredExempt(app, photo.id)
                    val uri = if (photo.filePath.startsWith("content://")) Uri.parse(photo.filePath) else null
                    val vaulted = PrivacyVaultManager.vaultAndEvictScreenshot(
                        app,
                        uri,
                        photo.filePath,
                        photo.fileName
                    )
                    if (vaulted != null) {
                        dbHelper.syncVaultedPathToQueue(
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
                        PrivacyVaultManager.setVaultEnabled(app, true)
                        _isVaultEnabled.value = true
                        _isAppUnlocked.value = true
                        true
                    } else {
                        false
                    }
                } finally {
                    delay(300)
                    PrivacyVaultManager.endInternalEviction()
                }
            }
            if (success) {
                _showRestoreSuccessBanner.value = "Đã chuyển ảnh '${photo.title}' vào Két bảo mật an toàn"
                if (_photoSettings.value?.id == photo.id) {
                    _photoSettings.value = repository.photos.value.firstOrNull { it.id == photo.id }
                }
            } else {
                _showRestoreSuccessBanner.value = "Không thể đưa ảnh '${photo.title}' vào Két bảo mật."
            }
        }
    }

    fun dismissRestoreSuccessBanner() {
        _showRestoreSuccessBanner.value = null
    }

    fun dismissAllFilesAccessDialog() {
        _showAllFilesAccessDialog.value = false
    }

    fun cancelAllFilesAccessDialog() {
        PrivacyVaultManager.setPendingVaultScope(app, null)
        _showAllFilesAccessDialog.value = false
    }

    fun unlockApp() {
        _isAppUnlocked.value = true
        lastBackgroundTimestamp = 0L
    }

    fun lockApp() {
        if (_isVaultEnabled.value) {
            _isAppUnlocked.value = false
        }
    }

    fun registerMediaObserver() {
        if (contentObserver != null) return
        contentObserver = object : ContentObserver(null) {
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
            app.contentResolver.registerContentObserver(
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
                app.contentResolver.unregisterContentObserver(it)
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
            if (!scanLock.tryLock()) {
                // Another scan is actively in progress. Flag pending scan to execute immediately after completion.
                // NEVER cancel the current scan to prevent partial file writes or desynchronized state!
                hasPendingScanRequest.set(true)
                return@launch
            }

            try {
                do {
                    hasPendingScanRequest.set(false)
                    performScanDevicePhotosInternal()
                } while (hasPendingScanRequest.getAndSet(false))
            } finally {
                scanLock.unlock()
            }
        }
    }

    private suspend fun performScanDevicePhotosInternal() {
        _isLocalScanning.value = true
        updateScanningState()
        repository.awaitInitialLoad()
        val scanResult = mediaScanner.scanDeviceImages()
        if (scanResult.photos.isNotEmpty() || scanResult.folders.isNotEmpty()) {
            val processedPhotos = if (_isVaultEnabled.value && PrivacyVaultManager.hasAllFilesAccess(app)) {
                withContext(Dispatchers.IO) {
                    PrivacyVaultManager.beginInternalEviction()
                    try {
                        scanResult.photos.map { photo ->
                            if (photo.isScreenshot && !photo.filePath.isEncryptedPath() && PrivacyVaultManager.shouldAutoVaultPhoto(app, photo.id, photo.dateAdded)) {
                                val uri = if (photo.filePath.startsWith("content://")) Uri.parse(photo.filePath) else null
                                val physicalPath = scanResult.physicalPaths[photo.id] ?: photo.filePath
                                val vaulted = PrivacyVaultManager.vaultAndEvictScreenshot(
                                    app,
                                    uri,
                                    physicalPath,
                                    photo.fileName
                                )
                                if (vaulted != null) {
                                    dbHelper.syncVaultedPathToQueue(
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
                        PrivacyVaultManager.endInternalEviction()
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

    private fun launchOnDeviceTriage() {
        onDeviceTriageJob?.cancel()
        onDeviceTriageJob = viewModelScope.launch(Dispatchers.IO) {
            processNewScreenshots()
            // After on-device triage completes processing all new screenshots,
            // trigger WorkManager to progressively enrich them via Cloud AI
            ScreenshotQueueWorker.enqueue(app)
        }
    }

    // IDEA.md: Process unprocessed screenshots through ML Kit OCR, PII scrubber, Smart Heuristics & FTS5 indexing
    private suspend fun processNewScreenshots() {
        val currentPhotos = repository.photos.value
        android.util.Log.d("PhotoTriage", "processNewScreenshots started, total photos: ${currentPhotos.size}")
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
                // Ensure action cards are populated for screenshots with OCR
                if (photo.isScreenshot && scrubbed.isNotBlank() && dbHelper.getActionCardsForPhoto(photo.id).isEmpty()) {
                    val cards = com.example.test2.ai.LocalActionCardExtractor.extractCardsFromText(
                        photoId = photo.id,
                        rawOcrText = scrubbed,
                        photoTitle = photo.title
                    )
                    if (cards.isNotEmpty()) {
                        dbHelper.replaceActionCardsForPhoto(photo.id, cards)
                    }
                }
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
        refreshActionCardCounts()
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
            processingCoordinator.scanPhotoWithCloud(photo)
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

                    val nowScan = System.currentTimeMillis()
                    if (repository.canUseAiScan(nowScan) && !SmartHeuristicParser.isJunkOrMeme(photo)) {
                        repository.incrementQuotaUsage(nowScan)
                        dbHelper.enqueueScreenshot(
                            photoId = photo.id,
                            filePath = photo.filePath,
                            dateAdded = photo.dateAdded,
                            isLiveCapture = false,
                            photoTitle = photo.title,
                            originalFileName = photo.fileName
                        )
                        processingCoordinator.scanPhotoWithCloud(photo, targetFolderId)
                    } else {
                        processingCoordinator.runOnDevicePipeline(photo, targetFolderId, targetCategoryIds)
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
                dbHelper.purgePhotoData(p.id)
            }
            repository.batchDeletePhotos(photosToClean.map { it.id })
            refreshActionCardCounts()
            _agingMonitorInfo.value = AgingMonitorManager.getLastCheckedInfo(app)
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
            dbHelper.purgePhotoData(photo.id)
            repository.deletePhoto(photo.id)
            refreshActionCardCounts()
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

    fun setActionCardFilter(type: ActionCardType) {
        _activeActionCardType.value = type
        viewModelScope.launch(Dispatchers.IO) {
            if (type == ActionCardType.ALL) {
                repository.setActionCardFilter(null, null)
            } else {
                val matchingIds = dbHelper.getPhotoIdsByActionCardType(type)
                repository.setActionCardFilter(type, matchingIds)
            }
        }
    }

    fun getActionCardsForPhoto(photoId: String): List<ActionCard> {
        return dbHelper.getActionCardsForPhoto(photoId)
    }

    /**
     * Reactively observes Smart Action Cards for a photo off the main thread.
     * Automatically emits updates when cards are extracted or re-extracted.
     */
    fun observeActionCards(photoId: String): kotlinx.coroutines.flow.Flow<List<ActionCard>> = kotlinx.coroutines.flow.flow {
        emit(dbHelper.getActionCardsForPhoto(photoId))

        if (com.example.test2.ai.cards.QualityFlags.REEXTRACT_ON_OPEN) {
            checkAndReextractCardsIfOld(photoId)
        }

        com.example.test2.ai.CloudAiSyncNotifier.syncEvents.collect { updatedPhotoIds ->
            if (photoId in updatedPhotoIds) {
                emit(dbHelper.getActionCardsForPhoto(photoId))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun checkAndReextractCardsIfOld(photoId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dbHelper.getActionCardsForPhoto(photoId)
            val needsExtraction = existing.isEmpty() || existing.any { card ->
                val ev = try {
                    org.json.JSONObject(card.payloadJson).optInt("ev", 1)
                } catch (_: Exception) { 1 }
                ev < com.example.test2.ai.cards.CardIds.EXTRACTOR_VERSION
            }
            if (needsExtraction) {
                val photo = repository.photos.value.find { it.id == photoId } ?: return@launch
                val result = com.example.test2.ai.OnDeviceAnalysis.analyze(
                    context = app,
                    photoId = photoId,
                    filePath = photo.filePath,
                    photoTitle = photo.title
                )
                if (result != null) {
                    dbHelper.replaceActionCardsForPhoto(photoId, result.cards)
                    com.example.test2.ai.CloudAiSyncNotifier.notifyBatchCompleted(listOf(photoId))
                    refreshActionCardCounts()
                }
            }
        }
    }

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

            val result = searchCoordinator.search(query)
            _semanticMatchScores.value = result.semanticMatchScores
            repository.setSearchQuery(query, result.matchingIds, dateRange = result.dateRange)
        }
    }

    suspend fun ensureVectorsIndexedSuspend(forceReindex: Boolean = false) = withContext(Dispatchers.IO) {
        _isVectorIndexing.value = true
        try {
            searchCoordinator.ensureVectorsIndexed(forceReindex)
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
            AgingMonitorManager.checkAgingAndNotify(app, force = true)
            _agingMonitorInfo.value = AgingMonitorManager.getLastCheckedInfo(app)
        }
    }

    fun setSortOption(sortOption: SortOption) {
        repository.setSortOption(sortOption)
    }

    fun clearFilters() {
        _activeActionCardType.value = ActionCardType.ALL
        repository.clearFilters()
    }

    fun saveCloudAiConfig(apiKey: String, modelName: String, customEndpoint: String, enabled: Boolean) {
        CloudAiConfigManager.saveConfig(app, apiKey, modelName, customEndpoint, enabled)
        _cloudAiConfig.value = CloudAiConfigManager.getConfig(app)
    }

    companion object {
        private const val CURRENT_VECTOR_CLUSTER_VERSION = 6
        private const val NEURAL_VECTOR_MODEL_VERSION = "1.0"
    }
}
