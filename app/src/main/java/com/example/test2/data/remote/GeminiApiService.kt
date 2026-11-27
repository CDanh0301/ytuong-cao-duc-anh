package com.example.test2.data.remote

import com.example.test2.data.local.prefs.CloudAiDefaults
import com.example.test2.shared.models.Category

/**
 * Remote service interface abstracting Gemini Generative Language and Embedding APIs
 * via [CloudAiBackendClient] and [com.example.test2.ai.CloudPrivacyGateway].
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
        apiKey: String = "",
        modelName: String = CloudAiDefaults.DEFAULT_EMBEDDING_MODEL,
        taskType: String = "RETRIEVAL_DOCUMENT"
    ): FloatArray?

    suspend fun getBatchEmbeddings(
        texts: List<String>,
        apiKey: String = "",
        modelName: String = CloudAiDefaults.DEFAULT_EMBEDDING_MODEL,
        taskType: String = "RETRIEVAL_DOCUMENT"
    ): List<FloatArray?>

    suspend fun verifyCustomCategoryCandidates(
        category: Category,
        candidates: List<CategoryCandidateSnippet>,
        apiKey: String = "",
        modelName: String = CloudAiDefaults.DEFAULT_MODEL
    ): List<String>
}
