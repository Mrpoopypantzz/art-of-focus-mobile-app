package com.artoffocus.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.artoffocus.app.data.local.PrefKeys
import com.artoffocus.app.data.local.appDataStore
import com.artoffocus.app.domain.focus.IDLE_SESSION
import com.artoffocus.app.domain.focus.SessionState
import com.artoffocus.app.domain.focus.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
private data class SessionDto(
    val status: String,
    val startedAt: Long,
    val accumulatedMs: Long,
    val sittingIndex: Int,
    val restStartedAt: Long,
    val restMinutes: Int,
)

private fun SessionState.toDto() = SessionDto(status.name, startedAt, accumulatedMs, sittingIndex, restStartedAt, restMinutes)

private fun SessionDto.toDomain(): SessionState? {
    val status = runCatching { SessionStatus.valueOf(status) }.getOrNull() ?: return null
    return SessionState(status, startedAt, accumulatedMs, sittingIndex, restStartedAt, restMinutes)
}

/**
 * Mirrors sessionStorage.ts (I7 resume). On read, a session whose sittingIndex no longer
 * fits the current piece count is discarded entirely, never repaired — the caller (the
 * ViewModel) is expected to pass the current [pieces] it knows about.
 */
class SessionRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(pieces: Int): SessionState {
        val raw = context.appDataStore.data.first()[PrefKeys.SESSION_JSON] ?: return IDLE_SESSION
        val state = runCatching { json.decodeFromString<SessionDto>(raw) }.getOrNull()?.toDomain() ?: return IDLE_SESSION
        return if (state.sittingIndex in 0..pieces) state else IDLE_SESSION
    }

    /**
     * The same validated read as [load], as a stream.
     *
     * Added for the ongoing session notification, which has to follow the session while the app
     * is closed and cannot call a one-shot [load] on a timer without re-reading DataStore every
     * second. Applies the identical discard rule, so the notification can never show a session
     * the app itself would refuse to resume.
     */
    fun observe(pieces: Int): Flow<SessionState> = context.appDataStore.data.map { prefs ->
        val raw = prefs[PrefKeys.SESSION_JSON] ?: return@map IDLE_SESSION
        val state = runCatching { json.decodeFromString<SessionDto>(raw) }.getOrNull()?.toDomain()
            ?: return@map IDLE_SESSION
        if (state.sittingIndex in 0..pieces) state else IDLE_SESSION
    }

    suspend fun save(state: SessionState) {
        context.appDataStore.edit { prefs -> prefs[PrefKeys.SESSION_JSON] = json.encodeToString(state.toDto()) }
    }

    suspend fun clear() {
        context.appDataStore.edit { prefs -> prefs.remove(PrefKeys.SESSION_JSON) }
    }

    /** I7 amendment — recorded on hide only to measure the away gap, never to pause. */
    suspend fun recordLastSeenAt(nowMs: Long) {
        context.appDataStore.edit { prefs -> prefs[PrefKeys.LAST_SEEN_AT] = nowMs }
    }

    suspend fun consumeLastSeenAt(): Long? {
        val value = context.appDataStore.data.first()[PrefKeys.LAST_SEEN_AT]
        context.appDataStore.edit { prefs -> prefs.remove(PrefKeys.LAST_SEEN_AT) }
        return value
    }
}
