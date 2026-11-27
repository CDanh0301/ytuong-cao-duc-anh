package com.example.test2.data.local.dao

import com.example.test2.database.ProcessedScreenshotRecord
import com.example.test2.database.QueueStateUpdate
import com.example.test2.database.QueueStatus
import com.example.test2.database.ScreenshotQueueItem

/**
 * Data Access Object contract for the screenshot processing queue and metadata records.
 */
interface ScreenshotQueueDao {
    fun enqueueScreenshot(
        photoId: String,
        filePath: String,
        dateAdded: Long,
        isLiveCapture: Boolean = false,
        scrubbedText: String? = null,
        photoTitle: String? = null,
        originalFileName: String? = null
    ): Boolean

    fun syncVaultedPathToQueue(
        photoId: String,
        newFilePath: String,
        dateAdded: Long = System.currentTimeMillis(),
        photoTitle: String? = null,
        originalFileName: String? = null
    ): Boolean

    fun enqueueScreenshotsBatch(items: List<Triple<String, String, Long>>): Int

    fun claimNextPendingQueueItem(): ScreenshotQueueItem?

    fun claimNextPendingQueueItems(limit: Int = 6): List<ScreenshotQueueItem>

    fun updateQueueItemState(update: QueueStateUpdate)

    fun updateQueueItemState(
        photoId: String,
        status: String,
        scrubbedText: String? = null,
        detectedIntent: String? = null,
        eventTitle: String? = null,
        eventDateMillis: Long? = null,
        targetCategoryId: String? = null,
        eventDescription: String? = null,
        summary: String? = null,
        tags: String? = null,
        entities: String? = null,
        itemsToBuy: String? = null,
        needsCloudUpgrade: Boolean = false,
        modelName: String? = null,
        modelVersion: String? = null
    )

    fun getPendingQueue(): List<ScreenshotQueueItem>

    fun getQueueItemsNeedingCloudUpgrade(): List<ScreenshotQueueItem>

    fun markQueueItemProcessed(photoId: String, status: String = QueueStatus.COMPLETED.name)

    fun markQueueItemProcessed(photoId: String, status: QueueStatus)

    fun isScreenshotProcessedOrNotified(photoId: String): Boolean

    fun recordProcessedScreenshot(
        photoId: String,
        filePath: String,
        dateAdded: Long,
        scrubbedText: String?,
        detectedIntent: String?,
        eventTitle: String?,
        eventDateMillis: Long?,
        targetCategoryId: String?,
        status: String = QueueStatus.COMPLETED.name,
        eventDescription: String? = null,
        summary: String? = null,
        tags: String? = null,
        entities: String? = null,
        itemsToBuy: String? = null,
        photoTitle: String? = null,
        originalFileName: String? = null,
        forceCategoryUpdate: Boolean = false
    )

    fun updatePhotosCategoryInQueue(
        photoIds: Collection<String>,
        targetCategoryId: String?,
        lockCategory: Boolean = true
    )

    fun clearCategoryInQueue(categoryId: String)

    fun getProcessedScreenshot(photoId: String): ProcessedScreenshotRecord?

    fun resetHangingProcessingItems()

    fun getPendingQueueCount(): Int

    fun migratePhotoRecords(
        oldPhotoId: String,
        newPhotoId: String,
        newFilePath: String,
        dateAdded: Long,
        scrubbedText: String?,
        detectedIntent: String?,
        eventTitle: String?,
        eventDateMillis: Long?,
        targetCategoryId: String?,
        eventDescription: String? = null,
        photoTitle: String? = null,
        originalFileName: String? = null
    )

    fun getProcessedScreenshotsByIds(photoIds: List<String>): List<ProcessedScreenshotRecord>

    fun getAllCompletedProcessedScreenshots(): List<ProcessedScreenshotRecord>

    fun getAllCompletedOrVaultedScreenshots(): List<ProcessedScreenshotRecord>

    fun purgePhotoData(photoId: String)
}
