package com.example.test2.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.net.Uri
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.test2.shared.models.ActionCard
import com.example.test2.shared.models.ActionCardType
import com.example.test2.shared.models.Category
import com.example.test2.shared.models.Folder
import com.example.test2.shared.models.Photo
import com.example.test2.ui.ActionCardIntentHelper
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(Locale.getDefault(), "%.1f MB", mb)
    } else {
        String.format(Locale.getDefault(), "%.1f KB", kb)
    }
}

fun formatDate(millis: Long): String {
    if (millis <= 0) return "Không rõ ngày"
    val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    return formatter.format(Date(millis))
}

@Composable
fun ZoomableImage(
    photo: Photo,
    onToggleControls: () -> Unit,
    onZoomStateChanged: (Boolean) -> Unit,
    onDismissDrag: (Float) -> Unit,
    onDismissDragEnd: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(photo.id) {
        scale = 1f
        offset = Offset.Zero
        onZoomStateChanged(false)
    }

    val imageSource = remember(photo.filePath) {
        if (photo.filePath.startsWith("content://")) {
            android.net.Uri.parse(photo.filePath)
        } else {
            File(photo.filePath)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(photo.id) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                            onZoomStateChanged(false)
                        } else {
                            scale = 2.5f
                            onZoomStateChanged(true)
                        }
                    },
                    onTap = {
                        onToggleControls()
                    }
                )
            }
            .pointerInput(photo.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var isDismissDragging = false
                    var totalDrag = Offset.Zero

                    do {
                        val event = awaitPointerEvent()
                        val activePointers = event.changes.filter { it.pressed }
                        val count = activePointers.size

                        if (count >= 2) {
                            // Two fingers: Pinch to zoom
                            val p1 = activePointers[0]
                            val p2 = activePointers[1]
                            val prevDist = (p1.previousPosition - p2.previousPosition).getDistance()
                            val currDist = (p1.position - p2.position).getDistance()

                            if (prevDist > 0f) {
                                val zoomFactor = currDist / prevDist
                                val newScale = (scale * zoomFactor).coerceIn(1f, 5f)
                                scale = newScale
                                onZoomStateChanged(newScale > 1.05f)
                            }

                            val panDelta = (p1.position - p1.previousPosition + p2.position - p2.previousPosition) / 2f
                            if (scale > 1.05f) {
                                val maxOffsetX = (size.width * (scale - 1f)) / 2f
                                val maxOffsetY = (size.height * (scale - 1f)) / 2f
                                offset = Offset(
                                    x = (offset.x + panDelta.x).coerceIn(-maxOffsetX, maxOffsetX),
                                    y = (offset.y + panDelta.y).coerceIn(-maxOffsetY, maxOffsetY)
                                )
                            } else {
                                offset = Offset.Zero
                            }
                            activePointers.forEach { it.consume() }
                        } else if (count == 1) {
                            val change = activePointers[0]
                            val dragAmount = change.position - change.previousPosition

                            if (scale > 1.05f) {
                                // Zoomed in: pan image freely in any direction
                                val maxOffsetX = (size.width * (scale - 1f)) / 2f
                                val maxOffsetY = (size.height * (scale - 1f)) / 2f
                                offset = Offset(
                                    x = (offset.x + dragAmount.x).coerceIn(-maxOffsetX, maxOffsetX),
                                    y = (offset.y + dragAmount.y).coerceIn(-maxOffsetY, maxOffsetY)
                                )
                                change.consume()
                            } else {
                                // 1.0x scale (normal view): ensure offset is strictly centered
                                offset = Offset.Zero
                                totalDrag += dragAmount

                                if (isDismissDragging) {
                                    onDismissDrag(dragAmount.y)
                                    change.consume()
                                } else {
                                    val touchSlop = viewConfiguration.touchSlop
                                    if (totalDrag.getDistance() > touchSlop) {
                                        // If dragging downwards and predominantly vertical:
                                        if (totalDrag.y > 0 && abs(totalDrag.y) > abs(totalDrag.x) * 1.25f) {
                                            isDismissDragging = true
                                            onDismissDrag(totalDrag.y)
                                            change.consume()
                                        } else {
                                            // Predominantly horizontal: DO NOT CONSUME!
                                            // HorizontalPager will receive the swipe to change photos!
                                        }
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (isDismissDragging) {
                        onDismissDragEnd()
                    } else {
                        // Gesture ended: If scale is near 1x or zoomed out, snap back to exact 1f and centered!
                        if (scale <= 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                            onZoomStateChanged(false)
                        } else {
                            // Ensure offset is strictly clamped within bounds
                            val maxOffsetX = (size.width * (scale - 1f)) / 2f
                            val maxOffsetY = (size.height * (scale - 1f)) / 2f
                            offset = Offset(
                                x = offset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                y = offset.y.coerceIn(-maxOffsetY, maxOffsetY)
                            )
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageSource)
                .crossfade(true)
                .build(),
            contentDescription = photo.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

/**
 * Swipeable Full-Screen Image Viewer:
 * Uses HorizontalPager to allow swiping across all photos in the current view/location.
 * Supports swipe down to dismiss and pinch-to-zoom.
 */
@Composable
fun ImageViewerDialog(
    photos: List<Photo>,
    initialPhotoId: String,
    folders: List<Folder>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onOpenSettings: (Photo) -> Unit,
    onToggleFavorite: (Photo) -> Unit,
    onAddToCalendar: (Photo) -> Unit,
    onGetActionCards: ((String) -> List<ActionCard>)? = null
) {
    if (photos.isEmpty()) return

    val initialIndex = remember(initialPhotoId, photos) {
        val found = photos.indexOfFirst { it.id == initialPhotoId }
        if (found >= 0) found else 0
    }

    val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size }
    var showControls by remember { mutableStateOf(true) }
    var isCurrentPhotoZoomed by remember { mutableStateOf(false) }
    var isActionDrawerExpanded by remember { mutableStateOf(false) }

    val dismissOffsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dismissProgress = (dismissOffsetY.value / 350f).coerceIn(0f, 1f)
    val backgroundAlpha = (1f - dismissProgress * 0.75f).coerceIn(0f, 1f)

    LaunchedEffect(pagerState.currentPage) {
        isCurrentPhotoZoomed = false
        dismissOffsetY.snapTo(0f)
        isActionDrawerExpanded = false
    }

    val currentPhoto = photos.getOrNull(pagerState.currentPage) ?: photos.first()
    val currentFolder = folders.find { it.id == currentPhoto.folderId }
    val photoCategories = categories.filter { it.id in currentPhoto.categoryIds }

    val actionCards = remember(currentPhoto.id) {
        onGetActionCards?.invoke(currentPhoto.id) ?: emptyList()
    }
    var qrPreviewCard by remember { mutableStateOf<ActionCard?>(null) }

    if (qrPreviewCard != null) {
        QrPreviewDialog(
            photo = currentPhoto,
            card = qrPreviewCard!!,
            onDismiss = { qrPreviewCard = null }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF020617).copy(alpha = backgroundAlpha))
        ) {
            // Horizontal Pager for swiping between photos (swipe disabled when zoomed in or when dismissing)
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !isCurrentPhotoZoomed && dismissOffsetY.value == 0f && !isActionDrawerExpanded,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = dismissOffsetY.value
                        val dismissScale = (1f - dismissProgress * 0.12f).coerceIn(0.85f, 1f)
                        scaleX = dismissScale
                        scaleY = dismissScale
                    }
            ) { page ->
                val photo = photos[page]
                ZoomableImage(
                    photo = photo,
                    onToggleControls = { showControls = !showControls },
                    onZoomStateChanged = { isZoomed ->
                        if (page == pagerState.currentPage) {
                            isCurrentPhotoZoomed = isZoomed
                        }
                    },
                    onDismissDrag = { deltaY ->
                        scope.launch {
                            dismissOffsetY.snapTo((dismissOffsetY.value + deltaY).coerceAtLeast(0f))
                        }
                    },
                    onDismissDragEnd = {
                        if (dismissOffsetY.value > 150f) {
                            scope.launch {
                                dismissOffsetY.animateTo(1000f, tween(160))
                                onDismiss()
                            }
                        } else {
                            scope.launch {
                                dismissOffsetY.animateTo(0f, spring())
                            }
                        }
                    }
                )
            }

            // Top overlay bar with Back, Title/Count, Favorite & Settings
            AnimatedVisibility(
                visible = showControls && dismissOffsetY.value < 50f,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 16.dp, vertical = 36.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Title & position indicator
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = currentPhoto.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${pagerState.currentPage + 1} / ${photos.size}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF60A5FA)
                            )
                            Text(
                                text = "• ${formatDate(currentPhoto.dateAdded)}",
                                fontSize = 11.5.sp,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }

                    // Right action buttons: Favorite & Settings
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Favorite toggle button
                        IconButton(
                            onClick = { onToggleFavorite(currentPhoto) },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (currentPhoto.isFavorite) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = if (currentPhoto.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Yêu thích",
                                tint = if (currentPhoto.isFavorite) Color(0xFFEF4444) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Settings Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF2563EB))
                                .clickable { onOpenSettings(currentPhoto) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Cài đặt",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Cài đặt",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Dimmed Scrim when Action Drawer is expanded (tapping outside collapses drawer)
            if (isActionDrawerExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.50f))
                        .clickable { isActionDrawerExpanded = false }
                )
            }

            // Bottom overlay bar with Smart Action Cards Peek Pill & clean folder info (Zero Clutter)
            AnimatedVisibility(
                visible = showControls && dismissOffsetY.value < 50f && !isActionDrawerExpanded,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.70f))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    // Action Cards Peek Pill (Tiny 42dp strip - Tap or Swipe Up to expand)
                    if (actionCards.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF1E293B).copy(alpha = 0.92f))
                                .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.40f), RoundedCornerShape(14.dp))
                                .clickable { isActionDrawerExpanded = true }
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures { _, dragAmount ->
                                        if (dragAmount < -10f) {
                                            isActionDrawerExpanded = true
                                        }
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    val summaryText = if (actionCards.size == 1) {
                                        actionCards.first().title
                                    } else {
                                        "${actionCards.size} thông tin hành động • Chạm hoặc vuốt lên"
                                    }
                                    Text(
                                        text = summaryText,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Mở rộng",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Folder and Categories badges
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = Color(0xFF60A5FA),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = currentFolder?.name ?: "Cuộn camera",
                                    fontSize = 12.sp,
                                    color = Color(0xFFE2E8F0)
                                )
                            }

                            photoCategories.take(2).forEach { cat ->
                                val catColor = parseHexColor(cat.colorHex)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(catColor.copy(alpha = 0.3f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = cat.name,
                                        fontSize = 11.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // Fallback Calendar Action Button if no Event Action Card
                        if (actionCards.none { it.type == ActionCardType.EVENT }) {
                            val isShopping = currentPhoto.detectedIntent == "SHOPPING" || currentPhoto.categoryIds.contains("cat_tobuy")
                            val isEvent = currentPhoto.detectedIntent == "EVENT" || currentPhoto.categoryIds.contains("cat_events") || currentPhoto.suggestedEventDateMillis != null
                            val hasScheduleOrShopping = isShopping || isEvent
                            if (hasScheduleOrShopping) {
                                val buttonText = if (currentPhoto.hasReminderCreated) {
                                    "Đã lên lịch"
                                } else if (isShopping) {
                                    "Đặt lịch mua"
                                } else {
                                    "Thêm vào lịch"
                                }
                                val buttonBg = if (isShopping) Color(0xFFF59E0B) else Color(0xFF10B981)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(buttonBg)
                                        .clickable { onAddToCalendar(currentPhoto) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CalendarMonth,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = buttonText,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Expanded Action Cards Drawer (Zero Clutter - slides up when requested)
            AnimatedVisibility(
                visible = isActionDrawerExpanded && actionCards.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.98f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { _, dragAmount ->
                                if (dragAmount > 20f) {
                                    isActionDrawerExpanded = false
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    // Drag handle
                    Box(
                        modifier = Modifier
                            .size(36.dp, 4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.35f))
                            .align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Header row: Title & Collapse Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = "Thông tin & Hành động 1 chạm (${actionCards.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        // Collapse Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .clickable { isActionDrawerExpanded = false }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Thu gọn",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Thu gọn",
                                    fontSize = 11.5.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Scrollable Action Cards List
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        actionCards.forEach { card ->
                            SmartActionCardBar(
                                card = card,
                                onOpenQr = { qrPreviewCard = card }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Photo Settings Sheet:
 * Opened only after the user selects "Settings" inside the Image Viewer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoSettingsDialog(
    photo: Photo,
    folders: List<Folder>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, folderId: String, categoryIds: Set<String>, isFavorite: Boolean) -> Unit,
    onDelete: (Photo) -> Unit,
    onScanPhoto: ((String) -> Unit)? = null
) {
    var title by remember { mutableStateOf(TextFieldValue(photo.title, selection = TextRange(photo.title.length))) }
    var description by remember { mutableStateOf(photo.description) }
    var selectedFolderId by remember { mutableStateOf(photo.folderId) }
    var selectedCategoryIds by remember { mutableStateOf(photo.categoryIds) }
    var isFavorite by remember { mutableStateOf(photo.isFavorite) }
    var folderMenuExpanded by remember { mutableStateOf(false) }

    val currentFolder = folders.find { it.id == selectedFolderId }
    val currentFolderName = currentFolder?.name ?: "Camera Roll"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 700.dp)
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

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cài đặt ảnh",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title
                Text("Tiêu đề ảnh", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        hintLocales = LocaleList(ComposeLocale("vi"), ComposeLocale("en"))
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Folder Location
                Text("Thư mục lưu trữ", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(4.dp))
                Box {
                    OutlinedButton(
                        onClick = { folderMenuExpanded = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF2563EB))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(currentFolderName, color = MaterialTheme.colorScheme.onSurface)
                    }

                    DropdownMenu(
                        expanded = folderMenuExpanded,
                        onDismissRequest = { folderMenuExpanded = false }
                    ) {
                        folders.forEach { folder ->
                            DropdownMenuItem(
                                text = { Text(folder.name) },
                                onClick = {
                                    selectedFolderId = folder.id
                                    folderMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Categories Selector
                Text("Gán danh mục & Thẻ", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { category ->
                        val isSelected = category.id in selectedCategoryIds
                        val catColor = parseHexColor(category.colorHex)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) catColor.copy(alpha = 0.18f) else Color(0xFFF1F5F9))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) catColor else Color(0xFFE2E8F0),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    selectedCategoryIds = if (isSelected) {
                                        selectedCategoryIds - category.id
                                    } else {
                                        selectedCategoryIds + category.id
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(catColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = category.name,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) catColor else Color(0xFF334155)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Metadata note
                Text(
                    text = "Tập tin: ${photo.fileName} (${formatFileSize(photo.fileSizeBytes)})",
                    fontSize = 11.5.sp,
                    color = Color(0xFF64748B)
                )
                Text(
                    text = "Ngày tạo: ${formatDate(photo.dateAdded)}",
                    fontSize = 11.5.sp,
                    color = Color(0xFF64748B)
                )

                // Minimal action button to re-scan if needed (Zero Clutter)
                if (onScanPhoto != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onScanPhoto.invoke(photo.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF3B82F6)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (!photo.isProcessed) "Quét & trích xuất thông tin" else "Quét lại ảnh",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF3B82F6)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons: Favorite & Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { isFavorite = !isFavorite },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Yêu thích",
                            tint = if (isFavorite) Color(0xFFEF4444) else Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isFavorite) "Đã thích" else "Yêu thích",
                            fontSize = 13.sp,
                            color = if (isFavorite) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Button(
                        onClick = { onDelete(photo) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE2E2)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Xóa",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Xóa", fontSize = 13.sp, color = Color(0xFFEF4444))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Save Button
                Button(
                    onClick = {
                        onSave(
                            title.text.trim(),
                            description.trim(),
                            selectedFolderId,
                            selectedCategoryIds,
                            isFavorite
                        )
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Lưu thay đổi", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Smart Action Card Bar (Clean UI, 1-Tap Actions)
 */
@Composable
fun SmartActionCardBar(
    card: ActionCard,
    onOpenQr: () -> Unit
) {
    val context = LocalContext.current
    val payload = remember(card.payloadJson) {
        try {
            JSONObject(card.payloadJson)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Card Title & Subtitle
        Text(
            text = card.title,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val subtitleText = card.subtitle
        if (!subtitleText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitleText,
                fontSize = 12.sp,
                color = Color(0xFFCBD5E1),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 1-Tap Action Pill Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (card.type) {
                ActionCardType.ADDRESS -> {
                    val fullAddress = payload.optString("fullAddress", subtitleText ?: "")
                    OneTapActionButton(
                        label = "Chỉ đường Maps",
                        icon = Icons.Default.Directions,
                        backgroundColor = Color(0xFF2563EB),
                        onClick = { ActionCardIntentHelper.openGoogleMaps(context, fullAddress) }
                    )
                    OneTapActionButton(
                        label = "Sao chép",
                        icon = Icons.Default.ContentCopy,
                        backgroundColor = Color.White.copy(alpha = 0.22f),
                        onClick = { ActionCardIntentHelper.copyToClipboard(context, "Địa chỉ", fullAddress) }
                    )
                }

                ActionCardType.PAYMENT -> {
                    val acc = payload.optString("accountNumber", "")
                    val bank = payload.optString("bankName", "")
                    val content = payload.optString("transferContent", "")
                    val rawQr = payload.optString("rawQrString", "")

                    if (acc.isNotBlank()) {
                        OneTapActionButton(
                            label = "Chép STK",
                            icon = Icons.Default.ContentCopy,
                            backgroundColor = Color(0xFF0284C7),
                            onClick = { ActionCardIntentHelper.copyToClipboard(context, "STK", acc, "Đã sao chép STK: $acc ($bank)") }
                        )
                    }
                    if (content.isNotBlank()) {
                        OneTapActionButton(
                            label = "Chép nội dung",
                            icon = Icons.Default.Description,
                            backgroundColor = Color.White.copy(alpha = 0.22f),
                            onClick = { ActionCardIntentHelper.copyToClipboard(context, "Nội dung CK", content) }
                        )
                    }
                    if (rawQr.isNotBlank()) {
                        OneTapActionButton(
                            label = "Xem mã QR",
                            icon = Icons.Default.QrCode,
                            backgroundColor = Color(0xFF059669),
                            onClick = onOpenQr
                        )
                    }
                }

                ActionCardType.CONTACT -> {
                    val phone = payload.optString("phoneNumber", "")
                    val name = payload.optString("name", "")
                    val email = payload.optString("email", "")
                    val company = payload.optString("company", "")

                    if (phone.isNotBlank()) {
                        OneTapActionButton(
                            label = "Gọi điện",
                            icon = Icons.Default.Phone,
                            backgroundColor = Color(0xFF16A34A),
                            onClick = { ActionCardIntentHelper.dialPhoneNumber(context, phone) }
                        )
                        OneTapActionButton(
                            label = "Nhắn Zalo",
                            icon = Icons.AutoMirrored.Filled.Chat,
                            backgroundColor = Color(0xFF2563EB),
                            onClick = { ActionCardIntentHelper.openZalo(context, phone) }
                        )
                    }
                    OneTapActionButton(
                        label = "Lưu danh bạ",
                        icon = Icons.Default.PersonAdd,
                        backgroundColor = Color.White.copy(alpha = 0.22f),
                        onClick = { ActionCardIntentHelper.insertContact(context, name, phone, email, company) }
                    )
                }

                ActionCardType.EVENT -> {
                    val title = payload.optString("title", card.title.removePrefix("📅").trim())
                    val time = payload.optLong("startTimeMillis", System.currentTimeMillis())
                    val desc = payload.optString("description", "")
                    OneTapActionButton(
                        label = "Thêm vào lịch",
                        icon = Icons.Default.CalendarMonth,
                        backgroundColor = Color(0xFF10B981),
                        onClick = { ActionCardIntentHelper.addToCalendar(context, title, time, desc) }
                    )
                }

                else -> {}
            }
        }
    }
}

@Composable
fun OneTapActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(13.5.dp)
            )
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun QrPreviewDialog(
    photo: Photo,
    card: ActionCard,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val payload = remember(card.payloadJson) {
        try { JSONObject(card.payloadJson) } catch (_: Exception) { JSONObject() }
    }
    val bank = payload.optString("bankName", "Ngân hàng")
    val acc = payload.optString("accountNumber", "")
    val holder = payload.optString("accountHolder", "")
    val amount = payload.optDouble("amount", 0.0)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp)),
            color = Color.White,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mã QR Chuyển Khoản",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // The QR Image Preview
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF8FAFC))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(if (photo.filePath.startsWith("content://")) Uri.parse(photo.filePath) else File(photo.filePath))
                            .crossfade(true)
                            .build(),
                        contentDescription = "Mã QR",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "$bank • $acc",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0284C7)
                )
                if (holder.isNotBlank()) {
                    Text(
                        text = holder,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF334155)
                    )
                }
                if (amount > 0) {
                    Text(
                        text = "${java.text.DecimalFormat("#,###").format(amount)} đ",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        ActionCardIntentHelper.copyToClipboard(context, "STK", acc, "Đã sao chép STK: $acc ($bank)")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sao chép số tài khoản", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
