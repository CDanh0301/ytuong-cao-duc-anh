package com.example.test2.di

import android.app.Application
import android.content.Context
import com.example.test2.PhotoApp
import com.example.test2.ai.PhotoProcessingCoordinator
import com.example.test2.ai.ScreenshotSyncOrchestrator
import com.example.test2.ai.SemanticSearchCoordinator
import com.example.test2.data.local.dao.ActionCardDao
import com.example.test2.data.local.dao.FtsSearchDao
import com.example.test2.data.local.dao.ScreenshotQueueDao
import com.example.test2.data.local.dao.VectorDao
import com.example.test2.data.local.prefs.CloudAiPrefsDataSource
import com.example.test2.data.local.prefs.VectorPrefsDataSource
import com.example.test2.data.remote.GeminiApiService
import com.example.test2.data.remote.GeminiApiServiceImpl
import com.example.test2.data.repository.AndroidActionCardAdapter
import com.example.test2.data.repository.AndroidAgingMonitorAdapter
import com.example.test2.data.repository.AndroidBackgroundSyncAdapter
import com.example.test2.data.repository.AndroidCloudAiConfigAdapter
import com.example.test2.data.repository.AndroidDeviceSecurityAdapter
import com.example.test2.data.repository.AndroidFilePersistenceDataSource
import com.example.test2.data.repository.AndroidFtsIndexAdapter
import com.example.test2.data.repository.AndroidMediaObserverAdapter
import com.example.test2.data.repository.AndroidMediaScannerAdapter
import com.example.test2.data.repository.AndroidMetadataSyncAdapter
import com.example.test2.data.repository.AndroidPhotoImportAdapter
import com.example.test2.data.repository.AndroidPhotoProcessingAdapter
import com.example.test2.data.repository.AndroidPhotoStorageCleanerAdapter
import com.example.test2.data.repository.AndroidQueueSyncAdapter
import com.example.test2.data.repository.AndroidRetroactiveCategorizerAdapter
import com.example.test2.data.repository.AndroidSemanticSearchAdapter
import com.example.test2.data.repository.AndroidVaultPortAdapter
import com.example.test2.data.repository.AndroidWidgetAdapter
import com.example.test2.data.repository.IAppDataSource
import com.example.test2.data.repository.PhotoRepositoryImpl
import com.example.test2.database.AppDatabaseHelper
import com.example.test2.presentation.main.MainNavigationState
import com.example.test2.presentation.vault.VaultActionCoordinator
import com.example.test2.presentation.vault.VaultStateStore
import com.example.test2.security.IVaultManager
import com.example.test2.security.VaultManagerImpl
import com.example.test2.shared.repository.IPhotoRepository
import com.example.test2.shared.repository.PersistenceDriver
import com.example.test2.shared.usecases.BackgroundSyncPort
import com.example.test2.shared.usecases.DeviceSecurityPort
import com.example.test2.shared.usecases.MediaObserverPort
import com.example.test2.shared.usecases.MetadataSyncPort
import com.example.test2.shared.usecases.WidgetPort
import com.example.test2.shared.usecases.category.CreateCategoryUseCase
import com.example.test2.shared.usecases.category.DeleteCategoryUseCase
import com.example.test2.shared.usecases.category.RenameCategoryUseCase
import com.example.test2.shared.usecases.category.RetroactiveCategoryUseCase
import com.example.test2.shared.usecases.housekeeping.AgingCleanupUseCase
import com.example.test2.shared.usecases.housekeeping.MarkReminderCreatedUseCase
import com.example.test2.shared.usecases.photo.ActionCardsUseCase
import com.example.test2.shared.usecases.photo.DeletePhotoUseCase
import com.example.test2.shared.usecases.photo.ImportPhotosUseCase
import com.example.test2.shared.usecases.photo.ScanDevicePhotosUseCase
import com.example.test2.shared.usecases.photo.ScanSinglePhotoUseCase
import com.example.test2.shared.usecases.photo.ToggleFavoriteUseCase
import com.example.test2.shared.usecases.photo.UpdatePhotoDetailsUseCase
import com.example.test2.shared.usecases.photo.VaultPhotoUseCase
import com.example.test2.shared.usecases.search.SearchPhotosUseCase
import com.example.test2.shared.usecases.settings.CloudAiConfigUseCase
import com.example.test2.shared.usecases.settings.ToggleUserTierUseCase
import com.example.test2.storage.AndroidFilePersistenceDriver
import com.example.test2.storage.AndroidPhotoStorage
import com.example.test2.storage.DeviceMediaScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Dependency container scoped to the [PhotoApp] process so all Activities and ViewModels
 * share a single [IPhotoRepository] source of truth.
 */
class AppContainer(
    val application: Application,
    hasStoragePermissionInitial: Boolean
) {
    val containerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val persistenceDriver: PersistenceDriver = AndroidFilePersistenceDriver(application)
    val repository: IPhotoRepository = AppModule.providePhotoRepository(persistenceDriver, containerScope)
    val dbHelper: AppDatabaseHelper = AppModule.provideAppDatabaseHelper(application)
    val queueDao: ScreenshotQueueDao = AppModule.provideScreenshotQueueDao(dbHelper)
    val ftsDao: FtsSearchDao = AppModule.provideFtsDao(dbHelper)
    val vectorDao: VectorDao = AppModule.provideVectorDao(dbHelper)
    val actionCardDao: ActionCardDao = AppModule.provideActionCardDao(dbHelper)

    val vaultManager: IVaultManager = AppModule.provideVaultManager(application)
    val cloudAiPrefs: CloudAiPrefsDataSource = AppModule.provideCloudAiPrefs(application)
    val vectorPrefs: VectorPrefsDataSource = AppModule.provideVectorPrefs(application)
    val geminiApiService: GeminiApiService = AppModule.provideGeminiApiService(application)
    val appDataSource: IAppDataSource = AppModule.provideAppDataSource(application)
    val storage = AndroidPhotoStorage(application)
    val mediaScanner = DeviceMediaScanner(application)

    val processingCoordinator = PhotoProcessingCoordinator(
        context = application,
        repository = repository,
        queueDao = queueDao,
        ftsDao = ftsDao,
        vectorDao = vectorDao,
        actionCardDao = actionCardDao,
        geminiApiService = geminiApiService,
        cloudAiPrefs = cloudAiPrefs
    )
    val searchCoordinator = SemanticSearchCoordinator(
        context = application,
        repository = repository,
        ftsDao = ftsDao,
        vectorDao = vectorDao,
        geminiApiService = geminiApiService,
        cloudAiPrefs = cloudAiPrefs,
        vectorPrefs = vectorPrefs
    )

    private val vaultPort = AndroidVaultPortAdapter(vaultManager)
    private val queueSyncPort = AndroidQueueSyncAdapter(queueDao)
    private val mediaScannerPort = AndroidMediaScannerAdapter(mediaScanner)
    private val storageCleanerPort = AndroidPhotoStorageCleanerAdapter(storage, queueDao)
    private val semanticSearchPort = AndroidSemanticSearchAdapter(searchCoordinator)
    private val retroCategorizerPort = AndroidRetroactiveCategorizerAdapter(
        context = application,
        vectorDao = vectorDao,
        ftsDao = ftsDao,
        queueDao = queueDao,
        cloudAiPrefs = cloudAiPrefs,
        geminiApiService = geminiApiService
    )
    private val ftsIndexPort = AndroidFtsIndexAdapter(ftsDao)
    private val agingMonitorPort = AndroidAgingMonitorAdapter(application)
    private val photoImportPort = AndroidPhotoImportAdapter(storage, processingCoordinator)
    private val actionCardPort = AndroidActionCardAdapter(application, actionCardDao)
    private val photoProcessingPort = AndroidPhotoProcessingAdapter(processingCoordinator)
    val widgetPort: WidgetPort = AndroidWidgetAdapter(application)
    val deviceSecurityPort: DeviceSecurityPort = AndroidDeviceSecurityAdapter(application)
    private val cloudAiConfigPort = AndroidCloudAiConfigAdapter(cloudAiPrefs)
    val backgroundSyncPort: BackgroundSyncPort = AndroidBackgroundSyncAdapter(application, queueDao)
    val metadataSyncPort: MetadataSyncPort = AndroidMetadataSyncAdapter
    val mediaObserverFactory: (() -> Unit) -> MediaObserverPort = { onChanged ->
        AndroidMediaObserverAdapter(application, vaultManager, onChanged)
    }

    val scanDevicePhotosUseCase = ScanDevicePhotosUseCase(mediaScannerPort, repository, vaultPort, queueSyncPort)
    val vaultPhotoUseCase = VaultPhotoUseCase(vaultPort, queueSyncPort, repository)
    val deletePhotoUseCase = DeletePhotoUseCase(repository, storageCleanerPort)
    val toggleFavoriteUseCase = ToggleFavoriteUseCase(repository)
    val importPhotosUseCase = ImportPhotosUseCase(repository, photoImportPort, queueSyncPort)
    val updatePhotoDetailsUseCase = UpdatePhotoDetailsUseCase(repository, ftsIndexPort, queueSyncPort)
    val scanSinglePhotoUseCase = ScanSinglePhotoUseCase(repository, photoProcessingPort)
    val actionCardsUseCase = ActionCardsUseCase(actionCardPort, repository)
    val createCategoryUseCase = CreateCategoryUseCase(repository)
    val renameCategoryUseCase = RenameCategoryUseCase(repository, semanticSearchPort)
    val retroactiveCategoryUseCase = RetroactiveCategoryUseCase(retroCategorizerPort, semanticSearchPort, ftsIndexPort, repository, queueSyncPort)
    val deleteCategoryUseCase = DeleteCategoryUseCase(repository, semanticSearchPort, queueSyncPort)
    val searchPhotosUseCase = SearchPhotosUseCase(semanticSearchPort, repository)
    val agingCleanupUseCase = AgingCleanupUseCase(repository, storageCleanerPort, agingMonitorPort)
    val markReminderCreatedUseCase = MarkReminderCreatedUseCase(repository)
    val toggleUserTierUseCase = ToggleUserTierUseCase(repository, widgetPort)
    val cloudAiConfigUseCase = CloudAiConfigUseCase(cloudAiConfigPort)

    val navigationState = MainNavigationState(hasStoragePermissionInitial)
    val vaultStateStore = VaultStateStore(vaultManager)

    val syncOrchestrator = ScreenshotSyncOrchestrator(
        context = application,
        repository = repository,
        queueDao = queueDao,
        ftsDao = ftsDao,
        actionCardDao = actionCardDao,
        processingCoordinator = processingCoordinator,
        scanDevicePhotosUseCase = scanDevicePhotosUseCase,
        searchPhotosUseCase = searchPhotosUseCase,
        widgetPort = widgetPort
    )

    val vaultActionCoordinator = VaultActionCoordinator(
        repository = repository,
        vaultManager = vaultManager,
        deviceSecurity = deviceSecurityPort,
        widgetPort = widgetPort,
        vaultPhotoUseCase = vaultPhotoUseCase,
        orchestrator = syncOrchestrator,
        vaultState = vaultStateStore,
        metadataSync = metadataSyncPort
    )
}

/**
 * Manual Dependency Injection module providing data sources, DAOs, repositories, services, and containers.
 */
object AppModule {
    fun provideAppDatabaseHelper(context: Context): AppDatabaseHelper =
        AppDatabaseHelper.getInstance(context)

    fun providePhotoRepository(driver: PersistenceDriver, scope: CoroutineScope): IPhotoRepository =
        PhotoRepositoryImpl(driver, scope)

    fun provideScreenshotQueueDao(db: AppDatabaseHelper): ScreenshotQueueDao = db

    fun provideFtsDao(db: AppDatabaseHelper): FtsSearchDao = db

    fun provideVectorDao(db: AppDatabaseHelper): VectorDao = db

    fun provideActionCardDao(db: AppDatabaseHelper): ActionCardDao = db

    fun provideVaultManager(context: Context): IVaultManager = VaultManagerImpl(context)

    fun provideCloudAiPrefs(context: Context): CloudAiPrefsDataSource = CloudAiPrefsDataSource(context)

    fun provideVectorPrefs(context: Context): VectorPrefsDataSource = VectorPrefsDataSource(context)

    fun provideGeminiApiService(context: Context): GeminiApiService = GeminiApiServiceImpl(context)

    fun provideAppDataSource(context: Context): IAppDataSource =
        AndroidFilePersistenceDataSource(AndroidFilePersistenceDriver(context.applicationContext))

    fun createContainer(application: Application, hasStoragePermissionInitial: Boolean): AppContainer =
        AppContainer(application, hasStoragePermissionInitial)

    fun getOrCreateContainer(application: Application, hasStoragePermissionInitial: Boolean): AppContainer =
        (application as? PhotoApp)?.container ?: createContainer(application, hasStoragePermissionInitial)
}
