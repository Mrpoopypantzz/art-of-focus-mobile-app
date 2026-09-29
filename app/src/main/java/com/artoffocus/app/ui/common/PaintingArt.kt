package com.artoffocus.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.artoffocus.app.domain.focus.Painting
import com.artoffocus.app.domain.focus.paintingGradient
import com.artoffocus.app.domain.focus.paintingOverlay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shared painting-art renderer used by both the Focus screen's puzzle mosaic and the Museum
 * tiles, so a painting looks identical wherever it's shown. Approximates the web app's CSS
 * gradient + soft-light-blended overlay (radial highlight + diagonal tint) in Compose, since
 * Brush.linearGradient has no direct CSS-angle parameter and there is no mix-blend-mode.
 */
@Composable
fun PaintingArt(
    painting: Painting,
    modifier: Modifier = Modifier,
    // Crop is a property of where the painting is shown, not of the painting. The Museum's
    // cards are a uniform 4:5 grid, so a photo is centre-cropped to fit; during a session the
    // frame takes the photo's own shape and nothing is cut off. The stored file is never
    // altered either way — this only decides how it is drawn.
    contentScale: ContentScale = ContentScale.Fit,
) {
    val imageRef = painting.imageRef
    if (imageRef != null) {
        AsyncImage(
            model = imageRef,
            contentDescription = painting.title,
            contentScale = contentScale,
            modifier = modifier,
        )
        return
    }

    Canvas(modifier = modifier) {
        drawRect(paintingBaseBrush(painting, size))

        drawIntoCanvas { canvas ->
            val paint = Paint()
            canvas.saveLayer(Rect(Offset.Zero, size), paint)
            paintingOverlayBrushes(painting, size).forEach { (brush, blend) ->
                drawRect(brush, blendMode = blend)
            }
            canvas.restore()
        }
    }
}

/** e.g. "linear-gradient(142deg, #c9a05a, #8a5a2f, #4a1f1a)" -> a Brush spanning the box's diagonal. */
fun paintingBaseBrush(painting: Painting, sizePx: Size): Brush {
    val css = paintingGradient(painting)
    val hexColors = Regex("#[0-9a-fA-F]{6}").findAll(css)
        .map { Color(android.graphics.Color.parseColor(it.value)) }
        .toList()
    val angleDeg = Regex("linear-gradient\\((\\d+)deg").find(css)
        ?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
    val (start, end) = angleToOffsets(angleDeg, sizePx)
    return Brush.linearGradient(colors = hexColors.ifEmpty { listOf(Color.Gray) }, start = start, end = end)
}

/**
 * A soft radial highlight plus a diagonal tint, matching the web's overlay math: center at
 * (30 + h%40, 25 + h%50) percent, tint angle h%360 degrees. Composited with BlendMode.Softlight
 * as a Compose approximation of CSS's mix-blend-mode: soft-light.
 */
fun paintingOverlayBrushes(painting: Painting, sizePx: Size): List<Pair<Brush, BlendMode>> {
    val h = paintingOverlay(painting)
    val cx = 0.30f + (h % 40) / 100f
    val cy = 0.25f + (h % 50) / 100f
    val angleDeg = (h % 360).toFloat()

    val radial = Brush.radialGradient(
        colors = listOf(Color(0xFFFAF3E4).copy(alpha = 0.30f), Color.Transparent),
        center = Offset(sizePx.width * cx, sizePx.height * cy),
        radius = sizePx.minDimension.coerceAtLeast(1f) * 0.6f,
    )
    val (s, e) = angleToOffsets(angleDeg, sizePx)
    val diagonal = Brush.linearGradient(
        colors = listOf(Color(0xFF4A1F1A).copy(alpha = 0.18f), Color.Transparent),
        start = s,
        end = e,
    )
    return listOf(radial to BlendMode.Softlight, diagonal to BlendMode.Softlight)
}

/** Converts a CSS gradient angle (0deg = bottom-to-top, clockwise) into Compose start/end points. */
private fun angleToOffsets(angleDeg: Float, size: Size): Pair<Offset, Offset> {
    val rad = Math.toRadians((angleDeg - 90).toDouble())
    val cx = size.width / 2f
    val cy = size.height / 2f
    val len = size.width.coerceAtLeast(size.height) / 2f
    val dx = (cos(rad) * len).toFloat()
    val dy = (sin(rad) * len).toFloat()
    return Offset(cx - dx, cy - dy) to Offset(cx + dx, cy + dy)
}
