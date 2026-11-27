package com.example.test2.data.remote

data class BatchPhotoItem(
    val photoId: String,
    val photoTitle: String,
    val scrubbedText: String,
    val hasFace: Boolean = false,
    val visualLabels: List<String> = emptyList()
)

data class ParsedScreenshotData(
    val detectedIntent: String,
    val suggestedEventTitle: String? = null,
    val suggestedEventDateMillis: Long? = null,
    val targetCategoryId: String? = null,
    val suggestedEventDescription: String? = null,
    val semanticTags: List<String> = emptyList(),
    val summary: String? = null,
    val entities: Map<String, String> = emptyMap(),
    val itemsToBuy: List<String> = emptyList(),
    val isCloudEnriched: Boolean = false
)

data class CategoryCandidateSnippet(
    val id: String,
    val title: String,
    val textSnippet: String
)
