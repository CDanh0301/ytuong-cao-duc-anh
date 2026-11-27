package com.example.test2.di

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.test2.presentation.categories.CategoriesViewModel
import com.example.test2.presentation.gallery.GalleryViewModel
import com.example.test2.presentation.housekeeping.HousekeepingViewModel
import com.example.test2.presentation.vault.VaultViewModel
import com.example.test2.ui.PhotoAppViewModel

/**
 * ViewModelProvider.Factory providing explicit manual DI backed by the Application-scoped
 * [AppContainer] so all Activities and feature ViewModels share a single repository instance.
 */
class AppViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    val container: AppContainer by lazy {
        AppModule.getOrCreateContainer(application, hasStoragePermission(application))
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            PhotoAppViewModel::class.java -> PhotoAppViewModel(
                app = application,
                repository = container.repository,
                navigationState = container.navigationState,
                vaultState = container.vaultStateStore,
                orchestrator = container.syncOrchestrator,
                vaultManager = container.vaultManager,
                vaultCoordinator = container.vaultActionCoordinator,
                actionCardsUseCase = container.actionCardsUseCase,
                backgroundSync = container.backgroundSyncPort,
                metadataSync = container.metadataSyncPort,
                containerScope = null,
                mediaObserverFactory = container.mediaObserverFactory
            )
            GalleryViewModel::class.java -> GalleryViewModel(
                repository = container.repository,
                navigationState = container.navigationState,
                vaultState = container.vaultStateStore,
                orchestrator = container.syncOrchestrator,
                deletePhotoUseCase = container.deletePhotoUseCase,
                toggleFavoriteUseCase = container.toggleFavoriteUseCase,
                importPhotosUseCase = container.importPhotosUseCase,
                searchPhotosUseCase = container.searchPhotosUseCase,
                updatePhotoDetailsUseCase = container.updatePhotoDetailsUseCase,
                scanSinglePhotoUseCase = container.scanSinglePhotoUseCase,
                actionCardsUseCase = container.actionCardsUseCase,
                toggleUserTierUseCase = container.toggleUserTierUseCase,
                cloudAiConfigUseCase = container.cloudAiConfigUseCase,
                metadataSync = container.metadataSyncPort
            )
            VaultViewModel::class.java -> VaultViewModel(
                repository = container.repository,
                vaultManager = container.vaultManager,
                deviceSecurity = container.deviceSecurityPort,
                vaultCoordinator = container.vaultActionCoordinator,
                widgetPort = container.widgetPort,
                vaultState = container.vaultStateStore
            )
            CategoriesViewModel::class.java -> CategoriesViewModel(
                repository = container.repository,
                navigationState = container.navigationState,
                createCategoryUseCase = container.createCategoryUseCase,
                renameCategoryUseCase = container.renameCategoryUseCase,
                deleteCategoryUseCase = container.deleteCategoryUseCase,
                retroactiveCategoryUseCase = container.retroactiveCategoryUseCase
            )
            HousekeepingViewModel::class.java -> HousekeepingViewModel(
                repository = container.repository,
                orchestrator = container.syncOrchestrator,
                agingCleanupUseCase = container.agingCleanupUseCase,
                markReminderCreatedUseCase = container.markReminderCreatedUseCase,
                metadataSync = container.metadataSyncPort
            )
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        } as T
    }

    companion object {
        fun hasStoragePermission(context: Context): Boolean {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}
