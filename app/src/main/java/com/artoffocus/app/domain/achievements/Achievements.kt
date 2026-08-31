package com.artoffocus.app.domain.achievements

import com.artoffocus.app.domain.profile.Statistics

/**
 * I15 — an earned achievement is never taken away. Achievements are derived, never stored,
 * which makes them retroactive (no unlock event to miss or back-fill). Streak achievements
 * read [Statistics.longestStreak], never currentStreak — a lapsed 30-day streak still
 * happened. If a cache table is ever added it must be a cache, never the source of truth.
 * Counts finished paintings only.
 */

enum class AchievementKind { STREAK, HOURS, PAINTINGS }

data class Achievement(val kind: AchievementKind, val threshold: Int, val title: String)

val ACHIEVEMENTS: List<Achievement> = listOf(
    Achievement(AchievementKind.STREAK, 3, "Three Days Running"),
    Achievement(AchievementKind.STREAK, 7, "A Full Week"),
    Achievement(AchievementKind.STREAK, 14, "A Fortnight"),
    Achievement(AchievementKind.STREAK, 30, "A Month of Mornings"),
    Achievement(AchievementKind.STREAK, 90, "A Season of Focus"),
    Achievement(AchievementKind.HOURS, 1, "First Hour"),
    Achievement(AchievementKind.HOURS, 10, "Ten Hours Deep"),
    Achievement(AchievementKind.HOURS, 50, "Fifty Hours"),
    Achievement(AchievementKind.HOURS, 100, "The Hundred"),
    Achievement(AchievementKind.HOURS, 500, "Five Hundred Hours"),
    Achievement(AchievementKind.PAINTINGS, 1, "First Masterpiece"),
    Achievement(AchievementKind.PAINTINGS, 5, "A Small Collection"),
    Achievement(AchievementKind.PAINTINGS, 10, "A Proper Gallery"),
    Achievement(AchievementKind.PAINTINGS, 25, "A Life in Paint"),
)

/** I15 — streak achievements MUST read longestStreak, never currentStreak. */
fun valueFor(stats: Statistics, kind: AchievementKind): Int = when (kind) {
    AchievementKind.STREAK -> stats.longestStreak
    AchievementKind.HOURS -> stats.focusMinutes / 60
    AchievementKind.PAINTINGS -> stats.count
}

fun isEarned(stats: Statistics, achievement: Achievement): Boolean =
    valueFor(stats, achievement.kind) >= achievement.threshold

fun earnedAchievements(stats: Statistics): List<Achievement> = ACHIEVEMENTS.filter { isEarned(stats, it) }

fun unearnedAchievements(stats: Statistics): List<Achievement> = ACHIEVEMENTS.filterNot { isEarned(stats, it) }

/** Nearest by proportion (highest completion fraction among the unearned), not absolute gap. */
fun nextAchievement(stats: Statistics): Achievement? =
    unearnedAchievements(stats).maxByOrNull { (valueFor(stats, it.kind).toDouble() / it.threshold).coerceAtMost(1.0) }

fun describeEarned(stats: Statistics): String = "${earnedAchievements(stats).size} of ${ACHIEVEMENTS.size} earned"
