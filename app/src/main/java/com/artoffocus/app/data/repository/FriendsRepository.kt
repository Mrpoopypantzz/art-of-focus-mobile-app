package com.artoffocus.app.data.repository

import com.artoffocus.app.domain.friends.Friend
import com.artoffocus.app.domain.friends.PendingRequest
import com.artoffocus.app.domain.friends.normaliseHandle
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A port of the web app's friendsClient.ts — same RPC names, same table/view names, same
 * least-first ordering on friendships, so this works against the existing schema unchanged.
 *
 * Two return shapes, matching web: actions that mutate state return an error message or null
 * on success (`sendRequest`, `acceptRequest`, `removeFriendship`, `claimHandle`); reads fail
 * quiet to an empty list, since a background load has no error channel worth surfacing.
 */
class FriendsRepository(private val supabase: SupabaseClient) {

    data class HandleMatch(val id: String, val handle: String, val displayName: String)

    @Serializable
    private data class HandleMatchRow(val id: String, val handle: String, @SerialName("display_name") val displayName: String? = null)

    @Serializable
    private data class FriendRow(
        val handle: String? = null,
        @SerialName("display_name") val displayName: String? = null,
        @SerialName("focus_minutes") val focusMinutes: Int? = null,
        @SerialName("current_streak") val currentStreak: Int? = null,
    )

    @Serializable
    private data class PendingRequestRow(
        @SerialName("other_id") val otherId: String,
        val handle: String? = null,
        @SerialName("display_name") val displayName: String? = null,
        val outgoing: Boolean = false,
    )

    @Serializable
    private data class WantedArg(val wanted: String)

    @Serializable
    private data class FriendshipRow(
        @SerialName("user_a") val userA: String,
        @SerialName("user_b") val userB: String,
        @SerialName("requester_id") val requesterId: String? = null,
        val status: String? = null,
    )

    /** `friendships` PK requires user_a < user_b — same least-first rule the DB check enforces. */
    private fun orderPair(a: String, b: String): Pair<String, String> = if (a < b) a to b else b to a

    suspend fun searchHandle(raw: String): Pair<HandleMatch?, String?> = runCatching {
        val result = supabase.postgrest.rpc("find_by_handle", WantedArg(normaliseHandle(raw)))
        val match = result.decodeList<HandleMatchRow>().firstOrNull()
            ?.let { HandleMatch(it.id, it.handle, it.displayName ?: "") }
        match to null
    }.getOrElse { null to "Could not search right now." }

    suspend fun claimHandle(raw: String): String? {
        val status = runCatching {
            supabase.postgrest.rpc("claim_handle", WantedArg(normaliseHandle(raw))).decodeAs<String>()
        }.getOrElse { return "Could not claim that handle right now." }

        return when (status) {
            "ok" -> null
            "taken" -> "That handle is already taken."
            "invalid" -> "That doesn't look like a valid handle."
            "not_signed_in" -> "Sign in to claim a handle."
            else -> "Could not claim that handle right now."
        }
    }

    suspend fun sendRequest(meId: String, themId: String): String? {
        val (userA, userB) = orderPair(meId, themId)
        return runCatching {
            supabase.from("friendships").insert(
                FriendshipRow(userA = userA, userB = userB, requesterId = meId, status = "pending"),
            )
            null
        }.getOrElse { error ->
            if (error.message?.contains("23505") == true) {
                "You are already connected, or a request is pending."
            } else {
                "Could not send that request right now."
            }
        }
    }

    suspend fun acceptRequest(meId: String, themId: String): String? {
        val (userA, userB) = orderPair(meId, themId)
        return runCatching {
            val result = supabase.from("friendships").update(
                { set("status", "accepted") },
            ) {
                filter {
                    eq("user_a", userA)
                    eq("user_b", userB)
                }
                select()
            }
            if (result.decodeList<FriendshipRow>().isEmpty()) {
                "Only the person who received a request can accept it."
            } else {
                null
            }
        }.getOrElse { "Could not accept that request right now." }
    }

    suspend fun removeFriendship(meId: String, themId: String): String? {
        val (userA, userB) = orderPair(meId, themId)
        return runCatching {
            supabase.from("friendships").delete {
                filter {
                    eq("user_a", userA)
                    eq("user_b", userB)
                }
            }
            null
        }.getOrElse { "Could not remove that connection right now." }
    }

    suspend fun loadFriends(): List<Friend> = runCatching {
        supabase.from("friend_leaderboard")
            .select(Columns.list("handle", "display_name", "focus_minutes", "current_streak"))
            .decodeList<FriendRow>()
            .map { Friend(it.handle ?: "", it.displayName ?: "", it.focusMinutes ?: 0, it.currentStreak ?: 0) }
    }.getOrElse { emptyList() }

    @Serializable
    private data class OwnHandleRow(val handle: String? = null)

    /** RLS allows selecting one's own row — used to restore claim state on app relaunch. */
    suspend fun loadMyHandle(): String? {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return null
        return runCatching {
            supabase.from("profiles")
                .select(Columns.list("handle")) { filter { eq("id", userId) } }
                .decodeList<OwnHandleRow>()
                .firstOrNull()?.handle
        }.getOrNull()
    }

    suspend fun loadPendingRequests(): List<PendingRequest> = runCatching {
        supabase.postgrest.rpc("pending_requests")
            .decodeList<PendingRequestRow>()
            .map { PendingRequest(it.otherId, it.handle ?: "", it.displayName ?: "", it.outgoing) }
    }.getOrElse { emptyList() }
}
