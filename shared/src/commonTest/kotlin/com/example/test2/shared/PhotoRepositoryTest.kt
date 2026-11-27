package com.example.test2.shared

import com.example.test2.shared.models.Folder
import com.example.test2.shared.models.Photo
import com.example.test2.shared.models.PhotoFilter
import com.example.test2.shared.models.SortOption
import com.example.test2.shared.repository.InMemoryPersistenceDriver
import com.example.test2.shared.repository.PhotoRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoRepositoryTest {

    @Test
    fun testDefaultsAndFolderCreation() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val driver = InMemoryPersistenceDriver()
        val repo = PhotoRepository(driver, scope)
        testScheduler.advanceUntilIdle()

        val initialFolders = repo.folders.value
        assertTrue(initialFolders.isNotEmpty())
        assertTrue(initialFolders.any { (it.name == "Cuộn camera" || it.name == "Camera Roll") && it.isDefault })

        val newFolder = repo.createFolder("Vacation 2026", colorHex = "#10B981", iconName = "plane", timestamp = 5000L)
        testScheduler.advanceUntilIdle()

        assertEquals("Vacation 2026", newFolder.name)
        assertEquals("#10B981", newFolder.colorHex)
        assertEquals("plane", newFolder.iconName)
        assertTrue(repo.folders.value.any { it.id == newFolder.id })
    }

    @Test
    fun testIdeaIntelligenceAndAging() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val driver = InMemoryPersistenceDriver()
        val repo = PhotoRepository(driver, scope)
        testScheduler.advanceUntilIdle()

        // Test Quota
        assertEquals(false, repo.accountTier.value.isPro)
        assertTrue(repo.canUseAiScan())
        assertTrue(repo.incrementQuotaUsage())
        assertEquals(1, repo.accountTier.value.aiScansUsedThisMonth)

        // Add a screenshot
        val now = 100_000_000L
        val screenshot = repo.addPhoto(
            fileName = "Screenshot_meeting.png",
            filePath = "/screenshots/1.png",
            title = "Team Sync Meeting",
            isScreenshot = true,
            dateAdded = now - (20L * 24L * 60L * 60L * 1000L) // 20 days old
        )
        testScheduler.advanceUntilIdle()

        // Process screenshot with Mock AI metadata
        repo.updatePhotoAiMetadata(
            photoId = screenshot.id,
            isScreenshot = true,
            piiScrubbedText = "Meeting at 3pm with [REDACTED]",
            detectedIntent = "EVENT",
            suggestedEventTitle = "Team Sync Meeting",
            suggestedEventDateMillis = now + 86400000L,
            targetCategoryId = "cat_events",
            isProcessed = true
        )
        testScheduler.advanceUntilIdle()

        val updated = repo.photos.value.first { it.id == screenshot.id }
        assertEquals("EVENT", updated.detectedIntent)
        assertTrue(updated.isProcessed)
        assertTrue("cat_events" in updated.categoryIds)

        // Add a recent screenshot taken today
        val todayScreenshot = repo.addPhoto(
            fileName = "Screenshot_today.png",
            filePath = "/screenshots/today.png",
            title = "Recent Screenshot",
            isScreenshot = true,
            dateAdded = now - 1000L // taken seconds ago
        )
        testScheduler.advanceUntilIdle()
        repo.markAsProcessed(todayScreenshot.id, true)
        testScheduler.advanceUntilIdle()

        // Check aging detection: only 14+ day old photos, today's photo must NOT be included!
        val aging14 = repo.getAgingPhotos(daysOld = 14, nowMillis = now)
        assertEquals(1, aging14.size)
        assertEquals(screenshot.id, aging14.first().id)

        // 30 day threshold: neither is older than 30 days
        val aging30 = repo.getAgingPhotos(daysOld = 30, nowMillis = now)
        assertEquals(0, aging30.size)

        // Test Pro Custom Category Rules (IDEA.md Part 3)
        repo.toggleUserTier(isPro = true)
        testScheduler.advanceUntilIdle()
        // Set custom rule: cat_events retains only 7 days
        repo.setCategoryRetentionRule("cat_events", 7)
        testScheduler.advanceUntilIdle()

        // Now the 20-day old screenshot (which has cat_events) is flagged even though global threshold is 30 days!
        val agingCustom = repo.getAgingPhotos(daysOld = 30, nowMillis = now)
        assertEquals(1, agingCustom.size)
        assertEquals(screenshot.id, agingCustom.first().id)

        // Batch delete
        val deleted = repo.batchDeletePhotos(listOf(screenshot.id, todayScreenshot.id))
        testScheduler.advanceUntilIdle()
        assertEquals(2, deleted.size)
        assertEquals(0, repo.photos.value.size)
    }

    @Test
    fun testAgingStrictlyRequiresAgeOverConfiguredDaysAndEnforcesProEdit() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val driver = InMemoryPersistenceDriver()
        val repo = PhotoRepository(driver, scope)
        testScheduler.advanceUntilIdle()

        val dayMs = 24L * 60L * 60L * 1000L
        val now = 2_000_000_000_000L

        // Recent screenshot (2 days old) that IS processed and has a PAST suggestedEventDateMillis
        val recentProcessed = repo.addPhoto(
            fileName = "Screenshot_recent.png",
            filePath = "/screenshots/recent.png",
            title = "Recent Event",
            isScreenshot = true,
            dateAdded = now - (2L * dayMs)
        )
        repo.updatePhotoAiMetadata(
            photoId = recentProcessed.id,
            isScreenshot = true,
            piiScrubbedText = "Hội thảo 15/11",
            detectedIntent = "EVENT",
            suggestedEventTitle = "Hội thảo",
            suggestedEventDateMillis = now - dayMs, // past event
            targetCategoryId = "cat_events",
            isProcessed = true
        )

        // Screenshot at 29 days old (must NOT appear for 30-day rule)
        repo.addPhoto(
            fileName = "Screenshot_29d.png",
            filePath = "/screenshots/29d.png",
            title = "29 days",
            isScreenshot = true,
            dateAdded = now - (29L * dayMs)
        )

        // Screenshot at 31 days old (MUST appear for 30-day rule)
        val old31d = repo.addPhoto(
            fileName = "Screenshot_31d.png",
            filePath = "/screenshots/31d.png",
            title = "31 days",
            isScreenshot = true,
            dateAdded = now - (31L * dayMs)
        )

        // Non-screenshot photo older than 60 days (MUST NEVER appear)
        repo.addPhoto(
            fileName = "Camera_60d.jpg",
            filePath = "/dcim/60d.jpg",
            title = "Camera Photo",
            isScreenshot = false,
            dateAdded = now - (60L * dayMs)
        )
        testScheduler.advanceUntilIdle()

        // Free tier cannot modify retention days
        assertFalse(repo.setDefaultRetentionDays(7))
        repo.setCategoryRetentionRule("cat_events", 1)
        testScheduler.advanceUntilIdle()
        assertEquals(30, repo.agingSettings.value.retentionDays)
        assertTrue(repo.agingSettings.value.categoryRetentionDays.isEmpty())

        // Free tier: only the 31-day-old screenshot matches 30-day cutoff
        val freeAging = repo.getAgingPhotos(nowMillis = now)
        assertEquals(listOf(old31d.id), freeAging.map { it.id })

        // Pro tier CAN edit default retention days
        repo.toggleUserTier(isPro = true)
        testScheduler.advanceUntilIdle()
        assertTrue(repo.setDefaultRetentionDays(14))
        testScheduler.advanceUntilIdle()
        assertEquals(14, repo.agingSettings.value.retentionDays)

        val proAging14 = repo.getAgingPhotos(daysOld = 14, nowMillis = now)
        assertEquals(2, proAging14.size)
        assertFalse(proAging14.any { it.id == recentProcessed.id })
    }

    @Test
    fun testCustomAiCategoriesProEnforcement() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val driver = InMemoryPersistenceDriver()
        val repo = PhotoRepository(driver, scope)
        testScheduler.advanceUntilIdle()

        // 1. Free tier: creating custom categories must fail
        assertFalse(repo.accountTier.value.isPro)
        val freeCreated = repo.createCategory("Gym & Fitness", colorHex = "#EF4444")
        testScheduler.advanceUntilIdle()
        kotlin.test.assertNull(freeCreated, "Free tier must not be able to create custom categories")
        assertFalse(repo.categories.value.any { it.name == "Gym & Fitness" })

        // 2. Upgrade to Pro: category creation succeeds
        repo.toggleUserTier(isPro = true)
        testScheduler.advanceUntilIdle()
        assertTrue(repo.accountTier.value.isPro)

        val proCreated = repo.createCategory("Gym & Fitness", colorHex = "#EF4444")
        testScheduler.advanceUntilIdle()
        assertNotNull(proCreated)
        assertEquals("Gym & Fitness", proCreated.name)
        assertTrue(repo.categories.value.any { it.id == proCreated.id })
    }

    @Test
    fun testMonthlyQuotaAutoReset() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val driver = InMemoryPersistenceDriver()
        val repo = PhotoRepository(driver, scope)
        testScheduler.advanceUntilIdle()

        // Nov 1, 2026: 1761955200000L
        val novStart = 1761955200000L
        repo.checkMonthlyQuotaReset(novStart)
        testScheduler.advanceUntilIdle()

        // Use up quota
        for (i in 1..50) {
            assertTrue(repo.incrementQuotaUsage(novStart))
        }
        assertEquals(50, repo.accountTier.value.aiScansUsedThisMonth)
        assertFalse(repo.canUseAiScan(novStart), "Free user out of 50 monthly quota should be blocked")

        // Same month (Nov 15, 2026): still blocked
        val novMid = novStart + 14L * 86_400_000L
        assertFalse(repo.checkMonthlyQuotaReset(novMid))
        assertFalse(repo.canUseAiScan(novMid))

        // New month arrives (Dec 1, 2026: 30 days later or next calendar month)
        val decStart = novStart + 31L * 86_400_000L
        assertTrue(repo.checkMonthlyQuotaReset(decStart), "Quota must auto-reset on new calendar month")
        testScheduler.advanceUntilIdle()

        assertEquals(0, repo.accountTier.value.aiScansUsedThisMonth)
        assertTrue(repo.canUseAiScan(decStart), "Quota should be restored to 50 for the new month")
    }

    @Test
    fun testDateRangeAndSemanticTagsFiltering() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val driver = InMemoryPersistenceDriver()
        val repo = PhotoRepository(driver, scope)
        testScheduler.advanceUntilIdle()

        val now = 1762000000000L
        val oneDayMillis = 86_400_000L

        // Photo 1: Added 2 days ago, has semantic tags: "nhà hàng", "ẩm thực", "phở"
        val photo1 = repo.addPhoto(
            fileName = "screenshot_pho.png",
            filePath = "/screenshots/pho.png",
            title = "Bát Phở Bò",
            isScreenshot = true,
            dateAdded = now - (2 * oneDayMillis)
        )
        // Photo 2: Added 10 days ago (last week/2 weeks ago), has semantic tags: "quán cafe", "đồ uống"
        val photo2 = repo.addPhoto(
            fileName = "screenshot_cafe.png",
            filePath = "/screenshots/cafe.png",
            title = "The Coffee House",
            isScreenshot = true,
            dateAdded = now - (10 * oneDayMillis)
        )
        testScheduler.advanceUntilIdle()

        repo.updatePhotoAiMetadata(
            photoId = photo1.id,
            isScreenshot = true,
            piiScrubbedText = "Quán Phở Thìn Lò Đúc",
            detectedIntent = "SHOPPING",
            suggestedEventTitle = "Hóa đơn Phở Thìn",
            suggestedEventDateMillis = null,
            suggestedEventDescription = "Ăn sáng tại Hà Nội cùng đồng nghiệp",
            semanticTags = listOf("nhà hàng", "ẩm thực", "phở", "quán ăn")
        )

        repo.updatePhotoAiMetadata(
            photoId = photo2.id,
            isScreenshot = true,
            piiScrubbedText = "The Coffee House Order #123",
            detectedIntent = "SHOPPING",
            suggestedEventTitle = "Cà phê gặp bạn",
            suggestedEventDateMillis = null,
            suggestedEventDescription = "Trà sữa mắc ca và americano",
            semanticTags = listOf("quán cafe", "đồ uống", "quán ăn", "cafe")
        )
        testScheduler.advanceUntilIdle()

        // 1. Search by semantic tag "ẩm thực"
        repo.setSearchQuery("ẩm thực")
        testScheduler.advanceUntilIdle()
        val matchFood = repo.filteredPhotos.value
        assertEquals(1, matchFood.size)
        assertEquals(photo1.id, matchFood.first().id)

        // 2. Search by suggestedEventDescription "đồng nghiệp"
        repo.setSearchQuery("đồng nghiệp")
        testScheduler.advanceUntilIdle()
        val matchDesc = repo.filteredPhotos.value
        assertEquals(1, matchDesc.size)
        assertEquals(photo1.id, matchDesc.first().id)

        // 3. Search "quán ăn" matches both photos
        repo.setSearchQuery("quán ăn")
        testScheduler.advanceUntilIdle()
        assertEquals(2, repo.filteredPhotos.value.size)

        // 4. Filter by dateRange (past 3 days: now - 3 days to now)
        val past3DaysRange = (now - 3 * oneDayMillis)..now
        repo.setSearchQuery("quán ăn", dateRange = past3DaysRange)
        testScheduler.advanceUntilIdle()
        val matchPast3Days = repo.filteredPhotos.value
        assertEquals(1, matchPast3Days.size)
        assertEquals(photo1.id, matchPast3Days.first().id)

        // 5. Filter by dateRange matching only photo 2 (past 14 to past 7 days)
        val lastWeekRange = (now - 14 * oneDayMillis)..(now - 7 * oneDayMillis)
        repo.setSearchQuery("quán ăn", dateRange = lastWeekRange)
        testScheduler.advanceUntilIdle()
        val matchLastWeek = repo.filteredPhotos.value
        assertEquals(1, matchLastWeek.size)
        assertEquals(photo2.id, matchLastWeek.first().id)

        // 6. Reset search query and filter
        repo.setSearchQuery("")
        testScheduler.advanceUntilIdle()
        assertEquals(2, repo.filteredPhotos.value.size)
    }

    @Test
    fun testAssignPhotosToCategory() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val driver = InMemoryPersistenceDriver()
        val repo = PhotoRepository(driver, scope)
        testScheduler.advanceUntilIdle()

        repo.toggleUserTier(isPro = true)
        testScheduler.advanceUntilIdle()

        val customCat = repo.createCategory("Đồ gia dụng", colorHex = "#10B981")
        testScheduler.advanceUntilIdle()
        assertNotNull(customCat)

        val photo1 = repo.addPhoto(fileName = "bep1.jpg", filePath = "/p/bep1.jpg", title = "Bếp từ")
        val photo2 = repo.addPhoto(fileName = "bep2.jpg", filePath = "/p/bep2.jpg", title = "Nồi cơm")
        val photo3 = repo.addPhoto(fileName = "ve.jpg", filePath = "/p/ve.jpg", title = "Vé xem phim")
        testScheduler.advanceUntilIdle()

        // Pre-assign photo1 to category
        repo.updatePhotoAiMetadata(photoId = photo1.id, isScreenshot = true, piiScrubbedText = "", detectedIntent = "SHOPPING", targetCategoryId = customCat.id)
        testScheduler.advanceUntilIdle()
        assertTrue(customCat.id in repo.photos.value.first { it.id == photo1.id }.categoryIds)

        // Assign photo1 and photo2 to customCat
        val assignedCount = repo.assignPhotosToCategory(listOf(photo1.id, photo2.id), customCat.id)
        testScheduler.advanceUntilIdle()

        // photo1 already had it, only photo2 is newly assigned
        assertEquals(1, assignedCount)

        val updatedPhoto2 = repo.photos.value.first { it.id == photo2.id }
        val updatedPhoto3 = repo.photos.value.first { it.id == photo3.id }
        assertTrue(customCat.id in updatedPhoto2.categoryIds)
        assertFalse(customCat.id in updatedPhoto3.categoryIds)
    }

    @Test
    fun testReassignPhotosToCategoryReplacesOldCategories() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val driver = InMemoryPersistenceDriver()
        val repo = PhotoRepository(driver, scope)
        testScheduler.advanceUntilIdle()

        repo.toggleUserTier(isPro = true)
        testScheduler.advanceUntilIdle()

        val newCat = repo.createCategory("Thiết bị bếp", colorHex = "#3B82F6")!!
        testScheduler.advanceUntilIdle()

        val photo = repo.addPhoto(fileName = "doc.jpg", filePath = "/p/doc.jpg", title = "Tài liệu đọc")
        testScheduler.advanceUntilIdle()

        // Assign to multiple old categories first (creating clutter)
        repo.updatePhotoAiMetadata(photoId = photo.id, isScreenshot = true, piiScrubbedText = "", detectedIntent = "READING", targetCategoryId = "cat_reading")
        testScheduler.advanceUntilIdle()
        repo.assignPhotosToCategory(listOf(photo.id), "cat_nature")
        testScheduler.advanceUntilIdle()

        val clutteredPhoto = repo.photos.value.first { it.id == photo.id }
        assertTrue("cat_nature" in clutteredPhoto.categoryIds)

        // Reassign to newCat (move exclusively)
        val reassignedCount = repo.reassignPhotosToCategory(listOf(photo.id), newCat.id)
        testScheduler.advanceUntilIdle()

        assertEquals(1, reassignedCount)
        val cleanedPhoto = repo.photos.value.first { it.id == photo.id }
        // Verify only newCat.id is present; old categories are replaced
        assertEquals(setOf(newCat.id), cleanedPhoto.categoryIds)
    }

    @Test
    fun testReassignPhotosPreservesCalendarAndShoppingCapabilities() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val driver = InMemoryPersistenceDriver()
        val repo = PhotoRepository(driver, scope)
        testScheduler.advanceUntilIdle()

        repo.toggleUserTier(isPro = true)
        testScheduler.advanceUntilIdle()

        val targetCat = repo.createCategory("Du lịch hè", colorHex = "#EC4899")!!
        testScheduler.advanceUntilIdle()

        val eventTime = 1750000000000L
        val eventPhoto = repo.addPhoto(fileName = "flight.png", filePath = "/p/flight.png", title = "Vé máy bay")
        val shoppingPhoto = repo.addPhoto(fileName = "bag.png", filePath = "/p/bag.png", title = "Vali kéo")
        testScheduler.advanceUntilIdle()

        // Setup event photo in cat_events with event timestamp
        repo.updatePhotoAiMetadata(
            photoId = eventPhoto.id,
            isScreenshot = true,
            piiScrubbedText = "Flight booking confirmation",
            detectedIntent = "EVENT",
            suggestedEventTitle = "Chuyến bay Hà Nội - Đà Nẵng",
            suggestedEventDateMillis = eventTime,
            targetCategoryId = "cat_events"
        )
        // Setup shopping photo in cat_tobuy
        repo.updatePhotoAiMetadata(
            photoId = shoppingPhoto.id,
            isScreenshot = true,
            piiScrubbedText = "Vali kéo 24 inch",
            detectedIntent = "SHOPPING",
            targetCategoryId = "cat_tobuy"
        )
        testScheduler.advanceUntilIdle()

        // Reassign both photos exclusively to targetCat ("Du lịch hè")
        val count = repo.reassignPhotosToCategory(listOf(eventPhoto.id, shoppingPhoto.id), targetCat.id)
        testScheduler.advanceUntilIdle()
        assertEquals(2, count)

        val updatedEventPhoto = repo.photos.value.first { it.id == eventPhoto.id }
        val updatedShoppingPhoto = repo.photos.value.first { it.id == shoppingPhoto.id }

        // Verify Event Photo:
        // 1. Clutter removed: categoryIds contains ONLY targetCat.id, no longer has "cat_events"
        assertEquals(setOf(targetCat.id), updatedEventPhoto.categoryIds)
        // 2. Calendar capabilities rigorously preserved:
        assertEquals("EVENT", updatedEventPhoto.detectedIntent)
        assertEquals(eventTime, updatedEventPhoto.suggestedEventDateMillis)
        assertEquals("Chuyến bay Hà Nội - Đà Nẵng", updatedEventPhoto.suggestedEventTitle)

        // Verify Shopping Photo:
        // 1. Clutter removed: categoryIds contains ONLY targetCat.id, no longer has "cat_tobuy"
        assertEquals(setOf(targetCat.id), updatedShoppingPhoto.categoryIds)
        // 2. Shopping schedule capability rigorously preserved:
        assertEquals("SHOPPING", updatedShoppingPhoto.detectedIntent)
    }

    @Test
    fun testScreenshotOnlySyncAndManualImportPreservation() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val driver = InMemoryPersistenceDriver()
        val repo = PhotoRepository(driver, scope)
        testScheduler.advanceUntilIdle()

        // 1. Manually imported photo (AES-256 internal encrypted path)
        val manualPhoto = repo.addPhoto(
            fileName = "secure_img_receipt.jpg.enc",
            filePath = "/data/user/0/com.example.test2/files/photos/secure_img_receipt.jpg.enc",
            title = "Receipt",
            isScreenshot = true
        )

        // 2. Simulate legacy device camera photo already in storage from past run
        val legacyCameraPhoto = Photo(
            id = "device_99",
            fileName = "IMG_20260101_123456.jpg",
            filePath = "content://media/external/images/media/99",
            title = "IMG_20260101_123456",
            folderId = "folder_camera",
            dateAdded = 1000L,
            isScreenshot = false
        )
        // Manually inject legacy photo into state to simulate old cached state
        val initialList = repo.photos.value + legacyCameraPhoto
        repo.syncDevicePhotos(emptyList(), emptyList())
        testScheduler.advanceUntilIdle()

        // 3. New device scan: ONLY finds a genuine screenshot on the device
        val newScreenshotPhoto = Photo(
            id = "device_101",
            fileName = "Screenshot_20261113.png",
            filePath = "content://media/external/images/media/101",
            title = "Screenshot_20261113",
            folderId = "folder_screenshots",
            dateAdded = 2000L,
            isScreenshot = true
        )
        val screenshotFolder = Folder(
            id = "folder_screenshots",
            name = "Ảnh chụp màn hình",
            isDefault = true,
            createdAt = 1500L
        )

        repo.syncDevicePhotos(listOf(newScreenshotPhoto), listOf(screenshotFolder))
        testScheduler.advanceUntilIdle()

        val photos = repo.photos.value
        // Verify:
        // - Manual import is PRESERVED
        assertTrue(photos.any { it.filePath == manualPhoto.filePath })
        // - New device screenshot is ACCEPTED
        assertTrue(photos.any { it.filePath == newScreenshotPhoto.filePath })
        // - Old legacy device camera photo is REMOVED
        assertFalse(photos.any { it.filePath == legacyCameraPhoto.filePath })
        assertEquals(2, photos.size)
    }

    @Test
    fun testForceUpdateOverridesEnrichedPhoto() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val driver = InMemoryPersistenceDriver()
        val repo = PhotoRepository(driver, scope)
        testScheduler.advanceUntilIdle()

        // 1. Add photo that is already cloud enriched
        val photo = repo.addPhoto(
            fileName = "Screenshot_Dining.png",
            filePath = "file:///photos/dining.png",
            fileSizeBytes = 2048L,
            dateAdded = 1000L,
            isScreenshot = true
        )
        repo.updatePhotoAiMetadata(
            photoId = photo.id,
            isScreenshot = true,
            piiScrubbedText = "Moo Beef Steak",
            detectedIntent = "EVENT",
            suggestedEventTitle = "Lịch khám bệnh",
            targetCategoryId = "cat_events",
            isCloudEnriched = true
        )
        testScheduler.advanceUntilIdle()

        val initialPhoto = repo.photos.value.first { it.id == photo.id }
        assertTrue(initialPhoto.isCloudEnriched)
        assertEquals("EVENT", initialPhoto.detectedIntent)
        assertTrue(initialPhoto.categoryIds.contains("cat_events"))

        // 2. Perform a forced manual rescan with forceUpdate = true
        repo.updatePhotoAiMetadata(
            photoId = photo.id,
            isScreenshot = true,
            piiScrubbedText = "Moo Beef Steak",
            detectedIntent = "SHOPPING",
            suggestedEventTitle = "Moo Beef Steak Trần Quốc Toản",
            targetCategoryId = "cat_tobuy",
            isCloudEnriched = true,
            forceUpdate = true
        )
        testScheduler.advanceUntilIdle()

        val updatedPhoto = repo.photos.value.first { it.id == photo.id }
        assertEquals("SHOPPING", updatedPhoto.detectedIntent)
        assertEquals("Moo Beef Steak Trần Quốc Toản", updatedPhoto.suggestedEventTitle)
        assertTrue(updatedPhoto.categoryIds.contains("cat_tobuy"))
        assertFalse(updatedPhoto.categoryIds.contains("cat_events"))
    }

    @Test
    fun testDefaultCategoriesMatchRedesignAndMigrateLegacyVisuals() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val driver = InMemoryPersistenceDriver()
        val repo = PhotoRepository(driver, scope)
        testScheduler.advanceUntilIdle()

        val defaults = repo.categories.value.associateBy { it.id }
        assertEquals(5, defaults.size)
        assertEquals("#1E4240" to "tag", defaults["cat_events"]?.let { it.colorHex to it.iconName })
        assertEquals("#B89054" to "utensils", defaults["cat_tobuy"]?.let { it.colorHex to it.iconName })
        assertEquals("#7E8D78" to "building", defaults["cat_reading"]?.let { it.colorHex to it.iconName })
        assertEquals("#5A7364" to "leaf", defaults["cat_nature"]?.let { it.colorHex to it.iconName })
        assertEquals("#7A5C7E" to "user", defaults["cat_personal"]?.let { it.colorHex to it.iconName })

        // Legacy persisted JSON with old pre-redesign default colors/icons should migrate automatically
        val legacyJson = """
            {
              "photos": [],
              "folders": [],
              "categories": [
                {"id": "cat_events", "name": "Lịch & Sự kiện", "colorHex": "#3B82F6", "iconName": "plane", "createdAt": 1000},
                {"id": "cat_tobuy", "name": "Cần mua & Chi tiêu", "colorHex": "#F59E0B", "iconName": "utensils", "createdAt": 2000},
                {"id": "cat_reading", "name": "Đọc & Tham khảo", "colorHex": "#8B5CF6", "iconName": "tag", "createdAt": 3000},
                {"id": "cat_nature", "name": "Thiên nhiên & Phong cảnh", "colorHex": "#009688", "iconName": "leaf", "createdAt": 4000},
                {"id": "cat_personal", "name": "Cá nhân & Giấy tờ", "colorHex": "#EC4899", "iconName": "user", "createdAt": 5000}
              ]
            }
        """.trimIndent()
        val legacyDriver = InMemoryPersistenceDriver().apply { saveData(legacyJson) }
        val migratedRepo = PhotoRepository(legacyDriver, scope)
        testScheduler.advanceUntilIdle()

        val migrated = migratedRepo.categories.value.associateBy { it.id }
        assertEquals("#1E4240" to "tag", migrated["cat_events"]?.let { it.colorHex to it.iconName })
        assertEquals("#B89054" to "utensils", migrated["cat_tobuy"]?.let { it.colorHex to it.iconName })
        assertEquals("#7E8D78" to "building", migrated["cat_reading"]?.let { it.colorHex to it.iconName })
        assertEquals("#5A7364" to "leaf", migrated["cat_nature"]?.let { it.colorHex to it.iconName })
        assertEquals("#7A5C7E" to "user", migrated["cat_personal"]?.let { it.colorHex to it.iconName })
    }

    @Test
    fun testReassignCategoryPersistsAcrossRestartAndBlocksStaleAiMerge() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val driver = InMemoryPersistenceDriver()
        val repo1 = PhotoRepository(driver, scope)
        testScheduler.advanceUntilIdle()

        repo1.toggleUserTier(isPro = true)
        val customCat = repo1.createCategory("Hóa đơn nhà", colorHex = "#10B981")!!
        val photo = repo1.addPhoto(
            fileName = "bill.png",
            filePath = "/p/bill.png",
            title = "Tiền điện tháng 11",
            isScreenshot = true,
            dateAdded = 1_000_000L
        )
        testScheduler.advanceUntilIdle()

        // Initially categorized by AI into default category "cat_tobuy"
        repo1.updatePhotoAiMetadata(
            photoId = photo.id,
            isScreenshot = true,
            piiScrubbedText = "Tổng thanh toán 500k",
            detectedIntent = "SHOPPING",
            targetCategoryId = "cat_tobuy",
            isCloudEnriched = true
        )
        testScheduler.advanceUntilIdle()
        assertEquals(setOf("cat_tobuy"), repo1.photos.value.first().categoryIds)

        // User moves photo into the newly created custom category
        val moved = repo1.reassignPhotosToCategory(listOf(photo.id), customCat.id)
        testScheduler.advanceUntilIdle()
        assertEquals(1, moved)
        val movedPhoto = repo1.photos.value.first()
        assertEquals(setOf(customCat.id), movedPhoto.categoryIds)
        assertTrue(movedPhoto.isCategoryUserModified)

        // Simulate exiting and re-entering the app with a fresh repository reading persisted JSON
        val repo2 = PhotoRepository(driver, scope)
        testScheduler.advanceUntilIdle()
        val reloaded = repo2.photos.value.first()
        assertEquals(setOf(customCat.id), reloaded.categoryIds)
        assertTrue(reloaded.isCategoryUserModified)

        // Simulate stale SQLite sync pushing old "cat_tobuy" back via updatePhotoAiMetadataBatch
        repo2.updatePhotoAiMetadataBatch(
            listOf(
                com.example.test2.shared.repository.PhotoAiMetadataUpdate(
                    photoId = reloaded.id,
                    isScreenshot = true,
                    targetCategoryId = "cat_tobuy",
                    isCloudEnriched = true,
                    suggestedEventDescription = "Enriched description"
                )
            )
        )
        testScheduler.advanceUntilIdle()

        val afterSync = repo2.photos.value.first()
        // Must ONLY have customCat.id, NOT both customCat.id and cat_tobuy
        assertEquals(setOf(customCat.id), afterSync.categoryIds)
        assertEquals("Enriched description", afterSync.suggestedEventDescription)

        // Explicit manual rescan (forceUpdate = true) can still re-assign if requested
        repo2.updatePhotoAiMetadata(
            photoId = reloaded.id,
            isScreenshot = true,
            piiScrubbedText = "Tổng thanh toán 500k",
            detectedIntent = "EVENT",
            targetCategoryId = "cat_events",
            isCloudEnriched = true,
            forceUpdate = true
        )
        testScheduler.advanceUntilIdle()

        val afterManualRescan = repo2.photos.value.first()
        assertEquals(setOf("cat_events"), afterManualRescan.categoryIds)
        assertFalse(afterManualRescan.isCategoryUserModified)
    }
}
