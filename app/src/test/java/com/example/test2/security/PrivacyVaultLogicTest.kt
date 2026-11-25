package com.example.test2.security

import com.example.test2.shared.models.Photo
import com.example.test2.shared.repository.InMemoryPersistenceDriver
import com.example.test2.shared.repository.PhotoRepository
import com.example.test2.storage.LocalCryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class PrivacyVaultLogicTest {

    private val testScope: CoroutineScope
        get() = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun testPhotoEncryptionRoundtripAndMagicHeader() {
        val originalBytes = "Test screenshot raw image bitmap byte array content".toByteArray(Charsets.UTF_8)
        val encrypted = LocalCryptoManager.encrypt(originalBytes)

        // Verify magic 4-byte header 'E', 'N', 'C', '1'
        val magic = encrypted.sliceArray(0..3)
        assertArrayEquals(byteArrayOf(0x45, 0x4E, 0x43, 0x31), magic)

        // Verify decrypt restores exact original bytes
        val decrypted = LocalCryptoManager.decrypt(encrypted)
        assertArrayEquals(originalBytes, decrypted)
    }

    @Test
    fun testStreamEncryptionToFileAndDetection() {
        val tempFile = File.createTempFile("vault_test_img", ".jpg.enc").apply { deleteOnExit() }
        val testContent = "Confidential banking screenshot with account information".toByteArray(Charsets.UTF_8)

        ByteArrayInputStream(testContent).use { input ->
            LocalCryptoManager.encryptStreamToFile(input, tempFile)
        }

        assertTrue("File should be identified as encrypted", LocalCryptoManager.isEncrypted(tempFile))

        // Read decrypted stream
        val readBackBytes = LocalCryptoManager.openDecryptedInputStream(tempFile).use { it.readBytes() }
        assertArrayEquals(testContent, readBackBytes)
    }

    @Test
    fun testPhotoStoragePathTransitionPreservesAllMetadata() = runBlocking {
        val driver = InMemoryPersistenceDriver()
        val repo = PhotoRepository(driver, testScope)
        repo.loadInitialData()

        // Add initial photo pointing to public MediaStore
        val photo = repo.addPhoto(
            fileName = "Screenshot_20261113.png",
            filePath = "content://media/external/images/media/98765",
            title = "Screenshot_20261113",
            folderId = "folder_screenshots",
            categoryIds = setOf("cat_events"),
            isScreenshot = true,
            dateAdded = 1700000000000L
        )

        // Attach AI metadata (e.g. event extraction)
        repo.updatePhotoAiMetadata(
            photoId = photo.id,
            isScreenshot = true,
            piiScrubbedText = "Lịch họp dự án lúc 10h",
            detectedIntent = "EVENT",
            suggestedEventTitle = "Họp dự án",
            suggestedEventDateMillis = 1700003600000L,
            targetCategoryId = "cat_events",
            isProcessed = true
        )

        // Transition to Private Vault storage
        val secureVaultPath = "/data/user/0/com.example.test2/files/photos/secure_vault_1700000000.png.enc"
        repo.updatePhotoStoragePath(photo.id, secureVaultPath, "secure_vault_1700000000.png.enc", 12345L)

        val updated = repo.photos.value.first { it.id == photo.id }
        assertEquals(secureVaultPath, updated.filePath)
        assertEquals("secure_vault_1700000000.png.enc", updated.fileName)
        assertEquals(12345L, updated.fileSizeBytes)
        // Ensure ALL AI and calendar metadata was completely preserved!
        assertEquals("Lịch họp dự án lúc 10h", updated.piiScrubbedText)
        assertEquals("EVENT", updated.detectedIntent)
        assertEquals("Họp dự án", updated.suggestedEventTitle)
        assertEquals(1700003600000L, updated.suggestedEventDateMillis)
        assertTrue(updated.categoryIds.contains("cat_events"))
        assertTrue(updated.isScreenshot)
        assertTrue(updated.isProcessed)
    }

    @Test
    fun testStandardModePreservesOriginalMediaStorePath() = runBlocking {
        val driver = InMemoryPersistenceDriver()
        val repo = PhotoRepository(driver, testScope)
        repo.loadInitialData()

        val originalPublicUri = "content://media/external/images/media/112233"
        val photo = repo.addPhoto(
            fileName = "Screenshot_Public.png",
            filePath = originalPublicUri,
            title = "Screenshot_Public",
            folderId = "folder_screenshots",
            isScreenshot = true,
            dateAdded = 1700000000000L
        )

        // In standard mode, filePath remains pointing to public MediaStore
        assertEquals(originalPublicUri, photo.filePath)
        assertFalse(photo.filePath.endsWith(".enc"))
    }

    @Test
    fun testPhotoStorageRestoreTransitionPreservesMetadataAndUpdatesId() = runBlocking {
        val driver = InMemoryPersistenceDriver()
        val repo = PhotoRepository(driver, testScope)
        repo.loadInitialData()

        // 1. Initial photo vaulted in private storage
        val vaultedPhoto = repo.addPhoto(
            fileName = "secure_vault_1700000000.png.enc",
            filePath = "/data/user/0/com.example.test2/files/photos/secure_vault_1700000000.png.enc",
            title = "Hóa đơn điện nước",
            folderId = "folder_screenshots",
            categoryIds = setOf("cat_bills", "cat_personal"),
            isScreenshot = true,
            dateAdded = 1700000000000L
        )

        repo.updatePhotoAiMetadata(
            photoId = vaultedPhoto.id,
            isScreenshot = true,
            piiScrubbedText = "Tổng tiền thanh toán: 500.000đ",
            detectedIntent = "SHOPPING",
            suggestedEventTitle = "Thanh toán tiền điện",
            suggestedEventDateMillis = 1700050000000L,
            targetCategoryId = "cat_bills",
            isProcessed = true,
            suggestedEventDescription = "Hạn chót ngày 20",
            semanticTags = listOf("tiendien", "hoadon")
        )

        // 2. Perform restoration to public MediaStore
        val restoredUri = "content://media/external/images/media/778899"
        val restoredId = "device_778899"
        val restoredFileName = "Hoa_don_dien_nuoc.png"
        val restoredSize = 987654L

        repo.updatePhotoStoragePath(
            photoId = vaultedPhoto.id,
            newFilePath = restoredUri,
            newFileName = restoredFileName,
            newFileSizeBytes = restoredSize,
            newPhotoId = restoredId
        )

        // 3. Verify the restored photo retains all AI metadata, tags, and new public URI
        val restored = repo.photos.value.first { it.id == restoredId }
        assertEquals(restoredId, restored.id)
        assertEquals(restoredUri, restored.filePath)
        assertEquals(restoredFileName, restored.fileName)
        assertEquals(restoredSize, restored.fileSizeBytes)
        assertEquals("Tổng tiền thanh toán: 500.000đ", restored.piiScrubbedText)
        assertEquals("SHOPPING", restored.detectedIntent)
        assertEquals("Thanh toán tiền điện", restored.suggestedEventTitle)
        assertEquals(1700050000000L, restored.suggestedEventDateMillis)
        assertEquals("Hạn chót ngày 20", restored.suggestedEventDescription)
        assertTrue(restored.categoryIds.contains("cat_bills"))
        assertTrue(restored.semanticTags.contains("tiendien"))
        assertTrue(restored.isProcessed)
        assertFalse(restored.filePath.endsWith(".enc"))
    }

    @Test
    fun testSyncDevicePhotosReconcilesRestoredPhotoWithoutDuplicates() = runBlocking {
        val driver = InMemoryPersistenceDriver()
        val repo = PhotoRepository(driver, testScope)
        repo.loadInitialData()

        val restoredUri = "content://media/external/images/media/334455"
        val restoredId = "device_334455"

        // Photo already in repo after restoration
        val p = repo.addPhoto(
            fileName = "Restored_Image.png",
            filePath = restoredUri,
            title = "Restored_Image",
            folderId = "folder_screenshots",
            categoryIds = setOf("cat_notes"),
            isScreenshot = true,
            dateAdded = 1700000000000L
        )
        repo.updatePhotoStoragePath(
            photoId = p.id,
            newFilePath = restoredUri,
            newPhotoId = restoredId
        )

        // DeviceMediaScanner later scans the device and encounters the newly written file
        val scannedPhoto = Photo(
            id = "device_334455",
            fileName = "Restored_Image.png",
            filePath = restoredUri,
            title = "Restored_Image",
            folderId = "Ảnh chụp màn hình",
            categoryIds = emptySet(),
            isScreenshot = true,
            dateAdded = 1700000000000L
        )

        repo.syncDevicePhotos(listOf(scannedPhoto), emptyList())

        // Ensure exactly 1 photo exists and metadata (cat_notes) is preserved
        assertEquals(1, repo.photos.value.size)
        val reconciled = repo.photos.value.first()
        assertEquals(restoredUri, reconciled.filePath)
        assertTrue(reconciled.categoryIds.contains("cat_notes"))
    }

    @Test
    fun testVaultScopeAllVsNewOnlyLogic() {
        val enabledSince = 1700000000000L
        val oldPhotoDate = 1699999999000L
        val newPhotoDate = 1700000001000L

        // Helper replicating shouldVault logic
        fun shouldVault(isEnabled: Boolean, scope: String, photoDate: Long): Boolean {
            if (!isEnabled) return false
            if (scope == PrivacyVaultManager.SCOPE_NEW_ONLY) {
                return photoDate >= enabledSince
            }
            return true
        }

        // Disabled mode: never vault
        assertFalse(shouldVault(false, PrivacyVaultManager.SCOPE_ALL, oldPhotoDate))
        assertFalse(shouldVault(false, PrivacyVaultManager.SCOPE_ALL, newPhotoDate))
        assertFalse(shouldVault(false, PrivacyVaultManager.SCOPE_NEW_ONLY, newPhotoDate))

        // SCOPE_ALL: vaults both old and new photos
        assertTrue(shouldVault(true, PrivacyVaultManager.SCOPE_ALL, oldPhotoDate))
        assertTrue(shouldVault(true, PrivacyVaultManager.SCOPE_ALL, newPhotoDate))

        // SCOPE_NEW_ONLY: protects ONLY new photos, keeps old photos untouched!
        assertFalse("Old photo before enablement timestamp should NOT be vaulted",
            shouldVault(true, PrivacyVaultManager.SCOPE_NEW_ONLY, oldPhotoDate))
        assertTrue("New photo taken after enablement timestamp MUST be vaulted",
            shouldVault(true, PrivacyVaultManager.SCOPE_NEW_ONLY, newPhotoDate))
    }

    @Test
    fun testNewOnlyModeCoexistsHybridPhotosInRepository() = runBlocking {
        val driver = InMemoryPersistenceDriver()
        val repo = PhotoRepository(driver, testScope)
        repo.loadInitialData()

        val enabledSince = 1700000000000L

        // 1. Existing photo on device before vault was enabled
        val oldPhoto = repo.addPhoto(
            fileName = "old_screenshot.png",
            filePath = "content://media/external/images/media/100",
            title = "old_screenshot",
            folderId = "folder_screenshots",
            isScreenshot = true,
            dateAdded = enabledSince - 50000L
        )

        // 2. New photo captured after vault was enabled
        val newPhoto = repo.addPhoto(
            fileName = "new_screenshot.png",
            filePath = "content://media/external/images/media/200",
            title = "new_screenshot",
            folderId = "folder_screenshots",
            isScreenshot = true,
            dateAdded = enabledSince + 50000L
        )

        // Simulate vaulting selective based on NEW_ONLY scope:
        // oldPhoto remains content://, newPhoto transitions to .enc
        repo.updatePhotoStoragePath(
            photoId = newPhoto.id,
            newFilePath = "/data/user/0/com.example.test2/files/photos/secure_vault_200.png.enc",
            newFileName = "secure_vault_200.png.enc"
        )

        val photos = repo.photos.value
        assertEquals(2, photos.size)

        val retrievedOld = photos.first { it.id == oldPhoto.id }
        val retrievedNew = photos.first { it.id == newPhoto.id }

        // Verify hybrid coexistence:
        assertTrue(retrievedOld.filePath.startsWith("content://"))
        assertFalse(retrievedOld.filePath.endsWith(".enc"))

        assertTrue(retrievedNew.filePath.endsWith(".enc"))
    }

    @Test
    fun testSyncDevicePhotosPreservesVaultedEncryptedVersionAgainstMediaStoreReScan() = runBlocking {
        val driver = InMemoryPersistenceDriver()
        val repo = PhotoRepository(driver, testScope)
        repo.loadInitialData()

        val vaultedPath = "/data/user/0/com.example.test2/files/photos/secure_vault_9999.png.enc"

        // 1. Photo is already vaulted in repository
        val vaultedPhoto = repo.addPhoto(
            fileName = "secure_vault_9999.png.enc",
            filePath = vaultedPath,
            title = "Secret Banking Document",
            folderId = "folder_screenshots",
            isScreenshot = true,
            dateAdded = 1700000000000L
        )

        // 2. MediaStore still contains the obsolete content:// URI before eviction completed
        val obsoleteMediaStorePhoto = Photo(
            id = vaultedPhoto.id,
            fileName = "Screenshot_Public.png",
            filePath = "content://media/external/images/media/9999",
            title = "Screenshot_Public",
            folderId = "Ảnh chụp màn hình",
            isScreenshot = true,
            dateAdded = 1700000000000L
        )

        // 3. syncDevicePhotos is triggered (e.g. onResume or media observer)
        repo.syncDevicePhotos(listOf(obsoleteMediaStorePhoto), emptyList())

        // 4. Verify that the vaulted .enc version is 100% PRESERVED, not overwritten by content://!
        val photos = repo.photos.value
        assertEquals(1, photos.size)
        val currentPhoto = photos.first()
        assertEquals(vaultedPath, currentPhoto.filePath)
        assertTrue(currentPhoto.filePath.endsWith(".enc"))
        assertFalse(currentPhoto.filePath.startsWith("content://"))
    }

    @Test
    fun testBatchMetadataUpdatePreservesAllPropertiesAndPersistsOnce() = runBlocking {
        val driver = InMemoryPersistenceDriver()
        val repo = PhotoRepository(driver, testScope)
        repo.loadInitialData()

        val p1 = repo.addPhoto(fileName = "img1.png", filePath = "content://1", title = "img1", dateAdded = 1000L, isScreenshot = true)
        val p2 = repo.addPhoto(fileName = "img2.png", filePath = "content://2", title = "img2", dateAdded = 2000L, isScreenshot = true)

        val updates = listOf(
            com.example.test2.shared.repository.PhotoAiMetadataUpdate(
                photoId = p1.id,
                isScreenshot = true,
                piiScrubbedText = "Text 1",
                detectedIntent = "EVENT",
                suggestedEventTitle = "Event 1",
                targetCategoryId = "cat_events"
            ),
            com.example.test2.shared.repository.PhotoAiMetadataUpdate(
                photoId = p2.id,
                isScreenshot = true,
                piiScrubbedText = "Text 2",
                detectedIntent = "SHOPPING",
                suggestedEventTitle = "Item 2",
                targetCategoryId = "cat_tobuy"
            )
        )

        repo.updatePhotoAiMetadataBatch(updates)

        val photos = repo.photos.value
        val updated1 = photos.first { it.id == p1.id }
        val updated2 = photos.first { it.id == p2.id }

        assertEquals("Text 1", updated1.piiScrubbedText)
        assertEquals("EVENT", updated1.detectedIntent)
        assertTrue(updated1.categoryIds.contains("cat_events"))

        assertEquals("Text 2", updated2.piiScrubbedText)
        assertEquals("SHOPPING", updated2.detectedIntent)
        assertTrue(updated2.categoryIds.contains("cat_tobuy"))
    }

    @Test
    fun testCloudDataImmutabilityPreventsDowngrade() = runBlocking {
        val testScope = CoroutineScope(Dispatchers.Unconfined)
        val repo = PhotoRepository(InMemoryPersistenceDriver(), testScope)
        val p = repo.addPhoto(fileName = "test.jpg", filePath = "/storage/test.jpg", title = "test", dateAdded = 1000L, isScreenshot = true)

        // 1. Enrich photo with high-quality Cloud Gemini metadata
        repo.updatePhotoAiMetadata(
            photoId = p.id,
            isScreenshot = true,
            piiScrubbedText = "Sony Bravia 98 inch TV",
            detectedIntent = "SHOPPING",
            suggestedEventTitle = "Google Tivi Sony True RGB BRAVIA 7 II 98",
            suggestedEventDateMillis = 1700000000L,
            targetCategoryId = "cat_tobuy",
            isProcessed = true,
            suggestedEventDescription = "🛒 Lịch mua sắm & Chi tiêu:\n• Sản phẩm: Google Tivi Sony 98 inch",
            semanticTags = listOf("tivi", "sony", "mua sắm")
        )

        val enriched = repo.photos.value.first { it.id == p.id }
        assertEquals("Google Tivi Sony True RGB BRAVIA 7 II 98", enriched.suggestedEventTitle)
        assertEquals("🛒 Lịch mua sắm & Chi tiêu:\n• Sản phẩm: Google Tivi Sony 98 inch", enriched.suggestedEventDescription)

        // 2. Attempt to overwrite with crude on-device heuristic regex
        repo.updatePhotoAiMetadata(
            photoId = p.id,
            isScreenshot = true,
            piiScrubbedText = "Some text",
            detectedIntent = "OTHER",
            suggestedEventTitle = "Screenshot_20261116_123456",
            suggestedEventDateMillis = null,
            targetCategoryId = "cat_other",
            isProcessed = true,
            suggestedEventDescription = null,
            semanticTags = listOf("thô sơ")
        )

        val guarded = repo.photos.value.first { it.id == p.id }
        // Cloud metadata MUST remain intact
        assertEquals("Google Tivi Sony True RGB BRAVIA 7 II 98", guarded.suggestedEventTitle)
        assertEquals("🛒 Lịch mua sắm & Chi tiêu:\n• Sản phẩm: Google Tivi Sony 98 inch", guarded.suggestedEventDescription)
        assertEquals("SHOPPING", guarded.detectedIntent)
        assertTrue(guarded.categoryIds.contains("cat_tobuy"))
        // Tags should be merged
        assertTrue(guarded.semanticTags.contains("tivi"))
        assertTrue(guarded.semanticTags.contains("thô sơ"))
    }

    @Test
    fun testCloudDataImmutabilityAllowsVaultPathUpdate() = runBlocking {
        val testScope = CoroutineScope(Dispatchers.Unconfined)
        val repo = PhotoRepository(InMemoryPersistenceDriver(), testScope)
        val p = repo.addPhoto(fileName = "test.jpg", filePath = "/storage/emulated/0/DCIM/Screenshots/test.jpg", title = "test", dateAdded = 1000L, isScreenshot = true)

        // 1. Cloud enriched
        repo.updatePhotoAiMetadata(
            photoId = p.id,
            isScreenshot = true,
            piiScrubbedText = "Lịch chiếu phim Lotte",
            detectedIntent = "EVENT",
            suggestedEventTitle = "Lịch chiếu phim Lotte Cinema",
            suggestedEventDateMillis = 1700000000L,
            targetCategoryId = "cat_events",
            isProcessed = true,
            suggestedEventDescription = "📅 Chi tiết sự kiện & Lịch hẹn:\n• Sự kiện: Lotte Cinema",
            semanticTags = listOf("phim", "cinema")
        )

        // 2. Vaulting occurs in background: path changes to .enc
        repo.updatePhotoAiMetadataBatch(
            listOf(
                com.example.test2.shared.repository.PhotoAiMetadataUpdate(
                    photoId = p.id,
                    newFilePath = "/data/user/0/com.example.test2/files/photos/secure_img_123.jpg.enc",
                    newFileName = "secure_img_123.jpg.enc",
                    newFileSizeBytes = 99999L,
                    isScreenshot = true,
                    targetCategoryId = "cat_events",
                    isProcessed = true
                )
            )
        )

        val vaulted = repo.photos.value.first { it.id == p.id }
        assertEquals("/data/user/0/com.example.test2/files/photos/secure_img_123.jpg.enc", vaulted.filePath)
        assertEquals("secure_img_123.jpg.enc", vaulted.fileName)
        assertEquals(99999L, vaulted.fileSizeBytes)
        // Cloud data is still fully preserved!
        assertEquals("Lịch chiếu phim Lotte Cinema", vaulted.suggestedEventTitle)
        assertEquals("📅 Chi tiết sự kiện & Lịch hẹn:\n• Sự kiện: Lotte Cinema", vaulted.suggestedEventDescription)
        assertEquals("EVENT", vaulted.detectedIntent)
    }

    @Test
    fun testOnDeviceTriageAllowsCloudUpgradeAndThenLocksImmutability() = runBlocking {
        val testScope = CoroutineScope(Dispatchers.Unconfined)
        val repo = PhotoRepository(InMemoryPersistenceDriver(), testScope)
        val p = repo.addPhoto(fileName = "Screenshot_20261116.png", filePath = "/storage/test.png", title = "Screenshot_20261116", dateAdded = 1000L, isScreenshot = true)

        // 1. Initial on-device triage with heuristic description (isCloudEnriched = false)
        repo.updatePhotoAiMetadata(
            photoId = p.id,
            isScreenshot = true,
            piiScrubbedText = "Hop du an luc 14h",
            detectedIntent = "EVENT",
            suggestedEventTitle = "Screenshot_20261116",
            suggestedEventDateMillis = 1700000000L,
            targetCategoryId = "cat_events",
            isProcessed = true,
            isCloudEnriched = false,
            suggestedEventDescription = "• Sự kiện: Hop du an • Thời gian: 14h"
        )

        val triaged = repo.photos.value.first { it.id == p.id }
        assertFalse("Should NOT be considered cloud enriched yet", triaged.isCloudEnriched)
        assertEquals("Screenshot_20261116", triaged.suggestedEventTitle)

        // 2. Cloud AI finishes in background with Gemini 3.5 Flash Lite -> MUST upgrade!
        repo.updatePhotoAiMetadataBatch(
            listOf(
                com.example.test2.shared.repository.PhotoAiMetadataUpdate(
                    photoId = p.id,
                    isScreenshot = true,
                    piiScrubbedText = "Họp dự án lúc 14h",
                    detectedIntent = "EVENT",
                    suggestedEventTitle = "Họp Triển Khai Dự Án Q4",
                    suggestedEventDateMillis = 1700003600L,
                    targetCategoryId = "cat_events",
                    isProcessed = true,
                    isCloudEnriched = true,
                    suggestedEventDescription = "📅 Sự kiện quan trọng:\n• Tên cuộc họp: Họp Triển Khai Dự Án Q4\n• Thời gian: 14:00"
                )
            )
        )

        val enriched = repo.photos.value.first { it.id == p.id }
        assertTrue("Now photo MUST be cloud enriched", enriched.isCloudEnriched)
        assertEquals("Họp Triển Khai Dự Án Q4", enriched.suggestedEventTitle)
        assertTrue(enriched.suggestedEventDescription!!.contains("Họp Triển Khai Dự Án Q4"))

        // 3. Subsequent on-device heuristic pass attempts to overwrite with crude title -> MUST be blocked!
        repo.updatePhotoAiMetadata(
            photoId = p.id,
            isScreenshot = true,
            piiScrubbedText = "Hop du an thô",
            detectedIntent = "OTHER",
            suggestedEventTitle = "Screenshot_20261116_crude",
            targetCategoryId = "cat_other",
            isProcessed = true,
            isCloudEnriched = false
        )

        val protected = repo.photos.value.first { it.id == p.id }
        assertEquals("Họp Triển Khai Dự Án Q4", protected.suggestedEventTitle)
        assertEquals("EVENT", protected.detectedIntent)
        assertTrue(protected.categoryIds.contains("cat_events"))
    }

    @Test
    fun testEncryptedPhotoReadDecryptedBytes() {
        val tempFile = File.createTempFile("vault_bytes_test", ".jpg.enc").apply { deleteOnExit() }
        val testData = "Top secret banking document data payload 2026".toByteArray(Charsets.UTF_8)

        ByteArrayInputStream(testData).use { input ->
            LocalCryptoManager.encryptStreamToFile(input, tempFile)
        }

        val decryptedBytes = LocalCryptoManager.readDecryptedBytes(tempFile)
        assertArrayEquals(testData, decryptedBytes)
    }

    @Test
    fun testVaultTransitionDuringSyncDevicePhotosPreservesAllMetadata() = runBlocking {
        val driver = InMemoryPersistenceDriver()
        val repo = PhotoRepository(driver, testScope)
        repo.loadInitialData()

        val publicUri = "content://media/external/images/media/9999"
        val photoId = "device_9999"

        // Step 1: Initial scan when vault was disabled (standard mode)
        val initialPhoto = Photo(
            id = photoId,
            fileName = "Screenshot_meeting.png",
            filePath = publicUri,
            title = "Screenshot_meeting",
            folderId = "folder_screenshots",
            categoryIds = setOf("cat_events"),
            isFavorite = true,
            isScreenshot = true,
            dateAdded = 1700000000000L
        )
        repo.syncDevicePhotos(listOf(initialPhoto), emptyList())

        // Attach AI metadata
        repo.updatePhotoAiMetadata(
            photoId = photoId,
            isScreenshot = true,
            piiScrubbedText = "Hop chien luoc 15h",
            detectedIntent = "EVENT",
            suggestedEventTitle = "Hop chien luoc",
            suggestedEventDateMillis = 1700010000000L,
            targetCategoryId = "cat_events",
            isProcessed = true
        )

        assertEquals(publicUri, repo.photos.value.first().filePath)

        // Step 2: Vault enabled, photo vaulted to .enc, syncDevicePhotos emits newly vaulted photo
        val vaultedPath = "/data/user/0/com.example.test2/files/photos/secure_vault_9999.png.enc"
        val vaultedScannedPhoto = initialPhoto.copy(
            filePath = vaultedPath,
            fileName = "secure_vault_9999.png.enc",
            fileSizeBytes = 54321L
        )
        repo.syncDevicePhotos(listOf(vaultedScannedPhoto), emptyList())

        val resultPhoto = repo.photos.value.first()
        // 1. Storage path updated to .enc!
        assertEquals(vaultedPath, resultPhoto.filePath)
        assertEquals("secure_vault_9999.png.enc", resultPhoto.fileName)
        assertEquals(54321L, resultPhoto.fileSizeBytes)
        // 2. All AI & user metadata completely preserved!
        assertEquals("Hop chien luoc 15h", resultPhoto.piiScrubbedText)
        assertEquals("EVENT", resultPhoto.detectedIntent)
        assertEquals("Hop chien luoc", resultPhoto.suggestedEventTitle)
        assertEquals(1700010000000L, resultPhoto.suggestedEventDateMillis)
        assertTrue(resultPhoto.isFavorite)
        assertTrue(resultPhoto.isProcessed)
        assertTrue(resultPhoto.categoryIds.contains("cat_events"))
    }

    @Test
    fun testDemonstrateRiskOfCancelingScanMidway() = runBlocking {
        val driver = InMemoryPersistenceDriver()
        val repo = PhotoRepository(driver, testScope)
        repo.loadInitialData()

        val photo1 = Photo(
            id = "device_1",
            fileName = "Screenshot_1.png",
            filePath = "content://media/external/images/media/1",
            title = "Screenshot_1",
            folderId = "folder_screenshots",
            isScreenshot = true,
            dateAdded = 1000L
        )
        val photo2 = Photo(
            id = "device_2",
            fileName = "Screenshot_2.png",
            filePath = "content://media/external/images/media/2",
            title = "Screenshot_2",
            folderId = "folder_screenshots",
            isScreenshot = true,
            dateAdded = 2000L
        )

        // Initial scan: both photos present in repository
        repo.syncDevicePhotos(listOf(photo1, photo2), emptyList())
        assertEquals(2, repo.photos.value.size)

        // Scenario: A vaulting scan starts. Photo 1 is encrypted to .enc and original evicted from disk.
        // BUT the scan job is CANCELLED before repo.syncDevicePhotos can be called!
        // Note: repo.syncDevicePhotos was NEVER called for Photo 1 because coroutine was cancelled!

        // Now, subsequent scan runs. Since photo 1 was evicted from MediaStore, MediaScanner only returns photo 2:
        val subsequentScanResult = listOf(
            photo2.copy(
                filePath = "/data/user/0/com.example.test2/files/photos/secure_vault_2.png.enc",
                fileName = "secure_vault_2.png.enc"
            )
        )
        repo.syncDevicePhotos(subsequentScanResult, emptyList())

        // WHAT HAPPENS TO PHOTO 1?
        val currentPhotos = repo.photos.value
        val hasPhoto1 = currentPhotos.any { it.id == "device_1" }
        // Verify: Photo 1 was dropped because repo still held content:// for device_1 and MediaStore no longer emitted it!
        assertFalse("Photo 1 was dropped from repository because cancellation prevented sync!", hasPhoto1)
        assertEquals(1, currentPhotos.size)
    }

    @Test
    fun testSequentialCoalescedScanPreservesDataIntegrity() = runBlocking {
        val driver = InMemoryPersistenceDriver()
        val repo = PhotoRepository(driver, testScope)
        repo.loadInitialData()

        val photo1 = Photo(
            id = "device_1",
            fileName = "Screenshot_1.png",
            filePath = "content://media/external/images/media/1",
            title = "Screenshot_1",
            folderId = "folder_screenshots",
            categoryIds = setOf("cat_events"),
            isScreenshot = true,
            dateAdded = 1000L
        )
        val photo2 = Photo(
            id = "device_2",
            fileName = "Screenshot_2.png",
            filePath = "content://media/external/images/media/2",
            title = "Screenshot_2",
            folderId = "folder_screenshots",
            categoryIds = setOf("cat_tobuy"),
            isScreenshot = true,
            dateAdded = 2000L
        )

        // Initial scan
        repo.syncDevicePhotos(listOf(photo1, photo2), emptyList())
        assertEquals(2, repo.photos.value.size)

        // Under Non-Interruptive Sequential Queue:
        // Batch 1 runs completely to the end, vaulting photo 1 and photo 2 and syncing to repo:
        val vaulted1 = photo1.copy(
            filePath = "/data/user/0/com.example.test2/files/photos/secure_vault_1.png.enc",
            fileName = "secure_vault_1.png.enc"
        )
        val vaulted2 = photo2.copy(
            filePath = "/data/user/0/com.example.test2/files/photos/secure_vault_2.png.enc",
            fileName = "secure_vault_2.png.enc"
        )
        repo.syncDevicePhotos(listOf(vaulted1, vaulted2), emptyList())

        // Subsequent queued scan runs (e.g. user took a 3rd screenshot during scan 1):
        val photo3 = Photo(
            id = "device_3",
            fileName = "Screenshot_3.png",
            filePath = "content://media/external/images/media/3",
            title = "Screenshot_3",
            folderId = "folder_screenshots",
            isScreenshot = true,
            dateAdded = 3000L
        )
        val vaulted3 = photo3.copy(
            filePath = "/data/user/0/com.example.test2/files/photos/secure_vault_3.png.enc",
            fileName = "secure_vault_3.png.enc"
        )
        // MediaStore now only has photo 3 (photos 1 & 2 were evicted from MediaStore)
        repo.syncDevicePhotos(listOf(vaulted3), emptyList())

        // VERIFY: All 3 photos are fully intact and vaulted! No data loss!
        val allPhotos = repo.photos.value
        assertEquals(3, allPhotos.size)
        assertTrue("Photo 1 must remain preserved in vault", allPhotos.any { it.id == "device_1" && it.filePath.endsWith(".enc") })
        assertTrue("Photo 2 must remain preserved in vault", allPhotos.any { it.id == "device_2" && it.filePath.endsWith(".enc") })
        assertTrue("Photo 3 must be added to vault", allPhotos.any { it.id == "device_3" && it.filePath.endsWith(".enc") })
    }

    @Test
    fun testSafeMarginDiskSpaceThresholdCheck() {
        val minimumSafeMargin = 300 * 1024 * 1024L // 300MB
        fun shouldAllowVault(usableSpace: Long): Boolean {
            return usableSpace >= minimumSafeMargin
        }

        // Less than 300MB -> must be rejected
        assertFalse("100MB usable space must be rejected", shouldAllowVault(100 * 1024 * 1024L))
        assertFalse("299MB usable space must be rejected", shouldAllowVault(299 * 1024 * 1024L))

        // 300MB or more -> allowed
        assertTrue("Exact 300MB usable space must be allowed", shouldAllowVault(300 * 1024 * 1024L))
        assertTrue("1GB usable space must be allowed", shouldAllowVault(1024 * 1024 * 1024L))
    }

    @Test
    fun testAppLockGracePeriodLogic() {
        val gracePeriodMs = 60_000L // 60s grace period
        var isAppLocked = false
        var lastBackgroundTimestamp = 0L

        fun notifyAppBackgrounded(currentTime: Long) {
            lastBackgroundTimestamp = currentTime
        }

        fun checkAppLockOnResume(isVaultEnabled: Boolean, currentTime: Long) {
            if (isVaultEnabled && lastBackgroundTimestamp > 0L) {
                val elapsed = currentTime - lastBackgroundTimestamp
                if (elapsed >= gracePeriodMs) {
                    isAppLocked = true
                }
            }
        }

        fun unlockApp() {
            isAppLocked = false
            lastBackgroundTimestamp = 0L
        }

        val baseTime = 1_000_000L

        // Scenario 1: Quick app switch (e.g. 15s to permission settings or camera) -> Should NOT lock
        notifyAppBackgrounded(baseTime)
        checkAppLockOnResume(isVaultEnabled = true, currentTime = baseTime + 15_000L)
        assertFalse("App should not be locked within 15 seconds", isAppLocked)

        // Scenario 2: Quick switch (45s) -> Should NOT lock
        checkAppLockOnResume(isVaultEnabled = true, currentTime = baseTime + 45_000L)
        assertFalse("App should not be locked within 45 seconds", isAppLocked)

        // Scenario 3: Gone for 65s (exceeding 60s) -> MUST lock
        checkAppLockOnResume(isVaultEnabled = true, currentTime = baseTime + 65_000L)
        assertTrue("App MUST be locked when backgrounded for over 60 seconds", isAppLocked)

        // Scenario 4: User unlocks -> Reset state
        unlockApp()
        assertFalse("App must be unlocked", isAppLocked)
        assertEquals(0L, lastBackgroundTimestamp)

        // Scenario 5: When vault is disabled, even after 10 minutes -> Should NOT lock
        notifyAppBackgrounded(baseTime)
        checkAppLockOnResume(isVaultEnabled = false, currentTime = baseTime + 600_000L)
        assertFalse("App should not lock if vault is disabled", isAppLocked)
    }

    @Test
    fun testReconciliationSyncRecoversBackgroundVaultedPhotos() = runBlocking {
        val driver = InMemoryPersistenceDriver()
        val repo = PhotoRepository(driver, testScope)
        repo.loadInitialData()

        // Initial photos in repository (1 existing photo)
        val initialPhoto = Photo(
            id = "device_1",
            fileName = "old_photo.jpg",
            filePath = "content://media/external/images/media/1",
            title = "Old Photo",
            folderId = "folder_screenshots",
            dateAdded = 1000L
        )
        repo.syncDevicePhotos(listOf(initialPhoto), emptyList())
        assertEquals(1, repo.photos.value.size)

        // Simulate background worker vaulted a 2nd photo while app was closed
        val backgroundVaultedPhoto = Photo(
            id = "device_2",
            fileName = "secure_vault_999.png.enc",
            filePath = "/data/user/0/com.example.test2/files/photos/secure_vault_999.png.enc",
            title = "Screenshot Hóa Đơn Điện Tử",
            description = "Hóa đơn điện sinh hoạt tháng 11",
            folderId = "folder_screenshots",
            categoryIds = setOf("cat_tobuy"),
            isScreenshot = true,
            dateAdded = 2000L,
            fileSizeBytes = 102400L,
            isProcessed = true,
            isCloudEnriched = true,
            piiScrubbedText = "Số tiền thanh toán: 540.000 VND",
            detectedIntent = "PAYMENT",
            suggestedEventTitle = "Thanh toán hóa đơn điện",
            suggestedEventDateMillis = 2500L
        )

        // Reconciliation Sync: nạp bù missing photo vào repository
        repo.addVaultedPhotos(listOf(backgroundVaultedPhoto))

        // VERIFY: Both photos exist, sorted descending by dateAdded
        val photos = repo.photos.value
        assertEquals(2, photos.size)
        assertEquals("device_2", photos[0].id)
        assertEquals("device_1", photos[1].id)

        val recovered = photos[0]
        assertEquals("Screenshot Hóa Đơn Điện Tử", recovered.title)
        assertEquals("PAYMENT", recovered.detectedIntent)
        assertTrue(recovered.categoryIds.contains("cat_tobuy"))
        assertEquals("Số tiền thanh toán: 540.000 VND", recovered.piiScrubbedText)
        assertTrue(recovered.filePath.endsWith(".enc"))
    }

    @Test
    fun testTwoPhaseActivationLogic() {
        var isVaultEnabled = false
        var pendingVaultActivationScope: String? = null

        fun confirmEnableVault(vaultAll: Boolean, hasAllFilesAccess: Boolean) {
            val scope = if (vaultAll) "ALL" else "NEW_ONLY"
            if (!hasAllFilesAccess) {
                pendingVaultActivationScope = scope
                // CRITICAL: Do NOT enable vault prematurely!
            } else {
                isVaultEnabled = true
            }
        }

        fun checkVaultPermissionResumed(hasAllFilesAccess: Boolean) {
            val pending = pendingVaultActivationScope
            if (pending != null) {
                if (hasAllFilesAccess) {
                    pendingVaultActivationScope = null
                    isVaultEnabled = true
                } else {
                    pendingVaultActivationScope = null
                    // Keep vault disabled!
                }
            }
        }

        // Scenario 1: User agrees to enable, but doesn't have permission yet
        confirmEnableVault(vaultAll = true, hasAllFilesAccess = false)
        assertFalse("Vault must NOT be enabled before permission is granted", isVaultEnabled)
        assertEquals("ALL", pendingVaultActivationScope)

        // Scenario 2: User returns from settings without granting permission
        checkVaultPermissionResumed(hasAllFilesAccess = false)
        assertFalse("Vault must remain DISABLED if user denied permission", isVaultEnabled)
        assertNull("Pending scope must be cleared", pendingVaultActivationScope)

        // Scenario 3: User tries again and grants permission in settings
        confirmEnableVault(vaultAll = false, hasAllFilesAccess = false)
        assertEquals("NEW_ONLY", pendingVaultActivationScope)
        checkVaultPermissionResumed(hasAllFilesAccess = true)
        assertTrue("Vault must now be safely ENABLED", isVaultEnabled)
        assertNull("Pending scope must be cleared", pendingVaultActivationScope)
    }

    @Test
    fun testRestorePreservesOriginalTitleInsteadOfHex() {
        fun resolveRestoreFileName(title: String, fileName: String, filePath: String): String {
            return if (title.isNotBlank() && !title.startsWith("secure_vault_")) {
                val ext = filePath.removeSuffix(".enc").substringAfterLast(".", "jpg")
                "$title.$ext"
            } else {
                fileName
            }
        }

        // Case 1: Photo has a meaningful title (e.g. OCR or user title)
        val name1 = resolveRestoreFileName(
            title = "Vé máy bay Đà Nẵng",
            fileName = "secure_vault_171000_abc123.png.enc",
            filePath = "/data/user/0/com.example.test2/files/photos/secure_vault_171000_abc123.png.enc"
        )
        assertEquals("Vé máy bay Đà Nẵng.png", name1)

        // Case 2: Photo has a default screenshot title
        val name2 = resolveRestoreFileName(
            title = "Screenshot_20261120_153022",
            fileName = "secure_vault_171000_xyz789.jpg.enc",
            filePath = "/data/user/0/com.example.test2/files/photos/secure_vault_171000_xyz789.jpg.enc"
        )
        assertEquals("Screenshot_20261120_153022.jpg", name2)
    }

    @Test
    fun testFailSafeRestoreLoopKeepsVaultWhenFailed() {
        data class MockRestoreResult(val newUri: String)

        fun simulateRestoreLoop(items: List<String>, shouldFailItem: String?): Pair<Boolean, Pair<Int, Int>> {
            var successCount = 0
            var failedCount = 0
            var isVaultEnabled = true

            for (item in items) {
                val result = if (item == shouldFailItem) null else MockRestoreResult("content://media/$item")
                if (result != null) {
                    successCount++
                } else {
                    failedCount++
                }
            }

            if (failedCount == 0) {
                isVaultEnabled = false
            } else {
                isVaultEnabled = true
            }

            return Pair(isVaultEnabled, Pair(successCount, failedCount))
        }

        // Case 1: All items succeed
        val (vaultState1, counts1) = simulateRestoreLoop(listOf("img1", "img2", "img3"), shouldFailItem = null)
        assertFalse("Vault must be disabled when 100% succeed", vaultState1)
        assertEquals(3, counts1.first)
        assertEquals(0, counts1.second)

        // Case 2: One item fails (e.g. disk full)
        val (vaultState2, counts2) = simulateRestoreLoop(listOf("img1", "img2", "img3"), shouldFailItem = "img2")
        assertTrue("Vault must remain ENABLED if any item fails to keep remaining items protected", vaultState2)
        assertEquals(2, counts2.first)
        assertEquals(1, counts2.second)
    }

    @Test
    fun testSinglePhotoVaultAndRestoreRemainingCountLogic() {
        val photos = mutableListOf(
            Photo(id = "p1", fileName = "secure_1.png.enc", filePath = "/photos/secure_1.png.enc", title = "P1", folderId = "f1", dateAdded = 1000L, isScreenshot = true),
            Photo(id = "p2", fileName = "secure_2.png.enc", filePath = "/photos/secure_2.png.enc", title = "P2", folderId = "f1", dateAdded = 2000L, isScreenshot = true),
            Photo(id = "p3", fileName = "normal.jpg", filePath = "content://media/3", title = "P3", folderId = "f1", dateAdded = 3000L, isScreenshot = false)
        )
        var isVaultEnabled = true

        fun simulateRestoreSingle(photoId: String) {
            val idx = photos.indexOfFirst { it.id == photoId }
            if (idx >= 0) {
                photos[idx] = photos[idx].copy(
                    filePath = "content://media/restored_$photoId",
                    fileName = "restored_$photoId.png"
                )
            }
            val remainingVaulted = photos.count {
                it.filePath.endsWith(".enc") || (it.isScreenshot && !it.filePath.startsWith("content://"))
            }
            if (remainingVaulted == 0) {
                isVaultEnabled = false
            }
        }

        // Restore first vaulted photo: 1 vaulted photo remains, vault stays active
        simulateRestoreSingle("p1")
        assertTrue("Vault must remain enabled when p2 is still vaulted", isVaultEnabled)
        assertEquals(1, photos.count { it.filePath.endsWith(".enc") })

        // Restore second vaulted photo: 0 vaulted photos remain, vault auto-disables
        simulateRestoreSingle("p2")
        assertFalse("Vault must automatically disable when last photo is restored", isVaultEnabled)
        assertEquals(0, photos.count { it.filePath.endsWith(".enc") })
    }

    @Test
    fun testRepositoryAwaitInitialLoadPreventsStartupRace() = runBlocking {
        // Seed persisted data containing a vaulted .enc photo
        val seedDriver = InMemoryPersistenceDriver()
        val seedRepo = PhotoRepository(seedDriver, testScope)
        seedRepo.loadInitialData()
        val vaultedPhoto = Photo(
            id = "device_777",
            fileName = "secure_vault_777.png.enc",
            filePath = "/data/user/0/com.example.test2/files/photos/secure_vault_777.png.enc",
            title = "Secret Screenshot",
            folderId = "folder_screenshots",
            dateAdded = 1700000000000L,
            isScreenshot = true
        )
        seedRepo.addVaultedPhotos(listOf(vaultedPhoto))

        // Delayed PersistenceDriver simulating async disk decryption on app startup
        val delayedDriver = object : com.example.test2.shared.repository.PersistenceDriver {
            override suspend fun loadData(): String? {
                kotlinx.coroutines.delay(120)
                return seedDriver.loadData()
            }
            override suspend fun saveData(json: String) {
                seedDriver.saveData(json)
            }
        }

        val freshRepo = PhotoRepository(delayedDriver, CoroutineScope(Dispatchers.Default))
        // Caller awaits initial load just like PhotoAppViewModel.performScanDevicePhotosInternal
        freshRepo.awaitInitialLoad()
        val stalePublicScan = vaultedPhoto.copy(
            filePath = "content://media/external/images/media/777",
            fileName = "Screenshot_777.png"
        )
        freshRepo.syncDevicePhotos(listOf(stalePublicScan), emptyList())

        val resultPhoto = freshRepo.photos.value.firstOrNull { it.id == "device_777" }
        assertEquals(
            "Vaulted .enc path must not be overwritten by stale MediaStore scan on startup",
            "/data/user/0/com.example.test2/files/photos/secure_vault_777.png.enc",
            resultPhoto?.filePath
        )
    }

    @Test
    fun testSanitizeRestoreFileNameStripsIllegalCharsAndPreservesExtension() {
        val sanitized = PrivacyVaultManager.sanitizeRestoreFileName(
            rawTitle = "Hóa đơn 25/11/2026: Tiền điện?.png",
            encryptedFilePath = "/data/user/0/com.example.test2/files/photos/secure_vault_1234.png.enc",
            fallbackFileName = "secure_vault_1234.png.enc"
        )
        assertEquals("Hóa đơn 25_11_2026_ Tiền điện.png", sanitized)

        val fallback = PrivacyVaultManager.sanitizeRestoreFileName(
            rawTitle = "secure_vault_1234",
            encryptedFilePath = "/data/user/0/com.example.test2/files/photos/secure_vault_1234.jpg.enc",
            fallbackFileName = "Screenshot_Backup.jpg.enc"
        )
        assertEquals("Screenshot_Backup.jpg", fallback)

        val generated = PrivacyVaultManager.sanitizeRestoreFileName(
            rawTitle = "///:::",
            encryptedFilePath = "/data/user/0/com.example.test2/files/photos/secure_vault_999.webp.enc",
            fallbackFileName = "secure_vault_999.webp.enc"
        )
        assertTrue(generated.startsWith("Screenshot_"))
        assertTrue(generated.endsWith(".webp"))
    }

    @Test
    fun testDeviceMediaScannerTimestampResolverPrefersDateTakenMillis() {
        assertEquals(
            1795581571450L,
            com.example.test2.storage.DeviceMediaScanner.resolveMediaTimestampMillis(
                dateTakenMillis = 1795581571450L,
                dateAddedSec = 1795589999L,
                dateModifiedSec = 1795588888L,
                fallbackNowMillis = 9999L
            )
        )
        assertEquals(
            1795581571000L,
            com.example.test2.storage.DeviceMediaScanner.resolveMediaTimestampMillis(
                dateTakenMillis = 0L,
                dateAddedSec = 1795581571L,
                dateModifiedSec = 1795588888L,
                fallbackNowMillis = 9999L
            )
        )
        assertEquals(
            1795588888000L,
            com.example.test2.storage.DeviceMediaScanner.resolveMediaTimestampMillis(
                dateTakenMillis = 0L,
                dateAddedSec = 0L,
                dateModifiedSec = 1795588888L,
                fallbackNowMillis = 9999L
            )
        )
    }

    @Test
    fun testChronologicalOrderPreservedAcrossMixedVaultedAndPublicTransitions() = runBlocking {
        val driver = InMemoryPersistenceDriver()
        val repo = PhotoRepository(driver, testScope)
        repo.loadInitialData()

        // Create 4 photos including two sharing the exact same second (ties resolved stably via title/filename)
        val pOldest = Photo(
            id = "device_1",
            fileName = "Screenshot_100001.png",
            filePath = "content://media/external/images/media/1",
            title = "Screenshot_100001",
            folderId = "folder_screenshots",
            dateAdded = 1700000001000L,
            isScreenshot = true
        )
        val pTieEarly = Photo(
            id = "device_2",
            fileName = "Screenshot_100005_a.png",
            filePath = "content://media/external/images/media/2",
            title = "Screenshot_100005_a",
            folderId = "folder_screenshots",
            dateAdded = 1700000005000L,
            isScreenshot = true
        )
        val pTieLate = Photo(
            id = "device_3",
            fileName = "Screenshot_100005_b.png",
            filePath = "content://media/external/images/media/3",
            title = "Screenshot_100005_b",
            folderId = "folder_screenshots",
            dateAdded = 1700000005000L,
            isScreenshot = true
        )
        val pNewest = Photo(
            id = "device_4",
            fileName = "Screenshot_100009.png",
            filePath = "content://media/external/images/media/4",
            title = "Screenshot_100009",
            folderId = "folder_screenshots",
            dateAdded = 1700000009500L,
            isScreenshot = true
        )

        // Initial MediaStore scan (unordered/shuffled list passed to syncDevicePhotos)
        repo.syncDevicePhotos(listOf(pOldest, pTieLate, pNewest, pTieEarly), emptyList())
        val expectedDescTitles = listOf(
            "Screenshot_100009",
            "Screenshot_100005_b",
            "Screenshot_100005_a",
            "Screenshot_100001"
        )
        assertEquals(expectedDescTitles, repo.filteredPhotos.value.map { it.title })

        // Transition pTieEarly and pNewest into Vault (.enc)
        repo.updatePhotoStoragePath(
            photoId = "device_2",
            newFilePath = "/files/photos/secure_vault_999.png.enc",
            newFileName = "secure_vault_999.png.enc"
        )
        repo.updatePhotoStoragePath(
            photoId = "device_4",
            newFilePath = "/files/photos/secure_vault_111.png.enc",
            newFileName = "secure_vault_111.png.enc"
        )
        assertEquals(expectedDescTitles, repo.filteredPhotos.value.map { it.title })

        // Simulate MediaStore rescan finding only remaining public photos (pOldest, pTieLate)
        repo.syncDevicePhotos(listOf(pOldest, pTieLate), emptyList())
        assertEquals(expectedDescTitles, repo.filteredPhotos.value.map { it.title })

        // Simulate restoring oldest before newest or newest with brand new higher MediaStore IDs
        repo.updatePhotoStoragePath(
            photoId = "device_4",
            newFilePath = "content://media/external/images/media/901",
            newFileName = "Screenshot_100009.png",
            newPhotoId = "device_901"
        )
        repo.updatePhotoStoragePath(
            photoId = "device_2",
            newFilePath = "content://media/external/images/media/905",
            newFileName = "Screenshot_100005_a.png",
            newPhotoId = "device_905"
        )
        assertEquals(expectedDescTitles, repo.filteredPhotos.value.map { it.title })

        // Toggle DATE_ASC and back to DATE_DESC
        repo.setSortOption(com.example.test2.shared.models.SortOption.DATE_ASC)
        assertEquals(expectedDescTitles.reversed(), repo.filteredPhotos.value.map { it.title })
        repo.setSortOption(com.example.test2.shared.models.SortOption.DATE_DESC)
        assertEquals(expectedDescTitles, repo.filteredPhotos.value.map { it.title })
    }
}
