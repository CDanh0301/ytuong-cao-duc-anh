package com.example.test2.data.remote

import com.example.test2.ai.BatchPhotoItem
import com.example.test2.ai.CloudAiConfigManager
import com.example.test2.ai.ParsedScreenshotData
import com.example.test2.shared.models.Category

/**
 * Remote service interface abstracting Gemini Generative Language and Embedding APIs.
 */
interface GeminiApiService {
    suspend fun analyzeWithFallback(
        scrubbedText: String,
        photoTitle: String,
        categories: List<Category>,
        isPro: Boolean = false,
        hasFace: Boolean = false,
        visualLabels: List<String> = emptyList()
    ): ParsedScreenshotData

    suspend fun analyzeBatchWithGemini(
        items: List<BatchPhotoItem>,
        categories: List<Category>,
        isPro: Boolean = false
    ): Map<String, ParsedScreenshotData>

    suspend fun getEmbedding(
        text: String,
        apiKey: String = CloudAiConfigManager.HARDCODED_API_KEY,
        modelName: String = CloudAiConfigManager.DEFAULT_EMBEDDING_MODEL,
        taskType: String = "RETRIEVAL_DOCUMENT"
    ): FloatArray?

    suspend fun getBatchEmbeddings(
        texts: List<String>,
        apiKey: String = CloudAiConfigManager.HARDCODED_API_KEY,
        modelName: String = CloudAiConfigManager.DEFAULT_EMBEDDING_MODEL,
        taskType: String = "RETRIEVAL_DOCUMENT"
    ): List<FloatArray?>
}
