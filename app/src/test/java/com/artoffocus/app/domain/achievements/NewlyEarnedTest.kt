package com.artoffocus.app.domain.achievements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule behind the achievement notification. Before this existed, `notifyAchievement` had no
 * caller at all and the "Achievement earned" toggle controlled nothing — so the thing most worth
 * pinning is that a notification fires exactly once, for exactly what was just earned.
 */
class NewlyEarnedTest {

    private val first = ACHIEVEMENTS[0]
    private val second = ACHIEVEMENTS[1]
    private val third = ACHIEVEMENTS[2]

    @Test
    fun `an achievement earned for the first time is reported`() {
        assertEquals(listOf(first), newlyEarned(before = emptySet(), after = listOf(first)))
    }

    @Test
    fun `an achievement already held is not reported again`() {
        // The notification must not repeat on every subsequent painting.
        assertTrue(newlyEarned(before = setOf(first), after = listOf(first)).isEmpty())
    }

    @Test
    fun `only the new ones are reported when several are held`() {
        val result = newlyEarned(before = setOf(first), after = listOf(first, second, third))
        assertEquals(listOf(second, third), result)
    }

    @Test
    fun `two earned at once are both reported`() {
        // One painting can cross two thresholds — a streak and an hours total, say — and the
        // user should hear about both.
        val result = newlyEarned(before = emptySet(), after = listOf(first, second))
        assertEquals(2, result.size)
    }

    @Test
    fun `nothing earned means nothing reported`() {
        assertTrue(newlyEarned(before = setOf(first, second), after = listOf(first, second)).isEmpty())
    }

    @Test
    fun `I15 - an achievement missing from after is never treated as news`() {
        // Statistics should never regress, but if one ever did, the honest response is silence:
        // an earned achievement is never removed, and it must certainly never re-notify.
        assertTrue(newlyEarned(before = setOf(first, second), after = listOf(first)).isEmpty())
    }
}
