package com.artoffocus.app.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artoffocus.app.data.repository.ArtworkRepository
import com.artoffocus.app.data.repository.ChosenArtwork
import com.artoffocus.app.data.repository.CommunityArtRepository
import com.artoffocus.app.data.repository.FocusSetupRepository
import com.artoffocus.app.data.repository.MuseumRepository
import com.artoffocus.app.data.repository.NotificationPrefsRepository
import com.artoffocus.app.data.repository.FocusSetup
import android.content.Context
import com.artoffocus.app.data.repository.SessionRepository
import com.artoffocus.app.data.repository.SyncRepository
import com.artoffocus.app.domain.achievements.Achievement
import com.artoffocus.app.domain.achievements.earnedAchievements
import com.artoffocus.app.domain.achievements.newlyEarned
import com.artoffocus.app.domain.profile.statistics
import com.artoffocus.app.notifications.AppNotifier
import com.artoffocus.app.notifications.NotificationScheduler
import com.artoffocus.app.notifications.SessionService
import com.artoffocus.app.domain.focus.ArtSource
import com.artoffocus.app.domain.focus.AUTO_CONTINUE_MS
import com.artoffocus.app.domain.focus.AWAY_PROMPT_MS
import com.artoffocus.app.domain.focus.CANCEL_COUNTDOWN_S
import com.artoffocus.app.domain.focus.paintingHint
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
import com.artoffocus.app.domain.focus.hasLiveSession
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

/** Where the next sitting's art should come from. Community is the default. */
enum class ArtChoice { COMMUNITY, OWN_PICTURE }

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
    /** Which source the next sitting draws from. Ignored while a painting is active (I6). */
    val artChoice: ArtChoice = ArtChoice.COMMUNITY,
    /**
     * The community painting lined up for the next sitting, chosen while idle so [hint] can
     * honestly describe it.
     *
     * Kept separate from [painting] on purpose: a non-null [painting] is the app's signal
     * that a painting is ACTIVE (I6) and locks the tempo, offers Abandon, and blocks setup
     * changes. A painting merely lined up must trigger none of that.
     */
    val pendingCommunity: CommunityArtRepository.CommunityChoice? = null,
    /** A picture the user picked in the Pictures tab, waiting for the next sitting. */
    val pendingPicture: String? = null,
    /** The curiosity hook shown before starting; null when there is nothing honest to say. */
    val hint: String? = null,
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
    private val communityArtRepository: CommunityArtRepository,
    private val notificationPrefsRepository: NotificationPrefsRepository,
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
            // A session that survived process death (I7) has no service: whatever killed the
            // process killed that too. Restarting it here is legal because the app is on screen.
            if (hasLiveSession(session.status)) SessionService.start(appContext)
            startTicker()
            // Both are background work, deliberately after the UI has its state: neither may
            // ever be something a sitting waits on (I7).
            refillCommunityArt()
            lineUpNextPainting()
        }
    }

    /**
     * Downloads community paintings into the local cache. The only place the app touches the
     * network for art, and never while a session is running — [next] reads the cache alone.
     */
    private fun refillCommunityArt() {
        viewModelScope.launch {
            val added = runCatching { communityArtRepository.topUp() }.getOrDefault(0)
            // A fresh install starting offline has nothing lined up; once art arrives, the
            // idle screen should pick it up rather than wait for the next app launch.
            if (added > 0 && _state.value.pendingCommunity == null) lineUpNextPainting()
        }
    }

    /**
     * Chooses the next community painting while the user is still idle, so the hint can
     * describe the painting they will actually get rather than a vague guess.
     *
     * Does nothing when a painting is already active: I6 means that painting is the one they
     * are working on, and nothing is lined up behind it.
     */
    private fun lineUpNextPainting() {
        viewModelScope.launch {
            if (_state.value.painting != null) return@launch
            val choice = runCatching { communityArtRepository.peek() }.getOrNull()
            _state.update {
                it.copy(
                    pendingCommunity = choice,
                    hint = choice?.let { c -> paintingHint(c.artwork.toPreviewPainting()) },
                )
            }
        }
    }

    /**
     * The hint reads a [Painting], but at this point no painting has been assigned a seed —
     * that happens at start. A throwaway stand-in carries the source and credit the hint
     * actually uses; it is never stored and never becomes the real painting.
     */
    private fun ChosenArtwork.toPreviewPainting() = Painting(
        id = "preview",
        title = title.orEmpty(),
        seed = 0L,
        imageRef = imageRef,
        source = source,
        credit = credit,
    )

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
        // Earned achievements BEFORE this painting lands, so the ones it causes can be told
        // apart from the ones already held. Read before the write, or everything looks new.
        val earnedBefore = earnedAchievements(statistics(museumRepository.getAll(), now)).toSet()

        // I12 — the museum record is written before the active slot is cleared.
        museumRepository.recordCompletion(record)
        notifyNewAchievements(earnedBefore, now)
        artworkRepository.clearActive()
        sessionRepository.clear()
        // The session is over: take the ongoing notification down, and cancel the rest alarm,
        // which was previously left armed to wake the device for a notification that the
        // receiver would then correctly decline to post.
        SessionService.stop(appContext)
        NotificationScheduler.cancelRestEnd(appContext)
        // M6 — the second of web's two sync moments. Deliberately after the local write and
        // after the session has ended, never during one (I7): if this fails or is slow, the
        // painting is already safe in Room and the next sync picks it up.
        syncRepository.pushLocalWork()
        _state.update {
            it.copy(session = IDLE_SESSION, painting = null, activePaintingStartedAt = 0L, justCompletedPainting = record)
        }
        // The museum just gained a painting and the cache just lost one: restock, then line
        // up what comes next so the idle screen has a hint ready.
        refillCommunityArt()
        lineUpNextPainting()
    }

    /**
     * Posts a notification for each achievement this painting just earned.
     *
     * `notifyAchievement` existed with no caller at all, so the "Achievement earned" toggle in
     * Profile controlled nothing while the sign-in copy promised it would. This is what makes
     * that promise true.
     *
     * Derived by diffing earned-before against earned-after rather than by any new rule:
     * [earnedAchievements] stays the single source of truth for what counts as earned, so I15
     * ("an earned achievement is never removed") is untouched — nothing here can un-earn one.
     */
    private suspend fun notifyNewAchievements(earnedBefore: Set<Achievement>, now: Long) {
        if (!notificationPrefsRepository.load().achievement) return
        val earnedAfter = earnedAchievements(statistics(museumRepository.getAll(), now))
        newlyEarned(earnedBefore, earnedAfter)
            .forEach { AppNotifier.notifyAchievement(appContext, it.title) }
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
     * Starts a sitting with the art the user has chosen: the lined-up community painting, or
     * one of their own pictures.
     *
     * Everything needed is already on disk, so this never waits on the network (I7). If no
     * art is available — offline on a fresh install with an empty cache — the painting falls
     * back to the generated gradient, which is the only place gradients still appear.
     *
     * I6 still holds: if a painting is already active, [ArtworkRepository.assignIfAbsent]
     * returns it and the choice is ignored rather than replacing live work.
     */
    fun startFocusSession() {
        if (_state.value.session.status != SessionStatus.IDLE) return
        viewModelScope.launch {
            val current = _state.value
            val now = System.currentTimeMillis()

            val chosen = when (current.artChoice) {
                ArtChoice.OWN_PICTURE -> current.pendingPicture
                    ?.let { ChosenArtwork(imageRef = it, source = ArtSource.OWN_PHOTO) }
                ArtChoice.COMMUNITY -> current.pendingCommunity?.artwork
            }

            val painting = artworkRepository.assignIfAbsent(seed = now, startedAt = now, artwork = chosen)

            // Spend the community painting only once a sitting has really begun, and only if
            // it is the one that got used — assignIfAbsent may have returned a pre-existing
            // active painting instead (I6).
            current.pendingCommunity
                ?.takeIf { current.artChoice == ArtChoice.COMMUNITY && painting.imageRef == it.artwork.imageRef }
                ?.let { communityArtRepository.commit(it) }

            val session = startSession(now)
            persistSession(session)
            // The foreground service is what stops the OS freezing or killing this process the
            // moment the app is backgrounded. Started here, from a user tap, because API 31+
            // forbids starting one from the background.
            SessionService.start(appContext)
            _state.update {
                it.copy(
                    painting = painting,
                    activePaintingStartedAt = now,
                    pendingCommunity = null,
                    pendingPicture = null,
                    hint = null,
                )
            }
        }
    }

    /** The Pictures tab's choice, held until the next sitting starts. Never starts one itself. */
    fun choosePicture(ref: String) {
        _state.update { it.copy(pendingPicture = ref, artChoice = ArtChoice.OWN_PICTURE) }
    }

    fun selectArtChoice(choice: ArtChoice) {
        _state.update { it.copy(artChoice = choice) }
        if (choice == ArtChoice.COMMUNITY && _state.value.pendingCommunity == null) lineUpNextPainting()
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
        _state.update { it.copy(cancelCountdown = CANCEL_COUNTDOWN_S) }
        cancelCountdownJob = viewModelScope.launch {
            for (remaining in CANCEL_COUNTDOWN_S - 1 downTo 0) {
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
        SessionService.stop(appContext)
        viewModelScope.launch {
            sessionRepository.clear()
            _state.update { it.copy(session = cancelSession(), cancelCountdown = null) }
        }
    }

    /** Discards the active painting entirely — reachable only from the idle, locked-pair state. */
    fun abandonPainting() {
        viewModelScope.launch {
            NotificationScheduler.cancelRestEnd(appContext)
            SessionService.stop(appContext)
            artworkRepository.clearActive()
            sessionRepository.clear()
            _state.update {
                it.copy(session = cancelSession(), painting = null, activePaintingStartedAt = 0L)
            }
            // Nothing is active now, so something should be lined up for the next attempt.
            lineUpNextPainting()
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
        // Idempotent, and needed here as well as at Begin: the alarm below is the *backup*, and
        // it is the service that stops the OS killing this process and cancelling that alarm.
        SessionService.start(appContext)
        // F14.1 — an exact alarm at the wall-clock rest end; the receiver re-checks state before
        // posting. Keyed by sitting index, which survives the RESTING -> REST_COMPLETE transition
        // (restStartedAt does not, which is why this used to fire only when the app was closed).
        NotificationScheduler.scheduleRestEnd(
            context = appContext,
            restStartedAt = now,
            restMinutes = minutes,
            pieces = _state.value.pieces,
            sittingIndex = resting.sittingIndex,
        )
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
        SessionService.start(appContext)
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
        private val communityArtRepository: CommunityArtRepository,
        private val notificationPrefsRepository: NotificationPrefsRepository,
        private val appContext: Context,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return FocusViewModel(
                focusSetupRepository,
                sessionRepository,
                artworkRepository,
                museumRepository,
                syncRepository,
                communityArtRepository,
                notificationPrefsRepository,
                appContext,
            ) as T
        }
    }
}
