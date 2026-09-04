package com.artoffocus.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.artoffocus.app.data.local.PrefKeys
import com.artoffocus.app.data.local.appDataStore
import com.artoffocus.app.domain.account.AccountStatus
import com.artoffocus.app.domain.account.SyncStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AccountState(
    val status: AccountStatus,
    val email: String?,
    /** Null until a push has succeeded — drives the clear-data warning. */
    val lastSyncedAt: Long? = null,
    val syncStatus: SyncStatus = SyncStatus.IDLE,
)

/**
 * I7 — a backend never sits in the timer's path; sync only happens on sign-in and after
 * painting completion, never during a session. The app must work fully offline with no
 * credentials, so every field here defaults to the signed-out/unconfigured state.
 */
class AccountRepository(private val context: Context) {

    suspend fun status(): AccountStatus {
        val raw = context.appDataStore.data.first()[PrefKeys.ACCOUNT_STATUS]
        return raw?.let { runCatching { AccountStatus.valueOf(it) }.getOrNull() } ?: AccountStatus.SIGNED_OUT
    }

    suspend fun setStatus(status: AccountStatus) {
        context.appDataStore.edit { prefs -> prefs[PrefKeys.ACCOUNT_STATUS] = status.name }
    }

    suspend fun email(): String? = context.appDataStore.data.first()[PrefKeys.ACCOUNT_EMAIL]

    suspend fun setEmail(email: String) {
        context.appDataStore.edit { prefs -> prefs[PrefKeys.ACCOUNT_EMAIL] = email }
    }

    /** Reactive view for the Settings screen — updates live as sign-in completes. */
    fun observe() = context.appDataStore.data.map { prefs ->
        val status = prefs[PrefKeys.ACCOUNT_STATUS]?.let { runCatching { AccountStatus.valueOf(it) }.getOrNull() }
            ?: AccountStatus.SIGNED_OUT
        AccountState(
            status = status,
            email = prefs[PrefKeys.ACCOUNT_EMAIL],
            lastSyncedAt = prefs[PrefKeys.LAST_SYNCED_AT],
            syncStatus = prefs[PrefKeys.SYNC_STATUS]?.let { runCatching { SyncStatus.valueOf(it) }.getOrNull() } ?: SyncStatus.IDLE,
        )
    }

    suspend fun lastSyncedAt(): Long? = context.appDataStore.data.first()[PrefKeys.LAST_SYNCED_AT]

    suspend fun recordSynced(nowMs: Long) {
        context.appDataStore.edit { prefs ->
            prefs[PrefKeys.LAST_SYNCED_AT] = nowMs
            prefs[PrefKeys.SYNC_STATUS] = SyncStatus.SYNCED.name
        }
    }

    suspend fun recordSyncFailed() {
        context.appDataStore.edit { prefs -> prefs[PrefKeys.SYNC_STATUS] = SyncStatus.FAILED.name }
    }

    suspend fun syncStatus(): SyncStatus {
        val raw = context.appDataStore.data.first()[PrefKeys.SYNC_STATUS]
        return raw?.let { runCatching { SyncStatus.valueOf(it) }.getOrNull() } ?: SyncStatus.IDLE
    }

    /** Clears all locally cached account state (F10.6's "clear local cache" — reachable with no account). */
    suspend fun clearAccountState() {
        context.appDataStore.edit { prefs ->
            prefs.remove(PrefKeys.ACCOUNT_STATUS)
            prefs.remove(PrefKeys.ACCOUNT_EMAIL)
            prefs.remove(PrefKeys.LAST_SYNCED_AT)
            prefs.remove(PrefKeys.SYNC_STATUS)
        }
    }
}
