package com.example.test2.ai

/**
 * Line of OCR text with normalized relative bounding coordinates ([0.0..1.0]).
 */
data class OcrLine(
    val text: String,
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
    val blockId: Int = 0
)

/**
 * Structured OCR result containing both individual geometric lines and the flat ML Kit text.
 */
data class StructuredOcr(
    val lines: List<OcrLine> = emptyList(),
    val flatText: String = ""
)
