package com.artoffocus.app.domain.friends

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupsTest {

    @Test
    fun `generateInviteCode produces codes of the expected length from the safe alphabet`() {
        val ambiguous = setOf('0', 'O', '1', 'I', 'L')
        repeat(200) {
            val code = generateInviteCode()
            assertEquals(INVITE_CODE_LENGTH, code.length)
            assertTrue(code.none { it in ambiguous })
            assertTrue(isValidInviteCode(code))
        }
    }

    @Test
    fun `generateInviteCode is not trivially repetitive`() {
        val codes = (1..200).map { generateInviteCode() }.toSet()
        assertTrue(codes.size > 190)
    }

    @Test
    fun `formatInviteCode splits at the midpoint and normaliseInviteCode reverses it`() {
        assertEquals("ABCD-2345", formatInviteCode("ABCD2345"))
        assertEquals("ABCD2345", normaliseInviteCode(formatInviteCode("ABCD2345")))
    }

    @Test
    fun `normaliseInviteCode tolerates case whitespace and stray hyphens`() {
        assertEquals("ABCD2345", normaliseInviteCode(" abcd-2345 "))
        assertEquals("ABCD2345", normaliseInviteCode("ab cd 23 45"))
    }

    @Test
    fun `isValidInviteCode rejects wrong length and characters outside the safe alphabet`() {
        assertFalse(isValidInviteCode(""))
        assertFalse(isValidInviteCode("ABCD234"))
        assertFalse(isValidInviteCode("ABCD23456"))
        assertFalse(isValidInviteCode("ABCD012O")) // 0 and O are excluded from CODE_ALPHABET
        assertFalse(isValidInviteCode("ABCD-!@#\$"))
        assertTrue(isValidInviteCode("ABCD2345"))
    }

    @Test
    fun `groupNameProblem flags empty and too-long names`() {
        assertEquals(GroupNameProblem.EMPTY, groupNameProblem(""))
        assertEquals(GroupNameProblem.EMPTY, groupNameProblem("   "))
        assertEquals(GroupNameProblem.TOO_LONG, groupNameProblem("x".repeat(41)))
        assertNull(groupNameProblem("x".repeat(40)))
        assertNull(groupNameProblem("Morning Study"))
    }

    @Test
    fun `describeGroupNameProblem has plain language for every problem`() {
        val dbVocabulary = Regex("constraint|violation|pg_|sqlstate", RegexOption.IGNORE_CASE)
        for (problem in GroupNameProblem.entries) {
            val message = describeGroupNameProblem(problem)
            assertTrue(message.isNotBlank())
            assertTrue(message.endsWith(".") || message.endsWith("!"))
            assertFalse(dbVocabulary.containsMatchIn(message))
        }
    }

    @Test
    fun `rankStandings delegates to rankFriends for both sort measures`() {
        val alice = Friend("alice", "Alice", focusMinutes = 600, currentStreak = 3)
        val bob = Friend("bob", "Bob", focusMinutes = 600, currentStreak = 10)
        val members = listOf(alice, bob)

        assertEquals(rankFriends(members, SortBy.HOURS), rankStandings(members, SortBy.HOURS))
        assertEquals(rankFriends(members, SortBy.STREAK), rankStandings(members, SortBy.STREAK))
    }

    @Test
    fun `rankStandings on an empty group returns an empty list`() {
        assertEquals(emptyList<Friend>(), rankStandings(emptyList(), SortBy.HOURS))
    }
}
