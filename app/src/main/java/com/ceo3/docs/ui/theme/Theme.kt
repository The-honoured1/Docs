package com.ceo3.docs.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary          = Color.Black,
    onPrimary        = Color.White,
    secondary        = BrightYellow,
    onSecondary      = Color.Black,
    tertiary         = SoftGreen,
    background       = BackgroundLight,
    onBackground     = Color.Black,
    surface          = SurfaceLight,
    onSurface        = Color.Black,
    surfaceVariant   = SoftYellow,
    onSurfaceVariant = Color.Black
)

private val DarkColorScheme = darkColorScheme(
    primary          = AccentPurpleDark,
    onPrimary        = Color.Black,
    secondary        = AccentYellowDark,
    onSecondary      = Color.Black,
    tertiary         = AccentGreenDark,
    background       = BackgroundDark,
    onBackground     = OnSurfaceDark,
    surface          = SurfaceDark,
    onSurface        = OnSurfaceDark,
    surfaceVariant   = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceDark
)

@Composable
fun DocsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}