package com.example.test2.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * Phase 1: Replaced by JobScheduler (ScreenshotJobService) + WorkManager (ScreenshotQueueWorker).
 * Discontinued always-on foreground service to comply with Android 15 (targetSdk 35) background restrictions.
 */
class ScreenshotWatcherService : Service() {

    companion object {
        fun start(context: Context) {
            // Forward to persistent JobScheduler & WorkManager
            ScreenshotJobScheduler.schedule(context)
            ScreenshotQueueWorker.enqueue(context)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
