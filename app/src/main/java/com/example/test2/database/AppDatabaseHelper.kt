package com.example.test2.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.test2.data.local.dao.ActionCardDao
import com.example.test2.data.local.dao.ActionCardDaoImpl
import com.example.test2.data.local.dao.FtsSearchDao
import com.example.test2.data.local.dao.FtsSearchDaoImpl
import com.example.test2.data.local.dao.ScreenshotQueueDao
import com.example.test2.data.local.dao.ScreenshotQueueDaoImpl
import com.example.test2.data.local.dao.VectorDao
import com.example.test2.data.local.dao.VectorDaoImpl
import com.example.test2.shared.models.ActionCard
import com.example.test2.shared.models.ActionCardType
import com.example.test2.util.AppLogger

data class ScreenshotQueueItem(
    val id: Long,
    val photoId: String,
    val filePath: String,
    val dateAdded: Long,
    val status: String,
    val scrubbedText: String? = null,
    val detectedIntent: String? = null,
    val eventTitle: String? = null,
    val eventDateMillis: Long? = null,
    val targetCategoryId: String? = null,
    val eventDescription: String? = null,
    val summary: String? = null,
    val tags: String? = null,
    val entities: String? = null,
    val itemsToBuy: String? = null,
    val needsCloudUpgrade: Boolean = false,
    val modelName: String? = null,
    val modelVersion: String? = null,
    val isLiveCapture: Boolean = false,
    val photoTitle: String? = null,
    val originalFileName: String? = null,
    val isUserCategorized: Boolean = false
)

data class ProcessedScreenshotRecord(
    val photoId: String,
    val filePath: String,
    val scrubbedText: String?,
    val detectedIntent: String?,
    val eventTitle: String?,
    val eventDateMillis: Long?,
    val targetCategoryId: String?,
    val eventDescription: String? = null,
    val summary: String? = null,
    val tags: String? = null,
    val entities: String? = null,
    val itemsToBuy: String? = null,
    val status: String = QueueStatus.COMPLETED.name,
    val dateAdded: Long = 0L,
    val photoTitle: String? = null,
    val originalFileName: String? = null,
    val isUserCategorized: Boolean = false
)

data class QueueStateUpdate(
    val photoId: String,
    val status: String,
    val scrubbedText: String? = null,
    val detectedIntent: String? = null,
    val eventTitle: String? = null,
    val eventDateMillis: Long? = null,
    val targetCategoryId: String? = null,
    val eventDescription: String? = null,
    val summary: String? = null,
    val tags: String? = null,
    val entities: String? = null,
    val itemsToBuy: String? = null,
    val needsCloudUpgrade: Boolean = false,
    val modelName: String? = null,
    val modelVersion: String? = null,
    val forceCategoryUpdate: Boolean = false
)

internal fun android.database.Cursor.toProcessedScreenshotRecord(): ProcessedScreenshotRecord {
    fun optStr(colName: String, fallbackIdx: Int): String? {
        val idx = getColumnIndex(colName).let { if (it >= 0) it else fallbackIdx }
        return if (idx in 0 until columnCount && !isNull(idx)) getString(idx) else null
    }
    fun optLong(colName: String, defaultVal: Long): Long {
        val idx = getColumnIndex(colName)
        return if (idx in 0 until columnCount && !isNull(idx)) getLong(idx) else defaultVal
    }
    fun optBool(colName: String): Boolean {
        val idx = getColumnIndex(colName)
        return idx in 0 until columnCount && !isNull(idx) && getInt(idx) == 1
    }
    return ProcessedScreenshotRecord(
        photoId = getString(0),
        filePath = getString(1),
        scrubbedText = getString(2),
        detectedIntent = getString(3),
        eventTitle = getString(4),
        eventDateMillis = if (isNull(5)) null else getLong(5),
        targetCategoryId = getString(6),
        eventDescription = optStr("event_description", 7),
        summary = optStr("summary", 8),
        tags = optStr("tags", 9),
        entities = optStr("entities", 10),
        itemsToBuy = optStr("items_to_buy", 11),
        status = optStr("status", 12) ?: QueueStatus.COMPLETED.name,
        dateAdded = optLong("date_added", 0L),
        photoTitle = optStr("photo_title", -1),
        originalFileName = optStr("original_file_name", -1),
        isUserCategorized = optBool("is_user_categorized")
    )
}

class AppDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION),
    ScreenshotQueueDao, FtsSearchDao, VectorDao, ActionCardDao {

    companion object {
        const val DATABASE_NAME = "smart_photo_manager.db"

        // DB Version History:
        // 1-5: Initial schema (TABLE_QUEUE, TABLE_FTS)
        // 6: Added TABLE_VECTORS (128D on-device embeddings)
        // 7: Added TABLE_NEURAL_VECTORS (768D Gemini embeddings)
        // 8: Added TABLE_ACTION_CARDS
        // 9: Added semantic_tags column to TABLE_QUEUE
        // 10: Enforced idempotent Action Cards table creation & schema migration
        // 11: Added is_user_categorized column to TABLE_QUEUE to protect user-assigned categories
        const val DATABASE_VERSION = 11

        private const val TABLE_QUEUE = ScreenshotQueueDaoImpl.TABLE_QUEUE
        private const val TABLE_FTS = FtsSearchDaoImpl.TABLE_FTS
        private const val TABLE_VECTORS = VectorDaoImpl.TABLE_VECTORS
        private const val TABLE_NEURAL_VECTORS = VectorDaoImpl.TABLE_NEURAL_VECTORS
        private const val TABLE_ACTION_CARDS = ActionCardDaoImpl.TABLE_ACTION_CARDS

        @Volatile
        private var instance: AppDatabaseHelper? = null

        fun getInstance(context: Context): AppDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: AppDatabaseHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    private fun logDebugError(message: String, error: Throwable) {
        AppLogger.e("AppDatabaseHelper", message, error)
    }

    private val ftsDaoImpl by lazy { FtsSearchDaoImpl(this, ::logDebugError) }
    private val vectorDaoImpl by lazy { VectorDaoImpl(this, ::logDebugError) }
    private val actionCardDaoImpl by lazy { ActionCardDaoImpl(this, ::logDebugError) }
    private val queueDaoImpl by lazy {
        ScreenshotQueueDaoImpl(this, ftsDaoImpl, vectorDaoImpl, actionCardDaoImpl, ::logDebugError)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        try {
            db.enableWriteAheadLogging()
        } catch (e: Exception) {
            AppLogger.w("AppDatabaseHelper", "Failed to enable WAL", e)
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        createActionCardsTable(db)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_QUEUE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                photo_id TEXT UNIQUE NOT NULL,
                file_path TEXT NOT NULL,
                date_added INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'PENDING',
                scrubbed_text TEXT,
                detected_intent TEXT,
                event_title TEXT,
                event_date_millis INTEGER,
                target_category_id TEXT,
                event_description TEXT,
                summary TEXT,
                tags TEXT,
                entities TEXT,
                items_to_buy TEXT,
                needs_cloud_upgrade INTEGER DEFAULT 0,
                model_name TEXT,
                model_version TEXT,
                is_live_capture INTEGER NOT NULL DEFAULT 0,
                photo_title TEXT,
                original_file_name TEXT,
                is_user_categorized INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
            """.trimIndent()
        )
        createFtsTable(db)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_VECTORS (
                photo_id TEXT PRIMARY KEY,
                vector BLOB NOT NULL,
                intent TEXT,
                dimension INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_NEURAL_VECTORS (
                photo_id TEXT PRIMARY KEY,
                vector BLOB NOT NULL,
                dimension INTEGER NOT NULL,
                model_name TEXT,
                model_version TEXT,
                updated_at INTEGER NOT NULL
            );
            """.trimIndent()
        )
        createActionCardsTable(db)
    }

    private fun createActionCardsTable(db: SQLiteDatabase) {
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_ACTION_CARDS (
                    id TEXT PRIMARY KEY,
                    photo_id TEXT NOT NULL,
                    type TEXT NOT NULL,
                    title TEXT NOT NULL,
                    subtitle TEXT,
                    payload_json TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY(photo_id) REFERENCES $TABLE_QUEUE(photo_id) ON DELETE CASCADE
                );
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_action_cards_photo_id ON $TABLE_ACTION_CARDS(photo_id);")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_action_cards_type ON $TABLE_ACTION_CARDS(type);")
        } catch (_: Exception) {
        }
    }

    private fun createFtsTable(db: SQLiteDatabase) {
        try {
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE_FTS USING fts5(
                    photo_id UNINDEXED,
                    title,
                    tags,
                    entities,
                    summary,
                    scrubbed_text,
                    intent,
                    category_names
                );
                """.trimIndent()
            )
        } catch (_: Exception) {
            try {
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE_FTS USING fts4(
                        photo_id,
                        title,
                        tags,
                        entities,
                        summary,
                        scrubbed_text,
                        intent,
                        category_names
                    );
                    """.trimIndent()
                )
            } catch (_: Exception) {
            }
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE $TABLE_QUEUE ADD COLUMN scrubbed_text TEXT;")
                db.execSQL("ALTER TABLE $TABLE_QUEUE ADD COLUMN detected_intent TEXT;")
                db.execSQL("ALTER TABLE $TABLE_QUEUE ADD COLUMN event_title TEXT;")
                db.execSQL("ALTER TABLE $TABLE_QUEUE ADD COLUMN event_date_millis INTEGER;")
                db.execSQL("ALTER TABLE $TABLE_QUEUE ADD COLUMN target_category_id TEXT;")
            } catch (_: Exception) {
            }
        }
        if (oldVersion < 3) {
            try {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS $TABLE_VECTORS (
                        photo_id TEXT PRIMARY KEY,
                        vector BLOB NOT NULL,
                        intent TEXT,
                        dimension INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    );
                    """.trimIndent()
                )
            } catch (_: Exception) {
            }
        }
        if (oldVersion < 4) {
            try {
                db.execSQL("ALTER TABLE $TABLE_QUEUE ADD COLUMN event_description TEXT;")
            } catch (_: Exception) {
            }
        }
        if (oldVersion < 5) {
            try {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS $TABLE_NEURAL_VECTORS (
                        photo_id TEXT PRIMARY KEY,
                        vector BLOB NOT NULL,
                        dimension INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    );
                    """.trimIndent()
                )
            } catch (_: Exception) {
            }
        }
        if (oldVersion < 6) {
            try {
                db.execSQL("ALTER TABLE $TABLE_QUEUE ADD COLUMN summary TEXT;")
                db.execSQL("ALTER TABLE $TABLE_QUEUE ADD COLUMN tags TEXT;")
                db.execSQL("ALTER TABLE $TABLE_QUEUE ADD COLUMN entities TEXT;")
                db.execSQL("ALTER TABLE $TABLE_QUEUE ADD COLUMN items_to_buy TEXT;")
                db.execSQL("ALTER TABLE $TABLE_QUEUE ADD COLUMN needs_cloud_upgrade INTEGER DEFAULT 0;")
                db.execSQL("ALTER TABLE $TABLE_QUEUE ADD COLUMN model_name TEXT;")
                db.execSQL("ALTER TABLE $TABLE_QUEUE ADD COLUMN model_version TEXT;")
                db.execSQL("ALTER TABLE $TABLE_QUEUE ADD COLUMN updated_at INTEGER DEFAULT 0;")
            } catch (_: Exception) {
            }
            try {
                db.execSQL("ALTER TABLE $TABLE_NEURAL_VECTORS ADD COLUMN model_name TEXT;")
                db.execSQL("ALTER TABLE $TABLE_NEURAL_VECTORS ADD COLUMN model_version TEXT;")
            } catch (_: Exception) {
            }
            try {
                db.execSQL("DROP TABLE IF EXISTS $TABLE_FTS;")
                createFtsTable(db)
            } catch (_: Exception) {
            }
        }
        if (oldVersion < 7) {
            try {
                db.execSQL("ALTER TABLE $TABLE_QUEUE ADD COLUMN is_live_capture INTEGER NOT NULL DEFAULT 0;")
            } catch (_: Exception) {
            }
        }
        if (oldVersion < 8) {
            try {
                db.execSQL("ALTER TABLE $TABLE_QUEUE ADD COLUMN photo_title TEXT;")
                db.execSQL("ALTER TABLE $TABLE_QUEUE ADD COLUMN original_file_name TEXT;")
            } catch (_: Exception) {
            }
        }
        if (oldVersion < 9) {
            try {
                createActionCardsTable(db)
            } catch (_: Exception) {
            }
        }
        if (oldVersion < 10) {
            try {
                createActionCardsTable(db)
            } catch (_: Exception) {
            }
        }
        if (oldVersion < 11) {
            try {
                db.execSQL("ALTER TABLE $TABLE_QUEUE ADD COLUMN is_user_categorized INTEGER NOT NULL DEFAULT 0;")
            } catch (_: Exception) {
            }
        }
    }

    // --- Delegated DAO Operations ---

    override fun enqueueScreenshot(
        photoId: String,
        filePath: String,
        dateAdded: Long,
        isLiveCapture: Boolean,
        scrubbedText: String?,
        photoTitle: String?,
        originalFileName: String?
    ): Boolean = queueDaoImpl.enqueueScreenshot(photoId, filePath, dateAdded, isLiveCapture, scrubbedText, photoTitle, originalFileName)

    override fun syncVaultedPathToQueue(
        photoId: String,
        newFilePath: String,
        dateAdded: Long,
        photoTitle: String?,
        originalFileName: String?
    ): Boolean = queueDaoImpl.syncVaultedPathToQueue(photoId, newFilePath, dateAdded, photoTitle, originalFileName)

    override fun enqueueScreenshotsBatch(items: List<Triple<String, String, Long>>): Int =
        queueDaoImpl.enqueueScreenshotsBatch(items)

    override fun claimNextPendingQueueItem(): ScreenshotQueueItem? =
        queueDaoImpl.claimNextPendingQueueItem()

    override fun claimNextPendingQueueItems(limit: Int): List<ScreenshotQueueItem> =
        queueDaoImpl.claimNextPendingQueueItems(limit)

    override fun updateQueueItemState(update: QueueStateUpdate) =
        queueDaoImpl.updateQueueItemState(update)

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
    ) = queueDaoImpl.updateQueueItemState(
        photoId, status, scrubbedText, detectedIntent, eventTitle, eventDateMillis,
        targetCategoryId, eventDescription, summary, tags, entities, itemsToBuy,
        needsCloudUpgrade, modelName, modelVersion
    )

    override fun getPendingQueue(): List<ScreenshotQueueItem> = queueDaoImpl.getPendingQueue()

    override fun getQueueItemsNeedingCloudUpgrade(): List<ScreenshotQueueItem> =
        queueDaoImpl.getQueueItemsNeedingCloudUpgrade()

    override fun markQueueItemProcessed(photoId: String, status: String) =
        queueDaoImpl.markQueueItemProcessed(photoId, status)

    override fun markQueueItemProcessed(photoId: String, status: QueueStatus) =
        queueDaoImpl.markQueueItemProcessed(photoId, status)

    override fun isScreenshotProcessedOrNotified(photoId: String): Boolean =
        queueDaoImpl.isScreenshotProcessedOrNotified(photoId)

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
        originalFileName: String?,
        forceCategoryUpdate: Boolean
    ) = queueDaoImpl.recordProcessedScreenshot(
        photoId, filePath, dateAdded, scrubbedText, detectedIntent, eventTitle, eventDateMillis,
        targetCategoryId, status, eventDescription, summary, tags, entities, itemsToBuy,
        photoTitle, originalFileName, forceCategoryUpdate
    )

    override fun updatePhotosCategoryInQueue(
        photoIds: Collection<String>,
        targetCategoryId: String?,
        lockCategory: Boolean
    ) = queueDaoImpl.updatePhotosCategoryInQueue(photoIds, targetCategoryId, lockCategory)

    override fun clearCategoryInQueue(categoryId: String) =
        queueDaoImpl.clearCategoryInQueue(categoryId)

    override fun getProcessedScreenshot(photoId: String): ProcessedScreenshotRecord? =
        queueDaoImpl.getProcessedScreenshot(photoId)

    override fun resetHangingProcessingItems() = queueDaoImpl.resetHangingProcessingItems()

    override fun getPendingQueueCount(): Int = queueDaoImpl.getPendingQueueCount()

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
    ) = queueDaoImpl.migratePhotoRecords(
        oldPhotoId, newPhotoId, newFilePath, dateAdded, scrubbedText, detectedIntent,
        eventTitle, eventDateMillis, targetCategoryId, eventDescription, photoTitle, originalFileName
    )

    override fun getProcessedScreenshotsByIds(photoIds: List<String>): List<ProcessedScreenshotRecord> =
        queueDaoImpl.getProcessedScreenshotsByIds(photoIds)

    override fun getAllCompletedProcessedScreenshots(): List<ProcessedScreenshotRecord> =
        queueDaoImpl.getAllCompletedProcessedScreenshots()

    override fun getAllCompletedOrVaultedScreenshots(): List<ProcessedScreenshotRecord> =
        queueDaoImpl.getAllCompletedOrVaultedScreenshots()

    override fun indexPhotoFts(
        photoId: String,
        title: String,
        scrubbedText: String,
        intent: String,
        categoryNames: String,
        semanticTags: String,
        entities: String,
        summary: String
    ) = ftsDaoImpl.indexPhotoFts(photoId, title, scrubbedText, intent, categoryNames, semanticTags, entities, summary)

    override fun removePhotoFts(photoId: String) = ftsDaoImpl.removePhotoFts(photoId)

    override fun searchPhotosFtsRanked(query: String): List<Pair<String, Double>> =
        ftsDaoImpl.searchPhotosFtsRanked(query)

    override fun searchPhotosFts(query: String, minScore: Double): List<String> =
        ftsDaoImpl.searchPhotosFts(query, minScore)

    override fun savePhotoVector(photoId: String, vector: FloatArray, intent: String): Boolean =
        vectorDaoImpl.savePhotoVector(photoId, vector, intent)

    override fun getAllPhotoVectors(): Map<String, FloatArray> = vectorDaoImpl.getAllPhotoVectors()

    override fun deletePhotoVector(photoId: String) = vectorDaoImpl.deletePhotoVector(photoId)

    override fun savePhotoNeuralVector(photoId: String, vector: FloatArray, modelName: String, modelVersion: String): Boolean =
        vectorDaoImpl.savePhotoNeuralVector(photoId, vector, modelName, modelVersion)

    override fun savePhotoNeuralVectorsBatch(vectors: Map<String, FloatArray>, modelName: String, modelVersion: String): Boolean =
        vectorDaoImpl.savePhotoNeuralVectorsBatch(vectors, modelName, modelVersion)

    override fun getAllPhotoNeuralVectors(): Map<String, FloatArray> = vectorDaoImpl.getAllPhotoNeuralVectors()

    override fun deletePhotoNeuralVector(photoId: String) = vectorDaoImpl.deletePhotoNeuralVector(photoId)

    override fun getPhotosNeedingNeuralVectorBackfill(targetModelVersion: String): List<String> =
        vectorDaoImpl.getPhotosNeedingNeuralVectorBackfill(targetModelVersion)

    override fun replaceActionCardsForPhoto(photoId: String, cards: List<ActionCard>) =
        actionCardDaoImpl.replaceActionCardsForPhoto(photoId, cards)

    override fun saveActionCards(cards: List<ActionCard>) = actionCardDaoImpl.saveActionCards(cards)

    override fun getActionCardsForPhoto(photoId: String): List<ActionCard> =
        actionCardDaoImpl.getActionCardsForPhoto(photoId)

    override fun getPhotoIdsByActionCardType(type: ActionCardType): Set<String> =
        actionCardDaoImpl.getPhotoIdsByActionCardType(type)

    override fun getActionCardsPhotoCountByType(): Map<ActionCardType, Int> =
        actionCardDaoImpl.getActionCardsPhotoCountByType()

    override fun deleteActionCardsForPhoto(photoId: String): Boolean =
        actionCardDaoImpl.deleteActionCardsForPhoto(photoId)

    override fun cleanOrphanedActionCards(validPhotoIds: Set<String>?) =
        actionCardDaoImpl.cleanOrphanedActionCards(validPhotoIds)

    override fun purgePhotoData(photoId: String) = queueDaoImpl.purgePhotoData(photoId)
}
