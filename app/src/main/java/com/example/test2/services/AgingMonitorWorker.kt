package com.example.test2.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.test2.util.AppLogger
import java.util.concurrent.TimeUnit

/**
 * Daily background worker that checks screenshot aging against configured retention rules
 * and notifies the user once per day when old screenshots can be cleaned up.
 */
class AgingMonitorWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            AgingMonitorManager.checkAgingAndNotify(applicationContext, force = false)
            Result.success()
        } catch (e: Exception) {
            AppLogger.e("AgingMonitorWorker", "Daily aging check failed", e)
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "aging_monitor_daily_work"

        fun enqueuePeriodic(context: Context) {
            try {
                val request = PeriodicWorkRequestBuilder<AgingMonitorWorker>(
                    24, TimeUnit.HOURS
                ).build()

                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            } catch (e: Exception) {
                AppLogger.w("AgingMonitorWorker", "Failed to schedule daily aging worker", e)
            }
        }
    }
}
