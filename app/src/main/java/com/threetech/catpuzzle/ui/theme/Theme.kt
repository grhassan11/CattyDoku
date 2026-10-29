package com.threetech.catpuzzle.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val CatLightColorScheme = lightColorScheme(
    primary = CatColors.Accent,
    onPrimary = CatColors.OnAccent,
    background = CatColors.Background,
    onBackground = CatColors.Ink,
    surface = CatColors.Surface,
    onSurface = CatColors.Ink,
    onSurfaceVariant = CatColors.InkMuted,
    error = CatColors.Conflict,
)

/** Light theme only (v1 decision). */
@Composable
fun CatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CatLightColorScheme,
        typography = CatTypography,
        shapes = CatShapes,
        content = content,
    )
}
