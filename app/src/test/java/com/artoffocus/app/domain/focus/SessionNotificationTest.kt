package com.artoffocus.app.domain.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ongoing notification's text — the one part of the app a user reads while the app is closed.
 *
 * Tested as a pure function so the wording, and more importantly the arithmetic behind it, can be
 * checked without an emulator. Two things here are genuinely load-bearing: the countdown must be
 * derived from wall-clock timestamps (I7) rather than any counter of its own, and the
 * REST_COMPLETE wording must not imply the next sitting has already begun (I11).
 */
class SessionNotificationTest {

    private val sittingMinutes = 30
    private val pieces = 4
    private val start = 1_000_000L

    @Test
    fun an_idle_session_shows_nothing() {
        // Nothing to keep alive and nothing to say, so the service should take the notification
        // down rather than display a stale one.
        assertFalse(hasLiveSession(SessionStatus.IDLE))
        assertNull(sessionNotice(IDLE_SESSION, sittingMinutes, pieces, start))
    }

    @Test
    fun every_non_idle_status_counts_as_a_live_session() {
        // SITTING_COMPLETE and REST_COMPLETE included: both wait on an explicit tap (I10/I11), so
        // they are precisely when the app most needs to survive long enough to be tapped.
        SessionStatus.entries.filter { it != SessionStatus.IDLE }.forEach { status ->
            assertTrue("$status should be live", hasLiveSession(status))
        }
    }

    @Test
    fun a_running_sitting_counts_down_from_the_sitting_length() {
        val session = SessionState(status = SessionStatus.RUNNING, startedAt = start)
        // Ten minutes in, twenty of thirty remain.
        val notice = sessionNotice(session, sittingMinutes, pieces, start + 10 * 60_000L)
        assertNotNull(notice)
        assertEquals("Focusing — 20:00 left", notice!!.title)
        assertEquals("Sitting 1 of 4", notice.text)
    }

    @Test
    fun the_countdown_is_derived_from_the_clock_not_from_a_counter() {
        // The heart of I7: the same session read at two different moments gives two different
        // countdowns, with nothing accumulated in between. If this ever needed a tick to advance,
        // a killed process would freeze the notification while real time moved on.
        val session = SessionState(status = SessionStatus.RUNNING, startedAt = start)
        val early = sessionNotice(session, sittingMinutes, pieces, start + 60_000L)!!
        val late = sessionNotice(session, sittingMinutes, pieces, start + 20 * 60_000L)!!
        assertEquals("Focusing — 29:00 left", early.title)
        assertEquals("Focusing — 10:00 left", late.title)
    }

    @Test
    fun a_sitting_that_has_overrun_never_shows_a_negative_countdown() {
        // The service can tick past the end before the app reopens to advance the state, so this
        // is a real state and not a hypothetical one.
        val session = SessionState(status = SessionStatus.RUNNING, startedAt = start)
        val notice = sessionNotice(session, sittingMinutes, pieces, start + 99 * 60_000L)!!
        assertEquals("Focusing — 00:00 left", notice.title)
        // Asserted on the number, not only the text: formatElapsed clamps internally, so a
        // string-only assertion passes even when this function's own arithmetic goes negative.
        assertEquals(0L, notice.remainingMs)
    }

    @Test
    fun a_paused_sitting_says_so_and_holds_its_remaining_time() {
        // accumulatedMs is frozen at pause, so the countdown must not keep running down.
        val session = SessionState(
            status = SessionStatus.PAUSED,
            accumulatedMs = 10 * 60_000L,
            startedAt = start,
        )
        val early = sessionNotice(session, sittingMinutes, pieces, start + 60_000L)!!
        val muchLater = sessionNotice(session, sittingMinutes, pieces, start + 60 * 60_000L)!!
        assertEquals("Paused — 20:00 left", early.title)
        assertEquals("Paused — 20:00 left", muchLater.title)
        assertTrue(early.text.contains("resume"))
    }

    @Test
    fun a_finished_sitting_says_the_piece_is_already_revealed() {
        // I10 — the piece is revealed on completion, never gated behind the user's choice, so the
        // wording must not imply they have to act to earn it.
        val session = SessionState(status = SessionStatus.SITTING_COMPLETE, sittingIndex = 1)
        val notice = sessionNotice(session, sittingMinutes, pieces, start)!!
        assertEquals("A piece is ready", notice.title)
        assertTrue(notice.text.contains("continue or rest"))
    }

    @Test
    fun a_rest_counts_down_on_its_own_clock() {
        // Rest has its own timer (restStartedAt/restMinutes) and rest time is never focus time (I1).
        val session = SessionState(
            status = SessionStatus.RESTING,
            sittingIndex = 1,
            restStartedAt = start,
            restMinutes = 10,
        )
        val notice = sessionNotice(session, sittingMinutes, pieces, start + 4 * 60_000L)!!
        assertEquals("Resting — 06:00 left", notice.title)
    }

    @Test
    fun a_finished_rest_never_implies_the_next_sitting_has_started() {
        // I11 — REST_COMPLETE stays put until the user taps Continue. Text suggesting otherwise
        // would be a lie about the app's most carefully guarded transition.
        val session = SessionState(status = SessionStatus.REST_COMPLETE, sittingIndex = 1, restMinutes = 10)
        val notice = sessionNotice(session, sittingMinutes, pieces, start)!!
        assertEquals("Rest is over", notice.title)
        assertTrue(notice.text.contains("open when you're ready"))
        assertFalse(notice.title.contains("Focusing"))
    }

    @Test
    fun the_sitting_number_is_shown_one_based_and_never_exceeds_the_total() {
        // sittingIndex is 0-based internally; a person reading "Sitting 0 of 4" would think it
        // was broken. The clamp guards the final sitting, where the index equals pieces - 1 and
        // completion can push the displayed number to the edge.
        val first = sessionNotice(
            SessionState(status = SessionStatus.RUNNING, startedAt = start, sittingIndex = 0),
            sittingMinutes, pieces, start,
        )!!
        assertTrue(first.text.contains("Sitting 1 of 4"))

        val last = sessionNotice(
            SessionState(status = SessionStatus.SITTING_COMPLETE, sittingIndex = 3),
            sittingMinutes, pieces, start,
        )!!
        assertTrue(last.text.contains("Sitting 4 of 4"))

        // Even a session whose index has somehow run past the count must not read "5 of 4".
        val beyond = sessionNotice(
            SessionState(status = SessionStatus.SITTING_COMPLETE, sittingIndex = 9),
            sittingMinutes, pieces, start,
        )!!
        assertTrue(beyond.text.contains("Sitting 4 of 4"))
    }
}
