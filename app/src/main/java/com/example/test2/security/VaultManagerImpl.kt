package com.example.test2.security

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.test2.data.local.prefs.VaultPrefsDataSource
import com.example.test2.storage.LocalCryptoManager
import com.example.test2.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Process-wide re-entrant eviction guard shared by [VaultManagerImpl] and [PrivacyVaultManager]
 * so MediaStore ContentObservers suppress self-triggered delete notifications cleanly.
 */
internal object VaultEvictionGuard {
    val isInternalEvicting = AtomicBoolean(false)
    private val evictionDepth = AtomicInteger(0)
    val exemptLock = Any()

    fun beginInternalEviction() {
        if (evictionDepth.incrementAndGet() > 0) {
            isInternalEvicting.set(true)
        }
    }

    fun endInternalEviction() {
        val remaining = evictionDepth.updateAndGet { current -> if (current > 0) current - 1 else 0 }
        if (remaining == 0) {
            isInternalEvicting.set(false)
        }
    }
}

/**
 * Default Android implementation of [IVaultManager] encapsulating AES-256-GCM Privacy Vault
 * encryption, eviction, restoration, and exemption rules via [VaultPrefsDataSource].
 */
class VaultManagerImpl(context: Context) : IVaultManager {

    private val appContext = context.applicationContext
    private val prefsSource = VaultPrefsDataSource(appContext)

    override fun isVaultEnabled(): Boolean = prefsSource.isVaultEnabled()

    override fun setVaultEnabled(enabled: Boolean) {
        prefsSource.setVaultEnabled(enabled)
    }

    override fun getVaultScope(): String = prefsSource.getVaultScope()

    override fun setVaultScope(scope: String) {
        prefsSource.setVaultScope(scope)
    }

    override fun getPendingVaultScope(): String? = prefsSource.getPendingVaultScope()

    override fun setPendingVaultScope(scope: String?) {
        prefsSource.setPendingVaultScope(scope)
    }

    override fun getVaultEnabledSince(): Long = prefsSource.getVaultEnabledSince()

    override fun setVaultEnabledSince(timestamp: Long) {
        prefsSource.setVaultEnabledSince(timestamp)
    }

    override fun shouldVaultScreenshot(dateAddedMillis: Long): Boolean {
        if (!isVaultEnabled()) return false
        if (getVaultScope() == VaultPrefsDataSource.SCOPE_NEW_ONLY) {
            return dateAddedMillis >= getVaultEnabledSince()
        }
        return true
    }

    private fun normalizeExemptKey(idOrMediaId: String): String {
        val trimmed = idOrMediaId.trim()
        return if (trimmed.startsWith("device_")) trimmed else "device_$trimmed"
    }

    override fun markRestoredExempt(photoIdOrMediaId: String) {
        if (photoIdOrMediaId.isBlank()) return
        val key = normalizeExemptKey(photoIdOrMediaId)
        synchronized(VaultEvictionGuard.exemptLock) {
            val current = prefsSource.getRestoredExemptIds().toMutableSet()
            if (current.add(key)) {
                prefsSource.saveRestoredExemptIds(current)
            }
        }
    }

    override fun clearRestoredExempt(photoIdOrMediaId: String) {
        if (photoIdOrMediaId.isBlank()) return
        val key = normalizeExemptKey(photoIdOrMediaId)
        synchronized(VaultEvictionGuard.exemptLock) {
            val current = prefsSource.getRestoredExemptIds().toMutableSet()
            if (current.remove(key)) {
                prefsSource.saveRestoredExemptIds(current)
            }
        }
    }

    override fun isRestoredExempt(photoIdOrMediaId: String): Boolean {
        if (photoIdOrMediaId.isBlank()) return false
        val key = normalizeExemptKey(photoIdOrMediaId)
        synchronized(VaultEvictionGuard.exemptLock) {
            val current = prefsSource.getRestoredExemptIds()
            return current.contains(key) || current.contains(photoIdOrMediaId)
        }
    }

    override fun clearAllRestoredExempts() {
        synchronized(VaultEvictionGuard.exemptLock) {
            prefsSource.clearRestoredExemptIds()
        }
    }

    override fun shouldAutoVaultPhoto(photoId: String, dateAddedMillis: Long): Boolean {
        if (!shouldVaultScreenshot(dateAddedMillis)) return false
        if (isRestoredExempt(photoId)) return false
        return true
    }

    override fun hasPromptedConsent(): Boolean = prefsSource.hasPromptedConsent()

    override fun setPromptedConsent(prompted: Boolean) {
        prefsSource.setPromptedConsent(prompted)
    }

    override fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    override fun sanitizeRestoreFileName(
        rawTitle: String,
        encryptedFilePath: String,
        fallbackFileName: String
    ): String = sanitizeRestoreFileNameCore(rawTitle, encryptedFilePath, fallbackFileName)

    override fun beginInternalEviction() {
        VaultEvictionGuard.beginInternalEviction()
    }

    override fun endInternalEviction() {
        VaultEvictionGuard.endInternalEviction()
    }

    override fun isInternalEvicting(): Boolean = VaultEvictionGuard.isInternalEvicting.get()

    override fun hasRemainingEncryptedFiles(): Boolean {
        return File(appContext.filesDir, "photos")
            .listFiles()
            ?.any { it.isFile && it.name.endsWith(".enc") && it.length() > 0L } == true
    }

    override suspend fun vaultAndEvictScreenshot(
        sourceUriString: String?,
        sourcePath: String,
        originalFileName: String
    ): VaultResult? = withContext(Dispatchers.IO) {
        val sourceUri = sourceUriString?.let { Uri.parse(it) }
        try {
            val usableSpace = appContext.filesDir.usableSpace
            val minimumSafeMargin = 300 * 1024 * 1024L
            if (usableSpace < minimumSafeMargin) {
                return@withContext null
            }

            val photosDir = File(appContext.filesDir, "photos")
            if (!photosDir.exists()) {
                photosDir.mkdirs()
            }

            val extension = originalFileName.substringAfterLast(".", "jpg")
            val uniqueName = "secure_vault_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$extension.enc"
            val targetFile = File(photosDir, uniqueName)

            val streamOpened = try {
                if (sourceUri != null) {
                    appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                        LocalCryptoManager.encryptStreamToFile(input, targetFile)
                        true
                    } ?: false
                } else false
            } catch (e: Exception) {
                AppLogger.w(TAG, "URI stream encryption failed for $sourceUri, trying direct path", e)
                false
            }

            val encryptedSuccessfully = if (streamOpened) {
                true
            } else {
                val directFile = File(sourcePath)
                if (directFile.exists() && directFile.canRead()) {
                    FileInputStream(directFile).use { input ->
                        LocalCryptoManager.encryptStreamToFile(input, targetFile)
                    }
                    true
                } else {
                    false
                }
            }

            if (!encryptedSuccessfully || !targetFile.exists() || targetFile.length() == 0L) {
                if (targetFile.exists()) {
                    try {
                        targetFile.delete()
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "Failed to delete incomplete vault file ${targetFile.name}", e)
                    }
                }
                return@withContext null
            }

            var originalEvicted = false
            if (hasAllFilesAccess()) {
                beginInternalEviction()
                try {
                    val resolvedPath = resolvePhysicalPath(sourceUri, sourcePath)
                    var deletedRows = 0
                    if (sourceUri != null) {
                        try {
                            deletedRows = appContext.contentResolver.delete(sourceUri, null, null)
                            if (deletedRows > 0) {
                                originalEvicted = true
                            }
                        } catch (e: Exception) {
                            AppLogger.w(TAG, "contentResolver.delete failed for $sourceUri", e)
                        }
                    }

                    if (!resolvedPath.isNullOrBlank()) {
                        val originalFile = File(resolvedPath)
                        if (originalFile.exists() && originalFile.delete()) {
                            originalEvicted = true
                        }
                        if (deletedRows <= 0) {
                            try {
                                MediaScannerConnection.scanFile(
                                    appContext,
                                    arrayOf(resolvedPath),
                                    null
                                ) { _, _ -> }
                            } catch (e: Exception) {
                                AppLogger.w(TAG, "MediaScannerConnection.scanFile failed for $resolvedPath", e)
                            }
                        }
                    }

                    try {
                        appContext.contentResolver.notifyChange(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null)
                        if (sourceUri != null) {
                            appContext.contentResolver.notifyChange(sourceUri, null)
                        }
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "notifyChange failed during eviction", e)
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Error during original eviction for $originalFileName", e)
                } finally {
                    endInternalEviction()
                }
            }

            VaultResult(
                newFilePath = targetFile.absolutePath,
                newFileName = uniqueName,
                fileSizeBytes = targetFile.length(),
                isOriginalEvicted = originalEvicted
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "vaultAndEvictScreenshot failed for $originalFileName", e)
            null
        }
    }

    private fun resolvePhysicalPath(sourceUri: Uri?, sourcePath: String): String? {
        if (sourcePath.isNotBlank() && !sourcePath.startsWith("content://")) {
            return sourcePath
        }
        if (sourceUri == null) return null
        return try {
            appContext.contentResolver.query(
                sourceUri,
                arrayOf(MediaStore.Images.Media.DATA),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val col = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                    if (col >= 0) cursor.getString(col) else null
                } else null
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "resolvePhysicalPath failed for $sourceUri", e)
            null
        }
    }

    override suspend fun restoreAndEvictScreenshot(
        vaultedFilePath: String,
        originalFileName: String,
        originalDateAddedMillis: Long?,
        markExemptOnRestore: Boolean
    ): RestoreResult? = withContext(Dispatchers.IO) {
        try {
            val vaultedFile = File(vaultedFilePath)
            val vaultedFileSize = vaultedFile.length()
            if (!vaultedFile.exists() || !vaultedFile.canRead() || vaultedFileSize == 0L) {
                AppLogger.w(TAG, "restoreAndEvictScreenshot missing/empty vaulted file: $vaultedFilePath")
                return@withContext null
            }

            val finalCleanName = sanitizeRestoreFileName(
                rawTitle = originalFileName,
                encryptedFilePath = vaultedFilePath,
                fallbackFileName = vaultedFile.name
            )

            val mimeType = when {
                finalCleanName.endsWith(".png", ignoreCase = true) -> "image/png"
                finalCleanName.endsWith(".webp", ignoreCase = true) -> "image/webp"
                else -> "image/jpeg"
            }

            val nowMillis = System.currentTimeMillis()
            val resolvedOriginalMillis = if (originalDateAddedMillis != null && originalDateAddedMillis > 0L) {
                if (originalDateAddedMillis > 10_000_000_000L) originalDateAddedMillis else originalDateAddedMillis * 1000L
            } else {
                nowMillis
            }
            val timestampSec = resolvedOriginalMillis / 1000L

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, finalCleanName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.DATE_TAKEN, resolvedOriginalMillis)
                put(MediaStore.Images.Media.DATE_ADDED, timestampSec)
                put(MediaStore.Images.Media.DATE_MODIFIED, timestampSec)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Screenshots")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val contentResolver = appContext.contentResolver
            val insertedUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (insertedUri == null) {
                AppLogger.w(TAG, "restoreAndEvictScreenshot MediaStore.insert returned null for $finalCleanName")
                return@withContext null
            }

            val preParsedId = try {
                ContentUris.parseId(insertedUri)
            } catch (_: Exception) {
                -1L
            }
            if (markExemptOnRestore && preParsedId > 0L) {
                markRestoredExempt("device_$preParsedId")
            }

            var writeSuccess = false
            try {
                contentResolver.openOutputStream(insertedUri)?.use { outputStream ->
                    LocalCryptoManager.openDecryptedInputStream(vaultedFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                        outputStream.flush()
                    }
                    writeSuccess = true
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "restoreAndEvictScreenshot write failed for $finalCleanName ($insertedUri)", e)
                writeSuccess = false
            }

            if (!writeSuccess) {
                AppLogger.w(TAG, "restoreAndEvictScreenshot !writeSuccess for $finalCleanName, deleting pending $insertedUri")
                if (markExemptOnRestore && preParsedId > 0L) {
                    clearRestoredExempt("device_$preParsedId")
                }
                try {
                    contentResolver.delete(insertedUri, null, null)
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed deleting pending MediaStore row $insertedUri", e)
                }
                return@withContext null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(insertedUri, contentValues, null, null)
            }

            var newMediaId = -1L
            var resolvedDataPath = ""
            var resolvedSize = 0L

            try {
                contentResolver.query(
                    insertedUri,
                    arrayOf(
                        MediaStore.Images.Media._ID,
                        MediaStore.Images.Media.SIZE,
                        MediaStore.Images.Media.DATA
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idCol = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                        if (idCol >= 0) newMediaId = cursor.getLong(idCol)
                        val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                        if (sizeCol >= 0) resolvedSize = cursor.getLong(sizeCol)
                        val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                        if (dataCol >= 0) resolvedDataPath = cursor.getString(dataCol) ?: ""
                    }
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Query after restore failed for $insertedUri", e)
            }

            if (newMediaId <= 0L) {
                newMediaId = if (preParsedId > 0L) preParsedId else System.currentTimeMillis()
            }
            if (markExemptOnRestore && newMediaId > 0L && newMediaId != preParsedId) {
                markRestoredExempt("device_$newMediaId")
            }

            if (resolvedSize <= 0L && resolvedDataPath.isNotBlank()) {
                val f = File(resolvedDataPath)
                if (f.exists()) resolvedSize = f.length()
            }

            if (resolvedDataPath.isNotBlank() && hasAllFilesAccess()) {
                try {
                    File(resolvedDataPath).setLastModified(resolvedOriginalMillis)
                } catch (e: Exception) {
                    AppLogger.w(TAG, "setLastModified failed for $resolvedDataPath", e)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val syncTimeValues = ContentValues().apply {
                        put(MediaStore.Images.Media.DATE_TAKEN, resolvedOriginalMillis)
                        put(MediaStore.Images.Media.DATE_MODIFIED, timestampSec)
                    }
                    contentResolver.update(insertedUri, syncTimeValues, null, null)
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Timestamp sync update failed for $insertedUri", e)
                }
            }

            try {
                vaultedFile.delete()
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed deleting vaulted file $vaultedFilePath", e)
            }

            RestoreResult(
                newUri = insertedUri.toString(),
                newFilePath = insertedUri.toString(),
                newFileName = finalCleanName,
                fileSizeBytes = if (resolvedSize > 0L) resolvedSize else vaultedFileSize,
                newMediaId = newMediaId
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "restoreAndEvictScreenshot failed for $vaultedFilePath", e)
            null
        }
    }

    companion object {
        private const val TAG = "VaultManagerImpl"

        fun sanitizeRestoreFileNameCore(
            rawTitle: String,
            encryptedFilePath: String,
            fallbackFileName: String
        ): String {
            val strippedEncrypted = encryptedFilePath.substringAfterLast("/").removeSuffix(".enc")
            val resolvedExt = strippedEncrypted
                .substringAfterLast(".", "")
                .lowercase()
                .takeIf { it in setOf("jpg", "jpeg", "png", "webp", "heic", "heif") }
                ?: fallbackFileName.removeSuffix(".enc").substringAfterLast(".", "")
                    .lowercase()
                    .takeIf { it in setOf("jpg", "jpeg", "png", "webp", "heic", "heif") }
                ?: "jpg"

            val baseCandidate = when {
                rawTitle.isNotBlank() && !rawTitle.startsWith("secure_vault_") -> rawTitle
                fallbackFileName.isNotBlank() && !fallbackFileName.startsWith("secure_vault_") ->
                    fallbackFileName.removeSuffix(".enc")
                else -> ""
            }

            val rawBase = baseCandidate
                .replace(Regex("\\.(jpg|jpeg|png|webp|heic|heif)$", RegexOption.IGNORE_CASE), "")
                .replace(Regex("[/\\\\:*?\"<>|\\r\\n\\t]+"), "_")
                .trim(' ', '.', '_')

            val finalBase = if (rawBase.isNotBlank()) {
                rawBase.take(100)
            } else {
                "Screenshot_${System.currentTimeMillis()}"
            }
            return "$finalBase.$resolvedExt"
        }
    }
}
