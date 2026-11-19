package com.example.test2.ai.cards

import com.example.test2.ai.OcrLine
import java.util.Locale

object ContactNamer {

    // Explicit name labels with word boundaries for short tokens
    private val EXPLICIT_NAME_LABEL_REGEX = Regex(
        """(?i)(?:^|[\s,;./\-]|\b)(?:họ\s*(?:và\s*)?tên|ho\s*(?:va\s*)?ten|người\s*liên\s*hệ|nguoi\s*lien\s*he|người\s*nhận|nguoi\s*nhan|liên\s*hệ|lien\s*he|contact(?:\s*person)?|\b(?:mr|ms|mrs|anh|chị|cô|chú)\b)\s*[:=.-]?\s*([^\n\r,;|]+)"""
    )

    // Functional service labels that should NEVER be treated as a person's name
    private val FUNCTIONAL_SERVICE_LABELS = listOf(
        "hotline", "cskh", "tư vấn", "tu van", "hỗ trợ", "ho tro",
        "đặt bàn", "dat ban", "cấp cứu", "cap cuu", "tổng đài", "tong dai",
        "phòng khám", "bệnh viện", "trung tâm", "chi nhánh"
    )

    // Words indicating e-commerce, ads, logistics, or slogans that must never be in a contact name
    private val PROHIBITED_NAME_WORDS = listOf(
        "sản phẩm", "san pham", "hàng", "hang", "bài", "bai", "công ty", "cong ty",
        "vận chuyển", "van chuyen", "miễn phí", "mien phi", "cam kết", "cam ket",
        "chính hãng", "chinh hang", "giảm giá", "giam gia", "khuyến mãi", "khuyen mai",
        "bài viết", "bai viet", "đơn hàng", "don hang", "thành viên", "thanh vien",
        "giao hàng", "giao hang", "ưu đãi", "uu dai", "bảo hành", "bao hanh",
        "chất lượng", "chat luong", "dịch vụ", "dich vu", "website", "facebook"
    )

    private val COMPANY_REGEX = Regex(
        """(?i)(?:công\s*ty|cong\s*ty|tnhh|cổ\s*phần|co\s*phan|corp|corporation|group|doanh\s*nghiệp)\s*[:=]?\s*([^\n\r]+)"""
    )

    /**
     * Resolves contact names for extracted phones.
     * Returns a map of phone canonical to resolved person name.
     */
    fun resolveNames(
        rawOcrText: String,
        phones: List<PhoneHit>,
        ocrLines: List<OcrLine>? = null
    ): Map<String, String> {
        if (phones.isEmpty() || rawOcrText.isBlank()) return emptyMap()

        val lines = rawOcrText.lines().map { it.trim() }
        val nameMap = mutableMapOf<String, String>()

        // 1. First check if a phone has a name on its own line
        for (phone in phones) {
            if (phone.lineIndex in lines.indices) {
                val line = lines[phone.lineIndex]
                val sameLineName = extractNameFromLine(line, phone.payloadNumber)
                if (sameLineName != null) {
                    nameMap[phone.canonical] = sameLineName
                }
            }
        }

        // 2. Look for standalone name declarations: "Người liên hệ: Nguyễn Văn Nam"
        var standaloneName: String? = null
        for (i in lines.indices) {
            val line = lines[i]
            // Skip lines that have a phone number if we already processed same-line
            val match = EXPLICIT_NAME_LABEL_REGEX.find(line)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                val validated = validateNameCandidate(candidate)
                if (validated != null) {
                    standaloneName = validated
                    break
                }
            }
        }

        // Attach standalone name to the first phone that doesn't have a name yet
        if (standaloneName != null) {
            val firstUnassigned = phones.firstOrNull { it.canonical !in nameMap }
            if (firstUnassigned != null) {
                nameMap[firstUnassigned.canonical] = standaloneName
            }
        }

        // 3. If ocrLines with coordinates are available, check immediately adjacent lines
        if (ocrLines != null && ocrLines.isNotEmpty()) {
            for (phone in phones) {
                if (phone.canonical in nameMap) continue
                val phoneOcrLine = ocrLines.getOrNull(phone.lineIndex) ?: continue

                // Check preceding line in the same block if close vertically
                if (phone.lineIndex > 0) {
                    val prevOcrLine = ocrLines[phone.lineIndex - 1]
                    val isSameBlock = prevOcrLine.blockId == phoneOcrLine.blockId
                    val verticalDistance = phoneOcrLine.top - prevOcrLine.bottom
                    val lineHeight = phoneOcrLine.bottom - phoneOcrLine.top

                    if (isSameBlock && verticalDistance >= 0 && verticalDistance <= 1.5f * lineHeight) {
                        val match = EXPLICIT_NAME_LABEL_REGEX.find(prevOcrLine.text)
                        if (match != null) {
                            val candidate = match.groupValues[1].trim()
                            val validated = validateNameCandidate(candidate)
                            if (validated != null) {
                                nameMap[phone.canonical] = validated
                            }
                        }
                    }
                }
            }
        }

        return nameMap
    }

    fun extractCompany(rawOcrText: String): String? {
        val match = COMPANY_REGEX.find(rawOcrText) ?: return null
        return match.value.trim().take(60)
    }

    private fun extractNameFromLine(line: String, phonePayload: String): String? {
        val match = EXPLICIT_NAME_LABEL_REGEX.find(line) ?: return null
        var candidate = match.groupValues[1].trim()

        // Remove phone number if it follows on the same line
        val phoneIndex = candidate.indexOf(phonePayload)
        if (phoneIndex >= 0) {
            candidate = candidate.substring(0, phoneIndex).trim()
        }

        return validateNameCandidate(candidate)
    }

    private fun validateNameCandidate(candidate: String): String? {
        var clean = candidate.trim().trim(':', '-', '.', ',', ';', '|')
        if (clean.length < 3 || clean.length > 40) return null

        // Must not contain any digits
        if (clean.any { it.isDigit() }) return null

        val lower = clean.lowercase(Locale.getDefault())

        // Check if candidate is a functional label (Hotline, CSKH, etc.)
        if (FUNCTIONAL_SERVICE_LABELS.any { lower.contains(it) }) return null

        // Check against prohibited noise words
        if (PROHIBITED_NAME_WORDS.any { lower.contains(it) }) return null

        val words = clean.split(Regex("""\s+""")).filter { it.isNotBlank() }
        // Person name should typically be between 2 and 5 words
        if (words.size !in 2..5) return null

        return clean
    }
}
