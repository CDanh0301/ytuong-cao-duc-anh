package com.example.test2.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class Photo(
    val id: String,
    val fileName: String,
    val filePath: String, // local file path or content URI
    val title: String,
    val description: String = "",
    val folderId: String,
    val categoryIds: Set<String> = emptySet(),
    val isFavorite: Boolean = false,
    val dateAdded: Long,
    val fileSizeBytes: Long = 0L,
    // Screenshot Intelligence & Processing metadata (IDEA.md)
    val isScreenshot: Boolean = false,
    val isProcessed: Boolean = false,
    val isCloudEnriched: Boolean = false,
    val piiScrubbedText: String? = null,
    val detectedIntent: String? = null, // "EVENT", "TASK", "SHOPPING", "READING"
    val suggestedEventTitle: String? = null,
    val suggestedEventDateMillis: Long? = null,
    val suggestedEventDescription: String? = null,
    val semanticTags: List<String> = emptyList(),
    val hasReminderCreated: Boolean = false
)

@Serializable
data class Folder(
    val id: String,
    val name: String,
    val colorHex: String = "#3B82F6",
    val iconName: String = "folder",
    val isDefault: Boolean = false,
    val createdAt: Long
)

@Serializable
data class Category(
    val id: String,
    val name: String,
    val colorHex: String = "#6366F1",
    val iconName: String = "tag",
    val createdAt: Long = 0L
)

@Serializable
enum class ActionCardType {
    ALL,
    ADDRESS,
    PAYMENT,
    CONTACT,
    EVENT
}

@Serializable
data class ActionCard(
    val id: String,
    val photoId: String,
    val type: ActionCardType,
    val title: String,
    val subtitle: String? = null,
    val payloadJson: String = "",
    val createdAt: Long = 0L
)

enum class SortOption {
    DATE_DESC,
    DATE_ASC,
    TITLE_ASC,
    TITLE_DESC
}

data class PhotoFilter(
    val folderId: String? = null,
    val categoryId: String? = null,
    val searchQuery: String = "",
    val onlyFavorites: Boolean = false,
    val onlyScreenshots: Boolean = false,
    val sortOption: SortOption = SortOption.DATE_DESC,
    val matchingPhotoIds: Set<String>? = null,
    val dateRange: LongRange? = null,
    val actionCardType: ActionCardType? = null
)

@Serializable
data class UserAccountTier(
    val isPro: Boolean = false,
    val aiScansUsedThisMonth: Int = 0,
    val aiScansQuota: Int = 50,
    val lastQuotaResetMillis: Long = 0L
)

@Serializable
data class AgingSettings(
    val retentionDays: Int = 30,
    val autoArchiveProcessed: Boolean = true,
    val categoryRetentionDays: Map<String, Int> = emptyMap()
)

@Serializable
data class AppData(
    val photos: List<Photo> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accountTier: UserAccountTier = UserAccountTier(),
    val agingSettings: AgingSettings = AgingSettings()
)
