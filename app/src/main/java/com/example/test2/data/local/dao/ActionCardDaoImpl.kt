package com.example.test2.data.local.dao

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.test2.shared.models.ActionCard
import com.example.test2.shared.models.ActionCardType

/**
 * SQLite implementation of [ActionCardDao].
 */
class ActionCardDaoImpl(
    private val dbProvider: SQLiteOpenHelper,
    private val logError: (String, Throwable) -> Unit
) : ActionCardDao {

    override fun replaceActionCardsForPhoto(photoId: String, cards: List<ActionCard>) {
        try {
            val db = dbProvider.writableDatabase
            db.beginTransaction()
            try {
                db.delete(TABLE_ACTION_CARDS, "photo_id = ?", arrayOf(photoId))
                val baseTime = System.currentTimeMillis()
                for ((index, card) in cards.withIndex()) {
                    val createdAt = if (card.createdAt > 0L) card.createdAt else baseTime + index
                    val values = ContentValues().apply {
                        put("id", card.id)
                        put("photo_id", photoId)
                        put("type", card.type.name)
                        put("title", card.title)
                        put("subtitle", card.subtitle)
                        put("payload_json", card.payloadJson)
                        put("created_at", createdAt)
                    }
                    db.insertWithOnConflict(TABLE_ACTION_CARDS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            logError("Failed to replace action cards for photo $photoId", e)
        }
    }

    override fun saveActionCards(cards: List<ActionCard>) {
        if (cards.isEmpty()) return
        try {
            val db = dbProvider.writableDatabase
            db.beginTransaction()
            try {
                for (card in cards) {
                    val values = ContentValues().apply {
                        put("id", card.id)
                        put("photo_id", card.photoId)
                        put("type", card.type.name)
                        put("title", card.title)
                        put("subtitle", card.subtitle)
                        put("payload_json", card.payloadJson)
                        put("created_at", if (card.createdAt > 0L) card.createdAt else System.currentTimeMillis())
                    }
                    db.insertWithOnConflict(TABLE_ACTION_CARDS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            logError("Failed to save action cards", e)
        }
    }

    override fun getActionCardsForPhoto(photoId: String): List<ActionCard> {
        val result = mutableListOf<ActionCard>()
        try {
            val db = dbProvider.readableDatabase
            db.rawQuery(
                "SELECT id, photo_id, type, title, subtitle, payload_json, created_at FROM $TABLE_ACTION_CARDS WHERE photo_id = ? ORDER BY created_at ASC",
                arrayOf(photoId)
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val pId = cursor.getString(1)
                    val typeStr = cursor.getString(2)
                    val title = cursor.getString(3)
                    val subtitle = cursor.getString(4)
                    val payloadJson = cursor.getString(5)
                    val createdAt = cursor.getLong(6)
                    val type = try {
                        ActionCardType.valueOf(typeStr)
                    } catch (_: Exception) {
                        ActionCardType.ALL
                    }
                    result.add(
                        ActionCard(
                            id = id,
                            photoId = pId,
                            type = type,
                            title = title,
                            subtitle = subtitle,
                            payloadJson = payloadJson,
                            createdAt = createdAt
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    override fun getPhotoIdsByActionCardType(type: ActionCardType): Set<String> {
        val ids = mutableSetOf<String>()
        try {
            val db = dbProvider.readableDatabase
            val cursor = if (type == ActionCardType.ALL) {
                db.rawQuery("SELECT DISTINCT photo_id FROM $TABLE_ACTION_CARDS", null)
            } else {
                db.rawQuery("SELECT DISTINCT photo_id FROM $TABLE_ACTION_CARDS WHERE type = ?", arrayOf(type.name))
            }
            cursor.use {
                while (it.moveToNext()) {
                    ids.add(it.getString(0))
                }
            }
        } catch (_: Exception) {
        }
        return ids
    }

    override fun getActionCardsPhotoCountByType(): Map<ActionCardType, Int> {
        val counts = mutableMapOf<ActionCardType, Int>()
        try {
            val db = dbProvider.readableDatabase
            db.rawQuery(
                "SELECT type, COUNT(DISTINCT photo_id) FROM $TABLE_ACTION_CARDS GROUP BY type",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val typeStr = cursor.getString(0)
                    val count = cursor.getInt(1)
                    try {
                        val type = ActionCardType.valueOf(typeStr)
                        counts[type] = count
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
        return counts
    }

    override fun deleteActionCardsForPhoto(photoId: String): Boolean {
        return try {
            val db = dbProvider.writableDatabase
            db.delete(TABLE_ACTION_CARDS, "photo_id = ?", arrayOf(photoId)) > 0
        } catch (_: Exception) {
            false
        }
    }

    override fun cleanOrphanedActionCards(validPhotoIds: Set<String>?) {
        try {
            val db = dbProvider.writableDatabase
            db.delete(TABLE_ACTION_CARDS, "type = ?", arrayOf("EVENT"))
        } catch (_: Exception) {
        }
    }

    companion object {
        const val TABLE_ACTION_CARDS = "photo_action_cards"
    }
}
