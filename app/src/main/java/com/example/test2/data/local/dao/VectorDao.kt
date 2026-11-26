package com.example.test2.data.local.dao

/**
 * Data Access Object contract for 128D on-device and 768D cloud neural embedding vectors.
 */
interface VectorDao {
    fun savePhotoVector(photoId: String, vector: FloatArray, intent: String = ""): Boolean

    fun getAllPhotoVectors(): Map<String, FloatArray>

    fun deletePhotoVector(photoId: String)

    fun savePhotoNeuralVector(
        photoId: String,
        vector: FloatArray,
        modelName: String = "gemini-embedding-2",
        modelVersion: String = "1.0"
    ): Boolean

    fun savePhotoNeuralVectorsBatch(
        vectors: Map<String, FloatArray>,
        modelName: String = "gemini-embedding-2",
        modelVersion: String = "1.0"
    ): Boolean

    fun getAllPhotoNeuralVectors(): Map<String, FloatArray>

    fun deletePhotoNeuralVector(photoId: String)

    fun getPhotosNeedingNeuralVectorBackfill(targetModelVersion: String = "1.0"): List<String>
}
