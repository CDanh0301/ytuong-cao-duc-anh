package com.example.test2.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test2.ui.AppTab
import com.example.test2.ui.theme.AppColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private data class LiquidNavItem(
    val tab: AppTab,
    val label: String,
    val icon: ImageVector
)

private val LIQUID_NAV_ITEMS = listOf(
    LiquidNavItem(AppTab.PHOTOS, "Ảnh", Icons.Outlined.Photo),
    LiquidNavItem(AppTab.CATEGORIES, "Danh mục", Icons.AutoMirrored.Outlined.Label),
    LiquidNavItem(AppTab.CLEANER, "Sắp xếp", Icons.Outlined.AutoAwesome),
    LiquidNavItem(AppTab.SETTINGS, "Cài đặt", Icons.Outlined.Settings)
)

private const val TRANSITION_DURATION_MS = 450

/**
 * Custom bottom navigation bar implementing a concave Bezier wave notch
 * paired with a Forest Teal 3D-lit orb that arcs upward when switching tabs (0.45s),
 * with compact tab labels anchored beneath each slot.
 */
@Composable
fun LiquidWaveBottomBar(
    activeTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val targetIndex = LIQUID_NAV_ITEMS.indexOfFirst { it.tab == activeTab }.coerceAtLeast(0)
    var fromIndex by remember { mutableIntStateOf(targetIndex) }
    var toIndex by remember { mutableIntStateOf(targetIndex) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(targetIndex) {
        if (targetIndex != toIndex) {
            // If interrupted mid-flight, start smoothly from the currently nearest tab
            fromIndex = if (progress.value < 0.5f) fromIndex else toIndex
            toIndex = targetIndex
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = TRANSITION_DURATION_MS,
                    easing = CubicBezierEasing(0.42f, 0.0f, 0.20f, 1.0f)
                )
            )
        }
    }

    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barBodyHeight = 68.dp
    val totalHeight = barBodyHeight + navBottomPadding
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight)
    ) {
        val totalWidthPx = with(density) { maxWidth.toPx() }
        val totalHeightPx = with(density) { totalHeight.toPx() }
        val dipDepthPx = with(density) { 28.dp.toPx() }
        val dipHalfWidthPx = with(density) { 42.dp.toPx() }
        val orbSizeDp = 44.dp
        val orbSizePx = with(density) { orbSizeDp.toPx() }
        val orbRestCenterYPx = with(density) { 4.dp.toPx() }
        val tabStepPx = totalWidthPx / LIQUID_NAV_ITEMS.size

        fun centerXFor(index: Int): Float = (index + 0.5f) * tabStepPx

        val t = progress.value.coerceIn(0f, 1f)
        val startX = centerXFor(fromIndex)
        val endX = centerXFor(toIndex)
        val orbX = startX + (endX - startX) * t

        // Slight elastic overshoot on the concave wave catch
        val settleT = if (t >= 1f) 1f else {
            val inv = t - 1f
            1f + 2.1f * inv * inv * inv + 1.1f * inv * inv
        }
        val notchX = startX + (endX - startX) * settleT.coerceIn(-0.08f, 1.08f)

        val arc = sin(PI * t).toFloat()
        val jumpHeightPx = with(density) {
            if (abs(toIndex - fromIndex) > 1) 28.dp.toPx() else 22.dp.toPx()
        }
        val orbCenterY = orbRestCenterYPx - jumpHeightPx * arc
        val dynamicDipDepth = dipDepthPx * (1f - 0.24f * arc)

        // Draw subtle top drop-shadow + alabaster wave surface
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path().apply {
                val leftEdge = (notchX - dipHalfWidthPx).coerceAtLeast(0f)
                val rightEdge = (notchX + dipHalfWidthPx).coerceAtMost(totalWidthPx)
                val cp1 = dipHalfWidthPx * 0.42f
                val cp2 = dipHalfWidthPx * 0.50f

                moveTo(0f, 0f)
                lineTo(leftEdge, 0f)
                cubicTo(
                    notchX - cp2, 0f,
                    notchX - cp1, dynamicDipDepth,
                    notchX, dynamicDipDepth
                )
                cubicTo(
                    notchX + cp1, dynamicDipDepth,
                    notchX + cp2, 0f,
                    rightEdge, 0f
                )
                lineTo(totalWidthPx, 0f)
                lineTo(totalWidthPx, totalHeightPx)
                lineTo(0f, totalHeightPx)
                close()
            }

            // Soft warm top border hairline shadow
            val shadowPath = Path().apply {
                val leftEdge = (notchX - dipHalfWidthPx).coerceAtLeast(0f)
                val rightEdge = (notchX + dipHalfWidthPx).coerceAtMost(totalWidthPx)
                val cp1 = dipHalfWidthPx * 0.42f
                val cp2 = dipHalfWidthPx * 0.50f
                moveTo(0f, -1.2f)
                lineTo(leftEdge, -1.2f)
                cubicTo(
                    notchX - cp2, -1.2f,
                    notchX - cp1, dynamicDipDepth - 1.2f,
                    notchX, dynamicDipDepth - 1.2f
                )
                cubicTo(
                    notchX + cp1, dynamicDipDepth - 1.2f,
                    notchX + cp2, -1.2f,
                    rightEdge, -1.2f
                )
                lineTo(totalWidthPx, -1.2f)
                lineTo(totalWidthPx, 4f)
                lineTo(0f, 4f)
                close()
            }
            drawPath(
                path = shadowPath,
                color = AppColors.BorderSand.copy(alpha = 0.75f),
                style = Fill
            )

            drawPath(
                path = path,
                color = AppColors.SurfaceAlabaster,
                style = Fill
            )
        }

        // Tab icon & small label slots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barBodyHeight)
                .padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            LIQUID_NAV_ITEMS.forEachIndexed { index, item ->
                val isSelected = item.tab == activeTab
                val dist = abs(orbX - centerXFor(index))
                val fadeStart = with(density) { 10.dp.toPx() }
                val fadeRange = with(density) { 26.dp.toPx() }
                val restAlpha = ((dist - fadeStart) / fadeRange).coerceIn(0f, 1f)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelectTab(item.tab) }
                        .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .alpha(restAlpha)
                            .graphicsLayer {
                                val s = 0.78f + 0.22f * restAlpha
                                scaleX = s
                                scaleY = s
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = AppColors.DeepPineText,
                            modifier = Modifier.size(21.dp)
                        )
                    }

                    Text(
                        text = item.label,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) AppColors.ForestTeal else AppColors.WarmTaupeText,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        // Forest Teal 3D-lit Jumping Orb
        val activeOrbItem = LIQUID_NAV_ITEMS[if (t < 0.44f) fromIndex else toIndex]
        val orbScale = 1f + 0.08f * arc
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (orbX - orbSizePx / 2f).roundToInt(),
                        y = (orbCenterY - orbSizePx / 2f).roundToInt()
                    )
                }
                .size(orbSizeDp)
                .graphicsLayer {
                    scaleX = orbScale
                    scaleY = orbScale
                }
                .shadow(
                    elevation = (8f + 6f * arc).dp,
                    shape = CircleShape,
                    ambientColor = AppColors.ForestTealDark,
                    spotColor = AppColors.ForestTealDark
                )
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(AppColors.ForestTealLight, AppColors.ForestTealDark)
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = activeOrbItem.icon,
                contentDescription = null,
                tint = AppColors.SurfaceAlabaster,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}
