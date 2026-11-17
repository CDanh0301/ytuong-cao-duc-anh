package com.example.test2.services

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ScreenshotJobService : JobService() {

    private val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters?): Boolean {
        jobScope.launch {
            try {
                ScreenshotDetectionManager.checkAndProcessScreenshots(applicationContext)
            } finally {
                // Re-schedule to maintain continuous trigger listening across screenshots
                ScreenshotJobScheduler.schedule(applicationContext)
                jobFinished(params, false)
            }
        }
        return true // Work running asynchronously in coroutine
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        jobScope.cancel()
        return true // Reschedule if cancelled prematurely
    }
}

object ScreenshotJobScheduler {
    private const val JOB_ID = 2026

    fun schedule(context: Context) {
        try {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val componentName = ComponentName(context, ScreenshotJobService::class.java)
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val trigger = JobInfo.TriggerContentUri(uri, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS)

            val builder = JobInfo.Builder(JOB_ID, componentName)
                .addTriggerContentUri(trigger)
                .setTriggerContentUpdateDelay(200)
                .setTriggerContentMaxDelay(1000)

            jobScheduler.schedule(builder.build())
        } catch (_: Exception) {
        }
    }
}
