package com.artoffocus.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.artoffocus.app.data.local.PrefKeys
import com.artoffocus.app.data.local.appDataStore
import com.artoffocus.app.domain.focus.FocusLevel
import com.artoffocus.app.domain.focus.PREFERRED_SITTING_MINUTES
import com.artoffocus.app.domain.focus.DEFAULT_TOTAL_MINUTES
import com.artoffocus.app.domain.focus.isValidPair
import com.artoffocus.app.domain.focus.sittingsFor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class FocusSetup(val totalMinutes: Int, val sittingMinutes: Int, val level: FocusLevel)

/**
 * Mirrors focusSetupStorage.ts: owns the Total/Sitting pair AND the focus level key (level
 * does not live with the rest of the profile). I4 — editing the level here is safe even
 * mid-painting; it only changes future defaults, never an already-frozen pair (I2).
 */
class FocusSetupRepository(private val context: Context) {

    fun defaultFor(level: FocusLevel): FocusSetup {
        val total = DEFAULT_TOTAL_MINUTES.getValue(level)
        val preferred = PREFERRED_SITTING_MINUTES.getValue(level)
        val sitting = if (sittingsFor(total).contains(preferred)) preferred else sittingsFor(total).first()
        return FocusSetup(total, sitting, level)
    }

    suspend fun load(): FocusSetup {
        val prefs = context.appDataStore.data.first()
        val level = FocusLevel.fromNameOrDefault(prefs[PrefKeys.FOCUS_LEVEL])
        val total = prefs[PrefKeys.TOTAL_MINUTES]
        val sitting = prefs[PrefKeys.SITTING_MINUTES]
        // Discard, don't repair: an invalid stored pair is dropped in favor of the level default.
        return if (total != null && sitting != null && isValidPair(total, sitting)) {
            FocusSetup(total, sitting, level)
        } else {
            defaultFor(level)
        }
    }

    fun observe() = context.appDataStore.data.map { prefs ->
        val level = FocusLevel.fromNameOrDefault(prefs[PrefKeys.FOCUS_LEVEL])
        val total = prefs[PrefKeys.TOTAL_MINUTES]
        val sitting = prefs[PrefKeys.SITTING_MINUTES]
        if (total != null && sitting != null && isValidPair(total, sitting)) {
            FocusSetup(total, sitting, level)
        } else {
            defaultFor(level)
        }
    }

    suspend fun save(totalMinutes: Int, sittingMinutes: Int) {
        require(isValidPair(totalMinutes, sittingMinutes)) { "Invalid Total/Sitting pair" }
        context.appDataStore.edit { prefs ->
            prefs[PrefKeys.TOTAL_MINUTES] = totalMinutes
            prefs[PrefKeys.SITTING_MINUTES] = sittingMinutes
        }
    }

    /** I4 — editable any time, including mid-painting; never touches the frozen active pair. */
    suspend fun saveLevel(level: FocusLevel) {
        context.appDataStore.edit { prefs -> prefs[PrefKeys.FOCUS_LEVEL] = level.name }
    }
}
