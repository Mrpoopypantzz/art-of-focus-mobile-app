package com.artoffocus.app.ui.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artoffocus.app.data.repository.AccountRepository
import com.artoffocus.app.data.repository.AccountState
import com.artoffocus.app.data.repository.AuthRepository
import com.artoffocus.app.data.repository.FocusSetupRepository
import com.artoffocus.app.data.repository.MuseumRepository
import com.artoffocus.app.data.repository.NotificationPrefsRepository
import com.artoffocus.app.data.repository.OnboardingRepository
import com.artoffocus.app.data.repository.ProfileRepository
import com.artoffocus.app.data.repository.SyncRepository
import com.artoffocus.app.domain.account.AccountStatus
import com.artoffocus.app.domain.achievements.Achievement
import com.artoffocus.app.domain.achievements.ACHIEVEMENTS
import com.artoffocus.app.domain.achievements.isEarned
import com.artoffocus.app.domain.focus.FocusLevel
import com.artoffocus.app.domain.museum.CompletedPainting
import com.artoffocus.app.domain.notifications.DEFAULT_PREFS
import com.artoffocus.app.domain.notifications.NotificationKind
import com.artoffocus.app.domain.notifications.NotificationPrefs
import com.artoffocus.app.domain.profile.Profile
import com.artoffocus.app.domain.profile.Statistics
import com.artoffocus.app.domain.profile.statistics
import com.artoffocus.app.ui.auth.requestGoogleSignIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: Profile = Profile(),
    val statistics: Statistics = statistics(emptyList(), System.currentTimeMillis()),
    val achievements: List<Achievement> = ACHIEVEMENTS,
    val earnedAchievements: Set<Achievement> = emptySet(),
    val account: AccountState = AccountState(AccountStatus.SIGNED_OUT, null),
    val authInProgress: Boolean = false,
    val authError: String? = null,
    val notificationPrefs: NotificationPrefs = DEFAULT_PREFS,
)

/** F10 — identity, editable focus level (I4, mid-painting-safe), statistics/achievements (F10.7/D35 — finished paintings only). */
class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val focusSetupRepository: FocusSetupRepository,
    private val museumRepository: MuseumRepository,
    private val accountRepository: AccountRepository,
    private val authRepository: AuthRepository,
    private val notificationPrefsRepository: NotificationPrefsRepository,
    private val syncRepository: SyncRepository,
    private val onboardingRepository: OnboardingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state

    init {
        viewModelScope.launch {
            combine(profileRepository.observe(), museumRepository.observeAll()) { profile, paintings ->
                profile to paintings
            }.collect { (profile, paintings) ->
                val stats = statistics(paintings, System.currentTimeMillis())
                _state.value = _state.value.copy(
                    profile = profile,
                    statistics = stats,
                    earnedAchievements = ACHIEVEMENTS.filter { isEarned(stats, it) }.toSet(),
                )
            }
        }
        viewModelScope.launch {
            val setup = focusSetupRepository.load()
            _state.value = _state.value.copy(profile = _state.value.profile.copy(level = setup.level))
        }
        viewModelScope.launch {
            accountRepository.observe().collect { account ->
                _state.value = _state.value.copy(account = account)
            }
        }
        viewModelScope.launch {
            notificationPrefsRepository.observe().collect { prefs ->
                _state.value = _state.value.copy(notificationPrefs = prefs)
            }
        }
    }

    /**
     * M8 — per-kind delivery preferences. The workers read these themselves before posting,
     * so a toggle takes effect on the next scheduled run without rescheduling anything.
     */
    fun setNotificationPref(kind: NotificationKind, enabled: Boolean) {
        val current = _state.value.notificationPrefs
        val next = when (kind) {
            NotificationKind.REST_END -> current.copy(restEnd = enabled)
            NotificationKind.ACHIEVEMENT -> current.copy(achievement = enabled)
            NotificationKind.DAILY_CALL -> current.copy(dailyCall = enabled)
            NotificationKind.STREAK_RISK -> current.copy(streakRisk = enabled)
        }
        viewModelScope.launch { notificationPrefsRepository.save(next) }
    }

    fun saveIdentity(username: String, goal: String) {
        viewModelScope.launch { profileRepository.save(username, goal) }
    }

    /** I4 — safe to change at any time, including mid-painting; only moves future defaults. */
    fun saveLevel(level: FocusLevel) {
        viewModelScope.launch {
            focusSetupRepository.saveLevel(level)
            _state.value = _state.value.copy(profile = _state.value.profile.copy(level = level))
        }
    }

    /** F10.6 — account auth, reachable with no account. Native Google Sign-In only. */
    fun signInWithGoogle(context: Context) {
        if (_state.value.authInProgress) return
        _state.value = _state.value.copy(authInProgress = true, authError = null)
        viewModelScope.launch {
            runCatching {
                val result = requestGoogleSignIn(context)
                authRepository.signInWithGoogleIdToken(result.idToken, result.rawNonce)
            }.onSuccess {
                // M6 — one of the two moments web syncs: signing in is when a device's local
                // work first has somewhere to go. Never during a session (I7).
                syncRepository.pushLocalWork()
            }.onFailure { e ->
                _state.value = _state.value.copy(authError = e.message ?: "Sign-in failed")
            }
            _state.value = _state.value.copy(authInProgress = false)
        }
    }

    /** F10.6 — signing out only ends the session; it deliberately leaves the Museum alone. */
    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    /**
     * F10.6 — the separate, always-reachable destructive action. Someone with no account is
     * exactly the person whose device holds the only copy, so this must not hide behind
     * sign-in. The confirmation sheet that gates it states what is about to be lost.
     */
    fun clearLocalData() {
        viewModelScope.launch { museumRepository.clearAll() }
    }

    /** F10.3 — clears the completion flag; AppRoot re-enters the introduction on the next frame. */
    fun replayIntroduction() {
        viewModelScope.launch { onboardingRepository.reset() }
    }

    fun dismissAuthError() {
        _state.value = _state.value.copy(authError = null)
    }

    class Factory(
        private val profileRepository: ProfileRepository,
        private val focusSetupRepository: FocusSetupRepository,
        private val museumRepository: MuseumRepository,
        private val accountRepository: AccountRepository,
        private val authRepository: AuthRepository,
        private val notificationPrefsRepository: NotificationPrefsRepository,
        private val syncRepository: SyncRepository,
        private val onboardingRepository: OnboardingRepository,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(
                profileRepository,
                focusSetupRepository,
                museumRepository,
                accountRepository,
                authRepository,
                notificationPrefsRepository,
                syncRepository,
                onboardingRepository,
            ) as T
        }
    }
}
