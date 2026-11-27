package com.example.test2

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.example.test2.presentation.vault.VaultUiEvent
import com.example.test2.presentation.vault.VaultViewModel
import com.example.test2.ui.ActionCardIntentHelper
import com.example.test2.ui.AppTab
import com.example.test2.ui.PhotoAppViewModel
import com.example.test2.ui.SystemBridgeHelper
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
import com.example.test2.ui.screens.SettingsScreen
import com.example.test2.ui.components.LiquidWaveBottomBar
import com.example.test2.ui.theme.AppColors
import com.example.test2.ui.theme.Test2Theme
import com.example.test2.widget.PhotoSearchWidgetProvider
import com.example.test2.widget.PhotoSearchWidgetUpdater
import com.example.test2.search.QuickSearchActivity

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

                LaunchedEffect(galleryState.error) {
                    galleryState.error?.let { msg ->
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                        galleryViewModel.dismissError()
                    }
                }

                LaunchedEffect(categoriesState.error) {
                    categoriesState.error?.let { msg ->
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                        categoriesViewModel.dismissError()
                    }
                }

                LaunchedEffect(housekeepingState.error) {
                    housekeepingState.error?.let { msg ->
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                        housekeepingViewModel.dismissError()
                    }
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

                LaunchedEffect(vaultViewModel) {
                    vaultViewModel.events.collect { event ->
                        when (event) {
                            VaultUiEvent.RequestBiometricUnlock -> {
                                SystemBridgeHelper.authenticateWithBiometrics(
                                    activity = this@MainActivity,
                                    onSuccess = { vaultViewModel.unlockApp() }
                                )
                            }
                            VaultUiEvent.OpenAllFilesSettings -> {
                                SystemBridgeHelper.openManageAllFilesSettings(this@MainActivity)
                            }
                            VaultUiEvent.OpenDeviceLockSettings -> {
                                SystemBridgeHelper.openDeviceLockSettings(this@MainActivity)
                            }
                        }
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
                                housekeepingViewModel.refreshAgingScreenshots(
                                    runDailyCatchUp = viewModel.hasStoragePermission.value
                                )
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
                        galleryViewModel.importPhotos(uris.map { it.toString() })
                    }
                }

                if (vaultState.isVaultEnabled && !vaultState.isAppUnlocked) {
                    AppLockScreen(
                        onUnlockClick = { vaultViewModel.requestBiometricUnlock() },
                        onBackPressed = { finish() }
                    )
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = AppColors.CanvasCream,
                        contentWindowInsets = WindowInsets.statusBars,
                        bottomBar = {
                            LiquidWaveBottomBar(
                                activeTab = mainState.activeTab,
                                onSelectTab = { viewModel.setActiveTab(it) }
                            )
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            AnimatedContent(
                                targetState = mainState.activeTab,
                                transitionSpec = {
                                    val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                                    (slideInHorizontally(animationSpec = tween(280)) { it / 6 * dir } +
                                        fadeIn(animationSpec = tween(260))) togetherWith
                                        (slideOutHorizontally(animationSpec = tween(240)) { -it / 6 * dir } +
                                            fadeOut(animationSpec = tween(220)))
                                },
                                label = "mainTabTransition"
                            ) { activeTab ->
                                when (activeTab) {
                                    AppTab.PHOTOS -> {
                                        PhotoGalleryScreen(
                                            photos = galleryState.photos,
                                            allPhotosCount = galleryState.allPhotosCount,
                                            folders = galleryState.folders,
                                            categories = galleryState.categories,
                                            currentFilter = galleryState.activeFilter,
                                            hasStoragePermission = mainState.hasStoragePermission,
                                            isScanning = mainState.isScanning,
                                            pendingAiCount = mainState.pendingAiCount,
                                            accountTier = galleryState.accountTier,
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
                                            categories = categoriesState.categories,
                                            photos = categoriesState.photos,
                                            isPro = categoriesState.isPro,
                                            onSelectCategory = { categoryId -> categoriesViewModel.selectCategory(categoryId) },
                                            onCreateCategoryClick = { categoriesViewModel.openCreateCategoryDialog() },
                                            onEditCategoryClick = { category -> categoriesViewModel.setCategoryToEdit(category) },
                                            onDeleteCategoryClick = { category -> categoriesViewModel.setCategoryToDelete(category) },
                                            onAutoCategorizeClick = { category -> categoriesViewModel.triggerRetroactiveCategoryScan(category) }
                                        )
                                    }
                                    AppTab.CLEANER -> {
                                        HousekeepingScreen(
                                            photos = housekeepingState.photos,
                                            accountTier = housekeepingState.accountTier,
                                            agingSettings = housekeepingState.agingSettings,
                                            agingPhotos = housekeepingState.agingPhotos,
                                            categories = housekeepingState.categories,
                                            isEventsExpanded = housekeepingState.isEventsExpanded,
                                            onToggleEventsExpanded = { housekeepingViewModel.toggleEventsExpanded() },
                                            isClutterExpanded = housekeepingState.isClutterExpanded,
                                            onToggleClutterExpanded = { housekeepingViewModel.toggleClutterExpanded() },
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
                                            onSetDefaultRetentionDays = { days ->
                                                housekeepingViewModel.setDefaultRetentionDays(days)
                                            },
                                            agingMonitorInfo = housekeepingState.agingMonitorInfo,
                                            onRunAgingScanNow = { housekeepingViewModel.runAgingScanNow() }
                                        )
                                    }
                                    AppTab.SETTINGS -> {
                                        SettingsScreen(
                                            accountTier = housekeepingState.accountTier,
                                            isVaultEnabled = vaultState.isVaultEnabled,
                                            protectedPhotosCount = vaultState.protectedPhotoCount,
                                            onToggleTier = { galleryViewModel.toggleUserTier(it) },
                                            onToggleVault = { vaultViewModel.setVaultEnabled(it) },
                                            onRequestRestorePhotos = { vaultViewModel.requestDisableVault() },
                                            onPinWidget = {
                                                val pinned = PhotoSearchWidgetUpdater.requestPinWidget(this@MainActivity)
                                                if (!pinned) {
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        "Nhấn giữ màn hình chính → Widgets → MemoCap để thêm thanh tìm kiếm.",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            },
                                            onOpenQuickSearch = {
                                                startActivity(Intent(this@MainActivity, QuickSearchActivity::class.java))
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    galleryState.viewerPhotoId?.let { photoId ->
                        ImageViewerDialog(
                            photos = galleryState.photos,
                            initialPhotoId = photoId,
                            folders = galleryState.folders,
                            categories = galleryState.categories,
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
                            folders = galleryState.folders,
                            categories = galleryState.categories,
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
                        onOpenSettings = { vaultViewModel.openAllFilesSettings() },
                        onDismiss = { vaultViewModel.cancelAllFilesAccessDialog() }
                    )
                }

                if (vaultState.showRequireDeviceLockDialog) {
                    AlertDialog(
                        onDismissRequest = { vaultViewModel.dismissRequireDeviceLockDialog() },
                        containerColor = AppColors.SurfaceAlabaster,
                        title = { Text("Yêu cầu Khóa màn hình", fontWeight = FontWeight.Bold, color = AppColors.DeepPineText) },
                        text = {
                            Text(
                                "Để bảo vệ ảnh trong Két an toàn, thiết bị của bạn cần phải cài đặt sẵn Khóa màn hình (Mã PIN, Hình vẽ hoặc Vân tay). Vui lòng thiết lập khóa máy trước khi bật tính năng này.",
                                color = AppColors.WarmTaupeText
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = { vaultViewModel.openDeviceLockSettings() },
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.ForestTeal)
                            ) {
                                Text("Cài đặt khóa máy")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { vaultViewModel.dismissRequireDeviceLockDialog() }) {
                                Text("Để sau", color = AppColors.WarmTaupeText)
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
                    DisableVaultConfirmDialog(
                        onConfirm = { vaultViewModel.restoreAllVaultedPhotos() },
                        onDismiss = { vaultViewModel.dismissDisableVaultDialog() },
                        photoCount = vaultState.protectedPhotoCount
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
        val openHousekeeping = intent?.getBooleanExtra(ScreenshotNotificationHelper.EXTRA_OPEN_HOUSEKEEPING, false) == true ||
            intent?.getStringExtra(ScreenshotNotificationHelper.EXTRA_TAB) == "housekeeping"
        val widgetActionCardName = intent?.getStringExtra(PhotoSearchWidgetProvider.EXTRA_ACTION_CARD_TYPE)
        viewModel.handleLaunchExtras(
            photoId = photoId,
            openHousekeeping = openHousekeeping,
            widgetActionCardName = widgetActionCardName
        )
    }
}
