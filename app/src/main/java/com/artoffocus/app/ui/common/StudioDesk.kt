package com.artoffocus.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.artoffocus.app.ui.theme.AppColors

/** What the desk is reacting to. Deliberately not a session type — the drawing knows nothing
 *  about sittings, so it can be previewed, tested, and reused on its own. */
enum class DeskMood { IDLE, WORKING, RESTING, COMPLETE }

/**
 * @param fill 0f..1f — how much sand remains in the upper bulb. 1f is a full glass.
 */
data class DeskState(val mood: DeskMood, val fill: Float)

/**
 * A still life on a shelf: hourglass, brush, palette. It reports the session's state without
 * a single number — the glass empties as a sitting runs, the brush lies down during a rest,
 * a seal is pressed when a sitting is finished.
 *
 * Purely derived: it holds no timer and no animation. The Focus screen already recomposes on
 * a 250ms tick, so passing a fresh [DeskState] is all the motion this needs.
 */
@Composable
fun StudioDesk(state: DeskState, modifier: Modifier = Modifier, height: Dp = DESK_HEIGHT) {
    Box(modifier = modifier.fillMaxWidth().height(height)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            val unit = size.height
            val shelfY = size.height * SHELF_Y
            val centre = size.width / 2f

            drawShelf(shelfY)

            // Spread relative to the shelf's own width so the group stays balanced on any
            // screen, rather than bunching in the middle on a wide one.
            val spread = (size.width * 0.17f).coerceAtMost(unit * 1.6f)

            drawPalette(Offset(centre - spread, shelfY), unit * 0.26f, state.mood)
            drawHourglass(Offset(centre, shelfY), unit * 0.66f, state.fill)
            drawBrush(Offset(centre + spread, shelfY), unit * 0.52f, state.mood)

            if (state.mood == DeskMood.COMPLETE) {
                drawSeal(Offset(centre + spread * 1.75f, shelfY - unit * 0.14f), unit * 0.13f)
            }
        }
    }
}

private fun DrawScope.drawShelf(y: Float) {
    // Fades at both ends so the objects sit on a surface without a hard line boxing them in.
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(
                AppColors.LineStrong.copy(alpha = 0f),
                AppColors.LineStrong,
                AppColors.LineStrong.copy(alpha = 0f),
            ),
        ),
        topLeft = Offset(0f, y),
        size = Size(size.width, 1.dp.toPx()),
    )
}

/**
 * Two triangles meeting at a waist, with brass caps. Sand fills the upper bulb from the waist
 * up by [fill], and heaps in the lower bulb by the remainder — so the two always sum to a
 * full glass and the eye can read progress from either half.
 */
private fun DrawScope.drawHourglass(base: Offset, h: Float, fill: Float) {
    val halfW = h * 0.30f
    val top = base.y - h
    val waistY = base.y - h / 2f
    val capH = h * 0.07f
    val safeFill = fill.coerceIn(0f, 1f)

    fun bulb(upper: Boolean) = Path().apply {
        if (upper) {
            moveTo(base.x - halfW, top + capH)
            lineTo(base.x + halfW, top + capH)
            lineTo(base.x, waistY)
        } else {
            moveTo(base.x - halfW, base.y - capH)
            lineTo(base.x + halfW, base.y - capH)
            lineTo(base.x, waistY)
        }
        close()
    }

    // Barely-there glass. Anything more opaque competes with the sand for the eye — the
    // gold must be the only thing that reads as a quantity.
    drawPath(bulb(upper = true), color = AppColors.Surface.copy(alpha = 0.22f))
    drawPath(bulb(upper = false), color = AppColors.Surface.copy(alpha = 0.22f))

    // Sand in the upper bulb, resting on the waist. The bulb narrows downward, so the
    // sand's surface widens as the level rises — width is proportional to height above
    // the waist, which is what makes it read as a cone and not a rectangle.
    val upperH = waistY - (top + capH)
    if (safeFill > 0.01f) {
        val sandTop = waistY - upperH * safeFill
        val wAtSandTop = halfW * safeFill
        drawPath(
            Path().apply {
                moveTo(base.x - wAtSandTop, sandTop)
                lineTo(base.x + wAtSandTop, sandTop)
                lineTo(base.x, waistY)
                close()
            },
            brush = sandBrush,
        )
    }

    // What has already fallen, heaped on the floor of the lower bulb.
    val settled = 1f - safeFill
    if (settled > 0.01f) {
        val lowerH = (base.y - capH) - waistY
        val heapH = lowerH * settled
        val heapTopY = (base.y - capH) - heapH
        // The lower bulb widens downward, so the heap's top is narrower than its base.
        val wAtHeapTop = halfW * (heapH / lowerH).coerceIn(0f, 1f)
        drawPath(
            Path().apply {
                moveTo(base.x - halfW, base.y - capH)
                lineTo(base.x + halfW, base.y - capH)
                lineTo(base.x + wAtHeapTop * 0.75f, heapTopY)
                lineTo(base.x - wAtHeapTop * 0.75f, heapTopY)
                close()
            },
            brush = sandBrush,
        )
    }

    drawPath(bulb(upper = true), brush = glassEdgeBrush, style = Stroke(width = 1.dp.toPx()))
    drawPath(bulb(upper = false), brush = glassEdgeBrush, style = Stroke(width = 1.dp.toPx()))

    // Brass caps, top and bottom.
    drawRect(brassBrush, topLeft = Offset(base.x - halfW * 1.15f, top), size = Size(halfW * 2.3f, capH))
    drawRect(brassBrush, topLeft = Offset(base.x - halfW * 1.15f, base.y - capH), size = Size(halfW * 2.3f, capH))
}

/** Upright and tilted while working; laid flat on the shelf when resting or done. */
private fun DrawScope.drawBrush(base: Offset, h: Float, mood: DeskMood) {
    val working = mood == DeskMood.WORKING
    val w = h * 0.14f

    fun draw() {
        val top = base.y - h
        // Handle.
        drawRect(woodBrush, topLeft = Offset(base.x - w / 2f, top), size = Size(w, h * 0.68f))
        // Ferrule.
        drawRect(brassBrush, topLeft = Offset(base.x - w * 0.62f, top + h * 0.68f), size = Size(w * 1.24f, h * 0.10f))
        // Bristles, tapering to a point.
        drawPath(
            Path().apply {
                moveTo(base.x - w * 0.60f, top + h * 0.78f)
                lineTo(base.x + w * 0.60f, top + h * 0.78f)
                lineTo(base.x, base.y)
                close()
            },
            color = AppColors.Ink.copy(alpha = 0.75f),
        )
    }

    if (working) {
        // Leaning, as if just set down mid-stroke.
        rotate(degrees = -14f, pivot = base) { draw() }
    } else {
        // Laid flat on the shelf. Pivoting about the brush's own middle keeps it beside the
        // hourglass instead of swinging its length across it.
        rotate(degrees = 90f, pivot = Offset(base.x, base.y - h / 2f)) { draw() }
    }
}

/** A thumb-hole palette; it carries a smear of gilt only while work is happening. */
private fun DrawScope.drawPalette(base: Offset, r: Float, mood: DeskMood) {
    val centre = Offset(base.x, base.y - r * 0.9f)
    drawOval(
        color = AppColors.TextFaint,
        topLeft = Offset(centre.x - r, centre.y - r * 0.72f),
        size = Size(r * 2f, r * 1.44f),
    )
    drawCircle(color = AppColors.Bg, radius = r * 0.22f, center = Offset(centre.x + r * 0.34f, centre.y + r * 0.10f))
    if (mood == DeskMood.WORKING) {
        drawCircle(brush = sandBrush, radius = r * 0.20f, center = Offset(centre.x - r * 0.36f, centre.y - r * 0.16f))
    }
}

/** The same mark used on an earned achievement — pressed here when a sitting is finished. */
private fun DrawScope.drawSeal(centre: Offset, r: Float) {
    val steps = 11 * 12
    val path = Path()
    for (step in 0..steps) {
        val angle = (step.toFloat() / steps) * 2f * Math.PI.toFloat()
        val wobble = 1f + 0.06f * kotlin.math.cos(11f * angle)
        val x = centre.x + r * wobble * kotlin.math.cos(angle)
        val y = centre.y + r * wobble * kotlin.math.sin(angle)
        if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = AppColors.InkSoft)
}

// Hoisted: this draws on every 250ms tick, so nothing here is allocated per frame.
private val sandBrush = Brush.verticalGradient(listOf(AppColors.GildedStart, AppColors.GildedEnd))
private val brassBrush = Brush.verticalGradient(listOf(AppColors.GildedStart, AppColors.GildedEnd))
private val woodBrush = Brush.verticalGradient(listOf(AppColors.InkSoft, AppColors.Ink))
private val glassEdgeBrush = Brush.verticalGradient(
    listOf(AppColors.LineStrong, AppColors.LineStrong.copy(alpha = 0.45f)),
)

private val DESK_HEIGHT = 56.dp
private const val SHELF_Y = 0.92f
