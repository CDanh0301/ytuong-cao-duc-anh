package com.example.test2.presentation.vault

import com.example.test2.ui.RestoreProgress

sealed interface VaultUiEvent {
    data object RequestBiometricUnlock : VaultUiEvent
    data object OpenAllFilesSettings : VaultUiEvent
    data object OpenDeviceLockSettings : VaultUiEvent
}

data class VaultUiState(
    val isVaultEnabled: Boolean = false,
    val isAppUnlocked: Boolean = true,
    val protectedPhotoCount: Int = 0,
    val showPrivacyConsentDialog: Boolean = false,
    val showAllFilesAccessDialog: Boolean = false,
    val showEnableVaultScopeDialog: Boolean = false,
    val showDisableVaultDialog: Boolean = false,
    val isRestoringPhotos: Boolean = false,
    val restoreProgress: RestoreProgress = RestoreProgress(0, 0),
    val showRestoreSuccessBanner: String? = null,
    val showRequireDeviceLockDialog: Boolean = false,
    val showVaultPermissionDeniedBanner: String? = null
)
