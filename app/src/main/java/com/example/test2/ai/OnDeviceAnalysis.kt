package com.example.test2.ai

import android.content.Context
import com.example.test2.ai.cards.ActionCardExtractorV2
import com.example.test2.shared.models.ActionCard
import com.example.test2.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AnalysisResult(
    val ocr: StructuredOcr,
    val cards: List<ActionCard>,
    val scrubbedText: String,
    val visual: VisualAnalysisResult
)

object OnDeviceAnalysis {

    /**
     * Executes single-pass memory-safe on-device analysis:
     * 1. Decodes bitmap once with maxPixels cap & EXIF rotation.
     * 2. ML Kit OCR (structured with relative geometry).
     * 3. QR scanning & Action Card extraction using the same bitmap.
     * 4. ML Kit Vision Labeling using the same bitmap.
     * 5. Recycles bitmap immediately.
     * 6. PII scrubbing on OCR text.
     */
    suspend fun analyze(
        context: Context,
        photoId: String,
        filePath: String,
        photoTitle: String = ""
    ): AnalysisResult? = withContext(Dispatchers.IO) {
        val decoded = ImageDecoder.decode(context, filePath)
        if (decoded == null) {
            AppLogger.e("OnDeviceAnalysis", "decode returned null for photoId=$photoId, filePath=$filePath")
            return@withContext null
        }

        try {
            // Step 1: Structured OCR
            val ocr = MlKitOcrEngine.recognizeStructured(decoded.bitmap, decoded.rotationDegrees)
            AppLogger.d("OnDeviceAnalysis", "OCR for $photoId: textLen=${ocr.flatText.length}, lines=${ocr.lines.size}")

            // Step 2: Extract Smart Action Cards (QR + Text)
            val cards = ActionCardExtractorV2.extractCardsFromBitmapAndText(
                bitmap = decoded.bitmap,
                photoId = photoId,
                rawOcrText = ocr.flatText,
                photoTitle = photoTitle,
                ocrLines = ocr.lines
            )
            AppLogger.d("OnDeviceAnalysis", "Cards for $photoId: count=${cards.size}")

            // Step 3: Vision Labeling
            val visual = LocalImageLabeler.analyze(decoded.bitmap, decoded.rotationDegrees)
            AppLogger.d("OnDeviceAnalysis", "Visual for $photoId: labels=${visual.rawLabels}")

            // Step 4: PII scrubbing
            val scrubbed = PiiScrubber.scrub(ocr.flatText)

            AnalysisResult(
                ocr = ocr,
                cards = cards,
                scrubbedText = scrubbed.scrubbedText,
                visual = visual
            )
        } finally {
            decoded.bitmap.recycle()
        }
    }
}
