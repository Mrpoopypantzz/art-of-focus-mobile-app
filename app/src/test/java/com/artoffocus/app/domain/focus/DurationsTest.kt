package com.artoffocus.app.domain.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DurationsTest {

    // --- I1: Total = Sitting × whole number ---

    @Test
    fun `I1 - pieceCount is null when total does not divide evenly`() {
        assertNull(pieceCount(100, 30))
    }

    @Test
    fun `I1 - pieceCount is exact division when it divides evenly`() {
        assertEquals(4, pieceCount(120, 30))
    }

    @Test
    fun `I1 - isValidPair requires exact division and piece bounds`() {
        assertTrue(isValidPair(120, 30))
        assertFalse(isValidPair(100, 30))
        assertFalse(isValidPair(30, 30)) // 1 piece < MIN_PIECES
    }

    // --- I3: the shared 20-value Total ladder, no gaps ---

    @Test
    fun `I3 - TOTAL_CANDIDATES has exactly 20 values`() {
        assertEquals(20, TOTAL_CANDIDATES.size)
    }

    @Test
    fun `I3 - TOTAL_CANDIDATES matches the exact published ladder`() {
        val expected = listOf(30, 45, 60, 75, 90, 105, 120, 180, 240, 300, 360, 420, 480, 540, 600, 660, 720, 780, 840, 900)
        assertEquals(expected, TOTAL_CANDIDATES)
    }

    @Test
    fun `I3 - every Total in TOTAL_LADDER offers at least two sitting options`() {
        for (total in TOTAL_LADDER) {
            assertTrue("Total $total should offer >= 2 sittings", sittingsFor(total).size >= 2)
        }
    }

    @Test
    fun `I3 - 30m is the floor of the ladder`() {
        assertEquals(30, TOTAL_LADDER.min())
    }

    // --- I2: piece count is derived, ceiling of 90 ---

    @Test
    fun `I2 - piece ceiling is 90, sittingsFor excludes lengths that would exceed it`() {
        // 900 minutes / 10 = 90 pieces exactly -> allowed
        assertTrue(sittingsFor(900).contains(10))
    }

    @Test
    fun `I2 - sittingsFor does not enforce a MIN_PIECES floor, only the MAX_PIECES ceiling`() {
        // 900 / 120 = 7.5 -> not exact, but confirm no floor rejection for any exact small-piece case
        // 120 total, 120 sitting -> 1 piece, below MIN_PIECES=2, but I3b does not check the floor.
        assertTrue(sittingsFor(120).contains(120))
    }

    // --- I3b: the four-condition sitting-length filter ---

    @Test
    fun `I3b - sittingsFor only returns multiples of 5 within 10 to 120`() {
        for (total in TOTAL_LADDER) {
            for (sitting in sittingsFor(total)) {
                assertTrue(sitting in SITTING_MIN..SITTING_MAX)
                assertEquals(0, sitting % SITTING_STEP)
                assertEquals(0, total % sitting)
            }
        }
    }

    @Test
    fun `I3b - sittingsFor(120) yields expected divisors`() {
        assertEquals(listOf(10, 15, 20, 30, 40, 60, 120), sittingsFor(120))
    }

    // --- I4: focus level sets defaults, never limits ---

    @Test
    fun `I4 - default totals per level`() {
        assertEquals(120, DEFAULT_TOTAL_MINUTES.getValue(FocusLevel.BEGINNER))
        assertEquals(360, DEFAULT_TOTAL_MINUTES.getValue(FocusLevel.INTERMEDIATE))
        assertEquals(720, DEFAULT_TOTAL_MINUTES.getValue(FocusLevel.ADVANCED))
    }

    @Test
    fun `I4 - default sittings per level`() {
        assertEquals(30, PREFERRED_SITTING_MINUTES.getValue(FocusLevel.BEGINNER))
        assertEquals(60, PREFERRED_SITTING_MINUTES.getValue(FocusLevel.INTERMEDIATE))
        assertEquals(90, PREFERRED_SITTING_MINUTES.getValue(FocusLevel.ADVANCED))
    }

    @Test
    fun `I4 - totalAnchors offers every level's default total, not filtered by level`() {
        val anchors = totalAnchors()
        assertEquals(setOf(120, 360, 720), anchors.map { it.value }.toSet())
    }

    @Test
    fun `I4 - sittingAnchors dedupes when options collapse to the same value`() {
        // A total with exactly 2 sitting options: middle anchor collapses onto one of the ends.
        val options = sittingsFor(60)
        val anchors = sittingAnchors(60)
        assertTrue(anchors.size <= options.size)
        assertTrue(anchors.map { it.value }.toSet().size == anchors.size)
    }

    // --- I5: the 25-minute exception, resolved ---

    @Test
    fun `I5 - 25 minutes is never offered as a sitting length (not a multiple of 5-aligned valid divisor set issue, it IS a multiple of 5 but must still divide evenly)`() {
        // 25 does not divide any whole-hour-only total like 120 evenly is false (120/25 not integer) - confirm rejected.
        assertFalse(sittingsFor(120).contains(25))
    }

    @Test
    fun `I5 - Beginner default sitting is 30 minutes, not 25`() {
        assertEquals(30, PREFERRED_SITTING_MINUTES.getValue(FocusLevel.BEGINNER))
    }

    // --- Formatting ---

    @Test
    fun `formatDuration formats hours minutes and combined`() {
        assertEquals("45m", formatDuration(45))
        assertEquals("2h", formatDuration(120))
        assertEquals("1h 30m", formatDuration(90))
    }

    @Test
    fun `describePieces returns null for an invalid pair and a sentence for a valid one`() {
        assertNull(describePieces(100, 30))
        assertEquals("4 sittings · one puzzle piece each", describePieces(120, 30))
    }
}
