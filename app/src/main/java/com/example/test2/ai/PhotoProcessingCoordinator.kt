package com.example.test2.ai

import android.content.Context
import com.example.test2.ai.cards.QualityFlags
import com.example.test2.data.local.dao.ActionCardDao
import com.example.test2.data.local.dao.FtsSearchDao
import com.example.test2.data.local.dao.ScreenshotQueueDao
import com.example.test2.data.local.dao.VectorDao
import com.example.test2.database.AppDatabaseHelper
import com.example.test2.database.DefaultCategories
import com.example.test2.database.ProcessedScreenshotRecord
import com.example.test2.database.QueueStatus
import com.example.test2.shared.models.Photo
import com.example.test2.shared.repository.PhotoRepository
import com.example.test2.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coordinates on-device and cloud-assisted AI processing pipelines for photos.
 * Extracts vision labeling, OCR, PII scrubbing, heuristic categorization,
 * FTS5 indexing, and vector embedding generation out of the ViewModel.
 */
class PhotoProcessingCoordinator(
    private val context: Context,
    private val repository: PhotoRepository,
    private val queueDao: ScreenshotQueueDao,
    private val ftsDao: FtsSearchDao,
    private val vectorDao: VectorDao,
    private val actionCardDao: ActionCardDao
) {
    constructor(
        context: Context,
        repository: PhotoRepository,
        dbHelper: AppDatabaseHelper
    ) : this(context, repository, dbHelper, dbHelper, dbHelper, dbHelper)

    /**
     * Re-verifies a cached photo's category against newly added custom categories
     * to prevent photos from remaining trapped in default categories.
     */
    suspend fun recheckWithCustomCategories(
        photo: Photo,
        cached: ProcessedScreenshotRecord
    ): String? = withContext(Dispatchers.IO) {
        var resolvedCatId = cached.targetCategoryId
        val customCats = repository.categories.value.filter { it.id !in DefaultCategories.ALL_IDS }
        if (customCats.isNotEmpty() && (resolvedCatId == null || resolvedCatId in DefaultCategories.ALL_IDS)) {
            val visualAnalysis = LocalImageLabeler.analyze(context, photo.filePath)
            val recheck = SmartHeuristicParser.parse(
                scrubbedText = cached.scrubbedText ?: "",
                photoTitle = photo.title,
                customCategories = repository.categories.value,
                hasFace = visualAnalysis.isPerson,
                visualLabels = visualAnalysis.rawLabels
            )
            if (recheck.targetCategoryId != null && recheck.targetCategoryId !in DefaultCategories.ALL_IDS) {
                resolvedCatId = recheck.targetCategoryId
                queueDao.recordProcessedScreenshot(
                    photoId = photo.id,
                    filePath = photo.filePath,
                    dateAdded = photo.dateAdded,
                    scrubbedText = cached.scrubbedText ?: "",
                    detectedIntent = cached.detectedIntent,
                    eventTitle = cached.eventTitle,
                    eventDateMillis = cached.eventDateMillis,
                    targetCategoryId = resolvedCatId,
                    eventDescription = cached.eventDescription
                )
            }
        }
        resolvedCatId
    }

    /**
     * Executes the local-first on-device AI processing pipeline:
     * 1. Junk/meme pre-filtering
     * 2. Vision analysis (ML Kit Image Labeling)
     * 3. Nature/scenery short-circuit optimization
     * 4. Local OCR (ML Kit)
     * 5. PII scrubbing
     * 6. Smart heuristic classification & metadata enrichment
     * 7. FTS5 indexing & on-device 128D vector generation (Pro tier)
     * 8. Background queue enrollment for progressive cloud enrichment
     */
    suspend fun runOnDevicePipeline(
        photo: Photo,
        targetFolderId: String? = null,
        targetCategoryIds: Set<String> = emptySet()
    ) = withContext(Dispatchers.IO) {
        // Step 1: Pre-filtering junk, memes, or blank icons without consuming quota
        if (SmartHeuristicParser.isJunkOrMeme(photo)) {
            repository.updatePhotoAiMetadata(
                photoId = photo.id,
                isScreenshot = true,
                piiScrubbedText = "",
                detectedIntent = null,
                suggestedEventTitle = null,
                suggestedEventDateMillis = null,
                targetCategoryId = null,
                isProcessed = true
            )
            return@withContext
        }

        AppLogger.d("PhotoCoordinator", "Starting runOnDevicePipeline for ${photo.id}: ${photo.fileName}, filePath=${photo.filePath}")
        // Step 2: Unified On-Device Vision & OCR Analysis
        val onDeviceResult = if (QualityFlags.SHARED_DECODE) {
            OnDeviceAnalysis.analyze(context, photo.id, photo.filePath, photo.title)
        } else null

        val visualAnalysis = onDeviceResult?.visual ?: LocalImageLabeler.analyze(context, photo.filePath)
        AppLogger.d("PhotoCoordinator", "visualAnalysis for ${photo.id}: nature=${visualAnalysis.isNature}, animal=${visualAnalysis.isAnimal}, labels=${visualAnalysis.rawLabels}")

        // Save cards immediately on device if available
        if (QualityFlags.INSTANT_EXTRACTION && onDeviceResult != null && onDeviceResult.cards.isNotEmpty()) {
            actionCardDao.replaceActionCardsForPhoto(photo.id, onDeviceResult.cards)
            CloudAiSyncNotifier.notifyBatchCompleted(listOf(photo.id))
        }

        // Short-Circuit Optimization: Pure Nature, Landscape, Scenery & Animal photos without text
        if ((visualAnalysis.isNature || visualAnalysis.isAnimal) && !visualAnalysis.hasTextOrDoc) {
            val targetCatId = DefaultCategories.NATURE
            val categoryNames = repository.categories.value
                .filter { it.id == targetCatId }
                .joinToString(" ") { it.name }

            val semanticTags = visualAnalysis.vietnameseSemanticTags.ifEmpty {
                if (visualAnalysis.isAnimal) listOf("động vật", "thú cưng") else listOf("thiên nhiên", "phong cảnh")
            }
            val semanticTagsStr = semanticTags.joinToString(" ")
            val fullSemanticText = "${photo.title} Thiên nhiên phong cảnh $semanticTagsStr".trim()

            ftsDao.indexPhotoFts(
                photoId = photo.id,
                title = photo.title,
                scrubbedText = fullSemanticText,
                intent = "OTHER",
                categoryNames = categoryNames,
                semanticTags = semanticTagsStr
            )

            if (repository.accountTier.value.isPro) {
                val embedding = VectorSemanticSearchEngine.generateEmbedding(
                    text = fullSemanticText,
                    title = photo.title,
                    intent = "OTHER",
                    categoryNames = if (categoryNames.isNotBlank()) listOf(categoryNames) else emptyList()
                )
                vectorDao.savePhotoVector(photo.id, embedding, "OTHER")
                queueDao.enqueueScreenshot(
                    photoId = photo.id,
                    filePath = photo.filePath,
                    dateAdded = photo.dateAdded,
                    isLiveCapture = false,
                    photoTitle = photo.title,
                    originalFileName = photo.fileName
                )
            }

            repository.updatePhotoAiMetadata(
                photoId = photo.id,
                isScreenshot = true,
                piiScrubbedText = "",
                detectedIntent = null,
                suggestedEventTitle = null,
                suggestedEventDateMillis = null,
                targetCategoryId = targetCatId,
                isProcessed = true,
                isCloudEnriched = false,
                suggestedEventDescription = null,
                semanticTags = semanticTags
            )
            return@withContext
        }

        // Step 3: Local OCR text evaluation
        val ocrText = onDeviceResult?.ocr?.flatText ?: MlKitOcrEngine.recognizeText(context, photo.filePath)
        val hasFace = visualAnalysis.isPerson

        if (ocrText.isBlank() || ocrText.trim().length < 4) {
            // Blank lock-screens or images without useful text: check filename/folder cues, face and visual labels
            val folderOrTarget = targetFolderId ?: photo.folderId ?: ""
            val heuristic = SmartHeuristicParser.parse(
                scrubbedText = "",
                photoTitle = "$folderOrTarget ${photo.title}".trim(),
                hasFace = hasFace,
                visualLabels = visualAnalysis.rawLabels
            )
            val targetCatId = heuristic.targetCategoryId
            val categoryNames = repository.categories.value
                .filter { it.id == targetCatId }
                .joinToString(" ") { it.name }

            val semanticTags = heuristic.semanticTags.ifEmpty { visualAnalysis.vietnameseSemanticTags }
            val semanticTagsStr = semanticTags.joinToString(" ")
            val fullSemanticText = "${photo.title} $semanticTagsStr".trim()

            ftsDao.indexPhotoFts(
                photoId = photo.id,
                title = photo.title,
                scrubbedText = fullSemanticText,
                intent = heuristic.detectedIntent,
                categoryNames = categoryNames,
                semanticTags = semanticTagsStr
            )

            if (repository.accountTier.value.isPro) {
                val embedding = VectorSemanticSearchEngine.generateEmbedding(
                    text = "$folderOrTarget $categoryNames $semanticTagsStr".trim(),
                    title = photo.title,
                    intent = heuristic.detectedIntent,
                    categoryNames = if (categoryNames.isNotBlank()) listOf(categoryNames) else emptyList()
                )
                vectorDao.savePhotoVector(photo.id, embedding, heuristic.detectedIntent)
                if (fullSemanticText.isNotBlank()) {
                    queueDao.enqueueScreenshot(
                        photoId = photo.id,
                        filePath = photo.filePath,
                        dateAdded = photo.dateAdded,
                        isLiveCapture = false,
                        photoTitle = photo.title,
                        originalFileName = photo.fileName
                    )
                }
            }

            // Check if photo still has informative visual content (e.g. receipt/doc/ticket/invoice)
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
            if (hasInformativeVisual) {
                queueDao.enqueueScreenshot(
                    photoId = photo.id,
                    filePath = photo.filePath,
                    dateAdded = photo.dateAdded,
                    isLiveCapture = false,
                    scrubbedText = "",
                    photoTitle = photo.title,
                    originalFileName = photo.fileName
                )
            }

            repository.updatePhotoAiMetadata(
                photoId = photo.id,
                isScreenshot = true,
                piiScrubbedText = "",
                detectedIntent = heuristic.detectedIntent.takeIf { it != "OTHER" },
                suggestedEventTitle = heuristic.suggestedEventTitle.takeIf { heuristic.detectedIntent == "EVENT" || heuristic.detectedIntent == "SHOPPING" }
                    ?: photo.title.takeIf { targetCatId == DefaultCategories.TO_BUY },
                suggestedEventDateMillis = heuristic.suggestedEventDateMillis,
                targetCategoryId = targetCatId,
                isProcessed = true,
                isCloudEnriched = false,
                suggestedEventDescription = heuristic.suggestedEventDescription,
                semanticTags = semanticTags
            )
            return@withContext
        }

        // Step 4: Local PII Scrubbing on device
        val piiResult = PiiScrubber.scrub(ocrText)

        // Step 5: Instant Local-First Processing (ML Kit Vision + OCR + SmartHeuristics + FTS5 + 128D Vector)
        val heuristic = SmartHeuristicParser.parse(
            scrubbedText = piiResult.scrubbedText,
            photoTitle = photo.title,
            customCategories = repository.categories.value,
            hasFace = hasFace,
            visualLabels = visualAnalysis.rawLabels
        )
        val targetCatId = heuristic.targetCategoryId
        val categoryNames = repository.categories.value
            .filter { it.id == targetCatId }
            .joinToString(" ") { it.name }

        val semanticTags = (heuristic.semanticTags + visualAnalysis.vietnameseSemanticTags).distinct()
        val semanticTagsStr = semanticTags.joinToString(" ")
        val fullSemanticText = buildString {
            append(piiResult.scrubbedText)
            if (!heuristic.suggestedEventTitle.isNullOrBlank()) append(" ").append(heuristic.suggestedEventTitle)
            if (!heuristic.suggestedEventDescription.isNullOrBlank()) append(" ").append(heuristic.suggestedEventDescription)
            if (semanticTagsStr.isNotBlank()) append(" ").append(semanticTagsStr)
        }.trim()

        ftsDao.indexPhotoFts(
            photoId = photo.id,
            title = photo.title,
            scrubbedText = fullSemanticText,
            intent = heuristic.detectedIntent,
            categoryNames = categoryNames,
            semanticTags = semanticTagsStr
        )

        if (repository.accountTier.value.isPro) {
            val embedding = VectorSemanticSearchEngine.generateEmbedding(
                text = fullSemanticText,
                title = heuristic.suggestedEventTitle ?: photo.title,
                intent = heuristic.detectedIntent,
                categoryNames = if (categoryNames.isNotBlank()) listOf(categoryNames) else emptyList(),
                customCategories = repository.categories.value
            )
            vectorDao.savePhotoVector(photo.id, embedding, heuristic.detectedIntent)
        }

        repository.updatePhotoAiMetadata(
            photoId = photo.id,
            isScreenshot = true,
            piiScrubbedText = piiResult.scrubbedText,
            detectedIntent = heuristic.detectedIntent.takeIf { it != "OTHER" },
            suggestedEventTitle = heuristic.suggestedEventTitle.takeIf { heuristic.detectedIntent == "EVENT" || heuristic.detectedIntent == "SHOPPING" }
                ?: photo.title.takeIf { targetCatId == DefaultCategories.TO_BUY },
            suggestedEventDateMillis = heuristic.suggestedEventDateMillis,
            targetCategoryId = targetCatId,
            isProcessed = true,
            isCloudEnriched = false,
            suggestedEventDescription = heuristic.suggestedEventDescription,
            semanticTags = semanticTags
        )

        // Enqueue to background queue for progressive Cloud enrichment (Gemini batching & neural vectors)
        queueDao.enqueueScreenshot(
            photoId = photo.id,
            filePath = photo.filePath,
            dateAdded = photo.dateAdded,
            isLiveCapture = false,
            scrubbedText = piiResult.scrubbedText,
            photoTitle = photo.title,
            originalFileName = photo.fileName
        )
    }

    /**
     * Executes cloud-assisted AI scanning with fallback to on-device heuristics.
     * Used when the user manually scans a single photo or imports photos with available AI quota.
     */
    suspend fun scanPhotoWithCloud(
        photo: Photo,
        targetFolderId: String? = null
    ) = withContext(Dispatchers.IO) {
        val onDeviceResult = if (QualityFlags.SHARED_DECODE) {
            OnDeviceAnalysis.analyze(context, photo.id, photo.filePath, photo.title)
        } else null

        val visualAnalysis = onDeviceResult?.visual ?: LocalImageLabeler.analyze(context, photo.filePath)
        val hasFace = visualAnalysis.isPerson
        val ocrText = onDeviceResult?.ocr?.flatText ?: if ((visualAnalysis.isNature || visualAnalysis.isAnimal) && !visualAnalysis.hasTextOrDoc) {
            ""
        } else {
            MlKitOcrEngine.recognizeText(context, photo.filePath)
        }
        val piiResult = onDeviceResult?.let { PiiScrubResult(it.scrubbedText, false) } ?: PiiScrubber.scrub(ocrText)

        // Save cards immediately on device if available
        if (QualityFlags.INSTANT_EXTRACTION && onDeviceResult != null && onDeviceResult.cards.isNotEmpty()) {
            actionCardDao.replaceActionCardsForPhoto(photo.id, onDeviceResult.cards)
            CloudAiSyncNotifier.notifyBatchCompleted(listOf(photo.id))
        }

        val privacyDecision = PrivacyPolicy.classify(
            scrubbedText = piiResult.scrubbedText,
            photoTitle = photo.title,
            visualLabels = visualAnalysis.rawLabels
        )

        val parsed = if (privacyDecision == PrivacyDecision.LOCAL_ONLY) {
            SmartHeuristicParser.parse(
                scrubbedText = piiResult.scrubbedText,
                photoTitle = photo.title,
                customCategories = if (repository.accountTier.value.isPro) repository.categories.value else emptyList(),
                hasFace = hasFace,
                visualLabels = visualAnalysis.rawLabels
            )
        } else {
            CloudAiBackendClient.analyzeWithFallback(
                context = context,
                scrubbedText = piiResult.scrubbedText,
                photoTitle = photo.title,
                categories = repository.categories.value,
                isPro = repository.accountTier.value.isPro,
                hasFace = hasFace,
                visualLabels = visualAnalysis.rawLabels
            )
        }

        val categoryNames = repository.categories.value
            .filter { it.id == parsed.targetCategoryId }
            .joinToString(" ") { it.name }

        ftsDao.indexPhotoFts(
            photoId = photo.id,
            title = photo.title,
            scrubbedText = piiResult.scrubbedText,
            intent = parsed.detectedIntent,
            categoryNames = categoryNames
        )

        // Index in Vector Semantic Search (128D on-device)
        val embedding = VectorSemanticSearchEngine.generateEmbedding(
            text = "${targetFolderId ?: ""} ${piiResult.scrubbedText}".trim(),
            title = photo.title,
            intent = parsed.detectedIntent,
            categoryNames = if (categoryNames.isNotBlank()) listOf(categoryNames) else emptyList(),
            customCategories = repository.categories.value
        )
        vectorDao.savePhotoVector(photo.id, embedding, parsed.detectedIntent)

        // 768D Neural Vector (Pro tier with API Key, only for CLOUD_ELIGIBLE)
        if (privacyDecision == PrivacyDecision.CLOUD_ELIGIBLE && repository.accountTier.value.isPro && piiResult.scrubbedText.isNotBlank()) {
            val apiKey = CloudAiConfigManager.getApiKey(context)
            if (apiKey.isNotBlank()) {
                val neuralVec = CloudAiBackendClient.getEmbedding(piiResult.scrubbedText, apiKey)
                if (neuralVec != null) {
                    vectorDao.savePhotoNeuralVector(photo.id, neuralVec)
                }
            }
        }

        queueDao.markQueueItemProcessed(photo.id, QueueStatus.COMPLETED)

        queueDao.recordProcessedScreenshot(
            photoId = photo.id,
            filePath = photo.filePath,
            dateAdded = photo.dateAdded,
            scrubbedText = piiResult.scrubbedText,
            detectedIntent = parsed.detectedIntent,
            eventTitle = parsed.suggestedEventTitle,
            eventDateMillis = parsed.suggestedEventDateMillis,
            targetCategoryId = parsed.targetCategoryId,
            eventDescription = parsed.suggestedEventDescription
        )

        repository.updatePhotoAiMetadata(
            photoId = photo.id,
            isScreenshot = true,
            piiScrubbedText = piiResult.scrubbedText,
            detectedIntent = parsed.detectedIntent,
            suggestedEventTitle = parsed.suggestedEventTitle,
            suggestedEventDateMillis = parsed.suggestedEventDateMillis,
            targetCategoryId = parsed.targetCategoryId,
            isProcessed = true,
            suggestedEventDescription = parsed.suggestedEventDescription,
            isCloudEnriched = parsed.isCloudEnriched,
            forceUpdate = true
        )
    }
}
