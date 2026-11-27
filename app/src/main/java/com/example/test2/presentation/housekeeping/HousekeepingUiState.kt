package com.example.test2.presentation.housekeeping

import com.example.test2.shared.models.AgingSettings
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Photo
import com.example.test2.shared.models.UserAccountTier

data class HousekeepingUiState(
    val photos: List<Photo> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accountTier: UserAccountTier = UserAccountTier(),
    val agingSettings: AgingSettings = AgingSettings(),
    val agingPhotos: List<Photo> = emptyList(),
    val agingMonitorInfo: Triple<Long, Int, Long> = Triple(0L, 0, 0L),
    val isEventsExpanded: Boolean = false,
    val isClutterExpanded: Boolean = false,
    val isCleaning: Boolean = false,
    val error: String? = null
)
