package com.example.test2.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.test2.shared.models.AgingSettings
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.DefaultCategories
import com.example.test2.shared.models.Photo
import com.example.test2.shared.models.UserAccountTier
import com.example.test2.ui.components.AppScreenHeader
import com.example.test2.ui.theme.AppColors
import com.example.test2.ui.theme.IconsHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PREVIEW_ITEM_LIMIT = 2

@Composable
fun HousekeepingScreen(
    photos: List<Photo>,
    accountTier: UserAccountTier,
    agingSettings: AgingSettings,
    agingPhotos: List<Photo>,
    categories: List<Category> = emptyList(),
    isEventsExpanded: Boolean = false,
    onToggleEventsExpanded: () -> Unit = {},
    isClutterExpanded: Boolean = false,
    onToggleClutterExpanded: () -> Unit = {},
    onCleanClutter: (List<Photo>) -> Unit,
    onDeleteSinglePhoto: (Photo) -> Unit,
    onAddToCalendar: (Photo) -> Unit,
    onPhotoClick: (Photo) -> Unit,
    onSetCategoryRetentionRule: (categoryId: String, retentionDays: Int) -> Unit = { _, _ -> },
    onSetDefaultRetentionDays: (retentionDays: Int) -> Unit = {},
    agingMonitorInfo: Triple<Long, Int, Long> = Triple(0L, 0, 0L),
    onRunAgingScanNow: () -> Unit = {}
) {
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showAgingSettingsDialog by remember { mutableStateOf(false) }

    val pendingEventPhotos = remember(photos) {
        photos.filter { photo ->
            photo.isScreenshot && (
                photo.suggestedEventDateMillis != null ||
                    photo.detectedIntent == "EVENT" ||
                    photo.detectedIntent == "SHOPPING" ||
                    photo.categoryIds.contains(DefaultCategories.TO_BUY) ||
                    photo.categoryIds.contains(DefaultCategories.EVENTS)
                )
        }
    }

    val totalClutterBytes = remember(agingPhotos) {
        agingPhotos.sumOf { it.fileSizeBytes }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.CanvasCream),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            AppScreenHeader(
                icon = Icons.Default.AutoAwesome,
                title = "Sắp xếp.",
                subtitle = "Sự kiện lịch phát hiện được & Dọn ảnh chụp màn hình cũ",
                kicker = "Trợ lý dọn dẹp"
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        val hasMoreEvents = pendingEventPhotos.size > PREVIEW_ITEM_LIMIT
        val displayedEventPhotos = if (isEventsExpanded) {
            pendingEventPhotos
        } else {
            pendingEventPhotos.take(PREVIEW_ITEM_LIMIT)
        }

        item {
            CollapsibleSectionHeaderCard(
                icon = Icons.Default.CalendarMonth,
                iconTint = AppColors.PlumMuted,
                iconBgColor = AppColors.PlumMutedLight,
                title = "Sự kiện lịch phát hiện được",
                subtitle = "Sự kiện được trích xuất thông minh từ ảnh chụp màn hình",
                countBadgeText = "${pendingEventPhotos.size} sự kiện",
                expanded = isEventsExpanded,
                showExpandToggle = hasMoreEvents,
                onToggle = onToggleEventsExpanded
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (pendingEventPhotos.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceAlabaster),
                    border = BorderStroke(1.dp, AppColors.BorderSand)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = AppColors.WarmMutedText,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Không có sự kiện lịch nào đang chờ",
                                fontSize = 13.sp,
                                color = AppColors.WarmTaupeText,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        } else {
            items(displayedEventPhotos, key = { "event_${it.id}" }) { photo ->
                EventCard(
                    photo = photo,
                    onAddToCalendar = { onAddToCalendar(photo) },
                    onPhotoClick = { onPhotoClick(photo) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            if (hasMoreEvents) {
                item {
                    ExpandableListToggleButton(
                        totalCount = pendingEventPhotos.size,
                        previewLimit = PREVIEW_ITEM_LIMIT,
                        expanded = isEventsExpanded,
                        itemLabel = "sự kiện",
                        onToggle = onToggleEventsExpanded
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }
            } else {
                item { Spacer(modifier = Modifier.height(6.dp)) }
            }
        }

        val hasMoreClutter = agingPhotos.size > PREVIEW_ITEM_LIMIT
        val displayedAgingPhotos = if (isClutterExpanded) {
            agingPhotos
        } else {
            agingPhotos.take(PREVIEW_ITEM_LIMIT)
        }

        item {
            val clutterSubtitle = if (agingPhotos.isNotEmpty()) {
                "Cũ hơn ${agingSettings.retentionDays} ngày · ~${formatFileSize(totalClutterBytes)} có thể giải phóng"
            } else {
                "Ảnh chụp màn hình cũ hơn ${agingSettings.retentionDays} ngày"
            }
            CollapsibleSectionHeaderCard(
                icon = Icons.Default.CleaningServices,
                iconTint = AppColors.OchreGoldDark,
                iconBgColor = AppColors.OchreGoldLight,
                title = "Ảnh chụp màn hình cũ & Rác bộ nhớ",
                subtitle = clutterSubtitle,
                countBadgeText = "${agingPhotos.size} ảnh",
                expanded = isClutterExpanded,
                showExpandToggle = hasMoreClutter,
                onToggle = onToggleClutterExpanded
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.SageOliveLight),
                border = BorderStroke(1.dp, AppColors.SageOlive.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(AppColors.SurfaceAlabaster),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CleaningServices,
                                contentDescription = null,
                                tint = AppColors.ForestTeal,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Tự động theo dõi rác nền",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.ForestTeal
                            )
                            val lastChecked = agingMonitorInfo.first
                            val checkText = if (lastChecked > 0L) {
                                val timeStr = SimpleDateFormat("HH:mm, dd/MM", Locale.getDefault()).format(Date(lastChecked))
                                "Lần kiểm tra nền gần nhất: $timeStr"
                            } else {
                                "Định kỳ kiểm tra dọn rác nền"
                            }
                            Text(
                                text = checkText,
                                fontSize = 10.5.sp,
                                color = AppColors.WarmTaupeText
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRunAgingScanNow,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp),
                            border = BorderStroke(1.dp, AppColors.SageOlive.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = AppColors.SurfaceAlabaster,
                                contentColor = AppColors.ForestTeal
                            )
                        ) {
                            Text("Quét ngay", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AppColors.SurfaceAlabaster)
                                .border(1.dp, AppColors.SageOlive.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .clickable { showAgingSettingsDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Cài đặt dọn dẹp",
                                tint = AppColors.ForestTeal,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        if (agingPhotos.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = { showBatchDeleteDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.TerracottaAlert),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Dọn tất cả (${agingPhotos.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (agingPhotos.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceAlabaster),
                    border = BorderStroke(1.dp, AppColors.BorderSand)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AppColors.SageOlive,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Thư viện ảnh của bạn thật gọn gàng!",
                                fontFamily = FontFamily.Serif,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.ForestTeal
                            )
                            Text(
                                text = "Không có ảnh chụp màn hình nào quá ${agingSettings.retentionDays} ngày cần dọn dẹp.",
                                fontSize = 12.sp,
                                color = AppColors.WarmTaupeText,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                }
            }
        } else {
            items(displayedAgingPhotos, key = { "clutter_${it.id}" }) { photo ->
                val customRuleDays = if (accountTier.isPro && agingSettings.categoryRetentionDays.isNotEmpty()) {
                    photo.categoryIds.firstNotNullOfOrNull { agingSettings.categoryRetentionDays[it] }
                } else {
                    null
                }
                ClutterItemCard(
                    photo = photo,
                    customRuleDays = customRuleDays,
                    onDelete = { onDeleteSinglePhoto(photo) },
                    onPhotoClick = { onPhotoClick(photo) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (hasMoreClutter) {
                item {
                    ExpandableListToggleButton(
                        totalCount = agingPhotos.size,
                        previewLimit = PREVIEW_ITEM_LIMIT,
                        expanded = isClutterExpanded,
                        itemLabel = "ảnh",
                        onToggle = onToggleClutterExpanded
                    )
                }
            }
        }
    }

    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            containerColor = AppColors.SurfaceAlabaster,
            title = {
                Text(
                    "Dọn dẹp ${agingPhotos.size} ảnh chụp màn hình cũ?",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.DeepPineText
                )
            },
            text = {
                Text(
                    "Thao tác này sẽ xóa ${agingPhotos.size} ảnh chụp màn hình cũ hơn ${agingSettings.retentionDays} ngày, giải phóng khoảng ${formatFileSize(totalClutterBytes)} dung lượng bộ nhớ.",
                    color = AppColors.WarmTaupeText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBatchDeleteDialog = false
                        onCleanClutter(agingPhotos)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.TerracottaAlert)
                ) {
                    Text("Xóa tất cả")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showBatchDeleteDialog = false }) {
                    Text("Hủy", color = AppColors.WarmTaupeText)
                }
            }
        )
    }

    if (showAgingSettingsDialog) {
        Dialog(
            onDismissRequest = { showAgingSettingsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceAlabaster),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AppColors.ForestTealSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = AppColors.ForestTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Thời gian lưu trữ & Dọn dẹp",
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 18.sp,
                                    color = AppColors.ForestTeal
                                )
                                Text(
                                    text = "Quy tắc dọn dẹp",
                                    fontSize = 12.sp,
                                    color = AppColors.WarmTaupeText
                                )
                            }
                        }

                        IconButton(
                            onClick = { showAgingSettingsDialog = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Đóng", tint = AppColors.WarmTaupeText, modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Quy tắc lưu trữ theo danh mục",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = AppColors.DeepPineText
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (accountTier.isPro) AppColors.ForestTealSoft else AppColors.WarmSand
                        ) {
                            Text(
                                text = if (accountTier.isPro) "PRO ĐANG BẬT" else "TÍNH NĂNG PRO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (accountTier.isPro) AppColors.ForestTeal else AppColors.WarmTaupeText,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (accountTier.isPro) {
                            "Đặt thời gian lưu trữ riêng cho từng danh mục (ví dụ: tự động dọn hóa đơn sau 7 ngày, lưu bài đọc 90 ngày)."
                        } else {
                            "Gói Pro cho phép tùy chỉnh thời gian lưu trữ và dọn dẹp riêng biệt theo từng danh mục."
                        },
                        fontSize = 12.sp,
                        color = AppColors.WarmTaupeText,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (accountTier.isPro) {
                        DefaultRetentionRow(
                            currentDays = agingSettings.retentionDays,
                            onSelectDays = onSetDefaultRetentionDays
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    if (accountTier.isPro && categories.isNotEmpty()) {
                        categories.forEach { category ->
                            val currentDays = agingSettings.categoryRetentionDays[category.id]
                            CategoryAgingRow(
                                category = category,
                                currentDays = currentDays,
                                defaultDays = agingSettings.retentionDays,
                                onSelectDays = { days ->
                                    onSetCategoryRetentionRule(category.id, days)
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    } else if (!accountTier.isPro) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = AppColors.CanvasCream,
                            border = BorderStroke(1.dp, AppColors.BorderSand),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = AppColors.WarmMutedText,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Thời gian mặc định chung: ${agingSettings.retentionDays} ngày",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppColors.DeepPineText
                                    )
                                    Text(
                                        text = "Tất cả ảnh chụp màn hình tồn tại trên máy quá ${agingSettings.retentionDays} ngày sẽ được đưa vào danh sách dọn dẹp.",
                                        fontSize = 11.5.sp,
                                        color = AppColors.WarmTaupeText
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { showAgingSettingsDialog = false },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.ForestTeal),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Xong", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun CollapsibleSectionHeaderCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBgColor: Color,
    title: String,
    subtitle: String,
    countBadgeText: String,
    expanded: Boolean,
    showExpandToggle: Boolean = true,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .then(if (showExpandToggle) Modifier.clickable { onToggle() } else Modifier),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceAlabaster),
        border = BorderStroke(1.dp, AppColors.BorderSand),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.DeepPineText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.5.sp,
                        color = AppColors.WarmTaupeText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AppColors.WarmSand
                ) {
                    Text(
                        text = countBadgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.DeepPineText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                if (showExpandToggle) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Thu gọn" else "Mở rộng",
                        tint = AppColors.WarmTaupeText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ExpandableListToggleButton(
    totalCount: Int,
    previewLimit: Int,
    expanded: Boolean,
    itemLabel: String,
    onToggle: () -> Unit
) {
    val remainingCount = (totalCount - previewLimit).coerceAtLeast(0)
    OutlinedButton(
        onClick = onToggle,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AppColors.BorderSand),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = AppColors.SurfaceAlabaster),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
    ) {
        Text(
            text = if (expanded) {
                "Thu gọn"
            } else {
                "Xem tất cả $totalCount $itemLabel (còn $remainingCount)"
            },
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.ForestTeal
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = AppColors.ForestTeal,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun EventCard(
    photo: Photo,
    onAddToCalendar: () -> Unit,
    onPhotoClick: () -> Unit
) {
    val isShopping = photo.detectedIntent == "SHOPPING" || photo.categoryIds.contains(DefaultCategories.TO_BUY)
    val dateText = remember(photo.suggestedEventDateMillis, isShopping) {
        val millis = photo.suggestedEventDateMillis ?: 0L
        if (millis > 0) {
            val formatter = SimpleDateFormat("EEE, dd/MM · HH:mm", Locale.getDefault())
            formatter.format(Date(millis))
        } else if (isShopping) {
            "Gợi ý nhắc mua sắm (+24h)"
        } else {
            "Sự kiện phát hiện được"
        }
    }

    val imageSource = if (photo.filePath.startsWith("content://")) {
        android.net.Uri.parse(photo.filePath)
    } else {
        File(photo.filePath)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clickable { onPhotoClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceAlabaster),
        border = BorderStroke(1.dp, AppColors.BorderSand),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageSource)
                    .crossfade(true)
                    .build(),
                contentDescription = photo.title,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.WarmSand),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val intentLabel = when (photo.detectedIntent) {
                        "EVENT" -> "SỰ KIỆN"
                        "SHOPPING" -> "MUA SẮM"
                        "TASK" -> "CẦN LÀM"
                        "READING" -> "BÀI ĐỌC"
                        else -> photo.detectedIntent ?: "SỰ KIỆN"
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isShopping) AppColors.OchreGoldLight else AppColors.PlumMutedLight
                    ) {
                        Text(
                            text = intentLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isShopping) AppColors.OchreGoldDark else AppColors.PlumMuted,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = photo.suggestedEventTitle ?: photo.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = AppColors.DeepPineText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = dateText,
                    fontSize = 12.sp,
                    color = AppColors.WarmTaupeText,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (photo.hasReminderCreated) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AppColors.SageOliveLight
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AppColors.ForestTeal,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Đã thêm",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.ForestTeal
                        )
                    }
                }
            } else {
                Button(
                    onClick = onAddToCalendar,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isShopping) AppColors.OchreGold else AppColors.ForestTeal
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isShopping) "Lên lịch mua" else "Thêm vào lịch", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ClutterItemCard(
    photo: Photo,
    customRuleDays: Int? = null,
    onDelete: () -> Unit,
    onPhotoClick: () -> Unit
) {
    val imageSource = if (photo.filePath.startsWith("content://")) {
        android.net.Uri.parse(photo.filePath)
    } else {
        File(photo.filePath)
    }

    val daysOld = remember(photo.dateAdded) {
        val diffMillis = System.currentTimeMillis() - photo.dateAdded
        val days = (diffMillis / (1000L * 60 * 60 * 24)).coerceAtLeast(0)
        "$days ngày trước"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clickable { onPhotoClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceAlabaster),
        border = BorderStroke(1.dp, AppColors.BorderSand),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageSource)
                    .crossfade(true)
                    .build(),
                contentDescription = photo.title,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.WarmSand),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = photo.title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = AppColors.DeepPineText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = daysOld, fontSize = 11.sp, color = AppColors.WarmTaupeText)
                    Text(text = " · ${formatFileSize(photo.fileSizeBytes)}", fontSize = 11.sp, color = AppColors.WarmTaupeText)

                    if (customRuleDays != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = AppColors.ForestTealSoft
                        ) {
                            Text(
                                text = "Quy tắc $customRuleDays ngày",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.ForestTeal,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    } else if (photo.detectedIntent != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = AppColors.WarmSandSoft
                        ) {
                            Text(
                                text = photo.detectedIntent!!,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.WarmTaupeText,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Xóa",
                    tint = AppColors.TerracottaAlert,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun DefaultRetentionRow(
    currentDays: Int,
    onSelectDays: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.ForestTealSoft.copy(alpha = 0.45f))
            .border(1.dp, AppColors.ForestTeal.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Thời gian mặc định chung",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.DeepPineText
            )
            Text(
                text = "Áp dụng cho ảnh chưa có quy tắc danh mục ($currentDays ngày)",
                fontSize = 11.sp,
                color = AppColors.WarmTaupeText
            )
        }

        Box {
            OutlinedButton(
                onClick = { expanded = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AppColors.BorderSand),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = "$currentDays ngày",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.DeepPineText
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AppColors.WarmTaupeText, modifier = Modifier.size(16.dp))
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(AppColors.SurfaceAlabaster)
            ) {
                listOf(7, 14, 30, 60, 90).forEach { days ->
                    DropdownMenuItem(
                        text = { Text("$days ngày", color = AppColors.DeepPineText) },
                        onClick = {
                            expanded = false
                            onSelectDays(days)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryAgingRow(
    category: Category,
    currentDays: Int?,
    defaultDays: Int,
    onSelectDays: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isCustom = currentDays != null
    val categoryColor = parseHexColor(category.colorHex)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.CanvasCream)
            .border(1.dp, AppColors.BorderSand, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(categoryColor, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = IconsHelper.getIconByName(category.iconName),
                    contentDescription = null,
                    tint = AppColors.SurfaceAlabaster,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = category.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.DeepPineText
                )
                Text(
                    text = if (isCustom) "$currentDays ngày (Quy tắc riêng)" else "$defaultDays ngày (Mặc định chung)",
                    fontSize = 11.sp,
                    color = if (isCustom) AppColors.ForestTeal else AppColors.WarmTaupeText
                )
            }
        }

        Box {
            OutlinedButton(
                onClick = { expanded = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AppColors.BorderSand),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = if (isCustom) "$currentDays ngày" else "Mặc định",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.DeepPineText
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AppColors.WarmTaupeText, modifier = Modifier.size(16.dp))
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(AppColors.SurfaceAlabaster)
            ) {
                DropdownMenuItem(
                    text = { Text("Mặc định chung ($defaultDays ngày)", color = AppColors.DeepPineText) },
                    onClick = {
                        expanded = false
                        onSelectDays(0)
                    }
                )
                listOf(7, 14, 30, 60, 90).forEach { days ->
                    DropdownMenuItem(
                        text = { Text("$days ngày", color = AppColors.DeepPineText) },
                        onClick = {
                            expanded = false
                            onSelectDays(days)
                        }
                    )
                }
            }
        }
    }
}
