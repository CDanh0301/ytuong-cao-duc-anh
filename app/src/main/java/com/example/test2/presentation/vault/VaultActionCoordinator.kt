package com.example.test2.presentation.vault

import android.app.Application
import com.example.test2.ai.ScreenshotSyncOrchestrator
import com.example.test2.data.local.prefs.VaultPrefsDataSource
import com.example.test2.di.AppSessionState
import com.example.test2.security.DeviceAuthManager
import com.example.test2.security.IVaultManager
import com.example.test2.shared.models.Photo
import com.example.test2.shared.repository.PhotoRepository
import com.example.test2.shared.usecases.photo.VaultPhotoUseCase
import com.example.test2.storage.isEncryptedPath
import com.example.test2.ui.RestoreProgress
import com.example.test2.widget.PhotoSearchWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/**
 * Shared coordinator for Privacy Vault enable/restore/single-photo operations used by
 * both [VaultViewModel] and [com.example.test2.ui.PhotoAppViewModel].
 */
class VaultActionCoordinator(
    private val app: Application,
    private val repository: PhotoRepository,
    private val vaultManager: IVaultManager,
    private val vaultPhotoUseCase: VaultPhotoUseCase,
    private val orchestrator: ScreenshotSyncOrchestrator,
    private val session: AppSessionState
) {
    fun confirmEnableVault(vaultAll: Boolean, scopeForScan: CoroutineScope) {
        val scope = if (vaultAll) VaultPrefsDataSource.SCOPE_ALL else VaultPrefsDataSource.SCOPE_NEW_ONLY
        session.showEnableVaultScopeDialog.value = false
        if (!vaultManager.hasAllFilesAccess()) {
            vaultManager.setPendingVaultScope(scope)
            session.showAllFilesAccessDialog.value = true
        } else {
            vaultManager.setPendingVaultScope(null)
            activateVaultNow(scope, scopeForScan)
        }
    }

    fun activateVaultNow(scope: String, scopeForScan: CoroutineScope) {
        if (scope == VaultPrefsDataSource.SCOPE_ALL) {
            vaultManager.clearAllRestoredExempts()
        }
        vaultManager.setVaultScope(scope)
        vaultManager.setVaultEnabledSince(System.currentTimeMillis())
        vaultManager.setVaultEnabled(true)
        session.isVaultEnabled.value = true
        session.isAppUnlocked.value = true
        PhotoSearchWidgetUpdater.updateAll(app)
        orchestrator.scanDevicePhotos(scopeForScan)
    }

    fun checkAppLockOnResume() {
        if (session.isVaultEnabled.value) {
            val elapsed = System.currentTimeMillis() - session.lastBackgroundTimestamp
            if (session.lastBackgroundTimestamp > 0L && elapsed >= 60_000L) {
                session.isAppUnlocked.value = false
            }
        }
    }

    fun checkVaultPermissionResumed(scopeForScan: CoroutineScope) {
        val pendingScope = vaultManager.getPendingVaultScope()
        if (pendingScope != null) {
            vaultManager.setPendingVaultScope(null)
            if (vaultManager.hasAllFilesAccess()) {
                activateVaultNow(pendingScope, scopeForScan)
            } else {
                session.showVaultPermissionDeniedBanner.value =
                    "Két bảo mật chưa được bật do thiếu quyền Quản lý tất cả tệp"
            }
        }
    }

    fun restoreAllVaultedPhotos(coroutineScope: CoroutineScope) {
        coroutineScope.launch(Dispatchers.IO) {
            repository.awaitInitialLoad()
            session.showDisableVaultDialog.value = false

            var restoredAny = false
            session.scanLock.withLock {
                val vaultedCount = repository.photos.value.count {
                    it.filePath.isEncryptedPath() || (it.isScreenshot && !it.filePath.startsWith("content://"))
                }
                if (vaultedCount > 0) {
                    session.isRestoringPhotos.value = true
                    session.restoreProgress.value = RestoreProgress(0, vaultedCount)
                    session.isVaultEnabled.value = false
                }

                val summary = vaultPhotoUseCase.restoreAllPhotos { current, total ->
                    session.restoreProgress.value = RestoreProgress(current, total)
                }.getOrThrow()

                session.isRestoringPhotos.value = false
                session.isVaultEnabled.value = summary.vaultEnabled
                if (!summary.vaultEnabled) {
                    session.isAppUnlocked.value = true
                }
                restoredAny = summary.successCount > 0

                if (summary.successCount > 0 || summary.failedCount > 0) {
                    session.showRestoreSuccessBanner.value = if (summary.failedCount == 0) {
                        "Đã khôi phục thành công toàn bộ ${summary.successCount} ảnh về Bộ sưu tập của máy"
                    } else {
                        "Đã khôi phục ${summary.successCount} ảnh. Còn ${summary.failedCount} ảnh chưa thể xuất ra (vẫn được lưu an toàn trong Két). Vui lòng thử lại sau."
                    }
                }
            }

            orchestrator.refreshActionCardCounts()
            if (restoredAny) {
                orchestrator.scanDevicePhotos(coroutineScope)
            }
        }
    }

    fun restoreSinglePhoto(photo: Photo, coroutineScope: CoroutineScope) {
        coroutineScope.launch(Dispatchers.IO) {
            val result = session.scanLock.withLock {
                vaultPhotoUseCase.restorePhoto(photo).getOrNull()
            }
            if (result != null) {
                session.isVaultEnabled.value = result.vaultStillEnabled
                orchestrator.refreshActionCardCounts()
                session.showRestoreSuccessBanner.value = "Đã khôi phục ảnh '${photo.title}' về Bộ sưu tập của máy"
                if (session.photoSettings.value?.id == photo.id) {
                    session.photoSettings.value = repository.photos.value.firstOrNull { it.id == result.newPhotoId }
                }
                orchestrator.scanDevicePhotos(coroutineScope)
            } else {
                session.showRestoreSuccessBanner.value = "Không thể khôi phục ảnh '${photo.title}'. Vui lòng thử lại sau."
            }
        }
    }

    fun vaultSinglePhoto(photo: Photo, coroutineScope: CoroutineScope) {
        if (!vaultManager.hasAllFilesAccess()) {
            session.showAllFilesAccessDialog.value = true
            return
        }
        if (!DeviceAuthManager.isDeviceSecure(app)) {
            session.showRequireDeviceLockDialog.value = true
            return
        }
        coroutineScope.launch(Dispatchers.IO) {
            val success = session.scanLock.withLock {
                vaultPhotoUseCase.vaultPhoto(photo).isSuccess
            }
            if (success) {
                session.isVaultEnabled.value = true
                session.isAppUnlocked.value = true
                PhotoSearchWidgetUpdater.updateAll(app)
                session.showRestoreSuccessBanner.value = "Đã chuyển ảnh '${photo.title}' vào Két bảo mật an toàn"
                if (session.photoSettings.value?.id == photo.id) {
                    session.photoSettings.value = repository.photos.value.firstOrNull { it.id == photo.id }
                }
            } else {
                session.showRestoreSuccessBanner.value = "Không thể đưa ảnh '${photo.title}' vào Két bảo mật."
            }
        }
    }
}
