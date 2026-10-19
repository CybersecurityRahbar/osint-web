package com.cyberphantom.osintworkbench.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal object WorkbenchColors {
    val Background = Color(0xFF080F1D)
    val Surface = Color(0xFF101B2D)
    val SurfaceElevated = Color(0xFF17253A)
    val Border = Color(0xFF263951)
    val TextPrimary = Color(0xFFF2F6FC)
    val TextSecondary = Color(0xFFA3B2C7)
    val Teal = Color(0xFF49E0C0)
    val Blue = Color(0xFF69A9FF)
    val Violet = Color(0xFFA88BFF)
    val Amber = Color(0xFFF7C66D)
    val Red = Color(0xFFFF7D91)
}

private val DarkPalette = darkColorScheme(
    primary = WorkbenchColors.Teal,
    onPrimary = Color(0xFF04231F),
    secondary = WorkbenchColors.Violet,
    onSecondary = Color(0xFF180F2F),
    tertiary = WorkbenchColors.Blue,
    background = WorkbenchColors.Background,
    onBackground = WorkbenchColors.TextPrimary,
    surface = WorkbenchColors.Surface,
    onSurface = WorkbenchColors.TextPrimary,
    surfaceVariant = WorkbenchColors.SurfaceElevated,
    onSurfaceVariant = WorkbenchColors.TextSecondary,
    outline = WorkbenchColors.Border,
    error = WorkbenchColors.Red
)

@Composable
fun WorkbenchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkPalette,
        content = content
    )
}
