package com.artoffocus.app.domain.profile

import com.artoffocus.app.domain.museum.CompletedPainting
import com.artoffocus.app.domain.museum.museumStats
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Streak algorithm — feeds I15 (achievements read [FocusStreaks.longestStreak], never
 * currentStreak). Statistics/achievements count finished paintings only (F10.7/D35).
 */

/** Local-midnight day index (deliberately local time, not UTC — matches museum's month grouping). */
fun dayNumber(epochMs: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = epochMs
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis / 86_400_000L
}

data class FocusStreaks(val currentStreak: Int, val longestStreak: Int)

fun focusStreaks(paintings: List<CompletedPainting>, now: Long): FocusStreaks {
    if (paintings.isEmpty()) return FocusStreaks(0, 0)

    val days = paintings.map { dayNumber(it.completedAt) }.distinct().sorted()

    var run = 1
    var longest = 1
    for (i in 1 until days.size) {
        run = if (days[i] == days[i - 1] + 1) run + 1 else 1
        longest = maxOf(longest, run)
    }

    val today = dayNumber(now)
    val lastDay = days.last()
    // A streak survives if the last completion was today or yesterday (today isn't over yet).
    val current = if (today - lastDay <= 1) run else 0

    return FocusStreaks(current, longest)
}

fun bestMonthLabel(paintings: List<CompletedPainting>): String? {
    if (paintings.isEmpty()) return null
    val cal = Calendar.getInstance()
    val monthNames = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    val totals = mutableMapOf<String, Int>()
    val labels = mutableMapOf<String, String>()
    for (p in paintings) {
        cal.timeInMillis = p.completedAt
        val key = "%04d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
        totals[key] = (totals[key] ?: 0) + p.totalMinutes
        labels[key] = "${monthNames[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}"
    }
    val bestKey = totals.maxByOrNull { it.value }?.key ?: return null
    return labels[bestKey]
}

data class Statistics(
    val count: Int,
    val focusMinutes: Int,
    val longestMinutes: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val averageMinutes: Int,
    val bestMonth: String?,
)

fun statistics(paintings: List<CompletedPainting>, now: Long): Statistics {
    val museum = museumStats(paintings)
    val streaks = focusStreaks(paintings, now)
    val average = if (museum.count == 0) 0 else (museum.focusMinutes.toDouble() / museum.count).roundToInt()
    return Statistics(
        count = museum.count,
        focusMinutes = museum.focusMinutes,
        longestMinutes = museum.longestMinutes,
        currentStreak = streaks.currentStreak,
        longestStreak = streaks.longestStreak,
        averageMinutes = average,
        bestMonth = bestMonthLabel(paintings),
    )
}
