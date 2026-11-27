package com.example.test2.presentation.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test2.presentation.main.MainNavigationState
import com.example.test2.shared.models.Category
import com.example.test2.shared.repository.IPhotoRepository
import com.example.test2.shared.usecases.category.CreateCategoryUseCase
import com.example.test2.shared.usecases.category.DeleteCategoryUseCase
import com.example.test2.shared.usecases.category.RenameCategoryUseCase
import com.example.test2.shared.usecases.category.RetroactiveCategoryUseCase
import com.example.test2.ui.AppTab
import com.example.test2.ui.RetroactiveCategoryPrompt
import com.example.test2.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Pure MVVM ViewModel dedicated to custom AI category management and retroactive auto-categorization.
 * Owns its feature UI state independently without sharing mutable session bags.
 */
class CategoriesViewModel(
    private val repository: IPhotoRepository,
    private val navigationState: MainNavigationState,
    private val createCategoryUseCase: CreateCategoryUseCase,
    private val renameCategoryUseCase: RenameCategoryUseCase,
    private val deleteCategoryUseCase: DeleteCategoryUseCase,
    private val retroactiveCategoryUseCase: RetroactiveCategoryUseCase
) : ViewModel() {

    private val showCreateCategoryDialog = MutableStateFlow(false)
    private val categoryToEdit = MutableStateFlow<Category?>(null)
    private val categoryToDelete = MutableStateFlow<Category?>(null)
    private val retroactiveCategoryPrompt = MutableStateFlow<RetroactiveCategoryPrompt?>(null)
    private val retroactiveScanningCategory = MutableStateFlow<Category?>(null)
    private val noMatchesCategory = MutableStateFlow<Category?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    private val dataFlow = combine(
        repository.categories,
        repository.photos,
        repository.accountTier
    ) { categories, photos, tier ->
        CategoriesUiState(
            categories = categories,
            photos = photos,
            isPro = tier.isPro
        )
    }

    private val dialogsFlow = combine(
        showCreateCategoryDialog,
        categoryToEdit,
        categoryToDelete
    ) { showCreate, toEdit, toDelete ->
        Triple(showCreate, toEdit, toDelete)
    }

    private val retroactiveFlow = combine(
        retroactiveCategoryPrompt,
        retroactiveScanningCategory,
        noMatchesCategory,
        errorMessage
    ) { prompt, scanning, noMatches, err ->
        CategoriesUiState(
            retroactiveCategoryPrompt = prompt,
            retroactiveScanningCategory = scanning,
            noMatchesCategory = noMatches,
            error = err
        )
    }

    val uiState: StateFlow<CategoriesUiState> = combine(
        dataFlow,
        dialogsFlow,
        retroactiveFlow
    ) { data, dialogs, retro ->
        data.copy(
            showCreateCategoryDialog = dialogs.first,
            categoryToEdit = dialogs.second,
            categoryToDelete = dialogs.third,
            retroactiveCategoryPrompt = retro.retroactiveCategoryPrompt,
            retroactiveScanningCategory = retro.retroactiveScanningCategory,
            noMatchesCategory = retro.noMatchesCategory,
            error = retro.error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CategoriesUiState())

    fun selectCategory(categoryId: String?) {
        repository.setCategoryFilter(categoryId)
        navigationState.activeTab.value = AppTab.PHOTOS
    }

    fun openCreateCategoryDialog() {
        showCreateCategoryDialog.value = true
    }

    fun closeCreateCategoryDialog() {
        showCreateCategoryDialog.value = false
    }

    fun setCategoryToEdit(category: Category?) {
        categoryToEdit.value = category
    }

    fun setCategoryToDelete(category: Category?) {
        categoryToDelete.value = category
    }

    fun dismissNoMatchesDialog() {
        noMatchesCategory.value = null
    }

    fun dismissRetroactiveCategoryPrompt() {
        retroactiveCategoryPrompt.value = null
    }

    fun dismissError() {
        errorMessage.value = null
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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                renameCategoryUseCase(categoryId, newName, colorHex, iconName)
            } catch (e: Exception) {
                AppLogger.e("CategoriesViewModel", "Failed to rename category $categoryId", e)
                errorMessage.value = "Không thể cập nhật danh mục"
            }
        }
        setCategoryToEdit(null)
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                deleteCategoryUseCase.deleteAndReindex(categoryId)
            } catch (e: Exception) {
                AppLogger.e("CategoriesViewModel", "Failed to delete category $categoryId", e)
                errorMessage.value = "Không thể xóa danh mục"
            }
        }
        setCategoryToDelete(null)
    }

    fun triggerRetroactiveCategoryScan(
        category: Category,
        forceReindex: Boolean = false,
        onNoMatchesFound: (() -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            retroactiveScanningCategory.value = category
            try {
                val matchedPhotoIds = retroactiveCategoryUseCase.scanForCategory(category, forceReindex)
                if (matchedPhotoIds.isNotEmpty()) {
                    val matchedPhotos = repository.photos.value.filter { it.id in matchedPhotoIds }
                    retroactiveCategoryPrompt.value = RetroactiveCategoryPrompt(
                        category = category,
                        matchingPhotoIds = matchedPhotoIds,
                        previewPhotos = matchedPhotos.take(4)
                    )
                } else {
                    noMatchesCategory.value = category
                    onNoMatchesFound?.invoke()
                }
            } catch (e: Exception) {
                AppLogger.e("CategoriesViewModel", "Retroactive scan failed for ${category.name}", e)
                errorMessage.value = "Quét danh mục tự động thất bại"
            } finally {
                retroactiveScanningCategory.value = null
            }
        }
    }

    fun confirmRetroactiveCategoryMatch() {
        val prompt = retroactiveCategoryPrompt.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            retroactiveCategoryPrompt.value = null
            try {
                retroactiveCategoryUseCase.applyCategory(prompt.matchingPhotoIds, prompt.category)
            } catch (e: Exception) {
                AppLogger.e("CategoriesViewModel", "Failed applying category ${prompt.category.name}", e)
                errorMessage.value = "Không thể gán danh mục tự động"
            }
        }
    }
}
