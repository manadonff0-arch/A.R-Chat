package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private fun darkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color(0xFF042119),
    primaryContainer = ArEmeraldDeep,
    onPrimaryContainer = ArEmeraldBright,
    secondary = ArEmeraldBright,
    onSecondary = Color(0xFF042119),
    secondaryContainer = ArSurfaceElevatedDark,
    onSecondaryContainer = ArTextPrimaryDark,
    background = ArBackgroundDark,
    onBackground = ArTextPrimaryDark,
    surface = ArSurfaceDark,
    onSurface = ArTextPrimaryDark,
    surfaceVariant = ArSurfaceElevatedDark,
    onSurfaceVariant = ArTextSecondaryDark,
    outline = ArDividerDark,
    error = ArDangerRed,
    onError = Color.White
)

private fun lightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9FDD3),
    onPrimaryContainer = ArEmeraldDeep,
    secondary = ArEmeraldPrimaryLight,
    onSecondary = Color.White,
    secondaryContainer = ArSurfaceElevatedLight,
    onSecondaryContainer = ArTextPrimaryLight,
    background = ArBackgroundLight,
    onBackground = ArTextPrimaryLight,
    surface = ArSurfaceLight,
    onSurface = ArTextPrimaryLight,
    surfaceVariant = ArSurfaceElevatedLight,
    onSurfaceVariant = ArTextSecondaryLight,
    outline = Color(0xFFD1D7DB),
    error = ArDangerRed,
    onError = Color.White
)

@Composable
fun ARChatTheme(
    themeMode: String = "dark",
    accentColor: Color = ArEmeraldPrimary,
    fontScale: Float = 1.0f,
    highContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true
    }

    val baseScheme = if (useDark) darkScheme(accentColor) else lightScheme(accentColor)
    val finalScheme = if (highContrast && useDark) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color(0xFF090D10),
            onSurface = Color.White,
            onSurfaceVariant = Color(0xFFC8D3D8)
        )
    } else if (highContrast) {
        baseScheme.copy(
            background = Color.White,
            surface = Color.White,
            onSurface = Color.Black,
            onSurfaceVariant = Color(0xFF222E35)
        )
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = finalScheme,
        typography = buildArTypography(fontScale),
        content = content
    )
}
