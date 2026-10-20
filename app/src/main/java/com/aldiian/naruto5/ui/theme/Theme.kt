package com.aldiian.naruto5.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NarutoOrange = Color(0xFFFF7A1A)
val ChakraBlue = Color(0xFF3FA9F5)
val OkGreen = Color(0xFF3DDC84)
val WarnAmber = Color(0xFFFFC145)
val ErrRed = Color(0xFFFF5A5A)

private val Dark = darkColorScheme(
    primary = NarutoOrange,
    onPrimary = Color(0xFF1A0E00),
    secondary = ChakraBlue,
    background = Color(0xFF0B0F1A),
    surface = Color(0xFF121827),
    surfaceVariant = Color(0xFF1B2335),
    surfaceContainer = Color(0xFF161D2E),
    onSurface = Color(0xFFE8ECF4),
    onSurfaceVariant = Color(0xFFAAB4C8),
    outline = Color(0xFF3A4660),
)

private val Light = lightColorScheme(
    primary = Color(0xFFD95F00),
    secondary = Color(0xFF1C74C4),
    background = Color(0xFFF7F8FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFECEFF5),
)

@Composable
fun NarutoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
