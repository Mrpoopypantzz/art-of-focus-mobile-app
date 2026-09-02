package com.artoffocus.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** Single light scheme only — the source design has no dark mode. */
@Composable
fun ArtOfFocusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppLightColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
