package com.artoffocus.app.domain.friends

const val MIN_HANDLE = 3
const val MAX_HANDLE = 20

private val RESERVED = setOf(
    "admin", "root", "system", "support", "help", "artoffocus", "art_of_focus",
    "official", "staff", "moderator", "me", "you", "null", "undefined", "anonymous", "deleted",
)

private val VALID_HANDLE_CHARS = Regex("^[a-z0-9_]+$")

fun normaliseHandle(raw: String): String = raw.trim().trimStart('@').lowercase()

enum class HandleProblem { EMPTY, TOO_SHORT, TOO_LONG, BAD_CHARACTERS, RESERVED }

/** I13/I14 groundwork — handle search elsewhere is exact-match only; this only validates shape. */
fun handleProblem(raw: String): HandleProblem? {
    val handle = normaliseHandle(raw)
    return when {
        handle.isEmpty() -> HandleProblem.EMPTY
        handle.length < MIN_HANDLE -> HandleProblem.TOO_SHORT
        handle.length > MAX_HANDLE -> HandleProblem.TOO_LONG
        !VALID_HANDLE_CHARS.matches(handle) -> HandleProblem.BAD_CHARACTERS
        handle in RESERVED -> HandleProblem.RESERVED
        else -> null
    }
}

fun formatHandle(handle: String): String = "@$handle"
