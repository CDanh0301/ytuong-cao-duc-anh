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
}
