package com.example.test2.presentation.gallery

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.test2.ai.CloudAiSyncNotifier
import com.example.test2.ai.OnDeviceAnalysis
import com.example.test2.ai.cards.CardIds
import com.example.test2.ai.cards.QualityFlags
import com.example.test2.di.AppContainer
import com.example.test2.shared.models.ActionCard
import com.example.test2.shared.models.ActionCardType
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Folder
import com.example.test2.shared.models.Photo
import com.example.test2.shared.models.PhotoFilter
import com.example.test2.shared.models.SortOption
import com.example.test2.shared.models.UserAccountTier
import com.example.test2.ui.AppTab
import com.example.test2.ui.GalleryDisplayMode
import com.example.test2.widget.PhotoSearchWidgetProvider
import com.example.test2.widget.PhotoSearchWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * ViewModel dedicated to Gallery display, search, action card filtering, photo viewer/settings, and imports.
 */
class GalleryViewModel(
    private val app: Application,
    private val container: AppContainer
) : AndroidViewModel(app) {

    private val repository = container.repository
    private val session = container.sessionState
    private val orchestrator = container.syncOrchestrator
    private val actionCardDao = container.actionCardDao
    private val ftsDao = container.ftsDao
    private val processingCoordinator = container.processingCoordinator
    private val deletePhotoUseCase = container.deletePhotoUseCase
    private val toggleFavoriteUseCase = container.toggleFavoriteUseCase
    private val importPhotosUseCase = container.importPhotosUseCase
    private val searchPhotosUseCase = container.searchPhotosUseCase

    val photos: StateFlow<List<Photo>> = repository.photos
    val filteredPhotos: StateFlow<List<Photo>> = repository.filteredPhotos
    val folders: StateFlow<List<Folder>> = repository.folders
    val categories: StateFlow<List<Category>> = repository.categories
    val currentFilter: StateFlow<PhotoFilter> = repository.currentFilter
    val accountTier: StateFlow<UserAccountTier> = repository.accountTier

    private val filterDisplayFlow = combine(
        repository.filteredPhotos,
        repository.currentFilter,
        session.displayMode,
        session.semanticMatchScores,
        session.isVectorIndexing
    ) { filtered, filter, mode, scores, indexing ->
        GalleryUiState(
            photos = filtered,
            activeFilter = filter,
            searchQuery = filter.searchQuery,
            displayMode = mode,
            semanticMatchScores = scores,
            isVectorIndexing = indexing
        )
    }

    private val modalStateFlow = combine(
        session.viewingPhotoId,
        session.photoSettings,
        session.photoToDelete,
        session.activeActionCardType,
        session.actionCardPhotoCounts
    ) { viewerId, settings, toDelete, cardType, cardCounts ->
        GalleryUiState(
            viewerPhotoId = viewerId,
            photoSettings = settings,
            photoToDelete = toDelete,
            activeActionCardType = cardType,
            actionCardPhotoCounts = cardCounts
        )
    }

    val uiState: StateFlow<GalleryUiState> = combine(
        filterDisplayFlow,
        modalStateFlow,
        session.isScanning,
        session.pendingAiCount
    ) { base, modals, scanning, pending ->
        base.copy(
            viewerPhotoId = modals.viewerPhotoId,
            photoSettings = modals.photoSettings,
            photoToDelete = modals.photoToDelete,
            activeActionCardType = modals.activeActionCardType,
            actionCardPhotoCounts = modals.actionCardPhotoCounts,
            isLoading = scanning,
            pendingAiCount = pending
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GalleryUiState())

    private var searchJob: Job? = null

    fun setDisplayMode(mode: GalleryDisplayMode) {
        session.displayMode.value = mode
    }

    fun viewPhoto(photo: Photo?) {
        session.viewingPhotoId.value = photo?.id
    }

    fun closePhotoViewer() {
        session.viewingPhotoId.value = null
        session.photoSettings.value = null
    }

    fun openPhotoSettings(photo: Photo) {
        session.photoSettings.value = photo
    }

    fun closePhotoSettings() {
        session.photoSettings.value = null
    }

    fun setPhotoToDelete(photo: Photo?) {
        session.photoToDelete.value = photo
    }

    fun scanSinglePhoto(photoId: String) {
        val photo = repository.photos.value.firstOrNull { it.id == photoId } ?: return
        viewModelScope.launch {
            session.isScanning.value = true
            processingCoordinator.scanPhotoWithCloud(photo)
            if (session.photoSettings.value?.id == photoId) {
                session.photoSettings.value = repository.photos.value.firstOrNull { it.id == photoId }
            }
            session.isScanning.value = false
        }
    }

    fun importPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            session.isScanning.value = true
            val targetFolderId = repository.currentFilter.value.folderId
            val targetCategoryIds = repository.currentFilter.value.categoryId?.let { setOf(it) } ?: emptySet()
            importPhotosUseCase(
                uriStrings = uris.map { it.toString() },
                targetFolderId = targetFolderId,
                targetCategoryIds = targetCategoryIds,
                baseTimestampMillis = System.currentTimeMillis()
            )
            session.isScanning.value = false
        }
    }

    fun toggleFavorite(photoId: String) {
        val newState = toggleFavoriteUseCase(photoId)
        if (session.photoSettings.value?.id == photoId) {
            session.photoSettings.value = session.photoSettings.value?.copy(isFavorite = newState)
        }
    }

    fun toggleFavoriteFilter() = repository.toggleFavoriteFilter()

    fun toggleUserTier(isPro: Boolean) {
        repository.toggleUserTier(isPro)
        PhotoSearchWidgetProvider.cacheIsPro(app, isPro)
        viewModelScope.launch(Dispatchers.IO) {
            PhotoSearchWidgetUpdater.updateAll(app)
        }
        val query = repository.currentFilter.value.searchQuery
        if (query.isNotBlank()) {
            setSearchQuery(query)
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
        val updated = repository.updatePhoto(photoId, title, description, folderId, categoryIds, isFavorite)
        if (session.photoSettings.value?.id == photoId) {
            session.photoSettings.value = updated
        }
        updated?.let { p ->
            val catNames = repository.categories.value
                .filter { it.id in p.categoryIds }
                .joinToString(" ") { it.name }
            ftsDao.indexPhotoFts(
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
            deletePhotoUseCase(photo)
            orchestrator.refreshActionCardCounts()
            if (session.viewingPhotoId.value == photo.id) {
                session.viewingPhotoId.value = null
            }
            if (session.photoSettings.value?.id == photo.id) {
                session.photoSettings.value = null
            }
            session.photoToDelete.value = null
        }
    }

    fun filterByFolder(folderId: String?) {
        repository.setFolderFilter(folderId)
        session.activeTab.value = AppTab.PHOTOS
    }

    fun filterByCategory(categoryId: String?) {
        repository.setCategoryFilter(categoryId)
        session.activeTab.value = AppTab.PHOTOS
    }

    fun setActionCardFilter(type: ActionCardType) {
        session.activeActionCardType.value = type
        viewModelScope.launch(Dispatchers.IO) {
            if (type == ActionCardType.ALL) {
                repository.setActionCardFilter(null, null)
            } else {
                val matchingIds = actionCardDao.getPhotoIdsByActionCardType(type)
                repository.setActionCardFilter(type, matchingIds)
            }
        }
    }

    fun getActionCardsForPhoto(photoId: String): List<ActionCard> =
        actionCardDao.getActionCardsForPhoto(photoId)

    fun observeActionCards(photoId: String): Flow<List<ActionCard>> = flow {
        emit(actionCardDao.getActionCardsForPhoto(photoId))

        if (QualityFlags.REEXTRACT_ON_OPEN) {
            checkAndReextractCardsIfOld(photoId)
        }

        CloudAiSyncNotifier.syncEvents.collect { updatedPhotoIds ->
            if (photoId in updatedPhotoIds) {
                emit(actionCardDao.getActionCardsForPhoto(photoId))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun checkAndReextractCardsIfOld(photoId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = actionCardDao.getActionCardsForPhoto(photoId)
            val needsExtraction = existing.isEmpty() || existing.any { card ->
                val ev = try {
                    JSONObject(card.payloadJson).optInt("ev", 1)
                } catch (_: Exception) {
                    1
                }
                ev < CardIds.EXTRACTOR_VERSION
            }
            if (needsExtraction) {
                val photo = repository.photos.value.find { it.id == photoId } ?: return@launch
                val result = OnDeviceAnalysis.analyze(
                    context = app,
                    photoId = photoId,
                    filePath = photo.filePath,
                    photoTitle = photo.title
                )
                if (result != null) {
                    actionCardDao.replaceActionCardsForPhoto(photoId, result.cards)
                    CloudAiSyncNotifier.notifyBatchCompleted(listOf(photoId))
                    orchestrator.refreshActionCardCounts()
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                session.semanticMatchScores.value = emptyMap()
                searchPhotosUseCase("")
                return@launch
            }
            delay(400)
            val result = searchPhotosUseCase(query)
            session.semanticMatchScores.value = result.semanticMatchScores
        }
    }

    fun setSortOption(sortOption: SortOption) = repository.setSortOption(sortOption)

    fun clearFilters() {
        session.activeActionCardType.value = ActionCardType.ALL
        repository.clearFilters()
    }

    fun saveCloudAiConfig(apiKey: String, modelName: String, customEndpoint: String, enabled: Boolean) {
        container.cloudAiPrefs.saveConfig(apiKey, modelName, customEndpoint, enabled)
        session.cloudAiConfig.value = container.cloudAiPrefs.getConfig()
    }
}
