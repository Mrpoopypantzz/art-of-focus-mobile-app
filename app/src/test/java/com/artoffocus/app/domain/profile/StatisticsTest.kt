package com.artoffocus.app.domain.profile

import com.artoffocus.app.domain.focus.Painting
import com.artoffocus.app.domain.museum.CompletedPainting
import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsTest {

    private val dayMs = 86_400_000L
    // A fixed local-midnight-aligned reference instant, used only for day-difference math
    // (multiples of a full day apart), which is stable regardless of timezone/DST specifics.
    private val today = dayNumber(System.currentTimeMillis()) * dayMs + dayMs / 2

    private fun painting(completedAt: Long, totalMinutes: Int = 60) = CompletedPainting(
        painting = Painting("p0", "Test", 1L),
        totalMinutes = totalMinutes,
        sittingMinutes = 30,
        pieces = 2,
        startedAt = completedAt - totalMinutes * 60_000L,
        completedAt = completedAt,
    )

    @Test
    fun `focusStreaks is zero-zero for an empty museum`() {
        val streaks = focusStreaks(emptyList(), today)
        assertEquals(0, streaks.currentStreak)
        assertEquals(0, streaks.longestStreak)
    }

    @Test
    fun `focusStreaks dedupes multiple completions on the same day`() {
        val paintings = listOf(painting(today), painting(today + 1000L))
        val streaks = focusStreaks(paintings, today)
        assertEquals(1, streaks.currentStreak)
        assertEquals(1, streaks.longestStreak)
    }

    @Test
    fun `focusStreaks counts consecutive days as a run`() {
        val paintings = listOf(
            painting(today - 2 * dayMs),
            painting(today - dayMs),
            painting(today),
        )
        val streaks = focusStreaks(paintings, today)
        assertEquals(3, streaks.currentStreak)
        assertEquals(3, streaks.longestStreak)
    }

    @Test
    fun `current streak survives if last completion was yesterday, today is not over yet`() {
        val paintings = listOf(painting(today - dayMs - dayMs), painting(today - dayMs))
        val streaks = focusStreaks(paintings, today)
        assertEquals(2, streaks.currentStreak)
    }

    @Test
    fun `current streak is zero once more than a day has lapsed, but longest is preserved`() {
        val paintings = listOf(
            painting(today - 5 * dayMs),
            painting(today - 4 * dayMs),
            painting(today - 3 * dayMs), // 3-day run, then a gap
        )
        val streaks = focusStreaks(paintings, today)
        assertEquals(0, streaks.currentStreak)
        assertEquals(3, streaks.longestStreak)
    }

    @Test
    fun `statistics averageMinutes is zero, never NaN, for an empty museum`() {
        val stats = statistics(emptyList(), today)
        assertEquals(0, stats.averageMinutes)
    }

    @Test
    fun `statistics averageMinutes rounds focusMinutes over count`() {
        val paintings = listOf(painting(today, 60), painting(today, 90))
        val stats = statistics(paintings, today)
        assertEquals(75, stats.averageMinutes)
    }
}
