package com.example.test2.shared.repository

import com.example.test2.shared.models.ActionCardType
import com.example.test2.shared.models.AgingSettings
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Folder
import com.example.test2.shared.models.Photo
import com.example.test2.shared.models.PhotoFilter
import com.example.test2.shared.models.SortOption
import com.example.test2.shared.models.UserAccountTier
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain repository contract for photo, folder, category, quota, and aging state.
 */
interface IPhotoRepository {
    val photos: StateFlow<List<Photo>>
    val filteredPhotos: StateFlow<List<Photo>>
    val folders: StateFlow<List<Folder>>
    val categories: StateFlow<List<Category>>
    val accountTier: StateFlow<UserAccountTier>
    val agingSettings: StateFlow<AgingSettings>
    val currentFilter: StateFlow<PhotoFilter>

    suspend fun awaitInitialLoad()
    suspend fun loadInitialData(nowMillis: Long = 0L)

    fun updateFilter(filter: PhotoFilter)
    fun setFolderFilter(folderId: String?)
    fun setCategoryFilter(categoryId: String?)
    fun toggleFavoriteFilter()
    fun toggleScreenshotsFilter()
    fun setActionCardFilter(actionCardType: ActionCardType?, matchingPhotoIds: Set<String>?)
    fun setSearchQuery(query: String, matchingPhotoIds: Set<String>? = null, dateRange: LongRange? = null)
    fun setSortOption(sortOption: SortOption)
    fun clearFilters()

    fun toggleUserTier(isPro: Boolean)
    fun checkMonthlyQuotaReset(nowMillis: Long): Boolean
    fun canUseAiScan(nowMillis: Long = 0L): Boolean
    fun incrementQuotaUsage(nowMillis: Long = 0L): Boolean

    fun updatePhotoAiMetadata(
        photoId: String,
        isScreenshot: Boolean,
        piiScrubbedText: String?,
        detectedIntent: String?,
        suggestedEventTitle: String? = null,
        suggestedEventDateMillis: Long? = null,
        targetCategoryId: String? = null,
        isProcessed: Boolean = true,
        suggestedEventDescription: String? = null,
        semanticTags: List<String> = emptyList(),
        isCloudEnriched: Boolean? = null,
        forceUpdate: Boolean = false
    )

    fun updatePhotoAiMetadataBatch(updates: List<PhotoAiMetadataUpdate>)

    fun updatePhotoStoragePath(
        photoId: String,
        newFilePath: String,
        newFileName: String? = null,
        newFileSizeBytes: Long? = null,
        newPhotoId: String? = null
    )

    fun markReminderCreated(photoId: String)
    fun markAsProcessed(photoId: String, isProcessed: Boolean)

    fun getAgingPhotos(daysOld: Int = agingSettings.value.retentionDays, nowMillis: Long = 0L): List<Photo>
    fun setCategoryRetentionRule(categoryId: String, retentionDays: Int)

    fun batchDeletePhotos(photoIds: List<String>): List<Photo>
    fun syncDevicePhotos(scannedPhotos: List<Photo>, scannedFolders: List<Folder>)

    fun toggleFavorite(photoId: String): Boolean

    fun addPhoto(
        fileName: String,
        filePath: String,
        title: String = "",
        description: String = "",
        folderId: String? = null,
        categoryIds: Set<String> = emptySet(),
        isFavorite: Boolean = false,
        isScreenshot: Boolean = false,
        dateAdded: Long = 0L,
        fileSizeBytes: Long = 0L
    ): Photo

    fun addVaultedPhotos(newPhotos: List<Photo>)

    fun updatePhoto(
        photoId: String,
        title: String,
        description: String,
        folderId: String,
        categoryIds: Set<String>,
        isFavorite: Boolean? = null
    ): Photo?

    fun deletePhoto(photoId: String): Photo?
    fun movePhotosToFolder(photoIds: List<String>, targetFolderId: String)

    fun createFolder(name: String, colorHex: String = "#3B82F6", iconName: String = "folder", timestamp: Long = 0L): Folder
    fun renameFolder(folderId: String, newName: String, colorHex: String? = null, iconName: String? = null): Folder?
    fun deleteFolder(folderId: String): Boolean

    fun createCategory(name: String, colorHex: String = "#6366F1", iconName: String = "tag", timestamp: Long = 0L): Category?
    fun renameCategory(categoryId: String, newName: String, newColorHex: String, newIconName: String? = null): Category?
    fun deleteCategory(categoryId: String): Boolean
    fun assignPhotosToCategory(photoIds: Collection<String>, categoryId: String): Int
    fun reassignPhotosToCategory(photoIds: Collection<String>, newCategoryId: String): Int
}
