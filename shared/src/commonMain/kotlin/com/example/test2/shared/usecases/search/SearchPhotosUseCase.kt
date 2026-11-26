package com.example.test2.shared.usecases.search

import com.example.test2.shared.repository.IPhotoRepository
import com.example.test2.shared.usecases.SearchOutput
import com.example.test2.shared.usecases.SemanticSearchPort

/**
 * Use case for hybrid FTS5 + semantic vector search and vector index maintenance.
 */
class SearchPhotosUseCase(
    private val searchPort: SemanticSearchPort,
    private val repository: IPhotoRepository
) {
    suspend operator fun invoke(query: String): SearchOutput {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            repository.setSearchQuery("", null, null)
            return SearchOutput(matchingIds = null, dateRange = null, semanticMatchScores = emptyMap())
        }
        val result = searchPort.search(query)
        repository.setSearchQuery(query, result.matchingIds, dateRange = result.dateRange)
        return result
    }

    suspend fun ensureVectorsIndexed(forceReindex: Boolean = false) {
        searchPort.ensureVectorsIndexed(forceReindex)
    }
}
