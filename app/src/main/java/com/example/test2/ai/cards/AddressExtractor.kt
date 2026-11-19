package com.example.test2.ai.cards

import com.example.test2.ai.OcrLine
import com.example.test2.shared.utils.VietnameseUtils
import java.text.Normalizer
import java.util.Locale

data class ExtractedAddress(
    val fullAddress: String,
    val placeName: String,
    val city: String,
    val adminSystem: String
)

object AddressExtractor {

    private val NEGATIVE_WORDS = listOf(
        "quan trọng", "quan trong", "liên quan", "lien quan", "tổng quan", "tong quan",
        "quản lý", "quan ly", "tham quan", "kết quả", "ket qua", "cơ quan", "co quan",
        "dinh dưỡng", "dinh duong", "đường huyết", "duong huyet", "bồi dưỡng", "boi duong",
        "ngoài ra", "ngoai ra", "ngỡ ngàng", "ngo ngang", "nghi ngờ", "nghi ngo", "ngon miệng", "ngon mieng",
        "tuyển dụng", "tuyen dung", "mức lương", "muc luong", "thu nhập", "thu nhap",
        "thời tiết", "thoi tiet", "dự báo", "du bao", "tin tức", "tin tuc",
        "kẹt xe", "ket xe", "tắc đường", "tac duong", "nhiệt độ", "nhiet do",
        // Social media noise words
        "bình luận", "binh luan", "lượt thích", "luot thich", "lượt xem", "luot xem",
        "theo dõi", "theo doi", "bài viết", "bai viet", "thành viên", "thanh vien",
        "phút trước", "phut truoc", "giờ trước", "gio truoc", "chia sẻ", "chia se"
    )

    private val HOUSE_NO_REGEX = Regex("""(?:^|[\s,;./\-]|\b)\d+[a-zA-Z]?(?:[-/]\d+[a-zA-Z]?)*(?:\s*,\s*|\s+)[A-ZÀ-Ỹa-zà-ỹ0-9]""")
    private val ALLEY_REGEX = Regex("""(?i)(?:^|[\s,;./\-]|\b)(?:ngõ|ngách|hẻm|kiệt|k\s*\.?\s*)\s*\d+""")
    private val STREET_REGEX = Regex("""(?i)(?:^|[\s,;./\-]|\b)(?:[đĐdD]ường|[pP]hố|duong|pho)\s+[A-ZÀ-Ỹa-zà-ỹ0-9]""")
    private val BUILDING_REGEX = Regex(
        """(?i)(?:^|[\s,;./\-]|\b)(?:toà\s*nhà|tòa\s*nhà|tòa|toà|tầng\s*(?:l\d+|b\d+|\d+|g|m|trệt|lửng)|level\s*\d+|floor\s*\d+|kđt|chung\s*cư|residence|tower|plaza|building|center|mall|vincom|lotte|aeon|crescent|landmark|keangnam|tttm|trung\s*tâm\s*thương\s*mại|siêu\s*thị|chợ|gian\s*hàng|gh\s*-\s*[a-z0-9\-]+|lô\s+[a-z0-9\-]+|kiosk\s*[a-z0-9\-]+|shophouse|căn\s*(?:hộ)?\s*[a-z0-9\-]+)(?:$|[\s,;./\-]|\b)"""
    )
    private val LABEL_REGEX = Regex("""(?i)(?:^|[\s,;./\-]|\b)(?:[đĐdD]ịa\s*chỉ|dia\s*chi|[đĐdD]c|dc|address|location|cơ\s*sở|chi\s*nhánh)\s*[:=.-]?""")

    private val PLACE_PREFIXES = listOf(
        "quán", "cửa hàng", "nhà hàng", "tiệm", "cafe", "cà phê", "phê la",
        "kichi", "kfc", "lotteria", "highlands", "the coffee house", "chi nhánh", "cơ sở"
    )

    private val PLACE_FORBIDDEN_WORDS = listOf(
        "hôm nay", "hom nay", "hôm qua", "hom qua", "nghỉ", "nghi", "đóng cửa", "dong cua",
        "mở cửa", "mo cua", "xin lỗi", "xin loi", "thông báo", "thong bao", "tuyển", "tuyen"
    )

    fun extractAddress(
        rawOcrText: String,
        ocrLines: List<OcrLine>? = null
    ): ExtractedAddress? {
        if (rawOcrText.isBlank()) return null

        val normalized = Normalizer.normalize(rawOcrText, Normalizer.Form.NFC)
        val lines = normalized.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        var bestAddressText: String? = null
        var bestScore = 0
        var bestTopLineIndex = -1

        // Scan from bottom to top for administrative anchors
        for (i in lines.indices.reversed()) {
            val line = lines[i]
            val lowerLine = line.lowercase(Locale.getDefault())

            // Skip lines with negative noise words
            if (NEGATIVE_WORDS.any { lowerLine.contains(it) }) continue

            val combinedWithPrev = if (i > 0) "${lines[i - 1]} $line" else line
            val hasAnchor = AdminUnits.hasAdminAnchor(line) || (i > 0 && AdminUnits.hasAdminAnchor(combinedWithPrev))

            if (hasAnchor) {
                // Found an anchor at line i (or combined line i-1 and i). Build window upwards
                var bottomIdx = i
                val anchorStartLine = if (!AdminUnits.hasAdminAnchor(line) && i > 0) i - 1 else i

                // Check if line i+1 is a standalone province name (e.g. "Hà Nội" on next line)
                if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1]
                    val nextLower = nextLine.lowercase(Locale.getDefault())
                    if (AdminUnits.LEGACY_VN_CITIES.any { nextLower == it.lowercase(Locale.getDefault()) } &&
                        !NEGATIVE_WORDS.any { nextLower.contains(it) }) {
                        bottomIdx = i + 1
                    }
                }

                val windowParts = mutableListOf<String>()
                var topIdx = anchorStartLine

                // Walk upwards up to 3 lines
                for (step in 0..3) {
                    val candidateIdx = anchorStartLine - step
                    if (candidateIdx < 0) break

                    val curLine = lines[candidateIdx]
                    val curLower = curLine.lowercase(Locale.getDefault())

                    if (NEGATIVE_WORDS.any { curLower.contains(it) }) break

                    // Stop if we hit a different block in structured OCR
                    if (ocrLines != null && ocrLines.size == lines.size) {
                        if (candidateIdx < anchorStartLine && ocrLines[candidateIdx].blockId != ocrLines[anchorStartLine].blockId) {
                            break
                        }
                    }

                    val hasComponent = hasAddressComponent(curLine) || AdminUnits.hasAdminAnchor(curLine)
                    val isContinuation = curLine.length < 50 && !curLine.endsWith(".") && !curLine.endsWith("!")

                    if (hasComponent || (isContinuation && step <= 1)) {
                        topIdx = candidateIdx
                    } else if (step > 0) {
                        break
                    }
                }

                for (idx in topIdx..bottomIdx) {
                    windowParts.add(lines[idx])
                }

                val reconstructed = reconstructAddressWindow(windowParts)
                val cleanAddr = cleanAddressPrefix(reconstructed)

                // Validation criteria:
                // 1. Must have Admin anchor
                // 2. Must have house no / street / alley / building OR explicit label + street
                // 3. Must not be ONLY a province name
                if (isValidAddress(cleanAddr)) {
                    val score = scoreAddress(cleanAddr)
                    if (score > bestScore) {
                        bestScore = score
                        bestAddressText = cleanAddr
                        bestTopLineIndex = topIdx
                    }
                }
            }
        }

        if (bestAddressText == null) return null

        val placeName = extractPlaceName(lines, bestTopLineIndex, bestAddressText, ocrLines)
        val detectedCity = AdminUnits.detectCity(bestAddressText)
        val adminSystem = AdminUnits.detectAdminSystem(bestAddressText)

        return ExtractedAddress(
            fullAddress = bestAddressText,
            placeName = placeName,
            city = detectedCity,
            adminSystem = adminSystem
        )
    }

    private fun hasMultiAdminWithStreet(text: String): Boolean {
        val lower = text.lowercase(Locale.getDefault())
        val hasDistrict = AdminUnits.KNOWN_DISTRICTS.any { lower.contains(it.lowercase(Locale.getDefault())) } ||
                Regex("""(?i)(?:^|[\s,;./\-]|\b)(?:quận|huyện|thị\s*xã|q\.|q\s*\d+)(?:$|[\s,;./\-]|\b)""").containsMatchIn(lower)
        val hasCity = AdminUnits.detectCity(text).isNotBlank()
        val segments = text.split(",").map { it.trim() }.filter { it.isNotBlank() }
        return hasDistrict && hasCity && segments.size >= 3
    }

    private fun hasAddressComponent(line: String): Boolean {
        return HOUSE_NO_REGEX.containsMatchIn(line) ||
                ALLEY_REGEX.containsMatchIn(line) ||
                STREET_REGEX.containsMatchIn(line) ||
                BUILDING_REGEX.containsMatchIn(line) ||
                LABEL_REGEX.containsMatchIn(line) ||
                hasMultiAdminWithStreet(line)
    }

    private fun isValidAddress(text: String): Boolean {
        val lower = text.lowercase(Locale.getDefault())

        // Must not contain negative noise words
        if (NEGATIVE_WORDS.any { lower.contains(it) }) return false

        // Must have admin anchor
        if (!AdminUnits.hasAdminAnchor(text)) return false

        // Must have at least one specific street-level component or house number
        val hasSpecificComponent = HOUSE_NO_REGEX.containsMatchIn(text) ||
                ALLEY_REGEX.containsMatchIn(text) ||
                STREET_REGEX.containsMatchIn(text) ||
                BUILDING_REGEX.containsMatchIn(text) ||
                (LABEL_REGEX.containsMatchIn(text) && text.any { it.isDigit() }) ||
                hasMultiAdminWithStreet(text)

        if (!hasSpecificComponent) return false

        // Must not be a pure province name or single word
        if (text.length < 8) return false

        return true
    }

    private fun scoreAddress(text: String): Int {
        var score = 0
        if (LABEL_REGEX.containsMatchIn(text)) score += 4
        if (HOUSE_NO_REGEX.containsMatchIn(text)) score += 3
        if (ALLEY_REGEX.containsMatchIn(text)) score += 2
        if (STREET_REGEX.containsMatchIn(text)) score += 2
        if (BUILDING_REGEX.containsMatchIn(text)) score += 2
        if (AdminUnits.hasAdminAnchor(text)) score += 3
        if (AdminUnits.detectCity(text).isNotBlank()) score += 2
        if (hasMultiAdminWithStreet(text)) score += 2
        return score
    }

    private fun reconstructAddressWindow(parts: List<String>): String {
        if (parts.isEmpty()) return ""
        val sb = StringBuilder(parts[0].trim())
        for (k in 1 until parts.size) {
            val prev = parts[k - 1].trim()
            val cur = parts[k].trim()
            if (prev.endsWith(",") || prev.endsWith(";")) {
                sb.append(" ").append(cur)
            } else if (isWordWrapContinuation(prev, cur)) {
                sb.append(" ").append(cur)
            } else {
                sb.append(", ").append(cur)
            }
        }
        return sb.toString()
    }

    private fun isWordWrapContinuation(prev: String, cur: String): Boolean {
        if (prev.endsWith(",") || prev.endsWith(".")) return false
        val lastWord = prev.split(Regex("""[,\s]+""")).lastOrNull() ?: ""
        val combined = "$lastWord $cur"
        if (AdminUnits.detectCity(combined).isNotBlank() || AdminUnits.hasAdminAnchor(combined)) {
            return true
        }
        // If cur is a pure number (like zip code 100000), it's not a word continuation
        if (cur.all { it.isDigit() }) return false

        // If cur is a short continuation token without commas
        if (cur.split(" ").size <= 2 && cur.length <= 15 && !cur.contains(",")) {
            return true
        }
        return false
    }

    private fun cleanAddressPrefix(line: String): String {
        return line
            .replace(Regex("""^[📍•\-\*]\s*"""), "")
            .replace(Regex("""^(?:địa\s*chỉ|dia\s*chi|location|address|add|ở|o|tại|tai|đc|dc)\s*[:=.-]?\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex(""",\s*,"""), ",")
            .replace(Regex("""\s+,\s*"""), ", ")
            .replace(Regex("""(?:[,\s]+)\b\d{5,6}\b$"""), "")
            .trim()
    }

    /**
     * Resolves place name strictly from the line immediately above the address window (Decision D2).
     * Rejects global scanning and photoTitle fallback.
     */
    private fun extractPlaceName(
        lines: List<String>,
        topAddressLineIdx: Int,
        bestAddress: String,
        ocrLines: List<OcrLine>?
    ): String {
        if (topAddressLineIdx <= 0) return ""

        val candidateIdx = topAddressLineIdx - 1
        val candidate = lines[candidateIdx].trim()

        if (candidate.length !in 4..60) return ""
        if (bestAddress.contains(candidate, ignoreCase = true)) return ""

        val lower = candidate.lowercase(Locale.getDefault())
        val unacc = VietnameseUtils.removeDiacritics(lower)

        // Must not contain forbidden state verbs ("hôm nay", "nghỉ", "đóng cửa")
        if (PLACE_FORBIDDEN_WORDS.any { lower.contains(it) || unacc.contains(it) }) return ""

        // Must not be a phone or address line
        if (candidate.contains("0") && candidate.filter { it.isDigit() }.length >= 7) return ""
        if (hasAddressComponent(candidate) || AdminUnits.hasAdminAnchor(candidate)) return ""

        // Check structured OCR geometry if available: must be vertically close (<= 1.5 line height)
        if (ocrLines != null && ocrLines.size == lines.size) {
            val addrLine = ocrLines[topAddressLineIdx]
            val candLine = ocrLines[candidateIdx]
            if (candLine.blockId != addrLine.blockId) return ""
            val gap = addrLine.top - candLine.bottom
            val height = addrLine.bottom - addrLine.top
            if (gap > 1.5f * height) return ""
        }

        // Must match either place prefixes OR look like a capitalized brand/place name
        val hasPlacePrefix = PLACE_PREFIXES.any { lower.contains(it) || unacc.contains(it) }

        val words = candidate.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val capitalizedWords = words.count { it.firstOrNull()?.isUpperCase() == true }
        val isProperName = words.size in 2..7 && (capitalizedWords.toFloat() / words.size) >= 0.5f && !candidate.contains(":")

        if (hasPlacePrefix || isProperName) {
            return candidate
        }

        return ""
    }
}
