package com.example.test2.presentation.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test2.di.AppContainer
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Photo
import com.example.test2.shared.models.UserAccountTier
import com.example.test2.ui.AppTab
import com.example.test2.ui.RetroactiveCategoryPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel dedicated to custom AI category management and retroactive auto-categorization.
 */
class CategoriesViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val repository = container.repository
    private val session = container.sessionState
    private val orchestrator = container.syncOrchestrator
    private val createCategoryUseCase = container.createCategoryUseCase
    private val deleteCategoryUseCase = container.deleteCategoryUseCase
    private val retroactiveCategoryUseCase = container.retroactiveCategoryUseCase

    val categories: StateFlow<List<Category>> = repository.categories
    val photos: StateFlow<List<Photo>> = repository.photos
    val accountTier: StateFlow<UserAccountTier> = repository.accountTier

    private val dialogsFlow = combine(
        session.showCreateCategoryDialog,
        session.categoryToEdit,
        session.categoryToDelete
    ) { showCreate, toEdit, toDelete ->
        CategoriesUiState(
            showCreateCategoryDialog = showCreate,
            categoryToEdit = toEdit,
            categoryToDelete = toDelete
        )
    }

    private val retroactiveFlow = combine(
        session.retroactiveCategoryPrompt,
        session.retroactiveScanningCategory,
        session.noMatchesCategory
    ) { prompt, scanning, noMatches ->
        CategoriesUiState(
            retroactiveCategoryPrompt = prompt,
            retroactiveScanningCategory = scanning,
            noMatchesCategory = noMatches
        )
    }

    val uiState: StateFlow<CategoriesUiState> = combine(dialogsFlow, retroactiveFlow) { d, r ->
        d.copy(
            retroactiveCategoryPrompt = r.retroactiveCategoryPrompt,
            retroactiveScanningCategory = r.retroactiveScanningCategory,
            noMatchesCategory = r.noMatchesCategory
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CategoriesUiState())

    fun selectCategory(categoryId: String?) {
        repository.setCategoryFilter(categoryId)
        session.activeTab.value = AppTab.PHOTOS
    }

    fun openCreateCategoryDialog() {
        session.showCreateCategoryDialog.value = true
    }

    fun closeCreateCategoryDialog() {
        session.showCreateCategoryDialog.value = false
    }

    fun setCategoryToEdit(category: Category?) {
        session.categoryToEdit.value = category
    }

    fun setCategoryToDelete(category: Category?) {
        session.categoryToDelete.value = category
    }

    fun dismissNoMatchesDialog() {
        session.noMatchesCategory.value = null
    }

    fun dismissRetroactiveCategoryPrompt() {
        session.retroactiveCategoryPrompt.value = null
    }

    fun createCategory(name: String, colorHex: String = "#6366F1", iconName: String = "tag"): Boolean {
        val created = createCategoryUseCase(
            name = name,
            colorHex = colorHex,
            iconName = iconName,
            timestampMillis = System.currentTimeMillis()
        )
        closeCreateCategoryDialog()
        if (created != null) {
            triggerRetroactiveCategoryScan(created, forceReindex = true)
        }
        return created != null
    }

    fun renameCategory(categoryId: String, newName: String, colorHex: String, iconName: String) {
        if (newName.isNotBlank()) {
            repository.renameCategory(categoryId, newName, colorHex, iconName)
            viewModelScope.launch(Dispatchers.IO) {
                orchestrator.ensureVectorsIndexed(forceReindex = true)
            }
        }
        setCategoryToEdit(null)
    }

    fun deleteCategory(categoryId: String) {
        deleteCategoryUseCase(categoryId)
        viewModelScope.launch(Dispatchers.IO) {
            orchestrator.ensureVectorsIndexed(forceReindex = true)
        }
        setCategoryToDelete(null)
    }

    fun triggerRetroactiveCategoryScan(
        category: Category,
        forceReindex: Boolean = false,
        onNoMatchesFound: (() -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            session.retroactiveScanningCategory.value = category
            session.isVectorIndexing.value = true
            try {
                val matchedPhotoIds = retroactiveCategoryUseCase.scanForCategory(category, forceReindex)
                if (matchedPhotoIds.isNotEmpty()) {
                    val matchedPhotos = repository.photos.value.filter { it.id in matchedPhotoIds }
                    session.retroactiveCategoryPrompt.value = RetroactiveCategoryPrompt(
                        category = category,
                        matchingPhotoIds = matchedPhotoIds,
                        previewPhotos = matchedPhotos.take(4)
                    )
                } else {
                    session.noMatchesCategory.value = category
                    onNoMatchesFound?.invoke()
                }
            } finally {
                session.isVectorIndexing.value = false
                session.retroactiveScanningCategory.value = null
            }
        }
    }

    fun confirmRetroactiveCategoryMatch() {
        val prompt = session.retroactiveCategoryPrompt.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            session.retroactiveCategoryPrompt.value = null
            session.isVectorIndexing.value = true
            try {
                retroactiveCategoryUseCase.applyCategory(prompt.matchingPhotoIds, prompt.category)
            } finally {
                session.isVectorIndexing.value = false
            }
        }
    }
}
