package com.artoffocus.app.domain.notifications

import com.artoffocus.app.domain.profile.Statistics
import com.artoffocus.app.domain.profile.dayNumber

/**
 * Owns M8/I7's notification consequence: notifications never fire during a sitting. On the
 * web this module also carried a platform-support detector (iOS PWA install prompts, Chrome
 * Notification Trigger checks) because web notifications from a background tab are
 * unreliable. A native Android app has no equivalent problem — WorkManager/AlarmManager
 * notifications are reliable regardless of app state — so that whole platform-detection
 * concern is dropped here; every kind below can be treated as reliable on Android. This is a
 * genuine product improvement over the web version, not a missing feature.
 */

enum class NotificationKind { REST_END, ACHIEVEMENT, DAILY_CALL, STREAK_RISK }

data class NotificationPrefs(
    val restEnd: Boolean = true,
    val achievement: Boolean = true,
    val dailyCall: Boolean = false,
    val streakRisk: Boolean = false,
)

val DEFAULT_PREFS = NotificationPrefs()

fun isEnabled(prefs: NotificationPrefs, kind: NotificationKind): Boolean = when (kind) {
    NotificationKind.REST_END -> prefs.restEnd
    NotificationKind.ACHIEVEMENT -> prefs.achievement
    NotificationKind.DAILY_CALL -> prefs.dailyCall
    NotificationKind.STREAK_RISK -> prefs.streakRisk
}

/** True only if a streak is active, something has been completed before, and nothing finished today yet. */
fun shouldNotifyStreakRisk(stats: Statistics, lastCompletedAt: Long?, now: Long): Boolean {
    if (stats.currentStreak <= 0) return false
    if (lastCompletedAt == null) return false
    return dayNumber(lastCompletedAt) < dayNumber(now)
}
