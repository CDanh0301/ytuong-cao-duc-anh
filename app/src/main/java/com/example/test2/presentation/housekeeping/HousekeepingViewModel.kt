package com.example.test2.presentation.housekeeping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test2.ai.ScreenshotSyncOrchestrator
import com.example.test2.shared.models.AgingSettings
import com.example.test2.shared.models.Photo
import com.example.test2.shared.repository.IPhotoRepository
import com.example.test2.shared.usecases.MetadataSyncPort
import com.example.test2.shared.usecases.housekeeping.AgingCleanupUseCase
import com.example.test2.shared.usecases.housekeeping.MarkReminderCreatedUseCase
import com.example.test2.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Pure MVVM ViewModel dedicated to Housekeeping clutter cleanup, aging retention rules,
 * and calendar reminders. Owns its feature UI state and derives aging photos reactively.
 */
class HousekeepingViewModel(
    private val repository: IPhotoRepository,
    private val orchestrator: ScreenshotSyncOrchestrator,
    private val agingCleanupUseCase: AgingCleanupUseCase,
    private val markReminderCreatedUseCase: MarkReminderCreatedUseCase,
    private val metadataSync: MetadataSyncPort
) : ViewModel() {

    val agingSettings: StateFlow<AgingSettings> = repository.agingSettings

    private val clockRefreshMillis = MutableStateFlow(System.currentTimeMillis())
    private val agingMonitorInfo = MutableStateFlow(agingCleanupUseCase.getLastCheckedInfo())
    private val isEventsExpanded = MutableStateFlow(false)
    private val isClutterExpanded = MutableStateFlow(false)
    private val isCleaning = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    private val repoFlow = combine(
        repository.photos,
        repository.categories,
        repository.accountTier,
        repository.agingSettings,
        clockRefreshMillis
    ) { photos, categories, tier, settings, nowMillis ->
        val effectiveDefaultDays = settings.effectiveDefaultRetentionDays(tier.isPro)
        HousekeepingUiState(
            photos = photos,
            categories = categories,
            accountTier = tier,
            agingSettings = settings.copy(retentionDays = effectiveDefaultDays),
            agingPhotos = agingCleanupUseCase.getAgingScreenshots(effectiveDefaultDays, nowMillis)
        )
    }

    private val sectionExpansionFlow = combine(
        isEventsExpanded,
        isClutterExpanded
    ) { eventsExpanded, clutterExpanded ->
        eventsExpanded to clutterExpanded
    }

    val uiState: StateFlow<HousekeepingUiState> = combine(
        repoFlow,
        agingMonitorInfo,
        sectionExpansionFlow,
        isCleaning,
        errorMessage
    ) { base, info, expansion, cleaning, error ->
        base.copy(
            agingMonitorInfo = info,
            isEventsExpanded = expansion.first,
            isClutterExpanded = expansion.second,
            isCleaning = cleaning,
            error = error
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        HousekeepingUiState(agingMonitorInfo = agingMonitorInfo.value)
    )

    fun toggleEventsExpanded() {
        isEventsExpanded.value = !isEventsExpanded.value
    }

    fun toggleClutterExpanded() {
        isClutterExpanded.value = !isClutterExpanded.value
    }

    fun dismissError() {
        errorMessage.value = null
    }

    fun getAgingScreenshots(
        daysOld: Int = agingSettings.value.effectiveDefaultRetentionDays(repository.accountTier.value.isPro)
    ): List<Photo> {
        return agingCleanupUseCase.getAgingScreenshots(daysOld, System.currentTimeMillis())
    }

    fun refreshAgingScreenshots(runDailyCatchUp: Boolean = false) {
        clockRefreshMillis.value = System.currentTimeMillis()
        agingMonitorInfo.value = agingCleanupUseCase.getLastCheckedInfo()
        if (runDailyCatchUp) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val catchUpInfo = agingCleanupUseCase.runCatchUpScanIfDue()
                    if (catchUpInfo != null) {
                        agingMonitorInfo.value = catchUpInfo
                        clockRefreshMillis.value = System.currentTimeMillis()
                    }
                } catch (e: Exception) {
                    AppLogger.w("HousekeepingViewModel", "Catch-up daily aging scan failed", e)
                }
            }
        }
    }

    fun cleanAgingScreenshots(photosToClean: List<Photo>) {
        if (photosToClean.isEmpty()) return
        viewModelScope.launch {
            isCleaning.value = true
            try {
                val info = agingCleanupUseCase.cleanAgingScreenshots(photosToClean)
                metadataSync.markMetadataDirty()
                orchestrator.refreshActionCardCounts()
                agingMonitorInfo.value = info
                clockRefreshMillis.value = System.currentTimeMillis()
            } catch (e: Exception) {
                AppLogger.e("HousekeepingViewModel", "Failed cleaning aging screenshots", e)
                errorMessage.value = "Không thể dọn dẹp ảnh cũ"
            } finally {
                isCleaning.value = false
            }
        }
    }

    fun setDefaultRetentionDays(retentionDays: Int) {
        agingCleanupUseCase.setDefaultRetentionDays(retentionDays)
        metadataSync.markMetadataDirty()
    }

    fun setCategoryRetentionRule(categoryId: String, retentionDays: Int) {
        agingCleanupUseCase.setCategoryRetentionRule(categoryId, retentionDays)
        metadataSync.markMetadataDirty()
    }

    fun runAgingScanNow() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                agingMonitorInfo.value = agingCleanupUseCase.runAgingScanNow()
                clockRefreshMillis.value = System.currentTimeMillis()
            } catch (e: Exception) {
                AppLogger.e("HousekeepingViewModel", "Failed running aging scan", e)
                errorMessage.value = "Không thể quét kiểm tra ảnh cũ"
            }
        }
    }

    fun markReminderCreated(photoId: String) {
        markReminderCreatedUseCase(photoId)
        metadataSync.markMetadataDirty()
    }
}
