package com.example.test2.storage

import android.content.Context
import android.net.Uri
import com.example.test2.shared.repository.PersistenceDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * Android encrypted file persistence driver using AES-256-GCM (IDEA.md Part 1).
 */
class AndroidFilePersistenceDriver(private val context: Context) : PersistenceDriver {
    private val encryptedDataFile: File
        get() = File(context.filesDir, "photo_manager_metadata.json.enc")
    private val legacyDataFile: File
        get() = File(context.filesDir, "photo_manager_metadata.json")

    override suspend fun loadData(): String? = withContext(Dispatchers.IO) {
        // 1. Try reading encrypted file
        if (encryptedDataFile.exists()) {
            try {
                val cipherBytes = encryptedDataFile.readBytes()
                val plainBytes = LocalCryptoManager.decrypt(cipherBytes)
                return@withContext String(plainBytes, Charsets.UTF_8)
            } catch (_: Exception) {
            }
        }
        // 2. Fallback to legacy unencrypted file
        if (legacyDataFile.exists()) {
            try {
                return@withContext legacyDataFile.readText()
            } catch (_: Exception) {
            }
        }
        null
    }

    override suspend fun saveData(json: String) {
        withContext(Dispatchers.IO) {
            try {
                val plainBytes = json.toByteArray(Charsets.UTF_8)
                val encryptedBytes = LocalCryptoManager.encrypt(plainBytes)
                val tempFile = File(context.filesDir, "photo_manager_metadata.json.enc.tmp")
                tempFile.writeBytes(encryptedBytes)
                tempFile.renameTo(encryptedDataFile)
                // Securely remove unencrypted legacy file if it exists
                if (legacyDataFile.exists()) {
                    legacyDataFile.delete()
                }
            } catch (_: Exception) {
            }
        }
    }
}

/**
 * Local Encrypted Photo Storage (IDEA.md Part 1 & Part 3).
 * Encrypts all imported images with AES-256-GCM directly when written to app storage.
 */
class AndroidPhotoStorage(private val context: Context) {
    private val photosDir: File
        get() {
            val dir = File(context.filesDir, "photos")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    suspend fun saveImageFromUri(uri: Uri): StoredImageResult? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val extension = contentResolver.getType(uri)?.substringAfterLast("/") ?: "jpg"
            val uniqueName = "secure_img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$extension.enc"
            val targetFile = File(photosDir, uniqueName)

            contentResolver.openInputStream(uri)?.use { input ->
                LocalCryptoManager.encryptStreamToFile(input, targetFile)
            } ?: return@withContext null

            StoredImageResult(
                fileName = uniqueName,
                filePath = targetFile.absolutePath,
                fileSizeBytes = targetFile.length()
            )
        } catch (_: Exception) {
            null
        }
    }

    fun openDecryptedStream(filePath: String): InputStream {
        return LocalCryptoManager.openDecryptedInputStream(File(filePath))
    }

    suspend fun deletePhysicalFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            } else {
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}

data class StoredImageResult(
    val fileName: String,
    val filePath: String,
    val fileSizeBytes: Long
)
