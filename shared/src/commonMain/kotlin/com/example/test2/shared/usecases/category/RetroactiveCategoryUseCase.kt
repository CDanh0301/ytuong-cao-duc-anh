package com.example.test2.shared.usecases.category

import com.example.test2.shared.models.Category
import com.example.test2.shared.repository.IPhotoRepository
import com.example.test2.shared.usecases.FtsIndexPort
import com.example.test2.shared.usecases.QueueSyncPort
import com.example.test2.shared.usecases.RetroactiveCategorizerPort
import com.example.test2.shared.usecases.SemanticSearchPort

/**
 * Use case for scanning existing photos for retroactive custom category assignment
 * and applying the reassignment while keeping queue metadata, FTS5, and vector indexes synchronized.
 */
class RetroactiveCategoryUseCase(
    private val categorizer: RetroactiveCategorizerPort,
    private val searchPort: SemanticSearchPort,
    private val ftsIndexPort: FtsIndexPort,
    private val repository: IPhotoRepository,
    private val queueSyncPort: QueueSyncPort? = null
) {
    suspend fun scanForCategory(category: Category, forceReindex: Boolean = false): List<String> {
        searchPort.ensureVectorsIndexed(forceReindex = forceReindex)
        val photos = repository.photos.value
        return categorizer.findMatchingPhotosForCategory(category = category, photos = photos)
    }

    suspend fun applyCategory(photoIds: List<String>, category: Category): Int {
        val count = repository.reassignPhotosToCategory(photoIds, category.id)
        if (count > 0) {
            queueSyncPort?.updatePhotosCategoryInQueue(
                photoIds = photoIds,
                targetCategoryId = category.id,
                lockCategory = true
            )
            val idSet = photoIds.toSet()
            val updatedPhotos = repository.photos.value.filter { it.id in idSet }
            for (p in updatedPhotos) {
                ftsIndexPort.indexPhotoFts(
                    photoId = p.id,
                    title = p.title,
                    scrubbedText = p.piiScrubbedText ?: p.description,
                    intent = p.detectedIntent ?: "",
                    categoryNames = category.name,
                    semanticTags = p.semanticTags.joinToString(" ")
                )
            }
            searchPort.ensureVectorsIndexed(forceReindex = true)
        }
        return count
    }
}
