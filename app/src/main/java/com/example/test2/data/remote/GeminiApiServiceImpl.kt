package com.example.test2.data.remote

import android.content.Context
import com.example.test2.ai.BatchPhotoItem
import com.example.test2.ai.CloudAiBackendClient
import com.example.test2.ai.ParsedScreenshotData
import com.example.test2.shared.models.Category

/**
 * Default Android implementation of [GeminiApiService] delegating to [CloudAiBackendClient].
 */
class GeminiApiServiceImpl(private val context: Context) : GeminiApiService {

    private val appContext = context.applicationContext

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
        return CloudAiBackendClient.getEmbedding(
            text = text,
            apiKey = apiKey,
            modelName = modelName,
            taskType = taskType
        )
    }

    override suspend fun getBatchEmbeddings(
        texts: List<String>,
        apiKey: String,
        modelName: String,
        taskType: String
    ): List<FloatArray?> {
        return CloudAiBackendClient.getBatchEmbeddings(
            texts = texts,
            apiKey = apiKey,
            modelName = modelName,
            taskType = taskType
        )
    }
}
