package com.example.test2.presentation.main

import com.example.test2.ui.AppTab
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Minimal cross-feature navigation state shared between [com.example.test2.ui.PhotoAppViewModel]
 * and feature ViewModels (e.g., switching tabs on category/folder filter or opening a photo from a notification).
 */
class MainNavigationState(
    hasStoragePermissionInitial: Boolean
) {
    val activeTab = MutableStateFlow(AppTab.PHOTOS)
    val viewingPhotoId = MutableStateFlow<String?>(null)
    val hasStoragePermission = MutableStateFlow(hasStoragePermissionInitial)
}
