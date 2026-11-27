package com.example.test2.shared.usecases.category

import com.example.test2.shared.repository.IPhotoRepository
import com.example.test2.shared.usecases.QueueSyncPort
import com.example.test2.shared.usecases.SemanticSearchPort

/**
 * Use case for deleting a custom category and keeping queue metadata and vector indexes synchronized.
 */
class DeleteCategoryUseCase(
    private val repository: IPhotoRepository,
    private val searchPort: SemanticSearchPort? = null,
    private val queueSyncPort: QueueSyncPort? = null
) {
    operator fun invoke(categoryId: String): Boolean {
        val deleted = repository.deleteCategory(categoryId)
        if (deleted) {
            queueSyncPort?.clearCategoryInQueue(categoryId)
        }
        return deleted
    }

    suspend fun deleteAndReindex(categoryId: String): Boolean {
        val deleted = invoke(categoryId)
        if (deleted) {
            searchPort?.ensureVectorsIndexed(forceReindex = true)
        }
        return deleted
    }
}
