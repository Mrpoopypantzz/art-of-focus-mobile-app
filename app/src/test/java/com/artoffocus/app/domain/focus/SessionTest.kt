package com.artoffocus.app.domain.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {

    private val t0 = 1_000_000_000L

    // --- I7: elapsed time is wall-clock derived, never a ticking counter ---

    @Test
    fun `I7 - elapsed time while running is derived from now minus startedAt`() {
        val state = startSession(t0)
        assertEquals(5_000L, elapsedMs(state, t0 + 5_000L))
    }

    @Test
    fun `I7 - elapsed time never goes negative even if now is before startedAt`() {
        val state = startSession(t0)
        assertEquals(0L, elapsedMs(state, t0 - 5_000L))
    }

    @Test
    fun `I7 - pausing folds live elapsed time into accumulatedMs and clears startedAt`() {
        val running = startSession(t0)
        val paused = pauseSession(running, t0 + 10_000L)
        assertEquals(SessionStatus.PAUSED, paused.status)
        assertEquals(0L, paused.startedAt)
        assertEquals(10_000L, paused.accumulatedMs)
        // Elapsed time is stable while paused regardless of `now`.
        assertEquals(10_000L, elapsedMs(paused, t0 + 999_000L))
    }

    @Test
    fun `I7 - resuming restarts the live clock without touching accumulatedMs`() {
        val paused = pauseSession(startSession(t0), t0 + 10_000L)
        val resumed = resumeSession(paused, t0 + 50_000L)
        assertEquals(SessionStatus.RUNNING, resumed.status)
        assertEquals(10_000L, resumed.accumulatedMs)
        assertEquals(10_000L + 1_000L, elapsedMs(resumed, t0 + 51_000L))
    }

    @Test
    fun `I7 - pauseSession is a no-op guard when not running`() {
        val idle = IDLE_SESSION
        assertEquals(idle, pauseSession(idle, t0))
    }

    @Test
    fun `I7 - resumeSession is a no-op guard when not paused`() {
        val running = startSession(t0)
        assertEquals(running, resumeSession(running, t0 + 100))
    }

    @Test
    fun `I7 amendment - discardAwayTime shortens a running session's live stretch`() {
        val running = startSession(t0)
        val discarded = discardAwayTime(running, 20 * 60_000L)
        // startedAt moved forward by the away gap, so elapsed shrinks accordingly.
        assertEquals(t0 + 20 * 60_000L, discarded.startedAt)
    }

    @Test
    fun `I7 amendment - discardAwayTime on a non-running session reduces accumulatedMs, floored at zero`() {
        val paused = SessionState(status = SessionStatus.PAUSED, accumulatedMs = 5_000L)
        val discarded = discardAwayTime(paused, 20_000L)
        assertEquals(0L, discarded.accumulatedMs)
    }

    // --- I9: cancelling always returns to idle ---

    @Test
    fun `I9 - cancelSession always returns IDLE_SESSION`() {
        val running = startSession(t0)
        assertEquals(IDLE_SESSION, cancelSession())
        assertEquals(SessionStatus.IDLE, cancelSession().status)
    }

    // --- I10/I2: sitting completion freezes the clock and reveals immediately ---

    @Test
    fun `I2,I10 - completeSitting freezes accumulatedMs at exactly the sitting target, no overshoot`() {
        val running = startSession(t0)
        val overshot = running.copy(accumulatedMs = 999_999L) // simulate elapsed past target
        val completed = completeSitting(overshot, 30)
        assertEquals(SessionStatus.SITTING_COMPLETE, completed.status)
        assertEquals(30 * 60_000L, completed.accumulatedMs)
        assertEquals(0L, completed.startedAt)
    }

    @Test
    fun `isSittingComplete is false while idle regardless of time`() {
        assertFalse(isSittingComplete(IDLE_SESSION, 30, t0 + 999_999_999L))
    }

    @Test
    fun `isSittingComplete becomes true once elapsed reaches the sitting target`() {
        val running = startSession(t0)
        assertFalse(isSittingComplete(running, 30, t0 + 29 * 60_000L))
        assertTrue(isSittingComplete(running, 30, t0 + 30 * 60_000L))
    }

    @Test
    fun `startNextSitting advances sittingIndex and resets the clock and rest fields`() {
        val completed = completeSitting(startSession(t0), 30)
        val next = startNextSitting(completed, t0 + 100_000L)
        assertEquals(SessionStatus.RUNNING, next.status)
        assertEquals(1, next.sittingIndex)
        assertEquals(0L, next.accumulatedMs)
        assertEquals(t0 + 100_000L, next.startedAt)
        assertEquals(0L, next.restStartedAt)
        assertEquals(0, next.restMinutes)
    }

    // --- I11: rest completion never auto-starts the next sitting ---

    @Test
    fun `I11 - startRest only valid from SITTING_COMPLETE`() {
        val running = startSession(t0)
        assertEquals(running, startRest(running, 10, t0)) // no-op guard
        val completed = completeSitting(running, 30)
        val resting = startRest(completed, 10, t0 + 5_000L)
        assertEquals(SessionStatus.RESTING, resting.status)
        assertEquals(t0 + 5_000L, resting.restStartedAt)
        assertEquals(10, resting.restMinutes)
    }

    @Test
    fun `I11 - rest has its own clock, independent of the focus clock`() {
        val resting = startRest(completeSitting(startSession(t0), 30), 10, t0)
        assertEquals(3 * 60_000L, restElapsedMs(resting, t0 + 3 * 60_000L))
        // Focus elapsed stays frozen at the sitting target while resting.
        assertEquals(30 * 60_000L, elapsedMs(resting, t0 + 3 * 60_000L))
    }

    @Test
    fun `I11 - completeRest transitions to REST_COMPLETE and stays there`() {
        val resting = startRest(completeSitting(startSession(t0), 30), 10, t0)
        val restComplete = completeRest(resting.copy(restStartedAt = t0 - 10 * 60_000L))
        assertEquals(SessionStatus.REST_COMPLETE, restComplete.status)
        // restElapsedMs reports the full duration once complete, regardless of `now`.
        assertEquals(10 * 60_000L, restElapsedMs(restComplete, t0 + 999_999L))
    }

    @Test
    fun `I11 - no code path transitions REST_COMPLETE to RUNNING without continueFromRest`() {
        val restComplete = SessionState(status = SessionStatus.REST_COMPLETE, restMinutes = 10)
        // Merely calling completeRest again, or any getter, must never advance the status.
        assertEquals(SessionStatus.REST_COMPLETE, completeRest(restComplete).status)
    }

    @Test
    fun `I11 - continueFromRest from REST_COMPLETE always advances to the next sitting`() {
        val restComplete = SessionState(status = SessionStatus.REST_COMPLETE, sittingIndex = 2, restMinutes = 10)
        val next = continueFromRest(restComplete, t0)
        assertEquals(SessionStatus.RUNNING, next.status)
        assertEquals(3, next.sittingIndex)
    }

    @Test
    fun `D30 - continueFromRest from RESTING is a no-op before the 60s lock expires`() {
        val resting = SessionState(status = SessionStatus.RESTING, restStartedAt = t0, restMinutes = 10)
        val tooSoon = continueFromRest(resting, t0 + 30_000L)
        assertEquals(SessionStatus.RESTING, tooSoon.status)
    }

    @Test
    fun `D30 - continueFromRest from RESTING advances once the 60s lock has expired`() {
        val resting = SessionState(status = SessionStatus.RESTING, restStartedAt = t0, restMinutes = 10, sittingIndex = 1)
        val afterLock = continueFromRest(resting, t0 + CONTINUE_LOCK_MS)
        assertEquals(SessionStatus.RUNNING, afterLock.status)
        assertEquals(2, afterLock.sittingIndex)
    }

    @Test
    fun `restLockRemainingMs is only nonzero while RESTING`() {
        val resting = SessionState(status = SessionStatus.RESTING, restStartedAt = t0, restMinutes = 10)
        assertEquals(CONTINUE_LOCK_MS - 10_000L, restLockRemainingMs(resting, t0 + 10_000L))
        assertEquals(0L, restLockRemainingMs(resting.copy(status = SessionStatus.REST_COMPLETE), t0 + 10_000L))
        assertEquals(0L, restLockRemainingMs(IDLE_SESSION, t0))
    }

    // --- I1: rest time is never banked as focus time ---

    @Test
    fun `I1 - sessionProgress reports zero live sitting time while resting`() {
        val pieces = 4
        val sittingMinutes = 30
        val resting = startRest(completeSitting(startSession(t0), sittingMinutes), 10, t0 + 999_999L)
        val progress = sessionProgress(resting, sittingMinutes, pieces, t0 + 5 * 60_000L)
        // Only the one completed sitting counts; the frozen focus clock does not add extra time.
        assertEquals(sittingMinutes * 60_000L.toLong(), progress.focusedMs)
    }

    // --- Formatting ---

    @Test
    fun `formatElapsed widens to H MM SS past an hour`() {
        assertEquals("00:05", formatElapsed(5_000L))
        assertEquals("1:00:05", formatElapsed(60 * 60_000L + 5_000L))
    }

    @Test
    fun `formatAway formats minutes and hours`() {
        assertEquals("23m", formatAway(23 * 60_000L))
        assertEquals("1h 5m", formatAway(65 * 60_000L))
        assertEquals("2h", formatAway(120 * 60_000L))
    }
}
