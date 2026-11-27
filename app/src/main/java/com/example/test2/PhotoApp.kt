package com.example.test2

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.test2.di.AppContainer
import com.example.test2.di.AppModule
import com.example.test2.di.AppViewModelFactory
import com.example.test2.notifications.ScreenshotNotificationHelper
import com.example.test2.services.AgingJobScheduler
import com.example.test2.services.ScreenshotDetectionManager
import com.example.test2.services.ScreenshotJobScheduler
import com.example.test2.services.ScreenshotWatcherService
import com.example.test2.storage.EncryptedPhotoFetcher
import com.example.test2.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import java.util.concurrent.atomic.AtomicBoolean

class PhotoApp : Application(), ImageLoaderFactory {

    companion object {
        private val metadataDirty = AtomicBoolean(false)

        fun markMetadataDirty() {
            metadataDirty.set(true)
        }

        fun consumeMetadataDirty(): Boolean {
            return metadataDirty.getAndSet(false)
        }
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Application-scoped dependency container guaranteeing a single [com.example.test2.shared.repository.IPhotoRepository]
     * and shared state across [MainActivity], [com.example.test2.search.QuickSearchActivity], and tests.
     */
    val container: AppContainer by lazy {
        AppModule.createContainer(this, AppViewModelFactory.hasStoragePermission(this))
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(coil.intercept.Interceptor { chain ->
                    val request = chain.request
                    val isEncrypted = when (val data = request.data) {
                        is java.io.File -> data.name.endsWith(".enc")
                        is String -> data.endsWith(".enc")
                        is android.net.Uri -> data.path?.endsWith(".enc") == true
                        else -> false
                    }
                    val newRequest = if (isEncrypted) {
                        request.newBuilder()
                            .diskCachePolicy(coil.request.CachePolicy.DISABLED)
                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                            .build()
                    } else {
                        request
                    }
                    chain.proceed(newRequest)
                })
                add(EncryptedPhotoFetcher.StringFactory())
                add(EncryptedPhotoFetcher.UriFactory())
                add(EncryptedPhotoFetcher.FileFactory())
            }
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.e("PhotoApp", "FATAL CRASH on thread ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        ScreenshotNotificationHelper.createNotificationChannel(this)

        // 1. Arm JobScheduler with TriggerContentUri and daily aging monitor worker
        ScreenshotJobScheduler.schedule(this)
        AgingJobScheduler.schedule(this)

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
