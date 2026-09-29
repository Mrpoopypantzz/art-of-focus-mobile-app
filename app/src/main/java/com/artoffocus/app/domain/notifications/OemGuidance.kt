package com.artoffocus.app.domain.notifications

/**
 * What to tell the user when their phone's own battery manager is holding notifications back.
 *
 * This exists because the advice in Profile was hardcoded for one phone (Vivo/iQOO), on which
 * the problem was first reported. That was the wrong shape of fix: every major Android skin
 * has an aggressive app-killer, each buried under a different menu name, and a Xiaomi user
 * being told to look for "High background power consumption" simply finds nothing.
 *
 * Kept as a pure function of the manufacturer string so it is unit-testable, following
 * [describeDelivery] in `notifications/DeliveryMessages.kt`. The Android-side caller passes
 * `Build.MANUFACTURER`.
 *
 * The menu paths are necessarily approximate — OEMs rename them between OS versions, and no
 * API exposes them. They are written to name the *concept* to look for, so they stay useful
 * when a label shifts. An unrecognised manufacturer gets generic advice that is true of every
 * Android phone, never an empty string.
 */

/** A phone family with its own battery-manager quirks, plus [OTHER] for everything else. */
enum class PhoneFamily { XIAOMI, OPPO, VIVO, SAMSUNG, HUAWEI, ONEPLUS, OTHER }

/**
 * Maps a `Build.MANUFACTURER` value onto a [PhoneFamily].
 *
 * Matches case-insensitively on a contained substring rather than equality, because the field
 * is set by the vendor and is inconsistent in practice: "Xiaomi", "xiaomi", "Redmi" and
 * "POCO" are all the same skin (MIUI/HyperOS), and Realme ships ColorOS like Oppo.
 */
fun phoneFamily(manufacturer: String): PhoneFamily {
    val name = manufacturer.trim().lowercase()
    return when {
        name.isEmpty() -> PhoneFamily.OTHER
        // MIUI / HyperOS, under three brand names.
        name.contains("xiaomi") || name.contains("redmi") || name.contains("poco") -> PhoneFamily.XIAOMI
        // OnePlus is checked before Oppo: it runs ColorOS now but still labels its menus its own way.
        name.contains("oneplus") -> PhoneFamily.ONEPLUS
        name.contains("oppo") || name.contains("realme") -> PhoneFamily.OPPO
        name.contains("vivo") || name.contains("iqoo") -> PhoneFamily.VIVO
        name.contains("samsung") -> PhoneFamily.SAMSUNG
        name.contains("huawei") || name.contains("honor") -> PhoneFamily.HUAWEI
        else -> PhoneFamily.OTHER
    }
}

/**
 * The steps to offer, most effective first. Never empty: a phone we do not recognise still
 * gets the two things that are true everywhere (unrestricted battery use, and keeping the app
 * out of whatever "clean up" sweep the vendor runs).
 */
fun batterySteps(manufacturer: String): List<String> = when (phoneFamily(manufacturer)) {
    PhoneFamily.XIAOMI -> listOf(
        "Settings › Apps › Art of Focus › Battery saver › No restrictions",
        "Settings › Apps › Art of Focus › Autostart › on",
        "In Recents, pull the app's card down to lock it",
    )
    PhoneFamily.OPPO -> listOf(
        "Settings › Battery › Art of Focus › Allow background activity",
        "Settings › Apps › Art of Focus › Allow auto-launch",
        "In Recents, lock the app's card",
    )
    PhoneFamily.VIVO -> listOf(
        "Settings › Battery › High background power consumption › allow Art of Focus",
        "Settings › Apps › Art of Focus › Autostart › on",
        "In Recents, lock the app's card so it isn't swept away",
    )
    PhoneFamily.ONEPLUS -> listOf(
        "Settings › Battery › Battery optimization › Art of Focus › Don't optimize",
        "Settings › Battery › More settings › turn off Deep optimization",
        "In Recents, lock the app's card",
    )
    PhoneFamily.SAMSUNG -> listOf(
        "Settings › Battery › Background usage limits › remove Art of Focus from Sleeping apps",
        "Settings › Apps › Art of Focus › Battery › Unrestricted",
    )
    PhoneFamily.HUAWEI -> listOf(
        "Settings › Battery › App launch › Art of Focus › Manage manually, all three on",
        "In Recents, lock the app's card",
    )
    PhoneFamily.OTHER -> listOf(
        "Settings › Apps › Art of Focus › Battery › Unrestricted",
        "If your phone has a task cleaner or autostart list, allow Art of Focus there",
    )
}

/**
 * The one-line explanation above [batterySteps].
 *
 * Names the manufacturer when we recognise it, because "your phone" reads as boilerplate and
 * gets skipped, whereas a user who sees their own brand named believes the advice applies.
 */
fun batteryExplanation(manufacturer: String): String {
    val brand = when (phoneFamily(manufacturer)) {
        PhoneFamily.XIAOMI -> "Xiaomi phones"
        PhoneFamily.OPPO -> "Oppo and Realme phones"
        PhoneFamily.VIVO -> "Vivo and iQOO phones"
        PhoneFamily.ONEPLUS -> "OnePlus phones"
        PhoneFamily.SAMSUNG -> "Samsung phones"
        PhoneFamily.HUAWEI -> "Huawei and Honor phones"
        PhoneFamily.OTHER -> null
    }
    return if (brand == null) {
        "Your phone can stop a background app from posting notifications. If a rest notification never arrives, these settings are why."
    } else {
        "$brand close background apps aggressively to save battery, which can hold a notification back. These settings switch that off for Art of Focus."
    }
}
