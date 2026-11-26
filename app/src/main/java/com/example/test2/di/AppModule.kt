package com.example.test2.di

import android.app.Application
import android.content.Context
import com.example.test2.ai.CloudAiConfig
import com.example.test2.ai.PhotoProcessingCoordinator
import com.example.test2.ai.ScreenshotSyncOrchestrator
import com.example.test2.ai.SemanticSearchCoordinator
import com.example.test2.data.local.dao.ActionCardDao
import com.example.test2.data.local.dao.FtsSearchDao
import com.example.test2.data.local.dao.ScreenshotQueueDao
import com.example.test2.data.local.dao.VectorDao
import com.example.test2.data.local.prefs.CloudAiPrefsDataSource
import com.example.test2.data.remote.GeminiApiService
import com.example.test2.data.remote.GeminiApiServiceImpl
import com.example.test2.data.repository.AndroidAgingMonitorAdapter
import com.example.test2.data.repository.AndroidFilePersistenceDataSource
import com.example.test2.data.repository.AndroidFtsIndexAdapter
import com.example.test2.data.repository.AndroidMediaScannerAdapter
import com.example.test2.data.repository.AndroidPhotoImportAdapter
import com.example.test2.data.repository.AndroidPhotoStorageCleanerAdapter
import com.example.test2.data.repository.AndroidQueueSyncAdapter
import com.example.test2.data.repository.AndroidRetroactiveCategorizerAdapter
import com.example.test2.data.repository.AndroidSemanticSearchAdapter
import com.example.test2.data.repository.AndroidVaultPortAdapter
import com.example.test2.data.repository.IAppDataSource
import com.example.test2.database.AppDatabaseHelper
import com.example.test2.presentation.vault.VaultActionCoordinator
import com.example.test2.security.IVaultManager
import com.example.test2.security.VaultManagerImpl
import com.example.test2.services.AgingMonitorManager
import com.example.test2.shared.models.ActionCardType
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Folder
import com.example.test2.shared.models.Photo
import com.example.test2.shared.repository.PhotoRepository
import com.example.test2.shared.usecases.category.CreateCategoryUseCase
import com.example.test2.shared.usecases.category.DeleteCategoryUseCase
import com.example.test2.shared.usecases.category.RetroactiveCategoryUseCase
import com.example.test2.shared.usecases.housekeeping.AgingCleanupUseCase
import com.example.test2.shared.usecases.photo.DeletePhotoUseCase
import com.example.test2.shared.usecases.photo.ImportPhotosUseCase
import com.example.test2.shared.usecases.photo.ScanDevicePhotosUseCase
import com.example.test2.shared.usecases.photo.ToggleFavoriteUseCase
import com.example.test2.shared.usecases.photo.VaultPhotoUseCase
import com.example.test2.shared.usecases.search.SearchPhotosUseCase
import com.example.test2.storage.AndroidFilePersistenceDriver
import com.example.test2.storage.AndroidPhotoStorage
import com.example.test2.storage.DeviceMediaScanner
import com.example.test2.ui.AppTab
import com.example.test2.ui.GalleryDisplayMode
import com.example.test2.ui.RestoreProgress
import com.example.test2.ui.RetroactiveCategoryPrompt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex

/**
 * Shared UI-session state flows owned by [AppContainer] so all feature ViewModels
 * observe and mutate a single consistent source of truth without duplicating state.
 */
class AppSessionState(
    vaultManager: IVaultManager,
    cloudAiPrefs: CloudAiPrefsDataSource,
    agingMonitorInfoInitial: Triple<Long, Int, Long>,
    hasStoragePermissionInitial: Boolean
) {
    val activeTab = MutableStateFlow(AppTab.PHOTOS)
    val displayMode = MutableStateFlow(GalleryDisplayMode.BIG_PREVIEW)
    val viewingPhotoId = MutableStateFlow<String?>(null)
    val photoSettings = MutableStateFlow<Photo?>(null)
    val hasStoragePermission = MutableStateFlow(hasStoragePermissionInitial)

    val isVaultEnabled = MutableStateFlow(vaultManager.isVaultEnabled())
    val isAppUnlocked = MutableStateFlow(!vaultManager.isVaultEnabled())
    val showPrivacyConsentDialog = MutableStateFlow(false)
    val showAllFilesAccessDialog = MutableStateFlow(false)
    val showEnableVaultScopeDialog = MutableStateFlow(false)
    val isRestoringPhotos = MutableStateFlow(false)
    val restoreProgress = MutableStateFlow(RestoreProgress(0, 0))
    val showDisableVaultDialog = MutableStateFlow(false)
    val showRestoreSuccessBanner = MutableStateFlow<String?>(null)
    val showRequireDeviceLockDialog = MutableStateFlow(false)
    val showVaultPermissionDeniedBanner = MutableStateFlow<String?>(null)

    val isLocalScanning = MutableStateFlow(false)
    val pendingAiCount = MutableStateFlow(0)
    val isScanning = MutableStateFlow(false)

    val showCreateFolderDialog = MutableStateFlow(false)
    val showCreateCategoryDialog = MutableStateFlow(false)
    val folderToEdit = MutableStateFlow<Folder?>(null)
    val categoryToEdit = MutableStateFlow<Category?>(null)
    val photoToDelete = MutableStateFlow<Photo?>(null)
    val folderToDelete = MutableStateFlow<Folder?>(null)
    val categoryToDelete = MutableStateFlow<Category?>(null)
    val retroactiveCategoryPrompt = MutableStateFlow<RetroactiveCategoryPrompt?>(null)
    val retroactiveScanningCategory = MutableStateFlow<Category?>(null)
    val noMatchesCategory = MutableStateFlow<Category?>(null)

    val cloudAiConfig = MutableStateFlow<CloudAiConfig>(cloudAiPrefs.getConfig())
    val semanticMatchScores = MutableStateFlow<Map<String, Float>>(emptyMap())
    val isVectorIndexing = MutableStateFlow(false)
    val agingMonitorInfo = MutableStateFlow(agingMonitorInfoInitial)
    val activeActionCardType = MutableStateFlow(ActionCardType.ALL)
    val actionCardPhotoCounts = MutableStateFlow<Map<ActionCardType, Int>>(emptyMap())

    val scanLock = Mutex()
    var lastBackgroundTimestamp: Long = 0L
}

/**
 * Dependency container scoped to a UI session / ViewModelFactory.
 */
class AppContainer(
    val application: Application,
    hasStoragePermissionInitial: Boolean
) {
    val containerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val persistenceDriver = AndroidFilePersistenceDriver(application)
    val repository = PhotoRepository(persistenceDriver, containerScope)
    val dbHelper: AppDatabaseHelper = AppModule.provideAppDatabaseHelper(application)
    val queueDao: ScreenshotQueueDao = AppModule.provideScreenshotQueueDao(dbHelper)
    val ftsDao: FtsSearchDao = AppModule.provideFtsDao(dbHelper)
    val vectorDao: VectorDao = AppModule.provideVectorDao(dbHelper)
    val actionCardDao: ActionCardDao = AppModule.provideActionCardDao(dbHelper)

    val vaultManager: IVaultManager = AppModule.provideVaultManager(application)
    val cloudAiPrefs: CloudAiPrefsDataSource = AppModule.provideCloudAiPrefs(application)
    val geminiApiService: GeminiApiService = AppModule.provideGeminiApiService(application)
    val appDataSource: IAppDataSource = AppModule.provideAppDataSource(application)
    val storage = AndroidPhotoStorage(application)
    val mediaScanner = DeviceMediaScanner(application)

    val processingCoordinator = PhotoProcessingCoordinator(
        application, repository, queueDao, ftsDao, vectorDao, actionCardDao
    )
    val searchCoordinator = SemanticSearchCoordinator(
        application, repository, ftsDao, vectorDao
    )

    private val vaultPort = AndroidVaultPortAdapter(vaultManager)
    private val queueSyncPort = AndroidQueueSyncAdapter(queueDao)
    private val mediaScannerPort = AndroidMediaScannerAdapter(mediaScanner)
    private val storageCleanerPort = AndroidPhotoStorageCleanerAdapter(storage, queueDao)
    private val semanticSearchPort = AndroidSemanticSearchAdapter(searchCoordinator)
    private val retroCategorizerPort = AndroidRetroactiveCategorizerAdapter(application, vectorDao, ftsDao, queueDao)
    private val ftsIndexPort = AndroidFtsIndexAdapter(ftsDao)
    private val agingMonitorPort = AndroidAgingMonitorAdapter(application)
    private val photoImportPort = AndroidPhotoImportAdapter(storage, processingCoordinator)

    val scanDevicePhotosUseCase = ScanDevicePhotosUseCase(mediaScannerPort, repository, vaultPort, queueSyncPort)
    val vaultPhotoUseCase = VaultPhotoUseCase(vaultPort, queueSyncPort, repository)
    val deletePhotoUseCase = DeletePhotoUseCase(repository, storageCleanerPort)
    val toggleFavoriteUseCase = ToggleFavoriteUseCase(repository)
    val importPhotosUseCase = ImportPhotosUseCase(repository, photoImportPort, queueSyncPort)
    val createCategoryUseCase = CreateCategoryUseCase(repository)
    val retroactiveCategoryUseCase = RetroactiveCategoryUseCase(retroCategorizerPort, semanticSearchPort, ftsIndexPort, repository)
    val deleteCategoryUseCase = DeleteCategoryUseCase(repository)
    val searchPhotosUseCase = SearchPhotosUseCase(semanticSearchPort, repository)
    val agingCleanupUseCase = AgingCleanupUseCase(repository, storageCleanerPort, agingMonitorPort)

    val sessionState = AppSessionState(
        vaultManager = vaultManager,
        cloudAiPrefs = cloudAiPrefs,
        agingMonitorInfoInitial = AgingMonitorManager.getLastCheckedInfo(application),
        hasStoragePermissionInitial = hasStoragePermissionInitial
    )

    val syncOrchestrator = ScreenshotSyncOrchestrator(
        context = application,
        repository = repository,
        queueDao = queueDao,
        ftsDao = ftsDao,
        actionCardDao = actionCardDao,
        processingCoordinator = processingCoordinator,
        scanDevicePhotosUseCase = scanDevicePhotosUseCase,
        searchPhotosUseCase = searchPhotosUseCase,
        scanLock = sessionState.scanLock,
        isLocalScanning = sessionState.isLocalScanning,
        pendingAiCount = sessionState.pendingAiCount,
        isScanning = sessionState.isScanning,
        isVectorIndexing = sessionState.isVectorIndexing,
        actionCardPhotoCounts = sessionState.actionCardPhotoCounts
    )

    val vaultActionCoordinator = VaultActionCoordinator(
        app = application,
        repository = repository,
        vaultManager = vaultManager,
        vaultPhotoUseCase = vaultPhotoUseCase,
        orchestrator = syncOrchestrator,
        session = sessionState
    )
}

/**
 * Manual Dependency Injection module providing data sources, DAOs, services, and containers.
 */
object AppModule {
    fun provideAppDatabaseHelper(context: Context): AppDatabaseHelper =
        AppDatabaseHelper.getInstance(context)

    fun provideScreenshotQueueDao(db: AppDatabaseHelper): ScreenshotQueueDao = db

    fun provideFtsDao(db: AppDatabaseHelper): FtsSearchDao = db

    fun provideVectorDao(db: AppDatabaseHelper): VectorDao = db

    fun provideActionCardDao(db: AppDatabaseHelper): ActionCardDao = db

    fun provideVaultManager(context: Context): IVaultManager = VaultManagerImpl(context)

    fun provideCloudAiPrefs(context: Context): CloudAiPrefsDataSource = CloudAiPrefsDataSource(context)

    fun provideGeminiApiService(context: Context): GeminiApiService = GeminiApiServiceImpl(context)

    fun provideAppDataSource(context: Context): IAppDataSource =
        AndroidFilePersistenceDataSource(AndroidFilePersistenceDriver(context.applicationContext))

    fun createContainer(application: Application, hasStoragePermissionInitial: Boolean): AppContainer =
        AppContainer(application, hasStoragePermissionInitial)
}
