package com.artoffocus.app.domain.achievements

import com.artoffocus.app.domain.profile.Statistics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsTest {

    private fun stats(count: Int = 0, focusMinutes: Int = 0, currentStreak: Int = 0, longestStreak: Int = 0) =
        Statistics(
            count = count,
            focusMinutes = focusMinutes,
            longestMinutes = 0,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            averageMinutes = 0,
            bestMonth = null,
        )

    // --- I15: an earned achievement is never taken away ---

    @Test
    fun `I15 - streak achievements read longestStreak, never currentStreak`() {
        // Streak lapsed (currentStreak=0) but it happened (longestStreak=30) — still earned.
        val s = stats(currentStreak = 0, longestStreak = 30)
        val achievement = ACHIEVEMENTS.first { it.kind == AchievementKind.STREAK && it.threshold == 30 }
        assertTrue(isEarned(s, achievement))
    }

    @Test
    fun `I15 - a high currentStreak alone does not earn a streak achievement it never sustained`() {
        // currentStreak is high but longestStreak (the real historical max) is lower.
        val s = stats(currentStreak = 30, longestStreak = 5)
        val achievement = ACHIEVEMENTS.first { it.kind == AchievementKind.STREAK && it.threshold == 7 }
        assertFalse(isEarned(s, achievement))
    }

    @Test
    fun `I15 - achievements are retroactive, derived purely from current statistics`() {
        val before = stats(count = 0)
        val after = stats(count = 1)
        val achievement = ACHIEVEMENTS.first { it.kind == AchievementKind.PAINTINGS && it.threshold == 1 }
        assertFalse(isEarned(before, achievement))
        assertTrue(isEarned(after, achievement))
    }

    @Test
    fun `hours achievements floor focusMinutes to whole hours`() {
        val s = stats(focusMinutes = 119) // 1h59m, not yet a full 2 hours
        val achievement = ACHIEVEMENTS.first { it.kind == AchievementKind.HOURS && it.threshold == 1 }
        assertTrue(isEarned(s, achievement))
        val twoHourAchievement = Achievement(AchievementKind.HOURS, 2, "test")
        assertFalse(isEarned(s, twoHourAchievement))
    }

    @Test
    fun `describeEarned counts earned out of the full fixed achievement list`() {
        val s = stats(count = 1, focusMinutes = 0, longestStreak = 0)
        assertEquals("1 of ${ACHIEVEMENTS.size} earned", describeEarned(s))
    }

    @Test
    fun `nextAchievement picks the unearned achievement with the highest completion fraction`() {
        // 9 of 10 hours (90%) should beat 1 of 5 paintings (20%).
        val s = stats(focusMinutes = 9 * 60, count = 1)
        val next = nextAchievement(s)
        assertEquals(AchievementKind.HOURS, next?.kind)
        assertEquals(10, next?.threshold)
    }
}
