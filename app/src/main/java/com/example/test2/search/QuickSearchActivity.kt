package com.example.test2.search

import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.test2.di.AppViewModelFactory
import com.example.test2.presentation.gallery.GalleryViewModel
import com.example.test2.presentation.housekeeping.HousekeepingViewModel
import com.example.test2.presentation.vault.VaultUiEvent
import com.example.test2.presentation.vault.VaultViewModel
import com.example.test2.shared.models.ActionCardType
import com.example.test2.ui.ActionCardIntentHelper
import com.example.test2.ui.PhotoAppViewModel
import com.example.test2.ui.SystemBridgeHelper
import com.example.test2.ui.screens.ActionCardTabPill
import com.example.test2.ui.screens.AppLockScreen
import com.example.test2.ui.screens.ConfirmDeleteDialog
import com.example.test2.ui.screens.ImageViewerDialog
import com.example.test2.ui.screens.PhotoListRowCard
import com.example.test2.ui.screens.PhotoSettingsDialog
import com.example.test2.ui.theme.AppColors
import com.example.test2.ui.theme.Test2Theme
import kotlinx.coroutines.delay

/**
 * Dedicated fast search overlay launched from the Home Screen Search Widget.
 * Focuses search input and raises the IME immediately, enforces Privacy Vault AppLock,
 * and supports full FTS5 + Vector Semantic Search + Action Card filtering.
 */
class QuickSearchActivity : FragmentActivity() {

    private val viewModelFactory by lazy { AppViewModelFactory(application) }
    private val viewModel: PhotoAppViewModel by viewModels { viewModelFactory }
    private val galleryViewModel: GalleryViewModel by viewModels { viewModelFactory }
    private val vaultViewModel: VaultViewModel by viewModels { viewModelFactory }
    private val housekeepingViewModel: HousekeepingViewModel by viewModels { viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                val galleryState by galleryViewModel.uiState.collectAsState()
                val vaultState by vaultViewModel.uiState.collectAsState()

                LaunchedEffect(galleryState.error) {
                    galleryState.error?.let { msg ->
                        Toast.makeText(this@QuickSearchActivity, msg, Toast.LENGTH_SHORT).show()
                        galleryViewModel.dismissError()
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
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                LaunchedEffect(vaultViewModel) {
                    vaultViewModel.events.collect { event ->
                        if (event is VaultUiEvent.RequestBiometricUnlock) {
                            SystemBridgeHelper.authenticateWithBiometrics(
                                activity = this@QuickSearchActivity,
                                onSuccess = { vaultViewModel.unlockApp() },
                                noCredentialMessage = "Thiết bị chưa cài đặt mã khóa màn hình (PIN/Vân tay)."
                            )
                        }
                    }
                }

                if (vaultState.isVaultEnabled && !vaultState.isAppUnlocked) {
                    AppLockScreen(
                        onUnlockClick = { vaultViewModel.requestBiometricUnlock() },
                        onBackPressed = { finish() }
                    )
                } else {
                    val focusRequester = remember { FocusRequester() }
                    val keyboardController = LocalSoftwareKeyboardController.current
                    val currentFilter = galleryState.activeFilter
                    var searchTextFieldValue by remember {
                        mutableStateOf(
                            TextFieldValue(
                                currentFilter.searchQuery,
                                selection = TextRange(currentFilter.searchQuery.length)
                            )
                        )
                    }

                    LaunchedEffect(vaultState.isAppUnlocked) {
                        if (vaultState.isAppUnlocked) {
                            delay(80)
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    }

                    LaunchedEffect(currentFilter.searchQuery) {
                        if (currentFilter.searchQuery != searchTextFieldValue.text) {
                            searchTextFieldValue = TextFieldValue(
                                text = currentFilter.searchQuery,
                                selection = TextRange(currentFilter.searchQuery.length)
                            )
                        }
                    }

                    LaunchedEffect(searchTextFieldValue.text) {
                        val newText = searchTextFieldValue.text
                        if (newText != currentFilter.searchQuery) {
                            delay(150)
                            galleryViewModel.setSearchQuery(newText)
                        }
                    }

                    val hasQueryOrFilter = currentFilter.searchQuery.isNotBlank() ||
                        galleryState.activeActionCardType != ActionCardType.ALL
                    val filteredPhotos = galleryState.photos
                    val displayedPhotos = remember(filteredPhotos, hasQueryOrFilter) {
                        if (hasQueryOrFilter) filteredPhotos else filteredPhotos.take(25)
                    }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = WindowInsets.statusBars,
                        containerColor = AppColors.CanvasCream
                    ) { innerPadding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AppColors.CanvasCream)
                                    .padding(top = 8.dp, bottom = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { finish() }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Đóng",
                                            tint = AppColors.DeepPineText
                                        )
                                    }

                                    OutlinedTextField(
                                        value = searchTextFieldValue,
                                        onValueChange = { searchTextFieldValue = it },
                                        placeholder = {
                                            Text(
                                                text = if (galleryState.accountTier.isPro) {
                                                    "Tìm theo từ khóa hoặc ý nghĩa..."
                                                } else {
                                                    "Tìm ảnh, hóa đơn, địa chỉ..."
                                                },
                                                fontSize = 13.5.sp,
                                                color = AppColors.WarmMutedText,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Search,
                                                contentDescription = null,
                                                tint = AppColors.ForestTeal,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        trailingIcon = {
                                            if (searchTextFieldValue.text.isNotEmpty()) {
                                                IconButton(onClick = {
                                                    searchTextFieldValue = TextFieldValue("")
                                                    galleryViewModel.setSearchQuery("")
                                                }) {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "Xóa",
                                                        tint = AppColors.WarmMutedText
                                                    )
                                                }
                                            }
                                        },
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Text,
                                            imeAction = ImeAction.Search,
                                            hintLocales = LocaleList(Locale("vi"), Locale("en"))
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onSearch = {
                                                galleryViewModel.setSearchQuery(searchTextFieldValue.text)
                                                keyboardController?.hide()
                                            }
                                        ),
                                        singleLine = true,
                                        shape = RoundedCornerShape(16.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = AppColors.SurfaceAlabaster,
                                            unfocusedContainerColor = AppColors.SurfaceAlabaster,
                                            focusedBorderColor = AppColors.ForestTeal,
                                            unfocusedBorderColor = AppColors.BorderSand
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                            .focusRequester(focusRequester)
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (galleryState.accountTier.isPro) {
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(AppColors.PlumMutedLight)
                                                .border(1.dp, AppColors.PlumMuted.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (galleryState.isVectorIndexing) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(11.dp),
                                                    strokeWidth = 1.8.dp,
                                                    color = AppColors.PlumMuted
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = AppColors.PlumMuted,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (galleryState.isVectorIndexing) "Đang cập nhật chỉ mục Pro..." else "Tìm kiếm ngữ nghĩa Vector",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = AppColors.PlumMuted
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "Tìm kiếm từ khóa OCR cục bộ",
                                            fontSize = 11.sp,
                                            color = AppColors.WarmTaupeText
                                        )
                                    }

                                    Text(
                                        text = if (hasQueryOrFilter) {
                                            "${filteredPhotos.size} kết quả"
                                        } else {
                                            "Gần đây (${displayedPhotos.size}/${galleryState.allPhotosCount})"
                                        },
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppColors.WarmTaupeText
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ActionCardTabPill(
                                        label = "Tất cả",
                                        badgeCount = galleryState.allPhotosCount,
                                        color = AppColors.ForestTeal,
                                        isActive = galleryState.activeActionCardType == ActionCardType.ALL,
                                        onClick = { galleryViewModel.setActionCardFilter(ActionCardType.ALL) }
                                    )
                                    ActionCardTabPill(
                                        label = "📍 Địa chỉ",
                                        badgeCount = galleryState.actionCardPhotoCounts[ActionCardType.ADDRESS] ?: 0,
                                        color = AppColors.TerracottaAlert,
                                        isActive = galleryState.activeActionCardType == ActionCardType.ADDRESS,
                                        onClick = {
                                            galleryViewModel.setActionCardFilter(
                                                if (galleryState.activeActionCardType == ActionCardType.ADDRESS) ActionCardType.ALL else ActionCardType.ADDRESS
                                            )
                                        }
                                    )
                                    ActionCardTabPill(
                                        label = "💳 Chuyển khoản",
                                        badgeCount = galleryState.actionCardPhotoCounts[ActionCardType.PAYMENT] ?: 0,
                                        color = AppColors.OchreGold,
                                        isActive = galleryState.activeActionCardType == ActionCardType.PAYMENT,
                                        onClick = {
                                            galleryViewModel.setActionCardFilter(
                                                if (galleryState.activeActionCardType == ActionCardType.PAYMENT) ActionCardType.ALL else ActionCardType.PAYMENT
                                            )
                                        }
                                    )
                                    ActionCardTabPill(
                                        label = "👤 Liên hệ",
                                        badgeCount = galleryState.actionCardPhotoCounts[ActionCardType.CONTACT] ?: 0,
                                        color = AppColors.SageOlive,
                                        isActive = galleryState.activeActionCardType == ActionCardType.CONTACT,
                                        onClick = {
                                            galleryViewModel.setActionCardFilter(
                                                if (galleryState.activeActionCardType == ActionCardType.CONTACT) ActionCardType.ALL else ActionCardType.CONTACT
                                            )
                                        }
                                    )
                                }
                            }

                            if (displayedPhotos.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(18.dp))
                                                .background(AppColors.WarmSand),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Search,
                                                contentDescription = null,
                                                tint = AppColors.ForestTeal,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = if (hasQueryOrFilter) "Không tìm thấy ảnh phù hợp" else "Chưa có ảnh chụp màn hình nào",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AppColors.DeepPineText
                                        )
                                        if (hasQueryOrFilter) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = {
                                                    searchTextFieldValue = TextFieldValue("")
                                                    galleryViewModel.clearFilters()
                                                },
                                                shape = RoundedCornerShape(14.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.ForestTeal)
                                            ) {
                                                Text("Xóa tìm kiếm")
                                            }
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(displayedPhotos, key = { it.id }) { photo ->
                                        PhotoListRowCard(
                                            photo = photo,
                                            categories = galleryState.categories,
                                            semanticScore = galleryState.semanticMatchScores[photo.id],
                                            onClick = {
                                                keyboardController?.hide()
                                                galleryViewModel.viewPhoto(photo)
                                            },
                                            onToggleFavorite = {
                                                galleryViewModel.toggleFavorite(photo.id)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    galleryState.viewerPhotoId?.let { photoId ->
                        ImageViewerDialog(
                            photos = filteredPhotos,
                            initialPhotoId = photoId,
                            folders = galleryState.folders,
                            categories = galleryState.categories,
                            onDismiss = { galleryViewModel.closePhotoViewer() },
                            onOpenSettings = { galleryViewModel.openPhotoSettings(it) },
                            onToggleFavorite = { galleryViewModel.toggleFavorite(it.id) },
                            onAddToCalendar = { photo ->
                                if (ActionCardIntentHelper.launchCalendarForPhoto(this@QuickSearchActivity, photo)) {
                                    housekeepingViewModel.markReminderCreated(photo.id)
                                }
                            },
                            onGetActionCards = { galleryViewModel.getActionCardsForPhoto(it) },
                            onObserveActionCards = { galleryViewModel.observeActionCards(it) }
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
                            onScanPhoto = { galleryViewModel.scanSinglePhoto(it) },
                            onRestoreSinglePhoto = { vaultViewModel.restoreSinglePhoto(it) },
                            onVaultSinglePhoto = { vaultViewModel.vaultSinglePhoto(it) }
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
            }
        }
    }
}
