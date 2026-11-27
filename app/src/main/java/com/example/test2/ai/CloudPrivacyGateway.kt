package com.example.test2.ai

/**
 * Centralized remote privacy enforcement gateway (Single Enforcement Point).
 * Guarantees every outbound Cloud AI payload is scrubbed of PII digits/secrets
 * and passes [PrivacyPolicy] fail-closed checks before any network I/O occurs.
 */
object CloudPrivacyGateway {

    private val PLACEHOLDER_ONLY_REGEX = Regex(
        """^(?:\[(?:BANK_ACCOUNT|CREDIT_CARD|PHONE_NUMBER|ID_NUMBER|PASSPORT_NO|EMAIL_REDACTED|REDACTED_SECRET|OTP_OR_SECRET|PERSON_NAME)]|[\s.,:;!?/\-_*()\[\]])+$"""
    )

    data class SanitizedAnalysisInput(
        val decision: PrivacyDecision,
        val scrubbedText: String,
        val scrubbedTitle: String
    )

    data class SanitizedEmbeddingInput(
        val decision: PrivacyDecision,
        val scrubbedText: String,
        val isMeaningfulForCloud: Boolean
    )

    fun prepareForAnalysis(
        rawOrScrubbedText: String,
        photoTitle: String = "",
        visualLabels: List<String> = emptyList()
    ): SanitizedAnalysisInput {
        val cloudText = PiiScrubber.scrubForCloud(rawOrScrubbedText).scrubbedText
        val cloudTitle = PiiScrubber.scrubForCloud(photoTitle).scrubbedText
        val decision = PrivacyPolicy.classify(
            scrubbedText = cloudText,
            photoTitle = cloudTitle,
            visualLabels = visualLabels
        )
        return SanitizedAnalysisInput(
            decision = decision,
            scrubbedText = cloudText,
            scrubbedTitle = cloudTitle
        )
    }

    fun prepareTextForEmbedding(
        text: String,
        photoTitle: String = "",
        visualLabels: List<String> = emptyList()
    ): SanitizedEmbeddingInput {
        val cloudText = PiiScrubber.scrubForCloud(text).scrubbedText.trim()
        val cloudTitle = PiiScrubber.scrubForCloud(photoTitle).scrubbedText.trim()
        val decision = PrivacyPolicy.classify(
            scrubbedText = cloudText,
            photoTitle = cloudTitle,
            visualLabels = visualLabels
        )
        val isMeaningful = cloudText.isNotBlank() && !PLACEHOLDER_ONLY_REGEX.matches(cloudText)
        return SanitizedEmbeddingInput(
            decision = decision,
            scrubbedText = cloudText,
            isMeaningfulForCloud = decision == PrivacyDecision.CLOUD_ELIGIBLE && isMeaningful
        )
    }

    fun sanitizeCategoryName(categoryName: String): String {
        return PiiScrubber.scrubForCloud(categoryName).scrubbedText.trim()
    }

    fun isSafeHttpsUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (!trimmed.startsWith("https://", ignoreCase = true)) return false
        return trimmed.length > "https://".length && !trimmed.contains(" ")
    }
}
