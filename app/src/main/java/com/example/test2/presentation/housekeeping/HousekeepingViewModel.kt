package com.example.test2.presentation.housekeeping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test2.di.AppContainer
import com.example.test2.shared.models.AgingSettings
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Photo
import com.example.test2.shared.models.UserAccountTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel dedicated to Housekeeping clutter cleanup, aging retention rules, and calendar reminders.
 */
class HousekeepingViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val repository = container.repository
    private val session = container.sessionState
    private val orchestrator = container.syncOrchestrator
    private val agingCleanupUseCase = container.agingCleanupUseCase

    val photos: StateFlow<List<Photo>> = repository.photos
    val categories: StateFlow<List<Category>> = repository.categories
    val accountTier: StateFlow<UserAccountTier> = repository.accountTier
    val agingSettings: StateFlow<AgingSettings> = repository.agingSettings

    private val isCleaning = MutableStateFlow(false)

    val uiState: StateFlow<HousekeepingUiState> = combine(
        session.agingMonitorInfo,
        isCleaning
    ) { info, cleaning ->
        HousekeepingUiState(
            agingMonitorInfo = info,
            isCleaning = cleaning
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HousekeepingUiState(session.agingMonitorInfo.value))

    fun getAgingScreenshots(daysOld: Int = agingSettings.value.retentionDays): List<Photo> {
        return agingCleanupUseCase.getAgingScreenshots(daysOld, System.currentTimeMillis())
    }

    fun cleanAgingScreenshots(photosToClean: List<Photo>) {
        if (photosToClean.isEmpty()) return
        viewModelScope.launch {
            isCleaning.value = true
            try {
                val info = agingCleanupUseCase.cleanAgingScreenshots(photosToClean)
                orchestrator.refreshActionCardCounts()
                session.agingMonitorInfo.value = info
            } finally {
                isCleaning.value = false
            }
        }
    }

    fun setCategoryRetentionRule(categoryId: String, retentionDays: Int) {
        agingCleanupUseCase.setCategoryRetentionRule(categoryId, retentionDays)
    }

    fun runAgingScanNow() {
        viewModelScope.launch(Dispatchers.IO) {
            session.agingMonitorInfo.value = agingCleanupUseCase.runAgingScanNow()
        }
    }

    fun markReminderCreated(photoId: String) {
        repository.markReminderCreated(photoId)
    }
}
