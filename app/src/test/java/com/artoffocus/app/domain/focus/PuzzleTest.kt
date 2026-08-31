package com.artoffocus.app.domain.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleTest {

    // --- I2 pin: layout must never invent or drop a piece ---

    @Test
    fun `I2 - tileCount always equals the input piece count, across every reachable count`() {
        for (pieces in MIN_PIECES..MAX_PIECES) {
            val layout = layoutFor(pieces)
            assertEquals("pieces=$pieces", pieces, tileCount(layout))
        }
    }

    @Test
    fun `layoutFor(13) yields a 3x5 grid with one short final row`() {
        val layout = layoutFor(13)
        assertEquals(3, layout.cols)
        assertEquals(5, layout.rows)
        assertEquals(4, layout.completeRows)
        assertEquals(1, layout.lastRowCount)
    }

    @Test
    fun `layoutFor(90) yields a uniform 9x10 grid with no short row`() {
        val layout = layoutFor(90)
        assertEquals(9, layout.cols)
        assertEquals(10, layout.rows)
        // Uniform grid: every row (including the last) is "complete", so there is no
        // separate short final row to stretch.
        assertEquals(10, layout.completeRows)
        assertEquals(0, layout.lastRowCount)
    }

    @Test
    fun `layoutFor(1) is a single tile`() {
        val layout = layoutFor(1)
        assertEquals(1, layout.cols)
        assertEquals(1, layout.rows)
        assertEquals(1, tileCount(layout))
    }

    // --- revealedCount: must agree with sessionProgress's notion of "completed" ---

    @Test
    fun `revealedCount is zero while idle`() {
        assertEquals(0, revealedCount(IDLE_SESSION, 6))
    }

    @Test
    fun `revealedCount does not count the sitting in progress`() {
        val running = startSession(0L).copy(sittingIndex = 2)
        assertEquals(2, revealedCount(running, 6))
    }

    @Test
    fun `revealedCount includes the just-finished piece immediately on sitting completion`() {
        val completed = SessionState(status = SessionStatus.SITTING_COMPLETE, sittingIndex = 2)
        assertEquals(3, revealedCount(completed, 6))
    }

    @Test
    fun `revealedCount keeps the earned piece revealed through rest, D27`() {
        val resting = SessionState(status = SessionStatus.RESTING, sittingIndex = 2)
        val restComplete = SessionState(status = SessionStatus.REST_COMPLETE, sittingIndex = 2)
        assertEquals(3, revealedCount(resting, 6))
        assertEquals(3, revealedCount(restComplete, 6))
    }

    @Test
    fun `revealedCount is clamped to the piece count`() {
        val overComplete = SessionState(status = SessionStatus.SITTING_COMPLETE, sittingIndex = 6)
        assertEquals(6, revealedCount(overComplete, 6))
    }

    @Test
    fun `isPaintingComplete is true only when every piece is revealed`() {
        val allButOne = SessionState(status = SessionStatus.SITTING_COMPLETE, sittingIndex = 4)
        val all = SessionState(status = SessionStatus.SITTING_COMPLETE, sittingIndex = 5)
        assertTrue(!isPaintingComplete(allButOne, 6))
        assertTrue(isPaintingComplete(all, 6))
    }
}
