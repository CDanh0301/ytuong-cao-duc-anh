package com.example.test2.ai

import android.content.Context
import com.example.test2.data.local.prefs.CloudAiPrefsDataSource

data class CloudAiConfig(
    val apiKey: String,
    val modelName: String,
    val customEndpoint: String,
    val isEnabled: Boolean
)

object CloudAiConfigManager {

    /**
     * =========================================================================
     * 🔑 CẤU HÌNH HARDCODE CHO CLOUD AI (GEMINI)
     * Điền trực tiếp Gemini API Key và Tên Model bạn muốn sử dụng vào 2 hằng số dưới đây:
     * =========================================================================
     */
    const val HARDCODED_API_KEY = "" // <-- Điền API key của bạn vào đây (ví dụ: "AIzaSy...")
    const val HARDCODED_MODEL_NAME = "gemini-3.5-flash-lite" // <-- Model Gemini muốn dùng (ví dụ: "gemini-3.5-flash-lite", "gemini-3.6-flash", v.v.)

    private const val PREFS_NAME = "cloud_ai_settings"
    private const val KEY_API_KEY = "gemini_api_key"
    private const val KEY_MODEL_NAME = "gemini_model_name"
    private const val KEY_CUSTOM_ENDPOINT = "custom_endpoint"
    private const val KEY_ENABLED = "cloud_ai_enabled"

    const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
    const val DEFAULT_EMBEDDING_MODEL = "gemini-embedding-2"
    val AVAILABLE_MODELS = listOf("gemini-3.5-flash-lite", "gemini-3.6-flash")

    fun getConfig(context: Context): CloudAiConfig = CloudAiPrefsDataSource(context).getConfig()

    fun getApiKey(context: Context? = null): String {
        if (HARDCODED_API_KEY.isNotBlank()) return HARDCODED_API_KEY.trim()
        if (context == null) return ""
        return CloudAiPrefsDataSource(context).getApiKey()
    }

    fun saveConfig(
        context: Context,
        apiKey: String,
        modelName: String = DEFAULT_MODEL,
        customEndpoint: String = "",
        isEnabled: Boolean = true
    ) {
        CloudAiPrefsDataSource(context).saveConfig(apiKey, modelName, customEndpoint, isEnabled)
    }

    fun isConfigured(context: Context): Boolean = CloudAiPrefsDataSource(context).isConfigured()
}
