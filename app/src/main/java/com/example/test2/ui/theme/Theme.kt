package com.example.test2.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Editorial Organic light color scheme ensuring high legibility and warm calm
 * tones unaffected by device-level Dark Mode.
 */
private val LightColorScheme = lightColorScheme(
    primary = AppColors.ForestTeal,
    onPrimary = AppColors.SurfaceAlabaster,
    primaryContainer = AppColors.ForestTealSoft,
    onPrimaryContainer = AppColors.ForestTealDark,
    secondary = AppColors.OchreGold,
    onSecondary = AppColors.SurfaceAlabaster,
    secondaryContainer = AppColors.OchreGoldLight,
    onSecondaryContainer = AppColors.OchreGoldDark,
    tertiary = AppColors.SageOlive,
    onTertiary = AppColors.SurfaceAlabaster,
    background = AppColors.CanvasCream,
    onBackground = AppColors.DeepPineText,
    surface = AppColors.SurfaceAlabaster,
    onSurface = AppColors.DeepPineText,
    surfaceVariant = AppColors.WarmSandSoft,
    onSurfaceVariant = AppColors.WarmTaupeText,
    outline = AppColors.BorderSand,
    outlineVariant = AppColors.WarmSand,
    error = AppColors.TerracottaAlert,
    onError = AppColors.SurfaceAlabaster
)

@Composable
fun Test2Theme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
