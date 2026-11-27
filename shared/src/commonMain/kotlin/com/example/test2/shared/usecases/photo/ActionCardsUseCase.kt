package com.example.test2.shared.usecases.photo

import com.example.test2.shared.models.ActionCard
import com.example.test2.shared.models.ActionCardType
import com.example.test2.shared.repository.IPhotoRepository
import com.example.test2.shared.usecases.ActionCardPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Use case for reading, filtering, lazily re-extracting, and observing Smart Action Cards.
 */
class ActionCardsUseCase(
    private val actionCardPort: ActionCardPort,
    private val repository: IPhotoRepository
) {
    fun getActionCardsForPhoto(photoId: String): List<ActionCard> =
        actionCardPort.getActionCardsForPhoto(photoId)

    fun getPhotoIdsByType(type: ActionCardType): Set<String> =
        actionCardPort.getPhotoIdsByActionCardType(type)

    fun observeActionCards(photoId: String): Flow<List<ActionCard>> = flow {
        emit(actionCardPort.getActionCardsForPhoto(photoId))

        val photo = repository.photos.value.find { it.id == photoId }
        if (photo != null) {
            val reextracted = actionCardPort.reextractCardsIfNeeded(photo)
            if (reextracted != null) {
                emit(reextracted)
            }
        }

        actionCardPort.observeCardUpdates().collect { updatedPhotoIds ->
            if (photoId in updatedPhotoIds) {
                emit(actionCardPort.getActionCardsForPhoto(photoId))
            }
        }
    }
}
