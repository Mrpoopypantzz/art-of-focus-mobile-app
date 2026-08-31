package com.artoffocus.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * I7/M8: rest-end notifications are scheduled relative to a running session, so a reboot
 * during a sitting must not resurrect a stale alarm. This receiver only re-arms the
 * best-effort daily/streak reminders, never a rest-end alarm (those are re-derived from
 * session state on next app open).
 */
class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        NotificationScheduler.rescheduleBestEffortReminders(context)
    }
}
