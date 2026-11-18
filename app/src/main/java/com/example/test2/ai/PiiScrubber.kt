package com.example.test2.ai

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

    // Regex for Vietnamese phone numbers (4-3-3, 3-3-4, continuous, landlines)
    private val VN_PHONE_REGEXES = listOf(
        Regex("""(?<!\d)(?:(?:\+84|0)(?:3[2-9]|5[25689]|7[06-9]|8[1-9]|9[0-9]))\d[\s.-]\d{3}[\s.-]\d{3}(?!\d)"""),
        Regex("""(?<!\d)(?:(?:\+84|0)(?:3[2-9]|5[25689]|7[06-9]|8[1-9]|9[0-9]))[\s.-]?\d{3}[\s.-]?\d{4}(?!\d)"""),
        Regex("""(?<!\d)(?:(?:\+84|0)(?:3[2-9]|5[25689]|7[06-9]|8[1-9]|9[0-9]))\d{7}(?!\d)"""),
        Regex("""(?<!\d)02[48][\s.-]?\d{4}[\s.-]?\d{4}(?!\d)""")
    )

    // General international phone numbers fallback
    private val PHONE_REGEX = Regex("(?<!\\d)(?:\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}(?!\\d)")

    // Regex for Passwords / OTP codes
    private val OTP_PASSWORD_REGEX = Regex("(?i)\\b(otp|pass(?:word)?|mật\\s*khẩu|code|pin)\\s*[:=]?\\s*([a-zA-Z0-9]{4,10})\\b")

    // Regex for national ID / CCCD (9 or 12 digits)
    private val ID_CARD_REGEX = Regex("\\b\\d{9}\\b|\\b\\d{12}\\b")

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

        // 1c. Redact Emails (Phase 5)
        if (EMAIL_REGEX.containsMatchIn(result)) {
            result = EMAIL_REGEX.replace(result) {
                redacted = true
                "[EMAIL_REDACTED]"
            }
        }

        // 2. Redact Passwords / OTP
        if (OTP_PASSWORD_REGEX.containsMatchIn(result)) {
            result = OTP_PASSWORD_REGEX.replace(result) { match ->
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

        // 4. Redact ID numbers (CCCD/Passport)
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
}
