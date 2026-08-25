package com.offhand.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OffhandDarkColors = darkColorScheme(
    primary = Color(0xFF7FDCCB),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF1F4E44),
    onPrimaryContainer = Color(0xFFA2F2E0),
    background = Color(0xFF101214),
    onBackground = Color(0xFFE1E3E1),
    surface = Color(0xFF101214),
    onSurface = Color(0xFFE1E3E1),
    surfaceVariant = Color(0xFF1C2023),
    onSurfaceVariant = Color(0xFFBFC9C4),
    error = Color(0xFFFFB4AB),
)

@Composable
fun OffhandTheme(content: @Composable () -> Unit) {
    // Dark-only by design.
    MaterialTheme(
        colorScheme = OffhandDarkColors,
        content = content,
    )
}
