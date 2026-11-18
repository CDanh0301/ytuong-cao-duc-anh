package com.example.test2.ai

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object MlKitOcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Structured OCR recognition accepting a decoded bitmap and orientation.
     * Preserves bounding geometry and line blocks.
     */
    suspend fun recognizeStructured(bitmap: Bitmap, rotationDegrees: Int = 0): StructuredOcr = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromBitmap(bitmap, rotationDegrees)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val lines = mutableListOf<OcrLine>()
                        val bmpW = bitmap.width.toFloat().coerceAtLeast(1f)
                        val bmpH = bitmap.height.toFloat().coerceAtLeast(1f)

                        for ((blockIndex, block) in visionText.textBlocks.withIndex()) {
                            for (line in block.lines) {
                                val box = line.boundingBox
                                val left = if (box != null) box.left / bmpW else 0f
                                val top = if (box != null) box.top / bmpH else 0f
                                val right = if (box != null) box.right / bmpW else 0f
                                val bottom = if (box != null) box.bottom / bmpH else 0f
                                lines.add(
                                    OcrLine(
                                        text = line.text,
                                        left = left,
                                        top = top,
                                        right = right,
                                        bottom = bottom,
                                        blockId = blockIndex
                                    )
                                )
                            }
                        }
                        if (continuation.isActive) {
                            continuation.resume(StructuredOcr(lines = lines, flatText = visionText.text ?: ""))
                        }
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) {
                            continuation.resume(StructuredOcr())
                        }
                    }
            } catch (_: Exception) {
                if (continuation.isActive) {
                    continuation.resume(StructuredOcr())
                }
            }
        }
    }

    /**
     * Backward-compatible text recognition delegating to ImageDecoder and recognizeStructured.
     */
    suspend fun recognizeText(context: Context, filePath: String): String = withContext(Dispatchers.IO) {
        val decoded = ImageDecoder.decode(context, filePath) ?: return@withContext ""
        try {
            val structured = recognizeStructured(decoded.bitmap, decoded.rotationDegrees)
            structured.flatText
        } finally {
            decoded.bitmap.recycle()
        }
    }
}
