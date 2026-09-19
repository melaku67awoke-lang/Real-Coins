package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Real-Coins brand palette: black + gold.
private val Gold = Color(0xFFFFC107)
private val GoldLight = Color(0xFFFFD54F)
private val GoldDark = Color(0xFFD89E00)
private val Black = Color(0xFF080808)
private val Surface = Color(0xFF141414)
private val SurfaceVariant = Color(0xFF211B08)
private val TextPrimary = Color(0xFFF7F7F7)
private val TextSecondary = Color(0xFFB9B9B9)
private val Outline = Color(0xFF5A4A18)

private val DarkColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Black,

    primaryContainer = Color(0xFF3A2D05),
    onPrimaryContainer = GoldLight,

    secondary = GoldLight,
    onSecondary = Black,

    secondaryContainer = Color(0xFF302604),
    onSecondaryContainer = GoldLight,

    tertiary = GoldDark,
    onTertiary = Black,

    background = Black,
    onBackground = TextPrimary,

    surface = Surface,
    onSurface = TextPrimary,

    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,

    outline = Outline
)

private val LightColorScheme = lightColorScheme(
    primary = GoldDark,
    onPrimary = Black,

    primaryContainer = Color(0xFFFFE8A3),
    onPrimaryContainer = Color(0xFF2A2100),

    secondary = GoldDark,
    onSecondary = Black,

    secondaryContainer = Color(0xFFFFEDB8),
    onSecondaryContainer = Color(0xFF2A2100),

    tertiary = GoldDark,
    onTertiary = Black,

    background = Color(0xFFFAFAF8),
    onBackground = Color(0xFF171717),

    surface = Color.White,
    onSurface = Color(0xFF171717),

    surfaceVariant = Color(0xFFF1E9CC),
    onSurfaceVariant = Color(0xFF5A523D),

    outline = Color(0xFF9A8A52)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) {
            DarkColorScheme
        } else {
            LightColorScheme
        },
        typography = Typography,
        content = content
    )
}
