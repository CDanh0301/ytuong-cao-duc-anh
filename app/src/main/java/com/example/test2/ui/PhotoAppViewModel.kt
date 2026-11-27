package com.example.test2.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.test2.ai.ScreenshotSyncOrchestrator
import com.example.test2.di.AppContainer
import com.example.test2.di.AppModule
import com.example.test2.di.AppViewModelFactory
import com.example.test2.presentation.main.MainNavigationState
import com.example.test2.presentation.main.MainUiState
import com.example.test2.presentation.vault.VaultActionCoordinator
import com.example.test2.presentation.vault.VaultStateStore
import com.example.test2.security.IVaultManager
import com.example.test2.shared.models.ActionCardType
import com.example.test2.shared.models.AgingSettings
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Folder
import com.example.test2.shared.models.Photo
import com.example.test2.shared.models.PhotoFilter
import com.example.test2.shared.models.UserAccountTier
import com.example.test2.shared.repository.IPhotoRepository
import com.example.test2.shared.usecases.BackgroundSyncPort
import com.example.test2.shared.usecases.MediaObserverPort
import com.example.test2.shared.usecases.MetadataSyncPort
import com.example.test2.shared.usecases.photo.ActionCardsUseCase
import com.example.test2.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Slim coordinator ViewModel responsible for app-level lifecycle, permissions, tab navigation,
 * deep-link routing, media observation, and delegating domain operations via injected ports and UseCases.
 */
class PhotoAppViewModel(
    app: Application,
    val repository: IPhotoRepository,
    private val navigationState: MainNavigationState,
    private val vaultState: VaultStateStore,
    private val orchestrator: ScreenshotSyncOrchestrator,
    private val vaultManager: IVaultManager,
    private val vaultCoordinator: VaultActionCoordinator,
    private val actionCardsUseCase: ActionCardsUseCase,
    private val backgroundSync: BackgroundSyncPort,
    private val metadataSync: MetadataSyncPort,
    private val containerScope: CoroutineScope?,
    private val mediaObserverFactory: (() -> Unit) -> MediaObserverPort
) : AndroidViewModel(app) {

    internal constructor(
        app: Application,
        container: AppContainer,
        ownsContainerScope: Boolean = false
    ) : this(
        app = app,
        repository = container.repository,
        navigationState = container.navigationState,
        vaultState = container.vaultStateStore,
        orchestrator = container.syncOrchestrator,
        vaultManager = container.vaultManager,
        vaultCoordinator = container.vaultActionCoordinator,
        actionCardsUseCase = container.actionCardsUseCase,
        backgroundSync = container.backgroundSyncPort,
        metadataSync = container.metadataSyncPort,
        containerScope = if (ownsContainerScope) container.containerScope else null,
        mediaObserverFactory = container.mediaObserverFactory
    )

    @Deprecated("Use AppViewModelFactory for dependency injection")
    constructor(application: Application) : this(
        app = application,
        container = AppModule.createContainer(application, AppViewModelFactory.hasStoragePermission(application)),
        ownsContainerScope = true
    )

    val photos: StateFlow<List<Photo>> = repository.photos
    val filteredPhotos: StateFlow<List<Photo>> = repository.filteredPhotos
    val folders: StateFlow<List<Folder>> = repository.folders
    val categories: StateFlow<List<Category>> = repository.categories
    val currentFilter: StateFlow<PhotoFilter> = repository.currentFilter
    val accountTier: StateFlow<UserAccountTier> = repository.accountTier
    val agingSettings: StateFlow<AgingSettings> = repository.agingSettings

    val activeTab: StateFlow<AppTab> = navigationState.activeTab.asStateFlow()
    val viewingPhotoId: StateFlow<String?> = navigationState.viewingPhotoId.asStateFlow()
    val hasStoragePermission: StateFlow<Boolean> = navigationState.hasStoragePermission.asStateFlow()
    val isVaultEnabled: StateFlow<Boolean> = vaultState.isVaultEnabled.asStateFlow()
    val isAppUnlocked: StateFlow<Boolean> = vaultState.isAppUnlocked.asStateFlow()
    val isRestoringPhotos: StateFlow<Boolean> = vaultState.isRestoringPhotos.asStateFlow()
    val restoreProgress: StateFlow<RestoreProgress> = vaultState.restoreProgress.asStateFlow()
    val showRestoreSuccessBanner: StateFlow<String?> = vaultState.showRestoreSuccessBanner.asStateFlow()
    val isScanning: StateFlow<Boolean> = orchestrator.isScanning
    val pendingAiCount: StateFlow<Int> = orchestrator.pendingAiCount
    val activeActionCardType: StateFlow<ActionCardType> = repository.currentFilter
        .map { it.actionCardType ?: ActionCardType.ALL }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ActionCardType.ALL)

    val uiState: StateFlow<MainUiState> = combine(
        navigationState.activeTab,
        navigationState.hasStoragePermission,
        orchestrator.isScanning,
        orchestrator.pendingAiCount
    ) { tab, permission, scanning, pending ->
        MainUiState(
            activeTab = tab,
            hasStoragePermission = permission,
            isScanning = scanning,
            pendingAiCount = pending
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

    private val mediaObserver: MediaObserverPort = mediaObserverFactory { scanDevicePhotos() }

    init {
        repository.checkMonthlyQuotaReset(System.currentTimeMillis())
        ensureVectorsIndexed()
        backgroundSync.scheduleBackgroundJobs()
        backgroundSync.resetHangingQueueItems()

        orchestrator.observeCloudAiSyncEvents(viewModelScope)
        viewModelScope.launch(Dispatchers.IO) {
            orchestrator.syncCompletedScreenshotsFromDb()
        }

        val pendingScope = vaultManager.getPendingVaultScope()
        if (pendingScope != null && vaultManager.hasAllFilesAccess()) {
            vaultManager.setPendingVaultScope(null)
            vaultCoordinator.activateVaultNow(pendingScope, viewModelScope)
        }

        if (navigationState.hasStoragePermission.value) {
            if (!vaultManager.hasPromptedConsent()) {
                vaultState.showPrivacyConsentDialog.value = true
            }
            registerMediaObserver()
            scanDevicePhotos()
        }
    }

    fun setActiveTab(tab: AppTab) {
        navigationState.activeTab.value = tab
    }

    fun viewPhotoById(photoId: String) {
        navigationState.viewingPhotoId.value = photoId
        navigationState.activeTab.value = AppTab.PHOTOS
    }

    fun handleLaunchExtras(
        photoId: String?,
        openHousekeeping: Boolean,
        widgetActionCardName: String?
    ) {
        if (!photoId.isNullOrBlank()) {
            viewPhotoById(photoId)
        }
        if (openHousekeeping) {
            setActiveTab(AppTab.CLEANER)
        }
        if (!widgetActionCardName.isNullOrBlank()) {
            val cardType = try {
                ActionCardType.valueOf(widgetActionCardName)
            } catch (e: IllegalArgumentException) {
                AppLogger.w("PhotoAppViewModel", "Unknown action card extra: $widgetActionCardName", e)
                null
            }
            if (cardType != null) {
                setActiveTab(AppTab.PHOTOS)
                setActionCardFilter(cardType)
            }
        }
    }

    fun setActionCardFilter(type: ActionCardType) {
        if (type == ActionCardType.ALL) {
            repository.setActionCardFilter(null, null)
            return
        }
        repository.setActionCardFilter(type, null)
        viewModelScope.launch(Dispatchers.IO) {
            val matchingIds = actionCardsUseCase.getPhotoIdsByType(type)
            if (repository.currentFilter.value.actionCardType == type) {
                repository.setActionCardFilter(type, matchingIds)
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        navigationState.hasStoragePermission.value = granted
        if (granted) {
            backgroundSync.scheduleBackgroundJobs()
            registerMediaObserver()
            if (!vaultManager.hasPromptedConsent()) {
                vaultState.showPrivacyConsentDialog.value = true
            }
            scanDevicePhotos()
        }
    }

    fun onResumeScan() {
        vaultCoordinator.checkAppLockOnResume()
        vaultCoordinator.checkVaultPermissionResumed(viewModelScope)
        if (metadataSync.consumeMetadataDirty()) {
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
        vaultState.lastBackgroundTimestamp = System.currentTimeMillis()
    }

    fun unlockApp() {
        vaultState.isAppUnlocked.value = true
        vaultState.lastBackgroundTimestamp = 0L
    }

    fun confirmEnableVault(vaultAll: Boolean) = vaultCoordinator.confirmEnableVault(vaultAll, viewModelScope)
    fun restoreAllVaultedPhotos() = vaultCoordinator.restoreAllVaultedPhotos(viewModelScope)
    fun restoreSinglePhoto(photo: Photo) = vaultCoordinator.restoreSinglePhoto(photo, viewModelScope)
    fun vaultSinglePhoto(photo: Photo) = vaultCoordinator.vaultSinglePhoto(photo, viewModelScope)

    override fun onCleared() {
        super.onCleared()
        mediaObserver.unregister()
        containerScope?.cancel()
    }
}
