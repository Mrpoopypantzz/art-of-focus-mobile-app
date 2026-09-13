package com.artoffocus.app.data.repository

import com.artoffocus.app.domain.focus.ArtSource
import com.artoffocus.app.domain.focus.assignPainting
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
 * Push AND pull, with the conflict rule that was deferred until now made explicit.
 *
 * Pulling was held back because two devices with divergent museums need a merge rule, and
 * guessing at one risks I12 ("a completed painting is never destroyed"). The rule is:
 * **pulling only ever adds.** It never deletes, never overwrites and never reconciles a local
 * painting against a remote one. A painting held locally and absent from the server is left
 * exactly as it is. So I12 is not carefully avoided here, it is structurally unreachable —
 * there is no code path in which a pull can remove or alter a painting the user has earned.
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

    data class Result(val ok: Boolean, val pushed: Int, val pulled: Int = 0, val error: String?)

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
        /**
         * What the painting is made of. Defaulted so a row written by an older client (which
         * had none of these columns) still decodes — without that, one legacy row would fail
         * the whole pull.
         */
        val source: String? = null,
        val credit: String? = null,
        /**
         * A PUBLIC url only, and never the user's own photo. See schema.sql: community art
         * carries the museum's hosted image so it renders on any device, while a private
         * picture is deliberately never uploaded.
         */
        @SerialName("image_url") val imageUrl: String? = null,
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

    /**
     * Brings back paintings this device does not have. The other half of a real sync: without
     * it, signing in on a new phone shows an empty museum.
     *
     * ADDITIVE ONLY — this is the I12 conflict rule, and it is the whole reason pulling is safe:
     *
     *  - Insert goes through [PaintingDao.insert], which is `OnConflictStrategy.IGNORE` keyed on
     *    `paintingKey` ("startedAt:seed"). A painting already held is skipped, not rewritten.
     *  - The DAO exposes no update and no delete at all, so "pull deletes a local painting" is
     *    not a bug that has to be avoided here; it is unrepresentable.
     *  - A painting present locally and absent remotely is untouched.
     *
     * Not called during a session, exactly like [pushLocalWork]: Room is the source of truth
     * (I7) and the network never enters the timer's path.
     */
    suspend fun pullRemoteWork(): Result {
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: return Result(ok = false, pushed = 0, error = "Not signed in.")

        return runCatching {
            val rows = supabase.from("paintings")
                .select { filter { eq("user_id", userId) } }
                .decodeList<PaintingRow>()

            // Count what actually landed, not what arrived: insert returns -1 for a row that
            // conflicted, so "restored 7" means seven paintings the user genuinely did not have.
            var restored = 0
            for (remote in rows) {
                val record = remote.toRecordOrNull() ?: continue
                if (museumRepository.recordIfAbsent(record)) restored++
            }
            Result(ok = true, pushed = 0, pulled = restored, error = null)
        }.getOrElse { error ->
            accountRepository.recordSyncFailed()
            Result(ok = false, pushed = 0, error = error.message ?: "Could not sync right now.")
        }
    }

    /**
     * Null when the row cannot be trusted — an unparseable timestamp, say. One bad remote row
     * must not abort the whole restore, the same defensive stance [MuseumRepository] takes on
     * reads.
     */
    private fun PaintingRow.toRecordOrNull(): CompletedPainting? = runCatching {
        val artSource = source?.let { runCatching { ArtSource.valueOf(it) }.getOrNull() }
            ?: ArtSource.GENERATED
        CompletedPainting(
            painting = assignPainting(seed).copy(
                title = title,
                // No local file on this device: the public URL is the image, and Coil renders
                // it the same way. Null for a gradient (redrawn from the seed) and for the
                // user's own photo, whose picture never left the phone that made it.
                imageRef = imageUrl,
                source = artSource,
                credit = credit,
                remoteUrl = imageUrl,
            ),
            totalMinutes = totalMinutes,
            sittingMinutes = sittingMinutes,
            pieces = pieces,
            startedAt = Instant.parse(startedAt).toEpochMilli(),
            completedAt = Instant.parse(completedAt).toEpochMilli(),
        )
    }.getOrNull()

    private fun row(userId: String, p: CompletedPainting) = PaintingRow(
        userId = userId,
        seed = p.painting.seed,
        title = p.painting.title,
        totalMinutes = p.totalMinutes,
        sittingMinutes = p.sittingMinutes,
        pieces = p.pieces,
        startedAt = iso(p.startedAt),
        completedAt = iso(p.completedAt),
        source = p.painting.source.name,
        credit = p.painting.credit,
        // remoteUrl is populated for community art only, so this is null for a gradient and
        // for the user's own photo. That is the privacy line: their pictures stay on device.
        imageUrl = p.painting.remoteUrl,
    )

    /** Postgres `timestamptz` in the same ISO-8601 shape the web client sends. */
    private fun iso(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()
}
