package com.example.test2

import android.app.Application
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.example.test2.database.AppDatabaseHelper
import com.example.test2.security.PrivacyVaultManager
import com.example.test2.shared.models.Photo
import com.example.test2.shared.repository.PhotoRepository
import com.example.test2.storage.AndroidFilePersistenceDriver
import com.example.test2.storage.DeviceMediaScanner
import com.example.test2.storage.DeviceScanResult
import com.example.test2.storage.LocalCryptoManager
import com.example.test2.ui.PhotoAppViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PrivacyVaultSystemDiagnosticTest {

    companion object {
        private const val TAG = "VaultVerify"
    }

    private fun execShell(cmd: String): String {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val pfd: ParcelFileDescriptor = uiAutomation.executeShellCommand(cmd)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }

    private val testStores = mutableListOf<ViewModelStore>()

    private fun createManagedViewModel(app: Application): PhotoAppViewModel {
        val store = ViewModelStore()
        testStores.add(store)
        return ViewModelProvider(
            store,
            ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        )[PhotoAppViewModel::class.java]
    }

    private fun cleanAppState(context: android.content.Context) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            testStores.forEach { it.clear() }
            testStores.clear()
        }
        try {
            WorkManager.getInstance(context).cancelAllWork()
        } catch (_: Exception) {}
        PrivacyVaultManager.setVaultEnabled(context, false)
        PrivacyVaultManager.setPendingVaultScope(context, null)
        PrivacyVaultManager.setVaultScope(context, PrivacyVaultManager.SCOPE_ALL)
        PrivacyVaultManager.clearAllRestoredExempts(context)
        File(context.filesDir, "photo_manager_metadata.json.enc").delete()
        File(context.filesDir, "photo_manager_metadata.json").delete()
        File(context.filesDir, "photos").listFiles()?.forEach { it.delete() }
        try {
            val db = AppDatabaseHelper.getInstance(context)
            db.getAllCompletedOrVaultedScreenshots().forEach { db.purgePhotoData(it.photoId) }
            db.writableDatabase.execSQL("DELETE FROM screenshot_queue")
        } catch (_: Exception) {}
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA),
                null,
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                    if (dataCol >= 0) {
                        val p = cursor.getString(dataCol)
                        if (!p.isNullOrBlank()) {
                            try { File(p).delete() } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    @Before
    fun resetVaultStateBefore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        cleanAppState(context)
    }

    @After
    fun resetVaultStateAfter() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        cleanAppState(context)
    }

    private suspend fun captureSystemScreenshotAndWait(scanner: DeviceMediaScanner): Pair<Photo, String> {
        val beforeIds = scanner.scanDeviceImages().photos.map { it.id }.toSet()
        execShell("input keyevent 120")
        var found: Photo? = null
        var scanResult: DeviceScanResult? = null
        for (attempt in 1..20) {
            delay(500)
            val res = scanner.scanDeviceImages()
            val candidate = res.photos.firstOrNull { it.id !in beforeIds && !it.fileName.startsWith(".pending") }
            if (candidate != null) {
                val path = res.physicalPaths[candidate.id] ?: ""
                if (path.isNotBlank() && File(path).exists() && File(path).length() > 0L) {
                    found = candidate
                    scanResult = res
                    break
                }
            }
        }
        assertNotNull("Real SystemUI screenshot must be captured and readable on disk", found)
        val physPath = scanResult!!.physicalPaths[found!!.id]!!
        return found to physPath
    }

    @Test
    fun verifyEndToEndAndroidVaultAndEncryptionWithRealKeyStoreAndMediaStore() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            Log.i(TAG, "=== START END-TO-END ANDROID VERIFICATION ===")

            // 1. Verify LocalCryptoManager encrypt/decrypt byte array with real AndroidKeyStore
            val rawPayload = "Real AndroidKeyStore AES-256-GCM test payload 2026".toByteArray()
            val encryptedBytes = LocalCryptoManager.encrypt(rawPayload)
            val decryptedBytes = LocalCryptoManager.decrypt(encryptedBytes)
            assertArrayEquals("Byte array roundtrip must succeed with AndroidKeyStore", rawPayload, decryptedBytes)
            Log.i(TAG, "1. LocalCryptoManager byte encryption/decryption PASSED")

            // 2. Verify AndroidFilePersistenceDriver encrypted JSON persistence with real AndroidKeyStore
            val driver = AndroidFilePersistenceDriver(context)
            val repoSeed = PhotoRepository(driver, CoroutineScope(Dispatchers.Default))
            repoSeed.awaitInitialLoad()
            delay(400)
            val persistedJson = driver.loadData()
            assertFalse("Encrypted metadata JSON must persist and decrypt cleanly", persistedJson.isNullOrBlank())
            Log.i(TAG, "2. AndroidFilePersistenceDriver encrypted persistence PASSED (len=${persistedJson?.length})")

            // 3. Verify pendingVaultScope survives in SharedPreferences across process restarts
            PrivacyVaultManager.setPendingVaultScope(context, PrivacyVaultManager.SCOPE_ALL)
            assertEquals(PrivacyVaultManager.SCOPE_ALL, PrivacyVaultManager.getPendingVaultScope(context))
            PrivacyVaultManager.setPendingVaultScope(context, null)
            assertNull(PrivacyVaultManager.getPendingVaultScope(context))
            Log.i(TAG, "3. PendingVaultScope persistence PASSED")

            // 4. Verify real SystemUI screenshot vaulting + eviction (owner = com.android.systemui)
            assertTrue("Test requires MANAGE_EXTERNAL_STORAGE pre-granted", PrivacyVaultManager.hasAllFilesAccess(context))

            val scanner = DeviceMediaScanner(context)
            val (sysUiPhoto, physicalPath) = captureSystemScreenshotAndWait(scanner)
            val sourceUri = Uri.parse(sysUiPhoto.filePath)
            val originalBytes = File(physicalPath).readBytes()

            val vaultResult = PrivacyVaultManager.vaultAndEvictScreenshot(
                context = context,
                sourceUri = sourceUri,
                sourcePath = physicalPath,
                originalFileName = sysUiPhoto.fileName
            )
            assertNotNull("vaultAndEvictScreenshot must succeed on real SystemUI screenshot", vaultResult)
            vaultResult!!

            // Verify .enc file exists, decrypts to exact original PNG bytes, and bitmap decodes cleanly
            val encFile = File(vaultResult.newFilePath)
            assertTrue(".enc file must exist", encFile.exists() && encFile.length() > 0L)
            val decryptedPngBytes = LocalCryptoManager.readDecryptedBytes(encFile)
            assertArrayEquals("Decrypted bytes must match original SystemUI PNG", originalBytes, decryptedPngBytes)
            val decodedBitmap = LocalCryptoManager.decodeDecryptedBitmapSafely(encFile)
            assertNotNull("Encrypted screenshot must decode to Bitmap cleanly", decodedBitmap)
            decodedBitmap?.recycle()

            // Verify original external file AND MediaStore row are BOTH evicted
            assertTrue("isOriginalEvicted must be true", vaultResult.isOriginalEvicted)
            assertFalse("External screenshot file must be deleted from disk", File(physicalPath).exists())
            assertFalse("isInternalEvicting must reset cleanly to false", PrivacyVaultManager.isInternalEvicting.get())

            delay(1000)
            var mediaStoreStillHasRow = false
            context.contentResolver.query(
                sourceUri,
                arrayOf(MediaStore.Images.Media._ID),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) mediaStoreStillHasRow = true
            }
            assertFalse("MediaStore row must be completely removed so outside galleries hide it", mediaStoreStillHasRow)

            val rescan = scanner.scanDeviceImages()
            assertFalse("Rescan must not return evicted screenshot", rescan.photos.any { it.id == sysUiPhoto.id })
            Log.i(TAG, "4. Real SystemUI screenshot vault + MediaStore/disk eviction PASSED")

            // 5. Verify SQLite syncVaultedPathToQueue inserts & recovers un-queued photos
            val dbHelper = AppDatabaseHelper.getInstance(context)
            dbHelper.purgePhotoData(sysUiPhoto.id)
            val synced = dbHelper.syncVaultedPathToQueue(
                photoId = sysUiPhoto.id,
                newFilePath = vaultResult.newFilePath,
                dateAdded = sysUiPhoto.dateAdded,
                photoTitle = sysUiPhoto.title,
                originalFileName = sysUiPhoto.fileName
            )
            assertTrue("syncVaultedPathToQueue must insert record for older un-queued photos", synced)
            val recovered = dbHelper.getAllCompletedOrVaultedScreenshots().firstOrNull { it.photoId == sysUiPhoto.id }
            assertNotNull("Vaulted photo must be recoverable from SQLite", recovered)
            assertEquals(vaultResult.newFilePath, recovered?.filePath)
            Log.i(TAG, "5. SQLite syncVaultedPathToQueue & recovery PASSED")

            // 6. Verify restoreAndEvictScreenshot restores back to public Pictures/Screenshots and deletes .enc
            val restoreResult = PrivacyVaultManager.restoreAndEvictScreenshot(
                context = context,
                vaultedFilePath = vaultResult.newFilePath,
                originalFileName = sysUiPhoto.fileName
            )
            assertNotNull("restoreAndEvictScreenshot must succeed", restoreResult)
            restoreResult!!
            assertFalse(".enc file must be deleted after restore", encFile.exists())

            val restoredUri = Uri.parse(restoreResult.newUri)
            val restoredBytes = context.contentResolver.openInputStream(restoredUri)?.use { it.readBytes() }
            assertNotNull("Restored MediaStore URI must be readable", restoredBytes)
            assertArrayEquals("Restored public bytes must match original PNG bytes", originalBytes, restoredBytes)
            Log.i(TAG, "6. Restore back to public MediaStore PASSED")

            // Cleanup restored file and SQLite record
            try { context.contentResolver.delete(restoredUri, null, null) } catch (_: Exception) {}
            dbHelper.purgePhotoData(sysUiPhoto.id)
            Log.i(TAG, "=== ALL END-TO-END ANDROID VERIFICATION CHECKS PASSED ===")
        }
    }

    @Test
    fun verifyPhotoAppViewModelRelaunchOnMainThreadWithVaultEnabled() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val app = context.applicationContext as Application
            val dbHelper = AppDatabaseHelper.getInstance(context)

            // Capture screenshot BEFORE enabling Vault so background workers don't race the test setup
            val scanner = DeviceMediaScanner(context)
            val (sysUiPhoto, physicalPath) = captureSystemScreenshotAndWait(scanner)

            PrivacyVaultManager.setVaultScope(context, PrivacyVaultManager.SCOPE_ALL)
            PrivacyVaultManager.setVaultEnabled(context, true)

            val vaultResult = PrivacyVaultManager.vaultAndEvictScreenshot(
                context = context,
                sourceUri = Uri.parse(sysUiPhoto.filePath),
                sourcePath = physicalPath,
                originalFileName = sysUiPhoto.fileName
            )
            assertNotNull("Vaulting must succeed before relaunch test", vaultResult)
            vaultResult!!

            val driver = AndroidFilePersistenceDriver(context)
            val seedRepo = PhotoRepository(driver, CoroutineScope(Dispatchers.Default))
            seedRepo.awaitInitialLoad()
            val vaultedPhoto = sysUiPhoto.copy(
                filePath = vaultResult.newFilePath,
                fileName = vaultResult.newFileName,
                fileSizeBytes = vaultResult.fileSizeBytes
            )
            seedRepo.addVaultedPhotos(listOf(vaultedPhoto))
            dbHelper.syncVaultedPathToQueue(
                photoId = vaultedPhoto.id,
                newFilePath = vaultedPhoto.filePath,
                dateAdded = vaultedPhoto.dateAdded,
                photoTitle = vaultedPhoto.title,
                originalFileName = vaultedPhoto.fileName
            )
            delay(400)

            // Simulate Launch 2+: construct PhotoAppViewModel on Main Thread (Dispatchers.Main.immediate)
            // with READ_MEDIA_IMAGES granted and Vault already enabled!
            var relaunchedVm: PhotoAppViewModel? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val vm = createManagedViewModel(app)
                vm.onResumeScan()
                relaunchedVm = vm
            }
            val vm = relaunchedVm!!
            assertTrue("hasStoragePermission must be true on relaunch", vm.hasStoragePermission.value)
            assertTrue("isVaultEnabled must be true on relaunch", vm.isVaultEnabled.value)
            assertFalse("App must start locked on relaunch when Vault is enabled", vm.isAppUnlocked.value)

            // Wait for async loadInitialData + scanDevicePhotos + syncCompletedScreenshotsFromDb
            delay(2000)
            val loadedVaulted = vm.photos.value.firstOrNull { it.id == vaultedPhoto.id }
            assertNotNull("Vaulted photo must be loaded after relaunch", loadedVaulted)
            assertTrue(
                "Vaulted photo must retain .enc path after relaunch",
                loadedVaulted!!.filePath.endsWith(".enc")
            )
            Log.i(TAG, "7. MainThread PhotoAppViewModel relaunch with Vault enabled PASSED")

            // Cleanup
            PrivacyVaultManager.setVaultEnabled(context, false)
            File(vaultResult.newFilePath).delete()
            dbHelper.purgePhotoData(vaultedPhoto.id)
        }
    }

    @Test
    fun verifyBatchRestoreAllVaultedPhotosCompletesInSingleRunWithActiveObservers() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val app = context.applicationContext as Application
            val dbHelper = AppDatabaseHelper.getInstance(context)
            val scanner = DeviceMediaScanner(context)
            val photosDir = File(context.filesDir, "photos")
            photosDir.listFiles()?.forEach { it.delete() }

            // 1. Capture 5 real SystemUI screenshots while Vault is off
            val capturedPairs = (1..5).map { idx ->
                val pair = captureSystemScreenshotAndWait(scanner)
                Log.i(TAG, "Captured screenshot #$idx: ${pair.first.id}")
                pair
            }

            // 2. Enable Vault (SCOPE_NEW_ONLY with early timestamp, matching worst-case re-vault race)
            PrivacyVaultManager.setVaultScope(context, PrivacyVaultManager.SCOPE_NEW_ONLY)
            PrivacyVaultManager.setVaultEnabledSince(context, System.currentTimeMillis() - 60_000L)
            PrivacyVaultManager.setVaultEnabled(context, true)

            val vaultedPhotos = mutableListOf<Photo>()
            for ((sysUiPhoto, physicalPath) in capturedPairs) {
                val res = PrivacyVaultManager.vaultAndEvictScreenshot(
                    context = context,
                    sourceUri = Uri.parse(sysUiPhoto.filePath),
                    sourcePath = physicalPath,
                    originalFileName = sysUiPhoto.fileName
                )
                assertNotNull("Vaulting must succeed for ${sysUiPhoto.id}", res)
                res!!
                assertTrue("Original must be evicted before restore test starts: ${sysUiPhoto.id}", res.isOriginalEvicted)
                val vaultedPhoto = sysUiPhoto.copy(
                    filePath = res.newFilePath,
                    fileName = res.newFileName,
                    fileSizeBytes = res.fileSizeBytes,
                    title = "Hóa đơn 25/11: Test ${sysUiPhoto.id}"
                )
                vaultedPhotos.add(vaultedPhoto)
                dbHelper.syncVaultedPathToQueue(
                    photoId = vaultedPhoto.id,
                    newFilePath = vaultedPhoto.filePath,
                    dateAdded = vaultedPhoto.dateAdded,
                    photoTitle = vaultedPhoto.title,
                    originalFileName = sysUiPhoto.fileName
                )
            }

            val driver = AndroidFilePersistenceDriver(context)
            val seedRepo = PhotoRepository(driver, CoroutineScope(Dispatchers.Default))
            seedRepo.awaitInitialLoad()
            seedRepo.addVaultedPhotos(vaultedPhotos)
            delay(1000)

            assertEquals(5, photosDir.listFiles()?.count { it.name.endsWith(".enc") } ?: 0)

            // 3. Instantiate PhotoAppViewModel on MainThread with ContentObserver + ScreenshotJobScheduler active
            var vm: PhotoAppViewModel? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                vm = createManagedViewModel(app)
                vm!!.unlockApp()
            }
            val viewModel = vm!!
            viewModel.repository.awaitInitialLoad()
            delay(500)

            // 4. Invoke restoreAllVaultedPhotos ONCE
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                viewModel.restoreAllVaultedPhotos()
            }

            // Wait for restore loop + settle delay to finish
            var finished = false
            for (waitStep in 1..80) {
                delay(250)
                val p = viewModel.restoreProgress.value
                val restoring = viewModel.isRestoringPhotos.value
                if (waitStep % 4 == 0) {
                    Log.i(TAG, "Batch restore poll #$waitStep: restoring=$restoring progress=${p.current}/${p.total}")
                }
                if (!restoring && p.total == 5 && p.current == 5) {
                    finished = true
                    break
                }
            }
            val finalP = viewModel.restoreProgress.value
            assertTrue(
                "restoreAllVaultedPhotos must complete 5/5 in single run (actual restoring=${viewModel.isRestoringPhotos.value}, progress=${finalP.current}/${finalP.total}, photos=${viewModel.photos.value.size})",
                finished
            )

            // 5. Simulate ContentObserver + scanDevicePhotos + ScreenshotDetectionManager firing right after restore
            delay(2000)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                viewModel.scanDevicePhotos()
            }
            com.example.test2.services.ScreenshotDetectionManager.enqueueNewScreenshots(context)
            delay(1500)

            // 6. Verify 0 .enc files remain, Vault is disabled, and all 5 restored photos remain readable in MediaStore!
            val banner = viewModel.showRestoreSuccessBanner.value
            val remainingEnc = photosDir.listFiles()?.filter { it.name.endsWith(".enc") } ?: emptyList()
            assertFalse(
                "isVaultEnabled must remain false after 1 restore run (banner=$banner, remainingEnc=${remainingEnc.size}, photos=${viewModel.photos.value.map { it.id + ":" + it.filePath }})",
                viewModel.isVaultEnabled.value
            )
            assertFalse("PrivacyVaultManager.isVaultEnabled must be false", PrivacyVaultManager.isVaultEnabled(context))

            assertEquals("Zero .enc files must remain after single restoreAllVaultedPhotos run", 0, remainingEnc.size)

            val restoredPhotos = viewModel.photos.value.filter { it.isScreenshot && it.filePath.startsWith("content://") }
            assertTrue(
                "All 5 restored screenshots must remain in public MediaStore URIs (found ${restoredPhotos.size})",
                restoredPhotos.size >= 5
            )
            for (p in restoredPhotos.take(5)) {
                val uri = Uri.parse(p.filePath)
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                assertNotNull("Restored URI ${p.filePath} must be readable", bytes)
                assertTrue("Restored URI ${p.filePath} must be non-empty", bytes!!.isNotEmpty())
                assertFalse("Should not auto-vault restored photo when vault is off", PrivacyVaultManager.shouldAutoVaultPhoto(context, p.id, p.dateAdded))
            }
            Log.i(TAG, "8. Single-run restoreAllVaultedPhotos with active observers PASSED (5/5 stayed outside)")

            // Cleanup
            restoredPhotos.forEach { p ->
                try { context.contentResolver.delete(Uri.parse(p.filePath), null, null) } catch (_: Exception) {}
                dbHelper.purgePhotoData(p.id)
            }
        }
    }

    @Test
    fun verifySinglePhotoRestoreRemainsOutsideWhileVaultStaysEnabled() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val app = context.applicationContext as Application
            val dbHelper = AppDatabaseHelper.getInstance(context)
            val scanner = DeviceMediaScanner(context)
            val photosDir = File(context.filesDir, "photos")
            photosDir.listFiles()?.forEach { it.delete() }

            // Capture 2 screenshots
            val p1Pair = captureSystemScreenshotAndWait(scanner)
            val p2Pair = captureSystemScreenshotAndWait(scanner)

            PrivacyVaultManager.setVaultScope(context, PrivacyVaultManager.SCOPE_ALL)
            PrivacyVaultManager.setVaultEnabled(context, true)

            val vaulted1 = PrivacyVaultManager.vaultAndEvictScreenshot(
                context, Uri.parse(p1Pair.first.filePath), p1Pair.second, p1Pair.first.fileName
            )!!
            val vaulted2 = PrivacyVaultManager.vaultAndEvictScreenshot(
                context, Uri.parse(p2Pair.first.filePath), p2Pair.second, p2Pair.first.fileName
            )!!

            val photo1 = p1Pair.first.copy(filePath = vaulted1.newFilePath, fileName = vaulted1.newFileName, fileSizeBytes = vaulted1.fileSizeBytes)
            val photo2 = p2Pair.first.copy(filePath = vaulted2.newFilePath, fileName = vaulted2.newFileName, fileSizeBytes = vaulted2.fileSizeBytes)
            dbHelper.syncVaultedPathToQueue(photo1.id, photo1.filePath, photo1.dateAdded, photo1.title, p1Pair.first.fileName)
            dbHelper.syncVaultedPathToQueue(photo2.id, photo2.filePath, photo2.dateAdded, photo2.title, p2Pair.first.fileName)

            val driver = AndroidFilePersistenceDriver(context)
            val seedRepo = PhotoRepository(driver, CoroutineScope(Dispatchers.Default))
            seedRepo.awaitInitialLoad()
            seedRepo.addVaultedPhotos(listOf(photo1, photo2))
            for (waitSave in 1..20) {
                delay(150)
                if (File(context.filesDir, "photo_manager_metadata.json.enc").exists()) break
            }

            var vm: PhotoAppViewModel? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                vm = createManagedViewModel(app)
                vm!!.unlockApp()
            }
            val viewModel = vm!!
            viewModel.repository.awaitInitialLoad()
            for (waitLoad in 1..20) {
                delay(150)
                if (viewModel.photos.value.size >= 2) break
            }
            assertEquals("Both vaulted photos must be loaded before single restore test", 2, viewModel.photos.value.size)

            // Restore ONLY photo1 while photo2 remains vaulted (Vault stays TRUE!)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                viewModel.restoreSinglePhoto(photo1)
            }
            var singleRestored = false
            for (step in 1..40) {
                delay(250)
                if (viewModel.photos.value.any { !it.filePath.endsWith(".enc") && it.isScreenshot && it.filePath.startsWith("content://") }) {
                    singleRestored = true
                    break
                }
            }
            assertTrue("restoreSinglePhoto must complete moving photo1 to public MediaStore", singleRestored)

            // Trigger rescan while Vault is still ENABLED with SCOPE_ALL
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                viewModel.scanDevicePhotos()
            }
            com.example.test2.services.ScreenshotDetectionManager.enqueueNewScreenshots(context)
            delay(1500)

            assertTrue("Vault must remain enabled while photo2 is protected", viewModel.isVaultEnabled.value)
            val remainingEnc = photosDir.listFiles()?.filter { it.name.endsWith(".enc") } ?: emptyList()
            assertEquals("Exactly 1 .enc file (photo2) must remain in Vault", 1, remainingEnc.size)

            val currentList = viewModel.photos.value
            val restored1 = currentList.firstOrNull { !it.filePath.endsWith(".enc") && it.isScreenshot && it.filePath.startsWith("content://") }
            assertNotNull("Restored single photo must stay in public MediaStore even when Vault SCOPE_ALL is active", restored1)
            assertFalse(
                "shouldAutoVaultPhoto must exempt restored single photo",
                PrivacyVaultManager.shouldAutoVaultPhoto(context, restored1!!.id, restored1.dateAdded)
            )

            // Re-vaulting explicitly via vaultSinglePhoto must clear exemption and vault it again
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                viewModel.vaultSinglePhoto(restored1)
            }
            delay(1500)
            assertFalse("clearRestoredExempt must happen on explicit vaultSinglePhoto", PrivacyVaultManager.isRestoredExempt(context, restored1.id))
            val encAfterReVault = photosDir.listFiles()?.filter { it.name.endsWith(".enc") } ?: emptyList()
            assertEquals("Both photos should now be encrypted after explicit vaultSinglePhoto", 2, encAfterReVault.size)

            Log.i(TAG, "9. Single-photo restore exemption with Vault SCOPE_ALL active PASSED")

            // Cleanup
            PrivacyVaultManager.setVaultEnabled(context, false)
            encAfterReVault.forEach { it.delete() }
            dbHelper.purgePhotoData(photo1.id)
            dbHelper.purgePhotoData(photo2.id)
            dbHelper.purgePhotoData(restored1.id)
        }
    }

    @Test
    fun verifyPhotoOrderPreservedAcrossMultipleVaultAndRestoreRoundtrips() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val app = context.applicationContext as Application
            val dbHelper = AppDatabaseHelper.getInstance(context)
            val scanner = DeviceMediaScanner(context)
            val photosDir = File(context.filesDir, "photos")

            // 1. Capture 4 sequential real SystemUI screenshots cleanly outside Vault
            val captured = (1..4).map { idx ->
                delay(1100) // Distinct second boundary for clean natural chronological spacing
                val pair = captureSystemScreenshotAndWait(scanner)
                Log.i(TAG, "OrderTest captured #$idx: id=${pair.first.id}, title=${pair.first.title}, dateAdded=${pair.first.dateAdded}")
                pair.first
            }

            // Seed one screenshot with a future AI eventDateMillis (+30 days) to verify event date never hijacks photo order
            val futureEventMillis = System.currentTimeMillis() + 30L * 24 * 3600 * 1000L
            val targetWithFutureEvent = captured[1]
            dbHelper.recordProcessedScreenshot(
                photoId = targetWithFutureEvent.id,
                filePath = targetWithFutureEvent.filePath,
                dateAdded = targetWithFutureEvent.dateAdded,
                scrubbedText = "Sự kiện tháng sau",
                detectedIntent = "EVENT",
                eventTitle = "Hội thảo công nghệ",
                eventDateMillis = futureEventMillis,
                targetCategoryId = "cat_events",
                status = "COMPLETED",
                photoTitle = targetWithFutureEvent.title,
                originalFileName = targetWithFutureEvent.fileName
            )

            // 2. Launch ViewModel and record baseline gallery order
            var vm: PhotoAppViewModel? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                vm = createManagedViewModel(app)
                vm!!.unlockApp()
            }
            var viewModel = vm!!
            viewModel.repository.awaitInitialLoad()
            for (step in 1..40) {
                delay(200)
                if (viewModel.filteredPhotos.value.size == 4 && !viewModel.isScanning.value) break
            }
            assertEquals(4, viewModel.filteredPhotos.value.size)
            val expectedOrderTitles = viewModel.filteredPhotos.value.map { it.title }
            val expectedDateMap = viewModel.filteredPhotos.value.associate { it.title to it.dateAdded }
            Log.i(TAG, "Baseline gallery order: $expectedOrderTitles")

            // 3. Cycle 1: Move ALL photos INTO Vault (SCOPE_ALL)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                viewModel.confirmEnableVault(vaultAll = true)
            }
            var allVaulted = false
            for (step in 1..60) {
                delay(250)
                val encCount = photosDir.listFiles()?.count { it.name.endsWith(".enc") } ?: 0
                if (encCount == 4 && viewModel.photos.value.all { it.filePath.endsWith(".enc") }) {
                    allVaulted = true
                    break
                }
            }
            assertTrue("All 4 photos must be vaulted", allVaulted)
            // Allow PhotoRepository's async persistData() coroutine to flush JSON before clearing ViewModelStore
            delay(1000)
            assertEquals(
                "Gallery order inside Vault must match baseline",
                expectedOrderTitles,
                viewModel.filteredPhotos.value.map { it.title }
            )

            // 4. Cycle 2: Relaunch ViewModel cleanly while Vault stays active (tests SQLite + JSON recovery order)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                testStores.forEach { it.clear() }
                testStores.clear()
                vm = createManagedViewModel(app)
                vm!!.unlockApp()
            }
            viewModel = vm!!
            viewModel.repository.awaitInitialLoad()
            delay(2000)
            assertEquals(
                "Gallery order after relaunch inside Vault must still match baseline",
                expectedOrderTitles,
                viewModel.filteredPhotos.value.map { it.title }
            )
            assertEquals(
                "AI eventDateMillis must NOT overwrite original dateAdded",
                expectedDateMap[targetWithFutureEvent.title],
                viewModel.filteredPhotos.value.first { it.title == targetWithFutureEvent.title }.dateAdded
            )

            // 5. Cycle 3: Restore ALL photos OUT of Vault and rescan from MediaStore
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                viewModel.restoreAllVaultedPhotos()
            }
            var allRestored = false
            for (step in 1..80) {
                delay(250)
                val prog = viewModel.restoreProgress.value
                val restoring = viewModel.isRestoringPhotos.value
                if (!restoring &&
                    prog.total > 0 &&
                    prog.current == prog.total &&
                    viewModel.photos.value.isNotEmpty() &&
                    viewModel.photos.value.all { it.filePath.startsWith("content://") }
                ) {
                    allRestored = true
                    break
                }
            }
            assertTrue(
                "All 4 photos must restore cleanly (restoring=${viewModel.isRestoringPhotos.value}, prog=${viewModel.restoreProgress.value}, banner=${viewModel.showRestoreSuccessBanner.value}, encFiles=${photosDir.listFiles()?.size}, photos=${viewModel.photos.value.map { it.id + ":" + it.filePath + ":" + it.title }})",
                allRestored
            )
            delay(1500)
            assertEquals(
                "Gallery order after restoreAllVaultedPhotos must match baseline 100%",
                expectedOrderTitles,
                viewModel.filteredPhotos.value.map { it.title }
            )

            // Also verify raw DeviceMediaScanner output matches baseline order (even without JSON cache)
            val freshMediaStoreOrder = scanner.scanDeviceImages().photos.map { it.title }
            assertEquals(
                "Fresh MediaStore scan order (DATE_TAKEN / _ID) must match baseline 100%",
                expectedOrderTitles,
                freshMediaStoreOrder
            )

            // 6. Cycle 4: Single-photo Vault then Single-photo Restore + rescan
            val targetPhoto = viewModel.filteredPhotos.value[2]
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                viewModel.vaultSinglePhoto(targetPhoto)
            }
            delay(1500)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                viewModel.scanDevicePhotos()
            }
            delay(1500)
            assertEquals(
                "Mixed Vault + Public order after vaultSinglePhoto must match baseline 100%",
                expectedOrderTitles,
                viewModel.filteredPhotos.value.map { it.title }
            )

            val vaultedTarget = viewModel.filteredPhotos.value[2]
            assertTrue(vaultedTarget.filePath.endsWith(".enc"))
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                viewModel.restoreSinglePhoto(vaultedTarget)
            }
            delay(2000)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                viewModel.scanDevicePhotos()
            }
            delay(1500)
            assertEquals(
                "Order after restoreSinglePhoto roundtrip must match baseline 100%",
                expectedOrderTitles,
                viewModel.filteredPhotos.value.map { it.title }
            )
            Log.i(TAG, "10. Multi-roundtrip photo order preservation PASSED 100%")
        }
    }
}
