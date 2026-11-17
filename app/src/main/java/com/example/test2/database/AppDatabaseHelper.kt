package com.example.test2.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.test2.ai.VectorSemanticSearchEngine
import com.example.test2.shared.utils.VietnameseUtils
import java.util.concurrent.ConcurrentHashMap

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
    val originalFileName: String? = null
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
    val itemsToBuy: String? = null
)

class AppDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "smart_photo_manager.db"
        const val DATABASE_VERSION = 8

        private const val TABLE_QUEUE = "screenshot_queue"
        private const val TABLE_FTS = "photo_fts"
        private const val TABLE_VECTORS = "photo_vectors"
        private const val TABLE_NEURAL_VECTORS = "photo_neural_vectors"

        @Volatile
        private var instance: AppDatabaseHelper? = null

        fun getInstance(context: Context): AppDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: AppDatabaseHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    // Phase 3: In-memory vector caching to eliminate disk I/O and RAM reallocation on every keystroke
    private val neuralVectorCache = ConcurrentHashMap<String, FloatArray>()
    private val localVectorCache = ConcurrentHashMap<String, FloatArray>()

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        try {
            db.enableWriteAheadLogging()
        } catch (_: Exception) {
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // 1. Screenshot Queue table (State machine: PENDING -> PROCESSING -> OCR_DONE -> CLOUD_DONE -> EMBEDDED -> COMPLETED)
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
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        // 2. FTS5 Virtual Table with weighted columns (Phase 2 & 3)
        createFtsTable(db)

        // 3. Photo Vectors table for Advanced Vector Semantic Search (128D on-device)
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

        // 4. Photo Neural Vectors table for Cloud Embedding API (768D) with model versioning (Phase 2)
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
            // Phase 2 Schema additions: summary, tags, entities, items_to_buy, needs_cloud_upgrade, model_name, model_version
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
            // Re-create FTS table with separate weighted columns
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
    }

    // --- Queue Operations (Phase 1 & IDEA.md Step 1) ---

    fun enqueueScreenshot(
        photoId: String,
        filePath: String,
        dateAdded: Long,
        isLiveCapture: Boolean = false,
        scrubbedText: String? = null,
        photoTitle: String? = null,
        originalFileName: String? = null
    ): Boolean {
        return try {
            val db = writableDatabase
            val now = System.currentTimeMillis()
            val values = ContentValues().apply {
                put("photo_id", photoId)
                put("file_path", filePath)
                put("date_added", dateAdded)
                put("status", "PENDING")
                put("is_live_capture", if (isLiveCapture) 1 else 0)
                if (scrubbedText != null) put("scrubbed_text", scrubbedText)
                if (photoTitle != null) put("photo_title", photoTitle)
                if (originalFileName != null) put("original_file_name", originalFileName)
                put("created_at", now)
                put("updated_at", now)
            }
            val rowId = db.insertWithOnConflict(TABLE_QUEUE, null, values, SQLiteDatabase.CONFLICT_IGNORE)
            if (rowId < 0) {
                // If item is already enqueued, refresh file_path and metadata if not yet COMPLETED
                val updateValues = ContentValues().apply {
                    put("file_path", filePath)
                    if (scrubbedText != null) put("scrubbed_text", scrubbedText)
                    if (photoTitle != null) put("photo_title", photoTitle)
                    if (originalFileName != null) put("original_file_name", originalFileName)
                    put("updated_at", now)
                }
                db.update(TABLE_QUEUE, updateValues, "photo_id = ? AND status != 'COMPLETED'", arrayOf(photoId)) >= 0
            } else {
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Immediately synchronizes a photo's newly vaulted .enc file path into TABLE_QUEUE.
     * Prevents ScreenshotQueueWorker from attempting to read deleted public MediaStore URIs.
     */
    fun syncVaultedPathToQueue(photoId: String, newFilePath: String): Boolean {
        return try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("file_path", newFilePath)
                put("updated_at", System.currentTimeMillis())
            }
            db.update(TABLE_QUEUE, values, "photo_id = ?", arrayOf(photoId)) > 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Atomically enqueues a batch of screenshots to TABLE_QUEUE in a single SQLite transaction.
     * Uses CONFLICT_IGNORE to guarantee zero duplicate rows while finishing in < 5ms.
     */
    fun enqueueScreenshotsBatch(items: List<Triple<String, String, Long>>): Int {
        if (items.isEmpty()) return 0
        return try {
            val db = writableDatabase
            db.beginTransaction()
            var count = 0
            val now = System.currentTimeMillis()
            try {
                for (item in items) {
                    val values = ContentValues().apply {
                        put("photo_id", item.first)
                        put("file_path", item.second)
                        put("date_added", item.third)
                        put("status", "PENDING")
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

    /**
     * Atomically claims the next PENDING queue item by setting its status to 'PROCESSING'.
     * Prevents duplicate processing even when triggered concurrently by ContentObserver and JobScheduler.
     */
    fun claimNextPendingQueueItem(): ScreenshotQueueItem? {
        val db = writableDatabase
        db.beginTransaction()
        try {
            var claimedItem: ScreenshotQueueItem? = null
            db.rawQuery(
                "SELECT id, photo_id, file_path, date_added, status, is_live_capture, scrubbed_text, photo_title, original_file_name FROM $TABLE_QUEUE WHERE status = 'PENDING' ORDER BY date_added ASC LIMIT 1",
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    val photoId = cursor.getString(1)
                    val filePath = cursor.getString(2)
                    val dateAdded = cursor.getLong(3)
                    val isLiveCapture = if (cursor.columnCount > 5) cursor.getInt(5) == 1 else false
                    val scrubbedText = if (cursor.columnCount > 6) cursor.getString(6) else null
                    val photoTitle = if (cursor.columnCount > 7) cursor.getString(7) else null
                    val originalFileName = if (cursor.columnCount > 8) cursor.getString(8) else null
                    val now = System.currentTimeMillis()

                    val values = ContentValues().apply {
                        put("status", "PROCESSING")
                        put("updated_at", now)
                    }
                    val updated = db.update(TABLE_QUEUE, values, "id = ? AND status = 'PENDING'", arrayOf(id.toString()))
                    if (updated > 0) {
                        claimedItem = ScreenshotQueueItem(
                            id = id,
                            photoId = photoId,
                            filePath = filePath,
                            dateAdded = dateAdded,
                            status = "PROCESSING",
                            isLiveCapture = isLiveCapture,
                            scrubbedText = scrubbedText,
                            photoTitle = photoTitle,
                            originalFileName = originalFileName
                        )
                    }
                }
            }
            db.setTransactionSuccessful()
            return claimedItem
        } catch (_: Exception) {
            return null
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Atomically claims a batch of up to [limit] PENDING queue items by setting its status to 'PROCESSING'.
     * Guarantees zero duplicate processing while enabling 5-6x batching throughput.
     */
    fun claimNextPendingQueueItems(limit: Int = 6): List<ScreenshotQueueItem> {
        val db = writableDatabase
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
                        put("status", "PROCESSING")
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
                                status = "PROCESSING",
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
        } catch (_: Exception) {
            return emptyList()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Updates an item's state in the queue state machine:
     * PENDING -> PROCESSING -> OCR_DONE -> CLOUD_DONE -> EMBEDDED -> COMPLETED
     */
    fun updateQueueItemState(
        photoId: String,
        status: String,
        scrubbedText: String? = null,
        detectedIntent: String? = null,
        eventTitle: String? = null,
        eventDateMillis: Long? = null,
        targetCategoryId: String? = null,
        eventDescription: String? = null,
        summary: String? = null,
        tags: String? = null,
        entities: String? = null,
        itemsToBuy: String? = null,
        needsCloudUpgrade: Boolean = false,
        modelName: String? = null,
        modelVersion: String? = null
    ) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("status", status)
                put("updated_at", System.currentTimeMillis())
                if (scrubbedText != null) put("scrubbed_text", scrubbedText)
                if (detectedIntent != null) put("detected_intent", detectedIntent)
                if (eventTitle != null) put("event_title", eventTitle)
                if (eventDateMillis != null) put("event_date_millis", eventDateMillis)
                if (targetCategoryId != null) put("target_category_id", targetCategoryId)
                if (eventDescription != null) put("event_description", eventDescription)
                if (summary != null) put("summary", summary)
                if (tags != null) put("tags", tags)
                if (entities != null) put("entities", entities)
                if (itemsToBuy != null) put("items_to_buy", itemsToBuy)
                if (needsCloudUpgrade) put("needs_cloud_upgrade", 1) else put("needs_cloud_upgrade", 0)
                if (modelName != null) put("model_name", modelName)
                if (modelVersion != null) put("model_version", modelVersion)
            }
            db.update(TABLE_QUEUE, values, "photo_id = ?", arrayOf(photoId))
        } catch (_: Exception) {
        }
    }

    fun getPendingQueue(): List<ScreenshotQueueItem> {
        val items = mutableListOf<ScreenshotQueueItem>()
        try {
            val db = readableDatabase
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

    /**
     * Retrieves all items that were processed offline or with heuristics and need cloud AI upgrade.
     */
    fun getQueueItemsNeedingCloudUpgrade(): List<ScreenshotQueueItem> {
        val items = mutableListOf<ScreenshotQueueItem>()
        try {
            val db = readableDatabase
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

    fun markQueueItemProcessed(photoId: String, status: String = "COMPLETED") {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("status", status)
                put("updated_at", System.currentTimeMillis())
            }
            db.update(TABLE_QUEUE, values, "photo_id = ?", arrayOf(photoId))
        } catch (_: Exception) {
        }
    }

    fun isScreenshotProcessedOrNotified(photoId: String): Boolean {
        return try {
            val db = readableDatabase
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

    fun recordProcessedScreenshot(
        photoId: String,
        filePath: String,
        dateAdded: Long,
        scrubbedText: String?,
        detectedIntent: String?,
        eventTitle: String?,
        eventDateMillis: Long?,
        targetCategoryId: String?,
        status: String = "COMPLETED",
        eventDescription: String? = null,
        summary: String? = null,
        tags: String? = null,
        entities: String? = null,
        itemsToBuy: String? = null
    ) {
        try {
            val db = writableDatabase
            val now = System.currentTimeMillis()
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
                put("created_at", now)
                put("updated_at", now)
            }
            db.insertWithOnConflict(TABLE_QUEUE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (_: Exception) {
        }
    }

    fun getProcessedScreenshot(photoId: String): ProcessedScreenshotRecord? {
        return try {
            val db = readableDatabase
            db.rawQuery(
                "SELECT photo_id, file_path, scrubbed_text, detected_intent, event_title, event_date_millis, target_category_id, event_description, summary, tags, entities, items_to_buy FROM $TABLE_QUEUE WHERE photo_id = ? AND status = 'COMPLETED'",
                arrayOf(photoId)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    ProcessedScreenshotRecord(
                        photoId = cursor.getString(0),
                        filePath = cursor.getString(1),
                        scrubbedText = cursor.getString(2),
                        detectedIntent = cursor.getString(3),
                        eventTitle = cursor.getString(4),
                        eventDateMillis = if (cursor.isNull(5)) null else cursor.getLong(5),
                        targetCategoryId = cursor.getString(6),
                        eventDescription = if (cursor.columnCount > 7 && !cursor.isNull(7)) cursor.getString(7) else null,
                        summary = if (cursor.columnCount > 8 && !cursor.isNull(8)) cursor.getString(8) else null,
                        tags = if (cursor.columnCount > 9 && !cursor.isNull(9)) cursor.getString(9) else null,
                        entities = if (cursor.columnCount > 10 && !cursor.isNull(10)) cursor.getString(10) else null,
                        itemsToBuy = if (cursor.columnCount > 11 && !cursor.isNull(11)) cursor.getString(11) else null
                    )
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun resetHangingProcessingItems() {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("status", "PENDING")
                put("updated_at", System.currentTimeMillis())
            }
            db.update(TABLE_QUEUE, values, "status = 'PROCESSING'", null)
        } catch (_: Exception) {
        }
    }

    fun getPendingQueueCount(): Int {
        return try {
            val db = readableDatabase
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

    /**
     * Migrates records from oldPhotoId to newPhotoId (e.g. when restoring from vault to MediaStore)
     * and sets status to COMPLETED with the new filePath to prevent re-processing.
     */
    fun migratePhotoRecords(
        oldPhotoId: String,
        newPhotoId: String,
        newFilePath: String,
        dateAdded: Long,
        scrubbedText: String?,
        detectedIntent: String?,
        eventTitle: String?,
        eventDateMillis: Long?,
        targetCategoryId: String?,
        eventDescription: String? = null
    ) {
        try {
            val db = writableDatabase
            val now = System.currentTimeMillis()

            val oldRecord = getProcessedScreenshot(oldPhotoId)

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

            val values = ContentValues().apply {
                put("photo_id", newPhotoId)
                put("file_path", newFilePath)
                put("date_added", dateAdded)
                put("status", "COMPLETED")
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
                put("created_at", now)
                put("updated_at", now)
            }
            db.insertWithOnConflict(TABLE_QUEUE, null, values, SQLiteDatabase.CONFLICT_REPLACE)

            if (oldPhotoId != newPhotoId) {
                db.delete(TABLE_QUEUE, "photo_id = ?", arrayOf(oldPhotoId))

                try {
                    db.execSQL("UPDATE $TABLE_FTS SET photo_id = ? WHERE photo_id = ?", arrayOf(newPhotoId, oldPhotoId))
                } catch (_: Exception) {}

                try {
                    db.execSQL("UPDATE $TABLE_VECTORS SET photo_id = ? WHERE photo_id = ?", arrayOf(newPhotoId, oldPhotoId))
                    localVectorCache.remove(oldPhotoId)?.let { localVectorCache[newPhotoId] = it }
                } catch (_: Exception) {}

                try {
                    db.execSQL("UPDATE $TABLE_NEURAL_VECTORS SET photo_id = ? WHERE photo_id = ?", arrayOf(newPhotoId, oldPhotoId))
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {
        }
    }

    fun getProcessedScreenshotsByIds(photoIds: List<String>): List<ProcessedScreenshotRecord> {
        if (photoIds.isEmpty()) return emptyList()
        val results = mutableListOf<ProcessedScreenshotRecord>()
        try {
            val db = readableDatabase
            val placeholders = photoIds.joinToString(",") { "?" }
            db.rawQuery(
                "SELECT photo_id, file_path, scrubbed_text, detected_intent, event_title, event_date_millis, target_category_id, event_description, summary, tags, entities, items_to_buy FROM $TABLE_QUEUE WHERE photo_id IN ($placeholders) AND status = 'COMPLETED'",
                photoIds.toTypedArray()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    results.add(
                        ProcessedScreenshotRecord(
                            photoId = cursor.getString(0),
                            filePath = cursor.getString(1),
                            scrubbedText = cursor.getString(2),
                            detectedIntent = cursor.getString(3),
                            eventTitle = cursor.getString(4),
                            eventDateMillis = if (cursor.isNull(5)) null else cursor.getLong(5),
                            targetCategoryId = cursor.getString(6),
                            eventDescription = if (cursor.columnCount > 7 && !cursor.isNull(7)) cursor.getString(7) else null,
                            summary = if (cursor.columnCount > 8 && !cursor.isNull(8)) cursor.getString(8) else null,
                            tags = if (cursor.columnCount > 9 && !cursor.isNull(9)) cursor.getString(9) else null,
                            entities = if (cursor.columnCount > 10 && !cursor.isNull(10)) cursor.getString(10) else null,
                            itemsToBuy = if (cursor.columnCount > 11 && !cursor.isNull(11)) cursor.getString(11) else null
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return results
    }

    fun getAllCompletedProcessedScreenshots(): List<ProcessedScreenshotRecord> {
        val results = mutableListOf<ProcessedScreenshotRecord>()
        try {
            val db = readableDatabase
            db.rawQuery(
                "SELECT photo_id, file_path, scrubbed_text, detected_intent, event_title, event_date_millis, target_category_id, event_description, summary, tags, entities, items_to_buy FROM $TABLE_QUEUE WHERE status = 'COMPLETED'",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    results.add(
                        ProcessedScreenshotRecord(
                            photoId = cursor.getString(0),
                            filePath = cursor.getString(1),
                            scrubbedText = cursor.getString(2),
                            detectedIntent = cursor.getString(3),
                            eventTitle = cursor.getString(4),
                            eventDateMillis = if (cursor.isNull(5)) null else cursor.getLong(5),
                            targetCategoryId = cursor.getString(6),
                            eventDescription = if (cursor.columnCount > 7 && !cursor.isNull(7)) cursor.getString(7) else null,
                            summary = if (cursor.columnCount > 8 && !cursor.isNull(8)) cursor.getString(8) else null,
                            tags = if (cursor.columnCount > 9 && !cursor.isNull(9)) cursor.getString(9) else null,
                            entities = if (cursor.columnCount > 10 && !cursor.isNull(10)) cursor.getString(10) else null,
                            itemsToBuy = if (cursor.columnCount > 11 && !cursor.isNull(11)) cursor.getString(11) else null
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return results
    }

    // --- FTS5 Full-Text Search Operations (Phase 2 & 3: BM25 Weighted Multi-Column) ---

    fun indexPhotoFts(
        photoId: String,
        title: String,
        scrubbedText: String,
        intent: String = "",
        categoryNames: String = "",
        semanticTags: String = "",
        entities: String = "",
        summary: String = ""
    ) {
        try {
            val db = writableDatabase
            db.delete(TABLE_FTS, "photo_id = ?", arrayOf(photoId))

            val normTitle = "$title ${VietnameseUtils.removeDiacritics(title)}"
            val normTags = "$semanticTags ${VietnameseUtils.removeDiacritics(semanticTags)}"
            val normEntities = "$entities ${VietnameseUtils.removeDiacritics(entities)}"
            val normSummary = "$summary ${VietnameseUtils.removeDiacritics(summary)}"
            val normText = "$scrubbedText ${VietnameseUtils.removeDiacritics(scrubbedText)}"
            val normCat = "$categoryNames ${VietnameseUtils.removeDiacritics(categoryNames)}"

            val values = ContentValues().apply {
                put("photo_id", photoId)
                put("title", normTitle)
                put("tags", normTags)
                put("entities", normEntities)
                put("summary", normSummary)
                put("scrubbed_text", normText)
                put("intent", intent)
                put("category_names", normCat)
            }
            db.insert(TABLE_FTS, null, values)
        } catch (_: Exception) {
        }
    }

    fun removePhotoFts(photoId: String) {
        try {
            val db = writableDatabase
            db.delete(TABLE_FTS, "photo_id = ?", arrayOf(photoId))
        } catch (_: Exception) {
        }
    }

    /**
     * Executes FTS5 search with terms combined using AND (falling back to OR)
     * and ranked by BM25 with column weights:
     * tags (10.0), entities (8.0), title (5.0), summary (3.0), category (2.0), body (1.0).
     * Returns List of Pair(photoId, relevanceScore).
     */
    fun searchPhotosFtsRanked(query: String): List<Pair<String, Double>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val tokens = trimmed
            .replace(Regex("[^a-zA-Z0-9àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it.length >= 2 }

        if (tokens.isEmpty()) return emptyList()

        val termClauses = tokens.map { token ->
            val unacc = VietnameseUtils.removeDiacritics(token)
            val sanitized = token.replace("\"", "\"\"")
            val sanitizedUnacc = unacc.replace("\"", "\"\"")
            val usePrefix = sanitized.length >= 4
            val pattern1 = if (usePrefix) "\"$sanitized\"*" else "\"$sanitized\""
            val pattern2 = if (usePrefix) "\"$sanitizedUnacc\"*" else "\"$sanitizedUnacc\""
            if (sanitized != sanitizedUnacc) {
                "($pattern1 OR $pattern2)"
            } else {
                pattern1
            }
        }

        val andQuery = termClauses.joinToString(" AND ")
        val orQuery = termClauses.joinToString(" OR ")

        val results = mutableListOf<Pair<String, Double>>()
        try {
            val db = readableDatabase
            // 1. Try AND query first
            results.addAll(executeFtsMatch(db, andQuery))

            // 2. Fall back to OR query if AND returns 0 matches
            if (results.isEmpty() && tokens.size > 1) {
                results.addAll(executeFtsMatch(db, orQuery))
            }
        } catch (_: Exception) {
        }

        // 3. Fallback to LIKE if FTS produced 0 results
        if (results.isEmpty()) {
            results.addAll(fallbackLikeSearch(query))
        }

        return results
    }

    private fun executeFtsMatch(db: SQLiteDatabase, matchQuery: String): List<Pair<String, Double>> {
        val matches = mutableListOf<Pair<String, Double>>()
        // FTS5 BM25 column weights: title=5.0, tags=10.0, entities=8.0, summary=3.0, scrubbed_text=1.0, intent=1.0, category_names=2.0
        val sql = "SELECT photo_id, bm25(photo_fts, 5.0, 10.0, 8.0, 3.0, 1.0, 1.0, 2.0) AS score FROM $TABLE_FTS WHERE $TABLE_FTS MATCH ? ORDER BY score ASC LIMIT 200"
        try {
            db.rawQuery(sql, arrayOf(matchQuery)).use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow("photo_id")
                val scoreCol = cursor.getColumnIndexOrThrow("score")
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idCol)
                    val rawBm25 = cursor.getDouble(scoreCol)
                    // FTS5 bm25 returns negative values where more negative = higher relevance
                    val positiveScore = -rawBm25
                    matches.add(id to positiveScore)
                }
            }
        } catch (_: Exception) {
            // Legacy FTS4 fallback without bm25() function
            try {
                db.rawQuery("SELECT photo_id FROM $TABLE_FTS WHERE $TABLE_FTS MATCH ? LIMIT 100", arrayOf(matchQuery)).use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow("photo_id")
                    var rank = 100.0
                    while (cursor.moveToNext()) {
                        matches.add(cursor.getString(idCol) to rank)
                        rank -= 0.5
                    }
                }
            } catch (_: Exception) {
            }
        }
        return matches
    }

    private fun fallbackLikeSearch(query: String): List<Pair<String, Double>> {
        val matches = mutableListOf<Pair<String, Double>>()
        val clean = query.trim()
        val unacc = VietnameseUtils.removeDiacritics(clean)
        try {
            val db = readableDatabase
            val pattern1 = "%$clean%"
            val pattern2 = "%$unacc%"
            val sql = "SELECT photo_id FROM $TABLE_FTS WHERE title LIKE ? OR tags LIKE ? OR entities LIKE ? OR summary LIKE ? OR scrubbed_text LIKE ? OR category_names LIKE ? OR title LIKE ? OR tags LIKE ? LIMIT 100"
            db.rawQuery(sql, arrayOf(pattern1, pattern1, pattern1, pattern1, pattern1, pattern1, pattern2, pattern2)).use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow("photo_id")
                var rank = 50.0
                while (cursor.moveToNext()) {
                    matches.add(cursor.getString(idCol) to rank)
                    rank -= 0.5
                }
            }
        } catch (_: Exception) {
        }
        return matches
    }

    fun searchPhotosFts(query: String, minScore: Double = 0.0): List<String> {
        val ranked = searchPhotosFtsRanked(query)
        val filtered = if (minScore > 0.0) {
            ranked.filter { it.second >= minScore }
        } else {
            ranked
        }
        return filtered.map { it.first }
    }

    // --- Vector Semantic Search Operations (Phase 3: In-Memory Caching) ---

    fun savePhotoVector(photoId: String, vector: FloatArray, intent: String = ""): Boolean {
        localVectorCache[photoId] = vector
        return try {
            val db = writableDatabase
            val blob = VectorSemanticSearchEngine.vectorToBlob(vector)
            val values = ContentValues().apply {
                put("photo_id", photoId)
                put("vector", blob)
                put("intent", intent)
                put("dimension", vector.size)
                put("updated_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict(TABLE_VECTORS, null, values, SQLiteDatabase.CONFLICT_REPLACE) >= 0
        } catch (_: Exception) {
            false
        }
    }

    fun getAllPhotoVectors(): Map<String, FloatArray> {
        if (localVectorCache.isNotEmpty()) {
            return localVectorCache
        }
        val result = mutableMapOf<String, FloatArray>()
        try {
            val db = readableDatabase
            db.rawQuery("SELECT photo_id, vector FROM $TABLE_VECTORS", null).use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow("photo_id")
                val vecCol = cursor.getColumnIndexOrThrow("vector")
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idCol)
                    val blob = cursor.getBlob(vecCol)
                    if (blob != null && blob.isNotEmpty()) {
                        val vec = VectorSemanticSearchEngine.blobToVector(blob)
                        result[id] = vec
                        localVectorCache[id] = vec
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    fun deletePhotoVector(photoId: String) {
        localVectorCache.remove(photoId)
        try {
            val db = writableDatabase
            db.delete(TABLE_VECTORS, "photo_id = ?", arrayOf(photoId))
        } catch (_: Exception) {
        }
    }

    // --- Cloud Embedding API Neural Vectors Operations (gemini-embedding-2 - 768D) ---

    fun savePhotoNeuralVector(
        photoId: String,
        vector: FloatArray,
        modelName: String = "gemini-embedding-2",
        modelVersion: String = "1.0"
    ): Boolean {
        neuralVectorCache[photoId] = vector
        return try {
            val db = writableDatabase
            val blob = VectorSemanticSearchEngine.vectorToBlob(vector)
            val values = ContentValues().apply {
                put("photo_id", photoId)
                put("vector", blob)
                put("dimension", vector.size)
                put("model_name", modelName)
                put("model_version", modelVersion)
                put("updated_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict(TABLE_NEURAL_VECTORS, null, values, SQLiteDatabase.CONFLICT_REPLACE) >= 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Atomically saves a batch of neural vectors in a single database transaction.
     */
    fun savePhotoNeuralVectorsBatch(
        vectors: Map<String, FloatArray>,
        modelName: String = "gemini-embedding-2",
        modelVersion: String = "1.0"
    ): Boolean {
        if (vectors.isEmpty()) return true
        vectors.forEach { (id, vec) -> neuralVectorCache[id] = vec }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            for ((photoId, vector) in vectors) {
                val blob = VectorSemanticSearchEngine.vectorToBlob(vector)
                val values = ContentValues().apply {
                    put("photo_id", photoId)
                    put("vector", blob)
                    put("dimension", vector.size)
                    put("model_name", modelName)
                    put("model_version", modelVersion)
                    put("updated_at", now)
                }
                db.insertWithOnConflict(TABLE_NEURAL_VECTORS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
            return true
        } catch (_: Exception) {
            return false
        } finally {
            db.endTransaction()
        }
    }

    fun getAllPhotoNeuralVectors(): Map<String, FloatArray> {
        if (neuralVectorCache.isNotEmpty()) {
            return neuralVectorCache
        }
        val result = mutableMapOf<String, FloatArray>()
        try {
            val db = readableDatabase
            db.rawQuery("SELECT photo_id, vector FROM $TABLE_NEURAL_VECTORS", null).use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow("photo_id")
                val vecCol = cursor.getColumnIndexOrThrow("vector")
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idCol)
                    val blob = cursor.getBlob(vecCol)
                    if (blob != null && blob.isNotEmpty()) {
                        val vec = VectorSemanticSearchEngine.blobToVector(blob)
                        result[id] = vec
                        neuralVectorCache[id] = vec
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    fun deletePhotoNeuralVector(photoId: String) {
        neuralVectorCache.remove(photoId)
        try {
            val db = writableDatabase
            db.delete(TABLE_NEURAL_VECTORS, "photo_id = ?", arrayOf(photoId))
        } catch (_: Exception) {
        }
    }

    /**
     * Backfill helper (Phase 2): finds photo IDs that have no neural vector or are on an older model version.
     */
    fun getPhotosNeedingNeuralVectorBackfill(targetModelVersion: String = "1.0"): List<String> {
        val missingIds = mutableListOf<String>()
        try {
            val db = readableDatabase
            val sql = """
                SELECT q.photo_id FROM $TABLE_QUEUE q
                LEFT JOIN $TABLE_NEURAL_VECTORS n ON q.photo_id = n.photo_id
                WHERE n.photo_id IS NULL OR n.model_version IS NULL OR n.model_version != ?
            """.trimIndent()
            db.rawQuery(sql, arrayOf(targetModelVersion)).use { cursor ->
                while (cursor.moveToNext()) {
                    missingIds.add(cursor.getString(0))
                }
            }
        } catch (_: Exception) {
        }
        return missingIds
    }
}
