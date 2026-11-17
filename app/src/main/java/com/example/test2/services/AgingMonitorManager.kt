package com.example.test2.services

import android.content.Context
import android.content.SharedPreferences
import com.example.test2.notifications.ScreenshotNotificationHelper
import com.example.test2.shared.models.Photo
import com.example.test2.shared.repository.PhotoRepository
import com.example.test2.storage.AndroidFilePersistenceDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AgingCheckResult(
    val agingPhotos: List<Photo>,
    val totalBytes: Long,
    val notificationSent: Boolean
)

object AgingMonitorManager {

    private const val PREFS_NAME = "aging_monitor_prefs"
    private const val KEY_LAST_NOTIFIED = "key_last_notified"
    private const val KEY_LAST_CHECKED = "key_last_checked"
    private const val KEY_LAST_CLUTTER_COUNT = "key_last_clutter_count"
    private const val KEY_LAST_CLUTTER_BYTES = "key_last_clutter_bytes"

    // Throttling window: gentle periodic reminder, at most once every 24 hours
    private const val NOTIFY_INTERVAL_MS = 24 * 60 * 60 * 1000L

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Inspects stored photos and calculates clutter according to AgingSettings.
     * Evaluates Free vs Pro rules:
     * - Free: General retention cutoff (default 30 days)
     * - Pro: Custom per-category retention overrides + Auto-archive processed screenshots
     * Gently fires a notification if aging screenshots are found and throttling interval has elapsed.
     */
    suspend fun checkAgingAndNotify(context: Context, force: Boolean = false): AgingCheckResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val repo = PhotoRepository(
            persistenceDriver = AndroidFilePersistenceDriver(context),
            scope = CoroutineScope(Dispatchers.IO)
        )
        repo.loadInitialData(now)
        repo.checkMonthlyQuotaReset(now)
        val retentionDays = repo.agingSettings.value.retentionDays
        val agingPhotos = repo.getAgingPhotos(daysOld = retentionDays, nowMillis = now)
        val totalBytes = agingPhotos.sumOf { it.fileSizeBytes }
        val clutterCount = agingPhotos.size

        // Record last check
        val prefs = getPrefs(context)
        prefs.edit().apply {
            putLong(KEY_LAST_CHECKED, now)
            putInt(KEY_LAST_CLUTTER_COUNT, clutterCount)
            putLong(KEY_LAST_CLUTTER_BYTES, totalBytes)
            apply()
        }

        var notificationSent = false
        if (clutterCount > 0) {
            val lastNotified = prefs.getLong(KEY_LAST_NOTIFIED, 0L)
            val shouldNotify = force || (now - lastNotified >= NOTIFY_INTERVAL_MS)

            if (shouldNotify) {
                ScreenshotNotificationHelper.showAgingNotification(
                    context = context,
                    clutterCount = clutterCount,
                    reclaimableBytes = totalBytes
                )
                prefs.edit().putLong(KEY_LAST_NOTIFIED, now).apply()
                notificationSent = true
            }
        }

        AgingCheckResult(
            agingPhotos = agingPhotos,
            totalBytes = totalBytes,
            notificationSent = notificationSent
        )
    }

    /**
     * Returns (lastCheckedMillis, lastClutterCount, lastClutterBytes) for UI status display.
     */
    fun getLastCheckedInfo(context: Context): Triple<Long, Int, Long> {
        val prefs = getPrefs(context)
        val lastChecked = prefs.getLong(KEY_LAST_CHECKED, 0L)
        val clutterCount = prefs.getInt(KEY_LAST_CLUTTER_COUNT, 0)
        val clutterBytes = prefs.getLong(KEY_LAST_CLUTTER_BYTES, 0L)
        return Triple(lastChecked, clutterCount, clutterBytes)
    }
}
