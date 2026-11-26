package com.example.test2.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.test2.PhotoApp
import com.example.test2.di.AppContainer
import com.example.test2.di.AppModule
import com.example.test2.di.AppViewModelFactory
import com.example.test2.presentation.main.MainUiState
import com.example.test2.services.AgingJobScheduler
import com.example.test2.services.ScreenshotJobScheduler
import com.example.test2.shared.models.ActionCardType
import com.example.test2.shared.models.AgingSettings
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Folder
import com.example.test2.shared.models.Photo
import com.example.test2.shared.models.PhotoFilter
import com.example.test2.shared.models.UserAccountTier
import com.example.test2.shared.repository.PhotoRepository
import com.example.test2.storage.DeviceMediaObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Slim coordinator ViewModel responsible for app-level lifecycle, permissions, tab navigation,
 * media observation, and delegating domain operations via [AppContainer].
 */
class PhotoAppViewModel(
    private val app: Application,
    val container: AppContainer
) : AndroidViewModel(app) {

    constructor(application: Application) : this(
        app = application,
        container = AppModule.createContainer(application, AppViewModelFactory.hasStoragePermission(application))
    )

    val repository: PhotoRepository = container.repository
    private val session = container.sessionState
    private val orchestrator = container.syncOrchestrator
    private val vaultManager = container.vaultManager
    private val vaultCoordinator = container.vaultActionCoordinator

    val photos: StateFlow<List<Photo>> = repository.photos
    val filteredPhotos: StateFlow<List<Photo>> = repository.filteredPhotos
    val folders: StateFlow<List<Folder>> = repository.folders
    val categories: StateFlow<List<Category>> = repository.categories
    val currentFilter: StateFlow<PhotoFilter> = repository.currentFilter
    val accountTier: StateFlow<UserAccountTier> = repository.accountTier
    val agingSettings: StateFlow<AgingSettings> = repository.agingSettings

    val activeTab: StateFlow<AppTab> = session.activeTab.asStateFlow()
    val viewingPhotoId: StateFlow<String?> = session.viewingPhotoId.asStateFlow()
    val hasStoragePermission: StateFlow<Boolean> = session.hasStoragePermission.asStateFlow()
    val isVaultEnabled: StateFlow<Boolean> = session.isVaultEnabled.asStateFlow()
    val isAppUnlocked: StateFlow<Boolean> = session.isAppUnlocked.asStateFlow()
    val isRestoringPhotos: StateFlow<Boolean> = session.isRestoringPhotos.asStateFlow()
    val restoreProgress: StateFlow<RestoreProgress> = session.restoreProgress.asStateFlow()
    val showRestoreSuccessBanner: StateFlow<String?> = session.showRestoreSuccessBanner.asStateFlow()
    val isScanning: StateFlow<Boolean> = session.isScanning.asStateFlow()
    val pendingAiCount: StateFlow<Int> = session.pendingAiCount.asStateFlow()
    val activeActionCardType: StateFlow<ActionCardType> = session.activeActionCardType.asStateFlow()

    val uiState: StateFlow<MainUiState> = combine(
        session.activeTab,
        session.hasStoragePermission,
        session.isScanning,
        session.pendingAiCount
    ) { tab, permission, scanning, pending ->
        MainUiState(
            activeTab = tab,
            hasStoragePermission = permission,
            isScanning = scanning,
            pendingAiCount = pending
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

    private val mediaObserver = DeviceMediaObserver(
        context = app,
        vaultManager = vaultManager,
        onMediaChanged = { scanDevicePhotos() }
    )

    init {
        repository.checkMonthlyQuotaReset(System.currentTimeMillis())
        ensureVectorsIndexed()
        AgingJobScheduler.schedule(app)
        container.queueDao.resetHangingProcessingItems()

        orchestrator.observeCloudAiSyncEvents(viewModelScope)
        viewModelScope.launch(Dispatchers.IO) {
            orchestrator.syncCompletedScreenshotsFromDb()
        }

        val pendingScope = vaultManager.getPendingVaultScope()
        if (pendingScope != null && vaultManager.hasAllFilesAccess()) {
            vaultManager.setPendingVaultScope(null)
            vaultCoordinator.activateVaultNow(pendingScope, viewModelScope)
        }

        if (session.hasStoragePermission.value) {
            if (!vaultManager.hasPromptedConsent()) {
                session.showPrivacyConsentDialog.value = true
            }
            ScreenshotJobScheduler.schedule(app)
            registerMediaObserver()
            scanDevicePhotos()
        }
    }

    fun setActiveTab(tab: AppTab) {
        session.activeTab.value = tab
    }

    fun viewPhotoById(photoId: String) {
        session.viewingPhotoId.value = photoId
        session.activeTab.value = AppTab.PHOTOS
    }

    fun setActionCardFilter(type: ActionCardType) {
        session.activeActionCardType.value = type
        viewModelScope.launch(Dispatchers.IO) {
            if (type == ActionCardType.ALL) {
                repository.setActionCardFilter(null, null)
            } else {
                val matchingIds = container.actionCardDao.getPhotoIdsByActionCardType(type)
                repository.setActionCardFilter(type, matchingIds)
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        session.hasStoragePermission.value = granted
        if (granted) {
            ScreenshotJobScheduler.schedule(app)
            registerMediaObserver()
            if (!vaultManager.hasPromptedConsent()) {
                session.showPrivacyConsentDialog.value = true
            }
            scanDevicePhotos()
        }
    }

    fun onResumeScan() {
        vaultCoordinator.checkAppLockOnResume()
        vaultCoordinator.checkVaultPermissionResumed(viewModelScope)
        if (PhotoApp.consumeMetadataDirty()) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.loadInitialData()
                orchestrator.syncCompletedScreenshotsFromDb()
                scanDevicePhotos()
            }
        } else {
            scanDevicePhotos()
        }
    }

    fun scanDevicePhotos() = orchestrator.scanDevicePhotos(viewModelScope)

    fun ensureVectorsIndexed(forceReindex: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            orchestrator.ensureVectorsIndexed(forceReindex)
        }
    }

    fun registerMediaObserver() = mediaObserver.register()

    fun notifyAppBackgrounded() {
        session.lastBackgroundTimestamp = System.currentTimeMillis()
    }

    fun unlockApp() {
        session.isAppUnlocked.value = true
        session.lastBackgroundTimestamp = 0L
    }

    fun confirmEnableVault(vaultAll: Boolean) = vaultCoordinator.confirmEnableVault(vaultAll, viewModelScope)
    fun restoreAllVaultedPhotos() = vaultCoordinator.restoreAllVaultedPhotos(viewModelScope)
    fun restoreSinglePhoto(photo: Photo) = vaultCoordinator.restoreSinglePhoto(photo, viewModelScope)
    fun vaultSinglePhoto(photo: Photo) = vaultCoordinator.vaultSinglePhoto(photo, viewModelScope)

    override fun onCleared() {
        super.onCleared()
        mediaObserver.unregister()
        container.containerScope.cancel()
    }
}
