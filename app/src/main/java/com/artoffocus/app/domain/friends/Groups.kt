package com.artoffocus.app.domain.friends

import java.security.SecureRandom

const val MAX_GROUP_NAME = 40
const val INVITE_CODE_LENGTH = 8

/** Excludes 0/O and 1/I/L — ambiguous when read aloud or handwritten. ~850 billion combinations. */
private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

data class Group(
    val id: String,
    val name: String,
    val inviteCode: String,
    val ownerId: String,
    val memberCount: Int,
)

private val secureRandom = SecureRandom()

/** No directory, no search — a group is only reachable via its invite code. */
fun generateInviteCode(): String =
    (1..INVITE_CODE_LENGTH).map { CODE_ALPHABET[secureRandom.nextInt(CODE_ALPHABET.length)] }.joinToString("")

/** Display-only grouping, e.g. "ABCD-EF12" — the stored code has no separator. */
fun formatInviteCode(code: String): String {
    val mid = code.length / 2
    return "${code.substring(0, mid)}-${code.substring(mid)}"
}

/** Group standings and the friends board must order identically — delegates to [rankFriends]. */
fun rankStandings(members: List<Friend>, sortBy: SortBy): List<Friend> = rankFriends(members, sortBy)
