package com.example.test2.ai

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

object MlKitOcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeText(context: Context, filePath: String): String = withContext(Dispatchers.IO) {
        try {
            // Check if file is encrypted with on-device AES-256-GCM (IDEA.md Part 1)
            val file = File(filePath)
            if (file.exists() && com.example.test2.storage.LocalCryptoManager.isEncrypted(file)) {
                try {
                    val bitmap = com.example.test2.storage.LocalCryptoManager.decodeDecryptedBitmapSafely(file, maxDimension = 1920)
                    if (bitmap != null) {
                        val image = InputImage.fromBitmap(bitmap, 0)
                        val text = processImage(image)
                        bitmap.recycle()
                        if (text.isNotBlank()) {
                            return@withContext text
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

            // 1. Try directly via InputImage.fromFilePath
            val textFromUri = try {
                val image = InputImage.fromFilePath(context, uri)
                processImage(image)
            } catch (_: Exception) {
                ""
            }

            if (textFromUri.isNotBlank()) {
                return@withContext textFromUri
            }

            // 2. Fallback: Open stream directly and decode Bitmap
            try {
                val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)
                }
                if (bitmap != null) {
                    val image = InputImage.fromBitmap(bitmap, 0)
                    val textFromBitmap = processImage(image)
                    bitmap.recycle()
                    if (textFromBitmap.isNotBlank()) {
                        return@withContext textFromBitmap
                    }
                }
            } catch (_: Exception) {
            }

            textFromUri
        } catch (_: Exception) {
            ""
        }
    }

    private suspend fun processImage(image: InputImage): String {
        return suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    if (continuation.isActive) {
                        continuation.resume(visionText.text ?: "")
                    }
                }
                .addOnFailureListener {
                    if (continuation.isActive) {
                        continuation.resume("")
                    }
                }
        }
    }
}
