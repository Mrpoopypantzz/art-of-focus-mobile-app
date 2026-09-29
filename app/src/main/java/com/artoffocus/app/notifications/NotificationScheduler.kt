package com.artoffocus.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.artoffocus.app.domain.focus.SessionState
import com.artoffocus.app.domain.focus.SessionStatus
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    private const val TAG = "NotificationScheduler"

    /**
     * F14.1 — scheduled the moment rest starts; fires at the exact wall-clock rest end.
     *
     * Exact, not best-effort. This used WorkManager's `setInitialDelay`, which is explicitly
     * inexact and can slip by minutes under Doze — for a five-minute rest, useless. The
     * alarm is identified by [sittingIndex] rather than the rest start time, because
     * `completeRest` zeroes `restStartedAt` and the old identity check could never match
     * once the app had been open at rest end. [RestEndReceiver] re-checks state before it
     * posts anything, so a stale alarm is harmless.
     */
    fun scheduleRestEnd(context: Context, restStartedAt: Long, restMinutes: Int, pieces: Int, sittingIndex: Int) {
        val fireAt = restStartedAt + restMinutes * 60_000L
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = restEndIntent(context, sittingIndex, pieces)

        // canScheduleExactAlarms can be false on API 31/32, where the permission is
        // user-revocable. Degrading to an inexact alarm is the right failure: late is better
        // than silent, and the notification is a convenience, never a carrier of progress (I7).
        val canBeExact = android.os.Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
        runCatching {
            if (canBeExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pendingIntent)
            }
        }.onFailure {
            // Was a bare runCatching, so an unschedulable alarm — a revoked exact-alarm
            // permission, or an OEM limit on alarm count — was completely invisible. AppNotifier
            // already logs its failures; this is the other half of being able to diagnose a
            // missing notification from logcat instead of by bisecting builds.
            Log.w(TAG, "could not schedule rest-end alarm: $it")
        }
    }

    /**
     * Re-arms the rest-end alarm if a rest is still running, called on every app launch.
     *
     * [BootRescheduleReceiver] covers reboot, app update and clock changes, but those are the
     * cases that announce themselves with a broadcast. Several do not: a force-stop (from
     * Settings, or from an OEM "clean up" sweep) cancels every pending alarm silently, and on
     * API 31/32 the user can revoke the exact-alarm permission at any time. Nothing re-armed the
     * alarm in those cases, so a rest begun before them lost its notification permanently.
     *
     * Safe to call unconditionally: it does nothing unless a rest is genuinely in progress and
     * its end is still in the future, and re-scheduling the same alarm simply replaces it.
     */
    fun reconcileRestEnd(context: Context, session: SessionState, pieces: Int) {
        if (session.status != SessionStatus.RESTING) return
        val fireAt = session.restStartedAt + session.restMinutes * 60_000L
        if (fireAt <= System.currentTimeMillis()) return
        scheduleRestEnd(
            context = context,
            restStartedAt = session.restStartedAt,
            restMinutes = session.restMinutes,
            pieces = pieces,
            sittingIndex = session.sittingIndex,
        )
    }

    fun cancelRestEnd(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        // Extras are deliberately irrelevant here: PendingIntent equality ignores them, so
        // this matches and cancels whatever rest-end alarm is outstanding.
        alarmManager.cancel(restEndIntent(context, sittingIndex = -1, pieces = 0))
    }

    /**
     * The extras ride on the Intent itself rather than in a field on this object, so the
     * alarm survives process death — which is the exact case the rest-end alarm exists for.
     * FLAG_UPDATE_CURRENT makes re-scheduling overwrite the previous alarm's extras, the same
     * role `ExistingWorkPolicy.REPLACE` played before.
     */
    private fun restEndIntent(context: Context, sittingIndex: Int, pieces: Int): PendingIntent {
        val intent = Intent(context, RestEndReceiver::class.java).apply {
            putExtra(RestEndReceiver.KEY_SITTING_INDEX, sittingIndex)
            putExtra(RestEndReceiver.KEY_PIECES, pieces)
        }
        return PendingIntent.getBroadcast(
            context,
            RestEndReceiver.REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** F14.2/F14.3 — best-effort on the web; scheduled as reliable daily periodic work here. */
    fun scheduleBestEffortReminders(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DailyReminderWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyReminderWorker>(1, TimeUnit.DAYS).build(),
        )
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            StreakRiskWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<StreakRiskWorker>(1, TimeUnit.DAYS).build(),
        )
    }

    fun rescheduleBestEffortReminders(context: Context) = scheduleBestEffortReminders(context)
}
