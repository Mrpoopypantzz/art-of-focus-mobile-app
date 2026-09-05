package com.artoffocus.app.data.repository

import com.artoffocus.app.domain.friends.Friend
import com.artoffocus.app.domain.friends.Group
import com.artoffocus.app.domain.friends.generateInviteCode
import com.artoffocus.app.domain.friends.normaliseInviteCode
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A port of the web app's groupsClient.ts — same RPC/table names, same retry-on-taken-code behavior. */
class GroupsRepository(private val supabase: SupabaseClient) {

    @Serializable
    private data class GroupRow(
        val id: String,
        val name: String? = null,
        @SerialName("invite_code") val inviteCode: String? = null,
        @SerialName("owner_id") val ownerId: String,
        @SerialName("member_count") val memberCount: Long = 0,
    )

    @Serializable
    private data class StandingRow(
        val handle: String? = null,
        @SerialName("display_name") val displayName: String? = null,
        @SerialName("focus_minutes") val focusMinutes: Int? = null,
        @SerialName("current_streak") val currentStreak: Int? = null,
    )

    @Serializable
    private data class CreateGroupArgs(@SerialName("group_name") val groupName: String, val code: String)

    @Serializable
    private data class JoinGroupArgs(val code: String)

    @Serializable
    private data class GroupStandingsArgs(val gid: String)

    suspend fun loadGroups(): List<Group> = runCatching {
        supabase.postgrest.rpc("my_groups")
            .decodeList<GroupRow>()
            .map { Group(it.id, it.name ?: "", it.inviteCode ?: "", it.ownerId, it.memberCount.toInt()) }
    }.getOrElse { emptyList() }

    suspend fun loadStandings(groupId: String): List<Friend> = runCatching {
        supabase.postgrest.rpc("group_standings", GroupStandingsArgs(gid = groupId))
            .decodeList<StandingRow>()
            .map { Friend(it.handle ?: "", it.displayName ?: "", it.focusMinutes ?: 0, it.currentStreak ?: 0) }
    }.getOrElse { emptyList() }

    /** Retries on a colliding invite code — collisions are rare (~850 billion combinations) but not impossible. */
    suspend fun createGroup(name: String): String? {
        repeat(3) {
            val status = runCatching {
                supabase.postgrest.rpc(
                    "create_group",
                    CreateGroupArgs(groupName = name.trim(), code = generateInviteCode()),
                ).decodeAs<String>()
            }.getOrElse { return "Could not create that group right now." }

            when (status) {
                "ok" -> return null
                "code_taken" -> return@repeat
                "invalid_name" -> return "Give the group a name of 40 characters or fewer."
                "not_signed_in" -> return "Sign in to create a group."
                else -> return "Could not create that group right now."
            }
        }
        return "Could not create that group right now. Please try again."
    }

    suspend fun joinGroup(code: String): String? {
        val status = runCatching {
            supabase.postgrest.rpc("join_group", JoinGroupArgs(code = normaliseInviteCode(code))).decodeAs<String>()
        }.getOrElse { return "Could not join right now." }

        return when (status) {
            "ok" -> null
            "not_found" -> "No group with that code."
            "already_member" -> "You are already in that group."
            "not_signed_in" -> "Sign in to join a group."
            else -> "Could not join right now."
        }
    }

    suspend fun leaveGroup(groupId: String): String? {
        val me = supabase.auth.currentUserOrNull()?.id ?: return "Sign in to leave a group."
        return runCatching {
            supabase.from("group_members").delete {
                filter {
                    eq("group_id", groupId)
                    eq("user_id", me)
                }
            }
            null
        }.getOrElse { "Could not leave that group right now." }
    }
}
