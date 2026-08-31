package com.artoffocus.app.domain.museum

import com.artoffocus.app.domain.focus.Painting
import java.util.Calendar
import java.util.Locale

/** Owns I12 — a completed painting is never destroyed. */

data class CompletedPainting(
    val painting: Painting,
    val totalMinutes: Int,
    val sittingMinutes: Int,
    val pieces: Int,
    val startedAt: Long,
    val completedAt: Long,
)

/** Identity/idempotency key — matches the `unique (user_id, started_at, seed)` DB constraint. */
fun paintingKey(record: CompletedPainting): String = "${record.startedAt}:${record.painting.seed}"

fun paintingSpanMs(record: CompletedPainting): Long = (record.completedAt - record.startedAt).coerceAtLeast(0L)

data class MuseumStats(val count: Int, val focusMinutes: Int, val longestMinutes: Int)

fun museumStats(paintings: List<CompletedPainting>): MuseumStats {
    if (paintings.isEmpty()) return MuseumStats(0, 0, 0)
    return MuseumStats(
        count = paintings.size,
        focusMinutes = paintings.sumOf { it.totalMinutes },
        longestMinutes = paintings.maxOf { it.totalMinutes },
    )
}

data class MonthGroup(val key: String, val label: String, val paintings: List<CompletedPainting>)

private fun monthKey(epochMs: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = epochMs
    return "%04d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
}

private fun monthLabel(epochMs: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = epochMs
    val monthNames = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    return "${monthNames[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}"
}

/** F9.2 — Timeline view: grouped by local completion month, newest-first, paintings within a group newest-first. */
fun groupByMonth(paintings: List<CompletedPainting>): List<MonthGroup> {
    val groups = paintings.groupBy { monthKey(it.completedAt) }
    return groups.entries
        .sortedByDescending { it.key }
        .map { (key, group) ->
            MonthGroup(
                key = key,
                label = monthLabel(group.first().completedAt),
                paintings = group.sortedByDescending { it.completedAt },
            )
        }
}

fun byMostRecent(paintings: List<CompletedPainting>): List<CompletedPainting> =
    paintings.sortedByDescending { it.completedAt }

fun formatFocusSpan(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours == 0 -> "${mins}m"
        mins == 0 -> "${hours}h"
        else -> "${hours}h ${mins}m"
    }
}
