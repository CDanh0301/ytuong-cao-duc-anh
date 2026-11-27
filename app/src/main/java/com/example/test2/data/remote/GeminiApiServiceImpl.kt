package com.example.test2.data.remote

import android.content.Context
import com.example.test2.ai.CloudAiBackendClient
import com.example.test2.data.local.prefs.CloudAiPrefsDataSource
import com.example.test2.shared.models.Category

/**
 * Default Android implementation of [GeminiApiService] delegating to [CloudAiBackendClient]
 * and [com.example.test2.ai.CloudPrivacyGateway].
 */
class GeminiApiServiceImpl(private val context: Context) : GeminiApiService {

    private val appContext = context.applicationContext
    private val prefsDataSource by lazy { CloudAiPrefsDataSource(appContext) }

    override suspend fun analyzeWithFallback(
        scrubbedText: String,
        photoTitle: String,
        categories: List<Category>,
        isPro: Boolean,
        hasFace: Boolean,
        visualLabels: List<String>
    ): ParsedScreenshotData {
        return CloudAiBackendClient.analyzeWithFallback(
            context = appContext,
            scrubbedText = scrubbedText,
            photoTitle = photoTitle,
            categories = categories,
            isPro = isPro,
            hasFace = hasFace,
            visualLabels = visualLabels
        )
    }

    override suspend fun analyzeBatchWithGemini(
        items: List<BatchPhotoItem>,
        categories: List<Category>,
        isPro: Boolean
    ): Map<String, ParsedScreenshotData> {
        return CloudAiBackendClient.analyzeBatchWithGemini(
            context = appContext,
            items = items,
            categories = categories,
            isPro = isPro
        )
    }

    override suspend fun getEmbedding(
        text: String,
        apiKey: String,
        modelName: String,
        taskType: String
    ): FloatArray? {
        val config = prefsDataSource.getConfig()
        val effectiveKey = apiKey.ifBlank { config.apiKey }
        return CloudAiBackendClient.getEmbedding(
            text = text,
            apiKey = effectiveKey,
            modelName = modelName,
            taskType = taskType,
            proxyBaseUrl = config.proxyBaseUrl
        )
    }

    override suspend fun getBatchEmbeddings(
        texts: List<String>,
        apiKey: String,
        modelName: String,
        taskType: String
    ): List<FloatArray?> {
        val config = prefsDataSource.getConfig()
        val effectiveKey = apiKey.ifBlank { config.apiKey }
        return CloudAiBackendClient.getBatchEmbeddings(
            texts = texts,
            apiKey = effectiveKey,
            modelName = modelName,
            taskType = taskType,
            proxyBaseUrl = config.proxyBaseUrl
        )
    }

    override suspend fun verifyCustomCategoryCandidates(
        category: Category,
        candidates: List<CategoryCandidateSnippet>,
        apiKey: String,
        modelName: String
    ): List<String> {
        val config = prefsDataSource.getConfig()
        val effectiveKey = apiKey.ifBlank { config.apiKey }
        return CloudAiBackendClient.verifyCustomCategoryCandidates(
            category = category,
            candidates = candidates,
            apiKey = effectiveKey,
            modelName = modelName,
            proxyBaseUrl = config.proxyBaseUrl
        )
    }
}
