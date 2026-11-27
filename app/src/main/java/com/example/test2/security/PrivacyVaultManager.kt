package com.example.test2.security

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.test2.data.local.prefs.VaultPrefsDataSource

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

/**
 * Legacy static facade kept for backward compatibility with existing diagnostic tests and
 * Android settings Intent helpers. Production code should inject [IVaultManager] via DI.
 */
object PrivacyVaultManager {

    const val SCOPE_ALL = VaultPrefsDataSource.SCOPE_ALL
    const val SCOPE_NEW_ONLY = VaultPrefsDataSource.SCOPE_NEW_ONLY

    val isInternalEvicting = VaultEvictionGuard.isInternalEvicting

    fun getInstance(context: Context): IVaultManager = VaultManagerImpl(context)

    fun beginInternalEviction() = VaultEvictionGuard.beginInternalEviction()

    fun endInternalEviction() = VaultEvictionGuard.endInternalEviction()

    @Deprecated("Use IVaultManager via DI", ReplaceWith("VaultManagerImpl(context).isVaultEnabled()"))
    fun isVaultEnabled(context: Context): Boolean = VaultManagerImpl(context).isVaultEnabled()

    @Deprecated("Use IVaultManager via DI", ReplaceWith("VaultManagerImpl(context).setVaultEnabled(enabled)"))
    fun setVaultEnabled(context: Context, enabled: Boolean) = VaultManagerImpl(context).setVaultEnabled(enabled)

    @Deprecated("Use IVaultManager via DI")
    fun getVaultScope(context: Context): String = VaultManagerImpl(context).getVaultScope()

    @Deprecated("Use IVaultManager via DI")
    fun setVaultScope(context: Context, scope: String) = VaultManagerImpl(context).setVaultScope(scope)

    @Deprecated("Use IVaultManager via DI")
    fun getPendingVaultScope(context: Context): String? = VaultManagerImpl(context).getPendingVaultScope()

    @Deprecated("Use IVaultManager via DI")
    fun setPendingVaultScope(context: Context, scope: String?) = VaultManagerImpl(context).setPendingVaultScope(scope)

    @Deprecated("Use IVaultManager via DI")
    fun getVaultEnabledSince(context: Context): Long = VaultManagerImpl(context).getVaultEnabledSince()

    @Deprecated("Use IVaultManager via DI")
    fun setVaultEnabledSince(context: Context, timestamp: Long) = VaultManagerImpl(context).setVaultEnabledSince(timestamp)

    @Deprecated("Use IVaultManager via DI")
    fun shouldVaultScreenshot(context: Context, dateAddedMillis: Long): Boolean =
        VaultManagerImpl(context).shouldVaultScreenshot(dateAddedMillis)

    @Deprecated("Use IVaultManager via DI")
    fun markRestoredExempt(context: Context, photoIdOrMediaId: String) =
        VaultManagerImpl(context).markRestoredExempt(photoIdOrMediaId)

    @Deprecated("Use IVaultManager via DI")
    fun clearRestoredExempt(context: Context, photoIdOrMediaId: String) =
        VaultManagerImpl(context).clearRestoredExempt(photoIdOrMediaId)

    @Deprecated("Use IVaultManager via DI")
    fun isRestoredExempt(context: Context, photoIdOrMediaId: String): Boolean =
        VaultManagerImpl(context).isRestoredExempt(photoIdOrMediaId)

    @Deprecated("Use IVaultManager via DI")
    fun clearAllRestoredExempts(context: Context) =
        VaultManagerImpl(context).clearAllRestoredExempts()

    @Deprecated("Use IVaultManager via DI")
    fun shouldAutoVaultPhoto(context: Context, photoId: String, dateAddedMillis: Long): Boolean =
        VaultManagerImpl(context).shouldAutoVaultPhoto(photoId, dateAddedMillis)

    fun sanitizeRestoreFileName(
        rawTitle: String,
        encryptedFilePath: String,
        fallbackFileName: String
    ): String = VaultManagerImpl.sanitizeRestoreFileNameCore(rawTitle, encryptedFilePath, fallbackFileName)

    @Deprecated("Use IVaultManager via DI")
    fun hasPromptedConsent(context: Context): Boolean = VaultManagerImpl(context).hasPromptedConsent()

    @Deprecated("Use IVaultManager via DI")
    fun setPromptedConsent(context: Context, prompted: Boolean) =
        VaultManagerImpl(context).setPromptedConsent(prompted)

    @Deprecated("Use IVaultManager via DI")
    fun hasAllFilesAccess(context: Context): Boolean = VaultManagerImpl(context).hasAllFilesAccess()

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

    @Deprecated("Use IVaultManager via DI")
    suspend fun vaultAndEvictScreenshot(
        context: Context,
        sourceUri: Uri?,
        sourcePath: String,
        originalFileName: String
    ): VaultResult? = VaultManagerImpl(context).vaultAndEvictScreenshot(
        sourceUriString = sourceUri?.toString(),
        sourcePath = sourcePath,
        originalFileName = originalFileName
    )

    @Deprecated("Use IVaultManager via DI")
    suspend fun restoreAndEvictScreenshot(
        context: Context,
        vaultedFilePath: String,
        originalFileName: String,
        originalDateAddedMillis: Long? = null,
        markExemptOnRestore: Boolean = false
    ): RestoreResult? = VaultManagerImpl(context).restoreAndEvictScreenshot(
        vaultedFilePath = vaultedFilePath,
        originalFileName = originalFileName,
        originalDateAddedMillis = originalDateAddedMillis,
        markExemptOnRestore = markExemptOnRestore
    )
}
