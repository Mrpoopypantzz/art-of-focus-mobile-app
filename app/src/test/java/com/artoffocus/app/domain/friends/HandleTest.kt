package com.artoffocus.app.domain.friends

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandleTest {

    @Test
    fun `normaliseHandle strips leading at signs and lowercases`() {
        assertEquals("jamie", normaliseHandle("@@Jamie"))
    }

    @Test
    fun `handleProblem flags empty short long bad characters and reserved names`() {
        assertEquals(HandleProblem.EMPTY, handleProblem("   "))
        assertEquals(HandleProblem.TOO_SHORT, handleProblem("ab"))
        assertEquals(HandleProblem.TOO_LONG, handleProblem("a".repeat(21)))
        assertEquals(HandleProblem.BAD_CHARACTERS, handleProblem("jam-ie"))
        assertEquals(HandleProblem.RESERVED, handleProblem("admin"))
        assertNull(handleProblem("jamie_01"))
    }

    @Test
    fun `formatHandle prefixes with an at sign`() {
        assertEquals("@jamie", formatHandle("jamie"))
    }

    @Test
    fun `isValidHandle agrees with handleProblem`() {
        assertTrue(isValidHandle("jamie_01"))
        assertTrue(isValidHandle("a".repeat(MIN_HANDLE)))
        assertTrue(isValidHandle("a".repeat(MAX_HANDLE)))
        assertFalse(isValidHandle("ab"))
        assertFalse(isValidHandle("admin"))
        assertFalse(isValidHandle("jam-ie"))
    }

    @Test
    fun `describeHandleProblem has plain language for every possible problem`() {
        val dbVocabulary = Regex("constraint|violation|pg_|sqlstate", RegexOption.IGNORE_CASE)
        for (problem in HandleProblem.entries) {
            val message = describeHandleProblem(problem)
            assertTrue(message.isNotBlank())
            assertTrue(message.endsWith(".") || message.endsWith("!"))
            assertFalse(dbVocabulary.containsMatchIn(message))
        }
    }
}
