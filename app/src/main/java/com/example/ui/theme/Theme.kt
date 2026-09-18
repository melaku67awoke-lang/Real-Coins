package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00B8D4),
    onPrimary = Color.White,
    secondary = Color(0xFF80DEEA),
    onSecondary = Color(0xFF06242A),
    tertiary = Color(0xFFFFFFFF),
    background = Color(0xFF071014),
    onBackground = Color(0xFFF5F7F8),
    surface = Color(0xFF0D171C),
    onSurface = Color(0xFFF5F7F8),
    surfaceVariant = Color(0xFF142329),
    onSurfaceVariant = Color(0xFFB8C5C9),
    outline = Color(0xFF3B4D53)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF008FA3),
    onPrimary = Color.White,
    secondary = Color(0xFF00ACC1),
    onSecondary = Color.White,
    tertiary = Color(0xFF263238),
    background = Color(0xFFF7FAFB),
    onBackground = Color(0xFF10181C),
    surface = Color.White,
    onSurface = Color(0xFF10181C),
    surfaceVariant = Color(0xFFE8F0F2),
    onSurfaceVariant = Color(0xFF4B5B60),
    outline = Color(0xFF9AAEB4)
)

@Composable
fun MyApplicationTheme(darkTheme: Boolean = true, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme, typography = Typography, content = content)
}
