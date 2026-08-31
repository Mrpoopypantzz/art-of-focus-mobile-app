package com.artoffocus.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.artoffocus.app.data.local.PrefKeys
import com.artoffocus.app.data.local.appDataStore
import com.artoffocus.app.domain.onboarding.OnboardingState
import com.artoffocus.app.domain.onboarding.OnboardingStep
import kotlinx.coroutines.flow.first

/** Mirrors onboardingStorage.ts. F12.2 — a skip persists identically to a normal completion. */
class OnboardingRepository(private val context: Context) {

    suspend fun load(): OnboardingState {
        val prefs = context.appDataStore.data.first()
        val step = prefs[PrefKeys.ONBOARDING_STEP]?.let {
            runCatching { OnboardingStep.valueOf(it) }.getOrNull()
        } ?: OnboardingStep.SLIDES
        val slideIndex = prefs[PrefKeys.ONBOARDING_SLIDE_INDEX] ?: 0
        return OnboardingState(step, slideIndex)
    }

    suspend fun save(state: OnboardingState) {
        context.appDataStore.edit { prefs ->
            prefs[PrefKeys.ONBOARDING_STEP] = state.step.name
            prefs[PrefKeys.ONBOARDING_SLIDE_INDEX] = state.slideIndex
        }
    }

    suspend fun hasCompletedOnboarding(): Boolean = load().step == OnboardingStep.DONE
}
