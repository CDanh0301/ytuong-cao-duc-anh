package com.example.test2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.test2.shared.models.ActionCardType
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.DefaultCategories
import com.example.test2.shared.models.Folder
import com.example.test2.shared.models.Photo
import com.example.test2.shared.models.PhotoFilter
import com.example.test2.shared.models.UserAccountTier
import com.example.test2.ui.GalleryDisplayMode
import com.example.test2.ui.components.AppScreenHeader
import com.example.test2.ui.theme.AppColors
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun PhotoGalleryScreen(
    photos: List<Photo>,
    allPhotosCount: Int,
    folders: List<Folder>,
    categories: List<Category>,
    currentFilter: PhotoFilter,
    hasStoragePermission: Boolean,
    isScanning: Boolean,
    pendingAiCount: Int = 0,
    accountTier: UserAccountTier = UserAccountTier(),
    semanticMatchScores: Map<String, Float> = emptyMap(),
    isVectorIndexing: Boolean = false,
    displayMode: GalleryDisplayMode = GalleryDisplayMode.BIG_PREVIEW,
    onDisplayModeChange: (GalleryDisplayMode) -> Unit = {},
    onRequestPermission: () -> Unit,
    onPhotoClick: (Photo) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleFavoriteFilter: () -> Unit,
    onSelectFolder: (String?) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSearchChange: (String) -> Unit,
    onClearFilters: () -> Unit,
    onAddPhotoClick: () -> Unit,
    onToggleTier: ((Boolean) -> Unit)? = null,
    activeActionCardType: ActionCardType = ActionCardType.ALL,
    actionCardPhotoCounts: Map<ActionCardType, Int> = emptyMap(),
    onSelectActionCardType: (ActionCardType) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showSemanticSearchDialog by remember { mutableStateOf(false) }
    val activeFolder = folders.find { it.id == currentFilter.folderId }
    val activeCategory = categories.find { it.id == currentFilter.categoryId }

    var searchTextFieldValue by remember {
        mutableStateOf(TextFieldValue(currentFilter.searchQuery, selection = TextRange(currentFilter.searchQuery.length)))
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
            onSearchChange(newText)
        }
    }

    val headingTitle = when {
        currentFilter.onlyFavorites -> "Ảnh yêu thích."
        activeActionCardType == ActionCardType.ADDRESS -> "Địa chỉ & Địa điểm."
        activeActionCardType == ActionCardType.PAYMENT -> "Chuyển khoản."
        activeActionCardType == ActionCardType.CONTACT -> "Liên hệ."
        activeActionCardType == ActionCardType.EVENT -> "Lịch hẹn & Sự kiện."
        activeFolder != null -> "${activeFolder.name}."
        activeCategory != null -> "${activeCategory.name}."
        else -> "Tất cả ảnh."
    }

    val displayPhotos = photos

    val hasActiveFilter = currentFilter.folderId != null ||
            currentFilter.categoryId != null ||
            currentFilter.onlyFavorites ||
            currentFilter.searchQuery.isNotBlank() ||
            activeActionCardType != ActionCardType.ALL

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.CanvasCream)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.CanvasCream)
                .padding(bottom = 4.dp)
        ) {
            AppScreenHeader(
                icon = Icons.Default.Photo,
                title = headingTitle,
                subtitle = "${displayPhotos.size} ảnh",
                trailingContent = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (currentFilter.onlyFavorites) AppColors.TerracottaLight else AppColors.SurfaceAlabaster)
                                .border(
                                    1.dp,
                                    if (currentFilter.onlyFavorites) AppColors.TerracottaAlert.copy(alpha = 0.4f) else AppColors.BorderSand,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { onToggleFavoriteFilter() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (currentFilter.onlyFavorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Lọc ảnh yêu thích",
                                tint = if (currentFilter.onlyFavorites) AppColors.TerracottaAlert else AppColors.WarmTaupeText,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        if (!hasStoragePermission) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(AppColors.ForestTeal)
                                    .clickable { onAddPhotoClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Thêm ảnh",
                                    tint = AppColors.SurfaceAlabaster,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            )

            // Search Bar with Vietnamese IME (Telex/VNI) support
            OutlinedTextField(
                value = searchTextFieldValue,
                onValueChange = { newValue ->
                    searchTextFieldValue = newValue
                },
                placeholder = {
                    Text(
                        if (accountTier.isPro) "Tìm kiếm theo ý nghĩa (vd: cây cối, xe cộ, thuốc, tivi)..." else "Tìm kiếm ảnh & văn bản OCR (Nâng cấp Pro để bật tìm kiếm ngữ nghĩa)...",
                        fontSize = 13.sp,
                        color = AppColors.WarmMutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = AppColors.WarmMutedText,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchTextFieldValue.text.isNotEmpty()) {
                        IconButton(onClick = {
                            searchTextFieldValue = TextFieldValue("")
                            onSearchChange("")
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Xóa tìm kiếm", tint = AppColors.WarmMutedText)
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
                        onSearchChange(searchTextFieldValue.text)
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
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp)
            )

            // Vector Semantic Search Status & Pro Info Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (accountTier.isPro) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppColors.PlumMutedLight)
                            .border(1.dp, AppColors.PlumMuted.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isVectorIndexing) {
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
                            text = if (isVectorIndexing) "Đang cập nhật chỉ mục ngữ nghĩa Pro..." else "Tìm kiếm ngữ nghĩa đang bật",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.PlumMuted
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppColors.WarmSandSoft)
                            .border(1.dp, AppColors.BorderSand, RoundedCornerShape(10.dp))
                            .clickable { showSemanticSearchDialog = true }
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Khóa",
                            tint = AppColors.WarmTaupeText,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Bật tìm kiếm ngữ nghĩa",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppColors.WarmTaupeText
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(AppColors.OchreGold)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text("PRO", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = AppColors.SurfaceAlabaster)
                        }
                    }
                }

                if (currentFilter.searchQuery.isNotBlank() && accountTier.isPro && semanticMatchScores.isNotEmpty()) {
                    Text(
                        text = "${semanticMatchScores.size} kết quả ngữ nghĩa",
                        fontSize = 11.sp,
                        color = AppColors.PlumMuted,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Smart Action Card Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val allCount = allPhotosCount
                val addrCount = actionCardPhotoCounts[ActionCardType.ADDRESS] ?: 0
                val payCount = actionCardPhotoCounts[ActionCardType.PAYMENT] ?: 0
                val contactCount = actionCardPhotoCounts[ActionCardType.CONTACT] ?: 0

                ActionCardTabPill(
                    label = "Tất cả ảnh",
                    badgeCount = allCount,
                    color = AppColors.ForestTeal,
                    isActive = activeActionCardType == ActionCardType.ALL && currentFilter.categoryId == null,
                    onClick = { onSelectActionCardType(ActionCardType.ALL) }
                )

                ActionCardTabPill(
                    label = "📍 Địa chỉ",
                    badgeCount = addrCount,
                    color = AppColors.TerracottaAlert,
                    isActive = activeActionCardType == ActionCardType.ADDRESS,
                    onClick = {
                        onSelectActionCardType(
                            if (activeActionCardType == ActionCardType.ADDRESS) ActionCardType.ALL else ActionCardType.ADDRESS
                        )
                    }
                )

                ActionCardTabPill(
                    label = "💳 Chuyển khoản",
                    badgeCount = payCount,
                    color = AppColors.OchreGold,
                    isActive = activeActionCardType == ActionCardType.PAYMENT,
                    onClick = {
                        onSelectActionCardType(
                            if (activeActionCardType == ActionCardType.PAYMENT) ActionCardType.ALL else ActionCardType.PAYMENT
                        )
                    }
                )

                ActionCardTabPill(
                    label = "👤 Liên hệ",
                    badgeCount = contactCount,
                    color = AppColors.SageOlive,
                    isActive = activeActionCardType == ActionCardType.CONTACT,
                    onClick = {
                        onSelectActionCardType(
                            if (activeActionCardType == ActionCardType.CONTACT) ActionCardType.ALL else ActionCardType.CONTACT
                        )
                    }
                )
            }
        }

        // Active Filter Banner
        if (hasActiveFilter) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 5.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.ForestTealSoft)
                    .border(1.dp, AppColors.ForestTeal.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Bộ lọc:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.ForestTeal
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppColors.ForestTeal)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = when {
                                    currentFilter.onlyFavorites -> "Yêu thích"
                                    activeActionCardType == ActionCardType.ADDRESS -> "📍 Địa chỉ"
                                    activeActionCardType == ActionCardType.PAYMENT -> "💳 Chuyển khoản"
                                    activeActionCardType == ActionCardType.CONTACT -> "👤 Liên hệ"
                                    activeActionCardType == ActionCardType.EVENT -> "📅 Lịch hẹn"
                                    activeFolder != null -> activeFolder.name
                                    activeCategory != null -> activeCategory.name
                                    currentFilter.searchQuery.isNotBlank() && currentFilter.dateRange != null -> "\"${currentFilter.searchQuery}\" • Thời gian"
                                    currentFilter.searchQuery.isNotBlank() -> "\"${currentFilter.searchQuery}\""
                                    currentFilter.dateRange != null -> "Bộ lọc thời gian"
                                    else -> "Đang lọc"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.SurfaceAlabaster
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(AppColors.ForestTeal.copy(alpha = 0.14f))
                            .clickable { onClearFilters() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Xóa bộ lọc",
                            tint = AppColors.ForestTeal,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Graceful Degradation Banner
        if (!accountTier.isPro && accountTier.aiScansUsedThisMonth >= accountTier.aiScansQuota) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 5.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.OchreGoldLight)
                    .border(1.dp, AppColors.OchreGold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(AppColors.OchreGold),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = AppColors.SurfaceAlabaster,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        val remainingDays = if (accountTier.lastQuotaResetMillis > 0L) {
                            val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000L
                            val elapsed = System.currentTimeMillis() - accountTier.lastQuotaResetMillis
                            val rem = thirtyDaysMs - elapsed
                            if (rem > 0) (rem / (24 * 60 * 60 * 1000L)).toInt().coerceIn(1, 30) else 1
                        } else 30
                        Text(
                            text = "Hạn mức AI tháng này (Tự động đặt lại sau $remainingDays ngày)",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.OchreGoldDark
                        )
                        Text(
                            text = "Bạn đã dùng hết ${accountTier.aiScansQuota} lượt AI thông minh tháng này. Nâng cấp Pro để tiếp tục tự động hóa không giới hạn!",
                            fontSize = 11.5.sp,
                            color = AppColors.OchreGoldDark.copy(alpha = 0.85f),
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // Permission Prompt Banner
        if (!hasStoragePermission) {
            OutlinedCard(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = AppColors.SurfaceAlabaster),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.BorderSand),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 5.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(AppColors.ForestTealSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = AppColors.ForestTeal,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tự động tải ảnh từ thiết bị",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.DeepPineText
                        )
                        Text(
                            text = "Cấp quyền để tự động tải và sắp xếp tất cả ảnh trên thiết bị của bạn mà không cần chọn từng ảnh một.",
                            fontSize = 12.sp,
                            color = AppColors.WarmTaupeText,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onRequestPermission,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.ForestTeal),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Cho phép truy cập", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Scanning indicator
        if (isScanning) {
            val syncText = if (pendingAiCount > 0) {
                "Đang đồng bộ AI... (còn $pendingAiCount ảnh)"
            } else {
                "Đang đồng bộ ảnh..."
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 5.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.SageOliveLight)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = AppColors.ForestTeal)
                Spacer(modifier = Modifier.width(8.dp))
                Text(syncText, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AppColors.ForestTeal)
            }
        }

        // Photos Grid or Empty State
        if (displayPhotos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(AppColors.WarmSand),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Photo,
                            contentDescription = null,
                            tint = AppColors.ForestTeal,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (hasActiveFilter) "Không tìm thấy ảnh nào" else "Chưa có ảnh chụp màn hình nào",
                        fontFamily = FontFamily.Serif,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.ForestTeal
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (hasActiveFilter)
                            "Không có ảnh nào khớp với bộ lọc hoặc từ khóa tìm kiếm của bạn."
                        else if (!hasStoragePermission)
                            "Cho phép cấp quyền ở trên để tự động theo dõi ảnh chụp màn hình, hoặc bấm thêm ảnh tùy chọn."
                        else
                            "Chưa tìm thấy ảnh chụp màn hình trên thiết bị. Bạn có thể chụp màn hình mới hoặc bấm '+' để thêm bất kỳ bức ảnh nào bạn muốn lưu trữ.",
                        fontSize = 13.sp,
                        color = AppColors.WarmTaupeText,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (hasActiveFilter) {
                        Button(
                            onClick = onClearFilters,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.ForestTeal)
                        ) {
                            Text("Xóa bộ lọc")
                        }
                    } else {
                        Button(
                            onClick = onAddPhotoClick,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.ForestTeal)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Thêm ảnh tùy chọn")
                        }
                    }
                }
            }
        } else {
            val spanCount = when (displayMode) {
                GalleryDisplayMode.BIG_PREVIEW -> 2
                GalleryDisplayMode.SMALL_PREVIEW -> 4
                GalleryDisplayMode.LIST -> 1
            }

            val spacing = when (displayMode) {
                GalleryDisplayMode.BIG_PREVIEW -> 10.dp
                GalleryDisplayMode.SMALL_PREVIEW -> 6.dp
                GalleryDisplayMode.LIST -> 10.dp
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(spanCount),
                contentPadding = PaddingValues(
                    start = if (displayMode == GalleryDisplayMode.SMALL_PREVIEW) 12.dp else 18.dp,
                    end = if (displayMode == GalleryDisplayMode.SMALL_PREVIEW) 12.dp else 18.dp,
                    top = 4.dp,
                    bottom = 96.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = { GridItemSpan(spanCount) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Ảnh",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.DeepPineText
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AppColors.WarmSand)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${displayPhotos.size}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppColors.WarmTaupeText
                                )
                            }
                        }

                        DisplayModeSelector(
                            currentMode = displayMode,
                            onModeSelected = onDisplayModeChange
                        )
                    }
                }

                items(displayPhotos, key = { it.id }) { photo ->
                    val semanticScore = semanticMatchScores[photo.id]
                    when (displayMode) {
                        GalleryDisplayMode.BIG_PREVIEW -> {
                            PhotoBigPreviewCard(
                                photo = photo,
                                categories = categories,
                                semanticScore = semanticScore,
                                onClick = { onPhotoClick(photo) },
                                onToggleFavorite = { onToggleFavorite(photo.id) }
                            )
                        }
                        GalleryDisplayMode.SMALL_PREVIEW -> {
                            PhotoSmallPreviewCard(
                                photo = photo,
                                categories = categories,
                                semanticScore = semanticScore,
                                onClick = { onPhotoClick(photo) },
                                onToggleFavorite = { onToggleFavorite(photo.id) }
                            )
                        }
                        GalleryDisplayMode.LIST -> {
                            PhotoListRowCard(
                                photo = photo,
                                categories = categories,
                                semanticScore = semanticScore,
                                showTitle = false,
                                onClick = { onPhotoClick(photo) },
                                onToggleFavorite = { onToggleFavorite(photo.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSemanticSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSemanticSearchDialog = false },
            containerColor = AppColors.SurfaceAlabaster,
            icon = {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = AppColors.PlumMuted,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    "Tìm kiếm ngữ nghĩa nâng cao",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.ForestTeal
                )
            },
            text = {
                Column {
                    Text(
                        text = "Nâng cấp lên gói Pro để tìm kiếm ảnh chụp màn hình theo ý nghĩa, khái niệm và từ đồng nghĩa—không chỉ từ khóa chính xác.",
                        fontSize = 13.5.sp,
                        color = AppColors.DeepPineText,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Ví dụ:\n• Tìm \"chuyến bay\" hoặc \"flight\" để tìm vé máy bay, thẻ lên tàu bay.\n• Tìm \"ăn tối\" hoặc \"hóa đơn\" để tìm bill quán ăn, biên lai.\n• Tìm \"việc cần làm\" để tìm danh sách checklist công việc.",
                        fontSize = 12.sp,
                        color = AppColors.WarmTaupeText,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (com.example.test2.BuildConfig.DEBUG) {
                            onToggleTier?.invoke(true)
                        }
                        showSemanticSearchDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.ForestTeal)
                ) {
                    Text(if (com.example.test2.BuildConfig.DEBUG) "Thử chế độ Pro (Debug)" else "Nâng cấp Pro")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSemanticSearchDialog = false }) {
                    Text("Đóng", color = AppColors.WarmTaupeText)
                }
            }
        )
    }
}

@Composable
fun DisplayModeSelector(
    currentMode: GalleryDisplayMode,
    onModeSelected: (GalleryDisplayMode) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.WarmSandSoft)
            .border(1.dp, AppColors.BorderSand, RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DisplayModeButton(
            icon = Icons.Default.GridView,
            title = "Xem lớn (2 ảnh/hàng)",
            isSelected = currentMode == GalleryDisplayMode.BIG_PREVIEW,
            onClick = { onModeSelected(GalleryDisplayMode.BIG_PREVIEW) }
        )
        DisplayModeButton(
            icon = Icons.Default.Apps,
            title = "Xem nhỏ (4 ảnh/hàng)",
            isSelected = currentMode == GalleryDisplayMode.SMALL_PREVIEW,
            onClick = { onModeSelected(GalleryDisplayMode.SMALL_PREVIEW) }
        )
        DisplayModeButton(
            icon = Icons.AutoMirrored.Filled.ViewList,
            title = "Dạng danh sách",
            isSelected = currentMode == GalleryDisplayMode.LIST,
            onClick = { onModeSelected(GalleryDisplayMode.LIST) }
        )
    }
}

@Composable
private fun DisplayModeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (isSelected) AppColors.SurfaceAlabaster else Color.Transparent)
            .then(
                if (isSelected) Modifier.border(1.dp, AppColors.BorderSand, RoundedCornerShape(9.dp))
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isSelected) AppColors.ForestTeal else AppColors.WarmTaupeText,
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
fun ActionCardTabPill(
    label: String,
    badgeCount: Int,
    color: Color,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (isActive) color else AppColors.SurfaceAlabaster)
            .border(
                width = 1.dp,
                color = if (isActive) color else AppColors.BorderSand,
                shape = RoundedCornerShape(99.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.5.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) AppColors.SurfaceAlabaster else AppColors.DeepPineText
            )
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isActive) AppColors.SurfaceAlabaster.copy(alpha = 0.25f) else AppColors.WarmSand)
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "$badgeCount",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) AppColors.SurfaceAlabaster else AppColors.WarmTaupeText
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryPill(
    name: String,
    colorHex: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val pillColor = parseHexColor(colorHex)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (isActive) AppColors.ForestTeal else AppColors.SurfaceAlabaster)
            .border(
                1.dp,
                if (isActive) AppColors.ForestTeal else AppColors.BorderSand,
                RoundedCornerShape(99.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isActive) AppColors.SurfaceAlabaster else pillColor)
            )
            Text(
                text = name,
                fontSize = 12.5.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) AppColors.SurfaceAlabaster else AppColors.DeepPineText
            )
        }
    }
}

@Composable
fun PhotoBigPreviewCard(
    photo: Photo,
    categories: List<Category>,
    semanticScore: Float? = null,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val photoCategories = categories.filter { it.id in photo.categoryIds }
    val imageSource = remember(photo.filePath) {
        if (photo.filePath.startsWith("content://")) {
            android.net.Uri.parse(photo.filePath)
        } else {
            File(photo.filePath)
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceAlabaster),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppColors.BorderSand, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(AppColors.WarmSand)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageSource)
                        .crossfade(true)
                        .build(),
                    contentDescription = photo.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (semanticScore != null && semanticScore >= 0.28f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppColors.PlumMuted.copy(alpha = 0.92f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "✨ Khớp ${(semanticScore * 100).toInt()}%",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.SurfaceAlabaster
                        )
                    }
                }

                if (photo.filePath.endsWith(".enc")) {
                    Box(
                        modifier = Modifier
                            .align(if (semanticScore != null && semanticScore >= 0.28f) Alignment.BottomStart else Alignment.TopStart)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(AppColors.ForestTealDark.copy(alpha = 0.88f))
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Đã mã hóa trong Két",
                            tint = AppColors.SageOliveLight,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (photo.isFavorite) AppColors.SurfaceAlabaster else AppColors.DeepPineText.copy(alpha = 0.45f))
                        .clickable { onToggleFavorite() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (photo.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Yêu thích",
                        tint = if (photo.isFavorite) AppColors.TerracottaAlert else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (photoCategories.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        photoCategories.take(2).forEach { cat ->
                            val catColor = parseHexColor(cat.colorHex)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(AppColors.DeepPineText.copy(alpha = 0.72f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(catColor)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = cat.name,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = AppColors.SurfaceAlabaster,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Minimal metadata below image (date & schedule indicator only — no title/OCR clutter)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDate(photo.dateAdded),
                    fontSize = 11.sp,
                    color = AppColors.WarmTaupeText
                )
                val isShopping = photo.detectedIntent == "SHOPPING" || photo.categoryIds.contains(DefaultCategories.TO_BUY)
                val isEvent = photo.detectedIntent == "EVENT" || photo.categoryIds.contains(DefaultCategories.EVENTS) || photo.suggestedEventDateMillis != null
                val hasSchedule = isShopping || isEvent
                if (hasSchedule) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = if (isShopping) "Lên lịch mua" else "Lên lịch hẹn",
                        tint = if (isShopping) AppColors.OchreGold else AppColors.ForestTeal,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PhotoSmallPreviewCard(
    photo: Photo,
    categories: List<Category>,
    semanticScore: Float? = null,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val photoCategories = categories.filter { it.id in photo.categoryIds }
    val imageSource = remember(photo.filePath) {
        if (photo.filePath.startsWith("content://")) {
            android.net.Uri.parse(photo.filePath)
        } else {
            File(photo.filePath)
        }
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceAlabaster),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppColors.BorderSand, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(AppColors.WarmSand)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageSource)
                        .crossfade(true)
                        .build(),
                    contentDescription = photo.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (semanticScore != null && semanticScore >= 0.28f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(AppColors.PlumMuted.copy(alpha = 0.92f))
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "${(semanticScore * 100).toInt()}%",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.SurfaceAlabaster
                        )
                    }
                }

                if (photo.filePath.endsWith(".enc")) {
                    Box(
                        modifier = Modifier
                            .align(if (semanticScore != null && semanticScore >= 0.28f) Alignment.BottomStart else Alignment.TopStart)
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(AppColors.ForestTealDark.copy(alpha = 0.88f))
                            .padding(2.5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Đã mã hóa trong Két",
                            tint = AppColors.SageOliveLight,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }

                if (photo.isFavorite) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(AppColors.SurfaceAlabaster)
                            .clickable { onToggleFavorite() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Yêu thích",
                            tint = AppColors.TerracottaAlert,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }

                if (photoCategories.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(AppColors.DeepPineText.copy(alpha = 0.55f))
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        photoCategories.take(2).forEach { cat ->
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(cat.colorHex))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PhotoListRowCard(
    photo: Photo,
    categories: List<Category>,
    semanticScore: Float? = null,
    showTitle: Boolean = true,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val photoCategories = categories.filter { it.id in photo.categoryIds }
    val imageSource = remember(photo.filePath) {
        if (photo.filePath.startsWith("content://")) {
            android.net.Uri.parse(photo.filePath)
        } else {
            File(photo.filePath)
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceAlabaster),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppColors.BorderSand, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.WarmSand)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageSource)
                        .crossfade(true)
                        .build(),
                    contentDescription = photo.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (semanticScore != null && semanticScore >= 0.28f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(AppColors.PlumMuted.copy(alpha = 0.92f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "${(semanticScore * 100).toInt()}%",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.SurfaceAlabaster
                        )
                    }
                }

                if (photo.filePath.endsWith(".enc")) {
                    Box(
                        modifier = Modifier
                            .align(if (semanticScore != null && semanticScore >= 0.28f) Alignment.BottomStart else Alignment.TopStart)
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(AppColors.ForestTealDark.copy(alpha = 0.88f))
                            .padding(2.5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Đã mã hóa trong Két",
                            tint = AppColors.SageOliveLight,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (showTitle) {
                    Text(
                        text = photo.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.DeepPineText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                val sizeStr = formatFileSize(photo.fileSizeBytes)
                val dateStr = formatDate(photo.dateAdded)
                Text(
                    text = if (sizeStr.isNotBlank()) "$dateStr • $sizeStr" else dateStr,
                    fontSize = if (showTitle) 11.5.sp else 13.sp,
                    fontWeight = if (showTitle) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (showTitle) AppColors.WarmTaupeText else AppColors.DeepPineText
                )

                if (photoCategories.isNotEmpty() || photo.suggestedEventDateMillis != null || (semanticScore != null && semanticScore >= 0.28f)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (semanticScore != null && semanticScore >= 0.28f) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AppColors.PlumMutedLight)
                                    .border(1.dp, AppColors.PlumMuted.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "✨ Khớp ${(semanticScore * 100).toInt()}%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.PlumMuted
                                )
                            }
                        }

                        photoCategories.take(2).forEach { cat ->
                            val catColor = parseHexColor(cat.colorHex)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(catColor.copy(alpha = 0.14f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = cat.name,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = catColor
                                )
                            }
                        }

                        val isShopping = photo.detectedIntent == "SHOPPING" || photo.categoryIds.contains(DefaultCategories.TO_BUY)
                        val isEvent = photo.detectedIntent == "EVENT" || photo.categoryIds.contains(DefaultCategories.EVENTS) || photo.suggestedEventDateMillis != null
                        val hasSchedule = isShopping || isEvent
                        if (hasSchedule) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isShopping) AppColors.OchreGoldLight else AppColors.ForestTealSoft)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isShopping) "Lên lịch mua" else "Sự kiện",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isShopping) AppColors.OchreGoldDark else AppColors.ForestTeal
                                )
                            }
                        }
                    }
                }
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = if (photo.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Yêu thích",
                    tint = if (photo.isFavorite) AppColors.TerracottaAlert else AppColors.WarmMutedText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
