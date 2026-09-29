package com.artoffocus.app.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.artoffocus.app.ArtOfFocusApp
import com.artoffocus.app.MainActivity
import com.artoffocus.app.R
import com.artoffocus.app.domain.focus.SessionNotice
import com.artoffocus.app.domain.focus.SessionState
import com.artoffocus.app.domain.focus.SessionStatus
import com.artoffocus.app.domain.focus.hasLiveSession
import com.artoffocus.app.domain.focus.isSittingComplete
import com.artoffocus.app.domain.focus.pieceCount
import com.artoffocus.app.domain.focus.sessionNotice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps the app alive while a session is running, and shows the live countdown while it does.
 *
 * **Why this exists.** Android freezes and then kills a backgrounded process that has no
 * visible ongoing work. Stock Android is patient about it; MIUI, ColorOS, Funtouch and One UI
 * are not, and will do it within seconds of the app leaving the screen. When the process dies,
 * two things break that no amount of alarm-scheduling care can fix: the OEM's task cleaner
 * cancels pending alarms outright (a force-stop always does), and nothing is left running to
 * notice that a *sitting* has finished. A foreground service is the only mechanism Android
 * offers to say "this work is ongoing, do not kill it", which is why every reliable timer app
 * has one. The previous approach — asking the user to hunt through their phone's battery
 * settings — was treating the symptom.
 *
 * **What it deliberately is not.** This service does not drive the session. It never writes
 * session state, never advances a status, never completes a sitting or a rest. It reads what
 * DataStore already holds and derives every number it shows from wall-clock timestamps via the
 * pure functions in `domain/focus`. Delete this file and the timer behaves exactly as before —
 * it would only lose its resilience, not its correctness. That containment is what keeps I7
 * (progress is derived, never counted) and I11 (rest never auto-starts the next sitting) true:
 * there is no code path here that could violate either.
 *
 * The one notification it posts of its own accord is the sitting-complete one, which the app
 * had no way to deliver at all before — and that is a notification, not a state change.
 */
class SessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null

    /**
     * The sitting we have already announced, so the notification fires once per sitting rather
     * than once per tick. -1 means nothing announced yet.
     */
    private var announcedSittingIndex = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Post something immediately: on API 26+ a foreground service that does not call
        // startForeground within a few seconds is killed with an ANR-style crash.
        startForeground(NOTIFICATION_ID, placeholderNotification())
        if (tickJob == null) tickJob = scope.launch { follow() }
        // STICKY so the OS restarts the service if it does reclaim it under memory pressure;
        // the session is in DataStore, so a restarted service picks up exactly where it was.
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Follows the session once a second, updating the notification and stopping itself when the
     * session ends.
     *
     * A second, not the UI's 250ms: the text only shows whole seconds, so a faster tick would
     * cost battery to display nothing new.
     */
    private suspend fun follow() {
        val container = (application as? ArtOfFocusApp)?.container ?: run {
            Log.w(TAG, "no container; stopping")
            stopSelf()
            return
        }
        val setup = container.focusSetupRepository.load()
        val pieces = pieceCount(setup.totalMinutes, setup.sittingMinutes) ?: 1

        while (true) {
            val session = container.sessionRepository.load(pieces)
            if (!hasLiveSession(session.status)) {
                // The session ended (or was cancelled) while we were not looking. Take the
                // notification down with us rather than leaving it stranded in the shade.
                stopForegroundAndSelf()
                return
            }

            val now = System.currentTimeMillis()
            announceSittingIfComplete(container, session, setup.sittingMinutes, now)

            sessionNotice(session, setup.sittingMinutes, pieces, now)?.let { notice ->
                runCatching {
                    NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, ongoing(notice))
                }
            }
            delay(1000)
        }
    }

    /**
     * Posts "a piece is ready" the first time a sitting's time is up.
     *
     * Note what this does *not* do: it does not call `completeSitting`. The ViewModel's ticker
     * remains the only thing that advances the session, so I10's reveal-and-countdown sequence
     * happens exactly as it always has when the user opens the app. This only tells them it is
     * worth opening — the one thing the app previously could not do while closed.
     *
     * Keyed on `sittingIndex` so it fires once per sitting even though this runs every second.
     */
    private suspend fun announceSittingIfComplete(
        container: com.artoffocus.app.AppContainer,
        session: SessionState,
        sittingMinutes: Int,
        now: Long,
    ) {
        if (session.status != SessionStatus.RUNNING) return
        if (!isSittingComplete(session, sittingMinutes, now)) return
        if (announcedSittingIndex == session.sittingIndex) return
        announcedSittingIndex = session.sittingIndex
        if (!container.notificationPrefsRepository.load().sittingEnd) return
        AppNotifier.notifySittingEnd(this)
    }

    /** Shown for the fraction of a second before the first real tick computes the countdown. */
    private fun placeholderNotification(): Notification =
        ongoing(SessionNotice(title = "Focus session", text = "Keeping track of your sitting"))

    private fun ongoing(notice: SessionNotice): Notification = NotificationCompat.Builder(
        this,
        NotificationChannels.SESSION,
    )
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(notice.title)
        .setContentText(notice.text)
        // Ongoing and not dismissible: this is the app's claim on staying alive, so letting it
        // be swiped away would defeat the point. The loud rest-end notification is separate.
        .setOngoing(true)
        .setSilent(true)
        // The timestamp would read as the moment the service started, which is meaningless.
        .setShowWhen(false)
        .setContentIntent(openApp())
        .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
        // Visible on the lock screen: a countdown you have to unlock to read is no use.
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .build()

    /** Tapping the notification opens the app — which no notification in this app used to do. */
    private fun openApp(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            OPEN_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stopForegroundAndSelf() {
        @Suppress("DEPRECATION")
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
        }
        stopSelf()
    }

    companion object {
        private const val TAG = "SessionService"

        /** Distinct from every id in [AppNotifier] so the two never overwrite each other. */
        const val NOTIFICATION_ID = 42
        private const val OPEN_REQUEST_CODE = 2001

        /**
         * Starts the service. **Must be called while the app is in the foreground** — from a
         * ViewModel responding to a tap, never from a receiver or a worker — because API 31+
         * forbids a background start and throws `ForegroundServiceStartNotAllowedException`.
         * Every call site is a user action (Begin, Take Rest, Continue), so this holds.
         */
        fun start(context: Context) {
            runCatching {
                context.startService(Intent(context, SessionService::class.java))
            }.onFailure { Log.w(TAG, "could not start session service: $it") }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, SessionService::class.java))
            }.onFailure { Log.w(TAG, "could not stop session service: $it") }
        }
    }
}
