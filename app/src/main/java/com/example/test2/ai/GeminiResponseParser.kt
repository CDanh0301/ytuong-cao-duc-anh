package com.example.test2.ai

import android.util.Log
import com.example.test2.database.DefaultCategories
import com.example.test2.shared.models.Category
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Parses single and batch JSON responses returned from Google Gemini models.
 * Handles Markdown code fence stripping, dynamic root array extraction,
 * fuzzy ID matching, and fallback to on-device heuristics.
 */
object GeminiResponseParser {

    private const val TAG = "GeminiResponseParser"

    fun parseGeminiResponse(
        responseJson: String,
        scrubbedText: String,
        defaultTitle: String,
        categories: List<Category> = emptyList(),
        isPro: Boolean = false,
        hasFace: Boolean = false
    ): ParsedScreenshotData {
        val customCats = if (isPro) categories else emptyList()
        fun fallback() = SmartHeuristicParser.parse(scrubbedText, defaultTitle, customCats, hasFace)

        val root = JSONObject(responseJson)
        val candidates = root.optJSONArray("candidates") ?: return fallback()
        if (candidates.length() == 0) return fallback()

        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.optJSONObject("content") ?: return fallback()
        val parts = content.optJSONArray("parts") ?: return fallback()
        if (parts.length() == 0) return fallback()

        val rawText = parts.getJSONObject(0).optString("text", "{}")
        var cleanRaw = rawText.trim()
        if (cleanRaw.startsWith("```json")) {
            cleanRaw = cleanRaw.removePrefix("```json").trim()
        } else if (cleanRaw.startsWith("```")) {
            cleanRaw = cleanRaw.removePrefix("```").trim()
        }
        if (cleanRaw.endsWith("```")) {
            cleanRaw = cleanRaw.removeSuffix("```").trim()
        }
        val parsedJson = JSONObject(cleanRaw)

        return parseSingleJsonObject(
            parsedJson = parsedJson,
            scrubbedText = scrubbedText,
            defaultTitle = defaultTitle,
            categories = categories,
            isPro = isPro,
            hasFace = hasFace
        )
    }

    fun parseSingleJsonObject(
        parsedJson: JSONObject,
        scrubbedText: String,
        defaultTitle: String,
        categories: List<Category> = emptyList(),
        isPro: Boolean = false,
        hasFace: Boolean = false
    ): ParsedScreenshotData {
        val customCats = if (isPro) categories else emptyList()
        val intent = parsedJson.optString("intent", "OTHER").uppercase(Locale.getDefault())
        val title = parsedJson.optString("suggested_title", defaultTitle).ifBlank { defaultTitle }
        val rawEventTitle = parsedJson.optString("suggested_event_title").let { if (it.isBlank() || it == "null") null else it }
        val rawDescription = parsedJson.optString("suggested_event_description").let { if (it.isBlank() || it == "null") null else it }
        val rawDateMillis = if (parsedJson.has("event_date_millis") && !parsedJson.isNull("event_date_millis")) {
            val rawMillis = parsedJson.optLong("event_date_millis", 0L)
            if (rawMillis > 0L) rawMillis else null
        } else {
            null
        }

        // Strict guardrail: ONLY genuine EVENT and SHOPPING can have scheduling
        var (finalDateMillis, finalEventTitle) = when (intent) {
            "EVENT" -> {
                Pair(rawDateMillis, rawEventTitle ?: if (rawDateMillis != null) title else null)
            }
            "SHOPPING" -> {
                Pair(rawDateMillis, rawEventTitle ?: title)
            }
            else -> {
                Pair(null, null)
            }
        }

        val allValidCategoryIds = (DefaultCategories.ALL_IDS + customCats.map { it.id }).toSet()
        var categoryId = parsedJson.optString("target_category_id").let { if (it.isBlank() || it == "null") null else it }

        // Symmetrically align Intent and Category to respect Gemini's contextual classification
        // Rule: If Gemini returned a valid category ID, preserve it 100%!
        // Only if categoryId is missing, empty, or invalid, derive a sensible fallback from intent.
        if (categoryId.isNullOrBlank() || categoryId !in allValidCategoryIds) {
            categoryId = when (intent) {
                "EVENT" -> DefaultCategories.EVENTS
                "SHOPPING" -> DefaultCategories.TO_BUY
                "READING" -> DefaultCategories.READING
                "PERSONAL" -> DefaultCategories.PERSONAL
                else -> SmartHeuristicParser.parse(scrubbedText, title, customCats, hasFace).targetCategoryId
            }
        }

        if (intent == "EVENT") {
            if (finalEventTitle.isNullOrBlank()) {
                finalEventTitle = SmartHeuristicParser.extractSmartTitle(scrubbedText, title, "EVENT")
            }
        } else if (intent == "SHOPPING") {
            if (finalEventTitle.isNullOrBlank()) {
                finalEventTitle = SmartHeuristicParser.extractSmartTitle(scrubbedText, title, "SHOPPING")
            }
        } else {
            finalDateMillis = null
            finalEventTitle = null
        }

        // Filter out any accidental UI navigation or OCR noise from the title
        val cleanEventTitle = if (finalEventTitle != null) {
            if (SmartHeuristicParser.isUiNoiseLine(finalEventTitle)) {
                SmartHeuristicParser.extractSmartTitle(scrubbedText, defaultTitle, intent)
            } else {
                finalEventTitle
            }
        } else {
            null
        }

        // Generate structured description if not provided or intent requires it
        val finalDescription = if (intent in listOf("EVENT", "SHOPPING")) {
            val validRawDesc = rawDescription?.takeIf {
                it.isNotBlank() && it.contains("•") && !it.contains("tron\n") && !it.endsWith("tron")
            }
            validRawDesc ?: SmartHeuristicParser.generateStructuredDescription(
                text = scrubbedText,
                intent = intent,
                title = cleanEventTitle ?: title
            )
        } else {
            null
        }

        // Extract semantic tags generated by Gemini for deep concept expansion
        val rawTags = parsedJson.optJSONArray("semantic_tags")
        val semanticTags = mutableListOf<String>()
        if (rawTags != null) {
            for (i in 0 until rawTags.length()) {
                val tag = rawTags.optString(i, "").trim().lowercase(Locale.getDefault())
                if (tag.isNotBlank() && tag !in semanticTags) {
                    semanticTags.add(tag)
                }
            }
        }

        // Phase 2: Extract Summary, Entities, and Items to Buy
        val summary = parsedJson.optString("summary").let { if (it.isBlank() || it == "null") null else it }

        val entitiesMap = mutableMapOf<String, String>()
        val rawEntities = parsedJson.optJSONObject("entities")
        if (rawEntities != null) {
            val keys = rawEntities.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = rawEntities.optString(key, "").trim()
                if (value.isNotBlank() && value != "null") {
                    entitiesMap[key] = value
                }
            }
        }

        val rawItems = parsedJson.optJSONArray("items_to_buy")
        val itemsToBuy = mutableListOf<String>()
        if (rawItems != null) {
            for (i in 0 until rawItems.length()) {
                val item = rawItems.optString(i, "").trim()
                if (item.isNotBlank() && item != "null") {
                    itemsToBuy.add(item)
                }
            }
        }

        return ParsedScreenshotData(
            detectedIntent = intent,
            suggestedEventTitle = cleanEventTitle,
            suggestedEventDateMillis = finalDateMillis,
            targetCategoryId = categoryId,
            suggestedEventDescription = finalDescription,
            semanticTags = semanticTags,
            summary = summary,
            entities = entitiesMap,
            itemsToBuy = itemsToBuy
        )
    }

    fun parseGeminiBatchResponse(
        responseJson: String,
        items: List<BatchPhotoItem>,
        categories: List<Category> = emptyList(),
        isPro: Boolean = false
    ): Map<String, ParsedScreenshotData> {
        val root = JSONObject(responseJson)
        val candidates = root.optJSONArray("candidates")
        val rawText = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text", "[]") ?: "[]"

        val resultMap = mutableMapOf<String, ParsedScreenshotData>()
        val itemMap = items.associateBy { it.photoId }
        val normalizedItemMap = items.associateBy { it.photoId.removePrefix("device_").removePrefix("photo_").trim() }
        val processedPhotoIds = mutableSetOf<String>()

        try {
            var cleanRaw = rawText.trim()
            if (cleanRaw.startsWith("```json")) {
                cleanRaw = cleanRaw.removePrefix("```json").trim()
            }
            if (cleanRaw.startsWith("```")) {
                cleanRaw = cleanRaw.removePrefix("```").trim()
            }
            if (cleanRaw.endsWith("```")) {
                cleanRaw = cleanRaw.removeSuffix("```").trim()
            }

            // Tier 1: Dynamic Array Extraction (Handles direct array or wrapped object with any root key)
            val jsonArray = when {
                cleanRaw.startsWith("[") -> JSONArray(cleanRaw)
                cleanRaw.startsWith("{") -> {
                    val obj = JSONObject(cleanRaw)
                    var foundArray: JSONArray? = obj.optJSONArray("items")
                        ?: obj.optJSONArray("results")
                        ?: obj.optJSONArray("data")
                        ?: obj.optJSONArray("screenshots")
                        ?: obj.optJSONArray("photos")

                    if (foundArray == null) {
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val candidate = obj.optJSONArray(keys.next())
                            if (candidate != null && candidate.length() > 0) {
                                foundArray = candidate
                                break
                            }
                        }
                    }
                    foundArray ?: JSONArray()
                }
                else -> JSONArray()
            }

            // Strict safety check: Positional Index Fallback is ONLY permitted when array lengths match 1:1
            val allowPositionalFallback = jsonArray.length() == items.size

            for (i in 0 until jsonArray.length()) {
                val itemObj = jsonArray.optJSONObject(i) ?: continue
                val rawId = itemObj.optString("id", "").trim()
                val normalizedId = rawId.removePrefix("device_").removePrefix("photo_").trim()

                // Tier 2: Exact Match -> Normalized ID Match -> Tier 3: Strict Positional Fallback
                val matchedItem = itemMap[rawId]
                    ?: normalizedItemMap[normalizedId]
                    ?: if (allowPositionalFallback) items[i] else null
                    ?: continue

                // Prevent duplicate assignment if model repeated an ID
                if (matchedItem.photoId in processedPhotoIds) {
                    continue
                }

                val parsed = parseSingleJsonObject(
                    parsedJson = itemObj,
                    scrubbedText = matchedItem.scrubbedText,
                    defaultTitle = matchedItem.photoTitle,
                    categories = categories,
                    isPro = isPro,
                    hasFace = matchedItem.hasFace
                )
                resultMap[matchedItem.photoId] = parsed
                processedPhotoIds.add(matchedItem.photoId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing batch JSON response: ${e.message}", e)
        }

        // Independent Fallback for any genuinely missing items
        for (item in items) {
            if (!resultMap.containsKey(item.photoId)) {
                resultMap[item.photoId] = SmartHeuristicParser.parse(
                    scrubbedText = item.scrubbedText,
                    photoTitle = item.photoTitle,
                    customCategories = if (isPro) categories else emptyList(),
                    hasFace = item.hasFace,
                    visualLabels = item.visualLabels
                )
            }
        }

        return resultMap
    }
}
