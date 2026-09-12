package com.artoffocus.app.domain.focus

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every countdown in the app now states what it counts down from. These pin the shape of that
 * text, and pin the cancel countdown's total to its constant — it used to be a bare literal
 * repeated in three places, which is exactly how the number and the prose drift apart.
 */
class CountdownFormatTest {

    @Test
    fun `a seconds countdown names its total`() {
        assertEquals("3s of 5", formatCountdownOf(3, 5))
    }

    @Test
    fun `a countdown at zero still names its total`() {
        // The button swaps to its confirm label at zero, but the formatter must not produce
        // something malformed if it is ever rendered at the boundary.
        assertEquals("0s of 5", formatCountdownOf(0, 5))
    }

    @Test
    fun `the cancel countdown's total comes from the constant, not a literal`() {
        assertEquals("5s of 5", formatCountdownOf(CANCEL_COUNTDOWN_S, CANCEL_COUNTDOWN_S))
    }

    @Test
    fun `the auto-continue and rest-lock totals are whole seconds`() {
        // Both are 60_000ms today. They are independent constants and are read separately;
        // this asserts the conversion, not that they are equal to each other.
        assertEquals(60, (AUTO_CONTINUE_MS / 1000).toInt())
        assertEquals(60, (CONTINUE_LOCK_MS / 1000).toInt())
    }

    @Test
    fun `a clock countdown names its total in the same mm ss shape`() {
        assertEquals("04:12 of 15:00", formatElapsedOf(4 * 60_000L + 12_000L, 15 * 60_000L))
    }

    @Test
    fun `a clock countdown past an hour keeps both sides in the long shape`() {
        assertEquals("1:00:00 of 2:00:00", formatElapsedOf(3_600_000L, 7_200_000L))
    }

    @Test
    fun `a finished clock countdown reads as zero of the total`() {
        assertEquals("00:00 of 10:00", formatElapsedOf(0L, 10 * 60_000L))
    }
}
