package com.example.test2.security

import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.example.test2.storage.LocalCryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class VaultResult(
    val newFilePath: String,
    val newFileName: String,
    val fileSizeBytes: Long,
    val isOriginalEvicted: Boolean
)

data class RestoreResult(
    val newUri: String,
    val newFilePath: String,
    val newFileName: String,
    val fileSizeBytes: Long,
    val newMediaId: Long
)

object PrivacyVaultManager {

    private const val TAG = "PrivacyVaultManager"

    val isInternalEvicting = AtomicBoolean(false)
    private val evictionDepth = AtomicInteger(0)

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

    private const val PREF_NAME = "privacy_vault_prefs"
    private const val KEY_VAULT_ENABLED = "is_vault_enabled"
    private const val KEY_HAS_PROMPTED_CONSENT = "has_prompted_consent"
    private const val KEY_VAULT_SCOPE = "vault_scope"
    private const val KEY_VAULT_ENABLED_SINCE = "vault_enabled_since"
    private const val KEY_PENDING_VAULT_SCOPE = "pending_vault_scope"
    private const val KEY_RESTORED_EXEMPT_IDS = "restored_exempt_ids"

    private val exemptLock = Any()

    const val SCOPE_ALL = "ALL"
    const val SCOPE_NEW_ONLY = "NEW_ONLY"

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isVaultEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_VAULT_ENABLED, false)
    }

    fun setVaultEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_VAULT_ENABLED, enabled).commit()
    }

    fun getVaultScope(context: Context): String {
        return getPrefs(context).getString(KEY_VAULT_SCOPE, SCOPE_ALL) ?: SCOPE_ALL
    }

    fun setVaultScope(context: Context, scope: String) {
        getPrefs(context).edit().putString(KEY_VAULT_SCOPE, scope).commit()
    }

    fun getPendingVaultScope(context: Context): String? {
        return getPrefs(context).getString(KEY_PENDING_VAULT_SCOPE, null)
    }

    fun setPendingVaultScope(context: Context, scope: String?) {
        val editor = getPrefs(context).edit()
        if (scope == null) {
            editor.remove(KEY_PENDING_VAULT_SCOPE)
        } else {
            editor.putString(KEY_PENDING_VAULT_SCOPE, scope)
        }
        // Use synchronous commit() so pending state survives immediate OS process kill
        // when MANAGE_EXTERNAL_STORAGE is toggled in System Settings.
        editor.commit()
    }

    fun getVaultEnabledSince(context: Context): Long {
        return getPrefs(context).getLong(KEY_VAULT_ENABLED_SINCE, 0L)
    }

    fun setVaultEnabledSince(context: Context, timestamp: Long) {
        getPrefs(context).edit().putLong(KEY_VAULT_ENABLED_SINCE, timestamp).apply()
    }

    fun shouldVaultScreenshot(context: Context, dateAddedMillis: Long): Boolean {
        if (!isVaultEnabled(context)) return false
        val scope = getVaultScope(context)
        if (scope == SCOPE_NEW_ONLY) {
            val since = getVaultEnabledSince(context)
            return dateAddedMillis >= since
        }
        return true
    }

    private fun normalizeExemptKey(idOrMediaId: String): String {
        val trimmed = idOrMediaId.trim()
        return if (trimmed.startsWith("device_")) trimmed else "device_$trimmed"
    }

    fun markRestoredExempt(context: Context, photoIdOrMediaId: String) {
        if (photoIdOrMediaId.isBlank()) return
        val key = normalizeExemptKey(photoIdOrMediaId)
        synchronized(exemptLock) {
            val prefs = getPrefs(context)
            val current = prefs.getStringSet(KEY_RESTORED_EXEMPT_IDS, null)?.toMutableSet() ?: mutableSetOf()
            if (current.add(key)) {
                prefs.edit().putStringSet(KEY_RESTORED_EXEMPT_IDS, HashSet(current)).commit()
            }
        }
    }

    fun clearRestoredExempt(context: Context, photoIdOrMediaId: String) {
        if (photoIdOrMediaId.isBlank()) return
        val key = normalizeExemptKey(photoIdOrMediaId)
        synchronized(exemptLock) {
            val prefs = getPrefs(context)
            val current = prefs.getStringSet(KEY_RESTORED_EXEMPT_IDS, null)?.toMutableSet() ?: return
            if (current.remove(key)) {
                prefs.edit().putStringSet(KEY_RESTORED_EXEMPT_IDS, HashSet(current)).commit()
            }
        }
    }

    fun isRestoredExempt(context: Context, photoIdOrMediaId: String): Boolean {
        if (photoIdOrMediaId.isBlank()) return false
        val key = normalizeExemptKey(photoIdOrMediaId)
        synchronized(exemptLock) {
            val current = getPrefs(context).getStringSet(KEY_RESTORED_EXEMPT_IDS, null) ?: return false
            return current.contains(key) || current.contains(photoIdOrMediaId)
        }
    }

    fun clearAllRestoredExempts(context: Context) {
        synchronized(exemptLock) {
            getPrefs(context).edit().remove(KEY_RESTORED_EXEMPT_IDS).commit()
        }
    }

    /**
     * Unified auto-vault check for background workers and foreground gallery scans.
     * Respects both vault status/scope and explicit single-photo user restore exemptions.
     */
    fun shouldAutoVaultPhoto(context: Context, photoId: String, dateAddedMillis: Long): Boolean {
        if (!shouldVaultScreenshot(context, dateAddedMillis)) return false
        if (isRestoredExempt(context, photoId)) return false
        return true
    }

    /**
     * Produces a filesystem- and MediaStore-safe filename when restoring an encrypted photo,
     * stripping illegal path separators and avoiding double extensions or secure_vault_* prefixes.
     */
    fun sanitizeRestoreFileName(
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

    fun hasPromptedConsent(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_HAS_PROMPTED_CONSENT, false)
    }

    fun setPromptedConsent(context: Context, prompted: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_HAS_PROMPTED_CONSENT, prompted).apply()
    }

    /**
     * Checks if the app has All Files Access (MANAGE_EXTERNAL_STORAGE).
     * On Android 11+ (API 30+), this allows silently deleting screenshots from public MediaStore
     * without showing repeated system confirmation prompts.
     */
    fun hasAllFilesAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /**
     * Creates an Intent to navigate the user directly to the system settings page
     * where they can toggle "Allow management of all files" for this app.
     */
    fun getManageAllFilesIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
            } catch (_: Exception) {
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            }
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    /**
     * Moves a screenshot from public MediaStore into private AES-256-GCM encrypted app storage.
     * If All Files Access is granted, silently deletes the public original from disk and MediaStore.
     */
    suspend fun vaultAndEvictScreenshot(
        context: Context,
        sourceUri: Uri?,
        sourcePath: String,
        originalFileName: String
    ): VaultResult? = withContext(Dispatchers.IO) {
        try {
            // Check available internal storage before proceeding to prevent out-of-space crash
            val usableSpace = context.filesDir.usableSpace
            val minimumSafeMargin = 300 * 1024 * 1024L // 300MB safe margin
            if (usableSpace < minimumSafeMargin) {
                return@withContext null
            }

            val photosDir = File(context.filesDir, "photos")
            if (!photosDir.exists()) {
                photosDir.mkdirs()
            }

            val extension = originalFileName.substringAfterLast(".", "jpg")
            val uniqueName = "secure_vault_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$extension.enc"
            val targetFile = File(photosDir, uniqueName)

            // 1. Read source stream (try content resolver first, then direct file)
            val streamOpened = try {
                if (sourceUri != null) {
                    context.contentResolver.openInputStream(sourceUri)?.use { input ->
                        LocalCryptoManager.encryptStreamToFile(input, targetFile)
                        true
                    } ?: false
                } else false
            } catch (e: Exception) {
                Log.w(TAG, "URI stream encryption failed for $sourceUri, trying direct path", e)
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
                    try { targetFile.delete() } catch (_: Exception) {}
                }
                return@withContext null
            }

            // 2. Silently evict original file from public storage if permissions allow
            var originalEvicted = false
            if (hasAllFilesAccess(context)) {
                beginInternalEviction()
                try {
                    // Resolve physical path BEFORE deleting from MediaStore (query fails once row is gone)
                    val resolvedPath = resolvePhysicalPath(context, sourceUri, sourcePath)

                    // Delete via ContentResolver FIRST so MediaProvider atomically removes both row & disk file
                    var deletedRows = 0
                    if (sourceUri != null) {
                        try {
                            deletedRows = context.contentResolver.delete(sourceUri, null, null)
                            if (deletedRows > 0) {
                                originalEvicted = true
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "contentResolver.delete failed for $sourceUri", e)
                        }
                    }

                    // Fallback / cleanup if physical file remains on disk or MediaStore row wasn't removed
                    if (!resolvedPath.isNullOrBlank()) {
                        val originalFile = File(resolvedPath)
                        if (originalFile.exists()) {
                            val physicalDeleted = originalFile.delete()
                            if (physicalDeleted) {
                                originalEvicted = true
                            }
                        }
                        if (deletedRows <= 0) {
                            try {
                                MediaScannerConnection.scanFile(
                                    context.applicationContext,
                                    arrayOf(resolvedPath),
                                    null
                                ) { _, _ -> }
                            } catch (_: Exception) {
                            }
                        }
                    }

                    // Explicitly notify content resolver so system gallery & external media observers refresh immediately
                    try {
                        context.contentResolver.notifyChange(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null)
                        if (sourceUri != null) {
                            context.contentResolver.notifyChange(sourceUri, null)
                        }
                    } catch (_: Exception) {
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error during original eviction for $originalFileName", e)
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
            Log.e(TAG, "vaultAndEvictScreenshot failed for $originalFileName", e)
            null
        }
    }

    private fun resolvePhysicalPath(context: Context, sourceUri: Uri?, sourcePath: String): String? {
        if (sourcePath.isNotBlank() && !sourcePath.startsWith("content://")) {
            return sourcePath
        }
        if (sourceUri == null) return null
        return try {
            context.contentResolver.query(
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
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decrypts a screenshot from private app storage and restores it back to the public MediaStore
     * (e.g. Pictures/Screenshots). Only when the file is safely written to public storage and
     * registered in MediaStore is the internal .enc file safely evicted.
     */
    suspend fun restoreAndEvictScreenshot(
        context: Context,
        vaultedFilePath: String,
        originalFileName: String,
        originalDateAddedMillis: Long? = null,
        markExemptOnRestore: Boolean = false
    ): RestoreResult? = withContext(Dispatchers.IO) {
        try {
            val vaultedFile = File(vaultedFilePath)
            val vaultedFileSize = vaultedFile.length()
            if (!vaultedFile.exists() || !vaultedFile.canRead() || vaultedFileSize == 0L) {
                Log.w(TAG, "restoreAndEvictScreenshot missing/empty vaulted file: $vaultedFilePath")
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

            val contentResolver = context.contentResolver
            val insertedUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (insertedUri == null) {
                Log.w(TAG, "restoreAndEvictScreenshot MediaStore.insert returned null for $finalCleanName")
                return@withContext null
            }

            val preParsedId = try {
                ContentUris.parseId(insertedUri)
            } catch (_: Exception) {
                -1L
            }
            if (markExemptOnRestore && preParsedId > 0L) {
                // Register exemption BEFORE IS_PENDING = 0 so no async observer or worker can re-vault it
                markRestoredExempt(context, "device_$preParsedId")
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
                Log.e(TAG, "restoreAndEvictScreenshot write failed for $finalCleanName ($insertedUri)", e)
                writeSuccess = false
            }

            if (!writeSuccess) {
                Log.w(TAG, "restoreAndEvictScreenshot !writeSuccess for $finalCleanName, deleting pending $insertedUri")
                if (markExemptOnRestore && preParsedId > 0L) {
                    clearRestoredExempt(context, "device_$preParsedId")
                }
                try {
                    contentResolver.delete(insertedUri, null, null)
                } catch (_: Exception) {}
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
            } catch (_: Exception) {}

            if (newMediaId <= 0L) {
                newMediaId = if (preParsedId > 0L) preParsedId else System.currentTimeMillis()
            }
            if (markExemptOnRestore && newMediaId > 0L && newMediaId != preParsedId) {
                markRestoredExempt(context, "device_$newMediaId")
            }

            if (resolvedSize <= 0L && resolvedDataPath.isNotBlank()) {
                val f = File(resolvedDataPath)
                if (f.exists()) resolvedSize = f.length()
            }

            // Preserve original timestamps across both disk mtime and MediaStore columns
            if (resolvedDataPath.isNotBlank() && hasAllFilesAccess(context)) {
                try {
                    File(resolvedDataPath).setLastModified(resolvedOriginalMillis)
                } catch (_: Exception) {}
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val syncTimeValues = ContentValues().apply {
                        put(MediaStore.Images.Media.DATE_TAKEN, resolvedOriginalMillis)
                        put(MediaStore.Images.Media.DATE_MODIFIED, timestampSec)
                    }
                    contentResolver.update(insertedUri, syncTimeValues, null, null)
                } catch (_: Exception) {}
            }

            // Evict private encrypted file only after public file is safely written and verified
            try {
                vaultedFile.delete()
            } catch (_: Exception) {}

            RestoreResult(
                newUri = insertedUri.toString(),
                newFilePath = insertedUri.toString(),
                newFileName = finalCleanName,
                fileSizeBytes = if (resolvedSize > 0L) resolvedSize else vaultedFileSize,
                newMediaId = newMediaId
            )
        } catch (e: Exception) {
            Log.e(TAG, "restoreAndEvictScreenshot failed for $vaultedFilePath", e)
            null
        }
    }
}
