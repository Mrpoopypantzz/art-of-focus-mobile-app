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
import com.artoffocus.app.data.repository.ProfileRepository
import com.artoffocus.app.domain.account.AccountStatus
import com.artoffocus.app.domain.achievements.Achievement
import com.artoffocus.app.domain.achievements.ACHIEVEMENTS
import com.artoffocus.app.domain.achievements.isEarned
import com.artoffocus.app.domain.focus.FocusLevel
import com.artoffocus.app.domain.museum.CompletedPainting
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
)

/** F10 — identity, editable focus level (I4, mid-painting-safe), statistics/achievements (F10.7/D35 — finished paintings only). */
class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val focusSetupRepository: FocusSetupRepository,
    museumRepository: MuseumRepository,
    private val accountRepository: AccountRepository,
    private val authRepository: AuthRepository,
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
            }.onFailure { e ->
                _state.value = _state.value.copy(authError = e.message ?: "Sign-in failed")
            }
            _state.value = _state.value.copy(authInProgress = false)
        }
    }

    /** F10.6 — "clear local cache", reachable with no account. */
    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
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
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(profileRepository, focusSetupRepository, museumRepository, accountRepository, authRepository) as T
        }
    }
}
