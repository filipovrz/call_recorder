package com.androkall.recorder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF1B7A5A)
private val GreenDark = Color(0xFF0B3D2E)
private val Cream = Color(0xFFF3FAF6)
private val Ink = Color(0xFF12241C)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    secondary = GreenDark,
    background = Cream,
    surface = Color.White,
    onBackground = Ink,
    onSurface = Ink
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7AD4B0),
    onPrimary = GreenDark,
    secondary = Color(0xFF9BE0C4),
    background = Color(0xFF0C1612),
    surface = Color(0xFF15231C),
    onBackground = Color(0xFFE8F5F0),
    onSurface = Color(0xFFE8F5F0)
)

@Composable
fun AndroCallRecorderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
