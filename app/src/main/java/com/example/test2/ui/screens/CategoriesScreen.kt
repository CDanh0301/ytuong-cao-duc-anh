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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Photo
import com.example.test2.ui.theme.IconsHelper

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton

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
            title = {
                Text(text = "Danh mục AI tùy chỉnh (Pro)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text(
                    text = "Gói Miễn phí sử dụng 6 danh mục chuẩn cố định (Lịch & Sự kiện, Cần mua & Chi tiêu, Việc cần làm, Đọc & Tham khảo, Thiên nhiên & Phong cảnh, Cá nhân & Giấy tờ). Nâng cấp Pro để tự tạo danh mục riêng không giới hạn!",
                    fontSize = 14.sp,
                    color = Color(0xFF475569)
                )
            },
            confirmButton = {
                TextButton(onClick = { showProDialog = false }) {
                    Text("Đã hiểu", fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // App Header matching index.html
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Danh mục",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "${categories.size} danh mục",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B)
                )
            }

            Button(
                onClick = {
                    if (isPro) {
                        onCreateCategoryClick()
                    } else {
                        showProDialog = true
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isPro) Color(0xFF2563EB) else Color(0xFF64748B)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isPro) "Tạo danh mục" else "Tạo danh mục (Pro)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Categories List
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
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
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Chưa có danh mục nào",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF64748B)
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Colored icon circle
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(catColor.copy(alpha = 0.15f))
                        .border(1.5.dp, catColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = catIcon,
                        contentDescription = null,
                        tint = catColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = category.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "$photoCount ảnh được gắn thẻ",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Tùy chọn",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Xem ảnh") },
                        leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onClick()
                        }
                    )
                    if (onAutoCategorize != null) {
                        DropdownMenuItem(
                            text = { Text("Tự động gom ảnh (AI)") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFF6366F1)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onAutoCategorize()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Chỉnh sửa") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Xóa", color = Color(0xFFEF4444)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color(0xFFEF4444)
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
