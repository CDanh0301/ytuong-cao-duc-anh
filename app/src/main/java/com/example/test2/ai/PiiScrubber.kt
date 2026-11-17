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

    // Regex for Vietnamese bank accounts (Phase 5: 8-16 digits following banking account keywords)
    private val BANK_ACCOUNT_REGEX = Regex("(?i)\\b(stk|số\\s*tk|so\\s*tk|tài\\s*khoản(?:\\s*ngân\\s*hàng)?|tai\\s*khoan|account(?:\\s*no\\.?|\\s*number)?|acct)\\s*[:=]?\\s*([0-9]{8,16})\\b")

    // Regex for Vietnamese phone numbers (03/05/07/08/09 or +84)
    private val VN_PHONE_REGEX = Regex("(?<!\\d)(?:(?:\\+84|0)(?:3[2-9]|5[25689]|7[06-9]|8[1-9]|9[0-9]))[\\s.-]?\\d{3}[\\s.-]?\\d{4}(?!\\d)")

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

        // 1. Redact credit card numbers
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

        // 1b. Redact Emails (Phase 5)
        if (EMAIL_REGEX.containsMatchIn(result)) {
            result = EMAIL_REGEX.replace(result) {
                redacted = true
                "[EMAIL_REDACTED]"
            }
        }

        // 1c. Redact Bank Account numbers (Phase 5)
        if (BANK_ACCOUNT_REGEX.containsMatchIn(result)) {
            result = BANK_ACCOUNT_REGEX.replace(result) { match ->
                redacted = true
                val label = match.groupValues[1]
                val acc = match.groupValues[2]
                val last4 = if (acc.length >= 4) acc.takeLast(4) else acc
                "$label: STK_****$last4"
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
        if (VN_PHONE_REGEX.containsMatchIn(result)) {
            result = VN_PHONE_REGEX.replace(result) { match ->
                redacted = true
                val digits = match.value.filter { it.isDigit() }
                val suffix = digits.takeLast(3)
                "***-***-$suffix"
            }
        } else if (PHONE_REGEX.containsMatchIn(result)) {
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
