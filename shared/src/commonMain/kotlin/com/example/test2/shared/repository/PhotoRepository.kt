package com.example.test2.shared.repository

import com.example.test2.shared.models.ActionCardType
import com.example.test2.shared.models.AgingSettings
import com.example.test2.shared.models.AppData
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Folder
import com.example.test2.shared.models.Photo
import com.example.test2.shared.models.PhotoFilter
import com.example.test2.shared.models.SortOption
import com.example.test2.shared.models.UserAccountTier
import com.example.test2.shared.utils.VietnameseUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

interface PersistenceDriver {
    suspend fun loadData(): String?
    suspend fun saveData(json: String)
}

class InMemoryPersistenceDriver(private var initialJson: String? = null) : PersistenceDriver {
    override suspend fun loadData(): String? = initialJson
    override suspend fun saveData(json: String) {
        initialJson = json
    }
}

data class PhotoAiMetadataUpdate(
    val photoId: String,
    val isScreenshot: Boolean = true,
    val piiScrubbedText: String? = null,
    val detectedIntent: String? = null,
    val suggestedEventTitle: String? = null,
    val suggestedEventDateMillis: Long? = null,
    val targetCategoryId: String? = null,
    val isProcessed: Boolean = true,
    val suggestedEventDescription: String? = null,
    val semanticTags: List<String> = emptyList(),
    val newFilePath: String? = null,
    val newFileName: String? = null,
    val newFileSizeBytes: Long? = null,
    val isCloudEnriched: Boolean? = null
)

class PhotoRepository(
    private val persistenceDriver: PersistenceDriver = InMemoryPersistenceDriver(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos: StateFlow<List<Photo>> = _photos.asStateFlow()

    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _accountTier = MutableStateFlow(UserAccountTier())
    val accountTier: StateFlow<UserAccountTier> = _accountTier.asStateFlow()

    private val _agingSettings = MutableStateFlow(AgingSettings())
    val agingSettings: StateFlow<AgingSettings> = _agingSettings.asStateFlow()

    private val _currentFilter = MutableStateFlow(PhotoFilter())
    val currentFilter: StateFlow<PhotoFilter> = _currentFilter.asStateFlow()

    val filteredPhotos: StateFlow<List<Photo>> = combine(
        _photos,
        _currentFilter,
        _categories,
        _folders
    ) { photoList, filter, categoriesList, foldersList ->
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

                // 1. Direct contains check
                title.contains(query) ||
                desc.contains(query) ||
                fileName.contains(query) ||
                eventTitle.contains(query) ||
                eventDesc.contains(query) ||
                tags.contains(query) ||
                ocrText.contains(query) ||
                categoryNames.contains(query) ||
                folderName.contains(query) ||
                // 2. Vietnamese unaccented matching (e.g. "nui" matches "núi", "coc" matches "cốc")
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

        when (filter.sortOption) {
            SortOption.DATE_DESC -> {
                if (filter.searchQuery.isNotBlank() && filter.matchingPhotoIds != null && filter.matchingPhotoIds.isNotEmpty()) {
                    val idOrder = filter.matchingPhotoIds.toList()
                    val orderMap = idOrder.withIndex().associate { it.value to it.index }
                    result.sortedBy { orderMap[it.id] ?: Int.MAX_VALUE }
                } else {
                    result.sortedByDescending { it.dateAdded }
                }
            }
            SortOption.DATE_ASC -> result.sortedBy { it.dateAdded }
            SortOption.TITLE_ASC -> result.sortedBy { it.title.lowercase() }
            SortOption.TITLE_DESC -> result.sortedByDescending { it.title.lowercase() }
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scope.launch {
            loadInitialData()
        }
    }

    suspend fun loadInitialData(nowMillis: Long = 0L) {
        val rawData = persistenceDriver.loadData()
        if (!rawData.isNullOrBlank()) {
            try {
                val appData = json.decodeFromString<AppData>(rawData)
                _photos.value = appData.photos.map { photo ->
                    val migratedCats = photo.categoryIds.map { if (it == "cat_tasks") "cat_reading" else it }.toSet()
                    val migratedIntent = if (photo.detectedIntent == "TASK") "READING" else photo.detectedIntent
                    photo.copy(categoryIds = migratedCats, detectedIntent = migratedIntent)
                }

                val legacyFolderNames = mapOf(
                    "folder_camera" to "Cuộn camera",
                    "folder_screenshots" to "Ảnh chụp màn hình",
                    "folder_favorites" to "Yêu thích",
                    "folder_travel" to "Du lịch",
                    "folder_work" to "Công việc & Tài liệu"
                )
                _folders.value = appData.folders.map { folder ->
                    if (folder.id in legacyFolderNames && folder.name in listOf("Camera Roll", "Screenshots", "Favorites", "Travel", "Work & Docs")) {
                        folder.copy(name = legacyFolderNames[folder.id] ?: folder.name)
                    } else {
                        folder
                    }
                }.ifEmpty { defaultFoldersList() }

                val legacyCatNames = mapOf(
                    "cat_events" to "Lịch & Sự kiện",
                    "cat_tobuy" to "Cần mua & Chi tiêu",
                    "cat_reading" to "Đọc & Tham khảo",
                    "cat_nature" to "Thiên nhiên & Phong cảnh",
                    "cat_personal" to "Cá nhân & Giấy tờ"
                )
                val loadedCategories = appData.categories.map { cat ->
                    if (cat.id in legacyCatNames && cat.name in listOf("Events & Schedule", "To-Buy", "Tasks & To-Do", "Reading List", "Nature", "Personal")) {
                        cat.copy(name = legacyCatNames[cat.id] ?: cat.name)
                    } else {
                        cat
                    }
                }.filterNot { it.id == "cat_tasks" }.toMutableList()

                val existingIds = loadedCategories.map { it.id }.toSet()
                for (defaultCat in defaultCategoriesList()) {
                    if (defaultCat.id !in existingIds) {
                        loadedCategories.add(defaultCat)
                    }
                }
                _categories.value = loadedCategories
                _accountTier.value = appData.accountTier
                _agingSettings.value = appData.agingSettings
                if (nowMillis > 0L) {
                    checkMonthlyQuotaReset(nowMillis)
                }
            } catch (_: Exception) {
                seedDefaults(nowMillis)
            }
        } else {
            seedDefaults(nowMillis)
        }
    }

    private fun defaultFoldersList(): List<Folder> {
        return listOf(
            Folder(id = "folder_camera", name = "Cuộn camera", colorHex = "#3B82F6", iconName = "camera", isDefault = true, createdAt = 1000L),
            Folder(id = "folder_screenshots", name = "Ảnh chụp màn hình", colorHex = "#6366F1", iconName = "folder", isDefault = false, createdAt = 1500L),
            Folder(id = "folder_favorites", name = "Yêu thích", colorHex = "#EF4444", iconName = "heart", isDefault = false, createdAt = 2000L),
            Folder(id = "folder_travel", name = "Du lịch", colorHex = "#10B981", iconName = "plane", isDefault = false, createdAt = 3000L),
            Folder(id = "folder_work", name = "Công việc & Tài liệu", colorHex = "#8B5CF6", iconName = "briefcase", isDefault = false, createdAt = 4000L)
        )
    }

    private fun defaultCategoriesList(): List<Category> {
        return listOf(
            Category(id = "cat_events", name = "Lịch & Sự kiện", colorHex = "#3B82F6", iconName = "plane", createdAt = 1000L),
            Category(id = "cat_tobuy", name = "Cần mua & Chi tiêu", colorHex = "#F59E0B", iconName = "utensils", createdAt = 2000L),
            Category(id = "cat_reading", name = "Đọc & Tham khảo", colorHex = "#8B5CF6", iconName = "tag", createdAt = 3000L),
            Category(id = "cat_nature", name = "Thiên nhiên & Phong cảnh", colorHex = "#009688", iconName = "leaf", createdAt = 4000L),
            Category(id = "cat_personal", name = "Cá nhân & Giấy tờ", colorHex = "#EC4899", iconName = "user", createdAt = 5000L)
        )
    }

    private fun seedDefaults(nowMillis: Long = 0L) {
        _folders.value = defaultFoldersList()
        _categories.value = defaultCategoriesList()
        _photos.value = emptyList()
        _accountTier.value = UserAccountTier(lastQuotaResetMillis = nowMillis)
        _agingSettings.value = AgingSettings()
        persistData()
    }


    private fun persistData() {
        scope.launch {
            val appData = AppData(
                photos = _photos.value,
                folders = _folders.value,
                categories = _categories.value,
                accountTier = _accountTier.value,
                agingSettings = _agingSettings.value
            )
            try {
                val encoded = json.encodeToString(AppData.serializer(), appData)
                persistenceDriver.saveData(encoded)
            } catch (_: Exception) {
            }
        }
    }

    // Filter controls
    fun updateFilter(filter: PhotoFilter) {
        _currentFilter.value = filter
    }

    fun setFolderFilter(folderId: String?) {
        _currentFilter.value = _currentFilter.value.copy(folderId = folderId)
    }

    fun setCategoryFilter(categoryId: String?) {
        _currentFilter.value = _currentFilter.value.copy(categoryId = categoryId)
    }

    fun toggleFavoriteFilter() {
        _currentFilter.value = _currentFilter.value.copy(onlyFavorites = !_currentFilter.value.onlyFavorites)
    }

    fun toggleScreenshotsFilter() {
        _currentFilter.value = _currentFilter.value.copy(onlyScreenshots = !_currentFilter.value.onlyScreenshots)
    }

    fun setActionCardFilter(actionCardType: ActionCardType?, matchingPhotoIds: Set<String>?) {
        _currentFilter.value = _currentFilter.value.copy(
            actionCardType = actionCardType,
            matchingPhotoIds = if (actionCardType == null || actionCardType == ActionCardType.ALL) null else matchingPhotoIds
        )
    }

    fun setSearchQuery(query: String, matchingPhotoIds: Set<String>? = null, dateRange: LongRange? = null) {
        _currentFilter.value = _currentFilter.value.copy(
            searchQuery = query,
            matchingPhotoIds = matchingPhotoIds,
            dateRange = dateRange
        )
    }

    fun setSortOption(sortOption: SortOption) {
        _currentFilter.value = _currentFilter.value.copy(sortOption = sortOption)
    }

    fun clearFilters() {
        _currentFilter.value = PhotoFilter()
    }

    // Quota & Tier management (IDEA.md)
    fun toggleUserTier(isPro: Boolean) {
        _accountTier.value = _accountTier.value.copy(isPro = isPro)
        persistData()
    }


    /**
     * Monthly Quota Auto-Reset (IDEA.md Step 4 & Part 3).
     * Automatically resets aiScansUsedThisMonth to 0 when entering a new calendar month
     * or when 30 days have elapsed since last reset.
     */
    fun checkMonthlyQuotaReset(nowMillis: Long): Boolean {
        if (nowMillis <= 0L) return false
        val tier = _accountTier.value
        if (tier.lastQuotaResetMillis <= 0L) {
            _accountTier.value = tier.copy(lastQuotaResetMillis = nowMillis)
            persistData()
            return false
        }

        val (lastYear, lastMonth) = epochMillisToYearMonth(tier.lastQuotaResetMillis)
        val (curYear, curMonth) = epochMillisToYearMonth(nowMillis)

        val isNewMonth = curYear > lastYear || (curYear == lastYear && curMonth > lastMonth)
        val isThirtyDaysElapsed = (nowMillis - tier.lastQuotaResetMillis) >= 30L * 86_400_000L

        if (isNewMonth || isThirtyDaysElapsed) {
            _accountTier.value = tier.copy(
                aiScansUsedThisMonth = 0,
                lastQuotaResetMillis = nowMillis
            )
            persistData()
            return true
        }
        return false
    }

    private fun epochMillisToYearMonth(epochMillis: Long): Pair<Int, Int> {
        val days = (epochMillis / 86400000L).toInt() + 719468
        val era = if (days >= 0) days / 146097 else (days - 146096) / 146097
        val doe = days - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val m = mp + (if (mp < 10) 3 else -9)
        val year = y + (if (m <= 2) 1 else 0)
        return Pair(year, m)
    }

    fun canUseAiScan(nowMillis: Long = 0L): Boolean {
        if (nowMillis > 0L) {
            checkMonthlyQuotaReset(nowMillis)
        }
        val tier = _accountTier.value
        return tier.isPro || tier.aiScansUsedThisMonth < tier.aiScansQuota
    }

    fun incrementQuotaUsage(nowMillis: Long = 0L): Boolean {
        if (nowMillis > 0L) {
            checkMonthlyQuotaReset(nowMillis)
        }
        val tier = _accountTier.value
        if (!tier.isPro && tier.aiScansUsedThisMonth >= tier.aiScansQuota) {
            return false
        }
        _accountTier.value = tier.copy(aiScansUsedThisMonth = tier.aiScansUsedThisMonth + 1)
        persistData()
        return true
    }

    private fun isCrudeTitle(title: String?): Boolean {
        if (title.isNullOrBlank()) return true
        val lower = title.lowercase()
        return lower.startsWith("screenshot_") ||
                lower.startsWith("img_") ||
                lower.startsWith("pxl_") ||
                lower.startsWith("screen_shot_")
    }

    // Update AI & Screenshot intelligence metadata
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
        isCloudEnriched: Boolean? = null
    ) {
        updatePhotoAiMetadataBatch(
            listOf(
                PhotoAiMetadataUpdate(
                    photoId = photoId,
                    isScreenshot = isScreenshot,
                    piiScrubbedText = piiScrubbedText,
                    detectedIntent = detectedIntent,
                    suggestedEventTitle = suggestedEventTitle,
                    suggestedEventDateMillis = suggestedEventDateMillis,
                    targetCategoryId = targetCategoryId,
                    isProcessed = isProcessed,
                    suggestedEventDescription = suggestedEventDescription,
                    semanticTags = semanticTags,
                    isCloudEnriched = isCloudEnriched
                )
            )
        )
    }

    fun updatePhotoAiMetadataBatch(updates: List<PhotoAiMetadataUpdate>) {
        if (updates.isEmpty()) return
        val updateMap = updates.associateBy { it.photoId }
        val defaultCatIds = setOf("cat_events", "cat_tobuy", "cat_reading", "cat_nature", "cat_personal")

        _photos.value = _photos.value.map { photo ->
            val update = updateMap[photo.id]
            if (update != null) {
                val isAlreadyCloudEnriched = photo.isCloudEnriched
                val willBeCloudEnriched = update.isCloudEnriched ?: (isAlreadyCloudEnriched || !update.suggestedEventDescription.isNullOrBlank())

                val resolvedTitle = if (isAlreadyCloudEnriched && (update.suggestedEventTitle.isNullOrBlank() || isCrudeTitle(update.suggestedEventTitle))) {
                    photo.suggestedEventTitle
                } else {
                    update.suggestedEventTitle ?: photo.suggestedEventTitle
                }

                val resolvedDescription = if (isAlreadyCloudEnriched && update.suggestedEventDescription.isNullOrBlank()) {
                    photo.suggestedEventDescription
                } else {
                    update.suggestedEventDescription ?: photo.suggestedEventDescription
                }

                val resolvedIntent = if (isAlreadyCloudEnriched && (update.detectedIntent.isNullOrBlank() || update.detectedIntent == "OTHER")) {
                    photo.detectedIntent
                } else {
                    update.detectedIntent ?: photo.detectedIntent
                }

                val updatedCategories = if (update.targetCategoryId != null && (!isAlreadyCloudEnriched || update.targetCategoryId != "cat_other")) {
                    (photo.categoryIds.filterNot { it in defaultCatIds } + update.targetCategoryId).toSet()
                } else {
                    photo.categoryIds
                }

                val mergedTags = if (update.semanticTags.isNotEmpty()) {
                    (photo.semanticTags + update.semanticTags).distinct()
                } else {
                    photo.semanticTags
                }

                photo.copy(
                    filePath = update.newFilePath ?: photo.filePath,
                    fileName = update.newFileName ?: photo.fileName,
                    fileSizeBytes = update.newFileSizeBytes ?: photo.fileSizeBytes,
                    isScreenshot = update.isScreenshot,
                    isCloudEnriched = willBeCloudEnriched,
                    piiScrubbedText = if (isAlreadyCloudEnriched && !photo.piiScrubbedText.isNullOrBlank() && update.piiScrubbedText.isNullOrBlank()) {
                        photo.piiScrubbedText
                    } else {
                        update.piiScrubbedText ?: photo.piiScrubbedText
                    },
                    detectedIntent = resolvedIntent,
                    suggestedEventTitle = resolvedTitle,
                    suggestedEventDateMillis = update.suggestedEventDateMillis ?: photo.suggestedEventDateMillis,
                    suggestedEventDescription = resolvedDescription,
                    semanticTags = mergedTags,
                    categoryIds = updatedCategories,
                    isProcessed = update.isProcessed || photo.isProcessed
                )
            } else {
                photo
            }
        }
        persistData()
    }

    fun updatePhotoStoragePath(
        photoId: String,
        newFilePath: String,
        newFileName: String? = null,
        newFileSizeBytes: Long? = null,
        newPhotoId: String? = null
    ) {
        _photos.value = _photos.value.map { photo ->
            if (photo.id == photoId) {
                photo.copy(
                    id = newPhotoId ?: photo.id,
                    filePath = newFilePath,
                    fileName = newFileName ?: photo.fileName,
                    fileSizeBytes = newFileSizeBytes ?: photo.fileSizeBytes
                )
            } else {
                photo
            }
        }
        persistData()
    }


    fun markReminderCreated(photoId: String) {
        _photos.value = _photos.value.map { photo ->
            if (photo.id == photoId) {
                photo.copy(hasReminderCreated = true, isProcessed = true)
            } else {
                photo
            }
        }
        persistData()
    }


    fun markAsProcessed(photoId: String, isProcessed: Boolean) {
        _photos.value = _photos.value.map { photo ->
            if (photo.id == photoId) {
                photo.copy(isProcessed = isProcessed)
            } else {
                photo
            }
        }
        persistData()
    }


    // Aging Housekeeping: find screenshots older than X days (default 30 days)
    // Pro Tier allows Custom Aging Rules per category (IDEA.md Part 3)
    fun getAgingPhotos(daysOld: Int = _agingSettings.value.retentionDays, nowMillis: Long = 0L): List<Photo> {
        if (daysOld <= 0 || nowMillis <= 0L) return emptyList()
        val customRules = _agingSettings.value.categoryRetentionDays
        val isPro = _accountTier.value.isPro

        return _photos.value.filter { photo ->
            if (!photo.isScreenshot) return@filter false
            val isAutoArchived = _agingSettings.value.autoArchiveProcessed &&
                photo.isProcessed &&
                photo.suggestedEventDateMillis != null &&
                photo.suggestedEventDateMillis < nowMillis

            val effectiveDays = if (isPro && customRules.isNotEmpty()) {
                photo.categoryIds.firstNotNullOfOrNull { customRules[it] } ?: daysOld
            } else {
                daysOld
            }
            val cutoff = nowMillis - (effectiveDays * 24L * 60L * 60L * 1000L)
            photo.dateAdded < cutoff || isAutoArchived
        }
    }

    fun setCategoryRetentionRule(categoryId: String, retentionDays: Int) {
        val updatedMap = _agingSettings.value.categoryRetentionDays.toMutableMap()
        if (retentionDays > 0) {
            updatedMap[categoryId] = retentionDays
        } else {
            updatedMap.remove(categoryId)
        }
        _agingSettings.value = _agingSettings.value.copy(categoryRetentionDays = updatedMap)
        persistData()
    }


    fun batchDeletePhotos(photoIds: List<String>): List<Photo> {
        val idSet = photoIds.toSet()
        val deleted = _photos.value.filter { it.id in idSet }
        _photos.value = _photos.value.filterNot { it.id in idSet }
        persistData()
        return deleted
    }

    // Sync device scanned photos and folders
    fun syncDevicePhotos(scannedPhotos: List<Photo>, scannedFolders: List<Folder>) {
        val existingPhotoMap = _photos.value.associateBy { it.filePath }
        val existingById = _photos.value.associateBy { it.id }
        val existingFolderMap = _folders.value.associateBy { it.name.lowercase() }

        val newFolders = _folders.value.toMutableList()
        for (scannedFolder in scannedFolders) {
            if (!existingFolderMap.containsKey(scannedFolder.name.lowercase())) {
                newFolders.add(scannedFolder)
            }
        }
        _folders.value = newFolders

        val folderByName = newFolders.associateBy { it.name.lowercase() }
        val defaultFolderId = newFolders.firstOrNull { it.id == "folder_screenshots" || it.name.contains("màn hình", ignoreCase = true) || it.name.contains("screenshot", ignoreCase = true) }?.id
            ?: newFolders.firstOrNull { it.isDefault }?.id
            ?: newFolders.firstOrNull()?.id
            ?: "folder_screenshots"

        val mergedPhotos = mutableListOf<Photo>()
        val processedPaths = mutableSetOf<String>()
        val processedIds = mutableSetOf<String>()

        for (scanned in scannedPhotos) {
            // First check if this exact photo ID is already vaulted in private app storage (.enc)
            val existingSameId = existingById[scanned.id]
            if (existingSameId != null && existingSameId.filePath.endsWith(".enc")) {
                // Preserve the vaulted/protected version - do NOT let MediaStore content:// overwrite it!
                mergedPhotos.add(existingSameId)
                processedPaths.add(existingSameId.filePath)
                processedIds.add(scanned.id)
                continue
            }

            val existing = existingPhotoMap[scanned.filePath] ?: existingSameId
            if (existing != null) {
                val updatedExisting = if (!existing.isScreenshot && scanned.isScreenshot) {
                    existing.copy(isScreenshot = true)
                } else {
                    existing
                }
                mergedPhotos.add(updatedExisting)
                processedPaths.add(existing.filePath)
            } else {
                val targetFolderId = folderByName[scanned.folderId.lowercase()]?.id ?: defaultFolderId
                mergedPhotos.add(scanned.copy(folderId = targetFolderId))
                processedPaths.add(scanned.filePath)
            }
            processedIds.add(scanned.id)
        }

        // Preserve manual user imports and private encrypted vault photos while cleaning up obsolete legacy device camera photos
        for (existing in _photos.value) {
            if (existing.filePath !in processedPaths && existing.id !in processedIds) {
                val isVaultedOrManual = existing.filePath.endsWith(".enc") ||
                        !existing.filePath.startsWith("content://") ||
                        !existing.id.startsWith("device_")
                if (isVaultedOrManual) {
                    mergedPhotos.add(existing)
                    processedIds.add(existing.id)
                }
            }
        }

        _photos.value = mergedPhotos
        persistData()
    }


    fun toggleFavorite(photoId: String): Boolean {
        var newFavoriteState = false
        _photos.value = _photos.value.map { photo ->
            if (photo.id == photoId) {
                newFavoriteState = !photo.isFavorite
                photo.copy(isFavorite = newFavoriteState)
            } else {
                photo
            }
        }
        persistData()
        return newFavoriteState
    }

    fun addPhoto(
        fileName: String,
        filePath: String,
        title: String,
        description: String = "",
        folderId: String? = null,
        categoryIds: Set<String> = emptySet(),
        isFavorite: Boolean = false,
        isScreenshot: Boolean = false,
        dateAdded: Long = 0L,
        fileSizeBytes: Long = 0L
    ): Photo {
        val targetFolderId = folderId
            ?: _folders.value.firstOrNull { it.id == "folder_screenshots" || it.name.contains("màn hình", ignoreCase = true) }?.id
            ?: _folders.value.firstOrNull { it.isDefault }?.id
            ?: _folders.value.firstOrNull()?.id
            ?: "folder_screenshots"
        val photo = Photo(
            id = "photo_${dateAdded}_${_photos.value.size + 1}",
            fileName = fileName,
            filePath = filePath,
            title = if (title.isBlank()) fileName else title,
            description = description,
            folderId = targetFolderId,
            categoryIds = categoryIds,
            isFavorite = isFavorite,
            isScreenshot = isScreenshot,
            dateAdded = dateAdded,
            fileSizeBytes = fileSizeBytes
        )
        _photos.value = listOf(photo) + _photos.value
        persistData()
        return photo
    }

    fun updatePhoto(
        photoId: String,
        title: String,
        description: String,
        folderId: String,
        categoryIds: Set<String>,
        isFavorite: Boolean? = null
    ): Photo? {
        var updatedPhoto: Photo? = null
        _photos.value = _photos.value.map { photo ->
            if (photo.id == photoId) {
                val updated = photo.copy(
                    title = title,
                    description = description,
                    folderId = folderId,
                    categoryIds = categoryIds,
                    isFavorite = isFavorite ?: photo.isFavorite
                )
                updatedPhoto = updated
                updated
            } else {
                photo
            }
        }
        if (updatedPhoto != null) {
            persistData()
        }
        return updatedPhoto
    }

    fun deletePhoto(photoId: String): Photo? {
        val target = _photos.value.find { it.id == photoId } ?: return null
        _photos.value = _photos.value.filterNot { it.id == photoId }
        persistData()
        return target
    }

    fun movePhotosToFolder(photoIds: List<String>, targetFolderId: String) {
        if (_folders.value.none { it.id == targetFolderId }) return
        _photos.value = _photos.value.map { photo ->
            if (photo.id in photoIds) {
                photo.copy(folderId = targetFolderId)
            } else {
                photo
            }
        }
        persistData()
    }


    fun createFolder(name: String, colorHex: String = "#3B82F6", iconName: String = "folder", timestamp: Long = 0L): Folder {
        val trimmed = name.trim()
        val id = "folder_${timestamp}_${_folders.value.size + 1}"
        val folder = Folder(
            id = id,
            name = trimmed,
            colorHex = colorHex,
            iconName = iconName,
            isDefault = false,
            createdAt = timestamp
        )
        _folders.value = _folders.value + folder
        persistData()
        return folder
    }

    fun renameFolder(folderId: String, newName: String, colorHex: String? = null, iconName: String? = null): Folder? {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return null
        var updatedFolder: Folder? = null
        _folders.value = _folders.value.map { folder ->
            if (folder.id == folderId) {
                val updated = folder.copy(
                    name = trimmed,
                    colorHex = colorHex ?: folder.colorHex,
                    iconName = iconName ?: folder.iconName
                )
                updatedFolder = updated
                updated
            } else {
                folder
            }
        }
        if (updatedFolder != null) {
            persistData()
        }
        return updatedFolder
    }

    fun deleteFolder(folderId: String): Boolean {
        val folder = _folders.value.find { it.id == folderId } ?: return false
        if (folder.isDefault) return false

        val defaultFolder = _folders.value.firstOrNull { it.isDefault }
            ?: _folders.value.firstOrNull { it.id != folderId }

        val fallbackId = defaultFolder?.id ?: "folder_camera"

        _photos.value = _photos.value.map { photo ->
            if (photo.folderId == folderId) {
                photo.copy(folderId = fallbackId)
            } else {
                photo
            }
        }

        _folders.value = _folders.value.filterNot { it.id == folderId }

        if (_currentFilter.value.folderId == folderId) {
            setFolderFilter(null)
        }

        persistData()
        return true
    }

    fun createCategory(name: String, colorHex: String = "#6366F1", iconName: String = "tag", timestamp: Long = 0L): Category? {
        if (!_accountTier.value.isPro) {
            return null
        }
        val trimmed = name.trim()
        if (trimmed.isBlank()) return null
        val id = "cat_${timestamp}_${_categories.value.size + 1}"
        val category = Category(
            id = id,
            name = trimmed,
            colorHex = colorHex,
            iconName = iconName,
            createdAt = timestamp
        )
        _categories.value = _categories.value + category
        persistData()
        return category
    }

    fun renameCategory(categoryId: String, newName: String, newColorHex: String, newIconName: String? = null): Category? {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return null
        var updatedCategory: Category? = null
        _categories.value = _categories.value.map { cat ->
            if (cat.id == categoryId) {
                val updated = cat.copy(
                    name = trimmed,
                    colorHex = newColorHex,
                    iconName = newIconName ?: cat.iconName
                )
                updatedCategory = updated
                updated
            } else {
                cat
            }
        }
        if (updatedCategory != null) {
            persistData()
        }
        return updatedCategory
    }

    fun deleteCategory(categoryId: String): Boolean {
        if (_categories.value.none { it.id == categoryId }) return false

        _photos.value = _photos.value.map { photo ->
            if (categoryId in photo.categoryIds) {
                photo.copy(categoryIds = photo.categoryIds - categoryId)
            } else {
                photo
            }
        }

        _categories.value = _categories.value.filterNot { it.id == categoryId }

        if (_currentFilter.value.categoryId == categoryId) {
            setCategoryFilter(null)
        }

        persistData()
        return true
    }

    /**
     * Retroactively assign a collection of photo IDs to a specific category.
     * Preserves existing category associations and avoids duplicates.
     */
    fun assignPhotosToCategory(photoIds: Collection<String>, categoryId: String): Int {
        if (_categories.value.none { it.id == categoryId } || photoIds.isEmpty()) return 0
        val targetIdSet = photoIds.toSet()
        var updatedCount = 0

        _photos.value = _photos.value.map { photo ->
            if (photo.id in targetIdSet && categoryId !in photo.categoryIds) {
                updatedCount++
                photo.copy(categoryIds = photo.categoryIds + categoryId)
            } else {
                photo
            }
        }

        if (updatedCount > 0) {
            persistData()
        }
        return updatedCount
    }

    /**
     * Retroactively move and reassign a collection of photo IDs to a specific category.
     * Replaces previous category associations with newCategoryId to prevent clutter,
     * while rigorously preserving calendar and shopping scheduling capabilities.
     */
    fun reassignPhotosToCategory(photoIds: Collection<String>, newCategoryId: String): Int {
        if (_categories.value.none { it.id == newCategoryId } || photoIds.isEmpty()) return 0
        val targetIdSet = photoIds.toSet()
        var updatedCount = 0

        _photos.value = _photos.value.map { photo ->
            if (photo.id in targetIdSet) {
                updatedCount++

                // Identify if photo has calendar or shopping scheduling capabilities
                val hadCalendarEvent = photo.suggestedEventDateMillis != null ||
                        photo.detectedIntent == "EVENT" ||
                        photo.categoryIds.contains("cat_events")
                val hadShoppingSchedule = photo.detectedIntent == "SHOPPING" ||
                        photo.categoryIds.contains("cat_tobuy")

                // Rigorously preserve intent so calendar scheduling remains active regardless of old category removal
                val preservedIntent = when {
                    hadCalendarEvent -> "EVENT"
                    hadShoppingSchedule && photo.detectedIntent != "EVENT" -> "SHOPPING"
                    else -> photo.detectedIntent
                }

                photo.copy(
                    categoryIds = setOf(newCategoryId),
                    detectedIntent = preservedIntent,
                    suggestedEventTitle = photo.suggestedEventTitle,
                    suggestedEventDateMillis = photo.suggestedEventDateMillis,
                    suggestedEventDescription = photo.suggestedEventDescription,
                    hasReminderCreated = photo.hasReminderCreated
                )
            } else {
                photo
            }
        }

        if (updatedCount > 0) {
            persistData()
        }
        return updatedCount
    }
}
