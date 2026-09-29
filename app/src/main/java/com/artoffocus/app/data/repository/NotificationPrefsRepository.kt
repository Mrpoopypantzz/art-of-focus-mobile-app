package com.artoffocus.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.artoffocus.app.data.local.PrefKeys
import com.artoffocus.app.data.local.appDataStore
import com.artoffocus.app.domain.notifications.DEFAULT_PREFS
import com.artoffocus.app.domain.notifications.NotificationPrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class NotificationPrefsRepository(private val context: Context) {

    suspend fun load(): NotificationPrefs {
        val prefs = context.appDataStore.data.first()
        return NotificationPrefs(
            restEnd = prefs[PrefKeys.NOTIFY_REST_END] ?: DEFAULT_PREFS.restEnd,
            sittingEnd = prefs[PrefKeys.NOTIFY_SITTING_END] ?: DEFAULT_PREFS.sittingEnd,
            achievement = prefs[PrefKeys.NOTIFY_ACHIEVEMENT] ?: DEFAULT_PREFS.achievement,
            dailyCall = prefs[PrefKeys.NOTIFY_DAILY_CALL] ?: DEFAULT_PREFS.dailyCall,
            streakRisk = prefs[PrefKeys.NOTIFY_STREAK_RISK] ?: DEFAULT_PREFS.streakRisk,
        )
    }

    fun observe() = context.appDataStore.data.map { prefs ->
        NotificationPrefs(
            restEnd = prefs[PrefKeys.NOTIFY_REST_END] ?: DEFAULT_PREFS.restEnd,
            sittingEnd = prefs[PrefKeys.NOTIFY_SITTING_END] ?: DEFAULT_PREFS.sittingEnd,
            achievement = prefs[PrefKeys.NOTIFY_ACHIEVEMENT] ?: DEFAULT_PREFS.achievement,
            dailyCall = prefs[PrefKeys.NOTIFY_DAILY_CALL] ?: DEFAULT_PREFS.dailyCall,
            streakRisk = prefs[PrefKeys.NOTIFY_STREAK_RISK] ?: DEFAULT_PREFS.streakRisk,
        )
    }

    suspend fun save(prefs: NotificationPrefs) {
        context.appDataStore.edit { store ->
            store[PrefKeys.NOTIFY_REST_END] = prefs.restEnd
            store[PrefKeys.NOTIFY_SITTING_END] = prefs.sittingEnd
            store[PrefKeys.NOTIFY_ACHIEVEMENT] = prefs.achievement
            store[PrefKeys.NOTIFY_DAILY_CALL] = prefs.dailyCall
            store[PrefKeys.NOTIFY_STREAK_RISK] = prefs.streakRisk
        }
    }
}
