package com.artoffocus.app.domain.notifications

import com.artoffocus.app.domain.profile.Statistics
import com.artoffocus.app.domain.profile.dayNumber

/**
 * Owns M8/I7's notification consequence: notifications never fire during a sitting. On the
 * web this module also carried a platform-support detector (iOS PWA install prompts, Chrome
 * Notification Trigger checks) because web notifications from a background tab are
 * unreliable. Android has no equivalent problem, so that platform-detection concern is
 * dropped here.
 *
 * "Reliable" needs two qualifications, because earlier versions of this comment overstated
 * it twice. REST_END is an exact alarm and does arrive on time. The daily and streak kinds ride
 * on WorkManager, which is deliberately inexact and may run late under Doze — fine for a daily
 * nudge, not for a rest timer.
 *
 * The larger correction: scheduling an alarm correctly is not enough on its own. Android freezes
 * and then kills a backgrounded process, and an OEM task cleaner cancels its pending alarms when
 * it does, so a perfectly scheduled alarm can simply never fire. That is what
 * `notifications/SessionService` addresses — it is the only mechanism Android offers for saying
 * the work is ongoing. SITTING_END is posted from that service, and exists at all only because
 * of it: before, a sitting finishing while the app was closed was completely silent.
 *
 * Separately, every kind is silently dropped when the POST_NOTIFICATIONS permission has not been
 * granted (see AppNotifier.canPost).
 */

enum class NotificationKind { REST_END, SITTING_END, ACHIEVEMENT, DAILY_CALL, STREAK_RISK }

data class NotificationPrefs(
    val restEnd: Boolean = true,
    /** "A piece is ready." On by default: it is the reason most people want notifications here. */
    val sittingEnd: Boolean = true,
    val achievement: Boolean = true,
    val dailyCall: Boolean = false,
    val streakRisk: Boolean = false,
)

val DEFAULT_PREFS = NotificationPrefs()

fun isEnabled(prefs: NotificationPrefs, kind: NotificationKind): Boolean = when (kind) {
    NotificationKind.REST_END -> prefs.restEnd
    NotificationKind.SITTING_END -> prefs.sittingEnd
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
