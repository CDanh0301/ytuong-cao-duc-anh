package com.example.test2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Folder
import com.example.test2.shared.models.Photo
import com.example.test2.ui.RetroactiveCategoryPrompt
import java.io.File
import com.example.test2.ui.theme.IconsHelper

val PRESET_COLORS = listOf(
    "#3B82F6", // Blue
    "#EF4444", // Red
    "#10B981", // Emerald
    "#8B5CF6", // Purple
    "#F59E0B", // Amber
    "#EC4899", // Pink
    "#06B6D4", // Cyan
    "#64748B"  // Slate
)

fun parseHexColor(hex: String, default: Color = Color(0xFF3B82F6)): Color {
    return try {
        val clean = hex.removePrefix("#")
        val colorInt = clean.toLong(16)
        if (clean.length == 6) {
            Color(colorInt or 0x00000000FF000000)
        } else {
            Color(colorInt)
        }
    } catch (_: Exception) {
        default
    }
}

@Composable
fun SheetGrabber() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFFCBD5E1))
        )
    }
}

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, colorHex: String, iconName: String) -> Unit
) {
    var name by remember { mutableStateOf(TextFieldValue("")) }
    var selectedColor by remember { mutableStateOf(PRESET_COLORS.first()) }
    var selectedIcon by remember { mutableStateOf("folder") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SheetGrabber()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tạo thư mục mới",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("Tên thư mục", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Ví dụ: Du lịch hè, Hóa đơn...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        hintLocales = LocaleList(Locale("vi"), Locale("en"))
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("Màu sắc thư mục", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(8.dp))
                ColorPickerRow(
                    selectedColor = selectedColor,
                    onSelectColor = { selectedColor = it }
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("Biểu tượng thư mục", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(8.dp))
                IconPickerRow(
                    icons = IconsHelper.FOLDER_ICONS,
                    selectedIcon = selectedIcon,
                    accentColor = parseHexColor(selectedColor),
                    onSelectIcon = { selectedIcon = it }
                )

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Hủy")
                    }
                    Button(
                        onClick = { onConfirm(name.text.trim(), selectedColor, selectedIcon) },
                        enabled = name.text.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Tạo thư mục")
                    }
                }
            }
        }
    }
}

@Composable
fun RenameFolderDialog(
    folder: Folder,
    onDismiss: () -> Unit,
    onConfirm: (newName: String, colorHex: String, iconName: String) -> Unit
) {
    var name by remember { mutableStateOf(TextFieldValue(folder.name, selection = TextRange(folder.name.length))) }
    var selectedColor by remember { mutableStateOf(folder.colorHex) }
    var selectedIcon by remember { mutableStateOf(folder.iconName) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SheetGrabber()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chỉnh sửa thư mục",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("Tên thư mục", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        hintLocales = LocaleList(Locale("vi"), Locale("en"))
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("Màu sắc thư mục", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(8.dp))
                ColorPickerRow(
                    selectedColor = selectedColor,
                    onSelectColor = { selectedColor = it }
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("Biểu tượng thư mục", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(8.dp))
                IconPickerRow(
                    icons = IconsHelper.FOLDER_ICONS,
                    selectedIcon = selectedIcon,
                    accentColor = parseHexColor(selectedColor),
                    onSelectIcon = { selectedIcon = it }
                )

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Hủy")
                    }
                    Button(
                        onClick = { onConfirm(name.text.trim(), selectedColor, selectedIcon) },
                        enabled = name.text.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Lưu")
                    }
                }
            }
        }
    }
}

@Composable
fun CreateCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, colorHex: String, iconName: String) -> Unit
) {
    var name by remember { mutableStateOf(TextFieldValue("")) }
    var selectedColor by remember { mutableStateOf(PRESET_COLORS[3]) } // Purple default
    var selectedIcon by remember { mutableStateOf("tag") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SheetGrabber()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tạo danh mục mới",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("Tên danh mục", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Ví dụ: Thiên nhiên, Du lịch, Sự kiện...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        hintLocales = LocaleList(Locale("vi"), Locale("en"))
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("Màu sắc danh mục", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(8.dp))
                ColorPickerRow(
                    selectedColor = selectedColor,
                    onSelectColor = { selectedColor = it }
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("Biểu tượng danh mục", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(8.dp))
                IconPickerRow(
                    icons = IconsHelper.CATEGORY_ICONS,
                    selectedIcon = selectedIcon,
                    accentColor = parseHexColor(selectedColor),
                    onSelectIcon = { selectedIcon = it }
                )

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Hủy")
                    }
                    Button(
                        onClick = { onConfirm(name.text.trim(), selectedColor, selectedIcon) },
                        enabled = name.text.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Tạo danh mục")
                    }
                }
            }
        }
    }
}

@Composable
fun RenameCategoryDialog(
    category: Category,
    onDismiss: () -> Unit,
    onConfirm: (newName: String, newColorHex: String, newIconName: String) -> Unit
) {
    var name by remember { mutableStateOf(TextFieldValue(category.name, selection = TextRange(category.name.length))) }
    var selectedColor by remember { mutableStateOf(category.colorHex) }
    var selectedIcon by remember { mutableStateOf(category.iconName) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SheetGrabber()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chỉnh sửa danh mục",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("Tên danh mục", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        hintLocales = LocaleList(Locale("vi"), Locale("en"))
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("Màu sắc danh mục", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(8.dp))
                ColorPickerRow(
                    selectedColor = selectedColor,
                    onSelectColor = { selectedColor = it }
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("Biểu tượng danh mục", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(8.dp))
                IconPickerRow(
                    icons = IconsHelper.CATEGORY_ICONS,
                    selectedIcon = selectedIcon,
                    accentColor = parseHexColor(selectedColor),
                    onSelectIcon = { selectedIcon = it }
                )

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Hủy")
                    }
                    Button(
                        onClick = { onConfirm(name.text.trim(), selectedColor, selectedIcon) },
                        enabled = name.text.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Lưu")
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorPickerRow(
    selectedColor: String,
    onSelectColor: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PRESET_COLORS.forEach { colorHex ->
            val isSelected = colorHex.equals(selectedColor, ignoreCase = true)
            val color = parseHexColor(colorHex)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) Color(0xFF0F172A) else Color(0xFFCBD5E1),
                        shape = CircleShape
                    )
                    .clickable { onSelectColor(colorHex) }
            )
        }
    }
}

@Composable
private fun IconPickerRow(
    icons: List<Pair<String, String>>,
    selectedIcon: String,
    accentColor: Color,
    onSelectIcon: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icons.forEach { (iconName, label) ->
            val isSelected = iconName.equals(selectedIcon, ignoreCase = true)
            val vector = IconsHelper.getIconByName(iconName)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) accentColor.copy(alpha = 0.15f) else Color(0xFFF1F5F9))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) accentColor else Color(0xFFE2E8F0),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelectIcon(iconName) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = vector,
                    contentDescription = label,
                    tint = if (isSelected) accentColor else Color(0xFF475569),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(18.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                SheetGrabber()
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    fontSize = 13.5.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Hủy")
                    }
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Xóa")
                    }
                }
            }
        }
    }
}

@Composable
fun RetroactiveCategoryDialog(
    prompt: RetroactiveCategoryPrompt,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val catColor = parseHexColor(prompt.category.colorHex)
    val catIcon = IconsHelper.getIconByName(prompt.category.iconName)
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header with AI Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(catColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = catColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "Tự động gom ảnh (AI)",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng", modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Category pill tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(catColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(catIcon, contentDescription = null, tint = catColor, modifier = Modifier.size(16.dp))
                    Text(
                        text = prompt.category.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = catColor
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "AI phát hiện ${prompt.matchingPhotoIds.size} ảnh chụp trước đây có nội dung ngữ nghĩa phù hợp để chuyển vào danh mục này.",
                    fontSize = 14.sp,
                    color = Color(0xFF334155),
                    lineHeight = 20.sp
                )

                // Preview thumbnails row
                if (prompt.previewPhotos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (photo in prompt.previewPhotos) {
                            val imageSource = if (photo.filePath.startsWith("content://")) {
                                android.net.Uri.parse(photo.filePath)
                            } else {
                                File(photo.filePath)
                            }
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFE2E8F0))
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(imageSource)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = photo.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Bỏ qua")
                    }
                    Button(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = catColor),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Text("Chuyển ${prompt.matchingPhotoIds.size} ảnh")
                    }
                }
            }
        }
    }
}

@Composable
fun NoRetroactiveMatchesDialog(
    category: Category,
    onDismiss: () -> Unit
) {
    val catColor = parseHexColor(category.colorHex)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF1F5F9)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Không tìm thấy ảnh phù hợp",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "AI đã quét thư viện ảnh nhưng chưa tìm thấy ảnh nào có nội dung phù hợp với danh mục \"${category.name}\".\n\nCác ảnh chụp mới trong tương lai sẽ tự động được phân loại vào đây.",
                    fontSize = 13.5.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = catColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Đã hiểu", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun RetroactiveScanningDialog(
    category: Category
) {
    val catColor = parseHexColor(category.colorHex)

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(catColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = catColor,
                        strokeWidth = 3.5.dp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Đang quét ảnh cho danh mục...",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "AI đang đối soát toàn bộ thư viện ảnh để nhận diện các ảnh phù hợp với danh mục \"${category.name}\".\nVui lòng đợi trong giây lát...",
                    fontSize = 13.5.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
