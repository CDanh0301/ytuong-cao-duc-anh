package com.example.test2.ai

import java.util.Locale

data class PiiScrubResult(
    val scrubbedText: String,
    val hasPiiRedacted: Boolean
)

object PiiScrubber {
    // Regex for credit cards (13-16 digits with optional dashes/spaces)
    private val CREDIT_CARD_REGEX = Regex("\\b(?:\\d[ -]*?){13,16}\\b")

    // Regex for emails (Phase 5)
    private val EMAIL_REGEX = Regex("(?i)\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b")

    // Regex for Vietnamese bank accounts (Phase 5: 8-19 digits with optional spaces/dashes/dots following banking account keywords)
    private val BANK_ACCOUNT_REGEX = Regex("(?i)\\b(stk|số\\s*tk|so\\s*tk|tài\\s*khoản(?:\\s*thụ\\s*hưởng|\\s*nhận|\\s*ngân\\s*hàng)?|tai\\s*khoan|account(?:\\s*no\\.?|\\s*number)?|acct|tk\\s*thụ\\s*hưởng|tk\\s*nhận)\\s*[:=]?\\s*([0-9][0-9 .\\-]{6,22}[0-9])")

    // Context-aware standalone bank account regex (8-19 digits in banking context without explicit STK prefix)
    private val STANDALONE_BANK_DIGITS_REGEX = Regex("(?<!\\d)(\\d{3,4}(?:[ .\\-]\\d{3,4}){1,4}|\\d{8,19})(?!\\d)")

    private val BANKING_CONTEXT_REGEX = Regex(
        "(?i)\\b(?:vietcombank|vcb|techcombank|tcb|mb\\s*bank|mbbank|bidv|vietinbank|vpbank|acb|tpbank|sacombank|vib|hdbank|ocb|shb|seabank|msb|agribank|eximbank|lpbank|pvcombank|momo|zalopay|viettelpay|digibank|smartbanking|internet\\s*banking|chuyển\\s*khoản|chuyen\\s*khoan|chuyển\\s*tiền|chuyen\\s*tien|thụ\\s*hưởng|thu\\s*huong|số\\s*dư|so\\s*du|ngân\\s*hàng|ngan\\s*hang)\\b"
    )

    private val CURRENCY_FOLLOWING_REGEX = Regex("(?i)^\\s*(?:đ|₫|vnd|vnđ|k\\b|\\$|usd\\b)")
    private val DATE_LIKE_REGEX = Regex("^\\d{1,4}[.\\-/]\\d{1,2}[.\\-/]\\d{2,4}$")
    private val BANK_LINE_EXCLUSION_REGEX = Regex(
        "(?i)\\b(?:zoom|meeting|id\\s*cuộc\\s*họp|mã\\s*đơn|ma\\s*don|order|tracking|mã\\s*vận\\s*đơn|ma\\s*van\\s*don|mã\\s*khách\\s*hàng|ma\\s*khach\\s*hang|mã\\s*giao\\s*dịch|ma\\s*giao\\s*dich|hóa\\s*đơn|hoa\\s*don|invoice|bill|hotline|tổng\\s*đài|tong\\s*dai|sđt|sdt|tel|phone|đt|cccd|cmnd|căn\\s*cước|can\\s*cuoc|chứng\\s*minh|chung\\s*minh|hộ\\s*chiếu|ho\\s*chieu|passport|gplx|thẻ\\s*tín\\s*dụng|the\\s*tin\\s*dung|visa|mastercard|jcb)\\b"
    )

    private val CARD_PREFIX_OR_16_REGEX = Regex("^(?:4\\d{12}(?:\\d{3})?|5[1-5]\\d{14}|3[47]\\d{13}|6(?:011|5\\d{2})\\d{12})$")

    // Regex for Vietnamese phone numbers (4-3-3, 3-3-4, continuous, landlines)
    private val VN_PHONE_REGEXES = listOf(
        Regex("""(?<!\d)(?:(?:\+84|0)(?:3[2-9]|5[25689]|7[06-9]|8[1-9]|9[0-9]))\d[\s.-]\d{3}[\s.-]\d{3}(?!\d)"""),
        Regex("""(?<!\d)(?:(?:\+84|0)(?:3[2-9]|5[25689]|7[06-9]|8[1-9]|9[0-9]))[\s.-]?\d{3}[\s.-]?\d{4}(?!\d)"""),
        Regex("""(?<!\d)(?:(?:\+84|0)(?:3[2-9]|5[25689]|7[06-9]|8[1-9]|9[0-9]))\d{7}(?!\d)"""),
        Regex("""(?<!\d)02[48][\s.-]?\d{4}[\s.-]?\d{4}(?!\d)""")
    )

    // General international phone numbers fallback (requires at least one separator or '+' if > 10 digits so 12-digit CCCD is never misclassified)
    private val PHONE_REGEX = Regex("(?<!\\d)(?:\\+\\d{1,3}[-.\\s]?|\\(\\d{3}\\)[-.\\s]?)\\d{3}[-.\\s]?\\d{3,4}[-.\\s]?\\d{4}(?!\\d)|(?<!\\d)\\d{3}[-.\\s]\\d{3}[-.\\s]\\d{4}(?!\\d)|(?<!\\d)\\d{10}(?!\\d)")

    // Strong secret credentials (allows special chars & longer passwords, with optional VN/EN filler)
    private val STRONG_SECRET_REGEX = Regex(
        "(?i)\\b(pass(?:word)?|mật\\s*khẩu|mat\\s*khau|api[\\s_-]*key|secret(?:[\\s_-]*key)?|private[\\s_-]*key|recovery(?:\\s*code)?|mã\\s*khôi\\s*phục|ma\\s*khoi\\s*phuc)(?:\\s+(?:của\\s+(?:bạn|quý\\s*khách)\\s+)?(?:là|la|is))?\\s*[:=]?\\s*([^\\s\\r\\n]{4,32})"
    )

    // OTP / PIN / verification codes with optional VN/EN filler
    private val OTP_PASSWORD_REGEX = Regex(
        "(?i)\\b(otp|mã\\s*otp|ma\\s*otp|mã\\s*xác\\s*thực|ma\\s*xac\\s*thuc|mã\\s*xác\\s*nhận|ma\\s*xac\\s*nhan|mã\\s*pin|ma\\s*pin|pin|mã\\s*bảo\\s*mật|ma\\s*bao\\s*mat)(?:\\s+(?:của\\s+(?:bạn|quý\\s*khách)\\s+)?(?:là|la|is))?\\s*[:=]?\\s*([a-zA-Z0-9]{4,10})\\b"
    )

    // Explicit `code: 123456` style secrets (requires `:` or `=` and numeric/short code, avoiding "QR code" / "source code")
    private val LABELED_CODE_REGEX = Regex(
        "(?i)(?<!qr\\s)(?<!source\\s)(?<!promo\\s)(?<!voucher\\s)(?<!coupon\\s)(?<!mã\\s)(?<!ma\\s)\\b(code)\\s*[:=]\\s*([0-9]{4,8}|[a-zA-Z0-9]{4,10})\\b"
    )

    // Card security codes (CVV/CVC/CSC)
    private val CVV_REGEX = Regex(
        "(?i)\\b(cvv|cvc|csc|security\\s*code)(?:\\s+(?:là|la|is))?\\s*[:=]?\\s*(\\d{3,4})\\b"
    )

    // Passport numbers when passport keywords are present
    private val PASSPORT_CONTEXT_REGEX = Regex("(?i)\\b(?:passport|hộ\\s*chiếu|ho\\s*chieu|xuất\\s*nhập\\s*cảnh|xuat\\s*nhap\\s*canh)\\b")
    private val PASSPORT_NUMBER_REGEX = Regex("\\b([A-Z]\\d{7,8})\\b")

    // Identity document context for spaced CCCD/CMND numbers
    private val ID_DOC_CONTEXT_REGEX = Regex(
        "(?i)\\b(?:cccd|cmnd|căn\\s*cước|can\\s*cuoc|chứng\\s*minh|chung\\s*minh|thẻ\\s*căn\\s*cước|the\\s*can\\s*cuoc|identity\\s*card|national\\s*id|gplx|bằng\\s*lái|bang\\s*lai)\\b"
    )
    private val SPACED_ID_CARD_REGEX = Regex("\\b(\\d{3}[ .]\\d{3}[ .]\\d{3}(?:[ .]\\d{3})?)\\b")

    // Regex for national ID / CCCD (9 or 12 digits)
    private val ID_CARD_REGEX = Regex("\\b\\d{9}\\b|\\b\\d{12}\\b")

    // Cloud-strict cleanup regexes removing trailing 3-4 digits from local masks
    private val CLOUD_MASKED_BANK_REGEX = Regex("STK_\\*{4}\\d{4}")
    private val CLOUD_MASKED_CARD_REGEX = Regex("\\*{4}-\\*{4}-\\*{4}-\\d{4}")
    private val CLOUD_MASKED_PHONE_REGEX = Regex("\\*{3}-\\*{3}-\\d{3}")
    private val CLOUD_MASKED_ID_REGEX = Regex("ID_\\*{4}\\d{4}")

    fun scrub(rawText: String): PiiScrubResult {
        if (rawText.isBlank()) return PiiScrubResult(rawText, false)

        var result = rawText
        var redacted = false

        // 1. Redact Bank Account numbers (prefixed with STK, tài khoản...)
        if (BANK_ACCOUNT_REGEX.containsMatchIn(result)) {
            result = BANK_ACCOUNT_REGEX.replace(result) { match ->
                val label = match.groupValues[1]
                val rawDigits = match.groupValues[2].filter { it.isDigit() }
                if (rawDigits.length in 8..19) {
                    redacted = true
                    val last4 = rawDigits.takeLast(4)
                    "$label: STK_****$last4"
                } else {
                    match.value
                }
            }
        }

        // 1a. Context-aware standalone bank accounts (in banking context without explicit STK label)
        if (BANKING_CONTEXT_REGEX.containsMatchIn(result)) {
            result = STANDALONE_BANK_DIGITS_REGEX.replace(result) { match ->
                val rawValue = match.value
                val digits = rawValue.filter { it.isDigit() }
                if (digits.length !in 8..19) {
                    return@replace rawValue
                }
                if (DATE_LIKE_REGEX.matches(rawValue)) {
                    return@replace rawValue
                }
                if (digits.length == 8 && (digits.startsWith("19") || digits.startsWith("20"))) {
                    return@replace rawValue
                }
                if (isVietnamesePhoneDigits(digits)) {
                    return@replace rawValue
                }
                val afterText = result.substring((match.range.last + 1).coerceAtMost(result.length))
                if (CURRENCY_FOLLOWING_REGEX.containsMatchIn(afterText)) {
                    return@replace rawValue
                }
                val lineStart = result.lastIndexOf('\n', match.range.first).let { if (it < 0) 0 else it + 1 }
                val prefixOnLine = result.substring(lineStart, match.range.first)
                if (prefixOnLine.endsWith("#") || BANK_LINE_EXCLUSION_REGEX.containsMatchIn(prefixOnLine.takeLast(30))) {
                    return@replace rawValue
                }
                if (CARD_PREFIX_OR_16_REGEX.matches(digits)) {
                    return@replace rawValue
                }
                redacted = true
                "STK_****${digits.takeLast(4)}"
            }
        }

        // 1b. Redact credit card numbers (13-16 digits)
        if (CREDIT_CARD_REGEX.containsMatchIn(result)) {
            result = CREDIT_CARD_REGEX.replace(result) { match ->
                val digits = match.value.filter { it.isDigit() }
                if (digits.length in 13..16) {
                    redacted = true
                    val last4 = digits.takeLast(4)
                    "****-****-****-$last4"
                } else {
                    match.value
                }
            }
        }

        // 1b2. Redact CVV/CVC/CSC
        if (CVV_REGEX.containsMatchIn(result)) {
            result = CVV_REGEX.replace(result) { match ->
                redacted = true
                "${match.groupValues[1]}: [REDACTED_SECRET]"
            }
        }

        // 1c. Redact Emails (Phase 5)
        if (EMAIL_REGEX.containsMatchIn(result)) {
            result = EMAIL_REGEX.replace(result) {
                redacted = true
                "[EMAIL_REDACTED]"
            }
        }

        // 2. Redact Strong Passwords / Secrets, OTP / PIN, and Labeled Code
        if (STRONG_SECRET_REGEX.containsMatchIn(result)) {
            result = STRONG_SECRET_REGEX.replace(result) { match ->
                val value = match.groupValues[2]
                if (value == "[REDACTED_SECRET]" || value == "[OTP_OR_SECRET]") {
                    match.value
                } else {
                    redacted = true
                    "${match.groupValues[1]}: [REDACTED_SECRET]"
                }
            }
        }

        if (OTP_PASSWORD_REGEX.containsMatchIn(result)) {
            result = OTP_PASSWORD_REGEX.replace(result) { match ->
                redacted = true
                "${match.groupValues[1]}: [REDACTED_SECRET]"
            }
        }

        if (LABELED_CODE_REGEX.containsMatchIn(result)) {
            result = LABELED_CODE_REGEX.replace(result) { match ->
                redacted = true
                "${match.groupValues[1]}: [REDACTED_SECRET]"
            }
        }

        // 3. Redact Phone numbers (Vietnamese specific and international)
        var vnPhoneMatched = false
        for (vnRegex in VN_PHONE_REGEXES) {
            if (vnRegex.containsMatchIn(result)) {
                vnPhoneMatched = true
                result = vnRegex.replace(result) { match ->
                    redacted = true
                    val digits = match.value.filter { it.isDigit() }
                    val suffix = digits.takeLast(3)
                    "***-***-$suffix"
                }
            }
        }
        if (!vnPhoneMatched && PHONE_REGEX.containsMatchIn(result)) {
            result = PHONE_REGEX.replace(result) { match ->
                redacted = true
                val digits = match.value.filter { it.isDigit() }
                val suffix = digits.takeLast(3)
                "***-***-$suffix"
            }
        }

        // 4. Redact Passport numbers (when passport context exists)
        if (PASSPORT_CONTEXT_REGEX.containsMatchIn(result) && PASSPORT_NUMBER_REGEX.containsMatchIn(result)) {
            result = PASSPORT_NUMBER_REGEX.replace(result) {
                redacted = true
                "[PASSPORT_NO]"
            }
        }

        // 4b. Redact spaced ID numbers when identity doc context exists
        if (ID_DOC_CONTEXT_REGEX.containsMatchIn(result) && SPACED_ID_CARD_REGEX.containsMatchIn(result)) {
            result = SPACED_ID_CARD_REGEX.replace(result) { match ->
                val digits = match.value.filter { it.isDigit() }
                if (digits.length == 9 || digits.length == 12) {
                    redacted = true
                    "ID_****${digits.takeLast(4)}"
                } else {
                    match.value
                }
            }
        }

        // 4c. Redact ID numbers (CCCD/CMND)
        if (ID_CARD_REGEX.containsMatchIn(result)) {
            result = ID_CARD_REGEX.replace(result) { match ->
                redacted = true
                "ID_****${match.value.takeLast(4)}"
            }
        }

        return PiiScrubResult(
            scrubbedText = result,
            hasPiiRedacted = redacted
        )
    }

    /**
     * Strict Cloud-safe scrubber: runs [scrub] and removes all trailing 3-4 digit suffixes
     * from masked tokens so zero real PII digits leave the device while preserving semantic context.
     */
    fun scrubForCloud(rawText: String): PiiScrubResult {
        if (rawText.isBlank()) return PiiScrubResult(rawText, false)

        val base = scrub(rawText)
        var cloudText = base.scrubbedText
        var cloudRedacted = base.hasPiiRedacted

        if (CLOUD_MASKED_BANK_REGEX.containsMatchIn(cloudText)) {
            cloudText = CLOUD_MASKED_BANK_REGEX.replace(cloudText, "[BANK_ACCOUNT]")
            cloudRedacted = true
        }
        if (CLOUD_MASKED_CARD_REGEX.containsMatchIn(cloudText)) {
            cloudText = CLOUD_MASKED_CARD_REGEX.replace(cloudText, "[CREDIT_CARD]")
            cloudRedacted = true
        }
        if (CLOUD_MASKED_PHONE_REGEX.containsMatchIn(cloudText)) {
            cloudText = CLOUD_MASKED_PHONE_REGEX.replace(cloudText, "[PHONE_NUMBER]")
            cloudRedacted = true
        }
        if (CLOUD_MASKED_ID_REGEX.containsMatchIn(cloudText)) {
            cloudText = CLOUD_MASKED_ID_REGEX.replace(cloudText, "[ID_NUMBER]")
            cloudRedacted = true
        }

        return PiiScrubResult(
            scrubbedText = cloudText,
            hasPiiRedacted = cloudRedacted
        )
    }

    private fun isVietnamesePhoneDigits(digits: String): Boolean {
        val normalized = if (digits.startsWith("84") && digits.length >= 11) {
            "0" + digits.substring(2)
        } else {
            digits
        }
        if (normalized.length == 10 && normalized.startsWith("0")) {
            val prefix2 = normalized.substring(0, 2)
            return prefix2 in setOf("03", "05", "07", "08", "09")
        }
        if (normalized.length == 11 && (normalized.startsWith("024") || normalized.startsWith("028"))) {
            return true
        }
        if (normalized.lowercase(Locale.ROOT).let { it.startsWith("1800") || it.startsWith("1900") }) {
            return normalized.length == 8 || normalized.length == 10
        }
        return false
    }
}
