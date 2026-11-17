package com.example.test2.ai

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Realtime Reactive Notifier bridging Background Worker (WorkManager) and UI (ViewModel).
 * Emits the list of newly completed photo IDs so the ViewModel can immediately reflect
 * high-accuracy Gemini classifications onto the screen without requiring a manual rescan.
 */
object CloudAiSyncNotifier {

    private val _syncEvents = MutableSharedFlow<List<String>>(extraBufferCapacity = 64)
    val syncEvents = _syncEvents.asSharedFlow()

    fun notifyBatchCompleted(photoIds: List<String>) {
        if (photoIds.isNotEmpty()) {
            _syncEvents.tryEmit(photoIds)
        }
    }
}
