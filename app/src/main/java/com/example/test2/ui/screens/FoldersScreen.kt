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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.test2.shared.models.Folder
import com.example.test2.shared.models.Photo
import com.example.test2.ui.theme.IconsHelper
import java.io.File

@Composable
fun FoldersScreen(
    folders: List<Folder>,
    photos: List<Photo>,
    onSelectFolder: (String) -> Unit,
    onCreateFolderClick: () -> Unit,
    onRenameFolderClick: (Folder) -> Unit,
    onDeleteFolderClick: (Folder) -> Unit,
    modifier: Modifier = Modifier
) {
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
                    text = "Thư mục",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "${folders.size} thư mục",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B)
                )
            }

            Button(
                onClick = onCreateFolderClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tạo thư mục", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Folders Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(folders, key = { it.id }) { folder ->
                val folderPhotos = photos.filter { it.folderId == folder.id }
                val latestPhoto = folderPhotos.maxByOrNull { it.dateAdded }

                FolderCardView(
                    folder = folder,
                    photoCount = folderPhotos.size,
                    latestPhoto = latestPhoto,
                    onClick = { onSelectFolder(folder.id) },
                    onRename = { onRenameFolderClick(folder) },
                    onDelete = { onDeleteFolderClick(folder) }
                )
            }
        }
    }
}

@Composable
fun FolderCardView(
    folder: Folder,
    photoCount: Int,
    latestPhoto: Photo?,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val folderColor = parseHexColor(folder.colorHex)
    val folderIcon = IconsHelper.getIconByName(folder.iconName)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column {
            // Folder Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.25f)
                    .background(folderColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (latestPhoto != null) {
                    val imageSource = if (latestPhoto.filePath.startsWith("content://")) {
                        android.net.Uri.parse(latestPhoto.filePath)
                    } else {
                        File(latestPhoto.filePath)
                    }

                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageSource)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Gradient tint over image for icon visibility
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.25f))
                    )
                }

                // Folder Icon Badge
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(folderColor.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = folderIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Default badge
                if (folder.isDefault) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.9f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Mặc định",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = folderColor
                        )
                    }
                }
            }

            // Info row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$photoCount ảnh",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(28.dp)
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
                        DropdownMenuItem(
                            text = { Text("Đổi tên") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        if (!folder.isDefault) {
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
    }
}
