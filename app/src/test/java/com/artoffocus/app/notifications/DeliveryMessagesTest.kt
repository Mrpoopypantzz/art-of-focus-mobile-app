package com.artoffocus.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These messages exist because the app used to fail silently. If one of them ever degrades into
 * a class name or an empty string, the user is back to guessing — so the wording is pinned.
 */
class DeliveryMessagesTest {

    @Test
    fun `a clean send says so plainly`() {
        val message = describeDelivery(AppNotifier.PostResult.Posted, unrestricted = true)
        assertEquals("Sent — check your notification shade.", message)
    }

    @Test
    fun `a send on a battery-restricted phone admits it may not arrive`() {
        // Posting succeeded, so claiming plain success would be misleading on a phone that
        // freezes background apps — the case this whole feature was built for.
        val message = describeDelivery(AppNotifier.PostResult.Posted, unrestricted = false)
        assertTrue(message.startsWith("Sent, but"))
        assertTrue(message.contains("hold it back"))
    }

    @Test
    fun `each blocked reason names a different cause`() {
        val permission = describeDelivery(AppNotifier.PostResult.PermissionMissing, true)
        val app = describeDelivery(AppNotifier.PostResult.AppBlocked, true)
        val channel = describeDelivery(AppNotifier.PostResult.ChannelBlocked, true)

        // The whole point is telling these three apart; identical or overlapping text would
        // leave the user exactly as stuck as the old silence did.
        assertEquals(3, setOf(permission, app, channel).size)
        assertTrue(permission.contains("permission"))
        assertTrue(app.contains("turned off for Art of Focus"))
        assertTrue(channel.contains("Rest complete"))
    }

    @Test
    fun `a platform failure is reported without leaking the exception`() {
        val message = describeDelivery(
            AppNotifier.PostResult.Failed("java.lang.SecurityException: boom"),
            unrestricted = true,
        )
        assertFalse(message.contains("java.lang"))
        assertFalse(message.contains("Exception"))
        assertTrue(message.isNotBlank())
    }

    @Test
    fun `no message is empty, and none leaks a type name`() {
        val results = listOf(
            AppNotifier.PostResult.Posted,
            AppNotifier.PostResult.PermissionMissing,
            AppNotifier.PostResult.AppBlocked,
            AppNotifier.PostResult.ChannelBlocked,
            AppNotifier.PostResult.Failed("x"),
        )
        for (result in results) {
            for (unrestricted in listOf(true, false)) {
                val message = describeDelivery(result, unrestricted)
                assertTrue(message.isNotBlank())
                assertFalse(message.contains("PostResult"))
                assertFalse(message.contains("kotlin."))
            }
        }
    }
}
