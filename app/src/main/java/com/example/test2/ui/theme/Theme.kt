package com.example.test2.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * High-contrast, clean light color scheme ensuring all text and UI elements
 * are clearly legible and unaffected by system dark mode.
 */
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEFF6FF),
    onPrimaryContainer = Color(0xFF1E40AF),
    secondary = PurpleGrey40,
    onSecondary = Color.White,
    tertiary = Pink40,
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0)
)

@Composable
fun Test2Theme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // App interface strictly stays in Light theme so it is never affected
    // by device-level Dark Mode, keeping all text, labels, and backgrounds clearly legible.
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
