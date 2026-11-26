package com.example.test2.shared.repository

import com.example.test2.shared.models.ActionCardType
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Folder
import com.example.test2.shared.models.Photo
import com.example.test2.shared.models.PhotoFilter
import com.example.test2.shared.models.SortOption
import com.example.test2.shared.utils.VietnameseUtils

/**
 * Pure filtering and sorting engine extracted from PhotoRepository to keep repository data management
 * decoupled from UI filtering/search presentation rules.
 */
object PhotoFilterEngine {

    fun filterAndSort(
        photoList: List<Photo>,
        filter: PhotoFilter,
        categoriesList: List<Category>,
        foldersList: List<Folder>,
        chronologicalDescComparator: Comparator<Photo>,
        chronologicalAscComparator: Comparator<Photo>
    ): List<Photo> {
        var result = photoList

        if (filter.onlyFavorites) {
            result = result.filter { it.isFavorite }
        }

        if (filter.onlyScreenshots) {
            result = result.filter { it.isScreenshot }
        }

        if (!filter.folderId.isNullOrBlank()) {
            result = result.filter { it.folderId == filter.folderId }
        }

        if (!filter.categoryId.isNullOrBlank()) {
            result = result.filter { filter.categoryId in it.categoryIds }
        }

        if (filter.dateRange != null) {
            result = result.filter { it.dateAdded in filter.dateRange }
        }

        if (filter.actionCardType != null && filter.actionCardType != ActionCardType.ALL && filter.matchingPhotoIds != null && filter.searchQuery.isBlank()) {
            result = result.filter { it.id in filter.matchingPhotoIds }
        }

        if (filter.searchQuery.isNotBlank()) {
            val query = filter.searchQuery.trim().lowercase()
            val unaccentedQuery = VietnameseUtils.removeDiacritics(query)
            val ftsIds = filter.matchingPhotoIds
            val catMap = categoriesList.associate { it.id to it.name.lowercase() }
            val folderMap = foldersList.associate { it.id to it.name.lowercase() }

            result = result.filter { photo ->
                if (ftsIds != null && photo.id in ftsIds) return@filter true

                val title = photo.title.lowercase()
                val desc = photo.description.lowercase()
                val fileName = photo.fileName.lowercase()
                val eventTitle = photo.suggestedEventTitle?.lowercase() ?: ""
                val eventDesc = photo.suggestedEventDescription?.lowercase() ?: ""
                val tags = photo.semanticTags.joinToString(" ").lowercase()
                val ocrText = photo.piiScrubbedText?.lowercase() ?: ""
                val categoryNames = photo.categoryIds.mapNotNull { catMap[it] }.joinToString(" ")
                val folderName = folderMap[photo.folderId] ?: ""

                title.contains(query) ||
                    desc.contains(query) ||
                    fileName.contains(query) ||
                    eventTitle.contains(query) ||
                    eventDesc.contains(query) ||
                    tags.contains(query) ||
                    ocrText.contains(query) ||
                    categoryNames.contains(query) ||
                    folderName.contains(query) ||
                    VietnameseUtils.removeDiacritics(title).contains(unaccentedQuery) ||
                    VietnameseUtils.removeDiacritics(desc).contains(unaccentedQuery) ||
                    VietnameseUtils.removeDiacritics(fileName).contains(unaccentedQuery) ||
                    VietnameseUtils.removeDiacritics(eventTitle).contains(unaccentedQuery) ||
                    VietnameseUtils.removeDiacritics(eventDesc).contains(unaccentedQuery) ||
                    VietnameseUtils.removeDiacritics(tags).contains(unaccentedQuery) ||
                    VietnameseUtils.removeDiacritics(ocrText).contains(unaccentedQuery) ||
                    VietnameseUtils.removeDiacritics(categoryNames).contains(unaccentedQuery) ||
                    VietnameseUtils.removeDiacritics(folderName).contains(unaccentedQuery)
            }
        }

        return when (filter.sortOption) {
            SortOption.DATE_DESC -> {
                if (filter.searchQuery.isNotBlank() && filter.matchingPhotoIds != null && filter.matchingPhotoIds.isNotEmpty()) {
                    val idOrder = filter.matchingPhotoIds.toList()
                    val orderMap = idOrder.withIndex().associate { it.value to it.index }
                    result.sortedBy { orderMap[it.id] ?: Int.MAX_VALUE }
                } else {
                    result.sortedWith(chronologicalDescComparator)
                }
            }
            SortOption.DATE_ASC -> result.sortedWith(chronologicalAscComparator)
            SortOption.TITLE_ASC -> result.sortedWith(compareBy<Photo> { it.title.lowercase() }.then(chronologicalDescComparator))
            SortOption.TITLE_DESC -> result.sortedWith(compareByDescending<Photo> { it.title.lowercase() }.then(chronologicalDescComparator))
        }
    }
}
