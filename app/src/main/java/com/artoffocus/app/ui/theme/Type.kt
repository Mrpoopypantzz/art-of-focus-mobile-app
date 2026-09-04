package com.artoffocus.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.artoffocus.app.R

/**
 * Both fonts are bundled as variable-axis TrueType files (OFL-licensed, from
 * github.com/google/fonts — no static per-weight cuts exist upstream anymore), so each
 * weight is a separate [Font] entry pinning the `wght` (and for Fraunces, `opsz`) axis via
 * [FontVariation.Settings]. Bundled rather than fetched via the Downloadable Fonts provider
 * so the app's offline-first invariant (I7) isn't compromised by a first-use network fetch.
 */
/**
 * Fraunces at a given optical size.
 *
 * OPTICAL SIZE MUST MATCH THE RENDERED SIZE. Fraunces' `opsz` axis (9–144) is not a subtle
 * one: the high end is a display cut with fine, high-contrast strokes meant for headlines,
 * and the low end is sturdier with more open counters for small text. The browser does this
 * automatically — `font-optical-sizing: auto` is the CSS default, and the web app loads
 * `Fraunces:opsz,wght@9..144` precisely so it can — but Compose resolves a family by weight
 * alone, so the axis has to be pinned per family here.
 *
 * This was previously pinned to 72 for every size, which rendered 18sp titles and 24sp
 * headings in hairline display strokes barely a pixel wide. That is what read as blurred or
 * washed-out text: not resolution, but the wrong cut of the typeface.
 */
@OptIn(ExperimentalTextApi::class)
private fun frauncesAt(opticalSize: Float): FontFamily = FontFamily(
    Font(
        R.font.fraunces_variable,
        FontWeight.W600,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(600),
            FontVariation.Setting("opsz", opticalSize),
        ),
    ),
    Font(
        R.font.fraunces_variable,
        FontWeight.W700,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(700),
            FontVariation.Setting("opsz", opticalSize),
        ),
    ),
)

/** 32sp — page titles and the stopwatch. */
val FrauncesDisplay: FontFamily = frauncesAt(32f)

/** 24sp — screen headings and sheet titles. */
val FrauncesHeading: FontFamily = frauncesAt(24f)

/** 18sp — step titles, stat values, month labels, level durations. */
val FrauncesTitle: FontFamily = frauncesAt(18f)

/** Kept for callers that predate the split; the 18sp cut is the safest general default. */
val FrauncesFamily: FontFamily = FrauncesTitle

@OptIn(ExperimentalTextApi::class)
val InterFamily: FontFamily = FontFamily(
    Font(R.font.inter_variable, FontWeight.W400, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter_variable, FontWeight.W500, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter_variable, FontWeight.W600, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

val AppTypography = Typography(
    displayMedium = TextStyle(fontFamily = FrauncesDisplay, fontWeight = FontWeight.W600, fontSize = 32.sp, letterSpacing = (-0.02).em),
    headlineSmall = TextStyle(fontFamily = FrauncesHeading, fontWeight = FontWeight.W600, fontSize = 24.sp, letterSpacing = (-0.02).em),
    titleMedium = TextStyle(fontFamily = FrauncesTitle, fontWeight = FontWeight.W600, fontSize = 18.sp, letterSpacing = (-0.02).em),
    bodyLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.W500, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.W400, fontSize = 15.sp),
    bodySmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.W400, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.W500, fontSize = 11.sp),
)

/** Uppercase, wide-tracked, dim — the web's "eyebrow" label above a stat value. */
val EyebrowStyle = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.W600,
    fontSize = 11.sp,
    letterSpacing = 0.18.em,
    color = AppColors.TextDim,
)

/** Bold serif — the web's stat-value emphasis, distinct from body text. */
val StatValueStyle = TextStyle(
    fontFamily = FrauncesTitle,
    fontWeight = FontWeight.W600,
    fontSize = 18.sp,
    color = AppColors.Text,
)
