package com.artoffocus.app.domain.focus

/**
 * Owns I7 (no progress is ever lost / wall-clock-derived elapsed time), I9 (quitting has
 * friction), I10 (sitting completion defaults to continuing), I11 (rest completion never
 * auto-starts). Every transition function below is a guard clause: called from the wrong
 * status, it returns the state unchanged. This file is the single most safety-critical
 * surface in the app — do not "simplify" a guard away.
 */

enum class SessionStatus { IDLE, RUNNING, PAUSED, SITTING_COMPLETE, RESTING, REST_COMPLETE }

/** All times are epoch milliseconds; 0 means "not set" (never a valid wall-clock time in this domain). */
data class SessionState(
    val status: SessionStatus = SessionStatus.IDLE,
    val startedAt: Long = 0L,
    val accumulatedMs: Long = 0L,
    val sittingIndex: Int = 0,
    val restStartedAt: Long = 0L,
    val restMinutes: Int = 0,
)

val IDLE_SESSION = SessionState()

/** I7 amendment — a gap on return longer than this is surfaced to the user (keep or discard), never silently resolved. */
const val AWAY_PROMPT_MS: Long = 15L * 60 * 1000

// --- Rest sub-system constants (I11 / F4) ---
const val REST_MIN = 5
const val REST_MAX = 30
const val REST_STEP = 5
const val DEFAULT_REST = 10

/** F4.3 — extends I9 (friction on resuming from rest), does not apply it. */
const val CONTINUE_LOCK_MS: Long = 60_000

/** F5.1 — UI-timer only, never session state; only advances while the screen is visible (else I7 would break). */
const val AUTO_CONTINUE_MS: Long = 60_000

fun restOptions(): List<Int> = (REST_MIN..REST_MAX step REST_STEP).toList()

/**
 * I7 — the core timing formula. Elapsed time is always derived from timestamps, never
 * accumulated by a ticking counter, and is clamped so a clock skew can never go negative.
 */
fun elapsedMs(state: SessionState, now: Long): Long {
    val live = if (state.status == SessionStatus.RUNNING) now - state.startedAt else 0L
    return (state.accumulatedMs + live).coerceAtLeast(0L)
}

fun startSession(now: Long): SessionState = SessionState(
    status = SessionStatus.RUNNING,
    startedAt = now,
    accumulatedMs = 0L,
    sittingIndex = 0,
)

fun pauseSession(state: SessionState, now: Long): SessionState {
    if (state.status != SessionStatus.RUNNING) return state
    return state.copy(
        status = SessionStatus.PAUSED,
        startedAt = 0L,
        accumulatedMs = elapsedMs(state, now),
    )
}

fun resumeSession(state: SessionState, now: Long): SessionState {
    if (state.status != SessionStatus.PAUSED) return state
    return state.copy(status = SessionStatus.RUNNING, startedAt = now)
}

/** I9 — the caller is responsible for the 5s visible countdown; this is the confirmed action. */
fun cancelSession(): SessionState = IDLE_SESSION

fun isSittingComplete(state: SessionState, sittingMinutes: Int, now: Long): Boolean {
    if (state.status == SessionStatus.IDLE) return false
    return elapsedMs(state, now) >= sittingMinutes * 60_000L
}

/** I2/I10 — piece reveal happens immediately on this transition; the clock freezes at the exact target (no overshoot). */
fun completeSitting(state: SessionState, sittingMinutes: Int): SessionState = state.copy(
    status = SessionStatus.SITTING_COMPLETE,
    startedAt = 0L,
    accumulatedMs = sittingMinutes * 60_000L,
)

fun startNextSitting(state: SessionState, now: Long): SessionState = state.copy(
    status = SessionStatus.RUNNING,
    startedAt = now,
    accumulatedMs = 0L,
    sittingIndex = state.sittingIndex + 1,
    restStartedAt = 0L,
    restMinutes = 0,
)

/** I7 amendment — user chose "discard": shortens the live stretch (or accumulated time) by the away gap. */
fun discardAwayTime(state: SessionState, awayMs: Long): SessionState = when (state.status) {
    SessionStatus.RUNNING -> state.copy(startedAt = state.startedAt + awayMs)
    else -> state.copy(accumulatedMs = (state.accumulatedMs - awayMs).coerceAtLeast(0L))
}

fun formatElapsed(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

fun formatAway(ms: Long): String {
    val totalMinutes = (ms / 60_000).coerceAtLeast(0L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0L -> "${minutes}m"
        minutes == 0L -> "${hours}h"
        else -> "${hours}h ${minutes}m"
    }
}

// --- Rest sub-system (I11) ---

/** Only valid from SITTING_COMPLETE; a no-op guard from any other status. */
fun startRest(state: SessionState, minutes: Int, now: Long): SessionState {
    if (state.status != SessionStatus.SITTING_COMPLETE) return state
    return state.copy(status = SessionStatus.RESTING, restStartedAt = now, restMinutes = minutes)
}

fun restElapsedMs(state: SessionState, now: Long): Long = when (state.status) {
    SessionStatus.REST_COMPLETE -> state.restMinutes * 60_000L
    SessionStatus.RESTING -> (now - state.restStartedAt).coerceAtLeast(0L)
    else -> 0L
}

fun restRemainingMs(state: SessionState, now: Long): Long =
    (state.restMinutes * 60_000L - restElapsedMs(state, now)).coerceAtLeast(0L)

fun isRestComplete(state: SessionState, now: Long): Boolean =
    state.status == SessionStatus.RESTING && restElapsedMs(state, now) >= state.restMinutes * 60_000L

/**
 * I11 — rest ending enters REST_COMPLETE and stays there. No code path may transition
 * REST_COMPLETE -> RUNNING without [continueFromRest] being called from explicit user action.
 */
fun completeRest(state: SessionState): SessionState {
    if (state.status != SessionStatus.RESTING) return state
    return state.copy(status = SessionStatus.REST_COMPLETE, restStartedAt = 0L)
}

/** Nonzero only while RESTING; the F4.3 friction window on resuming early. */
fun restLockRemainingMs(state: SessionState, now: Long): Long {
    if (state.status != SessionStatus.RESTING) return 0L
    return (CONTINUE_LOCK_MS - restElapsedMs(state, now)).coerceAtLeast(0L)
}

/**
 * I11 — from REST_COMPLETE this always advances. From RESTING it only advances once the
 * continue-lock has expired; otherwise it is a no-op. Rest time itself is discarded, never
 * banked as sitting time (I1).
 */
fun continueFromRest(state: SessionState, now: Long): SessionState = when (state.status) {
    SessionStatus.REST_COMPLETE -> startNextSitting(state, now)
    SessionStatus.RESTING -> if (restLockRemainingMs(state, now) == 0L) startNextSitting(state, now) else state
    else -> state
}

// --- Session progress (display derivation, D27) ---

data class SessionProgress(val completedSittings: Int, val focusedMs: Long, val totalMs: Long)

/**
 * D27 — the mosaic, caption, and clock all derive from this single function so they can
 * never disagree. Rest time never counts as focused time (I1).
 */
fun sessionProgress(state: SessionState, sittingMinutes: Int, pieces: Int, now: Long): SessionProgress {
    val sittingTargetMs = sittingMinutes * 60_000L
    val justFinished = state.status in setOf(
        SessionStatus.SITTING_COMPLETE, SessionStatus.RESTING, SessionStatus.REST_COMPLETE,
    )
    val completed = (state.sittingIndex + if (justFinished) 1 else 0).coerceAtMost(pieces)
    val inRest = state.status in setOf(SessionStatus.RESTING, SessionStatus.REST_COMPLETE)
    val countsLiveSitting = state.status != SessionStatus.IDLE &&
        state.status != SessionStatus.SITTING_COMPLETE &&
        !inRest
    val sittingMs = if (countsLiveSitting) elapsedMs(state, now) else 0L
    val totalMs = pieces * sittingTargetMs
    val focusedMs = (completed * sittingTargetMs + sittingMs).coerceAtMost(totalMs)
    return SessionProgress(completed, focusedMs, totalMs)
}

fun describeSessionProgress(progress: SessionProgress, pieces: Int): String =
    "${progress.completedSittings} of $pieces complete · ${formatSpan(progress.focusedMs)} / ${formatSpan(progress.totalMs)}"

/** Rounds down to the minute — distinct from [formatDuration], which takes whole minutes as input. */
fun formatSpan(ms: Long): String = formatDuration((ms / 60_000L).toInt())
