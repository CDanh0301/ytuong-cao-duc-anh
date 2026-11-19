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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.example.test2.database.DefaultCategories
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.test2.shared.models.ActionCardType
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Folder
import com.example.test2.shared.models.Photo
import com.example.test2.shared.models.PhotoFilter
import com.example.test2.shared.models.UserAccountTier
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ViewAgenda
import com.example.test2.ui.GalleryDisplayMode
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

    // Preserve local composition state for Vietnamese IMEs (Telex/VNI)
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
        currentFilter.onlyFavorites -> "Ảnh yêu thích"
        activeActionCardType == ActionCardType.ADDRESS -> "Địa chỉ & Địa điểm"
        activeActionCardType == ActionCardType.PAYMENT -> "Thông tin chuyển khoản"
        activeActionCardType == ActionCardType.CONTACT -> "Liên hệ & Danh bạ"
        activeActionCardType == ActionCardType.EVENT -> "Lịch hẹn & Sự kiện"
        activeFolder != null -> activeFolder.name
        activeCategory != null -> activeCategory.name
        else -> "Tất cả ảnh"
    }

    val hasActiveFilter = currentFilter.folderId != null ||
            currentFilter.categoryId != null ||
            currentFilter.onlyFavorites ||
            currentFilter.searchQuery.isNotBlank() ||
            activeActionCardType != ActionCardType.ALL

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // App Header Bar matching index.html
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(top = 8.dp, bottom = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = headingTitle,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "${photos.size} ảnh",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Favorite filter toggle button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (currentFilter.onlyFavorites) Color(0xFFFEE2E2) else Color(0xFFF1F5F9))
                            .border(
                                1.dp,
                                if (currentFilter.onlyFavorites) Color(0xFFFCA5A5) else Color(0xFFE2E8F0),
                                CircleShape
                            )
                            .clickable { onToggleFavoriteFilter() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (currentFilter.onlyFavorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Lọc ảnh yêu thích",
                            tint = if (currentFilter.onlyFavorites) Color(0xFFEF4444) else Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Add Photo button (only kept on header when user hasn't granted access to all images)
                    if (!hasStoragePermission) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2563EB))
                                .clickable { onAddPhotoClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Thêm ảnh",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

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
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchTextFieldValue.text.isNotEmpty()) {
                        IconButton(onClick = {
                            searchTextFieldValue = TextFieldValue("")
                            onSearchChange("")
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Xóa tìm kiếm", tint = Color(0xFF94A3B8))
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
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF8FAFC),
                    unfocusedContainerColor = Color(0xFFF8FAFC),
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color(0xFFE2E8F0)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Vector Semantic Search Status & Pro Info Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
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
                        if (isVectorIndexing) {
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
                            text = if (isVectorIndexing) "Đang cập nhật chỉ mục ngữ nghĩa Pro..." else "Tìm kiếm ngữ nghĩa Vector đang bật",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF6B21A8)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFF1F5F9))
                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(6.dp))
                            .clickable { showSemanticSearchDialog = true }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Khóa",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Bật tìm kiếm ngữ nghĩa Vector",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF8B5CF6))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("PRO", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                if (currentFilter.searchQuery.isNotBlank() && accountTier.isPro && semanticMatchScores.isNotEmpty()) {
                    Text(
                        text = "${semanticMatchScores.size} kết quả ngữ nghĩa",
                        fontSize = 11.sp,
                        color = Color(0xFF7E22CE),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Smart Action Card Tabs (Replaces Category Carousel to avoid clutter)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val allCount = allPhotosCount
                val addrCount = actionCardPhotoCounts[ActionCardType.ADDRESS] ?: 0
                val payCount = actionCardPhotoCounts[ActionCardType.PAYMENT] ?: 0
                val contactCount = actionCardPhotoCounts[ActionCardType.CONTACT] ?: 0

                // 1. Tất cả ảnh Tab
                ActionCardTabPill(
                    label = "Tất cả ảnh",
                    badgeCount = allCount,
                    color = Color(0xFF2563EB),
                    isActive = activeActionCardType == ActionCardType.ALL && currentFilter.categoryId == null,
                    onClick = { onSelectActionCardType(ActionCardType.ALL) }
                )

                // 2. 📍 Địa chỉ Tab
                ActionCardTabPill(
                    label = "📍 Địa chỉ",
                    badgeCount = addrCount,
                    color = Color(0xFFEF4444),
                    isActive = activeActionCardType == ActionCardType.ADDRESS,
                    onClick = {
                        if (activeActionCardType == ActionCardType.ADDRESS) {
                            onSelectActionCardType(ActionCardType.ALL)
                        } else {
                            onSelectActionCardType(ActionCardType.ADDRESS)
                        }
                    }
                )

                // 3. 💳 Chuyển khoản Tab
                ActionCardTabPill(
                    label = "💳 Chuyển khoản",
                    badgeCount = payCount,
                    color = Color(0xFF0EA5E9),
                    isActive = activeActionCardType == ActionCardType.PAYMENT,
                    onClick = {
                        if (activeActionCardType == ActionCardType.PAYMENT) {
                            onSelectActionCardType(ActionCardType.ALL)
                        } else {
                            onSelectActionCardType(ActionCardType.PAYMENT)
                        }
                    }
                )

                // 4. 👤 Liên hệ Tab
                ActionCardTabPill(
                    label = "👤 Liên hệ",
                    badgeCount = contactCount,
                    color = Color(0xFF8B5CF6),
                    isActive = activeActionCardType == ActionCardType.CONTACT,
                    onClick = {
                        if (activeActionCardType == ActionCardType.CONTACT) {
                            onSelectActionCardType(ActionCardType.ALL)
                        } else {
                            onSelectActionCardType(ActionCardType.CONTACT)
                        }
                    }
                )
            }
        }

        // Active Filter Banner matching index.html
        if (hasActiveFilter) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFEFF6FF))
                    .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(10.dp))
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
                            color = Color(0xFF1E40AF)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2563EB))
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
                                color = Color.White
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFDBEAFE))
                            .clickable { onClearFilters() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Xóa bộ lọc",
                            tint = Color(0xFF1E40AF),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Graceful Degradation Banner (IDEA.md Step 4)
        if (!accountTier.isPro && accountTier.aiScansUsedThisMonth >= accountTier.aiScansQuota) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFEF3C7))
                    .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF59E0B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
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
                            color = Color(0xFF92400E)
                        )
                        Text(
                            text = "Bạn đã dùng hết ${accountTier.aiScansQuota} lượt AI thông minh tháng này. Nâng cấp Pro để tiếp tục tự động hóa không giới hạn!",
                            fontSize = 11.5.sp,
                            color = Color(0xFFB45309),
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // Permission Prompt Banner (if not granted)
        if (!hasStoragePermission) {
            OutlinedCard(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEFF6FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tự động tải ảnh từ thiết bị",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Cấp quyền để tự động tải và sắp xếp tất cả ảnh trên thiết bị của bạn mà không cần chọn từng ảnh một.",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onRequestPermission,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
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
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF2563EB))
                Spacer(modifier = Modifier.width(8.dp))
                Text(syncText, fontSize = 12.sp, color = Color(0xFF64748B))
            }
        }

        // Photos Grid or Empty State
        if (photos.isEmpty()) {
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
                            .clip(CircleShape)
                            .background(Color(0xFFEFF6FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Photo,
                            contentDescription = null,
                            tint = Color(0xFF3B82F6),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (hasActiveFilter) "Không tìm thấy ảnh nào" else "Chưa có ảnh chụp màn hình nào",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (hasActiveFilter)
                            "Không có ảnh nào khớp với bộ lọc hoặc từ khóa tìm kiếm của bạn."
                        else if (!hasStoragePermission)
                            "Cho phép cấp quyền ở trên để tự động theo dõi ảnh chụp màn hình, hoặc bấm thêm ảnh tùy chọn."
                        else
                            "Chưa tìm thấy ảnh chụp màn hình trên thiết bị. Bạn có thể chụp màn hình mới hoặc bấm '+' để thêm bất kỳ bức ảnh nào bạn muốn lưu trữ.",
                        fontSize = 13.5.sp,
                        color = Color(0xFF64748B),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (hasActiveFilter) {
                        Button(
                            onClick = onClearFilters,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("Xóa bộ lọc")
                        }
                    } else {
                        Button(
                            onClick = onAddPhotoClick,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
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
                GalleryDisplayMode.SMALL_PREVIEW -> 5.dp
                GalleryDisplayMode.LIST -> 10.dp
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(spanCount),
                contentPadding = PaddingValues(if (displayMode == GalleryDisplayMode.SMALL_PREVIEW) 8.dp else 12.dp),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
                modifier = Modifier.fillMaxSize()
            ) {
                // Section Header with Photos Count & Display Mode Switcher
                item(span = { GridItemSpan(spanCount) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Photo,
                                contentDescription = null,
                                tint = Color(0xFF334155),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Ảnh",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFE2E8F0))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${photos.size}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF475569)
                                )
                            }
                        }

                        // Display Mode Switcher (Big Preview, Small Preview, List)
                        DisplayModeSelector(
                            currentMode = displayMode,
                            onModeSelected = onDisplayModeChange
                        )
                    }
                }

                items(photos, key = { it.id }) { photo ->
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
            icon = {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF7E22CE),
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("Tìm kiếm ngữ nghĩa Vector nâng cao", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "Nâng cấp lên gói Pro để tìm kiếm ảnh chụp màn hình theo ý nghĩa, khái niệm và từ đồng nghĩa—không chỉ từ khóa chính xác.",
                        fontSize = 13.5.sp,
                        color = Color(0xFF334155),
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Ví dụ:\n• Tìm \"chuyến bay\" hoặc \"flight\" để tìm vé máy bay, thẻ lên tàu bay.\n• Tìm \"ăn tối\" hoặc \"hóa đơn\" để tìm bill quán ăn, biên lai.\n• Tìm \"việc cần làm\" để tìm danh sách checklist công việc.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7E22CE))
                ) {
                    Text(if (com.example.test2.BuildConfig.DEBUG) "Thử chế độ Pro (Debug)" else "Nâng cấp Pro")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSemanticSearchDialog = false }) {
                    Text("Đóng")
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
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF1F5F9))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
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
            .clip(RoundedCornerShape(7.dp))
            .background(if (isSelected) Color.White else Color.Transparent)
            .then(
                if (isSelected) Modifier.border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(7.dp))
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isSelected) Color(0xFF2563EB) else Color(0xFF64748B),
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
            .clip(RoundedCornerShape(20.dp))
            .background(if (isActive) color else color.copy(alpha = 0.10f))
            .border(
                width = 1.dp,
                color = if (isActive) color else color.copy(alpha = 0.25f),
                shape = RoundedCornerShape(20.dp)
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
                color = if (isActive) Color.White else Color(0xFF1E293B)
            )
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isActive) Color.White.copy(alpha = 0.25f) else color.copy(alpha = 0.20f))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "$badgeCount",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) Color.White else color
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
            .clip(RoundedCornerShape(20.dp))
            .background(if (isActive) Color(0xFF2563EB) else Color.White)
            .border(
                1.dp,
                if (isActive) Color(0xFF2563EB) else Color(0xFFE2E8F0),
                RoundedCornerShape(20.dp)
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
                    .background(if (isActive) Color.White else pillColor)
            )
            Text(
                text = name,
                fontSize = 12.5.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) Color.White else Color(0xFF334155)
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color(0xFFE2E8F0))
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

                // Semantic Match Badge in Top-Left (Pro Vector Search)
                if (semanticScore != null && semanticScore >= 0.28f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF7E22CE).copy(alpha = 0.9f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "✨ Khớp ${(semanticScore * 100).toInt()}%",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Favorite Heart Overlay in Top-Right
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (photo.isFavorite) Color.White else Color.Black.copy(alpha = 0.45f))
                        .clickable { onToggleFavorite() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (photo.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Yêu thích",
                        tint = if (photo.isFavorite) Color(0xFFEF4444) else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Category Badges Overlay in Bottom-Left
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
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.65f))
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
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Info below image (2 columns)
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text(
                    text = photo.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDate(photo.dateAdded),
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                    val isShopping = photo.detectedIntent == "SHOPPING" || photo.categoryIds.contains(DefaultCategories.TO_BUY)
                    val isEvent = photo.detectedIntent == "EVENT" || photo.categoryIds.contains(DefaultCategories.EVENTS) || photo.suggestedEventDateMillis != null
                    val hasSchedule = isShopping || isEvent
                    if (hasSchedule) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = if (isShopping) "Lên lịch mua" else "Lên lịch hẹn",
                            tint = if (isShopping) Color(0xFFF59E0B) else Color(0xFF2563EB),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // OCR Preview if extracted
                val ocrSnippet = photo.piiScrubbedText
                if (!ocrSnippet.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = ocrSnippet.take(45),
                        fontSize = 10.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color(0xFFE2E8F0))
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

                // Semantic Match Indicator in Top-Left (Pro Vector Search)
                if (semanticScore != null && semanticScore >= 0.28f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF7E22CE).copy(alpha = 0.9f))
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "${(semanticScore * 100).toInt()}%",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Favorite Heart Overlay in Top-Right
                if (photo.isFavorite) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { onToggleFavorite() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Yêu thích",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }

                // Category dots in Bottom-Left
                if (photoCategories.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
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

            // Minimal title below
            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)) {
                Text(
                    text = photo.title,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun PhotoListRowCard(
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail on left
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFE2E8F0))
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
                            .background(Color(0xFF7E22CE).copy(alpha = 0.9f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "${(semanticScore * 100).toInt()}%",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Center Column with Title, Date, Size, Category badges
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = photo.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val sizeStr = formatFileSize(photo.fileSizeBytes)
                val dateStr = formatDate(photo.dateAdded)
                Text(
                    text = if (sizeStr.isNotBlank()) "$dateStr • $sizeStr" else dateStr,
                    fontSize = 11.5.sp,
                    color = Color(0xFF64748B)
                )

                // Category badges & Semantic Match pill
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
                                    .background(Color(0xFFF3E8FF))
                                    .border(1.dp, Color(0xFFD8B4FE), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "✨ Khớp ${(semanticScore * 100).toInt()}%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF7E22CE)
                                )
                            }
                        }

                        photoCategories.take(2).forEach { cat ->
                            val catColor = parseHexColor(cat.colorHex)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(catColor.copy(alpha = 0.12f))
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
                                    .background(if (isShopping) Color(0xFFFEF3C7) else Color(0xFFEFF6FF))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isShopping) "Lên lịch mua" else "Sự kiện",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isShopping) Color(0xFFD97706) else Color(0xFF2563EB)
                                )
                            }
                        }
                    }
                }
            }

            // Favorite Button on Right
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = if (photo.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Yêu thích",
                    tint = if (photo.isFavorite) Color(0xFFEF4444) else Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

