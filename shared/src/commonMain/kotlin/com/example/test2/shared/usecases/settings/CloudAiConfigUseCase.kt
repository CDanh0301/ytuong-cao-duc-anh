package com.example.test2.shared.usecases.settings

import com.example.test2.shared.usecases.CloudAiConfigData
import com.example.test2.shared.usecases.CloudAiConfigPort

/**
 * Use case for reading and persisting Cloud AI configuration.
 */
class CloudAiConfigUseCase(
    private val configPort: CloudAiConfigPort
) {
    fun getConfig(): CloudAiConfigData = configPort.getConfig()

    fun saveConfig(
        apiKey: String,
        modelName: String,
        customEndpoint: String,
        enabled: Boolean
    ): CloudAiConfigData {
        configPort.saveConfig(apiKey, modelName, customEndpoint, enabled)
        return configPort.getConfig()
    }
}
