package com.example.test2.storage

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.provider.MediaStore
import com.example.test2.security.IVaultManager
import com.example.test2.util.AppLogger

/**
 * Encapsulates Android MediaStore [ContentObserver] registration and debouncing
 * so ViewModels do not directly couple to Android system content observer APIs.
 */
class DeviceMediaObserver(
    private val context: Context,
    private val vaultManager: IVaultManager,
    private val onMediaChanged: () -> Unit
) {
    private val appContext = context.applicationContext
    private var contentObserver: ContentObserver? = null

    fun register() {
        if (contentObserver != null) return
        contentObserver = object : ContentObserver(null) {
            private var lastTriggerTime = 0L

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (vaultManager.isInternalEvicting()) {
                    return
                }
                val now = System.currentTimeMillis()
                if (now - lastTriggerTime < 1500L) {
                    return
                }
                lastTriggerTime = now
                onMediaChanged()
            }
        }
        try {
            appContext.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserver!!
            )
        } catch (e: Exception) {
            AppLogger.w("DeviceMediaObserver", "Failed to register content observer", e)
        }
    }

    fun unregister() {
        contentObserver?.let {
            try {
                appContext.contentResolver.unregisterContentObserver(it)
            } catch (e: Exception) {
                AppLogger.w("DeviceMediaObserver", "Failed to unregister content observer", e)
            }
            contentObserver = null
        }
    }
}
