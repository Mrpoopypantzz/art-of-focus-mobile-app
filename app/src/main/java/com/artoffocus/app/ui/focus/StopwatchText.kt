package com.artoffocus.app.ui.focus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.artoffocus.app.ui.theme.AppColors
import com.artoffocus.app.ui.theme.FrauncesDisplay

private val stopwatchStyle = TextStyle(
    fontFamily = FrauncesDisplay,
    fontWeight = FontWeight.W600,
    fontSize = 32.sp,
    letterSpacing = (-0.02).em,
    fontFeatureSettings = "tnum",
)

/**
 * The web's "carved into parchment" emboss on a live clock:
 *
 *   text-shadow: 0 1px 0 rgba(250,243,228,.7), 0 -1px 1px rgba(74,31,26,.14);
 *
 * Both offsets are ONE CSS PIXEL, and the dark one carries a one-pixel blur. The previous
 * version stacked three opaque copies of the text at plus/minus 1.dp, which at this screen's
 * 2.625 density is a ~2.6px displacement with hard edges -- nearly three times the intended
 * offset and no blur, so a running clock rendered as a visible triple image. That is a real
 * source of "blurry" text, not an emulator artifact.
 *
 * The dark half is now a genuine [Shadow] (its offset is in pixels and it takes a blur
 * radius); the light half stays a second draw because a TextStyle carries only one shadow.
 */
@Composable
fun StopwatchText(text: String, isLive: Boolean, modifier: Modifier = Modifier) {
    val onePx = with(LocalDensity.current) { 1.toDp() }

    Box(modifier = modifier) {
        if (isLive) {
            Text(
                text,
                style = stopwatchStyle,
                color = AppColors.SurfaceRaised.copy(alpha = 0.7f),
                modifier = Modifier.offset(y = onePx),
            )
        }
        Text(
            text,
            style = if (isLive) {
                stopwatchStyle.copy(
                    shadow = Shadow(
                        color = AppColors.Ink.copy(alpha = 0.14f),
                        offset = Offset(0f, -1f),
                        blurRadius = 1f,
                    ),
                )
            } else {
                stopwatchStyle
            },
            color = AppColors.Text,
        )
    }
}
