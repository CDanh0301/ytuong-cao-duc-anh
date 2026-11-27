package com.example.test2.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Photo
import com.example.test2.ui.components.AppScreenHeader
import com.example.test2.ui.theme.AppColors
import com.example.test2.ui.theme.IconsHelper

@Composable
fun CategoriesScreen(
    categories: List<Category>,
    photos: List<Photo>,
    isPro: Boolean = false,
    onSelectCategory: (String) -> Unit,
    onCreateCategoryClick: () -> Unit,
    onEditCategoryClick: (Category) -> Unit,
    onDeleteCategoryClick: (Category) -> Unit,
    onAutoCategorizeClick: ((Category) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showProDialog by remember { mutableStateOf(false) }

    if (showProDialog) {
        AlertDialog(
            onDismissRequest = { showProDialog = false },
            containerColor = AppColors.SurfaceAlabaster,
            title = {
                Text(
                    text = "Danh mục AI tùy chỉnh (Pro)",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    color = AppColors.ForestTeal
                )
            },
            text = {
                Text(
                    text = "Gói Miễn phí sử dụng 5 danh mục chuẩn mặc định (Lịch & Sự kiện, Cần mua & Chi tiêu, Đọc & Tham khảo, Thiên nhiên & Phong cảnh, Cá nhân & Giấy tờ). Nâng cấp Pro để tự tạo danh mục riêng không giới hạn!",
                    fontSize = 13.5.sp,
                    color = AppColors.WarmTaupeText,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showProDialog = false }) {
                    Text("Đã hiểu", fontWeight = FontWeight.Bold, color = AppColors.ForestTeal)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.CanvasCream)
    ) {
        AppScreenHeader(
            icon = Icons.AutoMirrored.Filled.Label,
            title = "Danh mục.",
            subtitle = "${categories.size} danh mục",
            kicker = "Phân loại tự động",
            trailingContent = {
                Button(
                    onClick = {
                        if (isPro) {
                            onCreateCategoryClick()
                        } else {
                            showProDialog = true
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPro) AppColors.ForestTeal else AppColors.WarmSand,
                        contentColor = if (isPro) AppColors.SurfaceAlabaster else AppColors.DeepPineText
                    ),
                    contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isPro) "Tạo danh mục" else "Tạo danh mục (Pro)",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (categories.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Style,
                                contentDescription = null,
                                tint = AppColors.WarmMutedText,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Chưa có danh mục nào",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = AppColors.WarmTaupeText
                            )
                        }
                    }
                }
            } else {
                items(categories, key = { it.id }) { category ->
                    val count = photos.count { category.id in it.categoryIds }
                    CategoryCardView(
                        category = category,
                        photoCount = count,
                        onClick = { onSelectCategory(category.id) },
                        onEdit = { onEditCategoryClick(category) },
                        onDelete = { onDeleteCategoryClick(category) },
                        onAutoCategorize = onAutoCategorizeClick?.let { { it(category) } }
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryCardView(
    category: Category,
    photoCount: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAutoCategorize: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val catColor = parseHexColor(category.colorHex)
    val catIcon = IconsHelper.getIconByName(category.iconName)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceAlabaster),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppColors.BorderSand, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(catColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = catIcon,
                        contentDescription = null,
                        tint = AppColors.SurfaceAlabaster,
                        modifier = Modifier.size(21.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = category.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.DeepPineText
                    )
                    Text(
                        text = "$photoCount ảnh được gắn thẻ",
                        fontSize = 12.sp,
                        color = AppColors.WarmTaupeText
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppColors.WarmSandSoft)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Tùy chọn",
                        tint = AppColors.WarmTaupeText,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(AppColors.SurfaceAlabaster)
                ) {
                    DropdownMenuItem(
                        text = { Text("Xem ảnh", color = AppColors.DeepPineText) },
                        leadingIcon = {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = AppColors.ForestTeal)
                        },
                        onClick = {
                            menuExpanded = false
                            onClick()
                        }
                    )
                    if (onAutoCategorize != null) {
                        DropdownMenuItem(
                            text = { Text("Tự động gom ảnh (AI)", color = AppColors.DeepPineText) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = AppColors.OchreGold
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onAutoCategorize()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Chỉnh sửa", color = AppColors.DeepPineText) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = AppColors.WarmTaupeText) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Xóa", color = AppColors.TerracottaAlert) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = AppColors.TerracottaAlert
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
