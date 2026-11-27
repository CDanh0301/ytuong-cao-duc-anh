package com.example.test2.shared.usecases.photo

import com.example.test2.shared.models.Photo
import com.example.test2.shared.repository.IPhotoRepository
import com.example.test2.shared.usecases.PhotoProcessingPort

/**
 * Use case for running cloud-assisted AI analysis on a single photo.
 */
class ScanSinglePhotoUseCase(
    private val repository: IPhotoRepository,
    private val processingPort: PhotoProcessingPort
) {
    suspend operator fun invoke(photoId: String): Photo? {
        val photo = repository.photos.value.firstOrNull { it.id == photoId } ?: return null
        processingPort.scanPhotoWithCloud(photo)
        return repository.photos.value.firstOrNull { it.id == photoId }
    }
}
