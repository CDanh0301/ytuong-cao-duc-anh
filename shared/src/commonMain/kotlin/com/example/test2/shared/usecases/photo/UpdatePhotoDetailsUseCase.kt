package com.example.test2.shared.usecases.photo

import com.example.test2.shared.models.Photo
import com.example.test2.shared.repository.IPhotoRepository
import com.example.test2.shared.usecases.FtsIndexPort
import com.example.test2.shared.usecases.QueueSyncPort

/**
 * Use case for editing photo metadata and keeping queue metadata and the FTS5 index synchronized.
 */
class UpdatePhotoDetailsUseCase(
    private val repository: IPhotoRepository,
    private val ftsIndexPort: FtsIndexPort,
    private val queueSyncPort: QueueSyncPort? = null
) {
    operator fun invoke(
        photoId: String,
        title: String,
        description: String,
        folderId: String,
        categoryIds: Set<String>,
        isFavorite: Boolean? = null
    ): Photo? {
        val previousCategoryIds = repository.photos.value.firstOrNull { it.id == photoId }?.categoryIds
        val updated = repository.updatePhoto(photoId, title, description, folderId, categoryIds, isFavorite)
        updated?.let { photo ->
            if (previousCategoryIds != photo.categoryIds) {
                queueSyncPort?.updatePhotosCategoryInQueue(
                    photoIds = listOf(photo.id),
                    targetCategoryId = photo.categoryIds.firstOrNull(),
                    lockCategory = true
                )
            }
            val categoryNames = repository.categories.value
                .filter { it.id in photo.categoryIds }
                .joinToString(" ") { it.name }
            ftsIndexPort.indexPhotoFts(
                photoId = photo.id,
                title = photo.title,
                scrubbedText = photo.piiScrubbedText ?: photo.description,
                intent = photo.detectedIntent ?: "",
                categoryNames = categoryNames
            )
        }
        return updated
    }
}
