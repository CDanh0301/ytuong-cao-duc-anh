package com.example.test2.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.test2.ai.BatchPhotoItem
import com.example.test2.ai.CloudAiBackendClient
import com.example.test2.ai.CloudAiSyncNotifier
import com.example.test2.ai.CloudAiConfigManager
import com.example.test2.ai.LocalActionCardExtractor
import com.example.test2.ai.LocalImageLabeler
import com.example.test2.ai.MlKitOcrEngine
import com.example.test2.ai.OnDeviceAnalysis
import com.example.test2.ai.PiiScrubResult
import com.example.test2.ai.PiiScrubber
import com.example.test2.ai.PrivacyDecision
import com.example.test2.ai.PrivacyPolicy
import com.example.test2.ai.SmartHeuristicParser
import com.example.test2.ai.VectorSemanticSearchEngine
import com.example.test2.ai.cards.QualityFlags
import com.example.test2.database.AppDatabaseHelper
import com.example.test2.notifications.ScreenshotNotificationHelper
import com.example.test2.security.PrivacyVaultManager
import com.example.test2.shared.models.ActionCard
import com.example.test2.shared.models.Category
import com.example.test2.storage.AndroidFilePersistenceDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private data class EmbeddingTarget(
    val photoId: String,
    val semanticText: String
)

/**
 * Phase 1: Robust Background Queue Worker powered by WorkManager.
 * Drains the SQLite queue atomically: PENDING -> OCR_DONE -> CLOUD_DONE -> EMBEDDED -> COMPLETED.
 * Guarantees zero duplicate processing and automatic resumption if the process was interrupted.
 */
class ScreenshotQueueWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "screenshot_queue_worker"

        fun enqueue(context: Context) {
            try {
                val request = OneTimeWorkRequestBuilder<ScreenshotQueueWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    request
                )
            } catch (_: Exception) {
            }
        }
    }

    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo {
        val notification = ScreenshotNotificationHelper.getProcessingNotification(context)
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            androidx.work.ForegroundInfo(
                ScreenshotNotificationHelper.NOTIFICATION_PROCESSING_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            androidx.work.ForegroundInfo(
                ScreenshotNotificationHelper.NOTIFICATION_PROCESSING_ID,
                notification
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val dbHelper = AppDatabaseHelper.getInstance(context)
        dbHelper.resetHangingProcessingItems()

        var processedCount = 0
        val maxItemsPerRun = 20

        while (!isStopped) {
            val batch = dbHelper.claimNextPendingQueueItems(limit = 4)
            if (batch.isEmpty()) break
            processQueueBatch(batch, dbHelper)
            processedCount += batch.size

            if (QualityFlags.WORKER_HARDENING && processedCount >= maxItemsPerRun) {
                // Re-enqueue to avoid exceeding OS background execution limits
                enqueue(context)
                break
            }
        }

        Result.success()
    }

    private suspend fun processQueueBatch(
        batch: List<com.example.test2.database.ScreenshotQueueItem>,
        dbHelper: AppDatabaseHelper
    ) {
        if (batch.isEmpty()) return

        try {
            // Step 1: Load User Categories & Account Tier from persistent storage once per batch
            val persistenceDriver = AndroidFilePersistenceDriver(context)
            val rawJson = persistenceDriver.loadData()
            val (userCategories, isProTier) = if (!rawJson.isNullOrBlank()) {
                try {
                    val root = JSONObject(rawJson)
                    val catsArray = root.optJSONArray("categories")
                    val catList = mutableListOf<Category>()
                    if (catsArray != null) {
                        for (i in 0 until catsArray.length()) {
                            val obj = catsArray.optJSONObject(i) ?: continue
                            val id = obj.optString("id", "")
                            val name = obj.optString("name", "")
                            val colorHex = obj.optString("colorHex", "#6366F1")
                            val iconName = obj.optString("iconName", "tag")
                            val createdAt = obj.optLong("createdAt", 0L)
                            if (id.isNotBlank() && name.isNotBlank()) {
                                catList.add(Category(id = id, name = name, colorHex = colorHex, iconName = iconName, createdAt = createdAt))
                            }
                        }
                    }
                    val isPro = root.optJSONObject("accountTier")?.optBoolean("isPro", true) ?: true
                    Pair(catList, isPro)
                } catch (_: Exception) {
                    Pair(emptyList<Category>(), true)
                }
            } else {
                Pair(emptyList<Category>(), true)
            }

            val aiConfig = CloudAiConfigManager.getConfig(context)
            val isOnline = isNetworkAvailable(context)
            val canUseCloud = isOnline && aiConfig.isEnabled && aiConfig.apiKey.isNotBlank()

            // Pre-process items: Junk filter, ML Kit Vision & OCR
            data class PreprocessedItem(
                val queueItem: com.example.test2.database.ScreenshotQueueItem,
                val filePath: String,
                val fileName: String,
                val title: String,
                val visualAnalysis: com.example.test2.ai.VisualAnalysisResult,
                val scrubbedText: String,
                val isJunk: Boolean,
                val needsCloudAnalysis: Boolean,
                val actionCards: List<ActionCard> = emptyList()
            )

            val preprocessed = mutableListOf<PreprocessedItem>()
            val cloudCandidates = mutableListOf<BatchPhotoItem>()

            for (item in batch) {
                var currentFilePath = item.filePath
                var currentFileName = item.originalFileName ?: item.filePath.substringAfterLast("/")

                // If Privacy Vault mode is active and screenshot is eligible for auto-vault, seamlessly move and encrypt
                if (PrivacyVaultManager.shouldAutoVaultPhoto(context, item.photoId, item.dateAdded) && !currentFilePath.endsWith(".enc")) {
                    val uri = if (currentFilePath.startsWith("content://")) Uri.parse(currentFilePath) else null
                    val vaultResult = PrivacyVaultManager.vaultAndEvictScreenshot(context, uri, currentFilePath, currentFileName)
                    if (vaultResult != null) {
                        currentFilePath = vaultResult.newFilePath
                        currentFileName = vaultResult.newFileName
                        dbHelper.syncVaultedPathToQueue(
                            photoId = item.photoId,
                            newFilePath = currentFilePath,
                            dateAdded = item.dateAdded,
                            photoTitle = item.photoTitle,
                            originalFileName = item.originalFileName
                        )
                    }
                }

                val fileName = currentFileName
                val title = item.photoTitle ?: fileName.substringBeforeLast(".")

                if (SmartHeuristicParser.isJunkOrMeme(fileName)) {
                    dbHelper.recordProcessedScreenshot(
                        photoId = item.photoId,
                        filePath = currentFilePath,
                        dateAdded = item.dateAdded,
                        scrubbedText = "",
                        detectedIntent = null,
                        eventTitle = null,
                        eventDateMillis = null,
                        targetCategoryId = null,
                        status = "COMPLETED"
                    )
                    dbHelper.markQueueItemProcessed(item.photoId, "COMPLETED")
                    preprocessed.add(
                        PreprocessedItem(item, currentFilePath, fileName, title, com.example.test2.ai.VisualAnalysisResult(), "", isJunk = true, needsCloudAnalysis = false)
                    )
                    continue
                }

                // Step 2 & 3: Single-pass On-Device Analysis (Decodes once, OCR, QR, Cards, Vision)
                val onDeviceResult = if (QualityFlags.SHARED_DECODE) {
                    OnDeviceAnalysis.analyze(context, item.photoId, currentFilePath, title)
                } else null

                val visualAnalysis = onDeviceResult?.visual ?: LocalImageLabeler.analyze(context, currentFilePath)
                val rawOcrText = onDeviceResult?.ocr?.flatText ?: MlKitOcrEngine.recognizeText(context, currentFilePath)

                // Extract Smart Action Cards on-device from raw text and QR code before PII scrubbing
                val actionCards = onDeviceResult?.cards ?: LocalActionCardExtractor.extractCards(
                    context = context,
                    filePath = currentFilePath,
                    photoId = item.photoId,
                    rawOcrText = rawOcrText,
                    photoTitle = title
                )
                if (actionCards.isNotEmpty()) {
                    dbHelper.replaceActionCardsForPhoto(item.photoId, actionCards)
                }

                val piiResult = onDeviceResult?.let { PiiScrubResult(it.scrubbedText, false) } ?: PiiScrubber.scrub(rawOcrText)
                val scrubbedText = piiResult.scrubbedText

                dbHelper.updateQueueItemState(
                    photoId = item.photoId,
                    status = "OCR_DONE",
                    scrubbedText = scrubbedText
                )

                // Privacy Policy Gate: check if content is strictly LOCAL_ONLY
                val privacyDecision = PrivacyPolicy.classify(
                    scrubbedText = scrubbedText,
                    photoTitle = title,
                    visualLabels = visualAnalysis.rawLabels
                )
                val isCloudAllowed = privacyDecision == PrivacyDecision.CLOUD_ELIGIBLE

                // Smart Cloud Gatekeeper: Skip 3.5 Flash Lite ONLY for genuinely non-informative photos or LOCAL_ONLY
                // If text is short, but has face, or document/receipt visual labels, or informative title: still allow Cloud AI!
                val hasInformativeVisual = visualAnalysis.isPerson || visualAnalysis.hasTextOrDoc ||
                        visualAnalysis.rawLabels.any {
                            it.equals("Receipt", ignoreCase = true) ||
                            it.equals("Document", ignoreCase = true) ||
                            it.equals("Paper", ignoreCase = true) ||
                            it.equals("Invoice", ignoreCase = true) ||
                            it.equals("Identity document", ignoreCase = true) ||
                            it.equals("Ticket", ignoreCase = true) ||
                            it.equals("Poster", ignoreCase = true)
                        }
                val hasInformativeTitle = !title.startsWith("IMG_", ignoreCase = true) &&
                        !title.startsWith("Screenshot_", ignoreCase = true) &&
                        !title.startsWith("PXL_", ignoreCase = true) &&
                        title.trim().length > 3
                val isGenuinelyNonText = (scrubbedText.isBlank() || scrubbedText.trim().length < 4) && !hasInformativeVisual && !hasInformativeTitle
                val isPureNatureWithoutText = (visualAnalysis.isNature || visualAnalysis.isAnimal) && !visualAnalysis.hasTextOrDoc && scrubbedText.trim().length < 4

                val needsCloud = canUseCloud && isCloudAllowed && !isGenuinelyNonText && !isPureNatureWithoutText
                if (needsCloud) {
                    cloudCandidates.add(
                        BatchPhotoItem(
                            photoId = item.photoId,
                            photoTitle = title,
                            scrubbedText = scrubbedText,
                            hasFace = visualAnalysis.isPerson,
                            visualLabels = visualAnalysis.rawLabels
                        )
                    )
                }

                preprocessed.add(
                    PreprocessedItem(item, currentFilePath, fileName, title, visualAnalysis, scrubbedText, isJunk = false, needsCloudAnalysis = needsCloud, actionCards = actionCards)
                )
            }

            // Call Gemini 3.5 Flash Lite in Batch for text-containing items
            val cloudResults = if (canUseCloud && cloudCandidates.isNotEmpty()) {
                CloudAiBackendClient.analyzeBatchWithGemini(
                    context = context,
                    items = cloudCandidates,
                    categories = userCategories,
                    isPro = isProTier
                )
            } else {
                emptyMap()
            }

            // Prepare lists for storing structured metadata and batch neural vectors
            val embeddingTextsToGenerate = mutableListOf<EmbeddingTarget>()
            var hasCloudUpgradePending = false

            for (prep in preprocessed) {
                if (prep.isJunk) continue

                val item = prep.queueItem
                val title = prep.title
                val visualAnalysis = prep.visualAnalysis
                val scrubbedText = prep.scrubbedText

                // Get parsed data from Cloud results or on-device SmartHeuristicParser
                val parsed = if (prep.needsCloudAnalysis && cloudResults.containsKey(item.photoId)) {
                    val res = cloudResults[item.photoId]!!
                    if (!res.isCloudEnriched && aiConfig.isEnabled && aiConfig.apiKey.isNotBlank()) {
                        hasCloudUpgradePending = true
                    }
                    res
                } else {
                    if (aiConfig.isEnabled && aiConfig.apiKey.isNotBlank() && prep.needsCloudAnalysis) {
                        hasCloudUpgradePending = true
                    }
                    SmartHeuristicParser.parse(
                        scrubbedText = scrubbedText,
                        photoTitle = title,
                        customCategories = userCategories,
                        hasFace = visualAnalysis.isPerson,
                        visualLabels = visualAnalysis.rawLabels
                    )
                }

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

                // Save processed screenshot record
                dbHelper.recordProcessedScreenshot(
                    photoId = item.photoId,
                    filePath = prep.filePath,
                    dateAdded = item.dateAdded,
                    scrubbedText = scrubbedText,
                    detectedIntent = parsed.detectedIntent,
                    eventTitle = parsed.suggestedEventTitle,
                    eventDateMillis = parsed.suggestedEventDateMillis,
                    targetCategoryId = parsed.targetCategoryId,
                    status = "PROCESSING",
                    eventDescription = parsed.suggestedEventDescription,
                    summary = summary,
                    tags = semanticTagsStr,
                    entities = entitiesStr,
                    itemsToBuy = itemsToBuyStr
                )

                // Index in FTS5
                val categoryDisplayName = userCategories.firstOrNull { it.id == parsed.targetCategoryId }?.name ?: parsed.targetCategoryId ?: ""
                val cardKeywords = prep.actionCards.joinToString(" ") { "${it.title} ${it.subtitle ?: ""}" }
                val combinedEntities = if (entitiesStr.isNotBlank() && cardKeywords.isNotBlank()) {
                    "$entitiesStr $cardKeywords"
                } else if (cardKeywords.isNotBlank()) {
                    cardKeywords
                } else {
                    entitiesStr
                }

                dbHelper.indexPhotoFts(
                    photoId = item.photoId,
                    title = title,
                    scrubbedText = scrubbedText,
                    intent = parsed.detectedIntent,
                    categoryNames = categoryDisplayName,
                    semanticTags = semanticTagsStr,
                    entities = combinedEntities.trim(),
                    summary = summary
                )

                // Save on-device 128D vector
                val localEmbedding = VectorSemanticSearchEngine.generateEmbedding(
                    text = fullSemanticDoc,
                    title = parsed.suggestedEventTitle ?: title,
                    intent = parsed.detectedIntent,
                    categoryNames = if (!parsed.targetCategoryId.isNullOrBlank()) listOf(categoryDisplayName) else emptyList(),
                    customCategories = userCategories
                )
                dbHelper.savePhotoVector(item.photoId, localEmbedding, parsed.detectedIntent)

                // Enqueue for batch embedding ONLY if cloud is allowed for this item
                val itemPrivacy = PrivacyPolicy.classify(scrubbedText, title, visualAnalysis.rawLabels)
                if (itemPrivacy == PrivacyDecision.CLOUD_ELIGIBLE && fullSemanticDoc.isNotBlank()) {
                    embeddingTextsToGenerate.add(EmbeddingTarget(item.photoId, fullSemanticDoc))
                }

                // Push notifications ONLY for genuine EVENT or SHOPPING and ONLY if this was a live capture
                val shouldNotify = item.isLiveCapture &&
                        (parsed.detectedIntent == "EVENT" || parsed.detectedIntent == "SHOPPING") &&
                        !parsed.suggestedEventTitle.isNullOrBlank()
                if (shouldNotify) {
                    ScreenshotNotificationHelper.showScreenshotNotification(
                        context = context,
                        photoId = item.photoId,
                        photoTitle = title,
                        intent = parsed.detectedIntent,
                        eventTitle = parsed.suggestedEventTitle,
                        eventTimeMillis = parsed.suggestedEventDateMillis,
                        eventDescription = parsed.suggestedEventDescription
                    )
                }

                dbHelper.markQueueItemProcessed(item.photoId, "COMPLETED")
            }

            // Step 4: Batch Neural Vector Embeddings (1 HTTP call for the entire batch!)
            if (canUseCloud && embeddingTextsToGenerate.isNotEmpty()) {
                try {
                    val texts = embeddingTextsToGenerate.map { it.semanticText }
                    val vectors = CloudAiBackendClient.getBatchEmbeddings(
                        texts = texts,
                        apiKey = aiConfig.apiKey,
                        modelName = CloudAiConfigManager.DEFAULT_EMBEDDING_MODEL,
                        taskType = "RETRIEVAL_DOCUMENT"
                    )

                    val vectorsToSave = mutableMapOf<String, FloatArray>()
                    for (i in vectors.indices) {
                        val vec = vectors.getOrNull(i)
                        if (vec != null) {
                            val photoId = embeddingTextsToGenerate[i].photoId
                            vectorsToSave[photoId] = vec
                        }
                    }

                    if (vectorsToSave.isNotEmpty()) {
                        dbHelper.savePhotoNeuralVectorsBatch(
                            vectors = vectorsToSave,
                            modelName = CloudAiConfigManager.DEFAULT_EMBEDDING_MODEL,
                            modelVersion = "1.0"
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ScreenshotQueueWorker", "Batch embedding failed: ${e.message}", e)
                    hasCloudUpgradePending = true
                }
            }

            // Notify ViewModel/UI in realtime about all newly completed items in this batch
            val completedIds = preprocessed.filter { !it.isJunk }.map { it.queueItem.photoId }
            if (completedIds.isNotEmpty()) {
                CloudAiSyncNotifier.notifyBatchCompleted(completedIds)
            }

            if (hasCloudUpgradePending) {
                CloudUpgradeWorker.enqueue(context)
            }
        } catch (e: Exception) {
            android.util.Log.e("ScreenshotQueueWorker", "Batch processing failed: ${e.message}", e)
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
