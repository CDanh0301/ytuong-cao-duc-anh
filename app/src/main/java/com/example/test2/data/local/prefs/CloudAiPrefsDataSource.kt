package com.example.test2.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import com.example.test2.BuildConfig

data class CloudAiConfig(
    val apiKey: String,
    val modelName: String,
    val customEndpoint: String,
    val isEnabled: Boolean,
    val proxyBaseUrl: String = CloudAiDefaults.PROXY_BASE_URL
) {
    fun isCloudReady(): Boolean = isEnabled &&
        (proxyBaseUrl.isNotBlank() || customEndpoint.isNotBlank() || apiKey.isNotBlank())
}

object CloudAiDefaults {
    val PROXY_BASE_URL: String
        get() = BuildConfig.CLOUD_AI_PROXY_URL.trim()
    const val HARDCODED_MODEL_NAME = "gemini-3.5-flash-lite"
    const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
    const val DEFAULT_EMBEDDING_MODEL = "gemini-embedding-2"
    val AVAILABLE_MODELS = listOf("gemini-3.5-flash-lite", "gemini-3.6-flash")
}

/**
 * Local SharedPreferences data source for Cloud AI (Proxy / Gemini) configuration.
 * Never embeds hardcoded API keys in client source code.
 */
class CloudAiPrefsDataSource(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getConfig(): CloudAiConfig {
        val proxyBaseUrl = CloudAiDefaults.PROXY_BASE_URL
        val hardcodedModel = CloudAiDefaults.HARDCODED_MODEL_NAME
        val apiKey = prefs.getString(KEY_API_KEY, "") ?: ""
        val rawModel = if (hardcodedModel.isNotBlank()) {
            hardcodedModel.trim()
        } else {
            prefs.getString(KEY_MODEL_NAME, CloudAiDefaults.DEFAULT_MODEL) ?: CloudAiDefaults.DEFAULT_MODEL
        }
        var modelName = rawModel.ifBlank { CloudAiDefaults.DEFAULT_MODEL }
        if (modelName.contains("1.5") || modelName.contains("2.0") || modelName.contains("2.5")) {
            modelName = CloudAiDefaults.DEFAULT_MODEL
        }
        val customEndpoint = prefs.getString(KEY_CUSTOM_ENDPOINT, "") ?: ""
        val isEnabled = if (proxyBaseUrl.isNotBlank()) {
            true
        } else {
            prefs.getBoolean(KEY_ENABLED, apiKey.isNotBlank() || customEndpoint.isNotBlank())
        }
        return CloudAiConfig(
            apiKey = apiKey,
            modelName = modelName,
            customEndpoint = customEndpoint,
            isEnabled = isEnabled,
            proxyBaseUrl = proxyBaseUrl
        )
    }

    fun getApiKey(): String = getConfig().apiKey

    fun getProxyBaseUrl(): String = getConfig().proxyBaseUrl

    fun saveConfig(
        apiKey: String,
        modelName: String = CloudAiDefaults.DEFAULT_MODEL,
        customEndpoint: String = "",
        isEnabled: Boolean = true
    ) {
        prefs.edit().apply {
            putString(KEY_API_KEY, apiKey.trim())
            putString(KEY_MODEL_NAME, modelName.trim())
            putString(KEY_CUSTOM_ENDPOINT, customEndpoint.trim())
            putBoolean(KEY_ENABLED, isEnabled)
            apply()
        }
    }

    fun isConfigured(): Boolean = getConfig().isCloudReady()

    companion object {
        private const val PREFS_NAME = "cloud_ai_settings"
        private const val KEY_API_KEY = "gemini_api_key"
        private const val KEY_MODEL_NAME = "gemini_model_name"
        private const val KEY_CUSTOM_ENDPOINT = "custom_endpoint"
        private const val KEY_ENABLED = "cloud_ai_enabled"
    }
}
