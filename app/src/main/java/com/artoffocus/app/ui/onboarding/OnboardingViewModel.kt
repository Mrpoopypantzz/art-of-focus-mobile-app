package com.artoffocus.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artoffocus.app.data.repository.FocusSetupRepository
import com.artoffocus.app.data.repository.OnboardingRepository
import com.artoffocus.app.data.repository.ProfileRepository
import com.artoffocus.app.domain.focus.FocusLevel
import com.artoffocus.app.domain.onboarding.OnboardingState
import com.artoffocus.app.domain.onboarding.completeSetup
import com.artoffocus.app.domain.onboarding.nextSlide
import com.artoffocus.app.domain.onboarding.previousSlide
import com.artoffocus.app.domain.onboarding.skipOnboarding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * F12.1/F12.2 — the first-run introduction: premise slides, then one optional identity step.
 *
 * Every transition delegates to the pure functions in domain/onboarding; this class only
 * persists the result, so a kill mid-flow resumes on the same slide.
 */
class OnboardingViewModel(
    private val onboardingRepository: OnboardingRepository,
    private val profileRepository: ProfileRepository,
    private val focusSetupRepository: FocusSetupRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state

    init {
        // Observed, not read once: "Replay introduction" writes the reset from Profile
        // settings, and this is what puts the flow back on screen without a relaunch.
        viewModelScope.launch {
            onboardingRepository.observe().collect { _state.value = it }
        }
    }

    fun next() = persist(nextSlide(_state.value))

    fun back() = persist(previousSlide(_state.value))

    /** F12.2 — skipping is a completion, not a deferral: it never asks again. */
    fun skip() = persist(skipOnboarding())

    /**
     * All three fields are optional. Blank name and goal are saved as-is — the profile's own
     * displayName() supplies "The Artist" when the name is empty, so there is nothing to
     * special-case here.
     */
    fun finishSetup(username: String, goal: String, level: FocusLevel) {
        viewModelScope.launch {
            profileRepository.save(username, goal)
            // I4 — the level only moves where the setup wheels land, never what they offer.
            focusSetupRepository.saveLevel(level)
            val done = completeSetup()
            onboardingRepository.save(done)
            _state.value = done
        }
    }

    private fun persist(next: OnboardingState) {
        _state.value = next
        viewModelScope.launch { onboardingRepository.save(next) }
    }

    class Factory(
        private val onboardingRepository: OnboardingRepository,
        private val profileRepository: ProfileRepository,
        private val focusSetupRepository: FocusSetupRepository,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return OnboardingViewModel(onboardingRepository, profileRepository, focusSetupRepository) as T
        }
    }
}
