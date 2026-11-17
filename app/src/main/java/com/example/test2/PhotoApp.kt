package com.example.test2

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.test2.database.AppDatabaseHelper
import com.example.test2.notifications.ScreenshotNotificationHelper
import com.example.test2.services.ScreenshotDetectionManager
import com.example.test2.services.ScreenshotJobScheduler
import com.example.test2.services.ScreenshotWatcherService
import com.example.test2.storage.EncryptedPhotoFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PhotoApp : Application(), ImageLoaderFactory {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val dbHelper by lazy {
        AppDatabaseHelper.getInstance(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(EncryptedPhotoFetcher.StringFactory())
                add(EncryptedPhotoFetcher.UriFactory())
                add(EncryptedPhotoFetcher.FileFactory())
            }
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        ScreenshotNotificationHelper.createNotificationChannel(this)

        // 1. Arm JobScheduler with TriggerContentUri (survives process termination/swiping away)
        ScreenshotJobScheduler.schedule(this)

        // 2. Start Foreground Monitoring Service (maintains continuous 0ms response in background)
        ScreenshotWatcherService.start(this)

        // 3. Check for any recent screenshots captured while app was closed
        checkLatestScreenshot()
    }

    fun checkLatestScreenshot() {
        applicationScope.launch {
            ScreenshotDetectionManager.checkAndProcessScreenshots(this@PhotoApp)
        }
    }
}
