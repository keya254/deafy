package com.deafy.phone.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// High-contrast palette: text on surfaces is well above WCAG AA (4.5:1).
private val DeafyColors = lightColorScheme(
    primary = Color(0xFF00594F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEEE8),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF3F5F59),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE8E5),
    onSecondaryContainer = Color(0xFF0F201D),
    tertiaryContainer = Color(0xFFFFDDB8),
    onTertiaryContainer = Color(0xFF2B1700),
    background = Color(0xFFF6FAF9),
    onBackground = Color(0xFF121212),
    surface = Color.White,
    onSurface = Color(0xFF121212),
    surfaceVariant = Color(0xFFEEF4F2),
    onSurfaceVariant = Color(0xFF3A4745),
    outline = Color(0xFF6F7B79),
    error = Color(0xFFB3261E),
)

@Composable
fun DeafyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DeafyColors, content = content)
}
