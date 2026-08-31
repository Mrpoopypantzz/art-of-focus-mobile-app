package com.artoffocus.app.domain.friends

import org.junit.Assert.assertEquals
import org.junit.Test

class FriendsTest {

    private val alice = Friend("alice", "Alice", focusMinutes = 600, currentStreak = 3)
    private val bob = Friend("bob", "Bob", focusMinutes = 600, currentStreak = 10)
    private val carol = Friend("carol", "Carol", focusMinutes = 300, currentStreak = 10)

    @Test
    fun `rankFriends sorts by the chosen measure descending`() {
        // alice and bob tie on focusMinutes (600); bob's higher streak wins the tiebreak.
        val ranked = rankFriends(listOf(alice, bob, carol), SortBy.HOURS)
        assertEquals(listOf("bob", "alice", "carol"), ranked.map { it.handle })
    }

    @Test
    fun `rankFriends ties on the primary measure are broken by the other measure descending`() {
        // alice and bob tie on focusMinutes (600); bob has the higher streak so ranks first.
        val ranked = rankFriends(listOf(alice, bob), SortBy.HOURS)
        assertEquals(listOf("bob", "alice"), ranked.map { it.handle })
    }

    @Test
    fun `rankFriends final tiebreak is handle ascending for determinism`() {
        val a = Friend("zed", "Zed", 100, 5)
        val b = Friend("amy", "Amy", 100, 5)
        val ranked = rankFriends(listOf(a, b), SortBy.HOURS)
        assertEquals(listOf("amy", "zed"), ranked.map { it.handle })
    }

    @Test
    fun `positionOf returns a 1-based rank, or null if not present`() {
        val ranked = rankFriends(listOf(alice, bob, carol), SortBy.STREAK)
        assertEquals(1, positionOf("bob", ranked)) // bob and carol tie at streak=10, bob wins hours tiebreak
        assertEquals(3, positionOf("alice", ranked))
        assertEquals(null, positionOf("dave", ranked))
    }
}
