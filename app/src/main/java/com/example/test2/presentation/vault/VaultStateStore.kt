package com.example.test2.presentation.vault

import com.example.test2.security.IVaultManager
import com.example.test2.ui.RestoreProgress
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Presentation state holder dedicated exclusively to Privacy Vault lock, consent,
 * permission dialogs, and restore progress, shared cleanly between [VaultViewModel],
 * [VaultActionCoordinator], and startup/resume lock checks.
 */
class VaultStateStore(
    vaultManager: IVaultManager
) {
    val isVaultEnabled = MutableStateFlow(vaultManager.isVaultEnabled())
    val isAppUnlocked = MutableStateFlow(!vaultManager.isVaultEnabled())
    val showPrivacyConsentDialog = MutableStateFlow(false)
    val showAllFilesAccessDialog = MutableStateFlow(false)
    val showEnableVaultScopeDialog = MutableStateFlow(false)
    val isRestoringPhotos = MutableStateFlow(false)
    val restoreProgress = MutableStateFlow(RestoreProgress(0, 0))
    val showDisableVaultDialog = MutableStateFlow(false)
    val showRestoreSuccessBanner = MutableStateFlow<String?>(null)
    val showRequireDeviceLockDialog = MutableStateFlow(false)
    val showVaultPermissionDeniedBanner = MutableStateFlow<String?>(null)
    var lastBackgroundTimestamp: Long = 0L

    private val _restoredPhotoIdEvents = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 8)
    val restoredPhotoIdEvents: SharedFlow<Pair<String, String>> = _restoredPhotoIdEvents.asSharedFlow()

    fun emitRestoredPhotoMapping(oldPhotoId: String, newPhotoId: String) {
        _restoredPhotoIdEvents.tryEmit(oldPhotoId to newPhotoId)
    }
}
