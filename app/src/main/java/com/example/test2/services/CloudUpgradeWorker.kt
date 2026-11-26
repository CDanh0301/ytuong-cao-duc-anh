package com.example.test2.services

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.test2.ai.CloudAiBackendClient
import com.example.test2.ai.CloudAiConfigManager
import com.example.test2.ai.LocalImageLabeler
import com.example.test2.ai.VectorSemanticSearchEngine
import com.example.test2.database.AppDatabaseHelper
import com.example.test2.database.QueueStatus
import com.example.test2.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 1 & 2: Offline-to-Cloud Backfill & Upgrade Worker.
 * Automatically triggered when the device regains internet connection.
 * Upgrades photos that were previously analyzed only with offline heuristics.
 */
class CloudUpgradeWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "cloud_upgrade_worker"

        fun enqueue(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val request = OneTimeWorkRequestBuilder<CloudUpgradeWorker>()
                    .setConstraints(constraints)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    request
                )
            } catch (e: Exception) {
                AppLogger.w("CloudUpgradeWorker", "Failed to enqueue worker", e)
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val aiConfig = CloudAiConfigManager.getConfig(context)
        if (!aiConfig.isEnabled || aiConfig.apiKey.isBlank()) {
            return@withContext Result.success()
        }

        val dbHelper = AppDatabaseHelper.getInstance(context)
        val itemsToUpgrade = dbHelper.getQueueItemsNeedingCloudUpgrade()

        for (item in itemsToUpgrade) {
            try {
                val fileName = item.filePath.substringAfterLast("/")
                val title = fileName.substringBeforeLast(".")
                val scrubbedText = item.scrubbedText ?: ""

                val visualAnalysis = LocalImageLabeler.analyze(context, item.filePath)

                // Privacy Gate: skip items containing sensitive documents or credentials
                if (com.example.test2.ai.PrivacyPolicy.classify(scrubbedText, title, visualAnalysis.rawLabels) == com.example.test2.ai.PrivacyDecision.LOCAL_ONLY) {
                    dbHelper.updateQueueItemState(
                        photoId = item.photoId,
                        status = QueueStatus.COMPLETED.name,
                        scrubbedText = scrubbedText,
                        needsCloudUpgrade = false
                    )
                    continue
                }

                val parsed = CloudAiBackendClient.analyzeWithFallback(
                    context = context,
                    scrubbedText = scrubbedText,
                    photoTitle = title,
                    categories = emptyList(),
                    isPro = true,
                    hasFace = visualAnalysis.isPerson,
                    visualLabels = visualAnalysis.rawLabels
                )

                val semanticTags = (parsed.semanticTags + visualAnalysis.vietnameseSemanticTags).distinct()
                val semanticTagsStr = semanticTags.joinToString(" ")
                val summary = parsed.summary ?: "${parsed.detectedIntent}: $title"
                val entitiesStr = if (parsed.entities.isNotEmpty()) {
                    parsed.entities.entries.joinToString(", ") { "${it.key}: ${it.value}" }
                } else ""
                val itemsToBuyStr = parsed.itemsToBuy.joinToString(", ")

                val fullSemanticDoc = buildString {
                    if (!parsed.summary.isNullOrBlank()) append(parsed.summary).append(" ")
                    if (!parsed.suggestedEventTitle.isNullOrBlank()) append(parsed.suggestedEventTitle).append(" ")
                    if (entitiesStr.isNotBlank()) append(entitiesStr).append(" ")
                    if (semanticTagsStr.isNotBlank()) append(semanticTagsStr).append(" ")
                    append(scrubbedText)
                }.trim()

                // Update SQLite queue entry
                dbHelper.updateQueueItemState(
                    photoId = item.photoId,
                    status = QueueStatus.COMPLETED.name,
                    scrubbedText = scrubbedText,
                    detectedIntent = parsed.detectedIntent,
                    eventTitle = parsed.suggestedEventTitle,
                    eventDateMillis = parsed.suggestedEventDateMillis,
                    targetCategoryId = parsed.targetCategoryId,
                    eventDescription = parsed.suggestedEventDescription,
                    summary = summary,
                    tags = semanticTagsStr,
                    entities = entitiesStr,
                    itemsToBuy = itemsToBuyStr,
                    needsCloudUpgrade = false,
                    modelName = aiConfig.modelName,
                    modelVersion = "1.0"
                )

                // Update FTS5 with enriched columns
                dbHelper.indexPhotoFts(
                    photoId = item.photoId,
                    title = title,
                    scrubbedText = scrubbedText,
                    intent = parsed.detectedIntent,
                    categoryNames = parsed.targetCategoryId ?: "",
                    semanticTags = semanticTagsStr,
                    entities = entitiesStr,
                    summary = summary
                )

                // Generate and save 768D cloud neural embedding
                val neuralVec = CloudAiBackendClient.getEmbedding(
                    text = fullSemanticDoc,
                    apiKey = aiConfig.apiKey,
                    modelName = CloudAiConfigManager.DEFAULT_EMBEDDING_MODEL,
                    taskType = "RETRIEVAL_DOCUMENT"
                )
                if (neuralVec != null) {
                    dbHelper.savePhotoNeuralVector(
                        photoId = item.photoId,
                        vector = neuralVec,
                        modelName = CloudAiConfigManager.DEFAULT_EMBEDDING_MODEL,
                        modelVersion = "1.0"
                    )
                }
            } catch (e: Exception) {
                AppLogger.w("CloudUpgradeWorker", "Failed to enqueue worker", e)
            }
        }

        Result.success()
    }
}
