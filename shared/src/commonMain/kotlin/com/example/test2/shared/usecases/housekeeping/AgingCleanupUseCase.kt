package com.example.test2.shared.usecases.housekeeping

import com.example.test2.shared.models.Photo
import com.example.test2.shared.repository.IPhotoRepository
import com.example.test2.shared.usecases.AgingMonitorPort
import com.example.test2.shared.usecases.PhotoStorageCleanerPort

/**
 * Use case for housekeeping aging screenshot detection, retention rule configuration, and clutter cleanup.
 */
class AgingCleanupUseCase(
    private val repository: IPhotoRepository,
    private val storageCleaner: PhotoStorageCleanerPort,
    private val agingMonitor: AgingMonitorPort
) {
    fun getAgingScreenshots(
        daysOld: Int = repository.agingSettings.value.retentionDays,
        nowMillis: Long
    ): List<Photo> {
        return repository.getAgingPhotos(daysOld, nowMillis)
    }

    suspend fun cleanAgingScreenshots(photosToClean: List<Photo>): Triple<Long, Int, Long> {
        if (photosToClean.isNotEmpty()) {
            for (p in photosToClean) {
                if (!p.filePath.startsWith("content://")) {
                    storageCleaner.deletePhysicalFile(p.filePath)
                }
                storageCleaner.purgePhotoData(p.id)
            }
            repository.batchDeletePhotos(photosToClean.map { it.id })
        }
        return agingMonitor.getLastCheckedInfo()
    }

    suspend fun runAgingScanNow(): Triple<Long, Int, Long> {
        return agingMonitor.checkAgingAndNotify(force = true)
    }

    fun getLastCheckedInfo(): Triple<Long, Int, Long> {
        return agingMonitor.getLastCheckedInfo()
    }

    fun setCategoryRetentionRule(categoryId: String, retentionDays: Int) {
        repository.setCategoryRetentionRule(categoryId, retentionDays)
    }
}
