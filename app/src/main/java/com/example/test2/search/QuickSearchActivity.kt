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
import com.example.test2.PhotoApp
import com.example.test2.di.AppViewModelFactory
import com.example.test2.presentation.gallery.GalleryViewModel
import com.example.test2.presentation.housekeeping.HousekeepingViewModel
import com.example.test2.presentation.vault.VaultViewModel
import com.example.test2.shared.models.ActionCardType
import com.example.test2.ui.ActionCardIntentHelper
import com.example.test2.ui.PhotoAppViewModel
import com.example.test2.ui.screens.ActionCardTabPill
import com.example.test2.ui.screens.AppLockScreen
import com.example.test2.ui.screens.ConfirmDeleteDialog
import com.example.test2.ui.screens.ImageViewerDialog
import com.example.test2.ui.screens.PhotoListRowCard
import com.example.test2.ui.screens.PhotoSettingsDialog
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

                val photos by galleryViewModel.photos.collectAsState()
                val folders by galleryViewModel.folders.collectAsState()
                val categories by galleryViewModel.categories.collectAsState()
                val accountTier by galleryViewModel.accountTier.collectAsState()

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

                if (vaultState.isVaultEnabled && !vaultState.isAppUnlocked) {
                    AppLockScreen(
                        onUnlockClick = {
                            vaultViewModel.authenticate(
                                activity = this@QuickSearchActivity,
                                onSuccess = { vaultViewModel.unlockApp() },
                                onError = { errorCode, _ ->
                                    if (errorCode == androidx.biometric.BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL) {
                                        Toast.makeText(
                                            this@QuickSearchActivity,
                                            "Thiết bị chưa cài đặt mã khóa màn hình (PIN/Vân tay).",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            )
                        },
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
                        containerColor = Color(0xFFF8FAFC)
                    ) { innerPadding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White)
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
                                            tint = Color(0xFF0F172A)
                                        )
                                    }

                                    OutlinedTextField(
                                        value = searchTextFieldValue,
                                        onValueChange = { searchTextFieldValue = it },
                                        placeholder = {
                                            Text(
                                                text = if (accountTier.isPro) {
                                                    "Tìm theo từ khóa hoặc ý nghĩa..."
                                                } else {
                                                    "Tìm ảnh, hóa đơn, địa chỉ..."
                                                },
                                                fontSize = 13.5.sp,
                                                color = Color(0xFF94A3B8),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Search,
                                                contentDescription = null,
                                                tint = Color(0xFF2563EB),
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
                                                        tint = Color(0xFF94A3B8)
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
                                        shape = RoundedCornerShape(14.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color(0xFFF8FAFC),
                                            unfocusedContainerColor = Color(0xFFF8FAFC),
                                            focusedBorderColor = Color(0xFF2563EB),
                                            unfocusedBorderColor = Color(0xFFE2E8F0)
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
                                    if (accountTier.isPro) {
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFF3E8FF))
                                                .border(1.dp, Color(0xFFD8B4FE), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (galleryState.isVectorIndexing) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(11.dp),
                                                    strokeWidth = 1.8.dp,
                                                    color = Color(0xFF7E22CE)
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = Color(0xFF7E22CE),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (galleryState.isVectorIndexing) "Đang cập nhật chỉ mục Pro..." else "Tìm kiếm ngữ nghĩa Vector",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF6B21A8)
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "Tìm kiếm từ khóa OCR cục bộ",
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }

                                    Text(
                                        text = if (hasQueryOrFilter) {
                                            "${filteredPhotos.size} kết quả"
                                        } else {
                                            "Gần đây (${displayedPhotos.size}/${photos.size})"
                                        },
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF475569)
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
                                        badgeCount = photos.size,
                                        color = Color(0xFF2563EB),
                                        isActive = galleryState.activeActionCardType == ActionCardType.ALL,
                                        onClick = { galleryViewModel.setActionCardFilter(ActionCardType.ALL) }
                                    )
                                    ActionCardTabPill(
                                        label = "📍 Địa chỉ",
                                        badgeCount = galleryState.actionCardPhotoCounts[ActionCardType.ADDRESS] ?: 0,
                                        color = Color(0xFFEF4444),
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
                                        color = Color(0xFF0EA5E9),
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
                                        color = Color(0xFF8B5CF6),
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
                                                .clip(CircleShape)
                                                .background(Color(0xFFEFF6FF)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Search,
                                                contentDescription = null,
                                                tint = Color(0xFF2563EB),
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = if (hasQueryOrFilter) "Không tìm thấy ảnh phù hợp" else "Chưa có ảnh chụp màn hình nào",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                        if (hasQueryOrFilter) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = {
                                                    searchTextFieldValue = TextFieldValue("")
                                                    galleryViewModel.clearFilters()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
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
                                            categories = categories,
                                            semanticScore = galleryState.semanticMatchScores[photo.id],
                                            onClick = {
                                                keyboardController?.hide()
                                                galleryViewModel.viewPhoto(photo)
                                            },
                                            onToggleFavorite = {
                                                galleryViewModel.toggleFavorite(photo.id)
                                                PhotoApp.markMetadataDirty()
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
                            folders = folders,
                            categories = categories,
                            onDismiss = { galleryViewModel.closePhotoViewer() },
                            onOpenSettings = { galleryViewModel.openPhotoSettings(it) },
                            onToggleFavorite = {
                                galleryViewModel.toggleFavorite(it.id)
                                PhotoApp.markMetadataDirty()
                            },
                            onAddToCalendar = { photo ->
                                if (ActionCardIntentHelper.launchCalendarForPhoto(this@QuickSearchActivity, photo)) {
                                    housekeepingViewModel.markReminderCreated(photo.id)
                                    PhotoApp.markMetadataDirty()
                                }
                            },
                            onGetActionCards = { galleryViewModel.getActionCardsForPhoto(it) },
                            onObserveActionCards = { galleryViewModel.observeActionCards(it) }
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
                                PhotoApp.markMetadataDirty()
                            },
                            onDelete = { galleryViewModel.setPhotoToDelete(it) },
                            onScanPhoto = {
                                galleryViewModel.scanSinglePhoto(it)
                                PhotoApp.markMetadataDirty()
                            },
                            onRestoreSinglePhoto = {
                                vaultViewModel.restoreSinglePhoto(it)
                                PhotoApp.markMetadataDirty()
                            },
                            onVaultSinglePhoto = {
                                vaultViewModel.vaultSinglePhoto(it)
                                PhotoApp.markMetadataDirty()
                            }
                        )
                    }

                    galleryState.photoToDelete?.let { photo ->
                        ConfirmDeleteDialog(
                            title = "Xóa ảnh?",
                            message = "Bạn có chắc chắn muốn xóa '${photo.title}'? Hành động này không thể hoàn tác.",
                            onDismiss = { galleryViewModel.setPhotoToDelete(null) },
                            onConfirm = {
                                galleryViewModel.deletePhotoConfirmed(photo)
                                PhotoApp.markMetadataDirty()
                            }
                        )
                    }
                }
            }
        }
    }
}
