package com.example.test2.services

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Background Aging Monitor JobService (IDEA.md Step 6 & Part 3).
 * Runs periodically (every 24 hours) to evaluate screenshot retention and post gentle clutter notifications.
 */
class AgingMonitorJobService : JobService() {

    private val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters?): Boolean {
        jobScope.launch {
            try {
                AgingMonitorManager.checkAgingAndNotify(applicationContext, force = false)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        jobScope.cancel()
        return true
    }
}

object AgingJobScheduler {
    private const val JOB_ID = 2027

    fun schedule(context: Context) {
        try {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val componentName = ComponentName(context, AgingMonitorJobService::class.java)

            // Periodic 24-hour interval; requires battery not low
            val builder = JobInfo.Builder(JOB_ID, componentName)
                .setPeriodic(24 * 60 * 60 * 1000L)
                .setPersisted(true)
                .setRequiresBatteryNotLow(true)

            jobScheduler.schedule(builder.build())
        } catch (_: Exception) {
        }
    }
}
