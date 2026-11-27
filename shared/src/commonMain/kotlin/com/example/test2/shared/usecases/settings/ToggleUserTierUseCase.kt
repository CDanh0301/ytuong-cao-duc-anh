package com.example.test2.shared.usecases.settings

import com.example.test2.shared.repository.IPhotoRepository
import com.example.test2.shared.usecases.WidgetPort

/**
 * Use case for toggling Free/Pro user tier and synchronizing widget state.
 */
class ToggleUserTierUseCase(
    private val repository: IPhotoRepository,
    private val widgetPort: WidgetPort
) {
    operator fun invoke(isPro: Boolean): String {
        repository.toggleUserTier(isPro)
        widgetPort.cacheIsProAndUpdate(isPro)
        return repository.currentFilter.value.searchQuery
    }
}
