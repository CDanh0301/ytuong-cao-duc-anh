package com.example.test2.ai

import com.example.test2.shared.utils.VietnameseUtils
import java.util.Locale

/**
 * Visual Concept Expander (Tier 1 & Tier 2 Concept Expansion).
 * Bridges the semantic gap between high-level user category concepts (e.g., "Thú cưng", "Cây cảnh", "Cắm trại")
 * and concrete visual labels/tags produced by AI vision models (e.g., "chó", "mèo", "sen đá", "lều", "núi").
 * 100% on-device, 0ms latency, zero memory overhead.
 */
object VisualConceptExpander {

    private data class ConceptCluster(
        val broadPhrases: Set<String>,
        val vietnameseWords: Set<String>,
        val englishWords: Set<String>,
        val allExpansionTerms: Set<String>
    )

    private val CLUSTERS = listOf(
        // 1. Thú cưng & Động vật
        ConceptCluster(
            broadPhrases = setOf(
                "thú cưng", "thu cung", "vật nuôi", "vat nuoi", "động vật", "dong vat",
                "muông thú", "muong thu", "thế giới động vật", "the gioi dong vat",
                "cún yêu", "cung yeu", "cá cảnh", "ca canh"
            ),
            vietnameseWords = setOf("chó", "cún", "mèo", "miu", "chim", "thỏ", "rùa", "hamster"),
            englishWords = setOf("pet", "pets", "dog", "cat", "puppy", "kitten", "bird", "animal", "wildlife"),
            allExpansionTerms = setOf(
                "cho", "meo", "pet", "chó", "mèo", "dong vat", "dog", "cat", "thú cưng", "động vật",
                "cún", "miu", "chim", "cá cảnh", "hamster", "thỏ", "rùa", "cun", "tho", "rua",
                "thu cung", "puppy", "kitten", "bird", "animal", "wildlife"
            )
        ),

        // 2. Cây cảnh, Hoa & Thực vật
        ConceptCluster(
            broadPhrases = setOf(
                "cây cảnh", "cay canh", "thực vật", "thuc vat", "cây cối", "cay coi",
                "vườn tược", "vuon tuoc", "hoa lá", "hoa la", "hoa cỏ", "hoa co",
                "vườn hoa", "vuon hoa", "vườn nhà", "vuon nha", "chậu cây", "chau cay",
                "cây xanh", "cay xanh", "sen đá", "sen da"
            ),
            vietnameseWords = setOf("hoa", "lá", "cây", "bonsai"),
            englishWords = setOf("plant", "plants", "flower", "tree", "flora", "leaf", "grass", "succulent", "gardening"),
            allExpansionTerms = setOf(
                "hoa", "lá", "sen đá", "bonsai", "chậu cây", "vườn hoa", "cây xanh", "cây cảnh", "thực vật", "cây cối", "hoa lá", "hoa cỏ",
                "sen da", "chau cay", "vuon hoa", "cay xanh", "cay canh", "thuc vat", "cay coi", "hoa la", "hoa co",
                "flower", "tree", "plant", "flora", "leaf", "grass", "succulent"
            )
        ),

        // 3. Biển đảo & Sông nước
        ConceptCluster(
            broadPhrases = setOf(
                "du lịch biển", "du lich bien", "đi biển", "di bien", "nghỉ dưỡng biển", "nghi duong bien",
                "sông nước", "song nuoc", "tắm biển", "tam bien", "bãi biển", "bai bien",
                "bờ biển", "bo bien", "sóng biển", "song bien", "hải sản", "hai san", "sông hồ", "song ho",
                "đại dương", "dai duong"
            ),
            vietnameseWords = setOf("biển", "thuyền", "cát"),
            englishWords = setOf("beach", "sea", "ocean", "coast"),
            allExpansionTerms = setOf(
                "bãi biển", "bờ biển", "sóng biển", "cát", "thuyền", "hải sản", "sông hồ", "biển", "du lịch biển", "đi biển", "đại dương",
                "bai bien", "bo bien", "song bien", "cat", "thuyen", "hai san", "song ho", "bien", "du lich bien", "di bien", "dai duong",
                "beach", "sea", "ocean", "water", "sand", "coast"
            )
        ),

        // 4. Rừng núi, Dã ngoại & Cắm trại
        ConceptCluster(
            broadPhrases = setOf(
                "cắm trại", "cam trai", "dã ngoại", "da ngoai", "rừng núi", "rung nui",
                "đồi núi", "doi nui", "thác nước", "thac nuoc", "đỉnh núi", "dinh nui",
                "leo núi", "leo nui", "sinh thái", "sinh thai", "phong cảnh", "phong canh",
                "thiên nhiên", "thien nhien"
            ),
            vietnameseWords = setOf("lều", "rừng", "núi", "suối", "củi"),
            englishWords = setOf("camping", "camp", "picnic", "trekking", "mountain", "forest", "hill", "waterfall", "jungle", "wilderness"),
            allExpansionTerms = setOf(
                "lều", "rừng", "núi", "đồi núi", "thác nước", "suối", "đỉnh núi", "cắm trại", "dã ngoại", "trekking", "phượt", "camping",
                "leu", "rung", "nui", "doi nui", "thac nuoc", "suoi", "dinh nui", "cam trai", "da ngoai", "rung nui",
                "mountain", "forest", "hill", "waterfall", "woods", "jungle", "wilderness", "picnic"
            )
        ),

        // 5. Thể hình, Thể thao & Gym
        ConceptCluster(
            broadPhrases = setOf(
                "tập gym", "tap gym", "thể hình", "the hinh", "sức khỏe", "suc khoe",
                "thể thao", "the thao", "tập luyện", "tap luyen", "thể lực", "the luc",
                "phòng gym", "phong gym", "cơ bắp", "co bap", "chạy bộ", "chay bo",
                "bơi lội", "boi loi"
            ),
            vietnameseWords = setOf("tạ", "gym", "yoga"),
            englishWords = setOf("gym", "fitness", "workout", "cardio", "protein", "dumbbell", "barbell"),
            allExpansionTerms = setOf(
                "tạ", "phòng gym", "cơ bắp", "chạy bộ", "thể thao", "yoga", "bơi lội", "tập gym", "thể hình", "thể lực",
                "ta", "phong gym", "co bap", "chay bo", "the thao", "boi loi", "tap gym", "the hinh", "the luc",
                "dumbbell", "barbell", "gym", "cardio", "protein", "fitness", "workout"
            )
        ),

        // 6. Đồ làm bếp & Nấu ăn
        ConceptCluster(
            broadPhrases = setOf(
                "đồ làm bếp", "do lam bep", "nấu ăn", "nau an", "dụng cụ bếp", "dung cu bep",
                "gia dụng bếp", "gia dung bep", "bếp núc", "bep nuc", "ẩm thực", "am thuc",
                "nấu nướng", "nau nuong", "nhà bếp", "nha bep", "bát đĩa", "bat dia",
                "dao thớt", "dao thot", "nồi chiên", "noi chien", "lò vi sóng", "lo vi song",
                "nồi cơm", "noi com"
            ),
            vietnameseWords = setOf("nồi", "chảo", "bếp", "bát", "đĩa"),
            englishWords = setOf("kitchen", "cooking", "pan", "pot"),
            allExpansionTerms = setOf(
                "nồi", "chảo", "bếp", "bát đĩa", "dao thớt", "nồi chiên", "nhà bếp", "lò vi sóng", "nồi cơm", "đồ làm bếp", "nấu ăn",
                "noi", "chao", "bep", "bat dia", "dao thot", "noi chien", "nha bep", "lo vi song", "noi com", "do lam bep", "nau an",
                "kitchen", "cooking", "pan", "pot"
            )
        ),

        // 7. Xe cộ & Giao thông
        ConceptCluster(
            broadPhrases = setOf(
                "xe cộ", "xe co", "phương tiện", "phuong tien", "giao thông", "giao thong",
                "đi lại", "di lai", "xe máy", "xe may", "ô tô", "o to", "xe đạp", "xe dap",
                "xe hơi", "xe hoi", "mô tô", "mo to", "xe điện", "xe dien"
            ),
            vietnameseWords = setOf("xe", "oto"),
            englishWords = setOf("vehicle", "vehicles", "car", "motorcycle", "bike", "bicycle"),
            allExpansionTerms = setOf(
                "xe máy", "ô tô", "xe đạp", "xe hơi", "mô tô", "xe điện", "xe cộ", "phương tiện", "giao thông",
                "xe may", "o to", "xe dap", "xe hoi", "mo to", "xe dien", "xe co", "phuong tien", "giao thong",
                "car", "motorcycle", "bike", "bicycle", "vehicle"
            )
        ),

        // 8. Cá nhân, Chân dung & Giấy tờ
        ConceptCluster(
            broadPhrases = setOf(
                "cá nhân", "ca nhan", "chân dung", "chan dung", "ảnh cá nhân", "anh ca nhan",
                "hồ sơ", "ho so", "giấy tờ", "giay to", "căn cước", "can cuoc",
                "tự sướng", "tu suong", "bản thân", "ban than"
            ),
            vietnameseWords = setOf("cccd", "cmnd", "passport", "selfie", "portrait"),
            englishWords = setOf("personal", "selfie", "portrait", "profile", "identity"),
            allExpansionTerms = setOf(
                "cá nhân", "chân dung", "tự sướng", "cccd", "cmnd", "hộ chiếu", "bằng lái",
                "ca nhan", "chan dung", "tu suong", "ho chieu", "bang lai",
                "selfie", "portrait", "passport", "profile"
            )
        ),

        // 9. Cà phê, Trà sữa, Nước giải khát & Đồ uống
        ConceptCluster(
            broadPhrases = setOf(
                "cà phê", "ca phe", "đồ uống", "do uong", "trà sữa", "tra sua",
                "quán cà phê", "quan ca phe", "tiệm cà phê", "tiem ca phe", "tiệm trà", "tiem tra",
                "nước giải khát", "nuoc giai khat", "nước uống", "nuoc uong", "thức uống", "thuc uong"
            ),
            vietnameseWords = setOf("cafe", "coffee", "latte", "macchiato", "cappuccino", "espresso", "americano", "starbucks", "highlands"),
            englishWords = setOf("coffee", "drink", "drinks", "beverage", "beverages", "tea", "starbucks", "latte", "macchiato", "cappuccino", "espresso", "cafe"),
            allExpansionTerms = setOf(
                "cà phê", "ca phe", "đồ uống", "do uong", "trà sữa", "tra sua", "quán cà phê", "quan ca phe",
                "tiệm trà", "tiem tra", "nước giải khát", "nuoc giai khat", "cafe", "coffee", "starbucks", "highlands",
                "latte", "macchiato", "cappuccino", "espresso", "americano", "sinh tố", "nước ép", "sinh to", "nuoc ep",
                "drink", "drinks", "beverage", "beverages", "tea", "juice", "smoothie"
            )
        ),

        // 10. Mua sắm, Đơn hàng & Thương mại điện tử
        ConceptCluster(
            broadPhrases = setOf(
                "mua sắm", "mua sam", "đơn hàng", "don hang", "mua hàng", "mua hang",
                "giao hàng", "giao hang", "thương mại điện tử", "thuong mai dien tu",
                "shopee", "lazada", "tiki", "tiktok shop", "sendo"
            ),
            vietnameseWords = setOf("shopee", "lazada", "tiki", "order"),
            englishWords = setOf("shopping", "ecommerce", "order", "delivery", "parcel"),
            allExpansionTerms = setOf(
                "mua sắm", "đơn hàng", "giao hàng", "shopee", "lazada", "tiki", "tiktok shop",
                "mua sam", "don hang", "giao hang", "shopping", "ecommerce", "order", "delivery", "parcel"
            )
        )
    )

    val STOP_WORDS = setOf(
        "ảnh", "anh", "các", "cac", "của", "cua", "và", "va", "cho", "ở", "o", "tại", "tai",
        "bộ", "bo", "tập", "tap", "mới", "moi", "cái", "cai", "những", "nhung", "là", "la",
        "thì", "thi", "mà", "ma", "được", "duoc", "có", "co", "với", "voi", "trong", "trên",
        "tren", "dưới", "duoi", "này", "nay", "đó", "do", "khi"
    )

    /**
     * Checks if a tag matches any concept term using discrete whole-word tokens or multi-word phrase matching.
     * Prevents arbitrary substring matches (e.g. prevents "ca" from matching "camera", "cà phê").
     */
    fun isConceptMatch(tag: String, conceptTerms: Set<String>): Boolean {
        val trimmedTag = tag.trim().lowercase(Locale.getDefault())
        if (trimmedTag.isBlank() || conceptTerms.isEmpty()) return false
        val unaccTag = VietnameseUtils.removeDiacritics(trimmedTag)

        // 1. Direct whole-string match
        if (trimmedTag in conceptTerms || unaccTag in conceptTerms) return true

        // 2. Tokenize tag into discrete words
        val tagTokens = trimmedTag.split(Regex("[\\s,.:;!?'\"()\\[\\]{}<>\\\\/]+")).filter { it.isNotBlank() }
        val tagUnaccTokens = unaccTag.split(Regex("[\\s,.:;!?'\"()\\[\\]{}<>\\\\/]+")).filter { it.isNotBlank() }

        for (term in conceptTerms) {
            val termLower = term.trim().lowercase(Locale.getDefault())
            if (termLower.isBlank()) continue
            val termUnacc = VietnameseUtils.removeDiacritics(termLower)

            // Multi-word phrase concept (e.g., "thú cưng", "cây cảnh", "cắm trại", "xe máy")
            if (termLower.contains(" ")) {
                if (trimmedTag == termLower || unaccTag == termUnacc) return true
                if (trimmedTag.contains(termLower) || unaccTag.contains(termUnacc)) return true
            } else {
                // Single-word concept (e.g. "chó", "mèo", "lều", "núi", "xe")
                // MUST match as a discrete word token, NOT arbitrary substring!
                if (tagTokens.contains(termLower) || tagUnaccTokens.contains(termUnacc)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Retrieves visual cluster terms matching the query, if it matches any semantic visual cluster.
     * Returns an empty set if the query does not belong to any defined visual cluster.
     */
    fun getVisualClusterTerms(query: String): Set<String> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptySet()

        val lower = trimmed.lowercase(Locale.getDefault())
        val unaccented = VietnameseUtils.removeDiacritics(lower)
        val tokens = lower.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val unaccentedTokens = unaccented.split("\\s+".toRegex()).filter { it.isNotBlank() }

        val clusterTerms = mutableSetOf<String>()

        for (cluster in CLUSTERS) {
            val isPhraseMatch = cluster.broadPhrases.any { phrase ->
                lower == phrase || unaccented == phrase ||
                        lower.contains(phrase) || unaccented.contains(phrase)
            }
            val isVietnameseWordMatch = cluster.vietnameseWords.any { word ->
                tokens.contains(word) || lower == word || unaccented == word
            }
            val isEnglishWordMatch = cluster.englishWords.any { word ->
                tokens.contains(word) || unaccentedTokens.contains(word) || lower == word || unaccented == word
            }

            if (isPhraseMatch || isVietnameseWordMatch || isEnglishWordMatch) {
                clusterTerms.addAll(cluster.allExpansionTerms)
            }
        }

        // Exclude query itself and its immediate single tokens
        val excluded = setOf(lower, unaccented) + tokens + unaccentedTokens
        return clusterTerms.filterNot { excluded.contains(it) }.toSet()
    }

    /**
     * Expands a user category name into a comprehensive set of conceptual, visual,
     * and synonym terms in both Vietnamese and English (accented and unaccented).
     */
    fun expandCategoryConcepts(categoryName: String): Set<String> {
        val trimmed = categoryName.trim()
        if (trimmed.isBlank()) return emptySet()

        val lower = trimmed.lowercase(Locale.getDefault())
        val unaccented = VietnameseUtils.removeDiacritics(lower)
        val expanded = mutableSetOf<String>()

        // Add base name variations if not in stopwords
        if (lower !in STOP_WORDS) expanded.add(lower)
        if (unaccented !in STOP_WORDS) expanded.add(unaccented)

        // For single-word categories, add the individual tokens
        // For multi-word phrases, do not add individual ambiguous syllables like "ca", "nhan", "gia"
        // as isolated terms to avoid false-positive substring explosion
        if (!lower.contains(" ")) {
            val tokens = lower.split("\\s+".toRegex()).filter { it.length >= 2 && it !in STOP_WORDS }
            val unaccentedTokens = unaccented.split("\\s+".toRegex()).filter { it.length >= 2 && it !in STOP_WORDS }
            expanded.addAll(tokens)
            expanded.addAll(unaccentedTokens)
        }

        // Add terms from matched visual concept clusters
        expanded.addAll(getVisualClusterTerms(categoryName))

        return expanded
    }
}
