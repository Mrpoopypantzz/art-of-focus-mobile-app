package com.artoffocus.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.artoffocus.app.data.local.PrefKeys
import com.artoffocus.app.data.local.appDataStore
import com.artoffocus.app.domain.focus.Painting
import com.artoffocus.app.domain.focus.assignPainting
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class PaintingDto(val id: String, val title: String, val seed: Long)

private fun Painting.toDto() = PaintingDto(id, title, seed)
private fun PaintingDto.toDomain() = Painting(id, title, seed)

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

    /** I6 — refuses to replace a painting that is already active; returns the pre-existing one in that case. */
    suspend fun assignIfAbsent(seed: Long, startedAt: Long): Painting {
        val existing = loadActive()
        if (existing != null) return existing
        val painting = assignPainting(seed)
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
