package com.example.test2.ui

import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Photo

data class RetroactiveCategoryPrompt(
    val category: Category,
    val matchingPhotoIds: List<String>,
    val previewPhotos: List<Photo> = emptyList()
)

data class RestoreProgress(
    val current: Int = 0,
    val total: Int = 0
) {
    val first: Int get() = current
    val second: Int get() = total
}

data class AgingMonitorInfo(
    val lastCheckedMillis: Long = 0L,
    val clutterCount: Int = 0,
    val clutterBytes: Long = 0L
) {
    val first: Long get() = lastCheckedMillis
    val second: Int get() = clutterCount
    val third: Long get() = clutterBytes
}
