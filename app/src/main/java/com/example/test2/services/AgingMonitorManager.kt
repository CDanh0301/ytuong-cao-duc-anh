package com.example.test2.services

import android.content.Context
import com.example.test2.PhotoApp
import com.example.test2.data.local.prefs.AgingPrefsDataSource
import com.example.test2.di.AppModule
import com.example.test2.di.AppViewModelFactory
import com.example.test2.notifications.ScreenshotNotificationHelper
import com.example.test2.shared.models.Photo
import com.example.test2.shared.repository.IPhotoRepository
import com.example.test2.storage.AndroidFilePersistenceDriver
import com.example.test2.storage.DeviceMediaScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AgingCheckResult(
    val agingPhotos: List<Photo>,
    val totalBytes: Long,
    val notificationSent: Boolean
)

object AgingMonitorManager {

    // Throttling window: gentle periodic reminder, at most once every 24 hours
    const val NOTIFY_INTERVAL_MS = 24 * 60 * 60 * 1000L

    fun shouldSendDailyNotification(
        clutterCount: Int,
        lastNotifiedMillis: Long,
        nowMillis: Long,
        force: Boolean
    ): Boolean {
        if (clutterCount <= 0) return false
        if (force || lastNotifiedMillis <= 0L) return true
        return (nowMillis - lastNotifiedMillis) >= NOTIFY_INTERVAL_MS
    }

    fun isDueForDailyCheck(lastCheckedMillis: Long, nowMillis: Long): Boolean {
        if (lastCheckedMillis <= 0L) return true
        return (nowMillis - lastCheckedMillis) >= NOTIFY_INTERVAL_MS
    }

    /**
     * Inspects device screenshots (reconciling MediaStore if permission is granted without triggering AI/Vault)
     * and counts screenshots strictly older than the configured retention period.
     * Sends a daily notification with the exact count N if N > 0 and 24h have elapsed.
     */
    suspend fun checkAgingAndNotify(context: Context, force: Boolean = false): AgingCheckResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        val repo: IPhotoRepository = if (appContext is PhotoApp) {
            appContext.container.repository.also { it.awaitInitialLoad() }
        } else {
            AppModule.providePhotoRepository(
                driver = AndroidFilePersistenceDriver(appContext),
                scope = CoroutineScope(Dispatchers.IO)
            ).also { it.loadInitialData(now) }
        }

        repo.checkMonthlyQuotaReset(now)

        if (AppViewModelFactory.hasStoragePermission(appContext)) {
            val scanResult = DeviceMediaScanner(appContext).scanDeviceImages()
            if (scanResult.photos.isNotEmpty() || scanResult.folders.isNotEmpty()) {
                repo.syncDevicePhotos(scanResult.photos, scanResult.folders)
            }
        }

        val isPro = repo.accountTier.value.isPro
        val retentionDays = repo.agingSettings.value.effectiveDefaultRetentionDays(isPro)
        val agingPhotos = repo.getAgingPhotos(daysOld = retentionDays, nowMillis = now)
        val totalBytes = agingPhotos.sumOf { it.fileSizeBytes }
        val clutterCount = agingPhotos.size

        val prefsDataSource = AgingPrefsDataSource(appContext)
        prefsDataSource.recordCheck(now, clutterCount, totalBytes)

        var notificationSent = false
        if (clutterCount > 0) {
            val lastNotified = prefsDataSource.getLastNotifiedMillis()
            if (shouldSendDailyNotification(clutterCount, lastNotified, now, force)) {
                ScreenshotNotificationHelper.showAgingNotification(
                    context = appContext,
                    clutterCount = clutterCount,
                    reclaimableBytes = totalBytes,
                    retentionDays = retentionDays
                )
                prefsDataSource.setLastNotifiedMillis(now)
                notificationSent = true
            }
        } else {
            ScreenshotNotificationHelper.cancelAgingNotification(appContext)
        }

        AgingCheckResult(
            agingPhotos = agingPhotos,
            totalBytes = totalBytes,
            notificationSent = notificationSent
        )
    }

    suspend fun checkAgingIfDue(context: Context): AgingCheckResult? {
        val now = System.currentTimeMillis()
        val lastChecked = getLastCheckedInfo(context).first
        return if (isDueForDailyCheck(lastChecked, now)) {
            checkAgingAndNotify(context, force = false)
        } else {
            null
        }
    }

    /**
     * Returns (lastCheckedMillis, lastClutterCount, lastClutterBytes) for UI status display.
     */
    fun getLastCheckedInfo(context: Context): Triple<Long, Int, Long> =
        AgingPrefsDataSource(context).getLastCheckedInfo()
}
