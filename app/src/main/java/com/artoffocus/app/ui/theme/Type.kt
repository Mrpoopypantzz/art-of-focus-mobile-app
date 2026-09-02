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
@OptIn(ExperimentalTextApi::class)
val FrauncesFamily: FontFamily = FontFamily(
    Font(R.font.fraunces_variable, FontWeight.W600, variationSettings = FontVariation.Settings(FontVariation.weight(600), FontVariation.Setting("opsz", 72f))),
    Font(R.font.fraunces_variable, FontWeight.W700, variationSettings = FontVariation.Settings(FontVariation.weight(700), FontVariation.Setting("opsz", 72f))),
)

@OptIn(ExperimentalTextApi::class)
val InterFamily: FontFamily = FontFamily(
    Font(R.font.inter_variable, FontWeight.W400, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter_variable, FontWeight.W500, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter_variable, FontWeight.W600, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

val AppTypography = Typography(
    displayMedium = TextStyle(fontFamily = FrauncesFamily, fontWeight = FontWeight.W600, fontSize = 32.sp, letterSpacing = (-0.02).em),
    headlineSmall = TextStyle(fontFamily = FrauncesFamily, fontWeight = FontWeight.W600, fontSize = 24.sp, letterSpacing = (-0.02).em),
    titleMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.W600, fontSize = 18.sp),
    bodyLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.W500, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.W400, fontSize = 15.sp),
    bodySmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.W400, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.W500, fontSize = 11.sp),
)
