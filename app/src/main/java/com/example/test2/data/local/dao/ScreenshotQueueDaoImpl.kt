package com.example.test2.data.local.dao

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.test2.database.ProcessedScreenshotRecord
import com.example.test2.database.QueueStateUpdate
import com.example.test2.database.QueueStatus
import com.example.test2.database.ScreenshotQueueItem
import com.example.test2.database.toProcessedScreenshotRecord

/**
 * SQLite implementation of [ScreenshotQueueDao].
 */
class ScreenshotQueueDaoImpl(
    private val dbProvider: SQLiteOpenHelper,
    private val ftsDao: FtsSearchDaoImpl,
    private val vectorDao: VectorDaoImpl,
    private val actionCardDao: ActionCardDaoImpl,
    private val logError: (String, Throwable) -> Unit
) : ScreenshotQueueDao {

    override fun enqueueScreenshot(
        photoId: String,
        filePath: String,
        dateAdded: Long,
        isLiveCapture: Boolean,
        scrubbedText: String?,
        photoTitle: String?,
        originalFileName: String?
    ): Boolean {
        return try {
            val db = dbProvider.writableDatabase
            val now = System.currentTimeMillis()
            val values = ContentValues().apply {
                put("photo_id", photoId)
                put("file_path", filePath)
                put("date_added", dateAdded)
                put("status", QueueStatus.PENDING.name)
                put("is_live_capture", if (isLiveCapture) 1 else 0)
                if (scrubbedText != null) put("scrubbed_text", scrubbedText)
                if (photoTitle != null) put("photo_title", photoTitle)
                if (originalFileName != null) put("original_file_name", originalFileName)
                put("created_at", now)
                put("updated_at", now)
            }
            val rowId = db.insertWithOnConflict(TABLE_QUEUE, null, values, SQLiteDatabase.CONFLICT_IGNORE)
            if (rowId < 0) {
                val updateValues = ContentValues().apply {
                    put("file_path", filePath)
                    if (scrubbedText != null) put("scrubbed_text", scrubbedText)
                    if (photoTitle != null) put("photo_title", photoTitle)
                    if (originalFileName != null) put("original_file_name", originalFileName)
                    put("updated_at", now)
                }
                db.update(TABLE_QUEUE, updateValues, "photo_id = ? AND status != ?", arrayOf(photoId, QueueStatus.COMPLETED.name)) >= 0
            } else {
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun syncVaultedPathToQueue(
        photoId: String,
        newFilePath: String,
        dateAdded: Long,
        photoTitle: String?,
        originalFileName: String?
    ): Boolean {
        return try {
            val db = dbProvider.writableDatabase
            val now = System.currentTimeMillis()
            val values = ContentValues().apply {
                put("file_path", newFilePath)
                put("updated_at", now)
            }
            val updatedRows = db.update(TABLE_QUEUE, values, "photo_id = ?", arrayOf(photoId))
            if (updatedRows > 0) {
                true
            } else {
                val insertValues = ContentValues().apply {
                    put("photo_id", photoId)
                    put("file_path", newFilePath)
                    put("date_added", dateAdded)
                    put("status", QueueStatus.PENDING.name)
                    put("is_live_capture", 0)
                    if (!photoTitle.isNullOrBlank()) put("photo_title", photoTitle)
                    if (!originalFileName.isNullOrBlank()) put("original_file_name", originalFileName)
                    put("created_at", now)
                    put("updated_at", now)
                }
                db.insertWithOnConflict(TABLE_QUEUE, null, insertValues, SQLiteDatabase.CONFLICT_IGNORE) >= 0
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun enqueueScreenshotsBatch(items: List<Triple<String, String, Long>>): Int {
        if (items.isEmpty()) return 0
        return try {
            val db = dbProvider.writableDatabase
            db.beginTransaction()
            var count = 0
            val now = System.currentTimeMillis()
            try {
                for (item in items) {
                    val values = ContentValues().apply {
                        put("photo_id", item.first)
                        put("file_path", item.second)
                        put("date_added", item.third)
                        put("status", QueueStatus.PENDING.name)
                        put("is_live_capture", 0)
                        put("created_at", now)
                        put("updated_at", now)
                    }
                    val rowId = db.insertWithOnConflict(TABLE_QUEUE, null, values, SQLiteDatabase.CONFLICT_IGNORE)
                    if (rowId >= 0) count++
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            count
        } catch (_: Exception) {
            0
        }
    }

    override fun claimNextPendingQueueItem(): ScreenshotQueueItem? {
        return claimNextPendingQueueItems(limit = 1).firstOrNull()
    }

    override fun claimNextPendingQueueItems(limit: Int): List<ScreenshotQueueItem> {
        val db = dbProvider.writableDatabase
        db.beginTransaction()
        try {
            val claimedItems = mutableListOf<ScreenshotQueueItem>()
            val safeLimit = limit.coerceIn(1, 20)
            db.rawQuery(
                "SELECT id, photo_id, file_path, date_added, status, is_live_capture, scrubbed_text, photo_title, original_file_name FROM $TABLE_QUEUE WHERE status = 'PENDING' ORDER BY date_added ASC LIMIT $safeLimit",
                null
            ).use { cursor ->
                val now = System.currentTimeMillis()
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val photoId = cursor.getString(1)
                    val filePath = cursor.getString(2)
                    val dateAdded = cursor.getLong(3)
                    val isLiveCapture = if (cursor.columnCount > 5) cursor.getInt(5) == 1 else false
                    val scrubbedText = if (cursor.columnCount > 6) cursor.getString(6) else null
                    val photoTitle = if (cursor.columnCount > 7) cursor.getString(7) else null
                    val originalFileName = if (cursor.columnCount > 8) cursor.getString(8) else null

                    val values = ContentValues().apply {
                        put("status", QueueStatus.PROCESSING.name)
                        put("updated_at", now)
                    }
                    val updated = db.update(TABLE_QUEUE, values, "id = ? AND status = 'PENDING'", arrayOf(id.toString()))
                    if (updated > 0) {
                        claimedItems.add(
                            ScreenshotQueueItem(
                                id = id,
                                photoId = photoId,
                                filePath = filePath,
                                dateAdded = dateAdded,
                                status = QueueStatus.PROCESSING.name,
                                isLiveCapture = isLiveCapture,
                                scrubbedText = scrubbedText,
                                photoTitle = photoTitle,
                                originalFileName = originalFileName
                            )
                        )
                    }
                }
            }
            db.setTransactionSuccessful()
            return claimedItems
        } catch (e: Exception) {
            logError("Failed to claim next pending items", e)
            return emptyList()
        } finally {
            db.endTransaction()
        }
    }

    override fun updateQueueItemState(update: QueueStateUpdate) {
        try {
            val db = dbProvider.writableDatabase
            val values = ContentValues().apply {
                put("status", update.status)
                put("updated_at", System.currentTimeMillis())
                if (update.scrubbedText != null) put("scrubbed_text", update.scrubbedText)
                if (update.detectedIntent != null) put("detected_intent", update.detectedIntent)
                if (update.eventTitle != null) put("event_title", update.eventTitle)
                if (update.eventDateMillis != null) put("event_date_millis", update.eventDateMillis)
                if (update.targetCategoryId != null) put("target_category_id", update.targetCategoryId)
                if (update.eventDescription != null) put("event_description", update.eventDescription)
                if (update.summary != null) put("summary", update.summary)
                if (update.tags != null) put("tags", update.tags)
                if (update.entities != null) put("entities", update.entities)
                if (update.itemsToBuy != null) put("items_to_buy", update.itemsToBuy)
                if (update.needsCloudUpgrade) put("needs_cloud_upgrade", 1) else put("needs_cloud_upgrade", 0)
                if (update.modelName != null) put("model_name", update.modelName)
                if (update.modelVersion != null) put("model_version", update.modelVersion)
            }
            db.update(TABLE_QUEUE, values, "photo_id = ?", arrayOf(update.photoId))
        } catch (e: Exception) {
            logError("Failed to update queue item state for ${update.photoId}", e)
        }
    }

    override fun updateQueueItemState(
        photoId: String,
        status: String,
        scrubbedText: String?,
        detectedIntent: String?,
        eventTitle: String?,
        eventDateMillis: Long?,
        targetCategoryId: String?,
        eventDescription: String?,
        summary: String?,
        tags: String?,
        entities: String?,
        itemsToBuy: String?,
        needsCloudUpgrade: Boolean,
        modelName: String?,
        modelVersion: String?
    ) {
        updateQueueItemState(
            QueueStateUpdate(
                photoId = photoId,
                status = status,
                scrubbedText = scrubbedText,
                detectedIntent = detectedIntent,
                eventTitle = eventTitle,
                eventDateMillis = eventDateMillis,
                targetCategoryId = targetCategoryId,
                eventDescription = eventDescription,
                summary = summary,
                tags = tags,
                entities = entities,
                itemsToBuy = itemsToBuy,
                needsCloudUpgrade = needsCloudUpgrade,
                modelName = modelName,
                modelVersion = modelVersion
            )
        )
    }

    override fun getPendingQueue(): List<ScreenshotQueueItem> {
        val items = mutableListOf<ScreenshotQueueItem>()
        try {
            val db = dbProvider.readableDatabase
            db.rawQuery(
                "SELECT id, photo_id, file_path, date_added, status, is_live_capture FROM $TABLE_QUEUE WHERE status = 'PENDING' ORDER BY date_added DESC LIMIT 50",
                null
            ).use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow("id")
                val photoIdCol = cursor.getColumnIndexOrThrow("photo_id")
                val pathCol = cursor.getColumnIndexOrThrow("file_path")
                val dateCol = cursor.getColumnIndexOrThrow("date_added")
                val statusCol = cursor.getColumnIndexOrThrow("status")
                val isLiveCol = cursor.getColumnIndex("is_live_capture")

                while (cursor.moveToNext()) {
                    items.add(
                        ScreenshotQueueItem(
                            id = cursor.getLong(idCol),
                            photoId = cursor.getString(photoIdCol),
                            filePath = cursor.getString(pathCol),
                            dateAdded = cursor.getLong(dateCol),
                            status = cursor.getString(statusCol),
                            isLiveCapture = if (isLiveCol >= 0) cursor.getInt(isLiveCol) == 1 else false
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return items
    }

    override fun getQueueItemsNeedingCloudUpgrade(): List<ScreenshotQueueItem> {
        val items = mutableListOf<ScreenshotQueueItem>()
        try {
            val db = dbProvider.readableDatabase
            db.rawQuery(
                "SELECT id, photo_id, file_path, date_added, status, scrubbed_text, detected_intent, event_title, event_date_millis, target_category_id, event_description FROM $TABLE_QUEUE WHERE needs_cloud_upgrade = 1 LIMIT 20",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    items.add(
                        ScreenshotQueueItem(
                            id = cursor.getLong(0),
                            photoId = cursor.getString(1),
                            filePath = cursor.getString(2),
                            dateAdded = cursor.getLong(3),
                            status = cursor.getString(4),
                            scrubbedText = cursor.getString(5),
                            detectedIntent = cursor.getString(6),
                            eventTitle = cursor.getString(7),
                            eventDateMillis = if (cursor.isNull(8)) null else cursor.getLong(8),
                            targetCategoryId = cursor.getString(9),
                            eventDescription = cursor.getString(10),
                            needsCloudUpgrade = true
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return items
    }

    override fun markQueueItemProcessed(photoId: String, status: String) {
        try {
            val db = dbProvider.writableDatabase
            val values = ContentValues().apply {
                put("status", status)
                put("updated_at", System.currentTimeMillis())
            }
            db.update(TABLE_QUEUE, values, "photo_id = ?", arrayOf(photoId))
        } catch (_: Exception) {
        }
    }

    override fun markQueueItemProcessed(photoId: String, status: QueueStatus) {
        markQueueItemProcessed(photoId, status.name)
    }

    override fun isScreenshotProcessedOrNotified(photoId: String): Boolean {
        return try {
            val db = dbProvider.readableDatabase
            db.rawQuery(
                "SELECT id FROM $TABLE_QUEUE WHERE photo_id = ? AND status = 'COMPLETED'",
                arrayOf(photoId)
            ).use { cursor ->
                cursor.moveToFirst()
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun recordProcessedScreenshot(
        photoId: String,
        filePath: String,
        dateAdded: Long,
        scrubbedText: String?,
        detectedIntent: String?,
        eventTitle: String?,
        eventDateMillis: Long?,
        targetCategoryId: String?,
        status: String,
        eventDescription: String?,
        summary: String?,
        tags: String?,
        entities: String?,
        itemsToBuy: String?,
        photoTitle: String?,
        originalFileName: String?
    ) {
        try {
            val db = dbProvider.writableDatabase
            val now = System.currentTimeMillis()
            var existingTitle: String? = null
            var existingOrigName: String? = null
            if (photoTitle == null || originalFileName == null) {
                db.rawQuery(
                    "SELECT photo_title, original_file_name FROM $TABLE_QUEUE WHERE photo_id = ?",
                    arrayOf(photoId)
                ).use { c ->
                    if (c.moveToFirst()) {
                        if (!c.isNull(0)) existingTitle = c.getString(0)
                        if (!c.isNull(1)) existingOrigName = c.getString(1)
                    }
                }
            }
            val finalTitle = photoTitle ?: existingTitle
            val finalOrigName = originalFileName ?: existingOrigName
            val values = ContentValues().apply {
                put("photo_id", photoId)
                put("file_path", filePath)
                put("date_added", dateAdded)
                put("status", status)
                put("scrubbed_text", scrubbedText)
                put("detected_intent", detectedIntent)
                put("event_title", eventTitle)
                put("event_date_millis", eventDateMillis)
                put("target_category_id", targetCategoryId)
                put("event_description", eventDescription)
                put("summary", summary)
                put("tags", tags)
                put("entities", entities)
                put("items_to_buy", itemsToBuy)
                if (!finalTitle.isNullOrBlank()) put("photo_title", finalTitle)
                if (!finalOrigName.isNullOrBlank()) put("original_file_name", finalOrigName)
                put("created_at", now)
                put("updated_at", now)
            }
            db.insertWithOnConflict(TABLE_QUEUE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
            logError("Failed to record processed screenshot $photoId", e)
        }
    }

    override fun getProcessedScreenshot(photoId: String): ProcessedScreenshotRecord? {
        return try {
            val db = dbProvider.readableDatabase
            db.rawQuery(
                "SELECT photo_id, file_path, scrubbed_text, detected_intent, event_title, event_date_millis, target_category_id, event_description, summary, tags, entities, items_to_buy, status, date_added, photo_title, original_file_name FROM $TABLE_QUEUE WHERE photo_id = ? AND status = 'COMPLETED'",
                arrayOf(photoId)
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.toProcessedScreenshotRecord() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun resetHangingProcessingItems() {
        try {
            val db = dbProvider.writableDatabase
            val values = ContentValues().apply {
                put("status", QueueStatus.PENDING.name)
                put("updated_at", System.currentTimeMillis())
            }
            db.update(TABLE_QUEUE, values, "status = ?", arrayOf(QueueStatus.PROCESSING.name))
        } catch (_: Exception) {
        }
    }

    override fun getPendingQueueCount(): Int {
        return try {
            val db = dbProvider.readableDatabase
            db.rawQuery(
                "SELECT COUNT(*) FROM $TABLE_QUEUE WHERE status IN ('PENDING', 'PROCESSING', 'OCR_DONE')",
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        } catch (_: Exception) {
            0
        }
    }

    override fun migratePhotoRecords(
        oldPhotoId: String,
        newPhotoId: String,
        newFilePath: String,
        dateAdded: Long,
        scrubbedText: String?,
        detectedIntent: String?,
        eventTitle: String?,
        eventDateMillis: Long?,
        targetCategoryId: String?,
        eventDescription: String?,
        photoTitle: String?,
        originalFileName: String?
    ) {
        try {
            val db = dbProvider.writableDatabase
            val now = System.currentTimeMillis()

            val oldRecord = getProcessedScreenshot(oldPhotoId)
            var fallbackTitle: String? = null
            var fallbackOrigName: String? = null
            var oldStatus: String? = oldRecord?.status
            if (oldRecord == null) {
                db.rawQuery(
                    "SELECT photo_title, original_file_name, status FROM $TABLE_QUEUE WHERE photo_id = ?",
                    arrayOf(oldPhotoId)
                ).use { c ->
                    if (c.moveToFirst()) {
                        if (!c.isNull(0)) fallbackTitle = c.getString(0)
                        if (!c.isNull(1)) fallbackOrigName = c.getString(1)
                        if (!c.isNull(2)) oldStatus = c.getString(2)
                    }
                }
            }

            val finalScrubbedText = scrubbedText ?: oldRecord?.scrubbedText
            val finalIntent = detectedIntent ?: oldRecord?.detectedIntent
            val finalEventTitle = eventTitle ?: oldRecord?.eventTitle
            val finalEventDate = eventDateMillis ?: oldRecord?.eventDateMillis
            val finalCategoryId = targetCategoryId ?: oldRecord?.targetCategoryId
            val finalDescription = eventDescription ?: oldRecord?.eventDescription
            val finalSummary = oldRecord?.summary
            val finalTags = oldRecord?.tags
            val finalEntities = oldRecord?.entities
            val finalItems = oldRecord?.itemsToBuy
            val finalPhotoTitle = photoTitle ?: oldRecord?.photoTitle ?: fallbackTitle
            val finalOrigName = originalFileName ?: oldRecord?.originalFileName ?: fallbackOrigName
            val finalStatus = oldStatus ?: QueueStatus.COMPLETED.name

            val values = ContentValues().apply {
                put("photo_id", newPhotoId)
                put("file_path", newFilePath)
                put("date_added", dateAdded)
                put("status", finalStatus)
                put("scrubbed_text", finalScrubbedText)
                put("detected_intent", finalIntent)
                put("event_title", finalEventTitle)
                put("event_date_millis", finalEventDate)
                put("target_category_id", finalCategoryId)
                put("event_description", finalDescription)
                put("summary", finalSummary)
                put("tags", finalTags)
                put("entities", finalEntities)
                put("items_to_buy", finalItems)
                if (!finalPhotoTitle.isNullOrBlank()) put("photo_title", finalPhotoTitle)
                if (!finalOrigName.isNullOrBlank()) put("original_file_name", finalOrigName)
                put("created_at", now)
                put("updated_at", now)
            }
            db.insertWithOnConflict(TABLE_QUEUE, null, values, SQLiteDatabase.CONFLICT_REPLACE)

            if (oldPhotoId != newPhotoId) {
                try {
                    db.execSQL("UPDATE ${ActionCardDaoImpl.TABLE_ACTION_CARDS} SET photo_id = ? WHERE photo_id = ?", arrayOf(newPhotoId, oldPhotoId))
                } catch (_: Exception) {}

                db.delete(TABLE_QUEUE, "photo_id = ?", arrayOf(oldPhotoId))

                try {
                    db.execSQL("UPDATE ${FtsSearchDaoImpl.TABLE_FTS} SET photo_id = ? WHERE photo_id = ?", arrayOf(newPhotoId, oldPhotoId))
                } catch (_: Exception) {}

                try {
                    db.execSQL("UPDATE ${VectorDaoImpl.TABLE_VECTORS} SET photo_id = ? WHERE photo_id = ?", arrayOf(newPhotoId, oldPhotoId))
                    vectorDao.migrateCachedVectorKey(oldPhotoId, newPhotoId)
                } catch (_: Exception) {}

                try {
                    db.execSQL("UPDATE ${VectorDaoImpl.TABLE_NEURAL_VECTORS} SET photo_id = ? WHERE photo_id = ?", arrayOf(newPhotoId, oldPhotoId))
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            logError("Failed to migrate photo records from $oldPhotoId to $newPhotoId", e)
        }
    }

    override fun getProcessedScreenshotsByIds(photoIds: List<String>): List<ProcessedScreenshotRecord> {
        if (photoIds.isEmpty()) return emptyList()
        return try {
            val db = dbProvider.readableDatabase
            val placeholders = photoIds.joinToString(",") { "?" }
            db.rawQuery(
                "SELECT photo_id, file_path, scrubbed_text, detected_intent, event_title, event_date_millis, target_category_id, event_description, summary, tags, entities, items_to_buy, status, date_added, photo_title, original_file_name FROM $TABLE_QUEUE WHERE photo_id IN ($placeholders) AND status = 'COMPLETED'",
                photoIds.toTypedArray()
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.toProcessedScreenshotRecord())
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun getAllCompletedProcessedScreenshots(): List<ProcessedScreenshotRecord> {
        return try {
            val db = dbProvider.readableDatabase
            db.rawQuery(
                "SELECT photo_id, file_path, scrubbed_text, detected_intent, event_title, event_date_millis, target_category_id, event_description, summary, tags, entities, items_to_buy, status, date_added, photo_title, original_file_name FROM $TABLE_QUEUE WHERE status = 'COMPLETED'",
                null
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.toProcessedScreenshotRecord())
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun getAllCompletedOrVaultedScreenshots(): List<ProcessedScreenshotRecord> {
        return try {
            val db = dbProvider.readableDatabase
            db.rawQuery(
                "SELECT photo_id, file_path, scrubbed_text, detected_intent, event_title, event_date_millis, target_category_id, event_description, summary, tags, entities, items_to_buy, status, date_added, photo_title, original_file_name FROM $TABLE_QUEUE WHERE status = 'COMPLETED' OR file_path LIKE '%.enc'",
                null
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.toProcessedScreenshotRecord())
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun purgePhotoData(photoId: String) {
        try {
            val db = dbProvider.writableDatabase
            db.delete(TABLE_QUEUE, "photo_id = ?", arrayOf(photoId))
        } catch (_: Exception) {}
        ftsDao.removePhotoFts(photoId)
        vectorDao.deletePhotoVector(photoId)
        vectorDao.deletePhotoNeuralVector(photoId)
        actionCardDao.deleteActionCardsForPhoto(photoId)
    }

    companion object {
        const val TABLE_QUEUE = "screenshot_queue"
    }
}
