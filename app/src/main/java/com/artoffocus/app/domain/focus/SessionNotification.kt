package com.artoffocus.app.domain.focus

/**
 * The words on the ongoing session notification, and whether there should be one at all.
 *
 * Pure on purpose. The notification is the one part of the app the user sees while the app
 * itself is closed, so it is worth testing without an emulator — and keeping the text out of
 * the service keeps the service to its single job of displaying what it is given.
 *
 * Every countdown here is derived from the existing wall-clock helpers ([elapsedMs],
 * [restRemainingMs]) rather than from any counter of its own. That is what makes the ongoing
 * notification a *display* of session state and never a second source of truth for it (I7).
 */

/**
 * What to show, or null when a session is not running and the notification should go away.
 *
 * [remainingMs] is the countdown behind [title]. It is exposed rather than kept local so a test
 * can assert the clamp on the number itself: [formatElapsed] clamps internally too, so asserting
 * only on the formatted string passes even when the arithmetic here is wrong.
 */
data class SessionNotice(val title: String, val text: String, val remainingMs: Long = 0L)

/**
 * True while the app should be holding a foreground service.
 *
 * IDLE means no session, so nothing to show. Every other status is part of a live session the
 * user is expecting the app to be keeping track of — including SITTING_COMPLETE and
 * REST_COMPLETE, which wait on an explicit tap (I10/I11) and so are exactly when the app must
 * survive long enough to be tapped.
 */
fun hasLiveSession(status: SessionStatus): Boolean = status != SessionStatus.IDLE

/**
 * Builds the notice for a session, or null when there is none to show.
 *
 * [sittingMinutes] and [pieces] come from the user's setup; [now] is wall-clock. The piece
 * numbers shown are 1-based because they are read by a person, while `sittingIndex` is
 * 0-based internally.
 */
fun sessionNotice(
    state: SessionState,
    sittingMinutes: Int,
    pieces: Int,
    now: Long,
): SessionNotice? {
    if (!hasLiveSession(state.status)) return null

    val sittingTargetMs = sittingMinutes * 60_000L
    val sittingRemaining = (sittingTargetMs - elapsedMs(state, now)).coerceAtLeast(0L)
    // 1-based for reading: sittingIndex 0 is the first sitting.
    val sittingNumber = (state.sittingIndex + 1).coerceAtMost(pieces)
    val progress = "Sitting $sittingNumber of $pieces"

    return when (state.status) {
        // The only status whose text changes second to second.
        SessionStatus.RUNNING -> SessionNotice(
            title = "Focusing — ${formatElapsed(sittingRemaining)} left",
            text = progress,
            remainingMs = sittingRemaining,
        )
        SessionStatus.PAUSED -> SessionNotice(
            title = "Paused — ${formatElapsed(sittingRemaining)} left",
            text = "$progress · open to resume",
            remainingMs = sittingRemaining,
        )
        // A piece is already revealed by this point; the notification says so rather than
        // implying the user must act to earn it.
        SessionStatus.SITTING_COMPLETE -> SessionNotice(
            title = "A piece is ready",
            text = "$progress complete · open to continue or rest",
        )
        SessionStatus.RESTING -> SessionNotice(
            title = "Resting — ${formatElapsed(restRemainingMs(state, now))} left",
            text = "$progress complete",
            remainingMs = restRemainingMs(state, now),
        )
        // I11 — rest ending never starts the next sitting, so the text must not suggest one is
        // already under way.
        SessionStatus.REST_COMPLETE -> SessionNotice(
            title = "Rest is over",
            text = "$progress complete · open when you're ready",
        )
        SessionStatus.IDLE -> null
    }
}
