package com.example.test2.shared.usecases.photo

import com.example.test2.shared.repository.IPhotoRepository

/**
 * Use case for toggling a photo's favorite status.
 */
class ToggleFavoriteUseCase(
    private val repository: IPhotoRepository
) {
    operator fun invoke(photoId: String): Boolean {
        return repository.toggleFavorite(photoId)
    }
}
