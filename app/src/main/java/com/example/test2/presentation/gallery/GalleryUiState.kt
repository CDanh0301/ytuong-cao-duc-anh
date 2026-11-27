package com.example.test2.presentation.gallery

import com.example.test2.shared.models.ActionCardType
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Folder
import com.example.test2.shared.models.Photo
import com.example.test2.shared.models.PhotoFilter
import com.example.test2.shared.models.UserAccountTier
import com.example.test2.ui.GalleryDisplayMode

data class GalleryUiState(
    val photos: List<Photo> = emptyList(),
    val allPhotosCount: Int = 0,
    val folders: List<Folder> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accountTier: UserAccountTier = UserAccountTier(),
    val isLoading: Boolean = false,
    val pendingAiCount: Int = 0,
    val displayMode: GalleryDisplayMode = GalleryDisplayMode.BIG_PREVIEW,
    val activeFilter: PhotoFilter = PhotoFilter(),
    val searchQuery: String = "",
    val semanticMatchScores: Map<String, Float> = emptyMap(),
    val isVectorIndexing: Boolean = false,
    val viewerPhotoId: String? = null,
    val photoSettings: Photo? = null,
    val photoToDelete: Photo? = null,
    val activeActionCardType: ActionCardType = ActionCardType.ALL,
    val actionCardPhotoCounts: Map<ActionCardType, Int> = emptyMap(),
    val error: String? = null
)
