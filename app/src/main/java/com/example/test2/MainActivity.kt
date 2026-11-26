package com.example.test2

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.test2.di.AppViewModelFactory
import com.example.test2.notifications.ScreenshotNotificationHelper
import com.example.test2.presentation.categories.CategoriesViewModel
import com.example.test2.presentation.gallery.GalleryViewModel
import com.example.test2.presentation.housekeeping.HousekeepingViewModel
import com.example.test2.presentation.vault.VaultViewModel
import com.example.test2.services.AgingJobScheduler
import com.example.test2.services.ScreenshotJobScheduler
import com.example.test2.services.ScreenshotWatcherService
import com.example.test2.shared.models.ActionCardType
import com.example.test2.ui.ActionCardIntentHelper
import com.example.test2.ui.AppTab
import com.example.test2.ui.PhotoAppViewModel
import com.example.test2.ui.screens.AllFilesAccessDialog
import com.example.test2.ui.screens.AppLockScreen
import com.example.test2.ui.screens.CategoriesScreen
import com.example.test2.ui.screens.ConfirmDeleteDialog
import com.example.test2.ui.screens.CreateCategoryDialog
import com.example.test2.ui.screens.DisableVaultConfirmDialog
import com.example.test2.ui.screens.EnableVaultScopeDialog
import com.example.test2.ui.screens.HousekeepingScreen
import com.example.test2.ui.screens.ImageViewerDialog
import com.example.test2.ui.screens.NoRetroactiveMatchesDialog
import com.example.test2.ui.screens.PhotoGalleryScreen
import com.example.test2.ui.screens.PhotoSettingsDialog
import com.example.test2.ui.screens.PrivacyConsentDialog
import com.example.test2.ui.screens.RenameCategoryDialog
import com.example.test2.ui.screens.RestoreProgressDialog
import com.example.test2.ui.screens.RetroactiveCategoryDialog
import com.example.test2.ui.screens.RetroactiveScanningDialog
import com.example.test2.ui.theme.Test2Theme
import com.example.test2.widget.PhotoSearchWidgetProvider

class MainActivity : FragmentActivity() {
    private val viewModelFactory by lazy { AppViewModelFactory(application) }
    private val viewModel: PhotoAppViewModel by viewModels { viewModelFactory }
    private val galleryViewModel: GalleryViewModel by viewModels { viewModelFactory }
    private val vaultViewModel: VaultViewModel by viewModels { viewModelFactory }
    private val categoriesViewModel: CategoriesViewModel by viewModels { viewModelFactory }
    private val housekeepingViewModel: HousekeepingViewModel by viewModels { viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        ScreenshotJobScheduler.schedule(this)
        AgingJobScheduler.schedule(this)
        ScreenshotWatcherService.start(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        setContent {
            Test2Theme {
                val mainState by viewModel.uiState.collectAsState()
                val galleryState by galleryViewModel.uiState.collectAsState()
                val vaultState by vaultViewModel.uiState.collectAsState()
                val categoriesState by categoriesViewModel.uiState.collectAsState()
                val housekeepingState by housekeepingViewModel.uiState.collectAsState()

                val photos by galleryViewModel.photos.collectAsState()
                val folders by galleryViewModel.folders.collectAsState()
                val categories by galleryViewModel.categories.collectAsState()
                val accountTier by galleryViewModel.accountTier.collectAsState()
                val agingSettings by housekeepingViewModel.agingSettings.collectAsState()
                val agingPhotos = remember(photos, agingSettings) {
                    housekeepingViewModel.getAgingScreenshots()
                }

                LaunchedEffect(vaultState.showRestoreSuccessBanner) {
                    vaultState.showRestoreSuccessBanner?.let { msg ->
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                        vaultViewModel.dismissRestoreSuccessBanner()
                    }
                }

                LaunchedEffect(vaultState.showVaultPermissionDeniedBanner) {
                    vaultState.showVaultPermissionDeniedBanner?.let { msg ->
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                        vaultViewModel.dismissVaultPermissionDeniedBanner()
                    }
                }

                val permissionsToRequest = remember {
                    val list = mutableListOf<String>()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        list.add(Manifest.permission.READ_MEDIA_IMAGES)
                        list.add(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    list.toTypedArray()
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions[Manifest.permission.READ_MEDIA_IMAGES] == true
                    } else {
                        permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
                    }
                    viewModel.onPermissionResult(storageGranted)
                }

                LaunchedEffect(Unit) {
                    if (!mainState.hasStoragePermission) {
                        permissionLauncher.launch(permissionsToRequest)
                    }
                }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> {
                                if (viewModel.hasStoragePermission.value) {
                                    viewModel.onResumeScan()
                                    viewModel.ensureVectorsIndexed()
                                }
                            }
                            Lifecycle.Event.ON_STOP -> {
                                if (vaultViewModel.uiState.value.isVaultEnabled) {
                                    vaultViewModel.notifyAppBackgrounded()
                                }
                            }
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                val photoPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickMultipleVisualMedia()
                ) { uris ->
                    if (uris.isNotEmpty()) {
                        galleryViewModel.importPhotos(uris)
                    }
                }

                if (vaultState.isVaultEnabled && !vaultState.isAppUnlocked) {
                    AppLockScreen(
                        onUnlockClick = {
                            vaultViewModel.authenticate(
                                activity = this@MainActivity,
                                onSuccess = { vaultViewModel.unlockApp() },
                                onError = { errorCode, _ ->
                                    if (errorCode == androidx.biometric.BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Thiết bị chưa cài đặt mã khóa màn hình (PIN/Vân tay). Vui lòng thiết lập trong Cài đặt của máy.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            )
                        },
                        onBackPressed = { finish() }
                    )
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = WindowInsets.statusBars,
                        bottomBar = {
                            NavigationBar(
                                containerColor = Color.White,
                                tonalElevation = 8.dp
                            ) {
                                NavigationBarItem(
                                    selected = mainState.activeTab == AppTab.PHOTOS,
                                    onClick = { viewModel.setActiveTab(AppTab.PHOTOS) },
                                    icon = {
                                        Icon(
                                            Icons.Default.Photo,
                                            contentDescription = "Ảnh",
                                            modifier = Modifier.size(22.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            "Ảnh",
                                            fontSize = 11.sp,
                                            fontWeight = if (mainState.activeTab == AppTab.PHOTOS) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF2563EB),
                                        selectedTextColor = Color(0xFF2563EB),
                                        indicatorColor = Color(0xFFEFF6FF),
                                        unselectedIconColor = Color(0xFF64748B),
                                        unselectedTextColor = Color(0xFF64748B)
                                    )
                                )

                                NavigationBarItem(
                                    selected = mainState.activeTab == AppTab.CATEGORIES,
                                    onClick = { viewModel.setActiveTab(AppTab.CATEGORIES) },
                                    icon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Label,
                                            contentDescription = "Danh mục",
                                            modifier = Modifier.size(22.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            "Danh mục",
                                            fontSize = 11.sp,
                                            fontWeight = if (mainState.activeTab == AppTab.CATEGORIES) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF2563EB),
                                        selectedTextColor = Color(0xFF2563EB),
                                        indicatorColor = Color(0xFFEFF6FF),
                                        unselectedIconColor = Color(0xFF64748B),
                                        unselectedTextColor = Color(0xFF64748B)
                                    )
                                )

                                NavigationBarItem(
                                    selected = mainState.activeTab == AppTab.CLEANER,
                                    onClick = { viewModel.setActiveTab(AppTab.CLEANER) },
                                    icon = {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = "Trợ lý",
                                            modifier = Modifier.size(22.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            "Trợ lý",
                                            fontSize = 11.sp,
                                            fontWeight = if (mainState.activeTab == AppTab.CLEANER) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF2563EB),
                                        selectedTextColor = Color(0xFF2563EB),
                                        indicatorColor = Color(0xFFEFF6FF),
                                        unselectedIconColor = Color(0xFF64748B),
                                        unselectedTextColor = Color(0xFF64748B)
                                    )
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            when (mainState.activeTab) {
                                AppTab.PHOTOS -> {
                                    PhotoGalleryScreen(
                                        photos = galleryState.photos,
                                        allPhotosCount = photos.size,
                                        folders = folders,
                                        categories = categories,
                                        currentFilter = galleryState.activeFilter,
                                        hasStoragePermission = mainState.hasStoragePermission,
                                        isScanning = mainState.isScanning,
                                        pendingAiCount = mainState.pendingAiCount,
                                        accountTier = accountTier,
                                        displayMode = galleryState.displayMode,
                                        onDisplayModeChange = { galleryViewModel.setDisplayMode(it) },
                                        onRequestPermission = { permissionLauncher.launch(permissionsToRequest) },
                                        onPhotoClick = { photo -> galleryViewModel.viewPhoto(photo) },
                                        onToggleFavorite = { photoId -> galleryViewModel.toggleFavorite(photoId) },
                                        onToggleFavoriteFilter = { galleryViewModel.toggleFavoriteFilter() },
                                        onSelectFolder = { galleryViewModel.filterByFolder(it) },
                                        onSelectCategory = { galleryViewModel.filterByCategory(it) },
                                        onSearchChange = { galleryViewModel.setSearchQuery(it) },
                                        onClearFilters = { galleryViewModel.clearFilters() },
                                        activeActionCardType = galleryState.activeActionCardType,
                                        actionCardPhotoCounts = galleryState.actionCardPhotoCounts,
                                        onSelectActionCardType = { galleryViewModel.setActionCardFilter(it) },
                                        semanticMatchScores = galleryState.semanticMatchScores,
                                        isVectorIndexing = galleryState.isVectorIndexing,
                                        onToggleTier = { galleryViewModel.toggleUserTier(it) },
                                        onAddPhotoClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    )
                                }
                                AppTab.CATEGORIES -> {
                                    CategoriesScreen(
                                        categories = categories,
                                        photos = photos,
                                        isPro = accountTier.isPro,
                                        onSelectCategory = { categoryId -> categoriesViewModel.selectCategory(categoryId) },
                                        onCreateCategoryClick = { categoriesViewModel.openCreateCategoryDialog() },
                                        onEditCategoryClick = { category -> categoriesViewModel.setCategoryToEdit(category) },
                                        onDeleteCategoryClick = { category -> categoriesViewModel.setCategoryToDelete(category) },
                                        onAutoCategorizeClick = { category -> categoriesViewModel.triggerRetroactiveCategoryScan(category) }
                                    )
                                }
                                AppTab.CLEANER -> {
                                    HousekeepingScreen(
                                        photos = photos,
                                        accountTier = accountTier,
                                        agingSettings = agingSettings,
                                        agingPhotos = agingPhotos,
                                        categories = categories,
                                        isVaultEnabled = vaultState.isVaultEnabled,
                                        onToggleVault = { vaultViewModel.setVaultEnabled(it) },
                                        onRequestRestorePhotos = { vaultViewModel.requestDisableVault() },
                                        onToggleTier = { galleryViewModel.toggleUserTier(it) },
                                        onCleanClutter = { housekeepingViewModel.cleanAgingScreenshots(it) },
                                        onDeleteSinglePhoto = { galleryViewModel.setPhotoToDelete(it) },
                                        onAddToCalendar = { photo ->
                                            if (ActionCardIntentHelper.launchCalendarForPhoto(this@MainActivity, photo)) {
                                                housekeepingViewModel.markReminderCreated(photo.id)
                                            }
                                        },
                                        onPhotoClick = { photo -> galleryViewModel.viewPhoto(photo) },
                                        onSetCategoryRetentionRule = { catId, days ->
                                            housekeepingViewModel.setCategoryRetentionRule(catId, days)
                                        },
                                        agingMonitorInfo = housekeepingState.agingMonitorInfo,
                                        onRunAgingScanNow = { housekeepingViewModel.runAgingScanNow() }
                                    )
                                }
                            }
                        }
                    }

                    galleryState.viewerPhotoId?.let { photoId ->
                        ImageViewerDialog(
                            photos = galleryState.photos,
                            initialPhotoId = photoId,
                            folders = folders,
                            categories = categories,
                            onDismiss = { galleryViewModel.closePhotoViewer() },
                            onOpenSettings = { photo -> galleryViewModel.openPhotoSettings(photo) },
                            onToggleFavorite = { photo -> galleryViewModel.toggleFavorite(photo.id) },
                            onAddToCalendar = { photo ->
                                if (ActionCardIntentHelper.launchCalendarForPhoto(this@MainActivity, photo)) {
                                    housekeepingViewModel.markReminderCreated(photo.id)
                                }
                            },
                            onGetActionCards = { pId -> galleryViewModel.getActionCardsForPhoto(pId) },
                            onObserveActionCards = { pId -> galleryViewModel.observeActionCards(pId) }
                        )
                    }

                    galleryState.photoSettings?.let { photo ->
                        PhotoSettingsDialog(
                            photo = photo,
                            folders = folders,
                            categories = categories,
                            onDismiss = { galleryViewModel.closePhotoSettings() },
                            onSave = { title, description, folderId, categoryIds, isFav ->
                                galleryViewModel.updatePhotoDetails(photo.id, title, description, folderId, categoryIds, isFav)
                            },
                            onDelete = { galleryViewModel.setPhotoToDelete(it) },
                            onScanPhoto = { pId -> galleryViewModel.scanSinglePhoto(pId) },
                            onRestoreSinglePhoto = { photoToRestore -> vaultViewModel.restoreSinglePhoto(photoToRestore) },
                            onVaultSinglePhoto = { photoToVault -> vaultViewModel.vaultSinglePhoto(photoToVault) }
                        )
                    }

                    if (categoriesState.showCreateCategoryDialog) {
                        CreateCategoryDialog(
                            onDismiss = { categoriesViewModel.closeCreateCategoryDialog() },
                            onConfirm = { name, colorHex, iconName ->
                                categoriesViewModel.createCategory(name, colorHex, iconName)
                            }
                        )
                    }

                    categoriesState.retroactiveCategoryPrompt?.let { prompt ->
                        RetroactiveCategoryDialog(
                            prompt = prompt,
                            onDismiss = { categoriesViewModel.dismissRetroactiveCategoryPrompt() },
                            onConfirm = { categoriesViewModel.confirmRetroactiveCategoryMatch() }
                        )
                    }

                    categoriesState.retroactiveScanningCategory?.let { category ->
                        RetroactiveScanningDialog(category = category)
                    }

                    categoriesState.noMatchesCategory?.let { category ->
                        NoRetroactiveMatchesDialog(
                            category = category,
                            onDismiss = { categoriesViewModel.dismissNoMatchesDialog() }
                        )
                    }

                    categoriesState.categoryToEdit?.let { category ->
                        RenameCategoryDialog(
                            category = category,
                            onDismiss = { categoriesViewModel.setCategoryToEdit(null) },
                            onConfirm = { newName, newColor, newIcon ->
                                categoriesViewModel.renameCategory(category.id, newName, newColor, newIcon)
                            }
                        )
                    }

                    categoriesState.categoryToDelete?.let { category ->
                        ConfirmDeleteDialog(
                            title = "Xóa danh mục '${category.name}'?",
                            message = "Thẻ này sẽ bị xóa khỏi các ảnh đã gán thẻ. Ảnh của bạn sẽ không bị xóa.",
                            onDismiss = { categoriesViewModel.setCategoryToDelete(null) },
                            onConfirm = { categoriesViewModel.deleteCategory(category.id) }
                        )
                    }

                    galleryState.photoToDelete?.let { photo ->
                        ConfirmDeleteDialog(
                            title = "Xóa ảnh?",
                            message = "Bạn có chắc chắn muốn xóa '${photo.title}'? Hành động này không thể hoàn tác.",
                            onDismiss = { galleryViewModel.setPhotoToDelete(null) },
                            onConfirm = { galleryViewModel.deletePhotoConfirmed(photo) }
                        )
                    }
                }

                if (vaultState.showPrivacyConsentDialog) {
                    PrivacyConsentDialog(
                        onEnableVault = { vaultViewModel.onEnableVault() },
                        onKeepStandard = { vaultViewModel.onKeepStandard() }
                    )
                }

                if (vaultState.showAllFilesAccessDialog) {
                    AllFilesAccessDialog(
                        onOpenSettings = {
                            try {
                                startActivity(vaultViewModel.getManageAllFilesIntent())
                            } catch (_: Exception) {
                            }
                            vaultViewModel.dismissAllFilesAccessDialog()
                        },
                        onDismiss = { vaultViewModel.cancelAllFilesAccessDialog() }
                    )
                }

                if (vaultState.showRequireDeviceLockDialog) {
                    AlertDialog(
                        onDismissRequest = { vaultViewModel.dismissRequireDeviceLockDialog() },
                        title = { Text("Yêu cầu Khóa màn hình", fontWeight = FontWeight.Bold) },
                        text = {
                            Text("Để bảo vệ ảnh trong Két an toàn, thiết bị của bạn cần phải cài đặt sẵn Khóa màn hình (Mã PIN, Hình vẽ hoặc Vân tay). Vui lòng thiết lập khóa máy trước khi bật tính năng này.")
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    vaultViewModel.dismissRequireDeviceLockDialog()
                                    try {
                                        startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                                    } catch (_: Exception) {
                                        startActivity(Intent(Settings.ACTION_SETTINGS))
                                    }
                                }
                            ) {
                                Text("Cài đặt khóa máy")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { vaultViewModel.dismissRequireDeviceLockDialog() }) {
                                Text("Để sau")
                            }
                        }
                    )
                }

                if (vaultState.showEnableVaultScopeDialog) {
                    EnableVaultScopeDialog(
                        onConfirm = { vaultAll -> vaultViewModel.confirmEnableVault(vaultAll) },
                        onDismiss = { vaultViewModel.dismissEnableVaultScopeDialog() }
                    )
                }

                if (vaultState.showDisableVaultDialog) {
                    val protectedCount = photos.count {
                        it.filePath.endsWith(".enc") || (it.isScreenshot && !it.filePath.startsWith("content://"))
                    }
                    DisableVaultConfirmDialog(
                        onConfirm = { vaultViewModel.restoreAllVaultedPhotos() },
                        onDismiss = { vaultViewModel.dismissDisableVaultDialog() },
                        photoCount = protectedCount
                    )
                }

                if (vaultState.isRestoringPhotos) {
                    RestoreProgressDialog(
                        current = vaultState.restoreProgress.first,
                        total = vaultState.restoreProgress.second
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val photoId = intent?.getStringExtra(ScreenshotNotificationHelper.EXTRA_PHOTO_ID)
        if (!photoId.isNullOrBlank()) {
            viewModel.viewPhotoById(photoId)
        }
        val openHousekeeping = intent?.getBooleanExtra(ScreenshotNotificationHelper.EXTRA_OPEN_HOUSEKEEPING, false) == true ||
            intent?.getStringExtra(ScreenshotNotificationHelper.EXTRA_TAB) == "housekeeping"
        if (openHousekeeping) {
            viewModel.setActiveTab(AppTab.CLEANER)
        }

        val widgetActionCardName = intent?.getStringExtra(PhotoSearchWidgetProvider.EXTRA_ACTION_CARD_TYPE)
        if (!widgetActionCardName.isNullOrBlank()) {
            val cardType = try {
                ActionCardType.valueOf(widgetActionCardName)
            } catch (_: Exception) {
                null
            }
            if (cardType != null) {
                viewModel.setActiveTab(AppTab.PHOTOS)
                galleryViewModel.setActionCardFilter(cardType)
            }
        }
    }
}
