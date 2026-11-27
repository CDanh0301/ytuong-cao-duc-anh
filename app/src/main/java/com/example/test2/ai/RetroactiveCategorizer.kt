package com.example.test2.ai

import android.content.Context
import com.example.test2.data.local.dao.FtsSearchDao
import com.example.test2.data.local.dao.ScreenshotQueueDao
import com.example.test2.data.local.dao.VectorDao
import com.example.test2.data.remote.CategoryCandidateSnippet
import com.example.test2.data.remote.GeminiApiService
import com.example.test2.database.AppDatabaseHelper
import com.example.test2.shared.models.DefaultCategories
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Photo
import com.example.test2.shared.utils.VietnameseUtils
import com.example.test2.util.AppLogger
import java.util.Locale

/**
 * Advanced Retroactive Auto-Categorization Engine.
 * Scans existing photos in gallery when a new custom category is created (or manually triggered),
 * combining Multi-Tier Confidence Scoring, OCR Full-Text Analysis, Visual Concept Expansion (VisualConceptExpander),
 * Contextual Guardrails (anti-false-positive engine), On-Device Vector Search, and Cloud Neural Embeddings.
 */
object RetroactiveCategorizer {

    const val MATCH_THRESHOLD = 45.0f
    const val MAX_CANDIDATE_LIMIT = 50

    private val STOP_WORDS = setOf(
        "and", "the", "for", "with", "của", "các", "những", "cho", "với", "ảnh", "anh", "em", "này", "đó", "đây", "la", "là"
    )

    private val TEXT_DOCUMENT_CATEGORY_KEYWORDS = setOf(
        "học", "tài liệu", "đọc", "sách", "bài viết", "hợp đồng", "công việc",
        "ghi chú", "thông báo", "báo cáo", "nghiên cứu", "kiến thức", "chứng từ", "ôn thi",
        "education", "document", "article", "contract", "study", "learning", "work"
    )

    private val PRICE_REGEX = Regex("""\d+[\.,]\d{3}\s*(đ|₫|vnd|\$)|(\$|₫|đ)\s*\d+|\b\d+k\b""", RegexOption.IGNORE_CASE)

    private val BILL_KEYWORDS = listOf(
        "hóa đơn", "hoa don", "tiền điện", "tien dien", "tiền nước", "tien nuoc",
        "phiếu thu", "phieu thu", "thanh toán", "thanh toan", "khách hàng", "khach hang",
        "mã khách hàng", "ma khach hang", "tổng cộng", "tong cong", "tổng tiền", "tong tien",
        "invoice", "receipt", "billing", "payment"
    )

    /**
     * Evaluates confidence score (0.0 to 100.0) that a photo belongs to a specific category.
     * Pure function with deterministic guardrails balancing high recall and zero accidental spam.
     */
    fun evaluatePhotoConfidence(
        photo: Photo,
        category: Category,
        expandedConcepts: Set<String> = VisualConceptExpander.expandCategoryConcepts(category.name),
        ftsMatched: Boolean = false,
        vectorSimilarity: Float = 0f,
        neuralSimilarity: Float = 0f
    ): Float {
        val catName = category.name.trim()
        if (catName.isBlank()) return 0f

        val catLower = catName.lowercase(Locale.getDefault())
        val catUnaccented = VietnameseUtils.removeDiacritics(catLower)
        val catTokens = tokenize(catLower).filter { it.length >= 2 && it !in STOP_WORDS }
        val catUnaccentedTokens = catTokens.map { VietnameseUtils.removeDiacritics(it) }

        val isSingleShortWord = catTokens.size == 1 && catLower.length <= 5
        val isCompoundPhrase = catTokens.size >= 2
        val isTextDocCategory = TEXT_DOCUMENT_CATEGORY_KEYWORDS.any {
            catLower.contains(it) || catUnaccented.contains(VietnameseUtils.removeDiacritics(it))
        }

        var score = 0f
        var penalty = 0f

        // 1. Direct Target Category Match (Previously detected by ML/Heuristic)
        if (photo.categoryIds.contains(category.id)) {
            return 100f
        }

        val text = (photo.piiScrubbedText ?: photo.description).trim()
        val textLower = text.lowercase(Locale.getDefault())
        val textUnaccented = VietnameseUtils.removeDiacritics(textLower)
        val titleLower = photo.title.lowercase(Locale.getDefault())
        val titleUnaccented = VietnameseUtils.removeDiacritics(titleLower)
        val eventTitleLower = (photo.suggestedEventTitle ?: "").lowercase(Locale.getDefault())
        val eventTitleUnaccented = VietnameseUtils.removeDiacritics(eventTitleLower)

        val isChat = isChatScreenshot(text)

        // Extract header text (top 3 lines or first 120 chars) - only for non-chat documents
        val textLines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val headerLines = if (isChat) "" else textLines.take(3).joinToString(" ").lowercase(Locale.getDefault())
        val headerUnaccented = if (isChat) "" else VietnameseUtils.removeDiacritics(headerLines)
        val allTokensInHeader = isCompoundPhrase && catTokens.isNotEmpty() && catTokens.indices.all { i ->
            headerLines.contains(catTokens[i]) || headerUnaccented.contains(catUnaccentedTokens[i])
        }
        val isHeaderMatch = headerLines.isNotBlank() && catLower.length >= 2 && (
            headerLines.contains(catLower) || headerUnaccented.contains(catUnaccented) || allTokensInHeader
        )

        // 2. Title & Suggested Event Title Matching (High Prominence)
        val hasDirectTitleMatch = (catLower.length >= 3 && titleLower.contains(catLower)) ||
                (catUnaccented.length >= 3 && titleUnaccented.contains(catUnaccented)) ||
                (isCompoundPhrase && catTokens.any { token -> token.length >= 4 && (titleLower.contains(token) || titleUnaccented.contains(token)) }) ||
                VisualConceptExpander.isConceptMatch(titleLower, expandedConcepts) ||
                VisualConceptExpander.isConceptMatch(titleUnaccented, expandedConcepts)

        if (hasDirectTitleMatch) {
            score += 35f
        }

        val hasEventTitleMatch = eventTitleLower.isNotBlank() && (
                (catLower.length >= 3 && eventTitleLower.contains(catLower)) ||
                        (catUnaccented.length >= 3 && eventTitleUnaccented.contains(catUnaccented)) ||
                        VisualConceptExpander.isConceptMatch(eventTitleLower, expandedConcepts)
                )
        if (hasEventTitleMatch) {
            score += 30f
        }

        // 3. Text OCR Matching ($S_{Text}$)
        val textTokens = tokenize(textLower)
        val textUnaccentedTokens = tokenize(textUnaccented)

        if (textLower.isNotBlank()) {
            val fullTextMatch = textLower.contains(catLower) || textUnaccented.contains(catUnaccented)
            val exactWordOccurrences = textTokens.count { it == catLower }

            if (isCompoundPhrase) {
                // Compound phrases (e.g. "Hóa đơn điện", "Shopee Pay", "Vé máy bay")
                if (fullTextMatch) {
                    score += 40f
                    if (isHeaderMatch) score += 10f
                } else {
                    // Check flexible phrase proximity: all category tokens appear in the same line or in text
                    val allTokensInSameLine = textLines.any { line ->
                        val lineLower = line.lowercase(Locale.getDefault())
                        val lineUnacc = VietnameseUtils.removeDiacritics(lineLower)
                        catTokens.indices.all { i ->
                            lineLower.contains(catTokens[i]) || lineUnacc.contains(catUnaccentedTokens[i])
                        }
                    }

                    if (allTokensInSameLine || allTokensInHeader) {
                        score += 38f
                        if (isHeaderMatch) score += 10f
                    } else {
                        val allTokensInText = catTokens.indices.all { i ->
                            textTokens.contains(catTokens[i]) || textUnaccentedTokens.contains(catUnaccentedTokens[i])
                        }
                        if (allTokensInText) {
                            score += 30f
                            if (isHeaderMatch) score += 10f
                        } else {
                            val matchedCount = catTokens.indices.count { i ->
                                textTokens.contains(catTokens[i]) || textUnaccentedTokens.contains(catUnaccentedTokens[i])
                            }
                            if (matchedCount >= 2) {
                                val matchRatio = matchedCount.toFloat() / catTokens.size.toFloat()
                                score += if (matchRatio >= 0.5f) {
                                    (24f + (matchedCount * 4f)).coerceAtMost(32f)
                                } else {
                                    (matchedCount * 10f).coerceAtMost(25f)
                                }
                            }
                        }
                    }
                }
            } else if (isSingleShortWord) {
                // Single short words (e.g. "Cây", "Mèo", "Xe", "Gym", "Sách") require strict guardrails
                if (isHeaderMatch) {
                    score += 35f
                } else if (exactWordOccurrences >= 2) {
                    score += 30f
                } else if (exactWordOccurrences == 1 || fullTextMatch) {
                    // Single occurrence deep in body text: low initial score (requires concept or visual agreement)
                    score += 15f
                }
            } else {
                // Regular single words (> 5 chars)
                if (fullTextMatch) {
                    score += if (isHeaderMatch) 35f else 25f
                }
            }
        }

        // 4. Concept Expansion & Co-occurrence Matching ($S_{Concept}$)
        val visualTagsLower = photo.semanticTags.map { it.lowercase(Locale.getDefault()) }
        var matchedConceptCount = 0
        for (term in expandedConcepts) {
            if (term.length < 3 || term == catLower) continue
            val termUnacc = VietnameseUtils.removeDiacritics(term)
            val inTitle = titleLower.contains(term) || titleUnaccented.contains(termUnacc)
            val inText = textLower.contains(term) || textUnaccented.contains(termUnacc)
            val inVisual = visualTagsLower.any { it.contains(term) || it.contains(termUnacc) }
            if (inTitle || inText || inVisual) {
                matchedConceptCount++
            }
        }
        if (matchedConceptCount > 0) {
            score += (matchedConceptCount * 10f).coerceAtMost(25f)
            if (matchedConceptCount >= 2) {
                score += 5f // Co-occurrence bonus
            }
        }

        // 5. Visual Agreement Matching from ML Kit Vision ($S_{Visual}$)
        val visualMatches = visualTagsLower.filter { tag ->
            tag.contains(catLower) || catLower.contains(tag) ||
                    VisualConceptExpander.isConceptMatch(tag, expandedConcepts)
        }
        if (visualMatches.isNotEmpty()) {
            score += 20f
            // Strong visual evidence for visual-only photos (e.g. plants, animals, scenery)
            if (visualMatches.size >= 2 || textLower.isBlank()) {
                score += 15f
            }
        }

        // 6. Vector Similarity ($S_{Vector}$)
        val maxSim = maxOf(vectorSimilarity, neuralSimilarity)
        when {
            maxSim >= 0.55f -> score += 20f
            maxSim >= 0.40f -> score += 12f
            maxSim >= 0.28f -> score += 6f
        }

        // 7. FTS5 Indexing Bonus
        if (ftsMatched) {
            score += 10f
        }

        // 8. Contextual Guardrails & Penalties (Anti-False-Positive Engine)
        val hasPrice = PRICE_REGEX.containsMatchIn(text)
        val isBillOrReceipt = BILL_KEYWORDS.any { textLower.contains(it) || textUnaccented.contains(it) }

        // Guardrail A: Long news article with single incidental word in body text
        val isArticle = isLongArticle(text) && !isBillOrReceipt
        if (isArticle && !isHeaderMatch && visualMatches.isEmpty()) {
            if (!isTextDocCategory) {
                if (isSingleShortWord) {
                    // Single short word (e.g. "xe") in long article without header or visual match
                    penalty += 35f
                } else if (matchedConceptCount <= 1) {
                    penalty += 20f
                }
            }
        }

        // Guardrail B: Casual chat screenshot with incidental mention in chat bubbles
        if (isChat && visualMatches.isEmpty() && !hasPrice && !isBillOrReceipt) {
            // Mentioning coffee/food/drinks in casual conversation without prices, menus or receipts
            penalty += 35f
        }

        // Guardrail C: Conflict with strong distinct Intent (e.g. Banking receipt vs Learning material)
        val hasExplicitBankingIntent = (photo.semanticTags.contains("receipt") || photo.semanticTags.contains("banking")) &&
                (textLower.contains("giao dịch thành công") || textLower.contains("digital banking") || textLower.contains("chuyển khoản") || textLower.contains("số tiền"))
        val isNonFinancialCategory = category.id == DefaultCategories.READING || category.id == DefaultCategories.NATURE ||
                catLower.contains("học tập") || catLower.contains("thiên nhiên") || catLower.contains("sách") || catLower.contains("tài liệu")
        if (hasExplicitBankingIntent && isNonFinancialCategory && !hasDirectTitleMatch) {
            penalty += 40f
        }

        val finalScore = (score - penalty).coerceIn(0f, 100f)
        return finalScore
    }

    private fun isLongArticle(text: String): Boolean {
        if (text.length < 200) return false
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        return words.size >= 40
    }

    private fun isChatScreenshot(text: String): Boolean {
        val lower = text.lowercase(Locale.getDefault())
        val chatSignals = listOf("anh:", "em:", "bạn:", "hôm nay lúc", "đã gửi", "tin nhắn", "zalo", "messenger", "telegram", "đang hoạt động")
        return chatSignals.any { lower.contains(it) }
    }

    private fun tokenize(text: String): List<String> {
        return text.split(Regex("[\\s.,;:?!/\\-_()\\[\\]{}'\"]+"))
            .filter { it.length >= 2 }
    }

    suspend fun findMatchingPhotosForCategory(
        category: Category,
        photos: List<Photo>,
        dbHelper: AppDatabaseHelper? = null,
        apiKey: String? = null,
        minNeuralSimilarity: Float = 0.50f,
        minVectorSimilarity: Float = 0.35f,
        context: Context? = null,
        vectorDao: VectorDao? = dbHelper,
        ftsDao: FtsSearchDao? = dbHelper,
        queueDao: ScreenshotQueueDao? = dbHelper,
        geminiApiService: GeminiApiService? = null,
        isCloudAvailable: Boolean = !apiKey.isNullOrBlank()
    ): List<String> {
        val catName = category.name.trim()
        if (catName.isBlank() || photos.isEmpty()) return emptyList()

        val unassignedPhotos = photos.filter { category.id !in it.categoryIds }
        if (unassignedPhotos.isEmpty()) return emptyList()
        val unassignedIdSet = unassignedPhotos.map { it.id }.toSet()

        val matchedIds = mutableSetOf<String>()

        // 1. Level 1: 768D Cloud Neural Embedding (Google gemini-embedding-2 - Pro Tier)
        val neuralScores = mutableMapOf<String, Float>()
        if (isCloudAvailable && vectorDao != null) {
            try {
                val safeCatName = CloudPrivacyGateway.sanitizeCategoryName(catName)
                val catEmbedding = if (safeCatName.isNotBlank()) {
                    geminiApiService?.getEmbedding(safeCatName, apiKey ?: "")
                        ?: CloudAiBackendClient.getEmbedding(safeCatName, apiKey ?: "")
                } else {
                    null
                }
                if (catEmbedding != null) {
                    val allNeuralVectors = vectorDao.getAllPhotoNeuralVectors()
                        .filterKeys { it in unassignedIdSet }
                    if (allNeuralVectors.isNotEmpty()) {
                        val neuralResults = VectorSemanticSearchEngine.searchWithQueryVector(
                            queryVector = catEmbedding,
                            indexedVectors = allNeuralVectors,
                            minSimilarity = 0.30f
                        )
                        for ((id, sim) in neuralResults) {
                            neuralScores[id] = sim
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("RetroactiveCategorizer", "Neural vector search failed: ${e.message}", e)
            }
        }

        // 2. Level 2: On-device 128D Vector Embedding
        val vectorScores = mutableMapOf<String, Float>()
        if (vectorDao != null) {
            try {
                val onDeviceVectors = vectorDao.getAllPhotoVectors()
                    .filterKeys { it in unassignedIdSet }
                if (onDeviceVectors.isNotEmpty()) {
                    val vectorResults = VectorSemanticSearchEngine.search(
                        query = catName,
                        indexedVectors = onDeviceVectors,
                        customCategories = listOf(category),
                        minSimilarity = 0.25f
                    )
                    for ((id, sim) in vectorResults) {
                        vectorScores[id] = sim
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("RetroactiveCategorizer", "On-device vector search failed: ${e.message}", e)
            }
        }

        // 3. Level 3: FTS5 Lexical Search Matches
        val ftsMatches = if (ftsDao != null) {
            try {
                ftsDao.searchPhotosFts(catName, minScore = 2.0).toSet()
            } catch (_: Exception) {
                emptySet()
            }
        } else {
            emptySet()
        }

        // 4. Level 4: Concept Expansion
        val expandedConcepts = VisualConceptExpander.expandCategoryConcepts(catName)
        val cloudCandidateSnippets = mutableListOf<CategoryCandidateSnippet>()
        val localOnlyCandidateSnippets = mutableListOf<CategoryCandidateSnippet>()
        val visualLabelsByPhotoId = mutableMapOf<String, List<String>>()

        for (p in unassignedPhotos) {
            var effectivePhoto = p
            var visualRawLabels: List<String> = emptyList()

            // Fast On-Demand OCR for cold unprocessed photos (camera photos, downloaded images)
            if (effectivePhoto.piiScrubbedText.isNullOrBlank() && context != null) {
                try {
                    val ocrText = MlKitOcrEngine.recognizeText(context, effectivePhoto.filePath)
                    if (ocrText.isNotBlank()) {
                        val scrubbed = PiiScrubber.scrub(ocrText).scrubbedText
                        effectivePhoto = effectivePhoto.copy(piiScrubbedText = scrubbed)
                    }
                } catch (_: Exception) {}
            }

            // On-Demand Visual Labeling for legacy photos without tags
            if (effectivePhoto.semanticTags.isEmpty() && context != null) {
                try {
                    val onDemandVisual = LocalImageLabeler.analyze(context, effectivePhoto.filePath)
                    visualRawLabels = onDemandVisual.rawLabels
                    if (visualRawLabels.isNotEmpty()) {
                        visualLabelsByPhotoId[p.id] = visualRawLabels
                    }
                    if (onDemandVisual.vietnameseSemanticTags.isNotEmpty()) {
                        effectivePhoto = effectivePhoto.copy(semanticTags = onDemandVisual.vietnameseSemanticTags)
                    }
                } catch (_: Exception) {}
            }

            // Check if previously AI-analyzed and recorded in SQLite queue
            val cachedRecord = queueDao?.getProcessedScreenshot(p.id)
            if (cachedRecord != null && cachedRecord.targetCategoryId == category.id) {
                matchedIds.add(p.id)
                continue
            }

            val isFtsCandidate = p.id in ftsMatches || checkLexicalTokenMatch(effectivePhoto.piiScrubbedText ?: "", catName)
            val confidence = evaluatePhotoConfidence(
                photo = effectivePhoto,
                category = category,
                expandedConcepts = expandedConcepts,
                ftsMatched = isFtsCandidate,
                vectorSimilarity = vectorScores[p.id] ?: 0f,
                neuralSimilarity = neuralScores[p.id] ?: 0f
            )

            if (confidence >= MATCH_THRESHOLD) {
                matchedIds.add(p.id)
            } else if (confidence >= 18f || (effectivePhoto.piiScrubbedText?.isNotBlank() == true && isFtsCandidate)) {
                // Potential borderline candidate (bounded by MAX_CANDIDATE_LIMIT)
                if (cloudCandidateSnippets.size + localOnlyCandidateSnippets.size < MAX_CANDIDATE_LIMIT) {
                    val rawSnippet = effectivePhoto.piiScrubbedText
                        ?: PiiScrubber.scrub(effectivePhoto.description).scrubbedText
                    if (rawSnippet.isNotBlank()) {
                        val sanitized = CloudPrivacyGateway.prepareForAnalysis(
                            rawOrScrubbedText = rawSnippet,
                            photoTitle = p.title,
                            visualLabels = visualRawLabels
                        )
                        if (sanitized.decision == PrivacyDecision.CLOUD_ELIGIBLE) {
                            cloudCandidateSnippets.add(
                                CategoryCandidateSnippet(
                                    id = p.id,
                                    title = sanitized.scrubbedTitle,
                                    textSnippet = sanitized.scrubbedText
                                )
                            )
                        } else {
                            localOnlyCandidateSnippets.add(
                                CategoryCandidateSnippet(
                                    id = p.id,
                                    title = p.title,
                                    textSnippet = rawSnippet
                                )
                            )
                        }
                    }
                }
            }
        }

        // Local-first verification for LOCAL_ONLY sensitive documents (never leaves device)
        if (localOnlyCandidateSnippets.isNotEmpty()) {
            fallbackVerifyWithSmartHeuristic(category, localOnlyCandidateSnippets, unassignedPhotos, matchedIds, visualLabelsByPhotoId)
        }

        // Batch AI Verification for CLOUD_ELIGIBLE borderline candidates with SmartHeuristic fallback
        if (cloudCandidateSnippets.isNotEmpty()) {
            if (isCloudAvailable) {
                try {
                    val chunks = cloudCandidateSnippets.chunked(10)
                    for (chunk in chunks) {
                        val aiVerifiedIds = geminiApiService?.verifyCustomCategoryCandidates(
                            category = category,
                            candidates = chunk,
                            apiKey = apiKey ?: ""
                        ) ?: CloudAiBackendClient.verifyCustomCategoryCandidates(
                            category = category,
                            candidates = chunk,
                            apiKey = apiKey ?: ""
                        )
                        matchedIds.addAll(aiVerifiedIds)
                    }
                } catch (e: Exception) {
                    AppLogger.w("RetroactiveCategorizer", "AI candidate verification fallback to Heuristics: ${e.message}", e)
                    fallbackVerifyWithSmartHeuristic(category, cloudCandidateSnippets, unassignedPhotos, matchedIds, visualLabelsByPhotoId)
                }
            } else {
                // Local-first Fallback: When offline or unconfigured, use SmartHeuristicParser
                fallbackVerifyWithSmartHeuristic(category, cloudCandidateSnippets, unassignedPhotos, matchedIds, visualLabelsByPhotoId)
            }
        }

        // Return matched IDs preserving original photo order
        return unassignedPhotos.filter { it.id in matchedIds }.map { it.id }
    }

    private fun checkLexicalTokenMatch(text: String, catName: String): Boolean {
        if (text.isBlank() || catName.isBlank()) return false
        val catLower = catName.lowercase(Locale.getDefault()).trim()
        val catUnacc = VietnameseUtils.removeDiacritics(catLower)
        val textLower = text.lowercase(Locale.getDefault())
        val textUnacc = VietnameseUtils.removeDiacritics(textLower)
        if (textLower.contains(catLower) || textUnacc.contains(catUnacc)) return true
        val tokens = tokenize(catLower).filter { it.length >= 3 && it !in STOP_WORDS }
        return tokens.any { token ->
            val tokenUnacc = VietnameseUtils.removeDiacritics(token)
            textLower.contains(token) || textUnacc.contains(tokenUnacc)
        }
    }

    private fun fallbackVerifyWithSmartHeuristic(
        category: Category,
        candidateSnippets: List<CategoryCandidateSnippet>,
        photos: List<Photo>,
        matchedIds: MutableSet<String>,
        visualLabelsByPhotoId: Map<String, List<String>> = emptyMap()
    ) {
        val photoMap = photos.associateBy { it.id }
        for (candidate in candidateSnippets) {
            val photo = photoMap[candidate.id] ?: continue
            try {
                val labels = visualLabelsByPhotoId[candidate.id].orEmpty()
                val hasFace = labels.any {
                    it.equals("Person", ignoreCase = true) ||
                        it.equals("Face", ignoreCase = true) ||
                        it.equals("Selfie", ignoreCase = true) ||
                        it.equals("Portrait", ignoreCase = true)
                } || photo.semanticTags.any {
                    it.equals("chân dung", ignoreCase = true) ||
                        it.equals("khuôn mặt", ignoreCase = true) ||
                        it.equals("con người", ignoreCase = true)
                }
                val parsed = SmartHeuristicParser.parse(
                    scrubbedText = candidate.textSnippet,
                    photoTitle = candidate.title,
                    customCategories = listOf(category),
                    hasFace = hasFace,
                    visualLabels = labels
                )
                if (parsed.targetCategoryId == category.id) {
                    matchedIds.add(candidate.id)
                }
            } catch (_: Exception) {}
        }
    }
}
