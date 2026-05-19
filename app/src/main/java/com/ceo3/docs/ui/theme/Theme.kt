package com.ceo3.docs.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary             = BrandAccent,
    onPrimary           = Color.White,
    primaryContainer    = Color(0xFFEDE9FE),
    onPrimaryContainer  = BrandHighlight,
    secondary           = AccentAmber,
    onSecondary         = Color.White,
    secondaryContainer  = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF92400E),
    tertiary            = AccentEmerald,
    onTertiary          = Color.White,
    tertiaryContainer   = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF065F46),
    background          = BackgroundLight,
    onBackground        = OnBackgroundLight,
    surface             = SurfaceLight,
    onSurface           = OnSurfaceLight,
    surfaceVariant      = SurfaceElevated,
    onSurfaceVariant    = OnSurfaceVariantL,
    error               = AccentRose,
    onError             = Color.White,
    outline             = DividerLight
)

private val DarkColorScheme = darkColorScheme(
    primary             = BrandAccent,
    onPrimary           = Color.White,
    primaryContainer    = Color(0xFF2D2A4E),
    onPrimaryContainer  = Color(0xFFC4B5FD),
    secondary           = AccentAmber,
    onSecondary         = Color(0xFF1A1A2E),
    secondaryContainer  = Color(0xFF2D2210),
    onSecondaryContainer = Color(0xFFFCD34D),
    tertiary            = AccentEmerald,
    onTertiary          = Color(0xFF0A1F16),
    tertiaryContainer   = Color(0xFF064E3B),
    onTertiaryContainer = Color(0xFF6EE7B7),
    background          = BackgroundDark,
    onBackground        = OnSurfaceDark,
    surface             = SurfaceDark,
    onSurface           = OnSurfaceDark,
    surfaceVariant      = SurfaceVariantDark,
    onSurfaceVariant    = OnSurfaceVariantDk,
    error               = Color(0xFFF87171),
    onError             = Color(0xFF2A0A0A),
    outline             = DividerDark
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