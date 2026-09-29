package com.artoffocus.app.domain.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The advice shown when a phone's battery manager is holding notifications back.
 *
 * Worth testing because the previous version was hardcoded for one phone and silently wrong on
 * every other: a Xiaomi user was told to look for a Vivo menu that does not exist. The rules that
 * matter here are that a recognised phone gets its *own* wording, and that an unrecognised one
 * still gets something true rather than nothing.
 */
class OemGuidanceTest {

    @Test
    fun each_known_manufacturer_is_recognised() {
        assertEquals(PhoneFamily.XIAOMI, phoneFamily("Xiaomi"))
        assertEquals(PhoneFamily.OPPO, phoneFamily("OPPO"))
        assertEquals(PhoneFamily.VIVO, phoneFamily("vivo"))
        assertEquals(PhoneFamily.SAMSUNG, phoneFamily("samsung"))
        assertEquals(PhoneFamily.HUAWEI, phoneFamily("HUAWEI"))
        assertEquals(PhoneFamily.ONEPLUS, phoneFamily("OnePlus"))
    }

    @Test
    fun sub_brands_map_to_the_skin_they_actually_run() {
        // Build.MANUFACTURER is vendor-set and inconsistent: these are the same skins under
        // different brand names, and treating them as unknown would show generic advice to a
        // large share of users.
        assertEquals(PhoneFamily.XIAOMI, phoneFamily("Redmi"))
        assertEquals(PhoneFamily.XIAOMI, phoneFamily("POCO"))
        assertEquals(PhoneFamily.OPPO, phoneFamily("realme"))
        assertEquals(PhoneFamily.VIVO, phoneFamily("iQOO"))
        assertEquals(PhoneFamily.HUAWEI, phoneFamily("HONOR"))
    }

    @Test
    fun oneplus_is_not_swallowed_by_oppo() {
        // OnePlus runs ColorOS but labels its menus differently, and the checks are ordered so
        // the more specific match wins. Easy to break by reordering the when.
        assertEquals(PhoneFamily.ONEPLUS, phoneFamily("OnePlus"))
    }

    @Test
    fun an_unknown_or_blank_manufacturer_is_other() {
        assertEquals(PhoneFamily.OTHER, phoneFamily("Fairphone"))
        assertEquals(PhoneFamily.OTHER, phoneFamily(""))
        assertEquals(PhoneFamily.OTHER, phoneFamily("   "))
    }

    @Test
    fun every_phone_gets_at_least_one_actionable_step() {
        // Including the ones we do not recognise: empty advice would leave a user with a real
        // problem and nothing to try.
        val manufacturers = listOf("Xiaomi", "OPPO", "vivo", "samsung", "HUAWEI", "OnePlus", "Fairphone", "")
        manufacturers.forEach { name ->
            val steps = batterySteps(name)
            assertTrue("no steps for '$name'", steps.isNotEmpty())
            assertTrue("blank step for '$name'", steps.all { it.isNotBlank() })
        }
    }

    @Test
    fun a_recognised_phone_gets_advice_written_for_it() {
        // The bug being guarded: Vivo's steps must not be what a Xiaomi user sees.
        assertFalse(batterySteps("Xiaomi") == batterySteps("vivo"))
        assertTrue(batterySteps("samsung").any { it.contains("Sleeping apps") })
        assertTrue(batterySteps("vivo").any { it.contains("High background power consumption") })
    }

    @Test
    fun the_explanation_names_a_phone_it_recognises() {
        // A user who sees their own brand named believes the advice applies to them; generic
        // "your phone" wording reads as boilerplate and gets skipped.
        assertTrue(batteryExplanation("Xiaomi").contains("Xiaomi"))
        assertTrue(batteryExplanation("iQOO").contains("iQOO"))
        assertTrue(batteryExplanation("samsung").contains("Samsung"))
    }

    @Test
    fun the_explanation_for_an_unknown_phone_is_still_true_and_not_empty() {
        val text = batteryExplanation("Fairphone")
        assertTrue(text.isNotBlank())
        // It must not invent a brand name it does not know.
        assertFalse(text.contains("Fairphone"))
    }
}
