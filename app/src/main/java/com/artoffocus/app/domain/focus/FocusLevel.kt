package com.artoffocus.app.domain.focus

/** I4 — Focus Level Sets Defaults, Never Limits. */
enum class FocusLevel {
    BEGINNER, INTERMEDIATE, ADVANCED;

    companion object {
        val DEFAULT: FocusLevel = BEGINNER

        fun fromNameOrDefault(name: String?): FocusLevel =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}

/** Minutes. Never used to restrict which totals/sittings are offered — only the default landing position (I4). */
val DEFAULT_TOTAL_MINUTES: Map<FocusLevel, Int> = mapOf(
    FocusLevel.BEGINNER to 120,
    FocusLevel.INTERMEDIATE to 360,
    FocusLevel.ADVANCED to 720,
)

/** Minutes. A landing preference only — falls back to the nearest valid sitting length for the chosen total. */
val PREFERRED_SITTING_MINUTES: Map<FocusLevel, Int> = mapOf(
    FocusLevel.BEGINNER to 30,
    FocusLevel.INTERMEDIATE to 60,
    FocusLevel.ADVANCED to 90,
)
