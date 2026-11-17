package com.example.test2.ai

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Result of on-device visual image labeling.
 */
data class VisualAnalysisResult(
    val rawLabels: List<String> = emptyList(),
    val isPerson: Boolean = false,
    val isNature: Boolean = false,
    val isAnimal: Boolean = false,
    val hasTextOrDoc: Boolean = false,
    val vietnameseSemanticTags: List<String> = emptyList()
)

/**
 * Unified On-Device Vision Engine powered by Google ML Kit Image Labeling.
 * Provides 100% on-device visual recognition for nature, animals, scenery, and people.
 * Zero bytes uploaded, zero heavy RAM background footprint.
 */
object LocalImageLabeler {

    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    }

    private val PERSON_LABELS = setOf(
        "person", "face", "selfie", "portrait", "smile", "human",
        "girl", "boy", "man", "woman", "child", "baby", "hair", "lip", "chin"
    )

    private val NATURE_LABELS = setOf(
        "nature", "tree", "plant", "forest", "mountain", "landscape", "sea",
        "ocean", "water", "beach", "sky", "cloud", "flower", "wilderness",
        "hill", "lake", "river", "valley", "flora", "scenery", "wood",
        "natural landscape", "grass", "field", "sunrise", "sunset", "jungle"
    )

    private val ANIMAL_LABELS = setOf(
        "animal", "pet", "dog", "cat", "bird", "wildlife", "mammal",
        "fish", "insect", "fauna", "canidae", "felidae", "horse", "puppy",
        "kitten", "carnivore", "vertebrate", "reptile"
    )

    private val TEXT_DOC_LABELS = setOf(
        "text", "font", "document", "receipt", "paper", "whiteboard",
        "screenshot", "web page", "scoreboard", "newspaper", "book", "handwriting",
        "ticket", "invoicing", "bill", "menu", "poster"
    )

    /**
     * Pure classification function without Android dependencies for unit testing and fast evaluation.
     */
    fun classifyLabels(labels: List<String>): VisualAnalysisResult {
        if (labels.isEmpty()) return VisualAnalysisResult()

        val normalized = labels.map { it.trim().lowercase(Locale.ROOT) }
        val isPerson = normalized.any { it in PERSON_LABELS }
        val isNature = normalized.any { it in NATURE_LABELS }
        val isAnimal = normalized.any { it in ANIMAL_LABELS }
        val hasTextOrDoc = normalized.any { it in TEXT_DOC_LABELS }

        val tags = mutableListOf<String>()

        if (isNature) {
            tags.add("thiên nhiên")
            tags.add("phong cảnh")
            if (normalized.any { it.contains("forest") || it.contains("wood") || it.contains("jungle") }) tags.add("rừng")
            if (normalized.any { it.contains("tree") || it.contains("plant") || it.contains("flora") }) tags.add("cây cối")
            if (normalized.any { it.contains("mountain") || it.contains("hill") || it.contains("valley") }) tags.add("núi")
            if (normalized.any { it.contains("sea") || it.contains("ocean") || it.contains("beach") }) tags.add("biển")
            if (normalized.any { it.contains("water") || it.contains("lake") || it.contains("river") }) tags.add("sông hồ")
            if (normalized.any { it.contains("sky") || it.contains("cloud") }) tags.add("bầu trời")
            if (normalized.any { it.contains("flower") }) tags.add("hoa cỏ")
            if (normalized.any { it.contains("sunset") || it.contains("sunrise") }) tags.add("hoàng hôn")
        }

        if (isAnimal) {
            tags.add("động vật")
            tags.add("thú cưng")
            if (normalized.any { it.contains("dog") || it.contains("puppy") || it.contains("canidae") }) tags.add("chó")
            if (normalized.any { it.contains("cat") || it.contains("kitten") || it.contains("felidae") }) tags.add("mèo")
            if (normalized.any { it.contains("bird") }) tags.add("chim")
            if (normalized.any { it.contains("wildlife") }) tags.add("thiên nhiên hoang dã")
        }

        if (isPerson) {
            tags.add("cá nhân")
            tags.add("chân dung")
            if (normalized.any { it.contains("selfie") }) tags.add("tự sướng")
        }

        return VisualAnalysisResult(
            rawLabels = labels,
            isPerson = isPerson,
            isNature = isNature,
            isAnimal = isAnimal,
            hasTextOrDoc = hasTextOrDoc,
            vietnameseSemanticTags = tags.distinct()
        )
    }

    /**
     * Runs ML Kit Image Labeling on the photo file asynchronously.
     * Gracefully returns an empty result on any error.
     */
    suspend fun analyze(context: Context, filePath: String): VisualAnalysisResult = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)

            // 1. Handle on-device encrypted files
            if (file.exists() && com.example.test2.storage.LocalCryptoManager.isEncrypted(file)) {
                try {
                    val bitmap = com.example.test2.storage.LocalCryptoManager.decodeDecryptedBitmapSafely(file, maxDimension = 1920)
                    if (bitmap != null) {
                        val image = InputImage.fromBitmap(bitmap, 0)
                        val labels = processImage(image)
                        bitmap.recycle()
                        if (labels.isNotEmpty()) {
                            return@withContext classifyLabels(labels)
                        }
                    }
                } catch (_: Exception) {
                }
            }

            val uri = if (filePath.startsWith("content://")) {
                Uri.parse(filePath)
            } else {
                Uri.fromFile(file)
            }

            // 2. Try directly via InputImage.fromFilePath
            val labelsFromUri = try {
                val image = InputImage.fromFilePath(context, uri)
                processImage(image)
            } catch (_: Exception) {
                emptyList()
            }

            if (labelsFromUri.isNotEmpty()) {
                return@withContext classifyLabels(labelsFromUri)
            }

            // 3. Fallback: Open stream directly and decode Bitmap
            try {
                val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)
                }
                if (bitmap != null) {
                    val image = InputImage.fromBitmap(bitmap, 0)
                    val labelsFromBitmap = processImage(image)
                    bitmap.recycle()
                    if (labelsFromBitmap.isNotEmpty()) {
                        return@withContext classifyLabels(labelsFromBitmap)
                    }
                }
            } catch (_: Exception) {
            }

            classifyLabels(labelsFromUri)
        } catch (_: Exception) {
            VisualAnalysisResult()
        }
    }

    private suspend fun processImage(image: InputImage): List<String> {
        return suspendCancellableCoroutine { continuation ->
            labeler.process(image)
                .addOnSuccessListener { labels ->
                    if (continuation.isActive) {
                        val validLabels = labels
                            .filter { it.confidence >= 0.45f }
                            .map { it.text }
                        continuation.resume(validLabels)
                    }
                }
                .addOnFailureListener {
                    if (continuation.isActive) {
                        continuation.resume(emptyList())
                    }
                }
        }
    }
}
