package com.artoffocus.app.domain.focus

/**
 * Owns I1 (Total = Sitting × whole number), I2 (piece count is derived, never stored),
 * I3 (the shared 20-value Total ladder) and I3b (the sitting-length filter).
 *
 * All durations are integer minutes. Nothing here may be reordered casually: [TOTAL_LADDER]
 * depends on [sittingsFor] having already been declared (a TDZ hazard in the original TS —
 * in Kotlin this is enforced by initialization order of top-level properties, so the
 * val ordering below is load-bearing, not stylistic).
 */

const val MIN_PIECES = 2
const val MAX_PIECES = 90

const val SITTING_MIN = 10
const val SITTING_MAX = 120
const val SITTING_STEP = 5

/** I3 — the one shared ladder of Total Focus candidates. Exactly 20 values, no gaps. */
val TOTAL_CANDIDATES: List<Int> = listOf(
    30, 45, 60, 75, 90, 105, 120, // 15-minute steps up to 2h
    180, 240, 300, 360, 420, 480, 540, 600, 660, 720, 780, 840, 900, // 1-hour steps, 3h..15h
)

/** I2 — pieces = total / sitting when the division is exact, else null (never rounded/repaired). */
fun pieceCount(totalMinutes: Int, sittingMinutes: Int): Int? {
    if (totalMinutes <= 0 || sittingMinutes <= 0) return null
    if (totalMinutes % sittingMinutes != 0) return null
    return totalMinutes / sittingMinutes
}

/** I1 + I2 — a stored Total/Sitting pair is valid only if it divides exactly and stays within the piece bounds. */
fun isValidPair(totalMinutes: Int, sittingMinutes: Int): Boolean {
    val pieces = pieceCount(totalMinutes, sittingMinutes) ?: return false
    return pieces in MIN_PIECES..MAX_PIECES
}

/**
 * I3b — a sitting length is offered for a Total when all four hold:
 * exact division, multiple of 5, >= 10 minutes, <= 120 minutes.
 * No curated list, no per-Total exception. Deliberately does NOT enforce MIN_PIECES here —
 * only the upper piece bound (I2 amendment raised the ceiling to 90 and dropped the floor
 * at this layer).
 */
fun sittingsFor(totalMinutes: Int): List<Int> {
    val result = mutableListOf<Int>()
    var sitting = SITTING_MIN
    while (sitting <= SITTING_MAX) {
        val pieces = pieceCount(totalMinutes, sitting)
        if (pieces != null && pieces <= MAX_PIECES) {
            result.add(sitting)
        }
        sitting += SITTING_STEP
    }
    return result
}

/** I3 — every Total that has at least one valid sitting length is real ladder, as a consequence of I3b, not a rule. */
val TOTAL_LADDER: List<Int> = TOTAL_CANDIDATES.filter { sittingsFor(it).isNotEmpty() }

data class TotalAnchor(val level: FocusLevel, val value: Int)

/** I4 — focus-level default totals, deduplicated by value (small ladders can collapse anchors). */
fun totalAnchors(): List<TotalAnchor> {
    val seen = mutableSetOf<Int>()
    val anchors = mutableListOf<TotalAnchor>()
    for (level in FocusLevel.entries) {
        val value = DEFAULT_TOTAL_MINUTES.getValue(level)
        if (seen.add(value)) anchors.add(TotalAnchor(level, value))
    }
    return anchors
}

data class SittingAnchor(val level: FocusLevel, val value: Int)

/** I4 — wheel fast-travel anchors for a given Total's sitting options: shortest / middle / longest. */
fun sittingAnchors(totalMinutes: Int): List<SittingAnchor> {
    val options = sittingsFor(totalMinutes)
    if (options.isEmpty()) return emptyList()
    val targets = mapOf(
        FocusLevel.BEGINNER to options.first(),
        FocusLevel.INTERMEDIATE to options[(options.size - 1) / 2],
        FocusLevel.ADVANCED to options.last(),
    )
    val seen = mutableSetOf<Int>()
    val anchors = mutableListOf<SittingAnchor>()
    for (level in FocusLevel.entries) {
        val value = targets.getValue(level)
        if (seen.add(value)) anchors.add(SittingAnchor(level, value))
    }
    return anchors
}

/** e.g. 90 -> "1h 30m", 120 -> "2h", 45 -> "45m". */
fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours == 0 -> "${mins}m"
        mins == 0 -> "${hours}h"
        else -> "${hours}h ${mins}m"
    }
}

/** e.g. "6 sittings · one puzzle piece each", or null if the pair is invalid. */
fun describePieces(totalMinutes: Int, sittingMinutes: Int): String? {
    val pieces = pieceCount(totalMinutes, sittingMinutes)?.takeIf { it in MIN_PIECES..MAX_PIECES } ?: return null
    return "$pieces sittings · one puzzle piece each"
}
