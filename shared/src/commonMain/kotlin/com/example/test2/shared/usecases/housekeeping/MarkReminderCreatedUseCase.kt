package com.example.test2.shared.usecases.housekeeping

import com.example.test2.shared.repository.IPhotoRepository

/**
 * Use case for marking that a calendar reminder was created for a photo.
 */
class MarkReminderCreatedUseCase(
    private val repository: IPhotoRepository
) {
    operator fun invoke(photoId: String) {
        repository.markReminderCreated(photoId)
    }
}
