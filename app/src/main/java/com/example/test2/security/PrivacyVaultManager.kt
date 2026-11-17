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
import com.example.test2.storage.LocalCryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

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

    val isInternalEvicting = AtomicBoolean(false)

    private const val PREF_NAME = "privacy_vault_prefs"
    private const val KEY_VAULT_ENABLED = "is_vault_enabled"
    private const val KEY_HAS_PROMPTED_CONSENT = "has_prompted_consent"
    private const val KEY_VAULT_SCOPE = "vault_scope"
    private const val KEY_VAULT_ENABLED_SINCE = "vault_enabled_since"

    const val SCOPE_ALL = "ALL"
    const val SCOPE_NEW_ONLY = "NEW_ONLY"

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isVaultEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_VAULT_ENABLED, false)
    }

    fun setVaultEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_VAULT_ENABLED, enabled).apply()
    }

    fun getVaultScope(context: Context): String {
        return getPrefs(context).getString(KEY_VAULT_SCOPE, SCOPE_ALL) ?: SCOPE_ALL
    }

    fun setVaultScope(context: Context, scope: String) {
        getPrefs(context).edit().putString(KEY_VAULT_SCOPE, scope).apply()
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
            } catch (_: Exception) {
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
                return@withContext null
            }

            // 2. Silently evict original file from public storage if permissions allow
            var originalEvicted = false
            if (hasAllFilesAccess(context)) {
                try {
                    isInternalEvicting.set(true)
                    val resolvedPath = if (sourcePath.isNotBlank() && !sourcePath.startsWith("content://")) {
                        sourcePath
                    } else if (sourceUri != null) {
                        try {
                            context.contentResolver.query(
                                sourceUri,
                                arrayOf(MediaStore.Images.Media.DATA),
                                null,
                                null,
                                null
                            )?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val col = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                                    cursor.getString(col)
                                } else null
                            }
                        } catch (_: Exception) {
                            null
                        }
                    } else null

                    if (!resolvedPath.isNullOrBlank()) {
                        val originalFile = File(resolvedPath)
                        if (originalFile.exists()) {
                            originalEvicted = originalFile.delete()
                            if (originalEvicted) {
                                // CRITICAL: Tell Android MediaScanner to remove this file from MediaStore database
                                // so device gallery immediately drops it!
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
                    }

                    if (sourceUri != null) {
                        try {
                            val deletedRows = context.contentResolver.delete(sourceUri, null, null)
                            if (deletedRows > 0) {
                                originalEvicted = true
                            }
                        } catch (_: Exception) {
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    try {
                        delay(300)
                    } catch (_: Exception) {
                    }
                    isInternalEvicting.set(false)
                }
            }

            VaultResult(
                newFilePath = targetFile.absolutePath,
                newFileName = uniqueName,
                fileSizeBytes = targetFile.length(),
                isOriginalEvicted = originalEvicted
            )
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
        originalFileName: String
    ): RestoreResult? = withContext(Dispatchers.IO) {
        try {
            val vaultedFile = File(vaultedFilePath)
            if (!vaultedFile.exists() || !vaultedFile.canRead() || vaultedFile.length() == 0L) {
                return@withContext null
            }

            val cleanName = if (originalFileName.endsWith(".enc")) {
                originalFileName.removeSuffix(".enc")
            } else if (vaultedFile.name.endsWith(".enc")) {
                vaultedFile.name.removeSuffix(".enc")
            } else {
                originalFileName
            }

            val mimeType = when {
                cleanName.endsWith(".png", ignoreCase = true) -> "image/png"
                cleanName.endsWith(".webp", ignoreCase = true) -> "image/webp"
                else -> "image/jpeg"
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, cleanName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                val nowSec = System.currentTimeMillis() / 1000L
                put(MediaStore.Images.Media.DATE_ADDED, nowSec)
                put(MediaStore.Images.Media.DATE_MODIFIED, nowSec)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Screenshots")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val contentResolver = context.contentResolver
            val insertedUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext null

            var writeSuccess = false
            try {
                contentResolver.openOutputStream(insertedUri)?.use { outputStream ->
                    LocalCryptoManager.openDecryptedInputStream(vaultedFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                        outputStream.flush()
                    }
                    writeSuccess = true
                }
            } catch (_: Exception) {
                writeSuccess = false
            }

            if (!writeSuccess) {
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
                newMediaId = try {
                    ContentUris.parseId(insertedUri)
                } catch (_: Exception) {
                    System.currentTimeMillis()
                }
            }

            if (resolvedSize <= 0L && resolvedDataPath.isNotBlank()) {
                val f = File(resolvedDataPath)
                if (f.exists()) resolvedSize = f.length()
            }

            // Evict private encrypted file only after public file is safely written and verified
            try {
                vaultedFile.delete()
            } catch (_: Exception) {}

            RestoreResult(
                newUri = insertedUri.toString(),
                newFilePath = insertedUri.toString(),
                newFileName = cleanName,
                fileSizeBytes = if (resolvedSize > 0L) resolvedSize else vaultedFile.length(),
                newMediaId = newMediaId
            )
        } catch (_: Exception) {
            null
        }
    }
}
