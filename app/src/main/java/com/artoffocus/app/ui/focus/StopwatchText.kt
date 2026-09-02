package com.artoffocus.app.ui.focus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.artoffocus.app.ui.theme.AppColors
import com.artoffocus.app.ui.theme.FrauncesFamily

private val stopwatchStyle = TextStyle(
    fontFamily = FrauncesFamily,
    fontWeight = FontWeight.W600,
    fontSize = 32.sp,
    letterSpacing = (-0.02).em,
    fontFeatureSettings = "tnum",
)

/**
 * Matches the web app's "carved into parchment" emboss: a dark shadow offset up and a light
 * shadow offset down, only while the clock is actually live (RUNNING) — a paused clock reads
 * flat, since TextStyle.shadow only accepts a single Shadow, so the effect is built by
 * stacking three Text draws instead.
 */
@Composable
fun StopwatchText(text: String, isLive: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        if (isLive) {
            Text(text, style = stopwatchStyle, color = AppColors.Ink.copy(alpha = 0.14f), modifier = Modifier.offset(y = (-1).dp))
            Text(text, style = stopwatchStyle, color = AppColors.SurfaceRaised.copy(alpha = 0.7f), modifier = Modifier.offset(y = 1.dp))
        }
        Text(text, style = stopwatchStyle, color = AppColors.Text)
    }
}
