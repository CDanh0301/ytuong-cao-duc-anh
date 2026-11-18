package com.example.test2.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.test2.ai.cards.ActionCardExtractorV2
import com.example.test2.ai.cards.LegacyActionCardExtractor
import com.example.test2.ai.cards.QualityFlags
import com.example.test2.shared.models.ActionCard
import com.example.test2.storage.LocalCryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Facade entry point for Smart Action Card extraction.
 * Routes to ActionCardExtractorV2 or LegacyActionCardExtractor based on QualityFlags.
 */
object LocalActionCardExtractor {

    suspend fun extractCards(
        context: Context,
        filePath: String,
        photoId: String,
        rawOcrText: String,
        photoTitle: String = "",
        lines: List<OcrLine>? = null,
        decodedBitmap: Bitmap? = null
    ): List<ActionCard> = withContext(Dispatchers.IO) {
        if (QualityFlags.NEW_CONTACT_EXTRACTOR && QualityFlags.NEW_ADDRESS_EXTRACTOR) {
            val bitmap = decodedBitmap ?: loadDecryptedBitmap(context, filePath)
            val shouldRecycle = decodedBitmap == null && bitmap != null
            try {
                ActionCardExtractorV2.extractCardsFromBitmapAndText(
                    bitmap = bitmap,
                    photoId = photoId,
                    rawOcrText = rawOcrText,
                    photoTitle = photoTitle,
                    ocrLines = lines
                )
            } finally {
                if (shouldRecycle) {
                    bitmap?.recycle()
                }
            }
        } else {
            LegacyActionCardExtractor.extractCards(
                context = context,
                filePath = filePath,
                photoId = photoId,
                rawOcrText = rawOcrText,
                photoTitle = photoTitle
            )
        }
    }

    fun extractCardsFromText(
        photoId: String,
        rawOcrText: String,
        photoTitle: String = "",
        lines: List<OcrLine>? = null
    ): List<ActionCard> {
        return if (QualityFlags.NEW_CONTACT_EXTRACTOR && QualityFlags.NEW_ADDRESS_EXTRACTOR) {
            ActionCardExtractorV2.extractCardsFromText(
                photoId = photoId,
                rawOcrText = rawOcrText,
                photoTitle = photoTitle,
                ocrLines = lines
            )
        } else {
            LegacyActionCardExtractor.extractCardsFromText(
                photoId = photoId,
                rawOcrText = rawOcrText,
                photoTitle = photoTitle
            )
        }
    }

    private fun loadDecryptedBitmap(context: Context, filePath: String): Bitmap? {
        return try {
            val file = File(filePath)
            if (file.exists() && LocalCryptoManager.isEncrypted(file)) {
                LocalCryptoManager.openDecryptedInputStream(file).use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } else if (filePath.startsWith("content://")) {
                val uri = Uri.parse(filePath)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } else if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
