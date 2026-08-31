package com.artoffocus.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.artoffocus.app.data.local.PrefKeys
import com.artoffocus.app.data.local.appDataStore
import com.artoffocus.app.domain.profile.EMPTY_PROFILE
import com.artoffocus.app.domain.profile.Profile
import com.artoffocus.app.domain.profile.normaliseGoal
import com.artoffocus.app.domain.profile.normaliseUsername
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Mirrors profileStorage.ts — username/goal ONLY. Focus level lives in [FocusSetupRepository]. */
class ProfileRepository(private val context: Context) {

    suspend fun load(): Profile {
        val prefs = context.appDataStore.data.first()
        return EMPTY_PROFILE.copy(
            username = prefs[PrefKeys.USERNAME] ?: "",
            goal = prefs[PrefKeys.GOAL] ?: "",
        )
    }

    fun observe() = context.appDataStore.data.map { prefs ->
        EMPTY_PROFILE.copy(username = prefs[PrefKeys.USERNAME] ?: "", goal = prefs[PrefKeys.GOAL] ?: "")
    }

    /** Tidying (whitespace collapse/trim/length clamp) happens here, on save — not on every keystroke. */
    suspend fun save(username: String, goal: String) {
        context.appDataStore.edit { prefs ->
            prefs[PrefKeys.USERNAME] = normaliseUsername(username)
            prefs[PrefKeys.GOAL] = normaliseGoal(goal)
        }
    }
}
