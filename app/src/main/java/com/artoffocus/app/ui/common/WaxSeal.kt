package com.artoffocus.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.artoffocus.app.ui.theme.AppColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A pressed wax seal — the studio's mark that something is finished and cannot be undone.
 *
 * Used in two places by design: beside an earned achievement (I15 — never taken away) and on
 * a completed painting. The scalloped edge is drawn rather than shipped as an asset so it
 * tints with the palette and stays crisp at any size.
 */
@Composable
fun WaxSeal(modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val centre = Offset(this.size.width / 2f, this.size.height / 2f)

        drawPath(
            path = scallopedCircle(centre, radius),
            brush = Brush.radialGradient(
                colors = listOf(AppColors.InkSoft, AppColors.Ink),
                center = Offset(centre.x - radius * 0.3f, centre.y - radius * 0.3f),
                radius = radius * 1.6f,
            ),
        )
        // The impressed monogram: a small diamond, matching the Focus tab's glyph.
        val inner = radius * 0.34f
        drawPath(
            path = Path().apply {
                moveTo(centre.x, centre.y - inner)
                lineTo(centre.x + inner, centre.y)
                lineTo(centre.x, centre.y + inner)
                lineTo(centre.x - inner, centre.y)
                close()
            },
            color = AppColors.OnInk.copy(alpha = 0.55f),
        )
    }
}

/** A circle with [LOBES] soft bumps — the squeeze-out of wax pressed under a stamp. */
private fun scallopedCircle(centre: Offset, radius: Float): Path = Path().apply {
    val steps = LOBES * 12
    for (step in 0..steps) {
        val angle = (step.toFloat() / steps) * 2f * PI.toFloat()
        val wobble = 1f + SCALLOP_DEPTH * cos(LOBES * angle)
        val r = radius * wobble
        val x = centre.x + r * cos(angle)
        val y = centre.y + r * sin(angle)
        if (step == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private const val LOBES = 11
private const val SCALLOP_DEPTH = 0.06f
