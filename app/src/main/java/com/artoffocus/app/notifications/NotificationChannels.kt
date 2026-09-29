package com.artoffocus.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationChannels {
    /**
     * NOTE THE `_v2`, AND DO NOT "TIDY" IT AWAY.
     *
     * Android freezes a channel's importance at the moment the channel is first created. After
     * that, `createNotificationChannel` on the same id is ignored — so raising the importance in
     * code has no effect on any device that has already run an earlier build. Calling it
     * IMPORTANCE_HIGH under the old `rest_end` id would have looked fixed here and changed
     * nothing on the user's phone.
     *
     * A new id is a new channel, so the higher importance actually applies, on existing installs
     * included. If this ever needs to get louder again, bump to `_v3` — renaming is the only
     * mechanism the platform offers.
     */
    const val REST_END = "rest_end_v2"

    /** Retired in favour of [REST_END]; deleted on launch so it stops cluttering settings. */
    private const val REST_END_LEGACY = "rest_end"

    /**
     * The ongoing session notification. IMPORTANCE_LOW deliberately: it must be present, never
     * interruptive — it is on screen for the whole sitting, so a sound or a peek would make the
     * app unusable. The loud one is [REST_END].
     */
    const val SESSION = "session_v1"

    /** "A piece is ready." Its own channel so it can be silenced without losing rest-end. */
    const val SITTING_END = "sitting_end_v1"

    const val DAILY_CALL = "daily_call"
    const val STREAK_RISK = "streak_risk"
    const val ACHIEVEMENT = "achievement"

    fun createAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        // Rest end is the one notification the user is actively waiting for, so it is the one
        // kind that may interrupt: HIGH peeks on screen and vibrates. The others are nudges and
        // stay quiet deliberately.
        manager.createNotificationChannel(
            NotificationChannel(REST_END, "Rest complete", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Tells you when a rest has finished and the next sitting is ready."
                enableVibration(true)
            },
        )
        runCatching { manager.deleteNotificationChannel(REST_END_LEGACY) }

        // LOW: shows in the shade with no sound and no peek. See the doc on SESSION.
        manager.createNotificationChannel(
            NotificationChannel(SESSION, "Session in progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "The live countdown while you are focusing or resting."
                setShowBadge(false)
            },
        )

        // HIGH like rest end: a finished sitting is the other thing the user is actively
        // waiting to hear about, and it could not be announced at all before.
        manager.createNotificationChannel(
            NotificationChannel(SITTING_END, "Sitting complete", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Tells you when a sitting has finished and a piece is revealed."
                enableVibration(true)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(DAILY_CALL, "Daily studio call", NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(STREAK_RISK, "Streak protection", NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(ACHIEVEMENT, "Achievements", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }
}
