package com.example.test2.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import com.example.test2.ai.CloudAiConfig
import com.example.test2.ai.CloudAiConfigManager

/**
 * Local SharedPreferences data source for Cloud AI (Gemini) configuration.
 */
class CloudAiPrefsDataSource(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getConfig(): CloudAiConfig {
        val hardcodedKey = CloudAiConfigManager.HARDCODED_API_KEY
        val hardcodedModel = CloudAiConfigManager.HARDCODED_MODEL_NAME
        val apiKey = if (hardcodedKey.isNotBlank()) hardcodedKey.trim() else (prefs.getString(KEY_API_KEY, "") ?: "")
        val rawModel = if (hardcodedModel.isNotBlank()) hardcodedModel.trim() else (prefs.getString(KEY_MODEL_NAME, CloudAiConfigManager.DEFAULT_MODEL) ?: CloudAiConfigManager.DEFAULT_MODEL)
        var modelName = rawModel.ifBlank { CloudAiConfigManager.DEFAULT_MODEL }
        if (modelName.contains("1.5") || modelName.contains("2.0") || modelName.contains("2.5")) {
            modelName = CloudAiConfigManager.DEFAULT_MODEL
        }
        val customEndpoint = prefs.getString(KEY_CUSTOM_ENDPOINT, "") ?: ""
        val isEnabled = if (hardcodedKey.isNotBlank()) true else prefs.getBoolean(KEY_ENABLED, apiKey.isNotBlank())
        return CloudAiConfig(
            apiKey = apiKey,
            modelName = modelName,
            customEndpoint = customEndpoint,
            isEnabled = isEnabled
        )
    }

    fun getApiKey(): String {
        if (CloudAiConfigManager.HARDCODED_API_KEY.isNotBlank()) {
            return CloudAiConfigManager.HARDCODED_API_KEY.trim()
        }
        return getConfig().apiKey
    }

    fun saveConfig(
        apiKey: String,
        modelName: String = CloudAiConfigManager.DEFAULT_MODEL,
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

    fun isConfigured(): Boolean {
        if (CloudAiConfigManager.HARDCODED_API_KEY.isNotBlank()) return true
        val config = getConfig()
        return config.isEnabled && (config.apiKey.isNotBlank() || config.customEndpoint.isNotBlank())
    }

    companion object {
        private const val PREFS_NAME = "cloud_ai_settings"
        private const val KEY_API_KEY = "gemini_api_key"
        private const val KEY_MODEL_NAME = "gemini_model_name"
        private const val KEY_CUSTOM_ENDPOINT = "custom_endpoint"
        private const val KEY_ENABLED = "cloud_ai_enabled"
    }
}
