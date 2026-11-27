package com.example.test2.ai

import android.content.Context
import com.example.test2.data.local.prefs.CloudAiDefaults
import com.example.test2.data.local.prefs.CloudAiPrefsDataSource
import com.example.test2.data.remote.CategoryCandidateSnippet
import com.example.test2.shared.models.Category
import com.example.test2.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Token Bucket Rate Limiter to strictly throttle calls to Gemini Free Tier.
 * Target: 12.5 RPM (minimum 4,800 ms interval between requests).
 * Supports adaptive exponential backoff on HTTP 429.
 */
object GeminiRateLimiter {
    private val mutex = Mutex()
    private var lastRequestTime = 0L
    private const val MIN_INTERVAL_MS = 4800L // 12.5 RPM -> 4.8s/req safe buffer
    private var backoffUntilMillis = 0L

    suspend fun acquire() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (backoffUntilMillis > now) {
                val waitTime = backoffUntilMillis - now
                delay(waitTime)
            }
            val afterBackoff = System.currentTimeMillis()
            val elapsed = afterBackoff - lastRequestTime
            if (elapsed < MIN_INTERVAL_MS) {
                delay(MIN_INTERVAL_MS - elapsed)
            }
            lastRequestTime = System.currentTimeMillis()
        }
    }

    fun reportRateLimitHit(retryAfterSeconds: Long = 15L) {
        val now = System.currentTimeMillis()
        val duration = (if (retryAfterSeconds > 0) retryAfterSeconds else 15L) * 1000L
        backoffUntilMillis = now + duration
        AppLogger.w("GeminiRateLimiter", "Rate limit 429 hit. Backing off for ${duration / 1000} seconds.")
    }
}

typealias BatchPhotoItem = com.example.test2.data.remote.BatchPhotoItem

object CloudAiBackendClient {

    private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 25_000

    private fun resolveEndpointUrl(
        modelName: String,
        action: String,
        customEndpoint: String = "",
        proxyBaseUrl: String = CloudAiDefaults.PROXY_BASE_URL
    ): String? {
        val candidate = when {
            customEndpoint.isNotBlank() -> customEndpoint.trim()
            proxyBaseUrl.isNotBlank() -> "${proxyBaseUrl.trim().trimEnd('/')}/${modelName.trim()}:$action"
            else -> "$GEMINI_BASE_URL/${modelName.trim()}:$action"
        }
        return if (CloudPrivacyGateway.isSafeHttpsUrl(candidate)) candidate else null
    }

    private fun openHttpsConnection(
        urlString: String,
        apiKey: String,
        method: String = "POST",
        connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = READ_TIMEOUT_MS
    ): HttpURLConnection {
        require(CloudPrivacyGateway.isSafeHttpsUrl(urlString)) {
            "Rejected non-HTTPS Cloud AI endpoint"
        }
        val url = URL(urlString)
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            if (method == "POST") {
                doOutput = true
            }
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            if (apiKey.isNotBlank()) {
                setRequestProperty("x-goog-api-key", apiKey.trim())
            }
        }
    }

    /**
     * Primary entry point for screenshot analysis:
     * Enforces [CloudPrivacyGateway] (fail-closed) before any remote request;
     * seamlessly falls back to [SmartHeuristicParser] if LOCAL_ONLY, offline, unconfigured, or on error.
     */
    suspend fun analyzeWithFallback(
        context: Context,
        scrubbedText: String,
        photoTitle: String,
        categories: List<Category>,
        isPro: Boolean = false,
        hasFace: Boolean = false,
        visualLabels: List<String> = emptyList()
    ): ParsedScreenshotData = withContext(Dispatchers.IO) {
        val config = CloudAiPrefsDataSource(context).getConfig()
        val sanitized = CloudPrivacyGateway.prepareForAnalysis(scrubbedText, photoTitle, visualLabels)

        if (sanitized.decision == PrivacyDecision.LOCAL_ONLY || !config.isCloudReady()) {
            return@withContext SmartHeuristicParser.parse(
                scrubbedText = scrubbedText,
                photoTitle = photoTitle,
                customCategories = if (isPro) categories else emptyList(),
                hasFace = hasFace,
                visualLabels = visualLabels
            )
        }

        try {
            GeminiRateLimiter.acquire()
            var result: ParsedScreenshotData? = null
            for (attempt in 0..1) {
                try {
                    result = callGeminiApi(
                        apiKey = config.apiKey,
                        modelName = config.modelName,
                        customEndpoint = config.customEndpoint,
                        proxyBaseUrl = config.proxyBaseUrl,
                        scrubbedText = sanitized.scrubbedText,
                        photoTitle = sanitized.scrubbedTitle,
                        categories = categories,
                        isPro = isPro,
                        hasFace = hasFace,
                        visualLabels = visualLabels
                    )
                    break
                } catch (e: Exception) {
                    if (e.message?.contains("429") == true && attempt == 0) {
                        AppLogger.w("CloudAiBackendClient", "Rate limit 429 encountered during single scan. Backing off 2.5s before retry...")
                        delay(2500L)
                        continue
                    } else {
                        throw e
                    }
                }
            }
            result ?: SmartHeuristicParser.parse(
                scrubbedText = scrubbedText,
                photoTitle = photoTitle,
                customCategories = if (isPro) categories else emptyList(),
                hasFace = hasFace,
                visualLabels = visualLabels
            )
        } catch (e: Exception) {
            AppLogger.e("CloudAiBackendClient", "Gemini analysis failed for model ${config.modelName}: ${e.message}. Falling back to SmartHeuristicParser.", e)
            // Graceful fallback to on-device heuristic parser
            SmartHeuristicParser.parse(
                scrubbedText = scrubbedText,
                photoTitle = photoTitle,
                customCategories = if (isPro) categories else emptyList(),
                hasFace = hasFace,
                visualLabels = visualLabels
            )
        }
    }

    /**
     * Calls Gemini Generative Language REST API (or HTTPS Backend Proxy) with PII-scrubbed text.
     * Guarantees that raw images and PII are NEVER sent over the network.
     */
    private fun callGeminiApi(
        apiKey: String,
        modelName: String,
        customEndpoint: String,
        proxyBaseUrl: String,
        scrubbedText: String,
        photoTitle: String,
        categories: List<Category>,
        isPro: Boolean = false,
        hasFace: Boolean = false,
        visualLabels: List<String> = emptyList()
    ): ParsedScreenshotData? {
        val sanitized = CloudPrivacyGateway.prepareForAnalysis(scrubbedText, photoTitle, visualLabels)
        if (sanitized.decision == PrivacyDecision.LOCAL_ONLY) {
            return null
        }

        val urlString = resolveEndpointUrl(
            modelName = modelName,
            action = "generateContent",
            customEndpoint = customEndpoint,
            proxyBaseUrl = proxyBaseUrl
        ) ?: throw IllegalArgumentException("Invalid or non-HTTPS Cloud AI endpoint")

        val effectiveCategories = if (categories.isNotEmpty()) categories else SmartHeuristicParser.DEFAULT_CATEGORIES
        val categoriesList = effectiveCategories.joinToString(", ") { "${it.id}: '${it.name}'" }
        val customCats = if (isPro) categories.filter { it.id !in SmartHeuristicParser.DEFAULT_CATEGORY_IDS } else emptyList()
        val customCatInstruction = if (customCats.isNotEmpty()) {
            val customCatStr = customCats.joinToString(", ") { "${it.id}: '${it.name}'" }
            "\n            CUSTOM USER CATEGORIES (PRO TIER): [$customCatStr]\n            PRIORITY RULE: If the screenshot content closely relates to any of these user-created custom categories, you MUST prioritize and assign 'target_category_id' to that custom category ID!"
        } else {
            ""
        }

        val systemInstruction = """
            You are an expert AI assistant that organizes screenshots and photos for users.
            Input text is OCR-extracted and pre-scrubbed of PII (placeholders such as [BANK_ACCOUNT], [CREDIT_CARD], [PHONE_NUMBER], [EMAIL_REDACTED], [ID_NUMBER], [PASSPORT_NO], [REDACTED_SECRET] indicate redacted tokens).
            NEVER copy bracketed redaction tokens like [BANK_ACCOUNT], [PHONE_NUMBER], or [EMAIL_REDACTED] into 'suggested_title', 'suggested_event_title', or 'summary'.
            Analyze the user intent from the text and title, and return a structured JSON response.
            
            ${buildCoreClassificationInstructions(categoriesList, customCatInstruction)}
            
            Respond ONLY with a JSON object in this exact schema:
            {
              "intent": "EVENT" | "SHOPPING" | "READING" | "PERSONAL" | "OTHER",
              "summary": "One-line concise summary in Vietnamese",
              "suggested_title": "Complete descriptive title under 60 chars",
              "suggested_event_title": "Clean, complete, meaningful title for calendar event or null",
              "suggested_event_description": "Comprehensive, structured bulleted description with complete sentences in Vietnamese or null",
              "semantic_tags": ["tag1", "tag2", "tag3", "tag4", "tag5", "tag6", "tag7", "tag8"],
              "entities": {
                "merchant": "Merchant or store name or null",
                "product": "Product name or model or null",
                "place": "Location or place or null"
              },
              "items_to_buy": ["item 1", "item 2"],
              "event_date_millis": 1740000000000 or null,
              "target_category_id": "category_id from available list or null"
            }
        """.trimIndent()

        val visualLabelsText = if (visualLabels.isNotEmpty()) "\nOn-Device Visual Labels (ML Kit): ${visualLabels.joinToString(", ")}" else ""
        val visualContext = if (hasFace) "\nVisual cue detected: Human face / portrait photo present in the image." else ""
        val promptText = """
            Original File Title: ${sanitized.scrubbedTitle}
            $visualLabelsText$visualContext
            Scrubbed Screenshot Text:
            ${sanitized.scrubbedText}
        """.trimIndent()

        val requestBody = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().apply { put("text", "$systemInstruction\n\n$promptText") })
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)

            val generationConfig = JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.1)
            }
            put("generationConfig", generationConfig)
        }

        val conn = openHttpsConnection(urlString = urlString, apiKey = apiKey)

        OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
            writer.write(requestBody.toString())
            writer.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            val errorStream = conn.errorStream ?: conn.inputStream
            val errorMsg = errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            AppLogger.e("CloudAiBackendClient", "Gemini API error ($responseCode) for model $modelName: $errorMsg")
            if (responseCode == 429) {
                GeminiRateLimiter.reportRateLimitHit()
            }
            throw RuntimeException("Gemini API error ($responseCode): $errorMsg")
        }

        val responseJsonString = conn.inputStream.bufferedReader().use { it.readText() }
        return parseGeminiResponse(
            responseJson = responseJsonString,
            scrubbedText = scrubbedText,
            defaultTitle = photoTitle,
            categories = effectiveCategories,
            isPro = isPro,
            hasFace = hasFace
        )
    }

    internal fun parseGeminiResponse(
        responseJson: String,
        scrubbedText: String,
        defaultTitle: String,
        categories: List<Category> = emptyList(),
        isPro: Boolean = false,
        hasFace: Boolean = false
    ): ParsedScreenshotData {
        return GeminiResponseParser.parseGeminiResponse(
            responseJson = responseJson,
            scrubbedText = scrubbedText,
            defaultTitle = defaultTitle,
            categories = categories,
            isPro = isPro,
            hasFace = hasFace
        )
    }

    internal fun parseSingleJsonObject(
        parsedJson: JSONObject,
        scrubbedText: String,
        defaultTitle: String,
        categories: List<Category> = emptyList(),
        isPro: Boolean = false,
        hasFace: Boolean = false
    ): ParsedScreenshotData {
        return GeminiResponseParser.parseSingleJsonObject(
            parsedJson = parsedJson,
            scrubbedText = scrubbedText,
            defaultTitle = defaultTitle,
            categories = categories,
            isPro = isPro,
            hasFace = hasFace
        )
    }

    /**
     * Batch Gemini Analysis: Analyzes 4-6 photos in a single HTTP request.
     * Enforces [CloudPrivacyGateway] (fail-closed) per item before any network transmission.
     */
    suspend fun analyzeBatchWithGemini(
        context: Context,
        items: List<BatchPhotoItem>,
        categories: List<Category>,
        isPro: Boolean = false
    ): Map<String, ParsedScreenshotData> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext emptyMap()
        val config = CloudAiPrefsDataSource(context).getConfig()

        // Defense-in-depth: sanitize and separate LOCAL_ONLY items from CLOUD_ELIGIBLE
        val eligibleItems = mutableListOf<BatchPhotoItem>()
        val localOnlyItems = mutableListOf<BatchPhotoItem>()
        for (item in items) {
            val sanitized = CloudPrivacyGateway.prepareForAnalysis(
                rawOrScrubbedText = item.scrubbedText,
                photoTitle = item.photoTitle,
                visualLabels = item.visualLabels
            )
            if (sanitized.decision == PrivacyDecision.CLOUD_ELIGIBLE) {
                eligibleItems.add(
                    item.copy(
                        photoTitle = sanitized.scrubbedTitle,
                        scrubbedText = sanitized.scrubbedText
                    )
                )
            } else {
                localOnlyItems.add(item)
            }
        }

        val localResults = localOnlyItems.associate { item ->
            item.photoId to SmartHeuristicParser.parse(
                scrubbedText = item.scrubbedText,
                photoTitle = item.photoTitle,
                customCategories = if (isPro) categories else emptyList(),
                hasFace = item.hasFace,
                visualLabels = item.visualLabels
            )
        }

        if (!config.isCloudReady() || eligibleItems.isEmpty()) {
            val remainingLocal = eligibleItems.associate { item ->
                item.photoId to SmartHeuristicParser.parse(
                    scrubbedText = item.scrubbedText,
                    photoTitle = item.photoTitle,
                    customCategories = if (isPro) categories else emptyList(),
                    hasFace = item.hasFace,
                    visualLabels = item.visualLabels
                )
            }
            return@withContext (localResults + remainingLocal)
        }

        try {
            GeminiRateLimiter.acquire()
            val cloudResults = callGeminiBatchApi(
                apiKey = config.apiKey,
                modelName = config.modelName,
                customEndpoint = config.customEndpoint,
                proxyBaseUrl = config.proxyBaseUrl,
                items = eligibleItems,
                categories = categories,
                isPro = isPro
            )
            localResults + cloudResults
        } catch (e: Exception) {
            AppLogger.e("CloudAiBackendClient", "Gemini batch analysis failed: ${e.message}", e)
            if (e.message?.contains("429") == true) {
                GeminiRateLimiter.reportRateLimitHit()
            }
            // Fallback independently for each item in the batch
            val fallbackMap = eligibleItems.associate { item ->
                item.photoId to SmartHeuristicParser.parse(
                    scrubbedText = item.scrubbedText,
                    photoTitle = item.photoTitle,
                    customCategories = if (isPro) categories else emptyList(),
                    hasFace = item.hasFace,
                    visualLabels = item.visualLabels
                )
            }
            localResults + fallbackMap
        }
    }

    internal fun buildCoreClassificationInstructions(
        categoriesList: String,
        customCatInstruction: String,
        isBatch: Boolean = false
    ): String {
        val specificInstructions = if (isBatch) {
            """
        - For SHOPPING (BATCH):
          * CRITICAL RULE: You MUST classify intent as 'SHOPPING' and 'target_category_id' as 'cat_tobuy' if the screenshot demonstrates retail shopping, e-commerce, commercial services, dining places, restaurants, cafes, food menus, or Google Maps venue cards.
          * 'suggested_event_title' MUST be the exact product name or restaurant/venue name (e.g., 'Tung\'s Kitchen Vietnamese Cuisine', 'Moo Beef Steak Trần Quốc Toản', or 'Nồi cơm nắp gài BlueStone 1.8 lít RCB-5527').
          * In 'entities', extract 'merchant' (store/platform/restaurant name), 'product' (product/dish/service name), and 'place' (address/location). Mentions of consultation/examination fees in medical schedules or tuition in school timetables DO NOT make it shopping!
          * 'suggested_event_description' MUST be a clean, concise 1-2 sentence description in Vietnamese summarizing the product or venue, key specs/cuisine, and price, or null.
        - For EVENT (BATCH):
          * CRITICAL RULE: You MUST classify intent as 'EVENT' and 'target_category_id' as 'cat_events' IF AND ONLY IF the document conveys a specific timetable, schedule, shift roster, appointment, flight, or calendar event.
          * In 'entities', you MUST extract 'datetime' (specific date/time) or 'place' (hospital, clinic, room, cinema, airport, or meeting platform). General news articles, editorials, weather reports, or chat messages talking about dates DO NOT qualify as events!
          * 'suggested_event_title' MUST be the specific, complete title of the event, movie, or meeting (e.g., 'Lịch chiếu phim Lotte Cinema' or 'Lịch khám bệnh Bệnh viện 19-8').
          * 'suggested_event_description' MUST be a clean, concise 1-2 sentence description in Vietnamese summarizing the event, time, and venue, or null.
        - For TARGET CATEGORY ID (BATCH MANDATORY):
          * 'target_category_id' MUST NEVER BE NULL OR EMPTY. You MUST pick the single most accurate category ID from the provided available categories list ([$categoriesList]) based on the strict Document Archetype rules above!
        - For SEMANTIC TAGS (BATCH):
          * 'semantic_tags' MUST be a JSON array of 3 to 5 natural, high-signal conceptual Vietnamese keywords/tags and synonyms.
            """.trimIndent()
        } else {
            """
        - For SHOPPING:
          * 'suggested_event_title' MUST be the exact, complete product name or restaurant/venue name (e.g., 'Tung\'s Kitchen Vietnamese Cuisine' or 'Google Tivi Sony True RGB BRAVIA 7 II 98').
          * 'suggested_event_description' MUST be a comprehensive, well-structured, fluent description in Vietnamese. NEVER leave it null or empty for shopping. Each bullet point MUST be a complete, meaningful sentence/phrase without truncation:
            🛒 Lịch mua sắm & Chi tiêu:
            • Tên / Thương hiệu: [Tên sản phẩm đầy đủ hoặc tên nhà hàng/quán cafe]
            • Thông số / Ẩm thực / Đặc điểm: [Thông số kỹ thuật chính hoặc loại hình ẩm thực, món ăn nổi bật]
            • Mức giá & Ưu đãi: [Mức giá chính xác, thông tin khuyến mãi/quà tặng nếu có]
            • Nơi bán / Địa chỉ: [Tên cửa hàng, sàn TMĐT hoặc địa chỉ quán hoàn chỉnh]
            • Ghi chú: Ghi chú nhắc nhở mua sắm, địa điểm từ ảnh chụp màn hình.
        - For EVENT:
          * 'suggested_event_title' MUST be the specific, complete title of the event, movie, or meeting (e.g., 'Lịch chiếu phim Lotte Cinema').
          * 'suggested_event_description' MUST be a comprehensive, well-structured, fluent description in Vietnamese:
            📅 Chi tiết sự kiện & Lịch hẹn:
            • Sự kiện: [Tên sự kiện / Phim / Cuộc họp đầy đủ]
            • Địa điểm / Rạp / Nền tảng: [Địa điểm, phòng chiếu hoặc link họp]
            • Thời gian / Suất chiếu: [Ngày và danh sách suất chiếu hoặc giờ cụ thể]
            • Chi tiết & Ghi chú: [Lưu ý vé, chuẩn bị hoặc nội dung quan trọng]
        - For SEMANTIC TAGS (Phase 2):
          * 'semantic_tags' MUST be a JSON array of 8 to 15 natural, conceptual Vietnamese keywords/tags and synonyms that a human user might search to find this image (e.g., ["đồ gia dụng", "nhà bếp", "nấu ăn", "cơm gia đình", "thiết bị điện", "bữa cơm", "nồi cơm điện", "blue stone"] for a rice cooker, or ["thời sự", "tin tức", "giao thông", "ùn tắc", "thời tiết", "đường phố", "đầu tuần", "báo chí", "hà nội"] for a news report).
            """.trimIndent()
        }

        return """
        Available user categories: [$categoriesList]$customCatInstruction
        Standard category IDs if none match custom:
        - cat_events (Meetings, flights, webinars, appointments, schedules)
        - cat_tobuy (Shopping, products, items to buy, merchandise, cups, invoices, receipts, prices)
        - cat_reading (Articles, blogs, news, editorial, traffic, weather, sports, press reports, book extracts, notes)
        - cat_nature (Nature, mountains, landscapes, hills, scenery, outdoors, travel, scenic wallpapers)
        - cat_personal (Personal IDs, certificates, documents, profiles, portraits, selfies, people, family)
        
        CRITICAL CLASSIFICATION RULES & DOCUMENT ARCHETYPES:
        1. Events & Schedules (cat_events):
           - Applies to any document whose primary purpose is to convey a schedule, timetable, or event.
           - Includes: Hospital/clinic examination schedules, doctor shift rosters, school/university class timetables, movie showtimes, concert/theater tickets, flight itineraries, boarding passes, webinars, calendar appointments, meeting invites.
           - CRITICAL: Mentions of personnel names (doctors, professors, speakers, staff), room numbers, or fees/co-pays (BHYT, tuition, consultation fees) are normal structural elements of timetables and DO NOT make the document a personal ID card or a shopping product! Classify intent as 'EVENT' and target_category_id as 'cat_events'.
        2. Commercial Goods, Venues, Dining & Services (cat_tobuy):
           - Applies to online e-commerce (Shopee, Lazada, Tiki, Amazon, TikTok Shop, etc.), physical retail stores, supermarkets, restaurants, eateries, cafes, bars, food stalls, hotels, spas, salons, and Google Maps place cards.
           - CRITICAL RULE: For any commercial merchant, eatery, or store: operating/business hours (e.g., 'Mở cửa 10:00 - 22:30', 'Đóng cửa vào 14:00 Mở lại vào 17:30'), menus, dish prices ('100.000 - 300.000 đ/người'), customer ratings/reviews ('4,9 ★', 'Bài đánh giá'), and map interaction buttons ('Đặt bàn', 'Xem thực đơn', 'Gọi điện', 'Chỉ đường') are commercial metadata -> ALWAYS classify intent as 'SHOPPING' and 'target_category_id' as 'cat_tobuy'.
           - Mentions of location/street names: When attached to a commercial merchant, store, eatery, or business, the address is a core commercial attribute and belongs to 'cat_tobuy'. However, mentions of administrative fees, consultation fees, or room addresses in clinic/school timetables DO NOT make it shopping: NEVER classify timetables or schedules as cat_tobuy!
           - EXCEPTION: Only classify as 'cat_events' if it is a confirmed reservation ticket with explicit booking date/time in the future.
        3. Personal Identity & Documents (cat_personal):
           - Applies to official personal identity credentials (CCCD, CMND, passport, driver's license, employee/student ID card, certificates, resumes, CVs) or prominent personal portrait photos/selfies/family albums.
           - CRITICAL: Staff duty rosters, doctor examination timetables, school class schedules, or hospital reception notices containing people's names or BHYT notes are NOT personal ID documents! Classify intent as 'OTHER' or 'PERSONAL', set 'target_category_id' to 'cat_personal', and set 'event_date_millis' to null and 'suggested_event_title' to null.
        4. Reading, News & Articles (cat_reading):
           - Applies to news articles, journalism press reports, traffic/weather updates, sports news, blog posts, editorials, or book chapters. Customer reviews or ratings on a store or dining location card DO NOT make it reading!
        5. Nature & Scenery (cat_nature):
           - STRICTLY for genuine outdoor nature, mountains, landscapes, hills, forests, lakes, rivers, beaches, scenic views, or wallpaper photography (e.g. National Geographic, Pexels, Unsplash).
        6. Support Vietnamese and English equally (e.g., 'lịch khám', 'bác sĩ', 'lịch làm việc', 'thời khóa biểu', 'chân dung', 'cá nhân', 'căn cước', 'núi', 'sản phẩm', 'lịch hẹn', 'chuyến bay', 'hóa đơn', 'bài viết').
        
        CRITICAL TITLE AND DESCRIPTION REQUIREMENTS (MANDATORY):
        - NEVER return generic UI navigation labels, status bar text, breadcrumbs, or buttons (such as 'MENU', 'Home', 'Trang chủ', 'Giỏ hàng', 'Hình ảnh', 'Chính sách', 'Đã bán', 'D Ngay dang', 'Quay lại', 'Chi tiết') as suggested_title or suggested_event_title!
        - All output text in 'suggested_event_title' and 'suggested_event_description' MUST be complete, natural, and grammatically fluent in Vietnamese. NEVER cut off words or leave sentences incomplete (e.g., do NOT truncate 'Hà Nội trọn gói' into 'Hà Nội tron', do NOT truncate 'Điện Tử Miền Bắc' into 'Điện Tử Miền').
        $specificInstructions
        - For ENTITIES & SUMMARY (Phase 2):
          * 'summary' MUST be a clean, factual one-line summary in Vietnamese.
          * 'entities' MUST extract merchant (brand/store/restaurant), product (name/dish/item), and place (location/address) if present.
          * 'items_to_buy' MUST be a list of distinct shopping items detected if intent is SHOPPING.
          * 'items_to_buy' MUST be a list of distinct shopping items detected if intent is SHOPPING.
    """.trimIndent()
    }

    private fun callGeminiBatchApi(
        apiKey: String,
        modelName: String,
        customEndpoint: String,
        proxyBaseUrl: String,
        items: List<BatchPhotoItem>,
        categories: List<Category>,
        isPro: Boolean = false
    ): Map<String, ParsedScreenshotData> {
        val sanitizedItems = items.mapNotNull { item ->
            val sanitized = CloudPrivacyGateway.prepareForAnalysis(item.scrubbedText, item.photoTitle, item.visualLabels)
            if (sanitized.decision == PrivacyDecision.CLOUD_ELIGIBLE) {
                item.copy(
                    photoTitle = sanitized.scrubbedTitle,
                    scrubbedText = sanitized.scrubbedText
                )
            } else {
                null
            }
        }
        if (sanitizedItems.isEmpty()) return emptyMap()

        val urlString = resolveEndpointUrl(
            modelName = modelName,
            action = "generateContent",
            customEndpoint = customEndpoint,
            proxyBaseUrl = proxyBaseUrl
        ) ?: throw IllegalArgumentException("Invalid or non-HTTPS Cloud AI endpoint")

        val effectiveCategories = if (categories.isNotEmpty()) categories else SmartHeuristicParser.DEFAULT_CATEGORIES
        val categoriesList = effectiveCategories.joinToString(", ") { "${it.id}: '${it.name}'" }
        val customCats = if (isPro) categories.filter { it.id !in SmartHeuristicParser.DEFAULT_CATEGORY_IDS } else emptyList()
        val customCatInstruction = if (customCats.isNotEmpty()) {
            val customCatStr = customCats.joinToString(", ") { "${it.id}: '${it.name}'" }
            "\n            CUSTOM USER CATEGORIES (PRO TIER): [$customCatStr]\n            PRIORITY RULE: If the screenshot content closely relates to any of these user-created custom categories, you MUST prioritize and assign 'target_category_id' to that custom category ID!"
        } else {
            ""
        }

        val delimitedItems = buildString {
            appendLine("INPUT SCREENSHOT ITEMS TO CLASSIFY (ANALYZE EACH BOUNDED ITEM IN STRICT ISOLATION):")
            for (item in sanitizedItems) {
                appendLine("<photo id=\"${item.photoId}\">")
                appendLine("Original File Title: ${item.photoTitle}")
                if (item.hasFace) {
                    appendLine("Visual Context: Human face / portrait photo detected in this image.")
                }
                if (item.visualLabels.isNotEmpty()) {
                    appendLine("On-Device Visual Labels (ML Kit): ${item.visualLabels.joinToString(", ")}")
                }
                appendLine("Scrubbed Screenshot Text:")
                appendLine(item.scrubbedText.take(4096))
                appendLine("</photo>")
                appendLine()
            }
        }

        val systemInstruction = """
            You are an expert AI assistant that organizes screenshots and photos for users.
            Input consists of screenshot items bounded between '<photo id="..."> ... </photo>'.
            Placeholders such as [BANK_ACCOUNT], [CREDIT_CARD], [PHONE_NUMBER], [EMAIL_REDACTED], [ID_NUMBER], [PASSPORT_NO], [REDACTED_SECRET] indicate redacted PII.
            NEVER copy bracketed redaction tokens like [BANK_ACCOUNT], [PHONE_NUMBER], or [EMAIL_REDACTED] into 'suggested_title', 'suggested_event_title', or 'summary'.
            For EACH bounded item in the input, analyze its content independently and return a structured JSON response matching its exact "id".
            
            CRITICAL ISOLATION DIRECTIVE:
            - Each bounded item MUST be analyzed in COMPLETE ISOLATION from other items in the batch.
            - NEVER allow keywords, merchant names, product prices, dates, or context from one item to influence the classification, intent, or extraction of any other item.
            
            ${buildCoreClassificationInstructions(categoriesList, customCatInstruction, isBatch = true)}

            OUTPUT FORMAT (MANDATORY):
            You MUST return a JSON object with an 'items' dictionary mapping each exact photo ID from input to its classification data:
            {
              "items": {
                "<exact_id_from_input>": {
                  "intent": "EVENT" | "SHOPPING" | "READING" | "PERSONAL" | "OTHER",
                  "suggested_title": "Complete descriptive title under 60 chars in Vietnamese",
                  "suggested_event_title": "Clean, complete, meaningful title for calendar event or restaurant/product name or null",
                  "suggested_event_description": "Clean, concise 1-2 sentence description in Vietnamese or null",
                  "summary": "Clean, factual one-line summary in Vietnamese",
                  "semantic_tags": ["tag1", "tag2", "tag3", "tag4"],
                  "entities": {
                    "merchant": "Merchant or restaurant or store name or null",
                    "product": "Product name or dish or null",
                    "place": "Location or street address or null"
                  },
                  "items_to_buy": ["item 1", "item 2"],
                  "event_date_millis": 1740000000000 or null,
                  "target_category_id": "category_id from available list (MANDATORY, NEVER NULL)"
                }
              }
            }
        """.trimIndent()

        val requestBody = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "$systemInstruction\n\n$delimitedItems")
                        })
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)
            val generationConfig = JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.1)
            }
            put("generationConfig", generationConfig)
        }

        val conn = openHttpsConnection(urlString = urlString, apiKey = apiKey)

        OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
            writer.write(requestBody.toString())
            writer.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            val errorStream = conn.errorStream ?: conn.inputStream
            val errorMsg = errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            if (responseCode == 429) {
                GeminiRateLimiter.reportRateLimitHit()
            }
            throw RuntimeException("Gemini API error ($responseCode): $errorMsg")
        }

        val responseJsonString = conn.inputStream.bufferedReader().use { it.readText() }
        return parseGeminiBatchResponse(
            responseJson = responseJsonString,
            items = sanitizedItems,
            categories = effectiveCategories,
            isPro = isPro
        )
    }

    internal fun parseGeminiBatchResponse(
        responseJson: String,
        items: List<BatchPhotoItem>,
        categories: List<Category> = emptyList(),
        isPro: Boolean = false
    ): Map<String, ParsedScreenshotData> {
        return GeminiResponseParser.parseGeminiBatchResponse(
            responseJson = responseJson,
            items = items,
            categories = categories,
            isPro = isPro
        )
    }

    /**
     * Calls Google gemini-embedding-2 batchEmbedContents API (or HTTPS Proxy) to generate multiple 768-dimensional neural vectors
     * in a SINGLE HTTP request. Enforces [CloudPrivacyGateway] per item.
     */
    suspend fun getBatchEmbeddings(
        texts: List<String>,
        apiKey: String = "",
        modelName: String = CloudAiDefaults.DEFAULT_EMBEDDING_MODEL,
        taskType: String = "RETRIEVAL_DOCUMENT",
        proxyBaseUrl: String = CloudAiDefaults.PROXY_BASE_URL
    ): List<FloatArray?> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()
        val effectiveApiKey = apiKey.trim()
        val effectiveProxyUrl = proxyBaseUrl.trim()
        if (effectiveProxyUrl.isBlank() && effectiveApiKey.isBlank()) {
            return@withContext texts.map { null }
        }

        // Defense-in-depth: scrub & separate LOCAL_ONLY items from CLOUD_ELIGIBLE while preserving original indices
        val eligibleTargets = mutableListOf<Pair<Int, String>>()
        for (i in texts.indices) {
            val check = CloudPrivacyGateway.prepareTextForEmbedding(texts[i])
            if (check.isMeaningfulForCloud) {
                eligibleTargets.add(i to check.scrubbedText)
            }
        }
        if (eligibleTargets.isEmpty()) return@withContext texts.map { null }

        val urlString = resolveEndpointUrl(
            modelName = modelName,
            action = "batchEmbedContents",
            proxyBaseUrl = effectiveProxyUrl
        ) ?: return@withContext texts.map { null }

        try {
            val requestsArray = JSONArray()
            for ((_, cleanScrubbed) in eligibleTargets) {
                val cleanText = cleanScrubbed.take(2048)
                val reqObj = JSONObject().apply {
                    put("model", "models/$modelName")
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            put(JSONObject().apply { put("text", cleanText.ifBlank { " " }) })
                        }
                        put("parts", partsArray)
                    }
                    put("content", contentObj)
                    put("outputDimensionality", 768)
                    if (taskType.isNotBlank()) {
                        put("taskType", taskType)
                    }
                }
                requestsArray.put(reqObj)
            }

            val requestBody = JSONObject().apply {
                put("requests", requestsArray)
            }

            val conn = openHttpsConnection(urlString = urlString, apiKey = effectiveApiKey)

            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                val errorStream = conn.errorStream ?: conn.inputStream
                val errorMsg = errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                AppLogger.e("CloudAiBackendClient", "batchEmbedContents failed ($responseCode) for model $modelName: $errorMsg")
                if (responseCode == 429) {
                    GeminiRateLimiter.reportRateLimitHit()
                }
                return@withContext texts.map { null }
            }

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val embeddingsArray = json.optJSONArray("embeddings") ?: return@withContext texts.map { null }

            val finalResult = arrayOfNulls<FloatArray>(texts.size).toMutableList()
            for ((reqOrder, targetPair) in eligibleTargets.withIndex()) {
                val origIdx = targetPair.first
                val embObj = embeddingsArray.optJSONObject(reqOrder)
                val valuesArray = embObj?.optJSONArray("values")
                if (valuesArray == null || valuesArray.length() == 0) {
                    continue
                }

                val vector = FloatArray(valuesArray.length())
                for (j in 0 until valuesArray.length()) {
                    vector[j] = valuesArray.optDouble(j, 0.0).toFloat()
                }

                // Normalize vector L2
                var sumSq = 0f
                for (v in vector) sumSq += v * v
                val norm = kotlin.math.sqrt(sumSq)
                if (norm > 0.00001f) {
                    for (k in vector.indices) vector[k] /= norm
                }
                finalResult[origIdx] = vector
            }

            finalResult
        } catch (e: Exception) {
            AppLogger.e("CloudAiBackendClient", "getBatchEmbeddings exception for model $modelName: ${e.message}", e)
            texts.map { null }
        }
    }

    /**
     * Calls Google gemini-embedding-2 API (or HTTPS Proxy) to generate a 768-dimensional neural vector.
     * Enforces [CloudPrivacyGateway] (fail-closed) before any network call.
     */
    suspend fun getEmbedding(
        text: String,
        apiKey: String = "",
        modelName: String = CloudAiDefaults.DEFAULT_EMBEDDING_MODEL,
        taskType: String = "RETRIEVAL_DOCUMENT",
        proxyBaseUrl: String = CloudAiDefaults.PROXY_BASE_URL
    ): FloatArray? = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        val effectiveApiKey = apiKey.trim()
        val effectiveProxyUrl = proxyBaseUrl.trim()
        if (trimmed.isBlank() || (effectiveProxyUrl.isBlank() && effectiveApiKey.isBlank())) {
            return@withContext null
        }

        // Privacy Gate Check via CloudPrivacyGateway
        val check = CloudPrivacyGateway.prepareTextForEmbedding(trimmed)
        if (!check.isMeaningfulForCloud) {
            return@withContext null
        }

        val cleanText = check.scrubbedText.take(2048) // Safe input size
        val urlString = resolveEndpointUrl(
            modelName = modelName,
            action = "embedContent",
            proxyBaseUrl = effectiveProxyUrl
        ) ?: return@withContext null

        try {
            GeminiRateLimiter.acquire()
            val requestBody = JSONObject().apply {
                put("model", "models/$modelName")
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().apply { put("text", cleanText) })
                    }
                    put("parts", partsArray)
                }
                put("content", contentObj)
                put("outputDimensionality", 768)
                if (taskType.isNotBlank()) {
                    put("taskType", taskType)
                }
            }

            val conn = openHttpsConnection(urlString = urlString, apiKey = effectiveApiKey)

            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                val errorStream = conn.errorStream ?: conn.inputStream
                val errorMsg = errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                AppLogger.e("CloudAiBackendClient", "getEmbedding failed ($responseCode) for model $modelName: $errorMsg")
                if (responseCode == 429) {
                    GeminiRateLimiter.reportRateLimitHit()
                }
                return@withContext null
            }

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val embeddingObj = json.optJSONObject("embedding")
                ?: json.optJSONArray("embeddings")?.optJSONObject(0)
            if (embeddingObj == null) {
                AppLogger.e("CloudAiBackendClient", "getEmbedding: Missing 'embedding' object in response: $responseText")
                return@withContext null
            }

            val valuesArray = embeddingObj.optJSONArray("values")
            if (valuesArray == null || valuesArray.length() == 0) {
                AppLogger.e("CloudAiBackendClient", "getEmbedding: Missing or empty 'values' array in response")
                return@withContext null
            }

            val vector = FloatArray(valuesArray.length())
            for (i in 0 until valuesArray.length()) {
                vector[i] = valuesArray.optDouble(i, 0.0).toFloat()
            }

            // Normalize vector L2
            var sumSq = 0f
            for (v in vector) sumSq += v * v
            val norm = kotlin.math.sqrt(sumSq)
            if (norm > 0.00001f) {
                for (i in vector.indices) vector[i] /= norm
            }
            vector
        } catch (e: Exception) {
            AppLogger.e("CloudAiBackendClient", "getEmbedding exception for model $modelName: ${e.message}", e)
            null
        }
    }

    /**
     * Targeted AI Verification for Custom Categories.
     * Evaluates a small pre-filtered batch of CLOUD_ELIGIBLE candidate photos against a user's custom category.
     * Enforces [CloudPrivacyGateway] (fail-closed) on category name, candidate titles, and text snippets.
     */
    suspend fun verifyCustomCategoryCandidates(
        category: Category,
        candidates: List<CategoryCandidateSnippet>,
        apiKey: String = "",
        modelName: String = CloudAiDefaults.DEFAULT_MODEL,
        proxyBaseUrl: String = CloudAiDefaults.PROXY_BASE_URL
    ): List<String> = withContext(Dispatchers.IO) {
        val effectiveApiKey = apiKey.trim()
        val effectiveProxyUrl = proxyBaseUrl.trim()
        if ((effectiveProxyUrl.isBlank() && effectiveApiKey.isBlank()) || candidates.isEmpty()) {
            return@withContext emptyList()
        }

        val safeCategoryName = CloudPrivacyGateway.sanitizeCategoryName(category.name)
        val safeCandidates = candidates.mapNotNull { candidate ->
            val sanitized = CloudPrivacyGateway.prepareForAnalysis(
                rawOrScrubbedText = candidate.textSnippet,
                photoTitle = candidate.title
            )
            if (sanitized.decision == PrivacyDecision.CLOUD_ELIGIBLE) {
                CategoryCandidateSnippet(
                    id = candidate.id,
                    title = sanitized.scrubbedTitle,
                    textSnippet = sanitized.scrubbedText
                )
            } else {
                null
            }
        }
        if (safeCandidates.isEmpty()) return@withContext emptyList()

        val urlString = resolveEndpointUrl(
            modelName = modelName,
            action = "generateContent",
            proxyBaseUrl = effectiveProxyUrl
        ) ?: return@withContext emptyList()

        val systemInstruction = """
            You are an expert AI photo organizer. The user has created a CUSTOM CATEGORY:
            ID: "${category.id}", Name: "$safeCategoryName"

            Analyze the candidate photos below (IDs, titles, and pre-scrubbed OCR text snippets).
            Determine which photo IDs genuinely belong to the user's custom category "$safeCategoryName".
            Understand true semantic intent, topic, brand, and purpose (e.g. if category is 'Shopee', include Shopee orders or packages; if 'Lego', include Lego bricks, sets, or builds).
            Do NOT include casual chat mentions, noise, or unrelated documents.

            Respond ONLY with a JSON object in this format:
            {
              "matched_photo_ids": ["photo_id_1", "photo_id_2"]
            }
        """.trimIndent()

        val candidatesText = buildString {
            append("User Custom Category: \"").append(safeCategoryName).append("\"\n\nCandidates:\n")
            for (c in safeCandidates) {
                append("[ID: ").append(c.id).append("] Title: ").append(c.title)
                append(" | Text: ").append(c.textSnippet.take(300).replace("\n", " "))
                append("\n")
            }
        }

        try {
            GeminiRateLimiter.acquire()
            val requestBody = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            put(JSONObject().apply { put("text", "$systemInstruction\n\n$candidatesText") })
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)

                val generationConfig = JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.1)
                }
                put("generationConfig", generationConfig)
            }

            val conn = openHttpsConnection(urlString = urlString, apiKey = effectiveApiKey)

            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val responseJsonString = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(responseJsonString)
                val candidatesJson = root.optJSONArray("candidates") ?: return@withContext emptyList()
                if (candidatesJson.length() == 0) return@withContext emptyList()
                val content = candidatesJson.getJSONObject(0).optJSONObject("content") ?: return@withContext emptyList()
                val parts = content.optJSONArray("parts") ?: return@withContext emptyList()
                if (parts.length() == 0) return@withContext emptyList()
                val textPart = parts.getJSONObject(0).optString("text", "")
                if (textPart.isNotBlank()) {
                    val parsedObj = JSONObject(textPart)
                    val matchedArray = parsedObj.optJSONArray("matched_photo_ids")
                    val matchedList = mutableListOf<String>()
                    if (matchedArray != null) {
                        for (i in 0 until matchedArray.length()) {
                            matchedList.add(matchedArray.getString(i))
                        }
                    }
                    return@withContext matchedList
                }
            }
            emptyList()
        } catch (e: Exception) {
            AppLogger.e("CloudAiBackendClient", "verifyCustomCategoryCandidates failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun testConnection(
        apiKey: String,
        modelName: String = CloudAiDefaults.DEFAULT_MODEL,
        customEndpoint: String = "",
        proxyBaseUrl: String = CloudAiDefaults.PROXY_BASE_URL
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() && customEndpoint.isBlank() && proxyBaseUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Cloud AI Proxy or API key is not configured."))
        }
        try {
            val urlString = resolveEndpointUrl(
                modelName = modelName,
                action = "generateContent",
                customEndpoint = customEndpoint,
                proxyBaseUrl = proxyBaseUrl
            ) ?: return@withContext Result.failure(IllegalArgumentException("Endpoint must use HTTPS."))

            val requestBody = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            put(JSONObject().apply { put("text", "Respond ONLY with JSON: {\"status\": \"ok\", \"model\": \"$modelName\"}") })
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                })
            }

            val conn = openHttpsConnection(
                urlString = urlString,
                apiKey = apiKey,
                connectTimeoutMs = 8_000,
                readTimeoutMs = 8_000
            )

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(requestBody.toString()) }

            val code = conn.responseCode
            if (code in 200..299) {
                Result.success("Connection Successful! Model '$modelName' is active and ready.")
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                val parsedMsg = try {
                    JSONObject(err).optJSONObject("error")?.optString("message") ?: err
                } catch (_: Exception) {
                    err
                }
                Result.failure(Exception("HTTP $code: $parsedMsg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches all available models directly from Google's models endpoint using the user's API key.
     * Automatically filters models that support generateContent.
     */
    suspend fun fetchAvailableModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter an API key first."))
        }
        try {
            val conn = openHttpsConnection(
                urlString = GEMINI_BASE_URL,
                apiKey = apiKey,
                method = "GET",
                connectTimeoutMs = 8_000,
                readTimeoutMs = 8_000
            )

            val code = conn.responseCode
            if (code in 200..299) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(responseText)
                val modelsArray = root.optJSONArray("models") ?: JSONArray()
                val modelsList = mutableListOf<String>()
                for (i in 0 until modelsArray.length()) {
                    val m = modelsArray.getJSONObject(i)
                    val methods = m.optJSONArray("supportedGenerationMethods")
                    var supportsGenerateContent = false
                    if (methods != null) {
                        for (j in 0 until methods.length()) {
                            if (methods.optString(j) == "generateContent") {
                                supportsGenerateContent = true
                                break
                            }
                        }
                    }
                    if (supportsGenerateContent) {
                        val name = m.optString("name").removePrefix("models/")
                        modelsList.add(name)
                    }
                }
                Result.success(modelsList.sorted())
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                val parsedMsg = try {
                    JSONObject(err).optJSONObject("error")?.optString("message") ?: err
                } catch (_: Exception) {
                    err
                }
                Result.failure(Exception("HTTP $code: $parsedMsg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
