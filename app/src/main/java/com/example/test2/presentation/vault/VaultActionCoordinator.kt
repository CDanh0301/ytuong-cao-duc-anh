package com.example.test2.presentation.vault

import com.example.test2.ai.ScreenshotSyncOrchestrator
import com.example.test2.data.local.prefs.VaultPrefsDataSource
import com.example.test2.security.IVaultManager
import com.example.test2.shared.models.Photo
import com.example.test2.shared.repository.IPhotoRepository
import com.example.test2.shared.usecases.DeviceSecurityPort
import com.example.test2.shared.usecases.MetadataSyncPort
import com.example.test2.shared.usecases.WidgetPort
import com.example.test2.shared.usecases.photo.VaultPhotoUseCase
import com.example.test2.storage.isEncryptedPath
import com.example.test2.ui.RestoreProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/**
 * Shared coordinator for Privacy Vault enable/restore/single-photo operations used by
 * [VaultViewModel] and [com.example.test2.ui.PhotoAppViewModel].
 */
class VaultActionCoordinator(
    private val repository: IPhotoRepository,
    private val vaultManager: IVaultManager,
    private val deviceSecurity: DeviceSecurityPort,
    private val widgetPort: WidgetPort,
    private val vaultPhotoUseCase: VaultPhotoUseCase,
    private val orchestrator: ScreenshotSyncOrchestrator,
    private val vaultState: VaultStateStore,
    private val metadataSync: MetadataSyncPort
) {
    fun confirmEnableVault(vaultAll: Boolean, scopeForScan: CoroutineScope) {
        val scope = if (vaultAll) VaultPrefsDataSource.SCOPE_ALL else VaultPrefsDataSource.SCOPE_NEW_ONLY
        vaultState.showEnableVaultScopeDialog.value = false
        if (!vaultManager.hasAllFilesAccess()) {
            vaultManager.setPendingVaultScope(scope)
            vaultState.showAllFilesAccessDialog.value = true
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
        vaultState.isVaultEnabled.value = true
        vaultState.isAppUnlocked.value = true
        widgetPort.updateAll()
        orchestrator.scanDevicePhotos(scopeForScan)
    }

    fun checkAppLockOnResume() {
        if (vaultState.isVaultEnabled.value) {
            val elapsed = System.currentTimeMillis() - vaultState.lastBackgroundTimestamp
            if (vaultState.lastBackgroundTimestamp > 0L && elapsed >= 60_000L) {
                vaultState.isAppUnlocked.value = false
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
                vaultState.showVaultPermissionDeniedBanner.value =
                    "Két bảo mật chưa được bật do thiếu quyền Quản lý tất cả tệp"
            }
        }
    }

    fun restoreAllVaultedPhotos(coroutineScope: CoroutineScope) {
        coroutineScope.launch(Dispatchers.IO) {
            repository.awaitInitialLoad()
            vaultState.showDisableVaultDialog.value = false

            var restoredAny = false
            orchestrator.scanLock.withLock {
                val vaultedCount = repository.photos.value.count {
                    it.filePath.isEncryptedPath() || (it.isScreenshot && !it.filePath.startsWith("content://"))
                }
                if (vaultedCount > 0) {
                    vaultState.isRestoringPhotos.value = true
                    vaultState.restoreProgress.value = RestoreProgress(0, vaultedCount)
                    vaultState.isVaultEnabled.value = false
                }

                val summary = vaultPhotoUseCase.restoreAllPhotos { current, total ->
                    vaultState.restoreProgress.value = RestoreProgress(current, total)
                }.getOrThrow()

                vaultState.isRestoringPhotos.value = false
                vaultState.isVaultEnabled.value = summary.vaultEnabled
                if (!summary.vaultEnabled) {
                    vaultState.isAppUnlocked.value = true
                }
                restoredAny = summary.successCount > 0
                if (restoredAny) {
                    metadataSync.markMetadataDirty()
                }

                if (summary.successCount > 0 || summary.failedCount > 0) {
                    vaultState.showRestoreSuccessBanner.value = if (summary.failedCount == 0) {
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
            val result = orchestrator.scanLock.withLock {
                vaultPhotoUseCase.restorePhoto(photo).getOrNull()
            }
            if (result != null) {
                vaultState.isVaultEnabled.value = result.vaultStillEnabled
                metadataSync.markMetadataDirty()
                orchestrator.refreshActionCardCounts()
                vaultState.showRestoreSuccessBanner.value = "Đã khôi phục ảnh '${photo.title}' về Bộ sưu tập của máy"
                vaultState.emitRestoredPhotoMapping(photo.id, result.newPhotoId)
                orchestrator.scanDevicePhotos(coroutineScope)
            } else {
                vaultState.showRestoreSuccessBanner.value = "Không thể khôi phục ảnh '${photo.title}'. Vui lòng thử lại sau."
            }
        }
    }

    fun vaultSinglePhoto(photo: Photo, coroutineScope: CoroutineScope) {
        if (!vaultManager.hasAllFilesAccess()) {
            vaultState.showAllFilesAccessDialog.value = true
            return
        }
        if (!deviceSecurity.isDeviceSecure()) {
            vaultState.showRequireDeviceLockDialog.value = true
            return
        }
        coroutineScope.launch(Dispatchers.IO) {
            val success = orchestrator.scanLock.withLock {
                vaultPhotoUseCase.vaultPhoto(photo).isSuccess
            }
            if (success) {
                vaultState.isVaultEnabled.value = true
                vaultState.isAppUnlocked.value = true
                metadataSync.markMetadataDirty()
                widgetPort.updateAll()
                vaultState.showRestoreSuccessBanner.value = "Đã chuyển ảnh '${photo.title}' vào Két bảo mật an toàn"
            } else {
                vaultState.showRestoreSuccessBanner.value = "Không thể đưa ảnh '${photo.title}' vào Két bảo mật."
            }
        }
    }
}
