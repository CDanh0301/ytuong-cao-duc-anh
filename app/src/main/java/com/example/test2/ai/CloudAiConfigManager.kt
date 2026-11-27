package com.example.test2.ai

import android.content.Context
import com.example.test2.data.local.prefs.CloudAiDefaults
import com.example.test2.data.local.prefs.CloudAiPrefsDataSource

typealias CloudAiConfig = com.example.test2.data.local.prefs.CloudAiConfig

object CloudAiConfigManager {

    const val HARDCODED_MODEL_NAME = CloudAiDefaults.HARDCODED_MODEL_NAME
    const val DEFAULT_MODEL = CloudAiDefaults.DEFAULT_MODEL
    const val DEFAULT_EMBEDDING_MODEL = CloudAiDefaults.DEFAULT_EMBEDDING_MODEL
    val AVAILABLE_MODELS = CloudAiDefaults.AVAILABLE_MODELS

    fun getConfig(context: Context): CloudAiConfig = CloudAiPrefsDataSource(context).getConfig()

    fun getApiKey(context: Context? = null): String {
        if (context == null) return ""
        return CloudAiPrefsDataSource(context).getApiKey()
    }

    fun getProxyBaseUrl(context: Context? = null): String {
        if (context == null) return CloudAiDefaults.PROXY_BASE_URL
        return CloudAiPrefsDataSource(context).getProxyBaseUrl()
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
