package com.example.test2.data.local.dao

import android.content.ContentValues
import android.database.sqlite.SQLiteOpenHelper
import com.example.test2.shared.utils.VietnameseUtils

/**
 * SQLite FTS5/FTS4 implementation of [FtsSearchDao].
 */
class FtsSearchDaoImpl(
    private val dbProvider: SQLiteOpenHelper
) : FtsSearchDao {

    override fun indexPhotoFts(
        photoId: String,
        title: String,
        scrubbedText: String,
        intent: String,
        categoryNames: String,
        semanticTags: String,
        entities: String,
        summary: String
    ) {
        try {
            val db = dbProvider.writableDatabase
            db.delete(TABLE_FTS, "photo_id = ?", arrayOf(photoId))

            val normTitle = "$title ${VietnameseUtils.removeDiacritics(title)}"
            val normTags = "$semanticTags ${VietnameseUtils.removeDiacritics(semanticTags)}"
            val normEntities = "$entities ${VietnameseUtils.removeDiacritics(entities)}"
            val normSummary = "$summary ${VietnameseUtils.removeDiacritics(summary)}"
            val normText = "$scrubbedText ${VietnameseUtils.removeDiacritics(scrubbedText)}"
            val normCat = "$categoryNames ${VietnameseUtils.removeDiacritics(categoryNames)}"

            val values = ContentValues().apply {
                put("photo_id", photoId)
                put("title", normTitle)
                put("tags", normTags)
                put("entities", normEntities)
                put("summary", normSummary)
                put("scrubbed_text", normText)
                put("intent", intent)
                put("category_names", normCat)
            }
            db.insert(TABLE_FTS, null, values)
        } catch (_: Exception) {
        }
    }

    override fun removePhotoFts(photoId: String) {
        try {
            val db = dbProvider.writableDatabase
            db.delete(TABLE_FTS, "photo_id = ?", arrayOf(photoId))
        } catch (_: Exception) {
        }
    }

    override fun searchPhotosFtsRanked(query: String): List<Pair<String, Double>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val tokens = trimmed
            .replace(Regex("[^a-zA-Z0-9àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it.length >= 2 }

        if (tokens.isEmpty()) return emptyList()

        val termClauses = tokens.map { token ->
            val unacc = VietnameseUtils.removeDiacritics(token)
            val sanitized = token.replace("\"", "\"\"")
            val sanitizedUnacc = unacc.replace("\"", "\"\"")
            val usePrefix = sanitized.length >= 4
            val pattern1 = if (usePrefix) "\"$sanitized\"*" else "\"$sanitized\""
            val pattern2 = if (usePrefix) "\"$sanitizedUnacc\"*" else "\"$sanitizedUnacc\""
            if (sanitized != sanitizedUnacc) {
                "($pattern1 OR $pattern2)"
            } else {
                pattern1
            }
        }

        val andQuery = termClauses.joinToString(" AND ")
        val orQuery = termClauses.joinToString(" OR ")

        val results = mutableListOf<Pair<String, Double>>()
        try {
            val db = dbProvider.readableDatabase
            results.addAll(executeFtsMatch(db, andQuery))

            if (results.isEmpty() && tokens.size > 1) {
                results.addAll(executeFtsMatch(db, orQuery))
            }
        } catch (_: Exception) {
        }

        if (results.isEmpty()) {
            results.addAll(fallbackLikeSearch(query))
        }

        return results
    }

    private fun executeFtsMatch(db: android.database.sqlite.SQLiteDatabase, matchQuery: String): List<Pair<String, Double>> {
        val matches = mutableListOf<Pair<String, Double>>()
        val sql = "SELECT photo_id, bm25(photo_fts, 5.0, 10.0, 8.0, 3.0, 1.0, 1.0, 2.0) AS score FROM $TABLE_FTS WHERE $TABLE_FTS MATCH ? ORDER BY score ASC LIMIT 200"
        try {
            db.rawQuery(sql, arrayOf(matchQuery)).use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow("photo_id")
                val scoreCol = cursor.getColumnIndexOrThrow("score")
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idCol)
                    val rawBm25 = cursor.getDouble(scoreCol)
                    matches.add(id to -rawBm25)
                }
            }
        } catch (_: Exception) {
            try {
                db.rawQuery("SELECT photo_id FROM $TABLE_FTS WHERE $TABLE_FTS MATCH ? LIMIT 100", arrayOf(matchQuery)).use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow("photo_id")
                    var rank = 100.0
                    while (cursor.moveToNext()) {
                        matches.add(cursor.getString(idCol) to rank)
                        rank -= 0.5
                    }
                }
            } catch (_: Exception) {
            }
        }
        return matches
    }

    private fun fallbackLikeSearch(query: String): List<Pair<String, Double>> {
        val matches = mutableListOf<Pair<String, Double>>()
        val clean = query.trim()
        val unacc = VietnameseUtils.removeDiacritics(clean)
        try {
            val db = dbProvider.readableDatabase
            val pattern1 = "%$clean%"
            val pattern2 = "%$unacc%"
            val sql = "SELECT photo_id FROM $TABLE_FTS WHERE title LIKE ? OR tags LIKE ? OR entities LIKE ? OR summary LIKE ? OR scrubbed_text LIKE ? OR category_names LIKE ? OR title LIKE ? OR tags LIKE ? LIMIT 100"
            db.rawQuery(sql, arrayOf(pattern1, pattern1, pattern1, pattern1, pattern1, pattern1, pattern2, pattern2)).use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow("photo_id")
                var rank = 50.0
                while (cursor.moveToNext()) {
                    matches.add(cursor.getString(idCol) to rank)
                    rank -= 0.5
                }
            }
        } catch (_: Exception) {
        }
        return matches
    }

    override fun searchPhotosFts(query: String, minScore: Double): List<String> {
        val ranked = searchPhotosFtsRanked(query)
        val filtered = if (minScore > 0.0) {
            ranked.filter { it.second >= minScore }
        } else {
            ranked
        }
        return filtered.map { it.first }
    }

    companion object {
        const val TABLE_FTS = "photo_fts"
    }
}
