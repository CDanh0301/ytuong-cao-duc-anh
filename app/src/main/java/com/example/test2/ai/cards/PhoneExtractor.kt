package com.example.test2.ai.cards

import java.text.Normalizer
import java.util.Locale

enum class PhoneKind { MOBILE, LANDLINE, HOTLINE }

data class PhoneHit(
    val payloadNumber: String, // digits only; preserves '+84' if original text explicitly had '+84'
    val canonical: String,     // '0xxxxxxxxx' normalized for deduplication
    val kind: PhoneKind,
    val lineIndex: Int,
    val labeled: Boolean
)

object PhoneExtractor {

    private const val MAX_PHONES = 5

    // Phone label keywords indicating intentional phone numbers on a line
    private val PHONE_LABEL_REGEX = Regex("""(?i)\b(?:hotline|sđt|sdt|đt|dt|tel|phone|telephone|zalo|liên\s*hệ|lien\s*he|gọi|goi|call)\b""")

    // Preceding context that excludes candidate as account / order / tracking / tax / doc ID
    private val EXCLUDE_PREFIX_REGEX = Regex(
        """(?i)\b(?:stk|số\s*tk|so\s*tk|tài\s*khoản|tai\s*khoan|account(?:\s*no\.?|\s*number)?|acct|mã\s*đơn|ma\s*don|mã\s*vận\s*đơn|ma\s*van\s*don|tracking|order|otp|mã\s*xác\s*thực|mã\s*số\s*thuế|mst|cccd|cmnd|hộ\s*chiếu)\b"""
    )

    // Suffix currency units
    private val CURRENCY_SUFFIX_REGEX = Regex("""(?i)^\s*(?:đ|₫|vnd|vnđ|k)\b""")

    // Mobile: 10 digits starting with Vietnamese prefixes
    private val MOBILE_CANONICAL_REGEX = Regex("""^0(?:3[2-9]|5[25689]|7[06-9]|8[1-9]|9\d)\d{7}$""")

    // Landline: 11 digits starting with 02
    private val LANDLINE_CANONICAL_REGEX = Regex("""^02\d{9}$""")

    // Hotline: 1800/1900 with 4 or 6 trailing digits (8 or 10 digits total)
    private val HOTLINE_CANONICAL_REGEX = Regex("""^1[89]00(?:\d{4}|\d{6})$""")

    fun extractPhones(rawOcrText: String): List<PhoneHit> {
        if (rawOcrText.isBlank()) return emptyList()

        val normalizedText = Normalizer.normalize(rawOcrText, Normalizer.Form.NFC)
        val lines = normalizedText.lines()

        val results = mutableListOf<PhoneHit>()
        val seenCanonical = mutableSetOf<String>()

        for (lineIndex in lines.indices) {
            val line = lines[lineIndex].trim()
            if (line.isBlank()) continue

            val hasPhoneLabel = PHONE_LABEL_REGEX.containsMatchIn(line)
            val linePhones = findPhonesInLine(line, lineIndex, hasPhoneLabel)

            for (hit in linePhones) {
                if (hit.canonical !in seenCanonical) {
                    seenCanonical.add(hit.canonical)
                    results.add(hit)
                    if (results.size >= MAX_PHONES) return results
                }
            }
        }

        return results
    }

    private fun findPhonesInLine(line: String, lineIndex: Int, hasPhoneLabel: Boolean): List<PhoneHit> {
        val hits = mutableListOf<PhoneHit>()

        // 1. Clean OCR digit confusions (O/o -> 0, l/I -> 1) inside digit tokens
        val cleanedLine = repairOcrDigits(line)

        // 2. Identify potential digit candidate tokens
        // Matches digit groups optionally preceded by '+' or with brackets
        val tokenRegex = Regex("""(?:\+84|\(\+84\)|0|\(0\d{2,3}\)|1[89]00|\d{2,4})[0-9\s.\-()]{4,25}""")
        val matches = tokenRegex.findAll(cleanedLine)

        for (match in matches) {
            val rawCandidate = match.value.trim()
            val matchStart = match.range.first
            val matchEnd = match.range.last + 1

            // Exclude if inside URL or preceded by /, =, @
            if (matchStart > 0) {
                val prevChar = cleanedLine[matchStart - 1]
                if (prevChar == '/' || prevChar == '=' || prevChar == '@' || prevChar == ':') {
                    // Check if it's "Hotline:" or URL
                    val before = cleanedLine.substring(0, matchStart).trimEnd()
                    if (!PHONE_LABEL_REGEX.containsMatchIn(before) && (before.contains("http", ignoreCase = true) || prevChar != ':')) {
                        continue
                    }
                }
            }

            // Exclude if followed by currency (e.g. 1.900.000đ, 200k)
            val after = cleanedLine.substring(matchEnd)
            if (CURRENCY_SUFFIX_REGEX.containsMatchIn(after)) {
                continue
            }

            // Exclude if preceding text on line contains exclusion keywords (STK, order code, OTP, etc.)
            // UNLESS the line explicitly has a phone label
            if (!hasPhoneLabel) {
                val lineBefore = cleanedLine.substring(0, matchStart)
                if (EXCLUDE_PREFIX_REGEX.containsMatchIn(lineBefore)) {
                    continue
                }
            }

            // Evaluate phone segments
            val hit = parsePhoneCandidate(rawCandidate, lineIndex, hasPhoneLabel)
            if (hit != null) {
                // Ensure candidate is not embedded in a continuous sequence of >= 12 digits
                val digitsOnly = rawCandidate.filter { it.isDigit() }
                if (digitsOnly.length >= 12 && !hit.labeled) {
                    continue
                }
                hits.add(hit)
            }
        }

        return hits
    }

    private fun parsePhoneCandidate(rawText: String, lineIndex: Int, labeled: Boolean): PhoneHit? {
        // Strip out brackets and non-essential chars
        val clean = rawText
            .replace("(", " ")
            .replace(")", " ")
            .trim()

        val tokens = clean.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        // Try merging sequences of digit tokens
        var bestHit: PhoneHit? = null

        for (i in tokens.indices) {
            var currentPayload = ""
            var currentRaw = ""

            for (j in i until tokens.size) {
                val token = tokens[j]
                val tokenDigits = token.filter { it.isDigit() || it == '+' }
                if (tokenDigits.isEmpty()) break

                // Stop if total digits exceed 13
                val totalDigitsCount = (currentPayload + tokenDigits).filter { it.isDigit() }.length
                if (totalDigitsCount > 13) break

                currentPayload = if (currentPayload.isEmpty()) tokenDigits else currentPayload + tokenDigits.removePrefix("+")
                currentRaw = if (currentRaw.isEmpty()) token else "$currentRaw $token"

                val hit = evaluatePhoneString(currentPayload, currentRaw, lineIndex, labeled)
                if (hit != null) {
                    // Remember the longest valid match
                    bestHit = hit
                }
            }
            if (bestHit != null) break
        }

        return bestHit
    }

    private fun evaluatePhoneString(payload: String, rawMatchedText: String, lineIndex: Int, labeled: Boolean): PhoneHit? {
        val digitsOnly = payload.filter { it.isDigit() }
        val hasPlus84 = payload.startsWith("+84") || rawMatchedText.contains("+84") || rawMatchedText.contains("(+84)")

        // Build canonical representation starting with '0' or '1'
        val canonical = when {
            hasPlus84 && digitsOnly.startsWith("84") -> "0" + digitsOnly.removePrefix("84")
            digitsOnly.startsWith("0") -> digitsOnly
            digitsOnly.startsWith("1") -> digitsOnly
            else -> return null
        }

        // Avoid year sequences like 20261225 or 19991212
        if (canonical.length == 8 && (canonical.startsWith("20") || canonical.startsWith("19")) && !canonical.startsWith("1900") && !canonical.startsWith("1800")) {
            return null
        }

        // 1. Mobile Check
        if (MOBILE_CANONICAL_REGEX.matches(canonical)) {
            val finalPayload = if (hasPlus84) "+84" + canonical.substring(1) else canonical
            return PhoneHit(
                payloadNumber = finalPayload,
                canonical = canonical,
                kind = PhoneKind.MOBILE,
                lineIndex = lineIndex,
                labeled = labeled
            )
        }

        // 2. Landline Check
        if (LANDLINE_CANONICAL_REGEX.matches(canonical)) {
            val finalPayload = if (hasPlus84) "+84" + canonical.substring(1) else canonical
            return PhoneHit(
                payloadNumber = finalPayload,
                canonical = canonical,
                kind = PhoneKind.LANDLINE,
                lineIndex = lineIndex,
                labeled = labeled
            )
        }

        // 3. Hotline Check (1800/1900 with 4 or 6 trailing digits)
        if (HOTLINE_CANONICAL_REGEX.matches(canonical)) {
            return PhoneHit(
                payloadNumber = canonical,
                canonical = canonical,
                kind = PhoneKind.HOTLINE,
                lineIndex = lineIndex,
                labeled = labeled
            )
        }

        return null
    }

    private fun repairOcrDigits(line: String): String {
        // Fix O/o -> 0 and l/I -> 1 when clearly part of a phone token (surrounded by digits and spaces)
        return line.replace(Regex("""(?<=\d)[Oo](?=\d)"""), "0")
            .replace(Regex("""(?<=\d)[lI|](?=\d)"""), "1")
    }
}
