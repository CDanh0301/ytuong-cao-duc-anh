package com.example.test2.presentation.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test2.security.IVaultManager
import com.example.test2.shared.models.Photo
import com.example.test2.shared.repository.IPhotoRepository
import com.example.test2.shared.usecases.DeviceSecurityPort
import com.example.test2.shared.usecases.WidgetPort
import com.example.test2.storage.isEncryptedPath
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Pure MVVM ViewModel dedicated to Privacy Vault locking, consent/permission dialogs,
 * and photo vault/restore operations without coupling to Android UI/Context APIs.
 */
class VaultViewModel(
    private val repository: IPhotoRepository,
    private val vaultManager: IVaultManager,
    private val deviceSecurity: DeviceSecurityPort,
    private val vaultCoordinator: VaultActionCoordinator,
    private val widgetPort: WidgetPort,
    private val vaultState: VaultStateStore
) : ViewModel() {

    private val _events = MutableSharedFlow<VaultUiEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<VaultUiEvent> = _events.asSharedFlow()

    private val protectedCountFlow = repository.photos.map { list ->
        list.count { it.filePath.isEncryptedPath() || (it.isScreenshot && !it.filePath.startsWith("content://")) }
    }

    private val lockDialogsFlow = combine(
        vaultState.isVaultEnabled,
        vaultState.isAppUnlocked,
        vaultState.showPrivacyConsentDialog,
        vaultState.showAllFilesAccessDialog,
        vaultState.showEnableVaultScopeDialog
    ) { enabled, unlocked, consent, allFiles, scope ->
        VaultUiState(
            isVaultEnabled = enabled,
            isAppUnlocked = unlocked,
            showPrivacyConsentDialog = consent,
            showAllFilesAccessDialog = allFiles,
            showEnableVaultScopeDialog = scope
        )
    }

    private val restoreDialogsFlow = combine(
        vaultState.showDisableVaultDialog,
        vaultState.isRestoringPhotos,
        vaultState.restoreProgress,
        vaultState.showRestoreSuccessBanner,
        vaultState.showRequireDeviceLockDialog
    ) { disableDlg, restoring, progress, successBanner, requireLock ->
        VaultUiState(
            showDisableVaultDialog = disableDlg,
            isRestoringPhotos = restoring,
            restoreProgress = progress,
            showRestoreSuccessBanner = successBanner,
            showRequireDeviceLockDialog = requireLock
        )
    }

    val uiState: StateFlow<VaultUiState> = combine(
        lockDialogsFlow,
        restoreDialogsFlow,
        vaultState.showVaultPermissionDeniedBanner,
        protectedCountFlow
    ) { lockState, restoreState, deniedBanner, protectedCount ->
        lockState.copy(
            protectedPhotoCount = protectedCount,
            showDisableVaultDialog = restoreState.showDisableVaultDialog,
            isRestoringPhotos = restoreState.isRestoringPhotos,
            restoreProgress = restoreState.restoreProgress,
            showRestoreSuccessBanner = restoreState.showRestoreSuccessBanner,
            showRequireDeviceLockDialog = restoreState.showRequireDeviceLockDialog,
            showVaultPermissionDeniedBanner = deniedBanner
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, VaultUiState())

    fun requestBiometricUnlock() {
        _events.tryEmit(VaultUiEvent.RequestBiometricUnlock)
    }

    fun openAllFilesSettings() {
        vaultState.showAllFilesAccessDialog.value = false
        _events.tryEmit(VaultUiEvent.OpenAllFilesSettings)
    }

    fun openDeviceLockSettings() {
        vaultState.showRequireDeviceLockDialog.value = false
        _events.tryEmit(VaultUiEvent.OpenDeviceLockSettings)
    }

    fun dismissRequireDeviceLockDialog() { vaultState.showRequireDeviceLockDialog.value = false }
    fun dismissVaultPermissionDeniedBanner() { vaultState.showVaultPermissionDeniedBanner.value = null }
    fun dismissRestoreSuccessBanner() { vaultState.showRestoreSuccessBanner.value = null }
    fun dismissAllFilesAccessDialog() { vaultState.showAllFilesAccessDialog.value = false }
    fun dismissEnableVaultScopeDialog() { vaultState.showEnableVaultScopeDialog.value = false }
    fun dismissDisableVaultDialog() { vaultState.showDisableVaultDialog.value = false }

    fun cancelAllFilesAccessDialog() {
        vaultManager.setPendingVaultScope(null)
        vaultState.showAllFilesAccessDialog.value = false
    }

    fun onEnableVault() {
        vaultManager.setPromptedConsent(true)
        vaultState.showPrivacyConsentDialog.value = false
        if (!deviceSecurity.isDeviceSecure()) {
            vaultState.showRequireDeviceLockDialog.value = true
            return
        }
        confirmEnableVault(vaultAll = true)
    }

    fun onKeepStandard() {
        vaultManager.setVaultEnabled(false)
        vaultManager.setPromptedConsent(true)
        vaultState.isVaultEnabled.value = false
        vaultState.isAppUnlocked.value = true
        vaultState.showPrivacyConsentDialog.value = false
    }

    fun setVaultEnabled(enabled: Boolean) {
        if (!enabled) requestDisableVault() else requestEnableVault()
    }

    fun requestEnableVault() {
        if (!deviceSecurity.isDeviceSecure()) {
            vaultState.showRequireDeviceLockDialog.value = true
        } else {
            vaultState.showEnableVaultScopeDialog.value = true
        }
    }

    fun confirmEnableVault(vaultAll: Boolean) =
        vaultCoordinator.confirmEnableVault(vaultAll, viewModelScope)

    fun notifyAppBackgrounded() {
        vaultState.lastBackgroundTimestamp = System.currentTimeMillis()
    }

    fun unlockApp() {
        vaultState.isAppUnlocked.value = true
        vaultState.lastBackgroundTimestamp = 0L
    }

    fun lockApp() {
        if (vaultState.isVaultEnabled.value) {
            vaultState.isAppUnlocked.value = false
        }
    }

    fun requestDisableVault() {
        val vaultedCount = repository.photos.value.count {
            it.filePath.isEncryptedPath() || (it.isScreenshot && !it.filePath.startsWith("content://"))
        }
        if (vaultedCount > 0) {
            vaultState.showDisableVaultDialog.value = true
        } else {
            vaultManager.setVaultEnabled(false)
            vaultManager.clearAllRestoredExempts()
            vaultState.isVaultEnabled.value = false
            vaultState.isAppUnlocked.value = true
            vaultState.showDisableVaultDialog.value = false
            widgetPort.updateAll()
        }
    }

    fun restoreAllVaultedPhotos() = vaultCoordinator.restoreAllVaultedPhotos(viewModelScope)
    fun restoreSinglePhoto(photo: Photo) = vaultCoordinator.restoreSinglePhoto(photo, viewModelScope)
    fun vaultSinglePhoto(photo: Photo) = vaultCoordinator.vaultSinglePhoto(photo, viewModelScope)
}
