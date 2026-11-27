package com.example.test2.ai

import com.example.test2.ai.cards.QualityFlags
import com.example.test2.shared.utils.VietnameseUtils
import java.util.Locale

enum class PrivacyDecision {
    LOCAL_ONLY,
    CLOUD_ELIGIBLE
}

object PrivacyPolicy {

    private val OTP_PATTERN = Regex("""(?i)\b(?:otp|mã\s*xác\s*thực|ma\s*xac\s*thuc|mã\s*xác\s*nhận|ma\s*xac\s*nhan)\D{0,20}\d{4,8}\b""")
    private val CVV_PIN_PATTERN = Regex("""(?i)\b(?:cvv|cvc|csc|security\s*code|mã\s*bảo\s*mật|ma\s*bao\s*mat|mã\s*pin|ma\s*pin)\D{0,15}\d{3,6}\b""")
    private val CREDIT_CARD_MASKED_PATTERN = Regex("""\*{4}-\*{4}-\*{4}-\d{4}""")
    private val RAW_CREDIT_CARD_PATTERN = Regex("""\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|3(?:0[0-5]|[68][0-9])[0-9]{11}|6(?:011|5[0-9]{2})[0-9]{12})\b""")
    private val EXPIRY_PATTERN = Regex("""(?i)\b(?:ngày\s*hết\s*hạn|ngay\s*het\s*han|valid\s*thru|expiry|exp\s*date)\b""")
    private val PASSPORT_KW_PATTERN = Regex("""(?i)\b(?:passport|hộ\s*chiếu|ho\s*chieu)\b""")
    private val PASSPORT_NUM_PATTERN = Regex("""\b[A-Z]\d{7,8}\b""")

    /**
     * Evaluates whether scrubbed photo text, title, and vision labels can be securely sent to Cloud AI.
     * Implements strict fail-closed privacy: sensitive personal identity, OTPs, and payment cards remain LOCAL_ONLY.
     */
    fun classify(
        scrubbedText: String,
        photoTitle: String = "",
        visualLabels: List<String> = emptyList()
    ): PrivacyDecision {
        if (!QualityFlags.CLOUD_PRIVACY_GATE) {
            return PrivacyDecision.CLOUD_ELIGIBLE
        }

        val combinedRaw = if (photoTitle.isNotBlank()) {
            "$photoTitle\n$scrubbedText"
        } else {
            scrubbedText
        }
        val textLower = combinedRaw.lowercase(Locale.getDefault())
        val textUnacc = VietnameseUtils.removeDiacritics(textLower)
        val combined = "$textLower $textUnacc"

        // 1. Identity Document Check:
        // Visual label "Identity document" OR >= 2 distinct signatures OR 1 signature + masked ID token OR passport with number
        val hasIdentityLabel = visualLabels.any { it.equals("Identity document", ignoreCase = true) }

        val matchedSigs = mutableSetOf<String>()
        for (sig in HeuristicKeywords.IDENTITY_DOC_SIGNATURES) {
            val unaccSig = VietnameseUtils.removeDiacritics(sig.lowercase(Locale.getDefault()))
            if (Regex("""\b${Regex.escape(unaccSig)}\b""").containsMatchIn(textUnacc)) {
                matchedSigs.add(unaccSig)
            }
        }
        val docSigCount = matchedSigs.size

        val hasRedactedId = combinedRaw.contains("ID_****") || combinedRaw.contains("[ID_NUMBER]")
        val hasPassportDoc = combinedRaw.contains("[PASSPORT_NO]") ||
            (PASSPORT_KW_PATTERN.containsMatchIn(textUnacc) && PASSPORT_NUM_PATTERN.containsMatchIn(combinedRaw))
        val isIdentityDoc = hasIdentityLabel || docSigCount >= 2 || (docSigCount >= 1 && hasRedactedId) || hasPassportDoc
        if (isIdentityDoc) {
            return PrivacyDecision.LOCAL_ONLY
        }

        // 2. OTP / Passwords / Secrets Check
        val hasSecretTag = combinedRaw.contains("[REDACTED_SECRET]") || combinedRaw.contains("[OTP_OR_SECRET]")
        val hasOtpPattern = OTP_PATTERN.containsMatchIn(combinedRaw)
        val hasCvvPin = CVV_PIN_PATTERN.containsMatchIn(combinedRaw)
        if (hasSecretTag || hasOtpPattern || hasCvvPin) {
            return PrivacyDecision.LOCAL_ONLY
        }

        // 3. Payment Card Check (Credit/Debit Card numbers)
        val hasMaskedCard = CREDIT_CARD_MASKED_PATTERN.containsMatchIn(combinedRaw) || combinedRaw.contains("[CREDIT_CARD]")
        val hasRawCard = RAW_CREDIT_CARD_PATTERN.containsMatchIn(combinedRaw)
        val hasExpiryWithCard = EXPIRY_PATTERN.containsMatchIn(combined) && (hasMaskedCard || hasRawCard || combinedRaw.contains("****"))
        if (hasMaskedCard || hasRawCard || hasExpiryWithCard) {
            return PrivacyDecision.LOCAL_ONLY
        }

        return PrivacyDecision.CLOUD_ELIGIBLE
    }
}
