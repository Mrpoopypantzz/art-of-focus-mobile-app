package com.artoffocus.app.domain.museum

import com.artoffocus.app.domain.focus.Painting
import org.junit.Assert.assertEquals
import org.junit.Test

class MuseumTest {

    private fun painting(startedAt: Long, completedAt: Long, totalMinutes: Int = 120, seed: Long = 1L) =
        CompletedPainting(
            painting = Painting("p0", "Test", seed),
            totalMinutes = totalMinutes,
            sittingMinutes = 30,
            pieces = 4,
            startedAt = startedAt,
            completedAt = completedAt,
        )

    @Test
    fun `paintingKey combines startedAt and seed for idempotency`() {
        val p = painting(1000L, 2000L, seed = 42L)
        assertEquals("1000:42", paintingKey(p))
    }

    @Test
    fun `museumStats sums focus minutes and finds the longest`() {
        val paintings = listOf(painting(0, 1, 60), painting(0, 1, 180), painting(0, 1, 90))
        val stats = museumStats(paintings)
        assertEquals(3, stats.count)
        assertEquals(330, stats.focusMinutes)
        assertEquals(180, stats.longestMinutes)
    }

    @Test
    fun `museumStats is all zero for an empty museum`() {
        val stats = museumStats(emptyList())
        assertEquals(0, stats.count)
        assertEquals(0, stats.focusMinutes)
        assertEquals(0, stats.longestMinutes)
    }

    @Test
    fun `byMostRecent sorts newest first without mutating input order semantics`() {
        val a = painting(0, 1000L)
        val b = painting(0, 3000L)
        val c = painting(0, 2000L)
        val sorted = byMostRecent(listOf(a, b, c))
        assertEquals(listOf(b, c, a), sorted)
    }

    @Test
    fun `formatFocusSpan formats hours and minutes`() {
        assertEquals("4h", formatFocusSpan(240))
        assertEquals("1h 30m", formatFocusSpan(90))
    }
}
