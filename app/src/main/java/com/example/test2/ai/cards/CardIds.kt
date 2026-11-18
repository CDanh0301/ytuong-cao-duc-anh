package com.example.test2.ai.cards

import com.example.test2.shared.models.ActionCardType
import com.example.test2.shared.utils.VietnameseUtils
import java.security.MessageDigest
import java.util.Locale

object CardIds {
    const val EXTRACTOR_VERSION: Int = 2

    /**
     * Generates a deterministic, reproducible card ID based on photoId, card type, and normalized key value.
     * Prevents card duplication when re-extracting the same photo.
     */
    fun generateId(photoId: String, type: ActionCardType, rawKey: String): String {
        val normalized = normalizeKey(type, rawKey)
        val input = "$photoId|${type.name}|$normalized"
        return sha1Hex(input).take(32)
    }

    private fun normalizeKey(type: ActionCardType, rawKey: String): String {
        return when (type) {
            ActionCardType.ADDRESS -> VietnameseUtils.removeDiacritics(rawKey.trim().lowercase(Locale.ROOT))
            ActionCardType.CONTACT -> rawKey.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9@]"), "")
            ActionCardType.PAYMENT -> rawKey.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
            ActionCardType.EVENT -> VietnameseUtils.removeDiacritics(rawKey.trim().lowercase(Locale.ROOT))
            ActionCardType.ALL -> rawKey.trim().lowercase(Locale.ROOT)
        }
    }

    private fun sha1Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
