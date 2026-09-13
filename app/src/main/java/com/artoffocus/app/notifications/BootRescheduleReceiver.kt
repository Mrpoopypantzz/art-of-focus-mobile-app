package com.artoffocus.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.artoffocus.app.ArtOfFocusApp
import com.artoffocus.app.domain.focus.MAX_PIECES
import com.artoffocus.app.domain.focus.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * I7/M8 — re-arms what a reboot destroys.
 *
 * The daily/streak reminders are WorkManager jobs and survive on their own; this re-arms them
 * for safety. The rest-end alarm does NOT survive: an AlarmManager alarm is dropped on reboot.
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
 */
class BootRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
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
                if (session.status != SessionStatus.RESTING) return@launch

                val fireAt = session.restStartedAt + session.restMinutes * 60_000L
                if (fireAt <= System.currentTimeMillis()) return@launch

                NotificationScheduler.scheduleRestEnd(
                    context = appContext,
                    restStartedAt = session.restStartedAt,
                    restMinutes = session.restMinutes,
                    pieces = MAX_PIECES,
                    sittingIndex = session.sittingIndex,
                )
            } finally {
                pending.finish()
            }
        }
    }
}
