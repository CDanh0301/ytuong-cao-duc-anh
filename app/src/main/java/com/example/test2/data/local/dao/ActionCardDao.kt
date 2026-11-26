package com.example.test2.data.local.dao

import com.example.test2.shared.models.ActionCard
import com.example.test2.shared.models.ActionCardType

/**
 * Data Access Object contract for Smart Action Cards extracted from screenshots.
 */
interface ActionCardDao {
    fun replaceActionCardsForPhoto(photoId: String, cards: List<ActionCard>)

    fun saveActionCards(cards: List<ActionCard>)

    fun getActionCardsForPhoto(photoId: String): List<ActionCard>

    fun getPhotoIdsByActionCardType(type: ActionCardType): Set<String>

    fun getActionCardsPhotoCountByType(): Map<ActionCardType, Int>

    fun deleteActionCardsForPhoto(photoId: String): Boolean

    fun cleanOrphanedActionCards(validPhotoIds: Set<String>? = null)
}
