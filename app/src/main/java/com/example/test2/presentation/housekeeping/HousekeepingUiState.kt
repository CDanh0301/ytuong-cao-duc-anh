package com.example.test2.presentation.housekeeping

data class HousekeepingUiState(
    val agingMonitorInfo: Triple<Long, Int, Long> = Triple(0L, 0, 0L),
    val isCleaning: Boolean = false
)
