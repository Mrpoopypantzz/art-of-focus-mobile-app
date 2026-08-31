package com.artoffocus.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import android.content.Context

/**
 * Local-first source of truth (mirrors the web app's "localStorage is source of truth"
 * model — a backend, if configured, only ever mirrors this). One DataStore file, namespaced
 * keys per concern, matching the one-module-owns-its-keys discipline of each state module.
 */
val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "art_of_focus_prefs")

object PrefKeys {
    // Focus setup (I1/I2 pair) + focus level — NOTE: level lives here, not in Profile,
    // matching focusSetupStorage.ts owning the level key on the web.
    val TOTAL_MINUTES = intPreferencesKey("focus.totalMinutes")
    val SITTING_MINUTES = intPreferencesKey("focus.sittingMinutes")
    val FOCUS_LEVEL = stringPreferencesKey("focus.level")

    // Session state (I7) — stored as JSON, always re-validated against the current setup on read.
    val SESSION_JSON = stringPreferencesKey("focus.session")

    // Active painting (I6 — at most one)
    val ACTIVE_PAINTING_JSON = stringPreferencesKey("focus.activePainting")
    val ACTIVE_PAINTING_STARTED_AT = longPreferencesKey("focus.activePaintingStartedAt")

    // Away-time tracking (I7 amendment)
    val LAST_SEEN_AT = longPreferencesKey("focus.lastSeenAt")

    // Profile (username/goal only — NOT level)
    val USERNAME = stringPreferencesKey("profile.username")
    val GOAL = stringPreferencesKey("profile.goal")

    // Onboarding
    val ONBOARDING_STEP = stringPreferencesKey("onboarding.step")
    val ONBOARDING_SLIDE_INDEX = intPreferencesKey("onboarding.slideIndex")

    // Notification prefs
    val NOTIFY_REST_END = booleanPreferencesKey("notify.restEnd")
    val NOTIFY_ACHIEVEMENT = booleanPreferencesKey("notify.achievement")
    val NOTIFY_DAILY_CALL = booleanPreferencesKey("notify.dailyCall")
    val NOTIFY_STREAK_RISK = booleanPreferencesKey("notify.streakRisk")

    // Account / sync
    val ACCOUNT_STATUS = stringPreferencesKey("account.status")
    val ACCOUNT_EMAIL = stringPreferencesKey("account.email")
    val LAST_SYNCED_AT = longPreferencesKey("account.lastSyncedAt")
    val SYNC_STATUS = stringPreferencesKey("account.syncStatus")
}
