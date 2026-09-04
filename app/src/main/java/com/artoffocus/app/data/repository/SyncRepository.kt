package com.artoffocus.app.data.repository

import com.artoffocus.app.domain.museum.CompletedPainting
import com.artoffocus.app.domain.profile.displayName
import com.artoffocus.app.domain.profile.statistics
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * M6 — pushing local work to the backend. A port of the web app's `sync.ts`.
 *
 * PUSH ONLY, DELIBERATELY, exactly as on web: pulling needs a conflict rule for two devices
 * with divergent museums, and guessing at one risks I12 ("a completed painting is never
 * destroyed"). That decision is deferred, not assumed.
 *
 * NEVER CALLED DURING A SESSION. Sync runs on sign-in and after a painting completes, so a
 * slow or failed push costs nothing — Room is the source of truth (I7) and the work is
 * already safe locally before this is ever reached.
 */
class SyncRepository(
    private val supabase: SupabaseClient,
    private val museumRepository: MuseumRepository,
    private val profileRepository: ProfileRepository,
    private val accountRepository: AccountRepository,
) {

    data class Result(val ok: Boolean, val pushed: Int, val error: String?)

    @Serializable
    private data class ProfileRow(
        val id: String,
        @SerialName("display_name") val displayName: String,
        @SerialName("focus_minutes") val focusMinutes: Int,
        @SerialName("current_streak") val currentStreak: Int,
        @SerialName("updated_at") val updatedAt: String,
    )

    @Serializable
    private data class PaintingRow(
        @SerialName("user_id") val userId: String,
        val seed: Long,
        val title: String,
        @SerialName("total_minutes") val totalMinutes: Int,
        @SerialName("sitting_minutes") val sittingMinutes: Int,
        val pieces: Int,
        @SerialName("started_at") val startedAt: String,
        @SerialName("completed_at") val completedAt: String,
    )

    /**
     * Mirrors the local museum and derived statistics to the backend.
     *
     * Idempotent: paintings upsert on `(user_id, started_at, seed)` — the same identity
     * `paintingKey()` uses locally and the same conflict target the web client uses — so
     * re-syncing cannot duplicate a painting.
     */
    suspend fun pushLocalWork(): Result {
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: return Result(ok = false, pushed = 0, error = "Not signed in.")

        val paintings = museumRepository.allWithLocalIds()
        val stats = statistics(paintings.map { it.second }, System.currentTimeMillis())
        val name = displayName(profileRepository.load())
        val now = System.currentTimeMillis()

        return runCatching {
            supabase.from("profiles").upsert(
                ProfileRow(
                    id = userId,
                    displayName = name,
                    focusMinutes = stats.focusMinutes,
                    currentStreak = stats.currentStreak,
                    updatedAt = iso(now),
                ),
            ) { onConflict = "id" }

            if (paintings.isNotEmpty()) {
                supabase.from("paintings").upsert(
                    paintings.map { (_, p) -> row(userId, p) },
                ) {
                    onConflict = "user_id,started_at,seed"
                    ignoreDuplicates = true
                }
                // Local bookkeeping only — this touches the syncedAt column, never the earned
                // painting content, so I12's protection of the record itself is untouched.
                paintings.forEach { (localId, _) -> museumRepository.markSynced(localId, now) }
            }

            accountRepository.recordSynced(now)
            Result(ok = true, pushed = paintings.size, error = null)
        }.getOrElse { error ->
            accountRepository.recordSyncFailed()
            Result(ok = false, pushed = 0, error = error.message ?: "Could not sync right now.")
        }
    }

    private fun row(userId: String, p: CompletedPainting) = PaintingRow(
        userId = userId,
        seed = p.painting.seed,
        title = p.painting.title,
        totalMinutes = p.totalMinutes,
        sittingMinutes = p.sittingMinutes,
        pieces = p.pieces,
        startedAt = iso(p.startedAt),
        completedAt = iso(p.completedAt),
    )

    /** Postgres `timestamptz` in the same ISO-8601 shape the web client sends. */
    private fun iso(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()
}
