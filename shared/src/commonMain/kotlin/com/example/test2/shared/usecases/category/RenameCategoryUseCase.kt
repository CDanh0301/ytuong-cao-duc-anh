package com.example.test2.shared.usecases.category

import com.example.test2.shared.models.Category
import com.example.test2.shared.repository.IPhotoRepository
import com.example.test2.shared.usecases.SemanticSearchPort

/**
 * Use case for renaming/updating a custom category and keeping vector indexes synchronized.
 */
class RenameCategoryUseCase(
    private val repository: IPhotoRepository,
    private val searchPort: SemanticSearchPort
) {
    suspend operator fun invoke(
        categoryId: String,
        newName: String,
        colorHex: String,
        iconName: String? = null
    ): Category? {
        if (newName.isBlank()) return null
        val updated = repository.renameCategory(categoryId, newName, colorHex, iconName)
        if (updated != null) {
            searchPort.ensureVectorsIndexed(forceReindex = true)
        }
        return updated
    }
}
