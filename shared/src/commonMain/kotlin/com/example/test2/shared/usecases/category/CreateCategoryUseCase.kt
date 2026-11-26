package com.example.test2.shared.usecases.category

import com.example.test2.shared.models.Category
import com.example.test2.shared.repository.IPhotoRepository

/**
 * Use case for creating a custom Pro AI category.
 */
class CreateCategoryUseCase(
    private val repository: IPhotoRepository
) {
    operator fun invoke(
        name: String,
        colorHex: String = "#6366F1",
        iconName: String = "tag",
        timestampMillis: Long
    ): Category? {
        if (!repository.accountTier.value.isPro || name.isBlank()) {
            return null
        }
        return repository.createCategory(name, colorHex, iconName, timestampMillis)
    }
}
