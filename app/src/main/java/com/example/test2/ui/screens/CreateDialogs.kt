package com.example.test2.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Folder
import com.example.test2.ui.RetroactiveCategoryPrompt
import com.example.test2.ui.theme.AppColors
import com.example.test2.ui.theme.IconsHelper
import java.io.File
import kotlin.math.abs

val PRESET_COLORS = listOf(
    "#1E4240", // Forest Teal
    "#5A7364", // Pine Moss
    "#7E8D78", // Sage Olive
    "#B89054", // Ochre Gold
    "#C2593F", // Terracotta
    "#7A5C7E", // Dusty Plum
    "#3A5A6B"  // Slate Teal
)

private val THEME_GRADIENT_STOPS = listOf(
    0.00f to Color(0xFF1E4240),
    0.18f to Color(0xFF5A7364),
    0.35f to Color(0xFF7E8D78),
    0.54f to Color(0xFFB89054),
    0.72f to Color(0xFFC2593F),
    0.88f to Color(0xFF7A5C7E),
    1.00f to Color(0xFF3A5A6B)
)

fun parseHexColor(hex: String, default: Color = AppColors.ForestTeal): Color {
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

private fun colorToHex(color: Color): String {
    val argb = color.toArgb() and 0x00FFFFFF
    return String.format("#%06X", argb)
}

private fun sampleThemeGradient(fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    for (i in 0 until THEME_GRADIENT_STOPS.lastIndex) {
        val (p1, c1) = THEME_GRADIENT_STOPS[i]
        val (p2, c2) = THEME_GRADIENT_STOPS[i + 1]
        if (f in p1..p2) {
            val localT = if (p2 > p1) (f - p1) / (p2 - p1) else 0f
            return lerp(c1, c2, localT)
        }
    }
    return THEME_GRADIENT_STOPS.last().second
}

private fun closestGradientFraction(hex: String): Float {
    val target = parseHexColor(hex)
    var bestF = 0f
    var bestDist = Float.MAX_VALUE
    for (step in 0..50) {
        val f = step / 50f
        val c = sampleThemeGradient(f)
        val dist = abs(c.red - target.red) + abs(c.green - target.green) + abs(c.blue - target.blue)
        if (dist < bestDist) {
            bestDist = dist
            bestF = f
        }
    }
    return bestF
}

@Composable
fun SheetGrabber() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AppColors.BorderSand)
        )
    }
}

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, colorHex: String, iconName: String) -> Unit
) {
    EntityEditorDialog(
        dialogTitle = "Tạo thư mục mới",
        fieldLabel = "Tên thư mục",
        placeholder = "Ví dụ: Du lịch hè, Hóa đơn...",
        initialName = "",
        initialColorHex = PRESET_COLORS.first(),
        initialIconName = "folder",
        icons = IconsHelper.FOLDER_ICONS,
        confirmLabel = "Tạo thư mục",
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

@Composable
fun RenameFolderDialog(
    folder: Folder,
    onDismiss: () -> Unit,
    onConfirm: (newName: String, colorHex: String, iconName: String) -> Unit
) {
    EntityEditorDialog(
        dialogTitle = "Chỉnh sửa thư mục",
        fieldLabel = "Tên thư mục",
        placeholder = "",
        initialName = folder.name,
        initialColorHex = folder.colorHex,
        initialIconName = folder.iconName,
        icons = IconsHelper.FOLDER_ICONS,
        confirmLabel = "Lưu",
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

@Composable
fun CreateCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, colorHex: String, iconName: String) -> Unit
) {
    EntityEditorDialog(
        dialogTitle = "Tạo danh mục mới",
        fieldLabel = "Tên danh mục",
        placeholder = "Ví dụ: Thiên nhiên, Du lịch, Sự kiện...",
        initialName = "",
        initialColorHex = AppColors.ForestTeal.let(::colorToHex),
        initialIconName = "tag",
        icons = IconsHelper.CATEGORY_ICONS,
        confirmLabel = "Tạo danh mục",
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

@Composable
fun RenameCategoryDialog(
    category: Category,
    onDismiss: () -> Unit,
    onConfirm: (newName: String, newColorHex: String, newIconName: String) -> Unit
) {
    EntityEditorDialog(
        dialogTitle = "Chỉnh sửa danh mục",
        fieldLabel = "Tên danh mục",
        placeholder = "",
        initialName = category.name,
        initialColorHex = category.colorHex,
        initialIconName = category.iconName,
        icons = IconsHelper.CATEGORY_ICONS,
        confirmLabel = "Lưu",
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

@Composable
private fun EntityEditorDialog(
    dialogTitle: String,
    fieldLabel: String,
    placeholder: String,
    initialName: String,
    initialColorHex: String,
    initialIconName: String,
    icons: List<Pair<String, String>>,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, colorHex: String, iconName: String) -> Unit
) {
    var name by remember { mutableStateOf(TextFieldValue(initialName, selection = TextRange(initialName.length))) }
    var selectedColor by remember { mutableStateOf(initialColorHex) }
    var selectedIcon by remember { mutableStateOf(initialIconName) }
    val previewColor = parseHexColor(selectedColor)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp)),
            color = AppColors.SurfaceAlabaster,
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
                        text = dialogTitle,
                        fontFamily = FontFamily.Serif,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.ForestTeal
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng", tint = AppColors.WarmTaupeText)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Live Squircle Preview
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.CanvasCream)
                        .border(1.dp, AppColors.BorderSand, RoundedCornerShape(16.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(previewColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = IconsHelper.getIconByName(selectedIcon),
                            contentDescription = null,
                            tint = AppColors.SurfaceAlabaster,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name.text.ifBlank { "Xem trước" },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.DeepPineText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Màu: $selectedColor",
                            fontSize = 11.5.sp,
                            color = AppColors.WarmTaupeText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(fieldLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.DeepPineText)
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder, color = AppColors.WarmMutedText) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        hintLocales = LocaleList(Locale("vi"), Locale("en"))
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AppColors.CanvasCream,
                        unfocusedContainerColor = AppColors.CanvasCream,
                        focusedBorderColor = AppColors.ForestTeal,
                        unfocusedBorderColor = AppColors.BorderSand
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Màu sắc", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.DeepPineText)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppColors.WarmSand)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(selectedColor, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = AppColors.WarmTaupeText)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                GradientColorPickerBar(
                    selectedColor = selectedColor,
                    onSelectColor = { selectedColor = it }
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("Biểu tượng", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.DeepPineText)
                Spacer(modifier = Modifier.height(8.dp))
                IconPickerRow(
                    icons = icons,
                    selectedIcon = selectedIcon,
                    accentColor = previewColor,
                    onSelectIcon = { selectedIcon = it }
                )

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.WarmTaupeText),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Hủy")
                    }
                    Button(
                        onClick = { onConfirm(name.text.trim(), selectedColor, selectedIcon) },
                        enabled = name.text.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.ForestTeal),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(confirmLabel)
                    }
                }
            }
        }
    }
}

/**
 * Continuous theme-aligned gradient bar supporting both tap and horizontal drag
 * so users can pick any shade harmonizing with MemoCap's Editorial Organic palette.
 */
@Composable
private fun GradientColorPickerBar(
    selectedColor: String,
    onSelectColor: (String) -> Unit
) {
    var fraction by remember(selectedColor) { mutableFloatStateOf(closestGradientFraction(selectedColor)) }
    var barWidthPx by remember { mutableFloatStateOf(1f) }
    val gradientColors = remember { THEME_GRADIENT_STOPS.map { it.second } }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newF = (offset.x / barWidthPx).coerceIn(0f, 1f)
                    fraction = newF
                    onSelectColor(colorToHex(sampleThemeGradient(newF)))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val newF = (change.position.x / barWidthPx).coerceIn(0f, 1f)
                    fraction = newF
                    onSelectColor(colorToHex(sampleThemeGradient(newF)))
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackHeight = 24.dp.toPx()
            val trackTop = (size.height - trackHeight) / 2f
            val corner = CornerRadius(trackHeight / 2f, trackHeight / 2f)

            drawRoundRect(
                brush = Brush.horizontalGradient(gradientColors),
                topLeft = Offset(0f, trackTop),
                size = androidx.compose.ui.geometry.Size(size.width, trackHeight),
                cornerRadius = corner
            )
            drawRoundRect(
                color = AppColors.BorderSand,
                topLeft = Offset(0f, trackTop),
                size = androidx.compose.ui.geometry.Size(size.width, trackHeight),
                cornerRadius = corner,
                style = Stroke(width = 1.dp.toPx())
            )

            val thumbRadius = 13.dp.toPx()
            val thumbX = (fraction * size.width).coerceIn(thumbRadius, (size.width - thumbRadius).coerceAtLeast(thumbRadius))
            val thumbCenter = Offset(thumbX, size.height / 2f)

            drawCircle(
                color = sampleThemeGradient(fraction),
                radius = thumbRadius,
                center = thumbCenter
            )
            drawCircle(
                color = AppColors.SurfaceAlabaster,
                radius = thumbRadius,
                center = thumbCenter,
                style = Stroke(width = 3.dp.toPx())
            )
            drawCircle(
                color = AppColors.DeepPineText,
                radius = thumbRadius + 1.dp.toPx(),
                center = thumbCenter,
                style = Stroke(width = 1.2.dp.toPx())
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
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) accentColor.copy(alpha = 0.16f) else AppColors.WarmSandSoft)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) accentColor else AppColors.BorderSand,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelectIcon(iconName) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = vector,
                    contentDescription = label,
                    tint = if (isSelected) accentColor else AppColors.WarmTaupeText,
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
                .clip(RoundedCornerShape(22.dp)),
            color = AppColors.SurfaceAlabaster,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                SheetGrabber()
                Text(
                    text = title,
                    fontFamily = FontFamily.Serif,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.DeepPineText
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    fontSize = 13.5.sp,
                    color = AppColors.WarmTaupeText,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.WarmTaupeText),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Hủy")
                    }
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.TerracottaAlert),
                        shape = RoundedCornerShape(14.dp),
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
                .clip(RoundedCornerShape(24.dp)),
            color = AppColors.SurfaceAlabaster,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
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
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
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
                            fontFamily = FontFamily.Serif,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.ForestTeal
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng", tint = AppColors.WarmTaupeText, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
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
                    color = AppColors.DeepPineText,
                    lineHeight = 20.sp
                )

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
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AppColors.WarmSand)
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
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.WarmTaupeText),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Bỏ qua")
                    }
                    Button(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(14.dp),
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
                .clip(RoundedCornerShape(24.dp)),
            color = AppColors.SurfaceAlabaster,
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
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.WarmSandSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = AppColors.WarmTaupeText,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Không tìm thấy ảnh phù hợp",
                    fontFamily = FontFamily.Serif,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.DeepPineText,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "AI đã quét thư viện ảnh nhưng chưa tìm thấy ảnh nào có nội dung phù hợp với danh mục \"${category.name}\".\n\nCác ảnh chụp mới trong tương lai sẽ tự động được phân loại vào đây.",
                    fontSize = 13.5.sp,
                    color = AppColors.WarmTaupeText,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
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
                .clip(RoundedCornerShape(24.dp)),
            color = AppColors.SurfaceAlabaster,
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = catColor,
                        strokeWidth = 3.5.dp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Đang quét ảnh cho danh mục...",
                    fontFamily = FontFamily.Serif,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.DeepPineText,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "AI đang đối soát toàn bộ thư viện ảnh để nhận diện các ảnh phù hợp với danh mục \"${category.name}\".\nVui lòng đợi trong giây lát...",
                    fontSize = 13.5.sp,
                    color = AppColors.WarmTaupeText,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
