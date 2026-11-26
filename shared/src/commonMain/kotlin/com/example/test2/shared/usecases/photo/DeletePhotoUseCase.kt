package com.example.test2.shared.usecases.photo

import com.example.test2.shared.models.Photo
import com.example.test2.shared.repository.IPhotoRepository
import com.example.test2.shared.usecases.PhotoStorageCleanerPort

/**
 * Use case for permanently deleting a photo's physical file (if private), DB indexes, and repository state.
 */
class DeletePhotoUseCase(
    private val repository: IPhotoRepository,
    private val storageCleaner: PhotoStorageCleanerPort
) {
    suspend operator fun invoke(photo: Photo) {
        if (!photo.filePath.startsWith("content://")) {
            storageCleaner.deletePhysicalFile(photo.filePath)
        }
        storageCleaner.purgePhotoData(photo.id)
        repository.deletePhoto(photo.id)
    }
}
