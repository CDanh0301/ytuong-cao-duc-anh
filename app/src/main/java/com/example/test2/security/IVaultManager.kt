package com.example.test2.security

import android.content.Intent
import android.net.Uri

/**
 * Interface abstracting Privacy Vault encryption, eviction, restoration, and exemption rules.
 */
interface IVaultManager {
    fun isVaultEnabled(): Boolean
    fun setVaultEnabled(enabled: Boolean)
    fun getVaultScope(): String
    fun setVaultScope(scope: String)
    fun getPendingVaultScope(): String?
    fun setPendingVaultScope(scope: String?)
    fun getVaultEnabledSince(): Long
    fun setVaultEnabledSince(timestamp: Long)
    fun shouldVaultScreenshot(dateAddedMillis: Long): Boolean
    fun markRestoredExempt(photoIdOrMediaId: String)
    fun clearRestoredExempt(photoIdOrMediaId: String)
    fun isRestoredExempt(photoIdOrMediaId: String): Boolean
    fun clearAllRestoredExempts()
    fun shouldAutoVaultPhoto(photoId: String, dateAddedMillis: Long): Boolean
    fun hasPromptedConsent(): Boolean
    fun setPromptedConsent(prompted: Boolean)
    fun hasAllFilesAccess(): Boolean
    fun getManageAllFilesIntent(): Intent
    fun sanitizeRestoreFileName(rawTitle: String, encryptedFilePath: String, fallbackFileName: String): String
    fun beginInternalEviction()
    fun endInternalEviction()
    fun isInternalEvicting(): Boolean
    fun hasRemainingEncryptedFiles(): Boolean

    suspend fun vaultAndEvictScreenshot(
        sourceUri: Uri?,
        sourcePath: String,
        originalFileName: String
    ): VaultResult?

    suspend fun restoreAndEvictScreenshot(
        vaultedFilePath: String,
        originalFileName: String,
        originalDateAddedMillis: Long? = null,
        markExemptOnRestore: Boolean = false
    ): RestoreResult?
}
