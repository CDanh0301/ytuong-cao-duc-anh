package com.example.test2.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.test2.services.AgingJobScheduler
import com.example.test2.services.ScreenshotJobScheduler
import com.example.test2.services.ScreenshotQueueWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent?.action == "android.intent.action.USER_UNLOCKED"
        ) {
            // Phase 1: Re-arm JobScheduler and WorkManager without starting a foreground service
            // (Android 15 targetSdk 35 disallows starting foreground services from boot receivers)
            ScreenshotJobScheduler.schedule(context)
            AgingJobScheduler.schedule(context)
            ScreenshotQueueWorker.enqueue(context)
        }
    }
}
