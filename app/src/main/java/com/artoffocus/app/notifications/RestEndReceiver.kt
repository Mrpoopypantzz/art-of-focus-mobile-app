package com.artoffocus.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.artoffocus.app.ArtOfFocusApp
import com.artoffocus.app.domain.focus.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * F14.1 — the rest-end alarm, delivered by [android.app.AlarmManager] rather than WorkManager.
 *
 * WorkManager was the wrong tool here and its KDoc said otherwise: `setInitialDelay` is
 * explicitly inexact, so under Doze the "your rest is over" notification could arrive minutes
 * late. For a five-minute rest that is the difference between useful and useless. An exact
 * alarm fires on time, which is the whole point of this particular notification.
 *
 * Identification is by [KEY_SITTING_INDEX], not by rest start time. The obvious-looking check
 * — "is this the same rest I was scheduled for?" via `restStartedAt` — cannot work, because
 * `completeRest` zeroes `restStartedAt` on the RESTING -> REST_COMPLETE transition. When the
 * app was open at rest end, the ticker had already made that transition before the alarm ran,
 * so the comparison was 0L against a real timestamp and the notification was silently dropped.
 * The sitting index survives that transition, so it is the honest identity of "which rest".
 */
class RestEndReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val expectedSittingIndex = intent.getIntExtra(KEY_SITTING_INDEX, -1)
        val pieces = intent.getIntExtra(KEY_PIECES, 0)
        val appContext = context.applicationContext
        // Safe cast: an unchecked one would crash the receiver outright under a test harness or
        // any build with a different Application class, losing the notification entirely.
        val container = (appContext as? ArtOfFocusApp)?.container ?: return

        // goAsync keeps the receiver alive across the DataStore reads below; without it the
        // process can be torn down before the coroutine posts anything.
        val pending = goAsync()
        // goAsync alone does not keep the CPU awake. This receiver runs on a process the alarm
        // has just woken from Doze, and then does two DataStore reads — the device can return to
        // sleep mid-read, in which case nothing is posted and nothing explains why. The wake lock
        // is held for the few milliseconds that takes and always released in the finally.
        val wakeLock = acquireWakeLock(appContext)
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val session = container.sessionRepository.load(pieces)
                // Both statuses are legitimate: RESTING when the app was closed through rest
                // end, REST_COMPLETE when it was open and the ticker got there first.
                val restStillCurrent = session.status in REST_STATUSES &&
                    session.sittingIndex == expectedSittingIndex
                if (restStillCurrent && container.notificationPrefsRepository.load().restEnd) {
                    AppNotifier.notifyRestEnd(appContext)
                }
            } finally {
                runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
                pending.finish()
            }
        }
    }

    /**
     * Null rather than throwing if the platform refuses: a missing wake lock degrades to the
     * previous behaviour, which usually works. Failing to notify at all would be worse.
     */
    private fun acquireWakeLock(context: Context): PowerManager.WakeLock? = runCatching {
        val manager = context.getSystemService(PowerManager::class.java) ?: return null
        manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            // A generous ceiling relative to goAsync's ~10s budget, purely so a bug here can
            // never strand a held lock and drain the battery.
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }.getOrNull()

    companion object {
        private const val WAKE_LOCK_TAG = "ArtOfFocus:restEnd"
        private const val WAKE_LOCK_TIMEOUT_MS = 30_000L

        const val KEY_SITTING_INDEX = "sittingIndex"
        const val KEY_PIECES = "pieces"
        const val REQUEST_CODE = 1001

        private val REST_STATUSES = setOf(SessionStatus.RESTING, SessionStatus.REST_COMPLETE)
    }
}
