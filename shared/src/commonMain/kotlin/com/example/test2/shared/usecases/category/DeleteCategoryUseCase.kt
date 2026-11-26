package com.example.test2.shared.usecases.category

import com.example.test2.shared.repository.IPhotoRepository

/**
 * Use case for deleting a custom category.
 */
class DeleteCategoryUseCase(
    private val repository: IPhotoRepository
) {
    operator fun invoke(categoryId: String): Boolean {
        return repository.deleteCategory(categoryId)
    }
}
