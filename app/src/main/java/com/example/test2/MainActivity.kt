package com.example.test2

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.test2.ai.SmartHeuristicParser
import com.example.test2.database.DefaultCategories
import com.example.test2.notifications.ScreenshotNotificationHelper
import com.example.test2.ui.AppTab
import com.example.test2.ui.PhotoAppViewModel
import com.example.test2.ui.screens.CategoriesScreen
import com.example.test2.ui.screens.ConfirmDeleteDialog
import com.example.test2.ui.screens.CreateCategoryDialog
import com.example.test2.ui.screens.HousekeepingScreen
import com.example.test2.ui.screens.ImageViewerDialog
import com.example.test2.ui.screens.PhotoGalleryScreen
import com.example.test2.services.AgingJobScheduler
import com.example.test2.services.ScreenshotJobScheduler
import com.example.test2.services.ScreenshotWatcherService
import com.example.test2.ui.screens.NoRetroactiveMatchesDialog
import com.example.test2.ui.screens.PhotoSettingsDialog
import com.example.test2.ui.screens.RenameCategoryDialog
import com.example.test2.ui.screens.RetroactiveCategoryDialog
import com.example.test2.ui.screens.RetroactiveScanningDialog
import com.example.test2.security.DeviceAuthManager
import com.example.test2.security.PrivacyVaultManager
import com.example.test2.ui.screens.AllFilesAccessDialog
import com.example.test2.ui.screens.AppLockScreen
import com.example.test2.ui.screens.DisableVaultConfirmDialog
import com.example.test2.ui.screens.EnableVaultScopeDialog
import com.example.test2.ui.screens.PrivacyConsentDialog
import com.example.test2.ui.screens.RestoreProgressDialog
import com.example.test2.ui.theme.Test2Theme

class MainActivity : FragmentActivity() {
    private val viewModel: PhotoAppViewModel by viewModels()

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
                val activeTab by viewModel.activeTab.collectAsState()
                val photos by viewModel.photos.collectAsState()
                val filteredPhotos by viewModel.filteredPhotos.collectAsState()
                val folders by viewModel.folders.collectAsState()
                val categories by viewModel.categories.collectAsState()
                val currentFilter by viewModel.currentFilter.collectAsState()
                val viewingPhotoId by viewModel.viewingPhotoId.collectAsState()
                val photoSettings by viewModel.photoSettings.collectAsState()
                val hasStoragePermission by viewModel.hasStoragePermission.collectAsState()
                val isScanning by viewModel.isScanning.collectAsState()
                val pendingAiCount by viewModel.pendingAiCount.collectAsState()
                val accountTier by viewModel.accountTier.collectAsState()
                val agingSettings by viewModel.agingSettings.collectAsState()
                val agingPhotos = remember(photos, agingSettings) { viewModel.getAgingScreenshots() }
                val displayMode by viewModel.displayMode.collectAsState()

                // Dialog states
                val showCreateCategoryDialog by viewModel.showCreateCategoryDialog.collectAsState()
                val categoryToEdit by viewModel.categoryToEdit.collectAsState()
                val photoToDelete by viewModel.photoToDelete.collectAsState()
                val categoryToDelete by viewModel.categoryToDelete.collectAsState()
                val semanticMatchScores by viewModel.semanticMatchScores.collectAsState()
                val isVectorIndexing by viewModel.isVectorIndexing.collectAsState()
                val agingMonitorInfo by viewModel.agingMonitorInfo.collectAsState()
                val activeActionCardType by viewModel.activeActionCardType.collectAsState()
                val actionCardPhotoCounts by viewModel.actionCardPhotoCounts.collectAsState()
                val retroactiveCategoryPrompt by viewModel.retroactiveCategoryPrompt.collectAsState()
                val retroactiveScanningCategory by viewModel.retroactiveScanningCategory.collectAsState()
                val noMatchesCategory by viewModel.noMatchesCategory.collectAsState()

                // Privacy Vault & Device Lock states
                val isVaultEnabled by viewModel.isVaultEnabled.collectAsState()
                val isAppUnlocked by viewModel.isAppUnlocked.collectAsState()
                val showPrivacyConsentDialog by viewModel.showPrivacyConsentDialog.collectAsState()
                val showAllFilesAccessDialog by viewModel.showAllFilesAccessDialog.collectAsState()
                val showEnableVaultScopeDialog by viewModel.showEnableVaultScopeDialog.collectAsState()
                val showDisableVaultDialog by viewModel.showDisableVaultDialog.collectAsState()
                val isRestoringPhotos by viewModel.isRestoringPhotos.collectAsState()
                val restoreProgress by viewModel.restoreProgress.collectAsState()
                val restoreSuccessBanner by viewModel.showRestoreSuccessBanner.collectAsState()

                LaunchedEffect(restoreSuccessBanner) {
                    restoreSuccessBanner?.let { msg ->
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                        viewModel.dismissRestoreSuccessBanner()
                    }
                }

                // Permission launcher (Storage + Notifications)
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

                // Request permissions automatically on app start if not already granted
                LaunchedEffect(Unit) {
                    if (!hasStoragePermission) {
                        permissionLauncher.launch(permissionsToRequest)
                    }
                }

                // Observe Lifecycle ON_RESUME so when user takes a photo or resumes the app,
                // photos are automatically updated immediately without restart!
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> {
                                if (viewModel.hasStoragePermission.value) {
                                    viewModel.checkVaultPermissionResumed()
                                    viewModel.scanDevicePhotos()
                                    viewModel.ensureVectorsIndexed()
                                }
                            }
                            Lifecycle.Event.ON_STOP -> {
                                if (viewModel.isVaultEnabled.value) {
                                    viewModel.lockApp()
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

                // Photo picker launcher (manual fallback / addition)
                val photoPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickMultipleVisualMedia()
                ) { uris ->
                    if (uris.isNotEmpty()) {
                        viewModel.importPhotos(uris)
                    }
                }

                if (isVaultEnabled && !isAppUnlocked) {
                    AppLockScreen(
                        onUnlockClick = {
                            DeviceAuthManager.authenticate(
                                activity = this@MainActivity,
                                onSuccess = { viewModel.unlockApp() }
                            )
                        },
                        onBackPressed = {
                            finish()
                        }
                    )
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.statusBars,
                    bottomBar = {
                        // Bottom Navigation matching index.html
                        NavigationBar(
                            containerColor = Color.White,
                            tonalElevation = 8.dp
                        ) {
                            NavigationBarItem(
                                selected = activeTab == AppTab.PHOTOS,
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
                                        fontWeight = if (activeTab == AppTab.PHOTOS) FontWeight.Bold else FontWeight.Medium
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
                                selected = activeTab == AppTab.CATEGORIES,
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
                                        fontWeight = if (activeTab == AppTab.CATEGORIES) FontWeight.Bold else FontWeight.Medium
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

                            // Smart Assistant & Housekeeping Cleaner (IDEA.md)
                            NavigationBarItem(
                                selected = activeTab == AppTab.CLEANER,
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
                                        fontWeight = if (activeTab == AppTab.CLEANER) FontWeight.Bold else FontWeight.Medium
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
                        when (activeTab) {
                            AppTab.PHOTOS -> {
                                PhotoGalleryScreen(
                                    photos = filteredPhotos,
                                    allPhotosCount = photos.size,
                                    folders = folders,
                                    categories = categories,
                                    currentFilter = currentFilter,
                                    hasStoragePermission = hasStoragePermission,
                                    isScanning = isScanning,
                                    pendingAiCount = pendingAiCount,
                                    accountTier = accountTier,
                                    displayMode = displayMode,
                                    onDisplayModeChange = { viewModel.setDisplayMode(it) },
                                    onRequestPermission = {
                                        permissionLauncher.launch(permissionsToRequest)
                                    },
                                    // When pressing an image, show the image viewer directly!
                                    onPhotoClick = { photo ->
                                        viewModel.viewPhoto(photo)
                                    },
                                    onToggleFavorite = { photoId ->
                                        viewModel.toggleFavorite(photoId)
                                    },
                                    onToggleFavoriteFilter = {
                                        viewModel.toggleFavoriteFilter()
                                    },
                                    onSelectFolder = { viewModel.filterByFolder(it) },
                                    onSelectCategory = { viewModel.filterByCategory(it) },
                                    onSearchChange = { viewModel.setSearchQuery(it) },
                                    onClearFilters = { viewModel.clearFilters() },
                                    activeActionCardType = activeActionCardType,
                                    actionCardPhotoCounts = actionCardPhotoCounts,
                                    onSelectActionCardType = { viewModel.setActionCardFilter(it) },
                                    semanticMatchScores = semanticMatchScores,
                                    isVectorIndexing = isVectorIndexing,
                                    onToggleTier = { viewModel.toggleUserTier(it) },
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
                                    onSelectCategory = { categoryId ->
                                        viewModel.filterByCategory(categoryId)
                                    },
                                    onCreateCategoryClick = { viewModel.openCreateCategoryDialog() },
                                    onEditCategoryClick = { category ->
                                        viewModel.setCategoryToEdit(category)
                                    },
                                    onDeleteCategoryClick = { category ->
                                        viewModel.setCategoryToDelete(category)
                                    },
                                    onAutoCategorizeClick = { category ->
                                        viewModel.triggerRetroactiveCategoryScan(category)
                                    }
                                )
                            }
                            AppTab.CLEANER -> {
                                HousekeepingScreen(
                                    photos = photos,
                                    accountTier = accountTier,
                                    agingSettings = agingSettings,
                                    agingPhotos = agingPhotos,
                                    categories = categories,
                                    isVaultEnabled = isVaultEnabled,
                                    onToggleVault = { viewModel.setVaultEnabled(it) },
                                    onRequestRestorePhotos = { viewModel.requestDisableVault() },
                                    onToggleTier = { viewModel.toggleUserTier(it) },
                                    onCleanClutter = { viewModel.cleanAgingScreenshots(it) },
                                    onDeleteSinglePhoto = { viewModel.setPhotoToDelete(it) },
                                    onAddToCalendar = { photo ->
                                        val isShopping = photo.detectedIntent == "SHOPPING" || photo.categoryIds.contains(DefaultCategories.TO_BUY)
                                        val time = photo.suggestedEventDateMillis ?: if (isShopping) {
                                            System.currentTimeMillis() + 24 * 3600_000L
                                        } else {
                                            System.currentTimeMillis() + 3600_000L
                                        }
                                        val calTitle = if (isShopping) {
                                            val rawTitle = photo.suggestedEventTitle ?: photo.title
                                            if (rawTitle.startsWith("Mua sắm:", ignoreCase = true)) rawTitle else "Mua sắm: $rawTitle"
                                        } else {
                                            photo.suggestedEventTitle ?: photo.title
                                        }
                                        val calDescription = photo.suggestedEventDescription
                                            ?: com.example.test2.ai.SmartHeuristicParser.generateStructuredDescription(
                                                text = photo.piiScrubbedText ?: photo.description,
                                                intent = photo.detectedIntent ?: "OTHER",
                                                title = calTitle
                                            )
                                        val intent = SmartHeuristicParser.createCalendarIntent(
                                            title = calTitle,
                                            startTimeMillis = time,
                                            description = calDescription
                                        )
                                        try {
                                            startActivity(intent)
                                            viewModel.markReminderCreated(photo.id)
                                        } catch (_: Exception) {
                                        }
                                    },
                                    onPhotoClick = { photo -> viewModel.viewPhoto(photo) },
                                    onSetCategoryRetentionRule = { catId, days ->
                                        viewModel.setCategoryRetentionRule(catId, days)
                                    },
                                    agingMonitorInfo = agingMonitorInfo,
                                    onRunAgingScanNow = { viewModel.runAgingScanNow() }
                                )
                            }
                        }
                    }
                }

                // 1. Swipeable Full-Screen Image Viewer across current tab/view
                viewingPhotoId?.let { photoId ->
                    ImageViewerDialog(
                        photos = filteredPhotos,
                        initialPhotoId = photoId,
                        folders = folders,
                        categories = categories,
                        onDismiss = { viewModel.closePhotoViewer() },
                        onOpenSettings = { photo ->
                            viewModel.openPhotoSettings(photo)
                        },
                        onToggleFavorite = { photo ->
                            viewModel.toggleFavorite(photo.id)
                        },
                        onAddToCalendar = { photo ->
                            val isShopping = photo.detectedIntent == "SHOPPING" || photo.categoryIds.contains(DefaultCategories.TO_BUY)
                            val time = photo.suggestedEventDateMillis ?: if (isShopping) {
                                System.currentTimeMillis() + 24 * 3600_000L
                            } else {
                                System.currentTimeMillis() + 3600_000L
                            }
                            val calTitle = if (isShopping) {
                                val rawTitle = photo.suggestedEventTitle ?: photo.title
                                if (rawTitle.startsWith("Mua sắm:", ignoreCase = true)) rawTitle else "Mua sắm: $rawTitle"
                            } else {
                                photo.suggestedEventTitle ?: photo.title
                            }
                            val calDescription = photo.suggestedEventDescription
                                ?: com.example.test2.ai.SmartHeuristicParser.generateStructuredDescription(
                                    text = photo.piiScrubbedText ?: photo.description,
                                    intent = photo.detectedIntent ?: "OTHER",
                                    title = calTitle
                                )
                            val intent = SmartHeuristicParser.createCalendarIntent(
                                title = calTitle,
                                startTimeMillis = time,
                                description = calDescription
                            )
                            try {
                                startActivity(intent)
                                viewModel.markReminderCreated(photo.id)
                            } catch (_: Exception) {
                            }
                        },
                        onGetActionCards = { pId ->
                            viewModel.getActionCardsForPhoto(pId)
                        }
                    )
                }

                // 2. Photo Settings Dialog: shown only when user chooses Settings inside the viewer
                photoSettings?.let { photo ->
                    PhotoSettingsDialog(
                        photo = photo,
                        folders = folders,
                        categories = categories,
                        onDismiss = { viewModel.closePhotoSettings() },
                        onSave = { title, description, folderId, categoryIds, isFav ->
                            viewModel.updatePhotoDetails(photo.id, title, description, folderId, categoryIds, isFav)
                        },
                        onDelete = {
                            viewModel.setPhotoToDelete(it)
                        },
                        onScanPhoto = { photoId ->
                            viewModel.scanSinglePhoto(photoId)
                        }
                    )
                }

                // Create Category Dialog
                if (showCreateCategoryDialog) {
                    CreateCategoryDialog(
                        onDismiss = { viewModel.closeCreateCategoryDialog() },
                        onConfirm = { name, colorHex, iconName ->
                            viewModel.createCategory(name, colorHex, iconName)
                        }
                    )
                }

                // Retroactive Auto-Categorization Prompt Dialog
                retroactiveCategoryPrompt?.let { prompt ->
                    RetroactiveCategoryDialog(
                        prompt = prompt,
                        onDismiss = { viewModel.dismissRetroactiveCategoryPrompt() },
                        onConfirm = { viewModel.confirmRetroactiveCategoryMatch() }
                    )
                }

                // Retroactive Scanning Indicator Dialog (IDEA UX enhancement)
                retroactiveScanningCategory?.let { category ->
                    RetroactiveScanningDialog(category = category)
                }

                // No Matches Found Notice Dialog
                noMatchesCategory?.let { category ->
                    NoRetroactiveMatchesDialog(
                        category = category,
                        onDismiss = { viewModel.dismissNoMatchesDialog() }
                    )
                }

                // Edit Category Dialog
                categoryToEdit?.let { category ->
                    RenameCategoryDialog(
                        category = category,
                        onDismiss = { viewModel.setCategoryToEdit(null) },
                        onConfirm = { newName, newColor, newIcon ->
                            viewModel.renameCategory(category.id, newName, newColor, newIcon)
                        }
                    )
                }

                // Delete Category Confirmation Dialog
                categoryToDelete?.let { category ->
                    ConfirmDeleteDialog(
                        title = "Xóa danh mục '${category.name}'?",
                        message = "Thẻ này sẽ bị xóa khỏi các ảnh đã gán thẻ. Ảnh của bạn sẽ không bị xóa.",
                        onDismiss = { viewModel.setCategoryToDelete(null) },
                        onConfirm = { viewModel.deleteCategory(category.id) }
                    )
                }

                // Delete Photo Confirmation Dialog
                photoToDelete?.let { photo ->
                    ConfirmDeleteDialog(
                        title = "Xóa ảnh?",
                        message = "Bạn có chắc chắn muốn xóa '${photo.title}'? Hành động này không thể hoàn tác.",
                        onDismiss = { viewModel.setPhotoToDelete(null) },
                        onConfirm = { viewModel.deletePhotoConfirmed(photo) }
                    )
                }
            }

            // Privacy Consent Dialog (prompted once after permissions granted)
            if (showPrivacyConsentDialog) {
                PrivacyConsentDialog(
                    onEnableVault = { viewModel.onEnableVault() },
                    onKeepStandard = { viewModel.onKeepStandard() }
                )
            }

            // All Files Access Explanation Dialog (for silent automated eviction)
            if (showAllFilesAccessDialog) {
                AllFilesAccessDialog(
                    onOpenSettings = {
                        val settingsIntent = PrivacyVaultManager.getManageAllFilesIntent(this@MainActivity)
                        try {
                            startActivity(settingsIntent)
                        } catch (_: Exception) {
                        }
                        viewModel.dismissAllFilesAccessDialog()
                    },
                    onDismiss = { viewModel.dismissAllFilesAccessDialog() }
                )
            }

            // Enable Vault Scope Selection Dialog
            if (showEnableVaultScopeDialog) {
                EnableVaultScopeDialog(
                    onConfirm = { vaultAll -> viewModel.confirmEnableVault(vaultAll) },
                    onDismiss = { viewModel.dismissEnableVaultScopeDialog() }
                )
            }

            // Restore / Disable Vault Confirmation Dialog
            if (showDisableVaultDialog) {
                val protectedCount = photos.count {
                    it.filePath.endsWith(".enc") || (it.isScreenshot && !it.filePath.startsWith("content://"))
                }
                DisableVaultConfirmDialog(
                    onConfirm = { viewModel.restoreAllVaultedPhotos() },
                    onDismiss = { viewModel.dismissDisableVaultDialog() },
                    photoCount = protectedCount
                )
            }

            // Restore in progress Dialog
            if (isRestoringPhotos) {
                RestoreProgressDialog(
                    current = restoreProgress.first,
                    total = restoreProgress.second
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
    }
}