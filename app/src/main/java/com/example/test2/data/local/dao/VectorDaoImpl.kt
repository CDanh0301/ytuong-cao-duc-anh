package com.example.test2.data.local.dao

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.test2.ai.VectorSemanticSearchEngine
import java.util.concurrent.ConcurrentHashMap

/**
 * SQLite + in-memory cache implementation of [VectorDao].
 */
class VectorDaoImpl(
    private val dbProvider: SQLiteOpenHelper,
    private val logError: (String, Throwable) -> Unit
) : VectorDao {

    private val neuralVectorCache = ConcurrentHashMap<String, FloatArray>()
    private val localVectorCache = ConcurrentHashMap<String, FloatArray>()

    internal fun migrateCachedVectorKey(oldPhotoId: String, newPhotoId: String) {
        localVectorCache.remove(oldPhotoId)?.let { localVectorCache[newPhotoId] = it }
        neuralVectorCache.remove(oldPhotoId)?.let { neuralVectorCache[newPhotoId] = it }
    }

    override fun savePhotoVector(photoId: String, vector: FloatArray, intent: String): Boolean {
        localVectorCache[photoId] = vector
        return try {
            val db = dbProvider.writableDatabase
            val blob = VectorSemanticSearchEngine.vectorToBlob(vector)
            val values = ContentValues().apply {
                put("photo_id", photoId)
                put("vector", blob)
                put("intent", intent)
                put("dimension", vector.size)
                put("updated_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict(TABLE_VECTORS, null, values, SQLiteDatabase.CONFLICT_REPLACE) >= 0
        } catch (e: Exception) {
            logError("Failed to save photo vector for $photoId", e)
            false
        }
    }

    override fun getAllPhotoVectors(): Map<String, FloatArray> {
        if (localVectorCache.isNotEmpty()) {
            return localVectorCache
        }
        val result = mutableMapOf<String, FloatArray>()
        try {
            val db = dbProvider.readableDatabase
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
        } catch (e: Exception) {
            logError("Failed loading local vectors", e)
        }
        return result
    }

    override fun deletePhotoVector(photoId: String) {
        localVectorCache.remove(photoId)
        try {
            val db = dbProvider.writableDatabase
            db.delete(TABLE_VECTORS, "photo_id = ?", arrayOf(photoId))
        } catch (e: Exception) {
            logError("Failed deleting local vector for $photoId", e)
        }
    }

    override fun savePhotoNeuralVector(
        photoId: String,
        vector: FloatArray,
        modelName: String,
        modelVersion: String
    ): Boolean {
        neuralVectorCache[photoId] = vector
        return try {
            val db = dbProvider.writableDatabase
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
        } catch (e: Exception) {
            logError("Failed saving neural vector for $photoId", e)
            false
        }
    }

    override fun savePhotoNeuralVectorsBatch(
        vectors: Map<String, FloatArray>,
        modelName: String,
        modelVersion: String
    ): Boolean {
        if (vectors.isEmpty()) return true
        vectors.forEach { (id, vec) -> neuralVectorCache[id] = vec }
        val db = dbProvider.writableDatabase
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
        } catch (e: Exception) {
            logError("Failed to save photo neural vectors batch", e)
            return false
        } finally {
            db.endTransaction()
        }
    }

    override fun getAllPhotoNeuralVectors(): Map<String, FloatArray> {
        if (neuralVectorCache.isNotEmpty()) {
            return neuralVectorCache
        }
        val result = mutableMapOf<String, FloatArray>()
        try {
            val db = dbProvider.readableDatabase
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
        } catch (e: Exception) {
            logError("Failed loading neural vectors", e)
        }
        return result
    }

    override fun deletePhotoNeuralVector(photoId: String) {
        neuralVectorCache.remove(photoId)
        try {
            val db = dbProvider.writableDatabase
            db.delete(TABLE_NEURAL_VECTORS, "photo_id = ?", arrayOf(photoId))
        } catch (e: Exception) {
            logError("Failed deleting neural vector for $photoId", e)
        }
    }

    override fun getPhotosNeedingNeuralVectorBackfill(targetModelVersion: String): List<String> {
        val missingIds = mutableListOf<String>()
        try {
            val db = dbProvider.readableDatabase
            val sql = """
                SELECT q.photo_id FROM ${ScreenshotQueueDaoImpl.TABLE_QUEUE} q
                LEFT JOIN $TABLE_NEURAL_VECTORS n ON q.photo_id = n.photo_id
                WHERE n.photo_id IS NULL OR n.model_version IS NULL OR n.model_version != ?
            """.trimIndent()
            db.rawQuery(sql, arrayOf(targetModelVersion)).use { cursor ->
                while (cursor.moveToNext()) {
                    missingIds.add(cursor.getString(0))
                }
            }
        } catch (e: Exception) {
            logError("Failed querying neural backfill IDs", e)
        }
        return missingIds
    }

    companion object {
        const val TABLE_VECTORS = "photo_vectors"
        const val TABLE_NEURAL_VECTORS = "photo_neural_vectors"
    }
}
