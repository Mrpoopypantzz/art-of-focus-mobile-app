package com.artoffocus.app.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Ported 1:1 from the web app's Renaissance-gallery palette (src/styles/tokens.css). */
object AppColors {
    val Bg = Color(0xFFDDC99A)
    val Surface = Color(0xFFFAF3E4)
    val SurfaceRaised = Color(0xFFFBF6EA)
    val SurfaceSunken = Color(0xFFDDCEAC)

    val Ink = Color(0xFF4A1F1A)
    val InkSoft = Color(0xFF6B2F26)
    val OnInk = Color(0xFFF6EAD2)

    val Line = Color(0xFFD9C9A3)
    val LineStrong = Color(0xFFC2AC7C)

    val Text = Color(0xFF4A1F1A)
    val TextDim = Color(0xFF6A5030)
    val TextFaint = Color(0xFF7D613F)

    val GildedStart = Color(0xFFD8B46A)
    val GildedEnd = Color(0xFFA8722F)
}

/**
 * Maps M3 slots so most existing Button/Card/Surface call sites pick up the palette with
 * zero per-call-site changes: primary -> ink fill / cream text, surfaceVariant -> the
 * "quiet" sunken surface, error -> ink-soft (reused for the danger/cancel-confirm button,
 * since this palette has no separate red).
 */
val AppLightColorScheme = lightColorScheme(
    primary = AppColors.Ink,
    onPrimary = AppColors.OnInk,
    secondary = AppColors.InkSoft,
    onSecondary = AppColors.OnInk,
    background = AppColors.Bg,
    onBackground = AppColors.Text,
    surface = AppColors.Surface,
    onSurface = AppColors.Text,
    surfaceVariant = AppColors.SurfaceSunken,
    onSurfaceVariant = AppColors.TextDim,
    outline = AppColors.Line,
    outlineVariant = AppColors.LineStrong,
    error = AppColors.InkSoft,
    onError = AppColors.OnInk,
)
