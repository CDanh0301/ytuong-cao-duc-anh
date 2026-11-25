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
        "kẹt xe", "ket xe", "tắc đường", "tac duong", "nhiệt độ", "nhiet do"
    )

    private const val BULLET_PATTERN = """(?:📍|[-•*+oO○●▪▫◦⁃→>]|[^\w\s])"""

    private val SECTION_HEADER_REGEX = Regex(
        """(?i)^\s*(?:$BULLET_PATTERN)?\s*(?:thời\s*gian|thoi\s*gian|nội\s*dung|noi\s*dung|lịch\s*trình|lich\s*trinh|giá\s*vé|gia\s*ve|chi\s*tiết|chi\s*tiet|mô\s*tả|mo\s*ta|quy\s*mô|quy\s*mo|thể\s*lệ|the\s*le|đối\s*tượng|doi\s*tuong|liên\s*hệ|lien\s*he|hotline|website|email)\s*[:=.-]"""
    )

    private val HOUSE_NO_REGEX = Regex("""(?:^|[-,\s;./]|\b)\d+[a-zA-Z]?(?:[-/]\d+[a-zA-Z]?)*(?:\s*,\s*|\s+)[A-ZÀ-Ỹa-zà-ỹ0-9]""")
    private val HOUSE_NO_START_REGEX = Regex("""^\s*\d+[a-zA-Z]?(?:[-/]\d+[a-zA-Z]?)*(?:\s*,\s*|\s+)[A-ZÀ-Ỹa-zà-ỹ0-9]""")
    private val ALLEY_REGEX = Regex("""(?i)(?:^|[-,\s;./]|\b)(?:ngõ|ngách|hẻm|kiệt|k\s*\.?\s*)\s*\d+""")
    private val STREET_REGEX = Regex("""(?i)(?:^|[-,\s;./]|\b)(?<!thành\s)(?<!thanh\s)(?:[đĐdD]ường|[pP]hố|duong|pho)\s+(?!đi\b)(?!di\b)[A-ZÀ-Ỹa-zà-ỹ0-9]""")
    private val BUILDING_REGEX = Regex(
        """(?i)(?:^|[-,\s;./]|\b)(?:sân\s*vận\s*động|nhà\s*thi\s*đấu|công\s*viên|quảng\s*trường|phố\s*đi\s*bộ|khu\s*du\s*lịch|hoàng\s*thành|bảo\s*tàng|nhà\s*hát|chùa|nhà\s*thờ|đền|di\s*tích|bệnh\s*viện|viện\s*huyết\s*học|viện\s*tim|phòng\s*khám|trung\s*tâm\s*y\s*tế|trạm\s*y\s*tế|đại\s*học|trường\s*đại\s*học|học\s*viện|trường\s*(?:thpt|thcs|tiểu\s*học)?|toà\s*nhà|tòa\s*nhà|tòa|toà|tầng\s*(?:l\d+|b\d+|\d+|g|m|trệt|lửng)|level\s*\d+|floor\s*\d+|kđt|chung\s*cư|residence|tower|plaza|building|center|mall|vincom|lotte|aeon|crescent|landmark|keangnam|tttm|trung\s*tâm\s*thương\s*mại|siêu\s*thị|chợ|gian\s*hàng|gh\s*-\s*[-a-z0-9]+|lô\s+[-a-z0-9]+|kiosk\s*[-a-z0-9]+|shophouse|căn\s*(?:hộ)?\s*[-a-z0-9]+)(?:$|[-,\s;./]|\b)"""
    )
    private val LABEL_REGEX = Regex("""(?i)(?:^|[-,\s;./]|\b)(?:[đĐdD]ịa\s*điểm|dia\s*diem|[đĐdD]ịa\s*chỉ|dia\s*chi|[đĐdD]c|dc|address|location|nơi\s*tổ\s*chức|cơ\s*sở|chi\s*nhánh)\s*[:=.-]?""")
    private val LABEL_START_REGEX = Regex(
        """^\s*(?:$BULLET_PATTERN)?\s*(?:thời\s*gian\s*&\s*)?(?:địa\s*điểm|dia\s*diem|địa\s*chỉ|dia\s*chi|[đĐdD]c|dc|address|location|nơi\s*tổ\s*chức|cơ\s*sở)\s*(?:sự\s*kiện)?\s*[:=.-]?""",
        RegexOption.IGNORE_CASE
    )

    private val PLACE_PREFIXES = listOf(
        "quán", "cửa hàng", "nhà hàng", "tiệm", "cafe", "cà phê", "phê la",
        "kichi", "kfc", "lotteria", "highlands", "the coffee house", "chi nhánh", "cơ sở"
    )

    private val PLACE_FORBIDDEN_WORDS = listOf(
        "hôm nay", "hom nay", "hôm qua", "hom qua", "nghỉ", "nghi", "đóng cửa", "dong cua",
        "mở cửa", "mo cua", "xin lỗi", "xin loi", "thông báo", "thong bao", "tuyển", "tuyen"
    )

    private data class AddressCandidate(
        val topIdx: Int,
        val bottomIdx: Int,
        val fullAddress: String,
        val score: Int
    )

    fun extractAddresses(
        rawOcrText: String,
        ocrLines: List<OcrLine>? = null
    ): List<ExtractedAddress> {
        if (rawOcrText.isBlank()) return emptyList()

        val normalized = Normalizer.normalize(rawOcrText, Normalizer.Form.NFC)
        val lines = normalized.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val candidates = mutableListOf<AddressCandidate>()

        // Scan from bottom to top for administrative anchors
        for (i in lines.indices.reversed()) {
            val line = lines[i]
            val lowerLine = line.lowercase(Locale.getDefault())

            // Skip lines with generic non-address noise or other section headers (Thời gian, Nội dung, etc.)
            if (NEGATIVE_WORDS.any { lowerLine.contains(it) }) continue
            if (SECTION_HEADER_REGEX.containsMatchIn(line)) continue

            // Spatial filter: Header area (top navigation, buttons like Log In / Open app)
            if (ocrLines != null && ocrLines.size == lines.size) {
                if (ocrLines[i].top < 0.08f) continue
            }

            val selfHasAnchor = AdminUnits.hasAdminAnchor(line)
            val prevHasAnchor = i > 0 && AdminUnits.hasAdminAnchor(lines[i - 1])
            val sameBlockWithPrev = i > 0 && (ocrLines == null || ocrLines.size != lines.size || ocrLines[i].blockId == ocrLines[i - 1].blockId)

            val combinedHasAnchor = if (!selfHasAnchor && i > 0 && !prevHasAnchor && sameBlockWithPrev) {
                AdminUnits.hasAdminAnchor("${lines[i - 1]} $line")
            } else false

            val hasAnchor = selfHasAnchor || combinedHasAnchor

            if (hasAnchor) {
                val anchorStartLine = if (!selfHasAnchor && i > 0) i - 1 else i
                var bottomIdx = i

                // Check if line i+1 is a standalone province name (e.g. "Hà Nội" on next line)
                if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1]
                    val nextLower = nextLine.lowercase(Locale.getDefault())
                    val nextSameBlock = ocrLines == null || ocrLines.size != lines.size || ocrLines[i + 1].blockId == ocrLines[anchorStartLine].blockId
                    if (nextSameBlock && AdminUnits.LEGACY_VN_CITIES.any { nextLower == it.lowercase(Locale.getDefault()) } &&
                        !NEGATIVE_WORDS.any { nextLower.contains(it) }) {
                        bottomIdx = i + 1
                    }
                }

                // Enforce spatial block cohesion on bottomIdx
                if (ocrLines != null && ocrLines.size == lines.size) {
                    if (ocrLines[bottomIdx].blockId != ocrLines[anchorStartLine].blockId) {
                        bottomIdx = anchorStartLine
                    }
                }

                var topIdx = anchorStartLine

                // If anchorStartLine is already a definitive address start, no need to walk upwards
                val anchorStartsDefinitive = isDefinitiveAddressStart(lines[anchorStartLine])

                if (!anchorStartsDefinitive) {
                    // Walk upwards up to 3 lines
                    for (step in 1..3) {
                        val candidateIdx = anchorStartLine - step
                        if (candidateIdx < 0) break

                        val curLine = lines[candidateIdx]
                        val curLower = curLine.lowercase(Locale.getDefault())

                        if (NEGATIVE_WORDS.any { curLower.contains(it) }) break
                        if (SECTION_HEADER_REGEX.containsMatchIn(curLine)) break

                        // Stop if we hit a different block in structured OCR or header
                        if (ocrLines != null && ocrLines.size == lines.size) {
                            if (candidateIdx < anchorStartLine && ocrLines[candidateIdx].blockId != ocrLines[anchorStartLine].blockId) {
                                break
                            }
                            if (ocrLines[candidateIdx].top < 0.08f) {
                                break
                            }
                        }

                        val hasComponent = hasAddressComponent(curLine) || AdminUnits.hasAdminAnchor(curLine)
                        val isContinuation = curLine.length < 80 && !curLine.endsWith(".") && !curLine.endsWith("!")

                        if (hasComponent || (isContinuation && step <= 1)) {
                            topIdx = candidateIdx
                            if (isDefinitiveAddressStart(curLine)) {
                                break
                            }
                        } else {
                            break
                        }
                    }
                }

                // Apply Hierarchical Grammar FSM Boundary Termination on anchor line
                val anchorLine = lines[bottomIdx]
                val rightmostBoundary = AdminUnits.findRightmostAdminBoundary(anchorLine)
                val cleanAnchorLine = if (rightmostBoundary != -1 && rightmostBoundary < anchorLine.length) {
                    anchorLine.substring(0, rightmostBoundary).trim()
                } else {
                    anchorLine.trim()
                }

                val windowParts = mutableListOf<String>()
                for (idx in topIdx until bottomIdx) {
                    windowParts.add(lines[idx])
                }
                if (cleanAnchorLine.isNotBlank()) {
                    windowParts.add(cleanAnchorLine)
                }

                val reconstructed = reconstructAddressWindow(windowParts)
                val cleanAddr = cleanAddressSuffix(cleanAddressPrefix(reconstructed))

                // Validation criteria:
                // 1. Must have Admin anchor
                // 2. Must have house no / street / alley / building / POI OR explicit label
                // 3. Must not be pure province or under minimum length
                if (isValidAddress(cleanAddr)) {
                    val score = scoreAddress(cleanAddr)
                    candidates.add(
                        AddressCandidate(
                            topIdx = topIdx,
                            bottomIdx = bottomIdx,
                            fullAddress = cleanAddr,
                            score = score
                        )
                    )
                }
            }
        }

        if (candidates.isEmpty()) return emptyList()

        // 1. Sort candidates by score descending, then by length descending
        val sortedCandidates = candidates.sortedWith(
            compareByDescending<AddressCandidate> { it.score }
                .thenByDescending { it.fullAddress.length }
        )

        // 2. Interval Non-Maximum Suppression (NMS) & Text Substring Deduplication
        val selected = mutableListOf<AddressCandidate>()
        for (cand in sortedCandidates) {
            val overlaps = selected.any { existing ->
                // Line interval overlap (same physical region in document)
                val lineOverlap = (cand.topIdx <= existing.bottomIdx && existing.topIdx <= cand.bottomIdx)
                // Adjacent line sub-entity (e.g. title line on top of full address line)
                val adjacentSubEntity = (Math.abs(cand.topIdx - existing.bottomIdx) <= 1 || Math.abs(existing.topIdx - cand.bottomIdx) <= 1) &&
                        (cand.score < existing.score) &&
                        areAddressesSharingTokens(cand.fullAddress, existing.fullAddress)
                // Semantic / text containment overlap (sub-window or identical address)
                val textOverlap = areAddressesOverlapping(cand.fullAddress, existing.fullAddress)
                lineOverlap || adjacentSubEntity || textOverlap
            }
            if (!overlaps) {
                selected.add(cand)
            }
        }

        // 3. Re-sort in natural visual reading order (top-to-bottom of the image)
        val orderedCandidates = selected.sortedBy { it.topIdx }

        // 4. Cap at maximum 4 cards per image to avoid UI clutter
        val cappedCandidates = orderedCandidates.take(4)

        // 5. Build ExtractedAddress objects
        return cappedCandidates.map { cand ->
            val placeName = extractPlaceName(lines, cand.topIdx, cand.fullAddress, ocrLines)
            val detectedCity = AdminUnits.detectCity(cand.fullAddress)
            val adminSystem = AdminUnits.detectAdminSystem(cand.fullAddress)

            ExtractedAddress(
                fullAddress = cand.fullAddress,
                placeName = placeName,
                city = detectedCity,
                adminSystem = adminSystem
            )
        }
    }

    fun extractAddress(
        rawOcrText: String,
        ocrLines: List<OcrLine>? = null
    ): ExtractedAddress? {
        val addresses = extractAddresses(rawOcrText, ocrLines)
        return addresses.maxByOrNull { scoreAddress(it.fullAddress) }
    }

    private fun areAddressesOverlapping(addr1: String, addr2: String): Boolean {
        val norm1 = VietnameseUtils.removeDiacritics(addr1.lowercase(Locale.getDefault())).trim()
        val norm2 = VietnameseUtils.removeDiacritics(addr2.lowercase(Locale.getDefault())).trim()
        if (norm1 == norm2) return true

        val (longer, shorter) = if (norm1.length >= norm2.length) norm1 to norm2 else norm2 to norm1
        if (shorter.length >= 8) {
            val escaped = Regex.escape(shorter)
            val regex = Regex("""(?:^|[,;()\-\s])\s*$escaped\s*(?:$|[,;()\-\s])""")
            if (regex.containsMatchIn(longer)) {
                return true
            }
        }
        return false
    }

    private fun areAddressesSharingTokens(addr1: String, addr2: String): Boolean {
        val norm1 = VietnameseUtils.removeDiacritics(addr1.lowercase(Locale.getDefault()))
        val norm2 = VietnameseUtils.removeDiacritics(addr2.lowercase(Locale.getDefault()))
        val tokens1 = norm1.split(Regex("""[\s,;./\-()]+""")).filter { it.length >= 4 }
        val tokens2 = norm2.split(Regex("""[\s,;./\-()]+""")).filter { it.length >= 4 }.toSet()
        val common = tokens1.count { it in tokens2 }
        return common >= 2 || (common >= 1 && (norm1.contains("aeon") || norm1.contains("vincom") || norm1.contains("lotte")))
    }

    private fun hasMultiAdminWithStreet(text: String): Boolean {
        val lower = text.lowercase(Locale.getDefault())
        val hasDistrict = AdminUnits.KNOWN_DISTRICTS.any { lower.contains(it.lowercase(Locale.getDefault())) } ||
                Regex("""(?i)(?:^|[\s,;./\-]|\b)(?:quận|huyện|thị\s*xã|q\.|q\s*\d+)(?:$|[\s,;./\-]|\b)""").containsMatchIn(lower)
        val hasCity = AdminUnits.detectCity(text).isNotBlank()
        val segments = text.split(",").map { it.trim() }.filter { it.isNotBlank() }
        return hasDistrict && hasCity && segments.size >= 3
    }

    private fun isDefinitiveAddressStart(line: String): Boolean {
        // If line starts with a number followed by an administrative prefix (e.g. "1, TP. Hồ Chí Minh" from "Quận\n1, TP.HCM"),
        // it is a wrapped administrative unit, NOT a house number!
        if (Regex("""^\s*\d+[a-zA-Z]?\s*,\s*(?:TP\.?|thành\s*phố|tỉnh|quận|huyện|phường|xã)\b""", RegexOption.IGNORE_CASE).containsMatchIn(line)) {
            return false
        }
        val cleanLine = line.replace(Regex("""^\s*(?:$BULLET_PATTERN)\s*"""), "").trim()
        return HOUSE_NO_START_REGEX.containsMatchIn(cleanLine) ||
                LABEL_START_REGEX.containsMatchIn(line) ||
                BUILDING_REGEX.containsMatchIn(cleanLine)
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

        // Must have at least one specific street-level component, house number, POI, or explicit label
        val hasSpecificComponent = HOUSE_NO_REGEX.containsMatchIn(text) ||
                ALLEY_REGEX.containsMatchIn(text) ||
                STREET_REGEX.containsMatchIn(text) ||
                BUILDING_REGEX.containsMatchIn(text) ||
                (LABEL_REGEX.containsMatchIn(text) && (text.any { it.isDigit() } || BUILDING_REGEX.containsMatchIn(text))) ||
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
        if (BUILDING_REGEX.containsMatchIn(text)) score += 3
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

        // If cur is common UI English button words, do not wrap
        val lowerCur = cur.lowercase(Locale.getDefault())
        if (lowerCur == "log in" || lowerCur == "open app" || lowerCur == "trang web" || lowerCur == "share") {
            return false
        }

        // If cur is a short continuation token without commas
        if (cur.split(" ").size <= 2 && cur.length <= 15 && !cur.contains(",")) {
            return true
        }
        return false
    }

    private fun cleanAddressPrefix(line: String): String {
        return line
            .replace(Regex("""^\s*(?:$BULLET_PATTERN)\s*"""), "")
            .replace(
                Regex(
                    """^(?:thời\s*gian\s*&\s*)?(?:địa\s*điểm|dia\s*diem|địa\s*chỉ|dia\s*chi|location|address|nơi\s*tổ\s*chức|cơ\s*sở)\s*(?:sự\s*kiện)?\s*[:=.-]?\s*""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .replace(Regex("""^\s*(?:$BULLET_PATTERN)\s*"""), "")
            .replace(Regex(""",\s*,"""), ",")
            .replace(Regex("""\s+,\s*"""), ", ")
            .replace(Regex("""(?:[,\s]+)\b\d{5,6}\b$"""), "")
            .trim()
    }

    private fun cleanAddressSuffix(line: String): String {
        return line
            .replace(Regex("""(?i)\s*[|•\-]\s*\d{1,2}(?:\s*[-–/]\s*\d{1,2})?[./\-]\d{2,4}.*$"""), "")
            .replace(Regex("""(?i)\s*-\s*Website:.*$"""), "")
            .trim()
    }

    /**
     * Resolves place name from explicit POI patterns in the address or from the line immediately above.
     */
    private fun extractPlaceName(
        lines: List<String>,
        topAddressLineIdx: Int,
        bestAddress: String,
        ocrLines: List<OcrLine>?
    ): String {
        // 1. Try to extract from POI prefix in bestAddress (e.g. "Hoàng Thành Thăng Long (19C Hoàng Diệu...)")
        val poiInParen = Regex("""^([^(),]{3,40})\s*\(""").find(bestAddress)
        if (poiInParen != null) {
            val candidate = poiInParen.groupValues[1].trim()
            val isStreetOrHouse = HOUSE_NO_REGEX.containsMatchIn(candidate) ||
                    ALLEY_REGEX.containsMatchIn(candidate) ||
                    STREET_REGEX.containsMatchIn(candidate) ||
                    AdminUnits.hasAdminAnchor(candidate)
            if (candidate.length in 4..60 && !isStreetOrHouse) {
                return candidate
            }
        }

        val poiBeforeComma = Regex(
            """^(sân\s*vận\s*động|nhà\s*thi\s*đấu|công\s*viên|quảng\s*trường|phố\s*đi\s*bộ|khu\s*du\s*lịch|hoàng\s*thành|bảo\s*tàng|nhà\s*hát|chùa|nhà\s*thờ|đền|di\s*tích|bệnh\s*viện|viện\s*huyết\s*học|viện\s*tim|phòng\s*khám|đại\s*học|trường\s*đại\s*học|học\s*viện|vincom|lotte|aeon|landmark|toà\s*nhà|tòa\s*nhà|chung\s*cư)[^,]{2,35}(?=,)""",
            RegexOption.IGNORE_CASE
        ).find(bestAddress)
        if (poiBeforeComma != null) {
            return poiBeforeComma.value.trim()
        }

        // 2. Fall back to line immediately above
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
