package com.example.test2.presentation.vault

import android.app.Application
import android.content.Intent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.test2.di.AppContainer
import com.example.test2.security.DeviceAuthManager
import com.example.test2.shared.models.Photo
import com.example.test2.storage.isEncryptedPath
import com.example.test2.ui.RestoreProgress
import com.example.test2.widget.PhotoSearchWidgetUpdater
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel dedicated to Privacy Vault locking, consent/permission dialogs, and photo vault/restore operations.
 */
class VaultViewModel(
    private val app: Application,
    private val container: AppContainer
) : AndroidViewModel(app) {

    private val repository = container.repository
    private val vaultManager = container.vaultManager
    private val vaultCoordinator = container.vaultActionCoordinator
    private val session = container.sessionState

    private val lockDialogsFlow = combine(
        session.isVaultEnabled,
        session.isAppUnlocked,
        session.showPrivacyConsentDialog,
        session.showAllFilesAccessDialog,
        session.showEnableVaultScopeDialog
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
        session.showDisableVaultDialog,
        session.isRestoringPhotos,
        session.restoreProgress,
        session.showRestoreSuccessBanner,
        session.showRequireDeviceLockDialog
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
        session.showVaultPermissionDeniedBanner
    ) { lockState, restoreState, deniedBanner ->
        lockState.copy(
            showDisableVaultDialog = restoreState.showDisableVaultDialog,
            isRestoringPhotos = restoreState.isRestoringPhotos,
            restoreProgress = restoreState.restoreProgress,
            showRestoreSuccessBanner = restoreState.showRestoreSuccessBanner,
            showRequireDeviceLockDialog = restoreState.showRequireDeviceLockDialog,
            showVaultPermissionDeniedBanner = deniedBanner
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, VaultUiState())

    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit = { unlockApp() },
        onError: (Int, CharSequence) -> Unit = { _, _ -> }
    ) = DeviceAuthManager.authenticate(activity = activity, onSuccess = onSuccess, onError = onError)

    fun getManageAllFilesIntent(): Intent = vaultManager.getManageAllFilesIntent()

    fun dismissRequireDeviceLockDialog() { session.showRequireDeviceLockDialog.value = false }
    fun dismissVaultPermissionDeniedBanner() { session.showVaultPermissionDeniedBanner.value = null }
    fun dismissRestoreSuccessBanner() { session.showRestoreSuccessBanner.value = null }
    fun dismissAllFilesAccessDialog() { session.showAllFilesAccessDialog.value = false }
    fun dismissEnableVaultScopeDialog() { session.showEnableVaultScopeDialog.value = false }
    fun dismissDisableVaultDialog() { session.showDisableVaultDialog.value = false }

    fun cancelAllFilesAccessDialog() {
        vaultManager.setPendingVaultScope(null)
        session.showAllFilesAccessDialog.value = false
    }

    fun onEnableVault() {
        vaultManager.setPromptedConsent(true)
        session.showPrivacyConsentDialog.value = false
        if (!DeviceAuthManager.isDeviceSecure(app)) {
            session.showRequireDeviceLockDialog.value = true
            return
        }
        confirmEnableVault(vaultAll = true)
    }

    fun onKeepStandard() {
        vaultManager.setVaultEnabled(false)
        vaultManager.setPromptedConsent(true)
        session.isVaultEnabled.value = false
        session.isAppUnlocked.value = true
        session.showPrivacyConsentDialog.value = false
    }

    fun setVaultEnabled(enabled: Boolean) {
        if (!enabled) requestDisableVault() else requestEnableVault()
    }

    fun requestEnableVault() {
        if (!DeviceAuthManager.isDeviceSecure(app)) {
            session.showRequireDeviceLockDialog.value = true
        } else {
            session.showEnableVaultScopeDialog.value = true
        }
    }

    fun confirmEnableVault(vaultAll: Boolean) =
        vaultCoordinator.confirmEnableVault(vaultAll, viewModelScope)

    fun notifyAppBackgrounded() {
        session.lastBackgroundTimestamp = System.currentTimeMillis()
    }

    fun unlockApp() {
        session.isAppUnlocked.value = true
        session.lastBackgroundTimestamp = 0L
    }

    fun lockApp() {
        if (session.isVaultEnabled.value) {
            session.isAppUnlocked.value = false
        }
    }

    fun requestDisableVault() {
        val vaultedCount = repository.photos.value.count {
            it.filePath.isEncryptedPath() || (it.isScreenshot && !it.filePath.startsWith("content://"))
        }
        if (vaultedCount > 0) {
            session.showDisableVaultDialog.value = true
        } else {
            vaultManager.setVaultEnabled(false)
            vaultManager.clearAllRestoredExempts()
            session.isVaultEnabled.value = false
            session.isAppUnlocked.value = true
            session.showDisableVaultDialog.value = false
            PhotoSearchWidgetUpdater.updateAll(app)
        }
    }

    fun restoreAllVaultedPhotos() = vaultCoordinator.restoreAllVaultedPhotos(viewModelScope)
    fun restoreSinglePhoto(photo: Photo) = vaultCoordinator.restoreSinglePhoto(photo, viewModelScope)
    fun vaultSinglePhoto(photo: Photo) = vaultCoordinator.vaultSinglePhoto(photo, viewModelScope)
}
