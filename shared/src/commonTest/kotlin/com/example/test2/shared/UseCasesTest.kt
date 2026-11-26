package com.example.test2.shared

import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Folder
import com.example.test2.shared.models.Photo
import com.example.test2.shared.repository.InMemoryPersistenceDriver
import com.example.test2.shared.repository.PhotoRepository
import com.example.test2.shared.usecases.AgingMonitorPort
import com.example.test2.shared.usecases.DeviceScanOutput
import com.example.test2.shared.usecases.FtsIndexPort
import com.example.test2.shared.usecases.ImportedFileResult
import com.example.test2.shared.usecases.MediaScannerPort
import com.example.test2.shared.usecases.PhotoImportPort
import com.example.test2.shared.usecases.PhotoStorageCleanerPort
import com.example.test2.shared.usecases.QueueSyncPort
import com.example.test2.shared.usecases.RestoredFileResult
import com.example.test2.shared.usecases.RetroactiveCategorizerPort
import com.example.test2.shared.usecases.SearchOutput
import com.example.test2.shared.usecases.SemanticSearchPort
import com.example.test2.shared.usecases.VaultPort
import com.example.test2.shared.usecases.VaultedFileResult
import com.example.test2.shared.usecases.category.CreateCategoryUseCase
import com.example.test2.shared.usecases.category.DeleteCategoryUseCase
import com.example.test2.shared.usecases.category.RetroactiveCategoryUseCase
import com.example.test2.shared.usecases.housekeeping.AgingCleanupUseCase
import com.example.test2.shared.usecases.photo.DeletePhotoUseCase
import com.example.test2.shared.usecases.photo.ImportPhotosUseCase
import com.example.test2.shared.usecases.photo.ScanDevicePhotosUseCase
import com.example.test2.shared.usecases.photo.ToggleFavoriteUseCase
import com.example.test2.shared.usecases.photo.VaultPhotoUseCase
import com.example.test2.shared.usecases.search.SearchPhotosUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UseCasesTest {

    private class FakeVaultPort(
        var enabled: Boolean = false,
        var allFilesAccess: Boolean = true,
        var remainingEnc: Boolean = false,
        var failNextRestore: Boolean = false
    ) : VaultPort {
        val exemptIds = mutableSetOf<String>()
        var evictionBeginCount = 0
        var evictionEndCount = 0

        override fun isVaultEnabled(): Boolean = enabled
        override fun setVaultEnabled(enabled: Boolean) { this.enabled = enabled }
        override fun hasAllFilesAccess(): Boolean = allFilesAccess
        override fun shouldAutoVaultPhoto(photoId: String, dateAddedMillis: Long): Boolean =
            enabled && photoId !in exemptIds
        override fun beginInternalEviction() { evictionBeginCount++ }
        override fun endInternalEviction() { evictionEndCount++ }
        override fun markRestoredExempt(photoIdOrMediaId: String) { exemptIds.add(photoIdOrMediaId) }
        override fun clearRestoredExempt(photoIdOrMediaId: String) { exemptIds.remove(photoIdOrMediaId) }
        override fun clearAllRestoredExempts() { exemptIds.clear() }
        override fun sanitizeRestoreFileName(rawTitle: String, encryptedFilePath: String, fallbackFileName: String): String =
            "${rawTitle.ifBlank { "Screenshot" }}.png"

        override suspend fun vaultAndEvictScreenshot(
            sourceUriString: String?,
            sourcePath: String,
            originalFileName: String
        ): VaultedFileResult {
            return VaultedFileResult(
                newFilePath = "/vault/${originalFileName}.enc",
                newFileName = "${originalFileName}.enc",
                fileSizeBytes = 2048L,
                isOriginalEvicted = true
            )
        }

        override suspend fun restoreAndEvictScreenshot(
            vaultedFilePath: String,
            originalFileName: String,
            originalDateAddedMillis: Long?,
            markExemptOnRestore: Boolean
        ): RestoredFileResult? {
            if (failNextRestore) {
                failNextRestore = false
                return null
            }
            val newId = 900L + originalFileName.hashCode().let { if (it < 0) -it else it } % 100
            return RestoredFileResult(
                newUri = "content://media/external/images/media/$newId",
                newFilePath = "content://media/external/images/media/$newId",
                newFileName = originalFileName,
                fileSizeBytes = 2048L,
                newMediaId = newId
            )
        }

        override fun hasRemainingEncryptedFiles(): Boolean = remainingEnc
    }

    private class FakeQueueSyncPort : QueueSyncPort {
        val syncedPaths = mutableListOf<Pair<String, String>>()
        val migrated = mutableListOf<Pair<String, String>>()
        val enqueued = mutableListOf<String>()

        override fun syncVaultedPathToQueue(
            photoId: String,
            newFilePath: String,
            dateAdded: Long,
            photoTitle: String?,
            originalFileName: String?
        ): Boolean {
            syncedPaths.add(photoId to newFilePath)
            return true
        }

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
            migrated.add(oldPhotoId to newPhotoId)
        }

        override fun enqueueScreenshot(
            photoId: String,
            filePath: String,
            dateAdded: Long,
            isLiveCapture: Boolean,
            photoTitle: String?,
            originalFileName: String?
        ): Boolean {
            enqueued.add(photoId)
            return true
        }
    }

    private class FakeStorageCleaner : PhotoStorageCleanerPort {
        val deletedPhysicalPaths = mutableListOf<String>()
        val purgedIds = mutableListOf<String>()

        override suspend fun deletePhysicalFile(filePath: String): Boolean {
            deletedPhysicalPaths.add(filePath)
            return true
        }

        override fun purgePhotoData(photoId: String) {
            purgedIds.add(photoId)
        }
    }

    @Test
    fun testToggleFavoriteAndDeletePhotoUseCases() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val repo = PhotoRepository(InMemoryPersistenceDriver(), scope)
        testScheduler.advanceUntilIdle()

        val photoPrivate = repo.addPhoto("a.jpg.enc", "/files/photos/a.jpg.enc", "Private", isScreenshot = true, dateAdded = 1000L)
        val photoPublic = repo.addPhoto("b.jpg", "content://media/external/images/media/2", "Public", isScreenshot = true, dateAdded = 2000L)
        testScheduler.advanceUntilIdle()

        val toggleFavorite = ToggleFavoriteUseCase(repo)
        assertTrue(toggleFavorite(photoPrivate.id))
        assertFalse(toggleFavorite(photoPrivate.id))

        val cleaner = FakeStorageCleaner()
        val deletePhoto = DeletePhotoUseCase(repo, cleaner)

        deletePhoto(photoPrivate)
        deletePhoto(photoPublic)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("/files/photos/a.jpg.enc"), cleaner.deletedPhysicalPaths)
        assertEquals(listOf(photoPrivate.id, photoPublic.id), cleaner.purgedIds)
        assertTrue(repo.photos.value.isEmpty())
    }

    @Test
    fun testScanDevicePhotosUseCaseAutoVaultsEligibleScreenshots() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val repo = PhotoRepository(InMemoryPersistenceDriver(), scope)
        testScheduler.advanceUntilIdle()

        val scanned = Photo(
            id = "device_1",
            fileName = "Screenshot_1.png",
            filePath = "content://media/external/images/media/1",
            title = "Screenshot_1",
            folderId = "Screenshots",
            dateAdded = 10_000L,
            isScreenshot = true
        )
        val scanner = object : MediaScannerPort {
            override suspend fun scanDeviceImages(): DeviceScanOutput = DeviceScanOutput(
                photos = listOf(scanned),
                folders = listOf(Folder("folder_screenshots", "Ảnh chụp màn hình", createdAt = 1000L)),
                physicalPaths = mapOf("device_1" to "/sdcard/Pictures/Screenshots/Screenshot_1.png")
            )
        }
        val vaultPort = FakeVaultPort(enabled = true)
        val queuePort = FakeQueueSyncPort()
        val useCase = ScanDevicePhotosUseCase(scanner, repo, vaultPort, queuePort)

        val output = useCase()
        testScheduler.advanceUntilIdle()

        assertEquals(1, output.photos.size)
        assertTrue(output.photos.first().filePath.endsWith(".enc"))
        assertEquals(1, queuePort.syncedPaths.size)
        assertEquals(1, vaultPort.evictionBeginCount)
        assertEquals(1, vaultPort.evictionEndCount)
        assertTrue(repo.photos.value.first().filePath.endsWith(".enc"))
    }

    @Test
    fun testVaultPhotoUseCaseVaultSingleRestoreSingleAndRestoreAll() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val repo = PhotoRepository(InMemoryPersistenceDriver(), scope)
        testScheduler.advanceUntilIdle()

        val publicPhoto = repo.addPhoto(
            fileName = "Screenshot_pay.png",
            filePath = "content://media/external/images/media/50",
            title = "Pay",
            isScreenshot = true,
            dateAdded = 5000L
        )
        testScheduler.advanceUntilIdle()

        val vaultPort = FakeVaultPort(enabled = false)
        val queuePort = FakeQueueSyncPort()
        val useCase = VaultPhotoUseCase(vaultPort, queuePort, repo)

        val vaulted = useCase.vaultPhoto(repo.photos.value.first())
        testScheduler.advanceUntilIdle()
        assertTrue(vaulted.isSuccess)
        assertTrue(vaultPort.isVaultEnabled())
        assertTrue(repo.photos.value.first().filePath.endsWith(".enc"))

        val singleRestore = useCase.restorePhoto(repo.photos.value.first())
        testScheduler.advanceUntilIdle()
        assertTrue(singleRestore.isSuccess)
        val restoredPhoto = repo.photos.value.first()
        assertTrue(restoredPhoto.filePath.startsWith("content://"))
        assertFalse(vaultPort.isVaultEnabled())

        // Re-vault and test restoreAllPhotos
        useCase.vaultPhoto(restoredPhoto)
        testScheduler.advanceUntilIdle()
        val progressUpdates = mutableListOf<Pair<Int, Int>>()
        val allResult = useCase.restoreAllPhotos { cur, total -> progressUpdates.add(cur to total) }
        testScheduler.advanceUntilIdle()
        assertTrue(allResult.isSuccess)
        assertEquals(1, allResult.getOrThrow().successCount)
        assertEquals(0, allResult.getOrThrow().failedCount)
        assertFalse(allResult.getOrThrow().vaultEnabled)
        assertTrue(progressUpdates.contains(1 to 1))
        assertFalse(publicPhoto.id == repo.photos.value.first().id)
    }

    @Test
    fun testCategoryAndSearchAndAgingUseCases() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val repo = PhotoRepository(InMemoryPersistenceDriver(), scope)
        testScheduler.advanceUntilIdle()

        val createCat = CreateCategoryUseCase(repo)
        val deleteCat = DeleteCategoryUseCase(repo)

        // Free tier cannot create custom category
        assertNull(createCat("Hóa đơn", timestampMillis = 1000L))
        repo.toggleUserTier(true)
        val created = createCat("Hóa đơn", timestampMillis = 2000L)
        testScheduler.advanceUntilIdle()
        assertNotNull(created)

        val photo = repo.addPhoto(
            fileName = "bill.png",
            filePath = "/p/bill.png",
            title = "Tiền điện",
            isScreenshot = true,
            dateAdded = 1_000_000L
        )
        testScheduler.advanceUntilIdle()

        val indexedCalls = mutableListOf<Pair<String, String>>()
        var reindexCount = 0
        val searchPort = object : SemanticSearchPort {
            override suspend fun search(query: String): SearchOutput =
                SearchOutput(matchingIds = setOf(photo.id), dateRange = null, semanticMatchScores = mapOf(photo.id to 0.95f))
            override suspend fun ensureVectorsIndexed(forceReindex: Boolean) { reindexCount++ }
        }
        val retroPort = object : RetroactiveCategorizerPort {
            override suspend fun findMatchingPhotosForCategory(category: Category, photos: List<Photo>): List<String> =
                listOf(photo.id)
        }
        val ftsPort = object : FtsIndexPort {
            override fun indexPhotoFts(
                photoId: String,
                title: String,
                scrubbedText: String,
                intent: String,
                categoryNames: String,
                semanticTags: String
            ) {
                indexedCalls.add(photoId to categoryNames)
            }
        }

        val retroUseCase = RetroactiveCategoryUseCase(retroPort, searchPort, ftsPort, repo)
        val matched = retroUseCase.scanForCategory(created, forceReindex = true)
        assertEquals(listOf(photo.id), matched)
        val applied = retroUseCase.applyCategory(matched, created)
        testScheduler.advanceUntilIdle()
        assertEquals(1, applied)
        assertEquals(listOf(photo.id to "Hóa đơn"), indexedCalls)
        assertTrue(reindexCount >= 2)

        val searchUseCase = SearchPhotosUseCase(searchPort, repo)
        val searchOut = searchUseCase("điện")
        testScheduler.advanceUntilIdle()
        assertEquals(0.95f, searchOut.semanticMatchScores[photo.id])
        assertEquals(1, repo.filteredPhotos.value.size)

        searchUseCase("")
        testScheduler.advanceUntilIdle()
        assertEquals("", repo.currentFilter.value.searchQuery)

        // Aging cleanup
        val cleaner = FakeStorageCleaner()
        val monitor = object : AgingMonitorPort {
            override suspend fun checkAgingAndNotify(force: Boolean): Triple<Long, Int, Long> = Triple(999L, 1, 512L)
            override fun getLastCheckedInfo(): Triple<Long, Int, Long> = Triple(888L, 0, 0L)
        }
        val agingUseCase = AgingCleanupUseCase(repo, cleaner, monitor)
        agingUseCase.setCategoryRetentionRule(created.id, 7)
        val now = 1_000_000L + 10L * 86_400_000L
        val agingList = agingUseCase.getAgingScreenshots(daysOld = 30, nowMillis = now)
        assertEquals(1, agingList.size)
        assertEquals(Triple(999L, 1, 512L), agingUseCase.runAgingScanNow())
        agingUseCase.cleanAgingScreenshots(agingList)
        testScheduler.advanceUntilIdle()
        assertTrue(repo.photos.value.isEmpty())

        assertTrue(deleteCat(created.id))
    }

    @Test
    fun testImportPhotosUseCaseUsesCloudWhenQuotaAvailableAndFallsBackWhenExhausted() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val repo = PhotoRepository(InMemoryPersistenceDriver(), scope)
        testScheduler.advanceUntilIdle()

        var cloudCalls = 0
        var localCalls = 0
        val importPort = object : PhotoImportPort {
            override suspend fun saveImageFromUriString(uriString: String): ImportedFileResult =
                ImportedFileResult("img.jpg.enc", "/vault/img.jpg.enc", 1024L)
            override fun inferInitialCategories(targetFolderId: String?, title: String): Set<String> = emptySet()
            override fun isJunkOrMeme(photo: Photo): Boolean = false
            override suspend fun processWithCloud(photo: Photo, targetFolderId: String?) { cloudCalls++ }
            override suspend fun processOnDevice(photo: Photo, targetFolderId: String?, targetCategoryIds: Set<String>) { localCalls++ }
        }
        val queuePort = FakeQueueSyncPort()
        val importUseCase = ImportPhotosUseCase(repo, importPort, queuePort)

        importUseCase(listOf("content://img/1"), null, emptySet(), 1762000000000L)
        testScheduler.advanceUntilIdle()
        assertEquals(1, cloudCalls)
        assertEquals(0, localCalls)
        assertEquals(1, queuePort.enqueued.size)

        // Exhaust free quota (50 scans)
        repeat(49) { repo.incrementQuotaUsage(1762000000000L) }
        importUseCase(listOf("content://img/2"), null, emptySet(), 1762000001000L)
        testScheduler.advanceUntilIdle()
        assertEquals(1, cloudCalls)
        assertEquals(1, localCalls)
    }
}
