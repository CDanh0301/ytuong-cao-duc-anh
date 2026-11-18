package com.example.test2.ai

import android.content.Context
import com.example.test2.database.AppDatabaseHelper
import com.example.test2.services.CloudUpgradeWorker
import com.example.test2.shared.repository.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class SearchCoordinatorResult(
    val matchingIds: Set<String>?,
    val dateRange: LongRange?,
    val semanticMatchScores: Map<String, Float>
)

/**
 * Coordinates hybrid semantic search (Reciprocal Rank Fusion of FTS5 BM25 and vector cosine similarity)
 * and vector indexing state management out of the ViewModel.
 */
class SemanticSearchCoordinator(
    private val context: Context,
    private val repository: PhotoRepository,
    private val dbHelper: AppDatabaseHelper
) {
    // Phase 3: In-memory cache for query neural embeddings to prevent redundant network calls
    private val queryEmbeddingCache = ConcurrentHashMap<String, FloatArray>()

    companion object {
        const val CURRENT_VECTOR_CLUSTER_VERSION = 6
        const val NEURAL_VECTOR_MODEL_VERSION = "1.0"
        private const val RRF_K = 60.0
        private const val TOP_SCORE_CUTOFF_RATIO = 0.35
        private const val MIN_NEURAL_SIMILARITY = 0.30f
        private const val MIN_ON_DEVICE_SIMILARITY = 0.25f
    }

    suspend fun search(query: String): SearchCoordinatorResult = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return@withContext SearchCoordinatorResult(
                matchingIds = null,
                dateRange = null,
                semanticMatchScores = emptyMap()
            )
        }

        val parsedQuery = TemporalQueryParser.parse(trimmed)
        val cleanQuery = parsedQuery.cleanTextQuery
        val dateRange = parsedQuery.dateRangeMillis

        // Phase 5: Privacy hardening - Scrub query of any phone, card, account, or email numbers before cloud
        val scrubbedQuery = PiiScrubber.scrub(cleanQuery).scrubbedText

        // 1. FTS5 BM25 Ranked keyword search
        val ftsRankedList = if (cleanQuery.isNotBlank()) {
            dbHelper.searchPhotosFtsRanked(cleanQuery)
        } else {
            emptyList()
        }

        val ftsIds = ftsRankedList.map { it.first }
        val isPro = repository.accountTier.value.isPro

        if (isPro) {
            val apiKey = CloudAiConfigManager.getApiKey(context)
            val neuralVectors = dbHelper.getAllPhotoNeuralVectors()

            var vectorRankedList: List<Pair<String, Float>> = emptyList()

            // Pro cloud semantic embedding with RETRIEVAL_QUERY taskType and in-memory cache
            if (apiKey.isNotBlank() && scrubbedQuery.isNotBlank() && neuralVectors.isNotEmpty()) {
                var queryNeuralVec = queryEmbeddingCache[scrubbedQuery]
                if (queryNeuralVec == null) {
                    queryNeuralVec = CloudAiBackendClient.getEmbedding(
                        text = scrubbedQuery,
                        apiKey = apiKey,
                        modelName = CloudAiConfigManager.DEFAULT_EMBEDDING_MODEL,
                        taskType = "RETRIEVAL_QUERY"
                    )
                    if (queryNeuralVec != null) {
                        queryEmbeddingCache[scrubbedQuery] = queryNeuralVec
                    }
                }

                if (queryNeuralVec != null) {
                    vectorRankedList = VectorSemanticSearchEngine.searchWithQueryVector(
                        queryVector = queryNeuralVec,
                        indexedVectors = neuralVectors,
                        minSimilarity = MIN_NEURAL_SIMILARITY
                    )
                }
            }

            // Fallback to on-device 128D vector engine if cloud neural embedding wasn't available / offline
            if (vectorRankedList.isEmpty() && cleanQuery.isNotBlank()) {
                val allVectors = dbHelper.getAllPhotoVectors()
                val customCategories = repository.categories.value
                vectorRankedList = VectorSemanticSearchEngine.search(
                    query = cleanQuery,
                    indexedVectors = allVectors,
                    customCategories = customCategories,
                    minSimilarity = MIN_ON_DEVICE_SIMILARITY
                )
            }

            // Phase 3: Reciprocal Rank Fusion (RRF) to merge keyword (BM25) and vector cosine similarity ranks
            val allCandidateIds = (ftsIds + vectorRankedList.map { it.first }).distinct()
            val ftsRankMap = ftsIds.withIndex().associate { it.value to it.index + 1 }
            val vecRankMap = vectorRankedList.map { it.first }.withIndex().associate { it.value to it.index + 1 }

            val rrfScores = mutableListOf<Pair<String, Double>>()
            for (id in allCandidateIds) {
                val rFts = ftsRankMap[id]
                val rVec = vecRankMap[id]
                val ftsScorePart = if (rFts != null) 1.0 / (RRF_K + rFts) else 0.0
                val vecScorePart = if (rVec != null) 1.2 / (RRF_K + rVec) else 0.0
                rrfScores.add(id to (ftsScorePart + vecScorePart))
            }

            val sortedRrf = rrfScores.sortedByDescending { it.second }
            val topScore = sortedRrf.firstOrNull()?.second ?: 0.0
            val cutoff = topScore * TOP_SCORE_CUTOFF_RATIO
            val filteredRrf = sortedRrf.filter { it.second >= cutoff }
            val finalMatchingIds = filteredRrf.map { it.first }.toSet()

            // Normalize scores to [0.0..1.0] for UI badge
            val maxRrf = if (topScore > 0.0) topScore else 1.0
            val displayScores = filteredRrf.associate {
                it.first to (it.second / maxRrf).toFloat()
            }

            SearchCoordinatorResult(
                matchingIds = finalMatchingIds,
                dateRange = dateRange,
                semanticMatchScores = displayScores
            )
        } else {
            // Free Tier: FTS5 multi-term matching with BM25 ranking (no cloud quota cost)
            val finalMatchingIds = ftsIds.toSet()
            SearchCoordinatorResult(
                matchingIds = if (cleanQuery.isBlank() && dateRange != null) null else finalMatchingIds,
                dateRange = dateRange,
                semanticMatchScores = emptyMap()
            )
        }
    }

    suspend fun ensureVectorsIndexed(forceReindex: Boolean = false) = withContext(Dispatchers.IO) {
        val isPro = repository.accountTier.value.isPro
        if (!isPro) return@withContext

        val prefs = context.getSharedPreferences("vector_prefs", Context.MODE_PRIVATE)
        val currentClusterVersion = prefs.getInt("vector_cluster_version", 0)
        val mustReindex = forceReindex || currentClusterVersion < CURRENT_VECTOR_CLUSTER_VERSION

        val allVectors = if (mustReindex) emptyMap() else dbHelper.getAllPhotoVectors()
        val photos = repository.photos.value
        val customCategories = repository.categories.value

        for (p in photos) {
            if (mustReindex || !allVectors.containsKey(p.id)) {
                val catNames = customCategories
                    .filter { it.id in p.categoryIds }
                    .map { it.name }
                val semanticTagsStr = p.semanticTags.joinToString(" ")
                val fullText = buildString {
                    append(p.piiScrubbedText ?: p.description)
                    if (!p.suggestedEventTitle.isNullOrBlank()) append(" ").append(p.suggestedEventTitle)
                    if (semanticTagsStr.isNotBlank()) append(" ").append(semanticTagsStr)
                }.trim()

                val embedding = VectorSemanticSearchEngine.generateEmbedding(
                    text = fullText,
                    title = p.suggestedEventTitle ?: p.title,
                    intent = p.detectedIntent ?: "",
                    categoryNames = catNames,
                    customCategories = customCategories
                )
                dbHelper.savePhotoVector(p.id, embedding, p.detectedIntent ?: "")
            }
        }

        if (mustReindex) {
            prefs.edit().putInt("vector_cluster_version", CURRENT_VECTOR_CLUSTER_VERSION).apply()
        }

        // Phase 2: Backfill neural vectors on model version 1.0
        val missingBackfill = dbHelper.getPhotosNeedingNeuralVectorBackfill(NEURAL_VECTOR_MODEL_VERSION)
        if (missingBackfill.isNotEmpty()) {
            CloudUpgradeWorker.enqueue(context)
        }
    }
}
