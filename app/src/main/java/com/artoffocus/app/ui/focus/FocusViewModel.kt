package com.artoffocus.app.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artoffocus.app.data.repository.ArtworkRepository
import com.artoffocus.app.data.repository.FocusSetupRepository
import com.artoffocus.app.data.repository.MuseumRepository
import com.artoffocus.app.data.repository.FocusSetup
import android.content.Context
import com.artoffocus.app.data.repository.SessionRepository
import com.artoffocus.app.data.repository.SyncRepository
import com.artoffocus.app.notifications.NotificationScheduler
import com.artoffocus.app.domain.focus.AUTO_CONTINUE_MS
import com.artoffocus.app.domain.focus.AWAY_PROMPT_MS
import com.artoffocus.app.domain.focus.IDLE_SESSION
import com.artoffocus.app.domain.focus.Painting
import com.artoffocus.app.domain.focus.SessionState
import com.artoffocus.app.domain.focus.SessionStatus
import com.artoffocus.app.domain.focus.cancelSession
import com.artoffocus.app.domain.focus.completeRest
import com.artoffocus.app.domain.focus.completeSitting
import com.artoffocus.app.domain.focus.continueFromRest
import com.artoffocus.app.domain.focus.discardAwayTime
import com.artoffocus.app.domain.focus.isRestComplete
import com.artoffocus.app.domain.focus.isSittingComplete
import com.artoffocus.app.domain.focus.isValidPair
import com.artoffocus.app.domain.focus.pauseSession
import com.artoffocus.app.domain.focus.pieceCount
import com.artoffocus.app.domain.focus.resumeSession
import com.artoffocus.app.domain.focus.revealedCount
import com.artoffocus.app.domain.focus.startNextSitting
import com.artoffocus.app.domain.focus.startRest
import com.artoffocus.app.domain.focus.startSession
import com.artoffocus.app.domain.museum.CompletedPainting
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FocusUiState(
    val isLoading: Boolean = true,
    val setup: FocusSetup = FocusSetup(120, 30, com.artoffocus.app.domain.focus.FocusLevel.BEGINNER),
    val pieces: Int = 4,
    val painting: Painting? = null,
    val activePaintingStartedAt: Long = 0L,
    val session: SessionState = IDLE_SESSION,
    val now: Long = System.currentTimeMillis(),
    val awayGapMs: Long? = null,
    val cancelCountdown: Int? = null,
    val remainingAutoContinueMs: Long? = null,
    val justCompletedPainting: CompletedPainting? = null,
)

/**
 * Owns the Focus core loop end to end. Every transition delegates to the pure functions in
 * domain/focus — this class is wiring and persistence, not rules.
 */
class FocusViewModel(
    private val focusSetupRepository: FocusSetupRepository,
    private val sessionRepository: SessionRepository,
    private val artworkRepository: ArtworkRepository,
    private val museumRepository: MuseumRepository,
    private val syncRepository: SyncRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(FocusUiState())
    val state: StateFlow<FocusUiState> = _state

    private var cancelCountdownJob: kotlinx.coroutines.Job? = null
    private var autoContinueJob: kotlinx.coroutines.Job? = null
    private var autoContinueDeadline: Long? = null

    init {
        viewModelScope.launch {
            val setup = focusSetupRepository.load()
            val pieces = pieceCount(setup.totalMinutes, setup.sittingMinutes) ?: 1
            val painting = artworkRepository.loadActive()
            val startedAt = artworkRepository.loadActiveStartedAt() ?: 0L
            val session = sessionRepository.load(pieces)
            _state.update {
                it.copy(
                    isLoading = false,
                    setup = setup,
                    pieces = pieces,
                    painting = painting,
                    activePaintingStartedAt = startedAt,
                    session = session,
                    now = System.currentTimeMillis(),
                )
            }
            checkAwayGapOnResume()
            startTicker()
        }
    }

    private fun startTicker() {
        viewModelScope.launch {
            while (true) {
                delay(250)
                val now = System.currentTimeMillis()
                val remaining = autoContinueDeadline?.let { (it - now).coerceAtLeast(0) }
                _state.update { it.copy(now = now, remainingAutoContinueMs = remaining) }
                checkForCompletions(now)
            }
        }
    }

    private suspend fun checkForCompletions(now: Long) {
        val current = _state.value
        when (current.session.status) {
            SessionStatus.RUNNING -> {
                if (isSittingComplete(current.session, current.setup.sittingMinutes, now)) {
                    onSittingComplete(now)
                }
            }
            SessionStatus.RESTING -> {
                if (isRestComplete(current.session, now)) {
                    val completed = completeRest(current.session)
                    persistSession(completed)
                }
            }
            else -> Unit
        }
    }

    private suspend fun onSittingComplete(now: Long) {
        val current = _state.value
        val completed = completeSitting(current.session, current.setup.sittingMinutes)
        persistSession(completed)

        if (revealedCount(completed, current.pieces) >= current.pieces) {
            // I10 — suppressed on the final sitting: no continue/rest sheet, straight to completion.
            finishPainting(now)
        } else {
            startAutoContinueCountdown()
        }
    }

    /** I10 — a visible 60s countdown; if it expires with no choice, the next sitting begins. */
    private fun startAutoContinueCountdown() {
        autoContinueJob?.cancel()
        autoContinueDeadline = System.currentTimeMillis() + AUTO_CONTINUE_MS
        _state.update { it.copy(remainingAutoContinueMs = AUTO_CONTINUE_MS) }
        autoContinueJob = viewModelScope.launch {
            delay(AUTO_CONTINUE_MS)
            val current = _state.value
            if (current.session.status == SessionStatus.SITTING_COMPLETE) {
                chooseContinue()
            }
        }
    }

    private suspend fun finishPainting(now: Long) {
        val current = _state.value
        val painting = current.painting ?: return
        val record = CompletedPainting(
            painting = painting,
            totalMinutes = current.setup.totalMinutes,
            sittingMinutes = current.setup.sittingMinutes,
            pieces = current.pieces,
            startedAt = current.activePaintingStartedAt,
            completedAt = now,
        )
        // I12 — the museum record is written before the active slot is cleared.
        museumRepository.recordCompletion(record)
        artworkRepository.clearActive()
        sessionRepository.clear()
        // M6 — the second of web's two sync moments. Deliberately after the local write and
        // after the session has ended, never during one (I7): if this fails or is slow, the
        // painting is already safe in Room and the next sync picks it up.
        syncRepository.pushLocalWork()
        _state.update {
            it.copy(session = IDLE_SESSION, painting = null, activePaintingStartedAt = 0L, justCompletedPainting = record)
        }
    }

    fun acknowledgePaintingCompletion() {
        _state.update { it.copy(justCompletedPainting = null) }
    }

    // --- Setup (I1/I6) ---

    fun selectSetup(totalMinutes: Int, sittingMinutes: Int) {
        if (_state.value.painting != null) return // I6 — blocked while a painting is active
        if (!isValidPair(totalMinutes, sittingMinutes)) return
        viewModelScope.launch {
            focusSetupRepository.save(totalMinutes, sittingMinutes)
            val setup = _state.value.setup.copy(totalMinutes = totalMinutes, sittingMinutes = sittingMinutes)
            _state.update { it.copy(setup = setup, pieces = pieceCount(totalMinutes, sittingMinutes) ?: it.pieces) }
        }
    }

    // --- Session lifecycle ---

    /**
     * [imageRef] is a picture the user chose (or shuffled to) in Your Pictures. When null the
     * painting is the generated gradient, exactly as before. I6 still holds: if a painting is
     * already active, [ArtworkRepository.assignIfAbsent] returns it and the choice is ignored
     * rather than replacing live work.
     */
    fun startFocusSession(imageRef: String? = null) {
        if (_state.value.session.status != SessionStatus.IDLE) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val painting = artworkRepository.assignIfAbsent(seed = now, startedAt = now, imageRef = imageRef)
            val session = startSession(now)
            persistSession(session)
            _state.update { it.copy(painting = painting, activePaintingStartedAt = now) }
        }
    }

    fun pause() {
        val now = System.currentTimeMillis()
        persistSession(pauseSession(_state.value.session, now))
    }

    fun resume() {
        val now = System.currentTimeMillis()
        persistSession(resumeSession(_state.value.session, now))
    }

    // --- I9: cancelling has friction; starting does not ---

    fun requestCancel() {
        cancelCountdownJob?.cancel()
        _state.update { it.copy(cancelCountdown = 5) }
        cancelCountdownJob = viewModelScope.launch {
            for (remaining in 4 downTo 0) {
                delay(1000)
                _state.update { it.copy(cancelCountdown = remaining) }
            }
        }
    }

    fun abortCancelRequest() {
        cancelCountdownJob?.cancel()
        _state.update { it.copy(cancelCountdown = null) }
    }

    /**
     * Only takes effect once the visible countdown has reached zero. Cancels this sitting only —
     * the active painting and any pieces already earned survive (matches web's cancelSession(),
     * which resets only session state). Distinct from [abandonPainting], which discards the
     * painting entirely.
     */
    fun confirmCancel() {
        if (_state.value.cancelCountdown != 0) return
        cancelCountdownJob?.cancel()
        NotificationScheduler.cancelRestEnd(appContext)
        viewModelScope.launch {
            sessionRepository.clear()
            _state.update { it.copy(session = cancelSession(), cancelCountdown = null) }
        }
    }

    /** Discards the active painting entirely — reachable only from the idle, locked-pair state. */
    fun abandonPainting() {
        viewModelScope.launch {
            NotificationScheduler.cancelRestEnd(appContext)
            artworkRepository.clearActive()
            sessionRepository.clear()
            _state.update {
                it.copy(session = cancelSession(), painting = null, activePaintingStartedAt = 0L)
            }
        }
    }

    // --- I11: rest completion never auto-starts the next sitting ---

    fun chooseRest(minutes: Int) {
        autoContinueJob?.cancel()
        autoContinueDeadline = null
        _state.update { it.copy(remainingAutoContinueMs = null) }
        val now = System.currentTimeMillis()
        val resting = startRest(_state.value.session, minutes, now)
        persistSession(resting)
        // F14.1 — scheduled for the exact wall-clock rest end; the worker re-checks state before posting.
        NotificationScheduler.scheduleRestEnd(appContext, now, minutes, _state.value.pieces)
    }

    fun chooseContinue() {
        autoContinueJob?.cancel()
        autoContinueDeadline = null
        _state.update { it.copy(remainingAutoContinueMs = null) }
        val now = System.currentTimeMillis()
        val current = _state.value.session
        val wasResting = current.status == SessionStatus.RESTING || current.status == SessionStatus.REST_COMPLETE
        val next = when (current.status) {
            SessionStatus.SITTING_COMPLETE -> startNextSitting(current, now)
            SessionStatus.RESTING, SessionStatus.REST_COMPLETE -> continueFromRest(current, now)
            else -> current
        }
        persistSession(next)
        if (wasResting && next.status != current.status) {
            NotificationScheduler.cancelRestEnd(appContext)
        }
    }

    // --- I7 amendment: away-time prompt ---

    fun onAppBackgrounded() {
        viewModelScope.launch { sessionRepository.recordLastSeenAt(System.currentTimeMillis()) }
    }

    fun onAppForegrounded() {
        viewModelScope.launch { checkAwayGapOnResume() }
    }

    private suspend fun checkAwayGapOnResume() {
        val lastSeen = sessionRepository.consumeLastSeenAt() ?: return
        if (_state.value.session.status != SessionStatus.RUNNING) return
        val gap = System.currentTimeMillis() - lastSeen
        if (gap > AWAY_PROMPT_MS) {
            _state.update { it.copy(awayGapMs = gap) }
        }
    }

    fun keepAwayTime() {
        // The wall clock already counted it — nothing to change.
        _state.update { it.copy(awayGapMs = null) }
    }

    fun discardAwayTime() {
        val gap = _state.value.awayGapMs ?: return
        persistSession(discardAwayTime(_state.value.session, gap))
        _state.update { it.copy(awayGapMs = null) }
    }

    private fun persistSession(session: SessionState) {
        _state.update { it.copy(session = session) }
        viewModelScope.launch { sessionRepository.save(session) }
    }

    class Factory(
        private val focusSetupRepository: FocusSetupRepository,
        private val sessionRepository: SessionRepository,
        private val artworkRepository: ArtworkRepository,
        private val museumRepository: MuseumRepository,
        private val syncRepository: SyncRepository,
        private val appContext: Context,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return FocusViewModel(focusSetupRepository, sessionRepository, artworkRepository, museumRepository, syncRepository, appContext) as T
        }
    }
}
