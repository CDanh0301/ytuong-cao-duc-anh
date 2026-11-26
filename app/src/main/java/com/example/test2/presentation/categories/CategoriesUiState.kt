package com.example.test2.presentation.categories

import com.example.test2.shared.models.Category
import com.example.test2.ui.RetroactiveCategoryPrompt

data class CategoriesUiState(
    val showCreateCategoryDialog: Boolean = false,
    val categoryToEdit: Category? = null,
    val categoryToDelete: Category? = null,
    val retroactiveCategoryPrompt: RetroactiveCategoryPrompt? = null,
    val retroactiveScanningCategory: Category? = null,
    val noMatchesCategory: Category? = null
)
