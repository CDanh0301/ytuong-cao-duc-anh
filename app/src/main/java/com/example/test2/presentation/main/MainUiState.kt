package com.example.test2.presentation.main

import com.example.test2.ui.AppTab

sealed interface MainNavigationEvent {
    data class OpenPhoto(val photoId: String) : MainNavigationEvent
    data object OpenHousekeeping : MainNavigationEvent
}

data class MainUiState(
    val activeTab: AppTab = AppTab.PHOTOS,
    val hasStoragePermission: Boolean = false,
    val isScanning: Boolean = false,
    val pendingAiCount: Int = 0
)
