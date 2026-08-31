package com.artoffocus.app.domain.profile

import com.artoffocus.app.domain.focus.FocusLevel

const val MAX_USERNAME = 40
const val MAX_GOAL = 120

data class Profile(
    val username: String = "",
    val goal: String = "",
    val level: FocusLevel = FocusLevel.DEFAULT,
)

val EMPTY_PROFILE = Profile()

/** Collapse whitespace runs, trim, slice to max — applied on save, not on every keystroke. */
private fun tidy(raw: String, max: Int): String =
    raw.trim().replace(Regex("\\s+"), " ").take(max)

fun normaliseUsername(raw: String): String = tidy(raw, MAX_USERNAME)
fun normaliseGoal(raw: String): String = tidy(raw, MAX_GOAL)

/** I13 — this is the display name shown publicly; falls back to a neutral default, never blank. */
fun displayName(profile: Profile): String = profile.username.ifEmpty { "The Artist" }
