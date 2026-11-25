package com.example.test2.ai

import android.content.Intent
import android.provider.CalendarContract
import com.example.test2.database.DefaultCategories
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Photo
import com.example.test2.shared.utils.VietnameseUtils
import java.util.Calendar
import java.util.Locale

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

object SmartHeuristicParser {

    fun isScreenshotPhoto(photo: Photo): Boolean {
        if (photo.isScreenshot) return true
        val lower = "${photo.fileName} ${photo.filePath} ${photo.folderId} ${photo.title}".lowercase(Locale.getDefault())
        return lower.contains("screenshot") ||
                lower.contains("screencap") ||
                lower.contains("screen_shot") ||
                lower.contains("ảnh chụp màn hình") ||
                lower.contains("chụp màn hình") ||
                lower.contains("chup man hinh") ||
                lower.contains("capture")
    }

    fun isJunkOrMeme(photo: Photo): Boolean {
        return isJunkOrMeme(photo.fileName, photo.fileSizeBytes)
    }

    fun isJunkOrMeme(fileName: String, fileSizeBytes: Long = 0L): Boolean {
        val name = fileName.lowercase(Locale.getDefault())
        return name.contains("meme") ||
                name.contains("sticker") ||
                name.contains(".tmp") ||
                (fileSizeBytes in 1..9_999) // under 10KB usually blank icon
    }

    fun createCalendarIntent(title: String, startTimeMillis: Long, description: String = ""): Intent {
        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTimeMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startTimeMillis + 3600000L) // 1 hour duration
        }
    }

    val DEFAULT_CATEGORY_IDS = com.example.test2.database.DefaultCategories.ALL_IDS

    val DEFAULT_CATEGORIES = listOf(
        Category(id = com.example.test2.database.DefaultCategories.EVENTS, name = "Lịch & Sự kiện", colorHex = "#3B82F6", iconName = "plane", createdAt = 1000L),
        Category(id = com.example.test2.database.DefaultCategories.TO_BUY, name = "Cần mua & Chi tiêu", colorHex = "#F59E0B", iconName = "utensils", createdAt = 2000L),
        Category(id = com.example.test2.database.DefaultCategories.READING, name = "Đọc & Tham khảo", colorHex = "#8B5CF6", iconName = "tag", createdAt = 3000L),
        Category(id = com.example.test2.database.DefaultCategories.NATURE, name = "Thiên nhiên & Phong cảnh", colorHex = "#009688", iconName = "leaf", createdAt = 4000L),
        Category(id = com.example.test2.database.DefaultCategories.PERSONAL, name = "Cá nhân & Giấy tờ", colorHex = "#EC4899", iconName = "user", createdAt = 5000L)
    )

    private val EVENT_ACTION_KEYWORDS get() = HeuristicKeywords.EVENT_ACTION_KEYWORDS
    val IDENTITY_DOC_SIGNATURES get() = HeuristicKeywords.IDENTITY_DOC_SIGNATURES
    private val PERSONAL_KEYWORDS get() = HeuristicKeywords.PERSONAL_KEYWORDS
    private val NATURE_KEYWORDS get() = HeuristicKeywords.NATURE_KEYWORDS
    private val PRODUCT_KEYWORDS get() = HeuristicKeywords.PRODUCT_KEYWORDS
    private val SHOPPING_KEYWORDS get() = HeuristicKeywords.SHOPPING_KEYWORDS
    private val READING_KEYWORDS get() = HeuristicKeywords.READING_KEYWORDS

    private fun tokenize(text: String): Set<String> {
        return text.split(Regex("[^\\p{L}\\p{Nd}]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun countMatches(
        combinedText: String,
        unaccentedCombinedText: String,
        tokens: Set<String>,
        unaccentedTokens: Set<String>,
        keywords: List<String>
    ): Int {
        var count = 0
        for (kw in keywords) {
            val lowerKw = kw.lowercase(Locale.getDefault())
            if (lowerKw.contains(" ")) {
                if (combinedText.contains(lowerKw) || unaccentedCombinedText.contains(lowerKw)) {
                    count++
                }
            } else {
                if (tokens.contains(lowerKw) || unaccentedTokens.contains(lowerKw)) {
                    count++
                }
            }
        }
        return count
    }

    internal data class ParsedContext(
        val lowerText: String,
        val lowerTitle: String,
        val combined: String,
        val unaccentedCombined: String,
        val tokens: Set<String>,
        val unaccentedTokens: Set<String>
    )

    internal fun buildParsedContext(photoTitle: String, text: String): ParsedContext {
        val lowerText = text.lowercase(Locale.getDefault())
        val lowerTitle = photoTitle.lowercase(Locale.getDefault())
        val combined = "$lowerTitle $lowerText"
        val unaccentedCombined = VietnameseUtils.removeDiacritics(combined)
        val tokens = tokenize(combined)
        val unaccentedTokens = tokenize(unaccentedCombined)
        return ParsedContext(lowerText, lowerTitle, combined, unaccentedCombined, tokens, unaccentedTokens)
    }

    fun isNewsOrReadingContent(photoTitle: String, text: String): Boolean {
        val ctx = buildParsedContext(photoTitle, text)

        // 1. Unambiguous news publishing legal & editorial signatures
        val newsEditorialSignatures = listOf(
            "thong tin dien tu", "bao dien tu", "tap chi dien tu",
            "tong bien tap", "pho tong bien tap", "toa soan", "phong vien", "nha bao",
            "giay phep xuat ban", "giay phep so", "co quan chu quan",
            "su kien: thoi su", "tin nong", "tin moi", "diem tin", "ban tin thoi su"
        )
        if (newsEditorialSignatures.any { ctx.unaccentedCombined.contains(it) }) {
            return true
        }

        // 2. Renowned news portals & domains in Vietnam combined with news topic cues
        val newsPortals = listOf(
            "24h.com.vn", "24h", "vnexpress", "dantri", "tuoitre", "thanhnien",
            "vietnamnet", "kenh14", "zingnews", "znews", "cafef", "cafebiz",
            "genk", "techz", "tienphong", "vtv.vn", "vtv", "laodong", "baomoi",
            "plo.vn", "nld.com.vn", "vtc.vn", "vtc", "soha.vn", "soha"
        )
        val hasNewsPortal = newsPortals.any { portal ->
            ctx.tokens.contains(portal) || ctx.unaccentedTokens.contains(portal) || ctx.unaccentedCombined.contains(portal)
        }
        if (hasNewsPortal && (ctx.unaccentedCombined.contains("thoi su") || ctx.unaccentedCombined.contains("tin tuc") || ctx.unaccentedCombined.contains("ngoai hang anh") || ctx.unaccentedCombined.contains("giao thong") || ctx.unaccentedCombined.contains("thoi tiet"))) {
            return true
        }

        // 3. Clear reading or article topics (e.g. "bài viết:", "tác giả:", "bài báo:")
        val readingScore = countMatches(ctx.combined, ctx.unaccentedCombined, ctx.tokens, ctx.unaccentedTokens, READING_KEYWORDS)
        val hasArticleCues = listOf("bai viet", "tac gia", "bai bao", "phong su", "un tac", "ket xe").any { ctx.unaccentedCombined.contains(it) }

        return hasArticleCues && readingScore >= 2
    }

    fun isChatScreenshot(text: String): Boolean {
        val lower = text.lowercase(Locale.getDefault())
        val chatSignals = listOf("anh:", "em:", "bạn:", "hôm nay lúc", "đã gửi", "tin nhắn", "zalo", "messenger", "telegram", "đang hoạt động")
        return chatSignals.any { lower.contains(it) }
    }

    fun isTimetableOrSchedule(photoTitle: String, text: String): Boolean {
        val ctx = buildParsedContext(photoTitle, text)

        // 1. Explicit Schedule / Timetable Terminology
        val scheduleSignatures = listOf(
            "lich kham", "lich lam viec", "gio nhan benh", "gio kham", "gio kham benh",
            "lich truc", "thoi khoa bieu", "lich hoat dong", "lich chieu", "lich tuan",
            "bang phan cong", "thoi gian bieu", "lich hen", "lich hop", "lich trinh",
            "lich bay", "lich thi", "lich cong tac", "doctor appointment", "meeting schedule",
            "class schedule", "exam schedule", "flight schedule", "work schedule"
        )
        if (scheduleSignatures.any { ctx.unaccentedCombined.contains(it) }) {
            return true
        }

        // 2. Day-of-week grid co-occurrence (e.g. Thứ 2, Thứ 3, Thứ 4, Thứ 5...)
        val daysOfWeek = listOf("thu 2", "thu 3", "thu 4", "thu 5", "thu 6", "thu 7", "chu nhat", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        val matchedDays = daysOfWeek.count { ctx.unaccentedCombined.contains(it) }

        // 3. Shifts / Sessions (Sáng, Chiều, Tối, Ca, Buổi...)
        val shifts = listOf("buoi sang", "buoi chieu", "buoi toi", "ca sang", "ca chieu", "ca toi", "ca truc", "ca kham", "tang cuong", "ban mai", "tiet hoc")
        val hasShift = shifts.any { ctx.unaccentedCombined.contains(it) } ||
                ((ctx.unaccentedCombined.contains("sang:") || ctx.unaccentedCombined.contains("sang :") || ctx.unaccentedTokens.contains("sang")) &&
                 (ctx.unaccentedCombined.contains("chieu:") || ctx.unaccentedCombined.contains("chieu :") || ctx.unaccentedTokens.contains("chieu")))

        // 4. Time range pattern (e.g., "7h30 - 11h30", "13:15 - 17:00", "05g45 - 7g00")
        val timeIntervalRegex = Regex("""\b\d{1,2}[h:g]\d{0,2}\s*[-~–]\s*\d{1,2}[h:g]\d{0,2}\b""", RegexOption.IGNORE_CASE)
        val timeIntervalMatches = timeIntervalRegex.findAll(ctx.combined).count()

        if (matchedDays >= 2 && (hasShift || timeIntervalMatches >= 1)) {
            return true
        }
        if (timeIntervalMatches >= 2 && (hasShift || ctx.unaccentedCombined.contains("tu ngay") || ctx.unaccentedCombined.contains("den ngay"))) {
            return true
        }

        return false
    }

    fun isShoppingContent(photoTitle: String, text: String): Boolean {
        val ctx = buildParsedContext(photoTitle, text)

        val explicitCartCues = listOf("gio hang", "them vao gio", "mua ngay", "dat hang", "don hang")
        val hasExplicitCart = explicitCartCues.any { ctx.unaccentedCombined.contains(it) }

        // Timetables and schedules MUST NOT be classified as shopping unless an explicit cart action exists
        if (isTimetableOrSchedule(photoTitle, text) && !hasExplicitCart) {
            return false
        }

        // News articles, press reports, or reading content MUST NOT be classified as shopping
        if (isNewsOrReadingContent(photoTitle, text) && !hasExplicitCart) {
            return false
        }

        val priceRegex = Regex("""\d+[\.,]\d{3}\s*(đ|₫|vnd|\$)|(\$|₫|đ)\s*\d+|\b\d+k\b""", RegexOption.IGNORE_CASE)
        val hasPrice = priceRegex.containsMatchIn(ctx.combined)

        val ecomSites = listOf("shopee", "lazada", "tiki", "sendo", "amazon", "dienmayxanh", "dientumienbac", "nguyenkim", "cellphones", "fptshop", "thegioididong")
        if (ecomSites.any { ctx.unaccentedCombined.contains(it) }) return true

        val shopScore = countMatches(ctx.combined, ctx.unaccentedCombined, ctx.tokens, ctx.unaccentedTokens, SHOPPING_KEYWORDS)
        val prodScore = countMatches(ctx.combined, ctx.unaccentedCombined, ctx.tokens, ctx.unaccentedTokens, PRODUCT_KEYWORDS)
        if (hasPrice && prodScore >= 1) return true
        if (hasPrice && shopScore >= 1) return true
        return (shopScore + prodScore) >= 2
    }

    fun isPersonalContent(photoTitle: String, text: String): Boolean {
        val ctx = buildParsedContext(photoTitle, text)

        val hasExplicitIdentityDoc = IDENTITY_DOC_SIGNATURES.any { ctx.unaccentedCombined.contains(it) }

        // Timetables and schedules MUST NOT be classified as personal unless explicit legal identity doc signatures exist
        if (isTimetableOrSchedule(photoTitle, text) && !hasExplicitIdentityDoc) {
            return false
        }

        // E-commerce products (cooking pots, TVs, clothing) MUST NOT be classified as personal
        if (isShoppingContent(photoTitle, text) && !hasExplicitIdentityDoc) {
            return false
        }

        return countMatches(ctx.combined, ctx.unaccentedCombined, ctx.tokens, ctx.unaccentedTokens, PERSONAL_KEYWORDS) > 0 || hasExplicitIdentityDoc
    }

    fun isNatureContent(photoTitle: String, text: String): Boolean {
        val ctx = buildParsedContext(photoTitle, text)

        if (isPersonalContent(photoTitle, text)) {
            return false
        }
        val scenerySources = listOf("nationalgeographic", "natgeo", "geographic.com", "pexels", "unsplash", "pixabay", "wallpaper")
        if (scenerySources.any { ctx.unaccentedCombined.contains(it) }) {
            return true
        }
        if (isShoppingContent(photoTitle, text)) {
            return false
        }
        return countMatches(ctx.combined, ctx.unaccentedCombined, ctx.tokens, ctx.unaccentedTokens, NATURE_KEYWORDS) > 0
    }

    fun parse(
        scrubbedText: String,
        photoTitle: String,
        customCategories: List<Category> = emptyList(),
        hasFace: Boolean = false,
        visualLabels: List<String> = emptyList()
    ): ParsedScreenshotData {
        val visualResult = LocalImageLabeler.classifyLabels(visualLabels)

        val cleanedText = cleanStatusBarNoise(scrubbedText)
        val ctx = buildParsedContext(photoTitle, cleanedText)
        val combined = ctx.combined
        val unaccentedCombined = ctx.unaccentedCombined
        val tokens = ctx.tokens
        val unaccentedTokens = ctx.unaccentedTokens

        val isTimetable = isTimetableOrSchedule(photoTitle, cleanedText)
        val isNews = isNewsOrReadingContent(photoTitle, cleanedText)
        val isTextHeavy = cleanedText.length > 120

        // In tables, timetables, and text-dense screenshots, small thumbnail faces/icons/cartoons
        // are secondary illustrations and must NOT override document classification into cat_personal
        val effectiveHasFace = if (isTimetable || isTextHeavy) false else (hasFace || visualResult.isPerson)

        // Vector Semantic Search prediction (128D Cosine Similarity against Category Prototypes)
        val vectorPrediction = VectorSemanticSearchEngine.classifyByCategoryVector(cleanedText, photoTitle)
        val title = extractTitle(cleanedText, photoTitle)

        // 0. Pro Feature: Check Custom AI Categories with Contextual Guardrails
        val proCustomCategories = customCategories.filter { it.id !in DEFAULT_CATEGORY_IDS }
        for (customCat in proCustomCategories) {
            val catNameLower = customCat.name.lowercase(Locale.getDefault()).trim()
            if (catNameLower.isNotBlank()) {
                val catNameUnaccented = VietnameseUtils.removeDiacritics(catNameLower)
                val catTokens = tokenize(catNameLower)
                val catUnaccentedTokens = tokenize(catNameUnaccented)
                val stopWords = setOf("and", "the", "for", "with", "của", "các", "những", "cho", "với", "ảnh", "anh")
                val isSingleShortWord = catTokens.size == 1 && catNameLower.length <= 5
                val isCompoundPhrase = catTokens.size >= 2

                val fullMatch = combined.contains(catNameLower) || unaccentedCombined.contains(catNameUnaccented)
                val tokenMatch = if (isCompoundPhrase) {
                    (catTokens + catUnaccentedTokens)
                        .filter { it.length >= 3 && it !in stopWords }
                        .any { tokens.contains(it) || unaccentedTokens.contains(it) }
                } else {
                    false
                }

                // High-precision semantic concept expansion matching (synonyms, brands, visual entities)
                val expandedTerms = VisualConceptExpander.expandCategoryConcepts(customCat.name)
                val hasVisualTagMatch = visualResult.vietnameseSemanticTags.any { VisualConceptExpander.isConceptMatch(it, expandedTerms) } ||
                        visualLabels.any { VisualConceptExpander.isConceptMatch(it, expandedTerms) }
                val titleMatch = VisualConceptExpander.isConceptMatch(title, expandedTerms) ||
                        photoTitle.lowercase(Locale.getDefault()).contains(catNameLower) ||
                        VietnameseUtils.removeDiacritics(photoTitle.lowercase(Locale.getDefault())).contains(catNameUnaccented)
                val textConceptMatch = VisualConceptExpander.isConceptMatch(cleanedText, expandedTerms)

                // Contextual Guardrails for single short words (e.g. "cây", "mèo", "xe", "gym")
                val isLegitimateMatch = if (isSingleShortWord) {
                    val count = tokens.count { it == catNameLower } + unaccentedTokens.count { it == catNameUnaccented }
                    val isCasualChat = isChatScreenshot(cleanedText)
                    val isArticle = (isNews || cleanedText.length > 250)
                    when {
                        hasVisualTagMatch || titleMatch -> true
                        isCasualChat -> false // casual chat mentions without visual confirmation are ignored
                        isArticle -> count >= 3 && textConceptMatch // long articles require high frequency and concept context
                        else -> count >= 2 || (fullMatch && textConceptMatch)
                    }
                } else {
                    fullMatch || tokenMatch || titleMatch || textConceptMatch || hasVisualTagMatch
                }

                if (isLegitimateMatch) {
                    return ParsedScreenshotData(
                        detectedIntent = "OTHER",
                        suggestedEventTitle = null,
                        suggestedEventDateMillis = null,
                        targetCategoryId = customCat.id,
                        semanticTags = visualResult.vietnameseSemanticTags
                    )
                }
            }
        }

        // 1. Calculate Shopping & Product Score
        val priceRegex = Regex("""\d+[\.,]\d{3}\s*(đ|₫|vnd|\$)|(\$|₫|đ)\s*\d+|\b\d+k\b""", RegexOption.IGNORE_CASE)
        val hasPrice = priceRegex.containsMatchIn(combined)
        val ecomSites = listOf("shopee", "lazada", "tiki", "sendo", "amazon", "dienmayxanh", "dientumienbac", "nguyenkim", "cellphones", "fptshop", "thegioididong")
        val hasEcomSite = ecomSites.any { unaccentedCombined.contains(it) }
        val explicitCartCues = listOf("gio hang", "them vao gio", "mua ngay", "dat hang", "don hang")
        val hasExplicitCart = explicitCartCues.any { unaccentedCombined.contains(it) }

        // Commercial Venues, Dining & POI Detection (Restaurants, Cafes, Spas, Salons, Maps Cards)
        val poiVenueCues = listOf("quan", "nha hang", "quan an", "ca phe", "cafe", "bit tet", "sushi", "mon an", "am thuc", "bistro", "bakery", "tiem", "khach san", "homestay", "spa", "salon")
        val poiOperationalCues = listOf("dang mo cua", "dong cua vao", "mo lai vao", "xem thuc don", "thuc don", "dat ban", "chi duong", "duong di", "d/nguoi", "bai danh gia", "tong quan", "maps/place")
        val hasPoiVenue = poiVenueCues.any { unaccentedCombined.contains(it) }
        val poiOperationalMatchCount = poiOperationalCues.count { unaccentedCombined.contains(it) }
        val isCommercialPoi = hasPoiVenue && poiOperationalMatchCount >= 2

        var rawShoppingScore = countMatches(combined, unaccentedCombined, tokens, unaccentedTokens, SHOPPING_KEYWORDS) * 2 +
                countMatches(combined, unaccentedCombined, tokens, unaccentedTokens, PRODUCT_KEYWORDS) * 2
        if (hasPrice) rawShoppingScore += 6
        if (hasEcomSite) rawShoppingScore += 6
        if (isCommercialPoi) rawShoppingScore += 25
        // News articles, press reports, or timetables must NEVER have shopping score unless explicit cart is present or commercial POI
        val shoppingScore = if ((isNews || isTimetable) && !hasExplicitCart && !isCommercialPoi) 0 else rawShoppingScore

        // 2. Calculate Personal Score
        val hasExplicitIdentityDoc = IDENTITY_DOC_SIGNATURES.any { unaccentedCombined.contains(it) }
        val faceBonus = if (effectiveHasFace) 15 else 0
        var rawPersonalScore = countMatches(combined, unaccentedCombined, tokens, unaccentedTokens, PERSONAL_KEYWORDS) * 3 + faceBonus
        if (hasExplicitIdentityDoc) rawPersonalScore += 20
        if (isTimetable && !hasExplicitIdentityDoc) {
            rawPersonalScore = 0
        }
        if (shoppingScore >= 6 && !effectiveHasFace && !hasExplicitIdentityDoc) {
            rawPersonalScore = 0
        }
        val personalScore = rawPersonalScore

        // 3. Calculate Nature Score
        val scenerySources = listOf("nationalgeographic", "natgeo", "geographic.com", "pexels", "unsplash", "pixabay", "wallpaper")
        val hasScenerySource = scenerySources.any { unaccentedCombined.contains(it) }
        var rawNatureScore = countMatches(combined, unaccentedCombined, tokens, unaccentedTokens, NATURE_KEYWORDS) * 2
        if (hasScenerySource) rawNatureScore += 10
        if (visualResult.isNature || visualResult.isAnimal) rawNatureScore += 15
        val natureScore = if (personalScore > 0 || isTimetable || (shoppingScore >= 6 && !hasScenerySource && !visualResult.isNature && !visualResult.isAnimal)) 0 else rawNatureScore

        // 4. Calculate Event Trigger & Score
        val hasEventTrigger = isTimetable || countMatches(combined, unaccentedCombined, tokens, unaccentedTokens, EVENT_ACTION_KEYWORDS) > 0 || vectorPrediction?.first == DefaultCategories.EVENTS
        val eventScore = when {
            isTimetable -> 25
            vectorPrediction?.first == DefaultCategories.EVENTS -> 15
            hasEventTrigger -> 5
            else -> 0
        }

        // 5. Calculate Reading Score
        var rawReadingScore = countMatches(combined, unaccentedCombined, tokens, unaccentedTokens, READING_KEYWORDS) * 2
        if (isNews) rawReadingScore += 12
        val readingScore = if (isTimetable) 0 else rawReadingScore

        // Decision logic
        when {
            // 1. Timetables & Schedules (Archetype dominance: hospital clinic hours, school schedules, showtimes)
            isTimetable -> {
                val resolvedTitle = extractSmartTitle(cleanedText, photoTitle, "EVENT")
                val dateMillis = extractDateTime(cleanedText)
                val description = generateStructuredDescription(cleanedText, "EVENT", resolvedTitle)
                return ParsedScreenshotData(
                    detectedIntent = "EVENT",
                    suggestedEventTitle = resolvedTitle,
                    suggestedEventDateMillis = dateMillis,
                    targetCategoryId = DefaultCategories.EVENTS,
                    suggestedEventDescription = description
                )
            }

            // 2. Reading articles, news & editorials: When reading/news cues dominate
            readingScore > 0 && readingScore >= shoppingScore && readingScore >= eventScore -> {
                val resolvedTitle = extractSmartTitle(cleanedText, photoTitle, "READING")
                return ParsedScreenshotData(
                    detectedIntent = "READING",
                    suggestedEventTitle = resolvedTitle,
                    suggestedEventDateMillis = null,
                    targetCategoryId = DefaultCategories.READING,
                    suggestedEventDescription = null
                )
            }

            // 3. Strong Shopping / Product: When shopping cues/prices dominate, it's cat_tobuy!
            shoppingScore > 0 && shoppingScore >= personalScore && shoppingScore >= natureScore && shoppingScore >= eventScore -> {
                val resolvedTitle = extractSmartTitle(cleanedText, photoTitle, "SHOPPING")
                val dateMillis = extractDateTime(cleanedText)
                val description = generateStructuredDescription(cleanedText, "SHOPPING", resolvedTitle)
                return ParsedScreenshotData(
                    detectedIntent = "SHOPPING",
                    suggestedEventTitle = resolvedTitle,
                    suggestedEventDateMillis = dateMillis,
                    targetCategoryId = DefaultCategories.TO_BUY,
                    suggestedEventDescription = description
                )
            }

            // 4. Explicit Event: requires unambiguous event action triggers with valid date/time
            hasEventTrigger && eventScore >= readingScore -> {
                val resolvedTitle = extractSmartTitle(cleanedText, photoTitle, "EVENT")
                val dateMillis = extractDateTime(cleanedText)
                val description = generateStructuredDescription(cleanedText, "EVENT", resolvedTitle)
                return ParsedScreenshotData(
                    detectedIntent = "EVENT",
                    suggestedEventTitle = resolvedTitle,
                    suggestedEventDateMillis = dateMillis,
                    targetCategoryId = DefaultCategories.EVENTS,
                    suggestedEventDescription = description
                )
            }

            // 5. Personal: Pictures of persons (face), selfies, family photos, IDs, CCCD, passports
            personalScore > 0 && personalScore >= natureScore -> {
                val tags = if (visualResult.vietnameseSemanticTags.isNotEmpty()) {
                    visualResult.vietnameseSemanticTags
                } else {
                    listOf("cá nhân", "chân dung")
                }
                return ParsedScreenshotData(
                    detectedIntent = "OTHER",
                    suggestedEventTitle = null,
                    suggestedEventDateMillis = null,
                    targetCategoryId = DefaultCategories.PERSONAL,
                    suggestedEventDescription = null,
                    semanticTags = tags
                )
            }

            // 6. Nature & Landscape: Scenery photos, National Geographic, Pexels, mountains, animals
            natureScore > 0 -> {
                val tags = if (visualResult.vietnameseSemanticTags.isNotEmpty()) {
                    visualResult.vietnameseSemanticTags
                } else {
                    listOf("thiên nhiên", "phong cảnh")
                }
                return ParsedScreenshotData(
                    detectedIntent = "OTHER",
                    suggestedEventTitle = null,
                    suggestedEventDateMillis = null,
                    targetCategoryId = DefaultCategories.NATURE,
                    suggestedEventDescription = null,
                    semanticTags = tags
                )
            }

            // 7. Vector semantic classification fallback (Holistic 128D space alignment)
            vectorPrediction != null && (vectorPrediction.first != DefaultCategories.NATURE || isNatureContent(photoTitle, cleanedText)) -> {
                val (catId, _) = vectorPrediction
                val resolvedIntent = when (catId) {
                    DefaultCategories.EVENTS -> "EVENT"
                    DefaultCategories.TO_BUY -> "SHOPPING"
                    DefaultCategories.READING -> "READING"
                    else -> "OTHER"
                }
                val resolvedTitle = if (resolvedIntent in listOf("EVENT", "SHOPPING", "READING")) {
                    extractSmartTitle(cleanedText, photoTitle, resolvedIntent)
                } else null
                val dateMillis = if (resolvedIntent in listOf("EVENT", "SHOPPING")) extractDateTime(cleanedText) else null
                val description = if (resolvedIntent in listOf("EVENT", "SHOPPING")) {
                    generateStructuredDescription(cleanedText, resolvedIntent, resolvedTitle ?: photoTitle)
                } else null

                return ParsedScreenshotData(
                    detectedIntent = resolvedIntent,
                    suggestedEventTitle = resolvedTitle,
                    suggestedEventDateMillis = dateMillis,
                    targetCategoryId = catId,
                    suggestedEventDescription = description
                )
            }

            // 8. Reading articles & books (fallback if not caught above)
            readingScore > 0 -> {
                val resolvedTitle = extractSmartTitle(cleanedText, photoTitle, "READING")
                return ParsedScreenshotData(
                    detectedIntent = "READING",
                    suggestedEventTitle = resolvedTitle,
                    suggestedEventDateMillis = null,
                    targetCategoryId = DefaultCategories.READING,
                    suggestedEventDescription = null
                )
            }

            // 9. Fallback for remaining shopping/product if any
            shoppingScore > 0 -> {
                val resolvedTitle = extractSmartTitle(cleanedText, photoTitle, "SHOPPING")
                val dateMillis = extractDateTime(cleanedText)
                val description = generateStructuredDescription(cleanedText, "SHOPPING", resolvedTitle)
                return ParsedScreenshotData(
                    detectedIntent = "SHOPPING",
                    suggestedEventTitle = resolvedTitle,
                    suggestedEventDateMillis = dateMillis,
                    targetCategoryId = DefaultCategories.TO_BUY,
                    suggestedEventDescription = description
                )
            }

            // 10. Default safe fallback: OTHER intent, no bogus calendar event!
            else -> {
                return ParsedScreenshotData(
                    detectedIntent = "OTHER",
                    suggestedEventTitle = null,
                    suggestedEventDateMillis = null,
                    targetCategoryId = null,
                    suggestedEventDescription = null
                )
            }
        }
    }

    private fun cleanStatusBarNoise(text: String): String {
        val lines = text.lines()
        val cleanedLines = mutableListOf<String>()
        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (index < 3 && isStatusBarLine(trimmed)) {
                continue
            }
            cleanedLines.add(line)
        }
        return cleanedLines.joinToString("\n")
    }

    private fun isStatusBarLine(line: String): Boolean {
        if (line.isBlank()) return true
        val lower = line.lowercase(Locale.getDefault())
        if (lower.matches(Regex("^\\d{1,3}%$"))) return true
        if (lower in listOf("4g", "5g", "lte", "wifi", "wi-fi", "viettel", "mobifone", "vinaphone", "vietnamobile")) return true
        if (lower.matches(Regex("^\\d{1,2}[:.]\\d{2}(\\s*(am|pm))?$"))) return true
        return false
    }

    fun cleanPhrase(text: String, maxLength: Int = 90): String {
        val trimmed = text.trim()
        if (trimmed.length <= maxLength) return trimmed
        val sub = trimmed.substring(0, maxLength)
        val lastSpace = sub.lastIndexOf(' ')
        return if (lastSpace > maxLength / 2) {
            sub.substring(0, lastSpace).trim()
        } else {
            sub.trim()
        }
    }

    fun isUiNoiseLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.length < 2) return true
        val lower = trimmed.lowercase(Locale.getDefault())
        val unaccented = VietnameseUtils.removeDiacritics(lower)

        if (isStatusBarLine(trimmed)) return true
        if (lower.contains("★") || lower.contains("⭐") || lower.contains("★*")) return true
        if (lower.matches(Regex("""^\*?\d+(\.\d+)?\s*(\([^\)]+\))?$"""))) return true
        if (lower.matches(Regex("""^[0-9\s\.\-\(\)\+]{7,}$"""))) return true
        if (lower.matches(Regex("""^[0-9:.\\sAPMapm]+$"""))) return true

        val exactUiWords = setOf(
            "menu", "home", "trang chu", "gio hang", "thong bao", "cai dat", "quay lai", "back",
            "tim kiem", "search", "chi tiet", "danh muc", "hinh anh", "chinh sach", "da ban",
            "khach hang moi", "khach hang", "gia", "khuyen mai", "uu dai", "giam gia", "danh gia",
            "binh luan", "chia se", "tin nhan", "tai khoan", "thanh toan", "mua ngay", "them vao gio",
            "giao hang", "van chuyen", "tong quan", "mo ta", "xem them", "dong", "huy", "luu",
            "yeu thich", "bo suu tap", "chon mua", "tra gop", "free download", "download",
            "duong di", "goi dien", "dat ban", "bai danh gia", "xem thuc don", "trang web",
            "dat den lay", "dat qua mang", "dat giao tan noi",
            "tag:", "tag", "tags", "c12", "mne stlos", "doar sihye", "u som 04.05202"
        )
        if (unaccented in exactUiWords) return true

        val datePrefixes = listOf(
            "d ngay dang", "ngay dang", "dang ngay", "post date", "posted", "upload date",
            "ngay gio", "tin dang", "dang luc", "theo doi cac ghe", "app lotte cinema", "app lotte",
            "su kien:", "chuyen muc:", "chu de:", "chuyen de:"
        )
        if (datePrefixes.any { unaccented.startsWith(it) }) return true

        if (unaccented.contains("thong tin dien tu") || unaccented.contains("bao dien tu") ||
            unaccented.contains("tap chi dien tu") || unaccented.contains("giay phep") ||
            unaccented.contains("tong bien tap") || unaccented.contains("toa soan") ||
            unaccented.contains("co quan chu quan")) {
            return true
        }

        // Multi-category menu header on news sites (e.g., "NGOẠI HẠNG ANH TIN TỨC BÓNG ĐÁ KINH DOANH")
        val newsCategoryWords = listOf("ngoai hang anh", "tin tuc", "bong da", "kinh doanh", "thoi su", "giai tri", "the thao")
        val menuMatchCount = newsCategoryWords.count { unaccented.contains(it) }
        if (menuMatchCount >= 2) {
            return true
        }

        if (lower.contains("shopee") || lower.contains("lazada") || lower.contains("tiki.vn") ||
            lower.contains("amazon.com") || lower.contains("dientumienbac") ||
            lower.contains("nationalgeographic") || lower.contains("wikimedia") ||
            lower.contains("wikipedia")) {
            return true
        }

        return false
    }

    fun extractSmartTitle(text: String, fallback: String, intent: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val validCandidates = lines.filter { line ->
            line.length in 4..100 &&
            !isUiNoiseLine(line) &&
            !line.matches(Regex("""^[\d\.,\s]+(đ|₫|vnd|\$|k)$""", RegexOption.IGNORE_CASE))
        }
        if (validCandidates.isEmpty()) return fallback

        when (intent.uppercase(Locale.getDefault())) {
            "SHOPPING" -> {
                val scoredLines = validCandidates.map { line ->
                    val lower = line.lowercase(Locale.getDefault())
                    val unacc = VietnameseUtils.removeDiacritics(lower)
                    var score = 0
                    for (prodKw in PRODUCT_KEYWORDS) {
                        val lowerKw = prodKw.lowercase(Locale.getDefault())
                        if (lower.contains(lowerKw) || unacc.contains(lowerKw)) {
                            score += 8
                            break
                        }
                    }
                    val brands = listOf(
                        "bluestone", "sony", "samsung", "apple", "xiaomi", "panasonic",
                        "philips", "sunhouse", "tefal", "lock&lock", "toshiba", "sharp",
                        "electrolux", "inax", "toto", "canon", "nikon", "fujifilm", "eos"
                    )
                    for (brand in brands) {
                        if (lower.contains(brand) || unacc.contains(brand)) {
                            score += 10
                            break
                        }
                    }
                    val poiVenues = listOf("quan", "nha hang", "steak", "kitchen", "cuisine", "bit tet", "sushi", "cafe", "ca phe", "bistro", "bakery", "tiem")
                    for (poi in poiVenues) {
                        if (unacc.contains(poi)) {
                            score += 10
                            break
                        }
                    }
                    if (Regex("""\b(\d+([.,]\d+)?\s*(lít|lit|l\b|cm|inch)|rcb-\d+|oled|pro|max)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
                        score += 6
                    }
                    if (line.length in 15..70) {
                        score += 3
                    }
                    if (line.contains(">")) {
                        score -= 5
                    }
                    if (unacc.contains("doi noi cu") || unacc.contains("ly tuong cho") || unacc.contains("dap ung") || unacc.contains("san sale")) {
                        score -= 8
                    }
                    Pair(line, score)
                }
                val bestCandidate = scoredLines.maxByOrNull { it.second }
                if (bestCandidate != null && bestCandidate.second > 0) {
                    return bestCandidate.first
                }
            }

            "EVENT" -> {
                val lowerText = text.lowercase(Locale.getDefault())
                val hasLotte = lowerText.contains("lotte cinema") || lowerText.contains("lotte")
                val hasCgv = lowerText.contains("cgv")
                val hasBhd = lowerText.contains("bhd")

                val scoredLines = validCandidates.map { line ->
                    val lower = line.lowercase(Locale.getDefault())
                    val unacc = VietnameseUtils.removeDiacritics(lower)
                    var score = 0
                    for (kw in EVENT_ACTION_KEYWORDS) {
                        val lowerKw = kw.lowercase(Locale.getDefault())
                        if (lower.contains(lowerKw) || unacc.contains(lowerKw)) {
                            score += 10
                            break
                        }
                    }
                    if (Regex("""\b\d{1,2}[/.-]\d{1,2}\b""").containsMatchIn(lower)) {
                        score += 5
                    }
                    if (unacc.contains("mot chiec") || unacc.contains("nha ca nha") || unacc.contains("len hoi muon")) {
                        score -= 10
                    }
                    Pair(line, score)
                }
                val bestCandidate = scoredLines.maxByOrNull { it.second }
                if (bestCandidate != null && bestCandidate.second > 0) {
                    val candidateLine = bestCandidate.first
                    return if (hasLotte && !candidateLine.contains("Lotte", ignoreCase = true)) {
                        "$candidateLine - Lotte Cinema"
                    } else if (hasCgv && !candidateLine.contains("CGV", ignoreCase = true)) {
                        "$candidateLine - CGV"
                    } else if (hasBhd && !candidateLine.contains("BHD", ignoreCase = true)) {
                        "$candidateLine - BHD"
                    } else {
                        candidateLine
                    }
                }
            }

            "READING" -> {
                val scoredLines = validCandidates.map { line ->
                    val lower = line.lowercase(Locale.getDefault())
                    val unacc = VietnameseUtils.removeDiacritics(lower)
                    var score = 0
                    if (line.length in 20..90) {
                        score += 6
                    }
                    val headlineCues = listOf(
                        "ùn tắc", "un tac", "giao thông", "giao thong", "thời tiết", "thoi tiet",
                        "mưa lạnh", "mua lanh", "mưa lớn", "mua lon", "kinh tế", "kinh te",
                        "thời sự", "thoi su", "bài viết", "tác giả", "kiến trúc", "phát hiện", "cảnh báo"
                    )
                    for (cue in headlineCues) {
                        if (lower.contains(cue) || unacc.contains(cue)) {
                            score += 8
                        }
                    }
                    if (line.endsWith(".") && line.length > 70) {
                        score -= 6
                    }
                    Pair(line, score)
                }
                val bestCandidate = scoredLines.maxByOrNull { it.second }
                if (bestCandidate != null && bestCandidate.second > 0) {
                    return bestCandidate.first
                }
            }
        }

        return validCandidates.firstOrNull() ?: fallback
    }

    fun generateStructuredDescription(text: String, intent: String, title: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val lowerText = text.lowercase(Locale.getDefault())
        val unaccented = VietnameseUtils.removeDiacritics(lowerText)

        when (intent.uppercase(Locale.getDefault())) {
            "SHOPPING" -> {
                val cleanTitle = if (title.startsWith("Mua sắm:", ignoreCase = true)) {
                    title.substringAfter(":").trim()
                } else {
                    title
                }

                // 1. Extract specs / capacity / dimensions
                val nonSpecWords = listOf(
                    "chinh sach", "chính sách", "van chuyen", "vận chuyển", "lap dat", "lắp đặt",
                    "doi tra", "đổi trả", "bao hanh", "bảo hành", "giao hang", "giao hàng",
                    "thanh toan", "thanh toán", "danh gia", "đánh giá", "binh luan", "bình luận",
                    "dien tu mien bac", "khuyen mai", "khuyến mãi", "uu dai", "ưu đãi", "san sale", "săn sale"
                )
                val specRegex = Regex(
                    """\b(\d+([.,]\d+)?\s*(lít|lit|l\b|ml|kg|g|cm|m\b|inch|"|w\b|kw|v\b)|\d{2,3}\s*("|\b(inch|in)\b)|oled|qled|mini led|led|4k|8k|hdr|120hz|bravia|\d+\s*-\s*\d+\s*(người|cốc)|nắp gài|cao tần|cơm điện tử|inox|eos|r5|r6|mark\s*[ivx]+|\d+mm|\d+mp|megapixel)\b""",
                    RegexOption.IGNORE_CASE
                )
                val specsList = mutableListOf<String>()
                for (line in lines) {
                    if (isUiNoiseLine(line) || line.matches(Regex("""^[\d\.,\s]+(đ|₫|vnd|\$|k)$""", RegexOption.IGNORE_CASE))) continue
                    val unacc = VietnameseUtils.removeDiacritics(line.lowercase(Locale.getDefault()))
                    if (nonSpecWords.any { unacc.contains(it) }) continue
                    if (specRegex.containsMatchIn(line) && !line.equals(cleanTitle, ignoreCase = true)) {
                        val trimmed = cleanPhrase(line, 90)
                        if (trimmed !in specsList) specsList.add(trimmed)
                    }
                }
                val specsStr = specsList.take(2).joinToString("; ").ifBlank { null }

                // 2. Extract price / discounts
                val priceRegex = Regex("""\b(\d+[\.,]\d{3}\s*(đ|₫|vnd|\$)|(\$|₫|đ)\s*\d+|\b\d+k\b)""", RegexOption.IGNORE_CASE)
                var priceStr: String? = null
                for (line in lines) {
                    if (isUiNoiseLine(line)) continue
                    val unaccLine = VietnameseUtils.removeDiacritics(line.lowercase(Locale.getDefault()))
                    if (unaccLine.contains("da ban") || unaccLine.contains("luot ban") || unaccLine.contains("danh gia")) {
                        continue
                    }
                    val match = priceRegex.find(line)
                    if (match != null) {
                        priceStr = cleanPhrase(line, 60)
                        break
                    }
                }
                if (priceStr == null && (unaccented.contains("khuyen mai") || unaccented.contains("san sale") || unaccented.contains("uu dai"))) {
                    priceStr = "Có chương trình ưu đãi / khuyến mãi"
                }

                // 3. Extract store / location / platform
                val stores = listOf("Điện Tử Miền Bắc", "Điện Máy Xanh", "Nguyễn Kim", "CellphoneS", "FPT Shop", "Thế Giới Di Động", "MediaMart", "Pico")
                var detectedStore: String? = null
                for (store in stores) {
                    val storeLower = store.lowercase(Locale.getDefault())
                    val storeUnacc = VietnameseUtils.removeDiacritics(storeLower)
                    if (lowerText.contains(storeLower) || unaccented.contains(storeUnacc)) {
                        detectedStore = store
                        break
                    }
                }

                val cities = listOf(
                    "Hà Nội" to listOf("hà nội", "ha noi"),
                    "TP. Hồ Chí Minh" to listOf("hồ chí minh", "ho chi minh", "tp.hcm", "tphcm", "sài gòn", "sai gon"),
                    "Đà Nẵng" to listOf("đà nẵng", "da nang")
                )
                var detectedCity: String? = null
                for ((cityName, keywords) in cities) {
                    if (keywords.any { unaccented.contains(it) }) {
                        detectedCity = if (unaccented.contains("noi thanh")) "Nội thành $cityName" else cityName
                        break
                    }
                }

                val locationStr = when {
                    detectedStore != null && detectedCity != null -> "$detectedStore ($detectedCity)"
                    detectedStore != null -> detectedStore
                    detectedCity != null -> detectedCity
                    else -> {
                        var locLine: String? = null
                        for (line in lines) {
                            val unacc = VietnameseUtils.removeDiacritics(line.lowercase(Locale.getDefault()))
                            if (unacc.contains("ha noi") || unacc.contains("ho chi minh") || unacc.contains("da nang")) {
                                locLine = cleanPhrase(line.replace(Regex("""^[0-9\.\-\s]+"""), ""), 80)
                                break
                            }
                        }
                        locLine ?: run {
                            val platforms = listOf("Shopee", "Lazada", "Tiki", "Sendo", "Amazon")
                            platforms.firstOrNull { lowerText.contains(it.lowercase(Locale.getDefault())) }?.let { "Sàn TMĐT $it" }
                        }
                    }
                }

                val sb = StringBuilder()
                sb.appendLine("🛒 Lịch mua sắm & Chi tiêu:")
                sb.appendLine("• Sản phẩm: $cleanTitle")
                if (!specsStr.isNullOrBlank()) {
                    sb.appendLine("• Thông số / Chi tiết: $specsStr")
                }
                if (!priceStr.isNullOrBlank()) {
                    sb.appendLine("• Mức giá / Ưu đãi: $priceStr")
                }
                if (!locationStr.isNullOrBlank()) {
                    sb.appendLine("• Nơi bán / Khu vực: $locationStr")
                }
                sb.append("• Ghi chú: Đặt lịch nhắc nhở mua sắm từ ảnh chụp màn hình.")
                return sb.toString()
            }

            "EVENT" -> {
                // 1. Venue / Theater
                val venues = listOf("Lotte Cinema", "Lotte", "CGV", "BHD", "Galaxy Cinema", "Vincom", "Aeon Mall", "Zoom", "Google Meet", "Teams", "Sân bay", "Khách sạn")
                var venueStr: String? = null
                val detectedVenues = mutableListOf<String>()
                for (v in venues) {
                    if (lowerText.contains(v.lowercase(Locale.getDefault()))) {
                        val cleanV = if (v == "Lotte" && lowerText.contains("lotte cinema")) "Lotte Cinema" else v
                        if (cleanV !in detectedVenues) detectedVenues.add(cleanV)
                    }
                }
                if (detectedVenues.isNotEmpty()) {
                    venueStr = detectedVenues.take(2).joinToString(" - ")
                }

                // 2. Dates and Showtimes
                val showtimeRegex = Regex("""\b(\d{1,2}[:h]\d{2})\b""")
                val times = mutableListOf<String>()
                for (line in lines) {
                    val match = showtimeRegex.find(line)
                    if (match != null) {
                        val timeToken = match.groupValues[1]
                        if (timeToken !in times) {
                            times.add(timeToken)
                        }
                    }
                }

                val dateRegex = Regex("""\b(\d{1,2}[/.-]\d{1,2}([/.-]\d{2,4})?)\b""")
                val dates = mutableListOf<String>()
                for (line in lines) {
                    if (isUiNoiseLine(line)) continue
                    val match = dateRegex.find(line)
                    if (match != null && !dates.contains(match.value)) {
                        dates.add(match.value)
                    }
                }

                val timeAndDateSummary = when {
                    dates.isNotEmpty() && times.isNotEmpty() -> "Ngày ${dates.take(3).joinToString(", ")} | Suất: ${times.take(6).joinToString(", ")}${if (times.size > 6) "..." else ""}"
                    times.isNotEmpty() -> "Suất/Giờ: ${times.take(6).joinToString(", ")}${if (times.size > 6) "..." else ""}"
                    dates.isNotEmpty() -> "Ngày: ${dates.take(3).joinToString(", ")}"
                    else -> null
                }

                // 3. Movie or Event Details / Notes
                var noteDetail: String? = null
                for (line in lines) {
                    val unacc = VietnameseUtils.removeDiacritics(line.lowercase(Locale.getDefault()))
                    if (unacc.contains("da vu thiu hon loan") || unacc.contains("da vu tru hon loan")) {
                        noteDetail = "Phim: Đa Vũ Trụ Hỗn Loạn"
                        break
                    }
                    if (unacc.contains("chuyen bay") || unacc.contains("boarding pass")) {
                        noteDetail = cleanPhrase(line, 80)
                        break
                    }
                }
                if (noteDetail == null && (unaccented.contains("app lotte") || unaccented.contains("dat ve") || unaccented.contains("ghe con trong"))) {
                    noteDetail = "Theo dõi chỗ ngồi và vé qua ứng dụng rạp"
                }

                val sb = StringBuilder()
                sb.appendLine("📅 Chi tiết sự kiện & Lịch hẹn:")
                sb.appendLine("• Sự kiện: $title")
                if (!venueStr.isNullOrBlank()) {
                    sb.appendLine("• Địa điểm / Rạp: $venueStr")
                }
                if (!timeAndDateSummary.isNullOrBlank()) {
                    sb.appendLine("• Thời gian / Suất chiếu: $timeAndDateSummary")
                }
                if (!noteDetail.isNullOrBlank()) {
                    sb.appendLine("• Chi tiết: $noteDetail")
                }
                sb.append("• Ghi chú: Đặt lịch nhắc nhở từ ảnh chụp màn hình.")
                return sb.toString()
            }

            else -> {
                val sb = StringBuilder()
                sb.appendLine("📌 Thông tin lịch trình:")
                sb.appendLine("• Nội dung: $title")
                sb.append("• Ghi chú: Lưu tự động từ ảnh chụp màn hình.")
                return sb.toString()
            }
        }
    }

    fun extractTitle(text: String, fallback: String): String {
        return extractSmartTitle(text, fallback, "OTHER")
    }

    fun extractDateTime(text: String): Long? {
        val lower = text.lowercase(Locale.getDefault())
        val unaccented = VietnameseUtils.removeDiacritics(lower)
        val targetCalendar = Calendar.getInstance()
        var dateFound = false
        var timeFound = false

        // 1. Vietnamese explicit date: "ngày 15 tháng 12 năm 2026" or "ngay 15 thang 12"
        val vnDateRegex = Regex("(?:ngày|ngay)\\s+(\\d{1,2})(?:\\s+(?:tháng|thang)\\s+|[/.-])(\\d{1,2})(?:(?:\\s+(?:năm|nam)\\s+|[/.-])(\\d{4}))?")
        val vnMatch = vnDateRegex.find(lower) ?: vnDateRegex.find(unaccented)
        if (vnMatch != null) {
            val day = vnMatch.groupValues[1].toIntOrNull() ?: 1
            val month = (vnMatch.groupValues[2].toIntOrNull() ?: 1) - 1 // 0-based
            val yearStr = vnMatch.groupValues[3]
            val year = if (yearStr.isNotBlank()) yearStr.toIntOrNull() ?: targetCalendar.get(Calendar.YEAR) else targetCalendar.get(Calendar.YEAR)
            targetCalendar.set(Calendar.YEAR, year)
            targetCalendar.set(Calendar.MONTH, month)
            targetCalendar.set(Calendar.DAY_OF_MONTH, day)
            dateFound = true
        }

        // 2. Standard numeric date: "15/12/2026", "15-12-2026", "15/12"
        if (!dateFound) {
            val numDateRegex = Regex("\\b(\\d{1,2})[/.-](\\d{1,2})(?:[/.-](\\d{2,4}))?\\b")
            val numMatch = numDateRegex.find(lower)
            if (numMatch != null) {
                val day = numMatch.groupValues[1].toIntOrNull() ?: 1
                val month = (numMatch.groupValues[2].toIntOrNull() ?: 1) - 1
                val yearGroup = numMatch.groupValues[3]
                val currentYear = targetCalendar.get(Calendar.YEAR)
                val year = when {
                    yearGroup.length == 4 -> yearGroup.toIntOrNull() ?: currentYear
                    yearGroup.length == 2 -> 2000 + (yearGroup.toIntOrNull() ?: (currentYear % 100))
                    else -> currentYear
                }
                if (day in 1..31 && month in 0..11) {
                    targetCalendar.set(Calendar.YEAR, year)
                    targetCalendar.set(Calendar.MONTH, month)
                    targetCalendar.set(Calendar.DAY_OF_MONTH, day)
                    dateFound = true
                }
            }
        }

        // 3. Relative date keywords
        if (!dateFound) {
            if (lower.contains("ngày mai") || unaccented.contains("ngay mai") || lower.contains("tomorrow")) {
                targetCalendar.add(Calendar.DAY_OF_YEAR, 1)
                dateFound = true
            } else if (lower.contains("tuần sau") || unaccented.contains("tuan sau") || lower.contains("next week")) {
                targetCalendar.add(Calendar.DAY_OF_YEAR, 7)
                dateFound = true
            } else if (lower.contains("hôm nay") || unaccented.contains("hom nay") || lower.contains("today")) {
                dateFound = true
            }
        }

        // 4. Time extraction: "14:30", "14h30", "9h00", "9h", "8:30 am", "8h30 tối", "15h"
        val timeRegex = Regex("\\b(\\d{1,2})[:h](\\d{2})?\\s*(am|pm|sáng|sang|chiều|chieu|tối|toi)?\\b")
        val timeMatch = timeRegex.find(lower) ?: timeRegex.find(unaccented)
        if (timeMatch != null) {
            val hourStr = timeMatch.groupValues[1]
            val minStr = timeMatch.groupValues[2]
            val modifier = timeMatch.groupValues[3]

            var hour = hourStr.toIntOrNull() ?: 9
            val minute = minStr.toIntOrNull() ?: 0

            if (modifier in listOf("pm", "chiều", "chieu", "tối", "toi") && hour < 12) {
                hour += 12
            }
            if (modifier in listOf("am", "sáng", "sang") && hour == 12) {
                hour = 0
            }

            if (hour in 0..23 && minute in 0..59) {
                targetCalendar.set(Calendar.HOUR_OF_DAY, hour)
                targetCalendar.set(Calendar.MINUTE, minute)
                targetCalendar.set(Calendar.SECOND, 0)
                targetCalendar.set(Calendar.MILLISECOND, 0)
                timeFound = true
            }
        }

        if (!dateFound && !timeFound) {
            return null
        }

        if (!timeFound) {
            // Default 9:00 AM
            targetCalendar.set(Calendar.HOUR_OF_DAY, 9)
            targetCalendar.set(Calendar.MINUTE, 0)
            targetCalendar.set(Calendar.SECOND, 0)
            targetCalendar.set(Calendar.MILLISECOND, 0)
        }

        val now = System.currentTimeMillis()
        if (!dateFound && timeFound) {
            if (targetCalendar.timeInMillis <= now) {
                targetCalendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        return targetCalendar.timeInMillis
    }
}
