package com.ceo3.docs.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    secondary = BrightYellow,
    onSecondary = Color.Black,
    tertiary = SoftGreen,
    background = BackgroundLight,
    onBackground = Color.Black,
    surface = SurfaceLight,
    onSurface = Color.Black,
    surfaceVariant = SoftYellow,
    onSurfaceVariant = Color.Black
)

@Composable
fun DocsTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography,
        content = content
    )
}