package com.example.test2.security

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File

/**
 * Default Android implementation of [IVaultManager] delegating to [PrivacyVaultManager].
 */
class VaultManagerImpl(private val context: Context) : IVaultManager {

    private val appContext = context.applicationContext

    override fun isVaultEnabled(): Boolean = PrivacyVaultManager.isVaultEnabled(appContext)

    override fun setVaultEnabled(enabled: Boolean) {
        PrivacyVaultManager.setVaultEnabled(appContext, enabled)
    }

    override fun getVaultScope(): String = PrivacyVaultManager.getVaultScope(appContext)

    override fun setVaultScope(scope: String) {
        PrivacyVaultManager.setVaultScope(appContext, scope)
    }

    override fun getPendingVaultScope(): String? = PrivacyVaultManager.getPendingVaultScope(appContext)

    override fun setPendingVaultScope(scope: String?) {
        PrivacyVaultManager.setPendingVaultScope(appContext, scope)
    }

    override fun getVaultEnabledSince(): Long = PrivacyVaultManager.getVaultEnabledSince(appContext)

    override fun setVaultEnabledSince(timestamp: Long) {
        PrivacyVaultManager.setVaultEnabledSince(appContext, timestamp)
    }

    override fun shouldVaultScreenshot(dateAddedMillis: Long): Boolean =
        PrivacyVaultManager.shouldVaultScreenshot(appContext, dateAddedMillis)

    override fun markRestoredExempt(photoIdOrMediaId: String) {
        PrivacyVaultManager.markRestoredExempt(appContext, photoIdOrMediaId)
    }

    override fun clearRestoredExempt(photoIdOrMediaId: String) {
        PrivacyVaultManager.clearRestoredExempt(appContext, photoIdOrMediaId)
    }

    override fun isRestoredExempt(photoIdOrMediaId: String): Boolean =
        PrivacyVaultManager.isRestoredExempt(appContext, photoIdOrMediaId)

    override fun clearAllRestoredExempts() {
        PrivacyVaultManager.clearAllRestoredExempts(appContext)
    }

    override fun shouldAutoVaultPhoto(photoId: String, dateAddedMillis: Long): Boolean =
        PrivacyVaultManager.shouldAutoVaultPhoto(appContext, photoId, dateAddedMillis)

    override fun hasPromptedConsent(): Boolean = PrivacyVaultManager.hasPromptedConsent(appContext)

    override fun setPromptedConsent(prompted: Boolean) {
        PrivacyVaultManager.setPromptedConsent(appContext, prompted)
    }

    override fun hasAllFilesAccess(): Boolean = PrivacyVaultManager.hasAllFilesAccess(appContext)

    override fun getManageAllFilesIntent(): Intent = PrivacyVaultManager.getManageAllFilesIntent(appContext)

    override fun sanitizeRestoreFileName(
        rawTitle: String,
        encryptedFilePath: String,
        fallbackFileName: String
    ): String = PrivacyVaultManager.sanitizeRestoreFileName(rawTitle, encryptedFilePath, fallbackFileName)

    override fun beginInternalEviction() {
        PrivacyVaultManager.beginInternalEviction()
    }

    override fun endInternalEviction() {
        PrivacyVaultManager.endInternalEviction()
    }

    override fun isInternalEvicting(): Boolean = PrivacyVaultManager.isInternalEvicting.get()

    override fun hasRemainingEncryptedFiles(): Boolean {
        return File(appContext.filesDir, "photos")
            .listFiles()
            ?.any { it.isFile && it.name.endsWith(".enc") && it.length() > 0L } == true
    }

    override suspend fun vaultAndEvictScreenshot(
        sourceUri: Uri?,
        sourcePath: String,
        originalFileName: String
    ): VaultResult? = PrivacyVaultManager.vaultAndEvictScreenshot(appContext, sourceUri, sourcePath, originalFileName)

    override suspend fun restoreAndEvictScreenshot(
        vaultedFilePath: String,
        originalFileName: String,
        originalDateAddedMillis: Long?,
        markExemptOnRestore: Boolean
    ): RestoreResult? = PrivacyVaultManager.restoreAndEvictScreenshot(
        context = appContext,
        vaultedFilePath = vaultedFilePath,
        originalFileName = originalFileName,
        originalDateAddedMillis = originalDateAddedMillis,
        markExemptOnRestore = markExemptOnRestore
    )
}
