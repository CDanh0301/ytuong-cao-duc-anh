package com.example.test2.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test2.BuildConfig
import com.example.test2.shared.models.UserAccountTier
import com.example.test2.ui.components.AppScreenHeader
import com.example.test2.ui.theme.AppColors

@Composable
fun SettingsScreen(
    accountTier: UserAccountTier,
    isVaultEnabled: Boolean,
    protectedPhotosCount: Int,
    onToggleTier: (Boolean) -> Unit,
    onToggleVault: (Boolean) -> Unit,
    onRequestRestorePhotos: () -> Unit,
    onPinWidget: () -> Unit,
    onOpenQuickSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.CanvasCream),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            AppScreenHeader(
                icon = Icons.Default.Settings,
                title = "Cài đặt.",
                subtitle = "Quản lý gói tài khoản, Két bảo mật & Tiện ích màn hình chính",
                kicker = "MemoCap"
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        item {
            AccountTierCard(
                accountTier = accountTier,
                onToggleTier = onToggleTier
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        item {
            VaultToggleSettingCard(
                isVaultEnabled = isVaultEnabled,
                onToggleVault = onToggleVault
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        if (isVaultEnabled || protectedPhotosCount > 0) {
            item {
                VaultRestoreWarningCard(
                    protectedPhotosCount = protectedPhotosCount,
                    onRequestRestorePhotos = onRequestRestorePhotos
                )
                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        item {
            WidgetSettingsCard(
                onPinWidget = onPinWidget,
                onOpenQuickSearch = onOpenQuickSearch
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AccountTierCard(
    accountTier: UserAccountTier,
    onToggleTier: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (accountTier.isPro) AppColors.ForestTealSoft else AppColors.SurfaceAlabaster
        ),
        border = BorderStroke(
            1.dp,
            if (accountTier.isPro) AppColors.ForestTeal.copy(alpha = 0.25f) else AppColors.BorderSand
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                            .size(40.dp)
                            .background(
                                if (accountTier.isPro) AppColors.ForestTeal else AppColors.WarmSand,
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (accountTier.isPro) Icons.Default.Star else Icons.Default.Security,
                            contentDescription = null,
                            tint = if (accountTier.isPro) AppColors.SurfaceAlabaster else AppColors.ForestTeal,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = if (accountTier.isPro) "Gói Pro" else "Gói Miễn phí",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = AppColors.DeepPineText
                        )
                        Text(
                            text = if (accountTier.isPro) {
                                "Không giới hạn quét AI & Bảo vệ PII"
                            } else {
                                "50 lượt quét OCR miễn phí mỗi tháng"
                            },
                            fontSize = 12.sp,
                            color = AppColors.WarmTaupeText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (BuildConfig.DEBUG) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Switch(
                            checked = accountTier.isPro,
                            onCheckedChange = onToggleTier,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AppColors.SurfaceAlabaster,
                                checkedTrackColor = AppColors.ForestTeal,
                                uncheckedThumbColor = AppColors.SurfaceAlabaster,
                                uncheckedTrackColor = AppColors.WarmSand,
                                uncheckedBorderColor = AppColors.BorderSand
                            )
                        )
                        Text(
                            text = "Chế độ Pro (Debug)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppColors.WarmTaupeText
                        )
                    }
                } else if (accountTier.isPro) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AppColors.ForestTeal.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "ĐANG HOẠT ĐỘNG",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.ForestTeal,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
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
                        color = AppColors.DeepPineText
                    )
                    Text(
                        text = "${accountTier.aiScansUsedThisMonth} / ${accountTier.aiScansQuota}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.ForestTeal
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(99.dp)),
                    color = AppColors.ForestTeal,
                    trackColor = AppColors.WarmSand
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.SurfaceAlabaster.copy(alpha = 0.78f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AppColors.ForestTeal,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Đang kích hoạt: Quét AI không giới hạn, đồng bộ Lịch & bảo vệ PII trên thiết bị",
                            fontSize = 12.sp,
                            color = AppColors.ForestTeal,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultToggleSettingCard(
    isVaultEnabled: Boolean,
    onToggleVault: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceAlabaster),
        border = BorderStroke(
            1.dp,
            if (isVaultEnabled) AppColors.SageOlive.copy(alpha = 0.55f) else AppColors.BorderSand
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isVaultEnabled) AppColors.SageOliveLight else AppColors.WarmSand),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isVaultEnabled) Icons.Default.Lock else Icons.Default.Security,
                        contentDescription = null,
                        tint = if (isVaultEnabled) AppColors.ForestTeal else AppColors.WarmTaupeText,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Két bảo mật & Khóa máy",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = AppColors.DeepPineText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isVaultEnabled) {
                            "Ảnh màn hình được bảo vệ riêng tư và yêu cầu mở khóa máy khi vào app"
                        } else {
                            "Ảnh màn hình hiển thị bình thường ở Bộ sưu tập máy"
                        },
                        fontSize = 12.sp,
                        color = AppColors.WarmTaupeText,
                        lineHeight = 16.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = isVaultEnabled,
                onCheckedChange = onToggleVault,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AppColors.SurfaceAlabaster,
                    checkedTrackColor = AppColors.ForestTeal,
                    uncheckedThumbColor = AppColors.SurfaceAlabaster,
                    uncheckedTrackColor = AppColors.WarmSand,
                    uncheckedBorderColor = AppColors.BorderSand
                )
            )
        }
    }
}

@Composable
private fun VaultRestoreWarningCard(
    protectedPhotosCount: Int,
    onRequestRestorePhotos: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.OchreGoldLight),
        border = BorderStroke(1.5.dp, AppColors.OchreGold.copy(alpha = 0.65f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = AppColors.OchreGoldDark,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "LƯU Ý QUAN TRỌNG KHI GỠ ỨNG DỤNG",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.5.sp,
                    color = AppColors.OchreGoldDark
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.SurfaceAlabaster.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = "• Khi Két bật, ảnh được mã hóa và lưu tại bộ nhớ riêng của ứng dụng để bảo vệ quyền riêng tư tuyệt đối.",
                    fontSize = 12.sp,
                    color = AppColors.OchreGoldDark,
                    lineHeight = 17.sp
                )
                Text(
                    text = "• Hệ điều hành Android sẽ xóa toàn bộ ảnh trong Két nếu bạn gỡ cài đặt ứng dụng.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TerracottaAlert,
                    lineHeight = 17.sp
                )
                Text(
                    text = "• Luôn nhấn nút Khôi phục bên dưới trước khi gỡ ứng dụng để đưa ảnh trở lại Bộ sưu tập máy.",
                    fontSize = 12.sp,
                    color = AppColors.OchreGoldDark,
                    lineHeight = 17.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onRequestRestorePhotos,
                enabled = protectedPhotosCount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.OchreGold,
                    contentColor = AppColors.SurfaceAlabaster,
                    disabledContainerColor = AppColors.WarmSand,
                    disabledContentColor = AppColors.WarmMutedText
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (protectedPhotosCount > 0) {
                        "Khôi phục $protectedPhotosCount ảnh về máy ngay"
                    } else {
                        "Không có ảnh nào trong Két"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun WidgetSettingsCard(
    onPinWidget: () -> Unit,
    onOpenQuickSearch: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceAlabaster),
        border = BorderStroke(1.dp, AppColors.BorderSand),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppColors.ForestTealSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Widgets,
                        contentDescription = null,
                        tint = AppColors.ForestTeal,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Thanh tìm kiếm màn hình chính (Widget)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = AppColors.DeepPineText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Đặt thanh tìm kiếm 1 chạm ra màn hình chính (kéo giãn 4×1 / 4×2, hỗ trợ nền tối). Chạm vào để tìm ngay mọi ảnh bằng OCR & AI.",
                        fontSize = 12.sp,
                        color = AppColors.WarmTaupeText,
                        lineHeight = 16.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPinWidget,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.ForestTeal,
                        contentColor = AppColors.SurfaceAlabaster
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Icon(imageVector = Icons.Default.Widgets, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Ghim Widget", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onOpenQuickSearch,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AppColors.BorderSand),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = AppColors.ForestTeal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Mở tìm kiếm",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.DeepPineText
                    )
                }
            }
        }
    }
}
