package com.example.test2.presentation.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test2.ai.ScreenshotSyncOrchestrator
import com.example.test2.presentation.main.MainNavigationState
import com.example.test2.presentation.vault.VaultStateStore
import com.example.test2.shared.models.ActionCard
import com.example.test2.shared.models.ActionCardType
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Folder
import com.example.test2.shared.models.Photo
import com.example.test2.shared.models.PhotoFilter
import com.example.test2.shared.models.SortOption
import com.example.test2.shared.models.UserAccountTier
import com.example.test2.shared.repository.IPhotoRepository
import com.example.test2.shared.usecases.MetadataSyncPort
import com.example.test2.shared.usecases.photo.ActionCardsUseCase
import com.example.test2.shared.usecases.photo.DeletePhotoUseCase
import com.example.test2.shared.usecases.photo.ImportPhotosUseCase
import com.example.test2.shared.usecases.photo.ScanSinglePhotoUseCase
import com.example.test2.shared.usecases.photo.ToggleFavoriteUseCase
import com.example.test2.shared.usecases.photo.UpdatePhotoDetailsUseCase
import com.example.test2.shared.usecases.search.SearchPhotosUseCase
import com.example.test2.shared.usecases.settings.CloudAiConfigUseCase
import com.example.test2.shared.usecases.settings.ToggleUserTierUseCase
import com.example.test2.ui.AppTab
import com.example.test2.ui.GalleryDisplayMode
import com.example.test2.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Pure MVVM ViewModel dedicated to Gallery display, search, action card filtering,
 * photo viewer/settings, and imports. Owns its UI state and observes repository & orchestrator flows.
 */
class GalleryViewModel(
    private val repository: IPhotoRepository,
    private val navigationState: MainNavigationState,
    private val vaultState: VaultStateStore,
    private val orchestrator: ScreenshotSyncOrchestrator,
    private val deletePhotoUseCase: DeletePhotoUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val importPhotosUseCase: ImportPhotosUseCase,
    private val searchPhotosUseCase: SearchPhotosUseCase,
    private val updatePhotoDetailsUseCase: UpdatePhotoDetailsUseCase,
    private val scanSinglePhotoUseCase: ScanSinglePhotoUseCase,
    private val actionCardsUseCase: ActionCardsUseCase,
    private val toggleUserTierUseCase: ToggleUserTierUseCase,
    private val cloudAiConfigUseCase: CloudAiConfigUseCase,
    private val metadataSync: MetadataSyncPort
) : ViewModel() {

    val photos: StateFlow<List<Photo>> = repository.photos
    val filteredPhotos: StateFlow<List<Photo>> = repository.filteredPhotos
    val folders: StateFlow<List<Folder>> = repository.folders
    val categories: StateFlow<List<Category>> = repository.categories
    val currentFilter: StateFlow<PhotoFilter> = repository.currentFilter
    val accountTier: StateFlow<UserAccountTier> = repository.accountTier

    private val displayMode = MutableStateFlow(GalleryDisplayMode.BIG_PREVIEW)
    private val photoSettingsId = MutableStateFlow<String?>(null)
    private val photoToDelete = MutableStateFlow<Photo?>(null)
    private val semanticMatchScores = MutableStateFlow<Map<String, Float>>(emptyMap())
    private val isLocalBusy = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    private val resolvedPhotoSettings = combine(
        photoSettingsId,
        repository.photos
    ) { targetId, allPhotos ->
        if (targetId == null) null else allPhotos.firstOrNull { it.id == targetId }
    }

    private val repoMetadataFlow = combine(
        repository.photos,
        repository.folders,
        repository.categories,
        repository.accountTier
    ) { allPhotos, allFolders, allCategories, tier ->
        GalleryUiState(
            allPhotosCount = allPhotos.size,
            folders = allFolders,
            categories = allCategories,
            accountTier = tier
        )
    }

    private val filterDisplayFlow = combine(
        repository.filteredPhotos,
        repository.currentFilter,
        displayMode,
        semanticMatchScores,
        orchestrator.isVectorIndexing
    ) { filtered, filter, mode, scores, indexing ->
        GalleryUiState(
            photos = filtered,
            activeFilter = filter,
            searchQuery = filter.searchQuery,
            displayMode = mode,
            semanticMatchScores = scores,
            isVectorIndexing = indexing,
            activeActionCardType = filter.actionCardType ?: ActionCardType.ALL
        )
    }

    private val modalStateFlow = combine(
        navigationState.viewingPhotoId,
        resolvedPhotoSettings,
        photoToDelete,
        orchestrator.actionCardPhotoCounts,
        errorMessage
    ) { viewerId, settings, toDelete, cardCounts, err ->
        GalleryUiState(
            viewerPhotoId = viewerId,
            photoSettings = settings,
            photoToDelete = toDelete,
            actionCardPhotoCounts = cardCounts,
            error = err
        )
    }

    private val syncStatusFlow = combine(
        orchestrator.isScanning,
        isLocalBusy,
        orchestrator.pendingAiCount
    ) { scanning, localBusy, pending ->
        Triple(scanning || localBusy, pending, Unit)
    }

    val uiState: StateFlow<GalleryUiState> = combine(
        repoMetadataFlow,
        filterDisplayFlow,
        modalStateFlow,
        syncStatusFlow
    ) { meta, base, modals, status ->
        base.copy(
            allPhotosCount = meta.allPhotosCount,
            folders = meta.folders,
            categories = meta.categories,
            accountTier = meta.accountTier,
            viewerPhotoId = modals.viewerPhotoId,
            photoSettings = modals.photoSettings,
            photoToDelete = modals.photoToDelete,
            actionCardPhotoCounts = modals.actionCardPhotoCounts,
            isLoading = status.first,
            pendingAiCount = status.second,
            error = modals.error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GalleryUiState())

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            vaultState.restoredPhotoIdEvents.collect { (oldId, newId) ->
                if (photoSettingsId.value == oldId) {
                    photoSettingsId.value = newId
                }
            }
        }
    }

    fun dismissError() {
        errorMessage.value = null
    }

    fun setDisplayMode(mode: GalleryDisplayMode) {
        displayMode.value = mode
    }

    fun viewPhoto(photo: Photo?) {
        navigationState.viewingPhotoId.value = photo?.id
    }

    fun closePhotoViewer() {
        navigationState.viewingPhotoId.value = null
        photoSettingsId.value = null
    }

    fun openPhotoSettings(photo: Photo) {
        photoSettingsId.value = photo.id
    }

    fun closePhotoSettings() {
        photoSettingsId.value = null
    }

    fun setPhotoToDelete(photo: Photo?) {
        photoToDelete.value = photo
    }

    fun scanSinglePhoto(photoId: String) {
        viewModelScope.launch {
            isLocalBusy.value = true
            try {
                val updated = scanSinglePhotoUseCase(photoId)
                if (updated != null) {
                    metadataSync.markMetadataDirty()
                }
            } catch (e: Exception) {
                AppLogger.e("GalleryViewModel", "Failed scanning single photo $photoId", e)
                errorMessage.value = "Không thể quét lại ảnh"
            } finally {
                isLocalBusy.value = false
            }
        }
    }

    fun importPhotos(uriStrings: List<String>) {
        if (uriStrings.isEmpty()) return
        viewModelScope.launch {
            isLocalBusy.value = true
            try {
                val targetFolderId = repository.currentFilter.value.folderId
                val targetCategoryIds = repository.currentFilter.value.categoryId?.let { setOf(it) } ?: emptySet()
                importPhotosUseCase(
                    uriStrings = uriStrings,
                    targetFolderId = targetFolderId,
                    targetCategoryIds = targetCategoryIds,
                    baseTimestampMillis = System.currentTimeMillis()
                )
                metadataSync.markMetadataDirty()
            } catch (e: Exception) {
                AppLogger.e("GalleryViewModel", "Failed importing photos", e)
                errorMessage.value = "Không thể nhập ảnh đã chọn"
            } finally {
                isLocalBusy.value = false
            }
        }
    }

    fun toggleFavorite(photoId: String) {
        toggleFavoriteUseCase(photoId)
        metadataSync.markMetadataDirty()
    }

    fun toggleFavoriteFilter() = repository.toggleFavoriteFilter()

    fun toggleUserTier(isPro: Boolean) {
        val activeQuery = toggleUserTierUseCase(isPro)
        metadataSync.markMetadataDirty()
        if (activeQuery.isNotBlank()) {
            setSearchQuery(activeQuery)
        }
    }

    fun updatePhotoDetails(
        photoId: String,
        title: String,
        description: String,
        folderId: String,
        categoryIds: Set<String>,
        isFavorite: Boolean? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updatePhotoDetailsUseCase(photoId, title, description, folderId, categoryIds, isFavorite)
                metadataSync.markMetadataDirty()
            } catch (e: Exception) {
                AppLogger.e("GalleryViewModel", "Failed updating photo details $photoId", e)
                errorMessage.value = "Không thể cập nhật thông tin ảnh"
            }
        }
    }

    fun deletePhotoConfirmed(photo: Photo) {
        viewModelScope.launch {
            try {
                deletePhotoUseCase(photo)
                metadataSync.markMetadataDirty()
                orchestrator.refreshActionCardCounts()
                if (navigationState.viewingPhotoId.value == photo.id) {
                    navigationState.viewingPhotoId.value = null
                }
                if (photoSettingsId.value == photo.id) {
                    photoSettingsId.value = null
                }
                photoToDelete.value = null
            } catch (e: Exception) {
                AppLogger.e("GalleryViewModel", "Failed deleting photo ${photo.id}", e)
                errorMessage.value = "Không thể xóa ảnh"
                photoToDelete.value = null
            }
        }
    }

    fun filterByFolder(folderId: String?) {
        repository.setFolderFilter(folderId)
        navigationState.activeTab.value = AppTab.PHOTOS
    }

    fun filterByCategory(categoryId: String?) {
        repository.setCategoryFilter(categoryId)
        navigationState.activeTab.value = AppTab.PHOTOS
    }

    fun setActionCardFilter(type: ActionCardType) {
        if (type == ActionCardType.ALL) {
            repository.setActionCardFilter(null, null)
            return
        }
        repository.setActionCardFilter(type, null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val matchingIds = actionCardsUseCase.getPhotoIdsByType(type)
                if (repository.currentFilter.value.actionCardType == type) {
                    repository.setActionCardFilter(type, matchingIds)
                }
            } catch (e: Exception) {
                AppLogger.e("GalleryViewModel", "Failed filtering by action card $type", e)
            }
        }
    }

    fun getActionCardsForPhoto(photoId: String): List<ActionCard> =
        actionCardsUseCase.getActionCardsForPhoto(photoId)

    fun observeActionCards(photoId: String): Flow<List<ActionCard>> =
        actionCardsUseCase.observeActionCards(photoId).flowOn(Dispatchers.IO)

    fun setSearchQuery(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                semanticMatchScores.value = emptyMap()
                searchPhotosUseCase("")
                return@launch
            }
            delay(400)
            try {
                val result = searchPhotosUseCase(query)
                semanticMatchScores.value = result.semanticMatchScores
            } catch (e: Exception) {
                AppLogger.e("GalleryViewModel", "Search failed for '$query'", e)
                errorMessage.value = "Tìm kiếm gặp lỗi"
            }
        }
    }

    fun setSortOption(sortOption: SortOption) = repository.setSortOption(sortOption)

    fun clearFilters() {
        repository.clearFilters()
    }

    fun saveCloudAiConfig(apiKey: String, modelName: String, customEndpoint: String, enabled: Boolean) {
        cloudAiConfigUseCase.saveConfig(apiKey, modelName, customEndpoint, enabled)
    }
}
