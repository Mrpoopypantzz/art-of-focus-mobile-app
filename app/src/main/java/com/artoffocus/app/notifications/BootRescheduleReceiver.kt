package com.artoffocus.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.artoffocus.app.ArtOfFocusApp
import com.artoffocus.app.domain.focus.MAX_PIECES
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * I7/M8 — re-arms what a reboot, an app update, or a clock change destroys.
 *
 * The daily/streak reminders are WorkManager jobs and survive on their own; this re-arms them
 * for safety. The rest-end alarm does NOT survive: an AlarmManager alarm is dropped on reboot,
 * dropped again when the app is updated, and, being RTC_WAKEUP (wall-clock), becomes due at the
 * wrong moment if the clock or timezone moves. Only BOOT_COMPLETED was handled, so shipping an
 * update to a user who was mid-rest silently lost their notification.
 *
 * This receiver used to skip it, on the reasoning that the alarm would be "re-derived from
 * session state on next app open". That reasoning was wrong in the one case that matters — the
 * whole point of the notification is to reach the user when the app is NOT open, so waiting for
 * them to open it means the notification never comes. Rebooting mid-rest silently lost it.
 *
 * The session is already persisted, so the rest end can be recomputed and the alarm re-armed.
 * Guarded so a stale session cannot resurrect a bogus alarm: it must still be RESTING and its
 * end must still be in the future. [RestEndReceiver] re-checks state before posting anyway, so
 * a wrong alarm here would be dropped rather than fire falsely.
 *
 * Broadcast-driven only. A force-stop cancels alarms without broadcasting anything, which is why
 * [NotificationScheduler.reconcileRestEnd] also runs on every app launch.
 */
class BootRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        NotificationScheduler.rescheduleBestEffortReminders(context)

        val appContext = context.applicationContext
        val container = (appContext as? ArtOfFocusApp)?.container ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // Pieces are unknown here, and SessionRepository.load discards a session whose
                // sittingIndex exceeds them. MAX_PIECES keeps a valid session from being thrown
                // away; the status and deadline checks below are what actually gate the alarm.
                val session = container.sessionRepository.load(MAX_PIECES)
                // Shared with the on-launch path so the status and deadline guards cannot drift
                // apart between the two.
                NotificationScheduler.reconcileRestEnd(appContext, session, MAX_PIECES)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        /**
         * TIME_SET and TIMEZONE_CHANGED matter because the alarm is RTC_WAKEUP: moving the clock
         * moves when it is due, so it has to be recomputed against the new wall clock.
         */
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
