package com.example.test2.ai

import android.content.Context
import android.content.SharedPreferences

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

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getConfig(context: Context): CloudAiConfig {
        val prefs = getPrefs(context)
        val apiKey = if (HARDCODED_API_KEY.isNotBlank()) HARDCODED_API_KEY.trim() else (prefs.getString(KEY_API_KEY, "") ?: "")
        val rawModel = if (HARDCODED_MODEL_NAME.isNotBlank()) HARDCODED_MODEL_NAME.trim() else (prefs.getString(KEY_MODEL_NAME, DEFAULT_MODEL) ?: DEFAULT_MODEL)
        var modelName = rawModel.ifBlank { DEFAULT_MODEL }
        if (modelName.contains("1.5") || modelName.contains("2.0") || modelName.contains("2.5")) {
            modelName = DEFAULT_MODEL
        }
        val customEndpoint = prefs.getString(KEY_CUSTOM_ENDPOINT, "") ?: ""
        val isEnabled = if (HARDCODED_API_KEY.isNotBlank()) true else prefs.getBoolean(KEY_ENABLED, apiKey.isNotBlank())
        return CloudAiConfig(
            apiKey = apiKey,
            modelName = modelName,
            customEndpoint = customEndpoint,
            isEnabled = isEnabled
        )
    }

    fun getApiKey(context: Context? = null): String {
        if (HARDCODED_API_KEY.isNotBlank()) return HARDCODED_API_KEY.trim()
        if (context == null) return ""
        return getConfig(context).apiKey
    }

    fun saveConfig(
        context: Context,
        apiKey: String,
        modelName: String = DEFAULT_MODEL,
        customEndpoint: String = "",
        isEnabled: Boolean = true
    ) {
        getPrefs(context).edit().apply {
            putString(KEY_API_KEY, apiKey.trim())
            putString(KEY_MODEL_NAME, modelName.trim())
            putString(KEY_CUSTOM_ENDPOINT, customEndpoint.trim())
            putBoolean(KEY_ENABLED, isEnabled)
            apply()
        }
    }

    fun isConfigured(context: Context): Boolean {
        if (HARDCODED_API_KEY.isNotBlank()) return true
        val config = getConfig(context)
        return config.isEnabled && (config.apiKey.isNotBlank() || config.customEndpoint.isNotBlank())
    }
}
