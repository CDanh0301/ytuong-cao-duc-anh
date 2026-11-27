package com.example.test2.data.local.dao

import com.example.test2.data.repository.AndroidFilePersistenceDataSource
import com.example.test2.database.AppDatabaseHelper
import com.example.test2.database.ProcessedScreenshotRecord
import com.example.test2.database.QueueStateUpdate
import com.example.test2.database.QueueStatus
import com.example.test2.database.ScreenshotQueueItem
import com.example.test2.shared.models.ActionCard
import com.example.test2.shared.models.ActionCardType
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Folder
import com.example.test2.shared.models.Photo
import com.example.test2.shared.models.PhotoFilter
import com.example.test2.shared.models.SortOption
import com.example.test2.shared.repository.InMemoryPersistenceDriver
import com.example.test2.shared.repository.PhotoFilterEngine
import com.example.test2.shared.repository.PhotoRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying DAO interface contracts, [AndroidFilePersistenceDataSource],
 * [PhotoFilterEngine], and [AppDatabaseHelper] interface conformance.
 */
class DaoContractsTest {

    private class InMemoryQueueDao : ScreenshotQueueDao {
        private val items = linkedMapOf<String, ScreenshotQueueItem>()

        override fun enqueueScreenshot(
            photoId: String,
            filePath: String,
            dateAdded: Long,
            isLiveCapture: Boolean,
            scrubbedText: String?,
            photoTitle: String?,
            originalFileName: String?
        ): Boolean {
            if (items.containsKey(photoId)) return false
            items[photoId] = ScreenshotQueueItem(
                id = items.size + 1L,
                photoId = photoId,
                filePath = filePath,
                dateAdded = dateAdded,
                status = QueueStatus.PENDING.name,
                scrubbedText = scrubbedText,
                isLiveCapture = isLiveCapture,
                photoTitle = photoTitle,
                originalFileName = originalFileName
            )
            return true
        }

        override fun syncVaultedPathToQueue(
            photoId: String,
            newFilePath: String,
            dateAdded: Long,
            photoTitle: String?,
            originalFileName: String?
        ): Boolean {
            val existing = items[photoId]
            items[photoId] = existing?.copy(filePath = newFilePath) ?: ScreenshotQueueItem(
                id = items.size + 1L,
                photoId = photoId,
                filePath = newFilePath,
                dateAdded = dateAdded,
                status = QueueStatus.PENDING.name,
                photoTitle = photoTitle,
                originalFileName = originalFileName
            )
            return true
        }

        override fun enqueueScreenshotsBatch(items: List<Triple<String, String, Long>>): Int =
            items.count { enqueueScreenshot(it.first, it.second, it.third) }

        override fun claimNextPendingQueueItem(): ScreenshotQueueItem? =
            claimNextPendingQueueItems(1).firstOrNull()

        override fun claimNextPendingQueueItems(limit: Int): List<ScreenshotQueueItem> {
            val claimed = items.values.filter { it.status == QueueStatus.PENDING.name }.take(limit)
            return claimed.map {
                val updated = it.copy(status = QueueStatus.PROCESSING.name)
                items[it.photoId] = updated
                updated
            }
        }

        override fun updateQueueItemState(update: QueueStateUpdate) {
            items[update.photoId]?.let { cur ->
                val allowCategory = update.forceCategoryUpdate || !cur.isUserCategorized
                items[update.photoId] = cur.copy(
                    status = update.status,
                    scrubbedText = update.scrubbedText ?: cur.scrubbedText,
                    detectedIntent = update.detectedIntent ?: cur.detectedIntent,
                    targetCategoryId = if (allowCategory) (update.targetCategoryId ?: cur.targetCategoryId) else cur.targetCategoryId,
                    isUserCategorized = if (update.forceCategoryUpdate) false else cur.isUserCategorized
                )
            }
        }

        override fun updateQueueItemState(
            photoId: String,
            status: String,
            scrubbedText: String?,
            detectedIntent: String?,
            eventTitle: String?,
            eventDateMillis: Long?,
            targetCategoryId: String?,
            eventDescription: String?,
            summary: String?,
            tags: String?,
            entities: String?,
            itemsToBuy: String?,
            needsCloudUpgrade: Boolean,
            modelName: String?,
            modelVersion: String?
        ) = updateQueueItemState(QueueStateUpdate(photoId, status, scrubbedText, detectedIntent, eventTitle, eventDateMillis, targetCategoryId))

        override fun getPendingQueue(): List<ScreenshotQueueItem> =
            items.values.filter { it.status == QueueStatus.PENDING.name }

        override fun getQueueItemsNeedingCloudUpgrade(): List<ScreenshotQueueItem> =
            items.values.filter { it.needsCloudUpgrade }

        override fun markQueueItemProcessed(photoId: String, status: String) {
            items[photoId]?.let { items[photoId] = it.copy(status = status) }
        }

        override fun markQueueItemProcessed(photoId: String, status: QueueStatus) =
            markQueueItemProcessed(photoId, status.name)

        override fun isScreenshotProcessedOrNotified(photoId: String): Boolean =
            items[photoId]?.status == QueueStatus.COMPLETED.name

        override fun recordProcessedScreenshot(
            photoId: String,
            filePath: String,
            dateAdded: Long,
            scrubbedText: String?,
            detectedIntent: String?,
            eventTitle: String?,
            eventDateMillis: Long?,
            targetCategoryId: String?,
            status: String,
            eventDescription: String?,
            summary: String?,
            tags: String?,
            entities: String?,
            itemsToBuy: String?,
            photoTitle: String?,
            originalFileName: String?,
            forceCategoryUpdate: Boolean
        ) {
            val existing = items[photoId]
            val finalCat = if (existing?.isUserCategorized == true && !forceCategoryUpdate) {
                existing.targetCategoryId
            } else {
                targetCategoryId
            }
            val finalLocked = if (forceCategoryUpdate) false else (existing?.isUserCategorized == true)
            items[photoId] = ScreenshotQueueItem(
                id = existing?.id ?: (items.size + 1L),
                photoId = photoId,
                filePath = filePath,
                dateAdded = dateAdded,
                status = status,
                scrubbedText = scrubbedText,
                detectedIntent = detectedIntent,
                eventTitle = eventTitle,
                eventDateMillis = eventDateMillis,
                targetCategoryId = finalCat,
                isUserCategorized = finalLocked
            )
        }

        override fun updatePhotosCategoryInQueue(
            photoIds: Collection<String>,
            targetCategoryId: String?,
            lockCategory: Boolean
        ) {
            for (photoId in photoIds) {
                items[photoId]?.let { cur ->
                    items[photoId] = cur.copy(
                        targetCategoryId = targetCategoryId,
                        isUserCategorized = lockCategory
                    )
                }
            }
        }

        override fun clearCategoryInQueue(categoryId: String) {
            for ((photoId, item) in items.toMap()) {
                if (item.targetCategoryId == categoryId) {
                    items[photoId] = item.copy(targetCategoryId = null)
                }
            }
        }

        override fun getProcessedScreenshot(photoId: String): ProcessedScreenshotRecord? =
            items[photoId]?.takeIf { it.status == QueueStatus.COMPLETED.name }?.let {
                ProcessedScreenshotRecord(
                    photoId = it.photoId,
                    filePath = it.filePath,
                    scrubbedText = it.scrubbedText,
                    detectedIntent = it.detectedIntent,
                    eventTitle = it.eventTitle,
                    eventDateMillis = it.eventDateMillis,
                    targetCategoryId = it.targetCategoryId,
                    isUserCategorized = it.isUserCategorized
                )
            }

        override fun resetHangingProcessingItems() {
            items.values.filter { it.status == QueueStatus.PROCESSING.name }.forEach {
                items[it.photoId] = it.copy(status = QueueStatus.PENDING.name)
            }
        }

        override fun getPendingQueueCount(): Int =
            items.values.count { it.status in setOf(QueueStatus.PENDING.name, QueueStatus.PROCESSING.name, QueueStatus.OCR_DONE.name) }

        override fun migratePhotoRecords(
            oldPhotoId: String,
            newPhotoId: String,
            newFilePath: String,
            dateAdded: Long,
            scrubbedText: String?,
            detectedIntent: String?,
            eventTitle: String?,
            eventDateMillis: Long?,
            targetCategoryId: String?,
            eventDescription: String?,
            photoTitle: String?,
            originalFileName: String?
        ) {
            items.remove(oldPhotoId)
            recordProcessedScreenshot(newPhotoId, newFilePath, dateAdded, scrubbedText, detectedIntent, eventTitle, eventDateMillis, targetCategoryId)
        }

        override fun getProcessedScreenshotsByIds(photoIds: List<String>): List<ProcessedScreenshotRecord> =
            photoIds.mapNotNull { getProcessedScreenshot(it) }

        override fun getAllCompletedProcessedScreenshots(): List<ProcessedScreenshotRecord> =
            items.keys.mapNotNull { getProcessedScreenshot(it) }

        override fun getAllCompletedOrVaultedScreenshots(): List<ProcessedScreenshotRecord> =
            getAllCompletedProcessedScreenshots()

        override fun purgePhotoData(photoId: String) {
            items.remove(photoId)
        }
    }

    @Test
    fun appDatabaseHelperImplementsAllFourDaoInterfaces() {
        val interfaces = AppDatabaseHelper::class.java.interfaces.toSet()
        assertTrue(ScreenshotQueueDao::class.java in interfaces)
        assertTrue(FtsSearchDao::class.java in interfaces)
        assertTrue(VectorDao::class.java in interfaces)
        assertTrue(ActionCardDao::class.java in interfaces)
    }

    @Test
    fun queueDaoStateMachineTransitionsWorkCleanly() {
        val dao: ScreenshotQueueDao = InMemoryQueueDao()
        assertTrue(dao.enqueueScreenshot("p1", "/path/p1.png", 1000L, isLiveCapture = true))
        assertFalse(dao.enqueueScreenshot("p1", "/path/p1.png", 1000L))
        assertEquals(1, dao.getPendingQueueCount())

        val claimed = dao.claimNextPendingQueueItems(limit = 5)
        assertEquals(1, claimed.size)
        assertEquals(QueueStatus.PROCESSING.name, claimed.first().status)

        dao.resetHangingProcessingItems()
        assertEquals(1, dao.getPendingQueue().size)

        dao.claimNextPendingQueueItem()
        dao.markQueueItemProcessed("p1", QueueStatus.COMPLETED)
        assertTrue(dao.isScreenshotProcessedOrNotified("p1"))
        assertEquals(0, dao.getPendingQueueCount())
        assertNotNull(dao.getProcessedScreenshot("p1"))

        dao.migratePhotoRecords("p1", "p2", "content://2", 1000L, "ocr", "EVENT", "Meet", null, "cat_events")
        assertTrue(dao.isScreenshotProcessedOrNotified("p2"))

        dao.updatePhotosCategoryInQueue(listOf("p2"), "cat_custom_1", lockCategory = true)
        assertEquals("cat_custom_1", dao.getProcessedScreenshot("p2")?.targetCategoryId)
        assertTrue(dao.getProcessedScreenshot("p2")?.isUserCategorized == true)

        // Background AI write without forceCategoryUpdate must not overwrite user category
        dao.recordProcessedScreenshot("p2", "content://2", 1000L, "ocr", "SHOPPING", "Buy", null, "cat_tobuy")
        assertEquals("cat_custom_1", dao.getProcessedScreenshot("p2")?.targetCategoryId)

        dao.clearCategoryInQueue("cat_custom_1")
        assertEquals(null, dao.getProcessedScreenshot("p2")?.targetCategoryId)

        dao.purgePhotoData("p2")
        assertFalse(dao.isScreenshotProcessedOrNotified("p2"))
    }

    @Test
    fun androidFilePersistenceDataSourceReadsCategoriesAndTierWithoutManualWorkerParsing() = runBlocking {
        val sampleJson = """
            {
              "categories": [
                {"id": "cat_custom_1", "name": "Du lịch", "colorHex": "#10B981", "iconName": "plane", "createdAt": 12345}
              ],
              "accountTier": {
                "isPro": false
              }
            }
        """.trimIndent()
        val dataSource = AndroidFilePersistenceDataSource(InMemoryPersistenceDriver(sampleJson))
        val categories = dataSource.getUserCategories()
        assertEquals(1, categories.size)
        assertEquals("cat_custom_1", categories.first().id)
        assertEquals("Du lịch", categories.first().name)
        assertFalse(dataSource.isProTier())
    }

    @Test
    fun photoFilterEngineHandlesUnaccentedVietnameseAndSorting() {
        val photos = listOf(
            Photo("1", "a.png", "/a.png", "Hóa đơn tiền điện", folderId = "f1", categoryIds = setOf("c1"), dateAdded = 100L),
            Photo("2", "b.png", "/b.png", "Ảnh núi Fansipan", folderId = "f1", categoryIds = setOf("c2"), isFavorite = true, dateAdded = 200L)
        )
        val categories = listOf(Category("c1", "Chi tiêu"), Category("c2", "Thiên nhiên"))
        val folders = listOf(Folder("f1", "Screenshots", createdAt = 1L))

        val unaccentedResult = PhotoFilterEngine.filterAndSort(
            photoList = photos,
            filter = PhotoFilter(searchQuery = "hoa don", sortOption = SortOption.DATE_DESC),
            categoriesList = categories,
            foldersList = folders,
            chronologicalDescComparator = PhotoRepository.chronologicalDescComparator,
            chronologicalAscComparator = PhotoRepository.chronologicalAscComparator
        )
        assertEquals(listOf("1"), unaccentedResult.map { it.id })

        val favAsc = PhotoFilterEngine.filterAndSort(
            photoList = photos,
            filter = PhotoFilter(onlyFavorites = true, sortOption = SortOption.DATE_ASC),
            categoriesList = categories,
            foldersList = folders,
            chronologicalDescComparator = PhotoRepository.chronologicalDescComparator,
            chronologicalAscComparator = PhotoRepository.chronologicalAscComparator
        )
        assertEquals(listOf("2"), favAsc.map { it.id })

        // Verify ActionCard model compatibility
        val card = ActionCard("ac1", "1", ActionCardType.PAYMENT, "EVN", "350k")
        assertEquals(ActionCardType.PAYMENT, card.type)
    }
}
