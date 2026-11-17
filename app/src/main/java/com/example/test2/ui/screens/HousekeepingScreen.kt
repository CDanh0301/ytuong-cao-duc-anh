package com.example.test2.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.example.test2.BuildConfig
import com.example.test2.shared.models.AgingSettings
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Photo
import com.example.test2.shared.models.UserAccountTier
import com.example.test2.ui.theme.IconsHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HousekeepingScreen(
    photos: List<Photo>,
    accountTier: UserAccountTier,
    agingSettings: AgingSettings,
    agingPhotos: List<Photo>,
    categories: List<Category> = emptyList(),
    isVaultEnabled: Boolean = false,
    onToggleVault: (Boolean) -> Unit = {},
    onRequestRestorePhotos: () -> Unit = {},
    onToggleTier: (Boolean) -> Unit,
    onCleanClutter: (List<Photo>) -> Unit,
    onDeleteSinglePhoto: (Photo) -> Unit,
    onAddToCalendar: (Photo) -> Unit,
    onPhotoClick: (Photo) -> Unit,
    onSetCategoryRetentionRule: (categoryId: String, retentionDays: Int) -> Unit = { _, _ -> },
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
                photo.categoryIds.contains("cat_tobuy") ||
                photo.categoryIds.contains("cat_events")
            )
        }
    }

    val totalClutterBytes = remember(agingPhotos) {
        agingPhotos.sumOf { it.fileSizeBytes }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC)),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        // 1. Header matching index.html aesthetics
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Trợ lý thông minh & Dọn dẹp",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }
                        Text(
                            text = "Nhận diện ý định AI, Bảo vệ dữ liệu cá nhân & Tối ưu bộ nhớ",
                            fontSize = 11.5.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 2. Freemium Quota & Tier Card (IDEA.md)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (accountTier.isPro) Color(0xFFEFF6FF) else Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (accountTier.isPro) Color(0xFF2563EB) else Color(0xFFF1F5F9),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (accountTier.isPro) Icons.Default.Star else Icons.Default.Security,
                                    contentDescription = null,
                                    tint = if (accountTier.isPro) Color.White else Color(0xFF2563EB),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = if (accountTier.isPro) "Gói Pro" else "Gói Miễn phí",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = if (accountTier.isPro) "Không giới hạn quét AI & Bảo vệ PII" else "50 lượt quét OCR miễn phí mỗi tháng",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Debug-only: Toggle Pro switch để nhà phát triển kiểm thử tính năng Pro
                        if (BuildConfig.DEBUG) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Switch(
                                    checked = accountTier.isPro,
                                    onCheckedChange = { onToggleTier(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF2563EB)
                                    )
                                )
                                Text(
                                    text = "Chế độ Pro (Debug)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF64748B)
                                )
                            }
                        } else if (accountTier.isPro) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFDBEAFE)
                            ) {
                                Text(
                                    text = "ĐANG HOẠT ĐỘNG",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1D4ED8),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!accountTier.isPro) {
                        val progress = (accountTier.aiScansUsedThisMonth.toFloat() / accountTier.aiScansQuota.toFloat()).coerceIn(0f, 1f)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Lượt dùng AI",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF334155)
                            )
                            Text(
                                text = "${accountTier.aiScansUsedThisMonth} / ${accountTier.aiScansQuota}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2563EB)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF2563EB),
                            trackColor = Color(0xFFE2E8F0),
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFDBEAFE),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF1D4ED8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Đang kích hoạt: Quét AI không giới hạn, đồng bộ Lịch & bảo vệ PII trên thiết bị",
                                    fontSize = 12.sp,
                                    color = Color(0xFF1D4ED8),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Privacy Vault & Device Lock Setting Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, if (isVaultEnabled) Color(0xFFBBF7D0) else Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isVaultEnabled) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isVaultEnabled) Icons.Default.Lock else Icons.Default.Security,
                                contentDescription = null,
                                tint = if (isVaultEnabled) Color(0xFF16A34A) else Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Két bảo mật & Khóa máy",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isVaultEnabled) "Ảnh màn hình được bảo vệ riêng tư và yêu cầu mở khóa máy khi vào app" else "Ảnh màn hình hiển thị bình thường ở Bộ sưu tập máy",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 16.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = isVaultEnabled,
                        onCheckedChange = { onToggleVault(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF16A34A)
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Card warning & action: Restore before uninstall
        val protectedPhotosCount = photos.count {
            it.filePath.endsWith(".enc") || (it.isScreenshot && !it.filePath.startsWith("content://"))
        }
        if (isVaultEnabled || protectedPhotosCount > 0) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Khôi phục ảnh trước khi gỡ ứng dụng",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF92400E)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Lưu ý: Nếu bạn có dự định gỡ cài đặt ứng dụng, hãy khôi phục tất cả ảnh về thiết bị trước để tránh việc hệ điều hành Android tự động xóa ảnh trong thư mục riêng của app.",
                            fontSize = 12.sp,
                            color = Color(0xFFB45309),
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onRequestRestorePhotos() },
                            enabled = protectedPhotosCount > 0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD97706),
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFFE2E8F0),
                                disabledContentColor = Color(0xFF94A3B8)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (protectedPhotosCount > 0) "Khôi phục $protectedPhotosCount ảnh về máy" else "Không có ảnh nào trong Két",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // 3. Pending Calendar Events Section (IDEA.md)
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = Color(0xFF7C3AED),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sự kiện lịch phát hiện được",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                    Text(
                        text = "${pendingEventPhotos.size} sự kiện",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
                Text(
                    text = "Sự kiện được trích xuất thông minh từ ảnh chụp màn hình bằng AI trên thiết bị",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )
            }
        }

        if (pendingEventPhotos.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardDefaults.outlinedCardBorder()
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
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Không có sự kiện lịch nào đang chờ",
                                fontSize = 13.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        } else {
            items(pendingEventPhotos, key = { "event_${it.id}" }) { photo ->
                EventCard(
                    photo = photo,
                    onAddToCalendar = { onAddToCalendar(photo) },
                    onPhotoClick = { onPhotoClick(photo) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // 4. Aging Housekeeping & Clutter Section (IDEA.md)
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            Icons.Default.CleaningServices,
                            contentDescription = null,
                            tint = Color(0xFFEA580C),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ảnh chụp màn hình cũ & Rác bộ nhớ",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (agingPhotos.isNotEmpty()) {
                            Button(
                                onClick = { showBatchDeleteDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFDC2626)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Dọn tất cả (${agingPhotos.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Gear button for Customizing Aging Rules & Retention
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF1F5F9))
                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                .clickable { showAgingSettingsDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Cài đặt dọn dẹp",
                                tint = Color(0xFF334155),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Ảnh chụp màn hình cũ hơn ${agingSettings.retentionDays} ngày",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                    if (agingPhotos.isNotEmpty()) {
                        Text(
                            text = "~${formatFileSize(totalClutterBytes)} có thể giải phóng",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF16A34A)
                        )
                    }
                }

                // Background Aging Monitor Status Card (IDEA.md Step 6 & Part 3)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFDCFCE7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CleaningServices,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Tự động theo dõi rác nền",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF15803D)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF16A34A))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text("HOẠT ĐỘNG (24H)", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                                val lastChecked = agingMonitorInfo.first
                                val checkText = if (lastChecked > 0L) {
                                    val timeStr = SimpleDateFormat("HH:mm, dd/MM", Locale.getDefault()).format(Date(lastChecked))
                                    "Lần kiểm tra nền gần nhất: $timeStr"
                                } else {
                                    "Định kỳ kiểm tra dọn rác nền 24h một lần"
                                }
                                Text(
                                    text = checkText,
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF166534)
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onRunAgingScanNow,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp),
                            border = BorderStroke(1.dp, Color(0xFF86EFAC))
                        ) {
                            Text("Quét ngay", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF15803D))
                        }
                    }
                }
            }
        }

        if (agingPhotos.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardDefaults.outlinedCardBorder()
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
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Thư viện ảnh của bạn thật gọn gàng!",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Không có ảnh chụp màn hình cũ hoặc đã xử lý nào cần dọn dẹp.",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        } else {
            items(agingPhotos, key = { "clutter_${it.id}" }) { photo ->
                ClutterItemCard(
                    photo = photo,
                    onDelete = { onDeleteSinglePhoto(photo) },
                    onPhotoClick = { onPhotoClick(photo) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // Confirmation dialog for batch cleaning
    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = {
                Text("Dọn dẹp ${agingPhotos.size} ảnh chụp màn hình cũ?")
            },
            text = {
                Text(
                    "Thao tác này sẽ xóa ${agingPhotos.size} ảnh chụp màn hình cũ hơn ${agingSettings.retentionDays} ngày, giải phóng khoảng ${formatFileSize(totalClutterBytes)} dung lượng bộ nhớ."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBatchDeleteDialog = false
                        onCleanClutter(agingPhotos)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Xóa tất cả")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showBatchDeleteDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    // Aging & Retention Rules Settings Dialog (opened from gear button)
    if (showAgingSettingsDialog) {
        Dialog(
            onDismissRequest = { showAgingSettingsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Dialog Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEFF6FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Thời gian lưu trữ & Dọn dẹp",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "Quy tắc dọn dẹp",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        IconButton(
                            onClick = { showAgingSettingsDialog = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Đóng",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Pro status badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Quy tắc lưu trữ theo danh mục",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color(0xFF334155)
                        )
                        if (accountTier.isPro) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFDBEAFE)
                            ) {
                                Text(
                                    text = "PRO ĐANG BẬT",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1D4ED8),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFF1F5F9)
                            ) {
                                Text(
                                    text = "TÍNH NĂNG PRO",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
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
                        color = Color(0xFF64748B),
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

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
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Thời gian mặc định chung: ${agingSettings.retentionDays} ngày",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF334155)
                                    )
                                    Text(
                                        text = "Tất cả ảnh chụp màn hình cũ hơn ${agingSettings.retentionDays} ngày sẽ được đưa vào danh sách dọn dẹp.",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { showAgingSettingsDialog = false },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
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
fun EventCard(
    photo: Photo,
    onAddToCalendar: () -> Unit,
    onPhotoClick: () -> Unit
) {
    val isShopping = photo.detectedIntent == "SHOPPING" || photo.categoryIds.contains("cat_tobuy")
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
            .padding(horizontal = 16.dp)
            .clickable { onPhotoClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageSource)
                    .crossfade(true)
                    .build(),
                contentDescription = photo.title,
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF1F5F9)),
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
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFF3E8FF)
                    ) {
                        Text(
                            text = intentLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7C3AED),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (photo.piiScrubbedText != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFDCFCE7)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Đã bảo vệ PII",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF16A34A)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = photo.suggestedEventTitle ?: photo.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = dateText,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (photo.hasReminderCreated) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFDCFCE7)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Đã thêm",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF16A34A)
                        )
                    }
                }
            } else {
                Button(
                    onClick = onAddToCalendar,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isShopping) Color(0xFFF59E0B) else Color(0xFF7C3AED)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
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
            .padding(horizontal = 16.dp)
            .clickable { onPhotoClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF1F5F9)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = photo.title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = daysOld,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                    Text(
                        text = " · ${formatFileSize(photo.fileSizeBytes)}",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )

                    if (photo.detectedIntent != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFF1F5F9)
                        ) {
                            Text(
                                text = photo.detectedIntent!!,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF475569),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
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
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp)
                )
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

    val categoryColor = remember(category.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(category.colorHex))
        } catch (_: Exception) {
            Color(0xFF6366F1)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF8FAFC))
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
                    .size(32.dp)
                    .background(categoryColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = IconsHelper.getIconByName(category.iconName),
                    contentDescription = null,
                    tint = categoryColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = category.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = if (isCustom) "$currentDays ngày (Quy tắc riêng)" else "$defaultDays ngày (Mặc định chung)",
                    fontSize = 11.sp,
                    color = if (isCustom) Color(0xFF2563EB) else Color(0xFF64748B)
                )
            }
        }

        Box {
            OutlinedButton(
                onClick = { expanded = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = if (isCustom) "${currentDays} ngày" else "Mặc định",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Mặc định chung (${defaultDays} ngày)") },
                    onClick = {
                        expanded = false
                        onSelectDays(0)
                    }
                )
                listOf(7, 14, 30, 60, 90).forEach { days ->
                    DropdownMenuItem(
                        text = { Text("$days ngày") },
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

