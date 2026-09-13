package com.artoffocus.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.artoffocus.app.data.local.PrefKeys
import com.artoffocus.app.data.local.appDataStore
import com.artoffocus.app.domain.focus.ArtSource
import com.artoffocus.app.domain.focus.Painting
import com.artoffocus.app.domain.focus.assignPainting
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Every nullable field here MUST keep its default. `ignoreUnknownKeys` tolerates *extra*
 * keys, not missing ones — without a default, an app update mid-painting would make decoding
 * throw, [ArtworkRepository.loadActive]'s runCatching would swallow it, and the user's active
 * painting would silently disappear. That is an I7 violation ("no progress is ever lost").
 *
 * This is not hypothetical for `source` and `credit`: they are new, so a painting that was
 * active across the update to this version has JSON without them. They decode to the
 * defaults below, which describe exactly what such a painting is.
 */
@Serializable
private data class PaintingDto(
    val id: String,
    val title: String,
    val seed: Long,
    val imageRef: String? = null,
    val source: String? = null,
    val credit: String? = null,
    val remoteUrl: String? = null,
)

private fun Painting.toDto() = PaintingDto(id, title, seed, imageRef, source.name, credit, remoteUrl)

private fun PaintingDto.toDomain(): Painting {
    // A painting stored before `source` existed can only be a gradient or the user's own
    // photo — community art did not exist yet — so the image tells us which, exactly as the
    // 2->3 database migration back-fills the same conclusion for museum rows.
    val resolved = source?.let { runCatching { ArtSource.valueOf(it) }.getOrNull() }
        ?: if (imageRef != null) ArtSource.OWN_PHOTO else ArtSource.GENERATED
    return Painting(id, title, seed, imageRef, resolved, credit, remoteUrl)
}

/**
 * The art a sitting is about to be started with, already resolved to something local.
 *
 * By the time one of these exists the image is on disk — a cached community painting or one
 * of the user's pictures — so starting a sitting never waits on the network (I7).
 */
data class ChosenArtwork(
    val imageRef: String,
    val source: ArtSource,
    val title: String? = null,
    val credit: String? = null,
    /** Where the image can be fetched again on another device; community art only. */
    val remoteUrl: String? = null,
)

/**
 * Mirrors artworkStorage.ts. I6 — only one painting may be active at a time; this is
 * enforced here (not in domain/) by [assignIfAbsent] refusing to replace a live painting.
 */
class ArtworkRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadActive(): Painting? {
        val raw = context.appDataStore.data.first()[PrefKeys.ACTIVE_PAINTING_JSON] ?: return null
        return runCatching { json.decodeFromString<PaintingDto>(raw) }.getOrNull()?.toDomain()
    }

    suspend fun loadActiveStartedAt(): Long? = context.appDataStore.data.first()[PrefKeys.ACTIVE_PAINTING_STARTED_AT]

    /**
     * I6 — refuses to replace a painting that is already active; returns the pre-existing one
     * in that case.
     *
     * [artwork] is the art this painting should wear: a community painting, one of the user's
     * pictures, or null for the generated gradient. The seed still decides the palette and
     * the id regardless, so identity is unaffected by what the painting looks like.
     */
    suspend fun assignIfAbsent(seed: Long, startedAt: Long, artwork: ChosenArtwork? = null): Painting {
        val existing = loadActive()
        if (existing != null) return existing
        val base = assignPainting(seed)
        val painting = when (artwork) {
            null -> base
            else -> base.copy(
                // A community painting carries its real title; a photo keeps the palette
                // name, because the user gave it none.
                title = artwork.title ?: base.title,
                imageRef = artwork.imageRef,
                source = artwork.source,
                credit = artwork.credit,
                remoteUrl = artwork.remoteUrl,
            )
        }
        context.appDataStore.edit { prefs ->
            prefs[PrefKeys.ACTIVE_PAINTING_JSON] = json.encodeToString(painting.toDto())
            prefs[PrefKeys.ACTIVE_PAINTING_STARTED_AT] = startedAt
        }
        return painting
    }

    /** Called only after the Museum record has been written successfully (I12), or on explicit abandon. */
    suspend fun clearActive() {
        context.appDataStore.edit { prefs ->
            prefs.remove(PrefKeys.ACTIVE_PAINTING_JSON)
            prefs.remove(PrefKeys.ACTIVE_PAINTING_STARTED_AT)
        }
    }
}
