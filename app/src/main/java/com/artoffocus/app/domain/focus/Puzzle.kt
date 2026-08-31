package com.artoffocus.app.domain.focus

import kotlin.math.ceil
import kotlin.math.max

/**
 * Owns the puzzle grid layout algorithm and I2's "piece count is derived, never re-derived"
 * guarantee: [layoutFor] arranges n tiles (presentation), it must never change n itself.
 */

private const val WASTE_PENALTY = 0.35
private const val CANVAS_W = 280.0
private const val CANVAS_H = 350.0

data class PuzzleLayout(
    val cols: Int,
    val rows: Int,
    val completeRows: Int,
    val lastRowCount: Int,
)

/**
 * D23 — picks the column count minimizing aspect-distortion + a waste penalty. Any short
 * final row stretches full-width rather than leaving a gap (a gap would expose unearned
 * painting underneath).
 */
fun layoutFor(pieces: Int): PuzzleLayout {
    require(pieces >= 1) { "pieces must be >= 1" }

    var bestCols = 1
    var bestRows = pieces
    var bestScore = Double.MAX_VALUE

    for (cols in 1..pieces) {
        val rows = ceil(pieces.toDouble() / cols).toInt()
        val w = CANVAS_W / cols
        val h = CANVAS_H / rows
        val aspect = max(w / h, h / w)
        val waste = cols * rows - pieces
        val score = aspect + waste * WASTE_PENALTY
        if (score < bestScore) {
            bestScore = score
            bestCols = cols
            bestRows = rows
        }
    }

    val waste = bestCols * bestRows - pieces
    val completeRows = if (waste > 0) bestRows - 1 else bestRows
    val lastRowCount = pieces - completeRows * bestCols

    return PuzzleLayout(bestCols, bestRows, completeRows, lastRowCount)
}

/** I2 pin — a layout must always account for exactly the piece count it was built from. */
fun tileCount(layout: PuzzleLayout): Int = layout.completeRows * layout.cols + layout.lastRowCount

/**
 * How many pieces are currently earned/revealed. Uses the same "just finished a sitting"
 * set as [sessionProgress] (SITTING_COMPLETE, RESTING, REST_COMPLETE) so the puzzle mosaic
 * and the session-progress caption can never disagree about how many pieces are revealed —
 * a piece revealed at sitting-complete must stay revealed through rest (D27).
 */
fun revealedCount(state: SessionState, pieces: Int): Int {
    val justFinished = state.status in setOf(
        SessionStatus.SITTING_COMPLETE, SessionStatus.RESTING, SessionStatus.REST_COMPLETE,
    )
    val earned = state.sittingIndex + if (justFinished) 1 else 0
    return earned.coerceIn(0, pieces)
}

fun isPaintingComplete(state: SessionState, pieces: Int): Boolean = revealedCount(state, pieces) == pieces

fun completionRatio(state: SessionState, pieces: Int): Double =
    if (pieces <= 0) 0.0 else revealedCount(state, pieces).toDouble() / pieces

fun describeProgress(state: SessionState, pieces: Int): String = "${revealedCount(state, pieces)} of $pieces pieces"
