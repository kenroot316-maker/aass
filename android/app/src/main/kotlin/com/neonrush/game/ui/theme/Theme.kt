package com.neonrush.game.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Neon Rush synthwave palette
val NeonBg = Color(0xFF0B0E1A)        // near-black blue backdrop
val NeonSurface = Color(0xFF131A2E)   // card / panel
val NeonCyan = Color(0xFF22E0FF)      // player + primary accent
val NeonMagenta = Color(0xFFFF2D95)   // obstacles + danger
val NeonLime = Color(0xFFB6FF3C)      // coins + success
val NeonDim = Color(0xFF3A4460)       // grid lines, inactive

private val DarkScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = NeonBg,
    secondary = NeonMagenta,
    onSecondary = NeonBg,
    tertiary = NeonLime,
    onTertiary = NeonBg,
    background = NeonBg,
    onBackground = Color.White,
    surface = NeonSurface,
    onSurface = Color.White,
    surfaceVariant = NeonDim,
    onSurfaceVariant = Color(0xFFB9C2D8)
)

@Composable
fun NeonRushTheme(content: @Composable () -> Unit) {
    // The game is always dark; isSystemInDarkTheme is intentionally ignored.
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
