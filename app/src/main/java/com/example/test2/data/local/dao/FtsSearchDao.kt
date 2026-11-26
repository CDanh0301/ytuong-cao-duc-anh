package com.example.test2.data.local.dao

/**
 * Data Access Object contract for FTS5/FTS4 full-text search indexing and querying.
 */
interface FtsSearchDao {
    fun indexPhotoFts(
        photoId: String,
        title: String,
        scrubbedText: String,
        intent: String = "",
        categoryNames: String = "",
        semanticTags: String = "",
        entities: String = "",
        summary: String = ""
    )

    fun removePhotoFts(photoId: String)

    fun searchPhotosFtsRanked(query: String): List<Pair<String, Double>>

    fun searchPhotosFts(query: String, minScore: Double = 0.0): List<String>
}
