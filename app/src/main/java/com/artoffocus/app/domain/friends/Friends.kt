package com.artoffocus.app.domain.friends

/** I13 — exactly the four fields that are ever public: handle, display name, focus hours, current streak. */
data class Friend(
    val handle: String,
    val displayName: String,
    val focusMinutes: Int,
    val currentStreak: Int,
)

data class PendingRequest(
    val id: String,
    val handle: String,
    val displayName: String,
    val outgoing: Boolean,
)

enum class SortBy { HOURS, STREAK }

fun friendMeasure(friend: Friend, sortBy: SortBy): Int = when (sortBy) {
    SortBy.HOURS -> friend.focusMinutes
    SortBy.STREAK -> friend.currentStreak
}

private fun otherMeasure(friend: Friend, sortBy: SortBy): Int = when (sortBy) {
    SortBy.HOURS -> friend.currentStreak
    SortBy.STREAK -> friend.focusMinutes
}

/**
 * Primary sort descending by the chosen measure, tie-broken by the other measure descending,
 * then by handle ascending for full determinism (a group board and the friends board must
 * order identically, since both delegate here).
 */
fun rankFriends(friends: List<Friend>, sortBy: SortBy): List<Friend> = friends.sortedWith(
    compareByDescending<Friend> { friendMeasure(it, sortBy) }
        .thenByDescending { otherMeasure(it, sortBy) }
        .thenBy { it.handle },
)

fun positionOf(handle: String, ranked: List<Friend>): Int? {
    val index = ranked.indexOfFirst { it.handle == handle }
    return if (index == -1) null else index + 1
}
