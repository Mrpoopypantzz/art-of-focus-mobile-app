package com.artoffocus.app.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.artoffocus.app.domain.focus.FocusLevel
import com.artoffocus.app.domain.focus.Painting
import com.artoffocus.app.domain.focus.SessionState
import com.artoffocus.app.domain.focus.SessionStatus
import com.artoffocus.app.domain.focus.TOTAL_LADDER
import com.artoffocus.app.domain.focus.DEFAULT_REST
import com.artoffocus.app.domain.focus.describePieces
import com.artoffocus.app.domain.focus.describeProgress
import com.artoffocus.app.domain.focus.describeSessionProgress
import com.artoffocus.app.domain.focus.elapsedMs
import com.artoffocus.app.domain.focus.formatAway
import com.artoffocus.app.domain.focus.formatDuration
import com.artoffocus.app.domain.focus.formatElapsed
import com.artoffocus.app.domain.focus.layoutFor
import com.artoffocus.app.domain.focus.pieceCount
import com.artoffocus.app.domain.focus.restLockRemainingMs
import com.artoffocus.app.domain.focus.restRemainingMs
import com.artoffocus.app.domain.focus.revealedCount
import com.artoffocus.app.domain.focus.restOptions
import com.artoffocus.app.domain.focus.sessionProgress
import com.artoffocus.app.domain.focus.sittingAnchors
import com.artoffocus.app.domain.focus.sittingsFor
import com.artoffocus.app.domain.focus.totalAnchors
import com.artoffocus.app.ui.common.AppSheet
import com.artoffocus.app.ui.common.DangerButton
import com.artoffocus.app.ui.common.PaintingArt
import com.artoffocus.app.ui.common.PrimaryButton
import com.artoffocus.app.ui.common.QuietButton
import com.artoffocus.app.ui.common.WheelPicker
import com.artoffocus.app.ui.theme.AppColors
import com.artoffocus.app.ui.theme.AppRadius
import com.artoffocus.app.ui.theme.AppSpacing
import com.artoffocus.app.ui.theme.EyebrowStyle
import com.artoffocus.app.ui.theme.StatValueStyle
import com.artoffocus.app.ui.theme.screenPadding
import kotlin.math.abs
import kotlin.math.ceil

@Composable
fun FocusScreen(viewModel: FocusViewModel) {
    val state by viewModel.state.collectAsState()
    var setupSheetOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isLoading) return@Box

        Column(modifier = Modifier.fillMaxSize()) {
            StudioHeader()

            Box(modifier = Modifier.fillMaxSize()) {
                when (state.session.status) {
                    SessionStatus.IDLE -> IdleContent(
                        state = state,
                        viewModel = viewModel,
                        onOpenSetup = { setupSheetOpen = true },
                    )
                    else -> RunningContent(state = state, viewModel = viewModel)
                }
            }
        }

        // Committed only on "Start" (matches FocusSetupSheet.tsx's local draft state) —
        // scrolling the wheels no longer writes to DataStore on every tick.
        if (setupSheetOpen) {
            SetupSheet(
                initialTotal = state.setup.totalMinutes,
                initialSitting = state.setup.sittingMinutes,
                onDismiss = { setupSheetOpen = false },
                onStart = { total, sitting ->
                    viewModel.selectSetup(total, sitting)
                    viewModel.startFocusSession()
                    setupSheetOpen = false
                },
            )
        }

        state.awayGapMs?.let { gap ->
            AwaySheet(gapMs = gap, onKeep = viewModel::keepAwayTime, onDiscard = viewModel::discardAwayTime)
        }

        state.justCompletedPainting?.let { record ->
            PaintingCompleteSheet(record = record, onDismiss = viewModel::acknowledgePaintingCompletion)
        }
    }
}

/** Persistent chrome — rendered above every session state, not gated on it (matches FocusScreen.tsx:289-292). */
@Composable
private fun StudioHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.s5, vertical = AppSpacing.s4),
    ) {
        Text("THE STUDIO", style = EyebrowStyle)
        Text("Art of Focus", style = MaterialTheme.typography.displayMedium)
    }
}

/** Short/Medium/Long — the web app's tier-anchor labels, distinct from the Profile's Beginner/Intermediate/Advanced. */
private fun tierLabel(level: FocusLevel): String = when (level) {
    FocusLevel.BEGINNER -> "Short"
    FocusLevel.INTERMEDIATE -> "Medium"
    FocusLevel.ADVANCED -> "Long"
}

/** Total changed — carry the sitting forward to the closest still-valid option rather than resetting to the first. */
private fun closestSitting(total: Int, previousSitting: Int): Int {
    val options = sittingsFor(total)
    return options.minByOrNull { abs(it - previousSitting) } ?: options.first()
}

/**
 * The idle screen (FocusScreen.tsx's non-active render). Never the wizard itself — that lives in
 * [SetupSheet], reachable from here via "Change tempo" (or "Begin sitting" starts immediately with
 * the current pair). While a painting is active (I6 lock), the pair can't be changed at all short
 * of abandoning it.
 */
@Composable
private fun IdleContent(state: FocusUiState, viewModel: FocusViewModel, onOpenSetup: () -> Unit) {
    val pieces = state.pieces
    val painting = state.painting

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AppSpacing.screenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SetupPuzzlePreview(pieces)
        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s5))

        Text(
            "${formatDuration(state.setup.totalMinutes)} total · ${formatDuration(state.setup.sittingMinutes)} per sitting",
            style = MaterialTheme.typography.bodyMedium,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s1))
        Text(
            if (painting != null) {
                "${painting.title} · ${describeProgress(state.session, pieces)}"
            } else {
                "$pieces sittings · one puzzle piece each"
            },
            style = MaterialTheme.typography.bodySmall,
        )

        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s6))

        PrimaryButton(onClick = viewModel::startFocusSession, modifier = Modifier.fillMaxWidth()) {
            Text("Begin sitting")
        }

        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s3))

        if (painting != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Locked to ${formatDuration(state.setup.totalMinutes)} · ${formatDuration(state.setup.sittingMinutes)} " +
                        "while this painting is unfinished. Finish or abandon it to change your tempo.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = AppSpacing.s2),
                )
                TextButton(onClick = viewModel::abandonPainting) { Text("Abandon this painting") }
            }
        } else {
            TextButton(onClick = onOpenSetup) { Text("Change tempo") }
        }
    }
}

/**
 * "Set your tempo" — a dismissible sheet (FocusSetupSheet.tsx), not a screen. Holds its own draft
 * state; nothing is persisted until "Start" is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupSheet(
    initialTotal: Int,
    initialSitting: Int,
    onDismiss: () -> Unit,
    onStart: (Int, Int) -> Unit,
) {
    var draftTotal by remember { mutableStateOf(initialTotal) }
    var draftSitting by remember { mutableStateOf(initialSitting) }
    val pieces = pieceCount(draftTotal, draftSitting)

    AppSheet(
        onDismissRequest = onDismiss,
        title = "Set your tempo",
        subtitle = "Choose how long the whole painting takes, then how long one sitting runs.",
        footer = {
            PrimaryButton(onClick = { onStart(draftTotal, draftSitting) }, modifier = Modifier.fillMaxWidth()) {
                Text("Start")
            }
        },
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            if (pieces != null) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SetupPuzzlePreview(pieces)
                        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s2))
                        Text(
                            "$pieces piece${if (pieces == 1) "" else "s"} to uncover",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s6))
            }

            StepHeader(1, "Total Focus", "How much deep work this masterpiece is worth.")
            androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s3))
            TierAnchorRow(
                anchors = totalAnchors().map { it.level to it.value },
                selected = draftTotal,
                onSelect = { value ->
                    draftTotal = value
                    draftSitting = closestSitting(value, draftSitting)
                },
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s2))
            WheelPicker(
                values = TOTAL_LADDER,
                selected = draftTotal,
                onSelect = { value ->
                    draftTotal = value
                    draftSitting = closestSitting(value, draftSitting)
                },
                label = ::formatDuration,
            )

            androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s6))

            StepHeader(2, "Single Sitting", "One unbroken stretch at the easel.")
            androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s3))
            TierAnchorRow(
                anchors = sittingAnchors(draftTotal).map { it.level to it.value },
                selected = draftSitting,
                onSelect = { value -> draftSitting = value },
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s2))
            WheelPicker(
                values = sittingsFor(draftTotal),
                selected = draftSitting,
                onSelect = { value -> draftSitting = value },
                label = ::formatDuration,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s2))
            Text(describePieces(draftTotal, draftSitting) ?: " ", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StepHeader(number: Int, title: String, hint: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s3)) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(AppColors.Ink),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), color = AppColors.OnInk, style = MaterialTheme.typography.labelSmall)
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TierAnchorRow(anchors: List<Pair<FocusLevel, Int>>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s2), modifier = Modifier.fillMaxWidth()) {
        anchors.forEach { (level, value) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(AppRadius.pill)
                    .background(if (isSelected) AppColors.Ink else AppColors.SurfaceSunken)
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tierLabel(level).uppercase(),
                    color = if (isSelected) AppColors.OnInk else AppColors.TextDim,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/** The setup-time preview: gilded tiles over a plain surface — no painting exists yet (I6). */
@Composable
private fun SetupPuzzlePreview(pieces: Int, modifier: Modifier = Modifier) {
    val layout = layoutFor(pieces)
    val previewShape = RoundedCornerShape(AppRadius.lg)

    Box(
        modifier = modifier
            .width(150.dp)
            .aspectRatio(4f / 5f)
            .clip(previewShape)
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Ink.copy(alpha = 0.28f), previewShape),
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            repeat(layout.completeRows) { PreviewRow(cols = layout.cols) }
            if (layout.lastRowCount > 0) PreviewRow(cols = layout.lastRowCount)
        }
    }
}

@Composable
private fun ColumnScope.PreviewRow(cols: Int) {
    Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        repeat(cols) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(gildedTileBrush))
        }
    }
}

@Composable
private fun RunningContent(state: FocusUiState, viewModel: FocusViewModel) {
    val progress = sessionProgress(state.session, state.setup.sittingMinutes, state.pieces, state.now)
    val revealed = revealedCount(state.session, state.pieces)
    val inRest = state.session.status == SessionStatus.RESTING || state.session.status == SessionStatus.REST_COMPLETE
    // UI-only, mirrors FocusScreen.tsx's `pickingRest` — the session doesn't become `resting`
    // until a duration is actually chosen (RestSheet.tsx's "picking" phase).
    var pickingRest by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(AppSpacing.screenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PuzzleMosaic(
            painting = state.painting,
            pieces = state.pieces,
            revealed = revealed,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )

        Text(describeSessionProgress(progress, state.pieces), style = MaterialTheme.typography.bodyMedium)
        if (!inRest) {
            StopwatchText(
                text = formatElapsed(elapsedMs(state.session, state.now)),
                isLive = state.session.status == SessionStatus.RUNNING,
            )
        }

        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))

        when (state.session.status) {
            SessionStatus.RUNNING -> RunningControls(state, viewModel)
            SessionStatus.PAUSED -> PausedControls(viewModel)
            else -> Unit
        }
    }

    when {
        pickingRest -> RestSheet(
            phase = RestPhase.PICKING,
            session = state.session,
            now = state.now,
            viewModel = viewModel,
            onStartRest = { minutes ->
                pickingRest = false
                viewModel.chooseRest(minutes)
            },
            onCancelPick = { pickingRest = false },
        )
        state.session.status == SessionStatus.SITTING_COMPLETE -> SittingCompleteSheet(
            state = state,
            viewModel = viewModel,
            onTakeRest = { pickingRest = true },
        )
        state.session.status == SessionStatus.RESTING -> RestSheet(RestPhase.RESTING, state.session, state.now, viewModel)
        state.session.status == SessionStatus.REST_COMPLETE -> RestSheet(RestPhase.REST_COMPLETE, state.session, state.now, viewModel)
    }
}

private val gildedTileBrush = Brush.linearGradient(listOf(AppColors.GildedStart, AppColors.GildedEnd))
private val cardShape = RoundedCornerShape(AppRadius.lg)

/**
 * The painting card: [PaintingArt] fills the frame, gilded tiles cover the pieces not yet
 * earned, and a revealed tile renders as fully transparent so the artwork beneath shows
 * through — this is the fix for the previous version, which rendered revealed pieces as a
 * flat color instead of the actual painting.
 */
@Composable
private fun PuzzleMosaic(painting: Painting?, pieces: Int, revealed: Int, modifier: Modifier = Modifier) {
    val layout = layoutFor(pieces)

    Box(
        modifier = modifier
            .aspectRatio(4f / 5f)
            .shadow(elevation = 8.dp, shape = cardShape, ambientColor = AppColors.Ink.copy(alpha = 0.08f), spotColor = AppColors.Ink.copy(alpha = 0.08f))
            .clip(cardShape)
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Ink.copy(alpha = 0.28f), cardShape),
    ) {
        painting?.let { PaintingArt(painting = it, modifier = Modifier.matchParentSize()) }

        var index = 0
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            repeat(layout.completeRows) {
                MosaicRow(cols = layout.cols, revealed = revealed, startIndex = index)
                index += layout.cols
            }
            if (layout.lastRowCount > 0) {
                MosaicRow(cols = layout.lastRowCount, revealed = revealed, startIndex = index)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.MosaicRow(cols: Int, revealed: Int, startIndex: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().weight(1f),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        repeat(cols) { i ->
            val tileIndex = startIndex + i
            val isRevealed = tileIndex < revealed
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (isRevealed) Modifier else Modifier.background(gildedTileBrush)),
            )
        }
    }
}

@Composable
private fun RunningControls(state: FocusUiState, viewModel: FocusViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuietButton(onClick = viewModel::pause) { Text("Pause") }
        CancelButton(state.cancelCountdown, viewModel)
    }
}

@Composable
private fun PausedControls(viewModel: FocusViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryButton(onClick = viewModel::resume) { Text("Resume") }
        CancelButton(null, viewModel)
    }
}

@Composable
private fun CancelButton(cancelCountdown: Int?, viewModel: FocusViewModel) {
    TextButton(onClick = viewModel::requestCancel) { Text("Cancel") }

    if (cancelCountdown != null) {
        CancelSheet(cancelCountdown = cancelCountdown, viewModel = viewModel)
    }
}

/** "Leave this sitting?" — mirrors the web's shared Sheet.tsx modal (SessionControls.tsx). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CancelSheet(cancelCountdown: Int, viewModel: FocusViewModel) {
    AppSheet(
        onDismissRequest = viewModel::abortCancelRequest,
        title = "Leave this sitting?",
        subtitle = "The time you have focused so far will not be saved.",
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.s2)) {
                PrimaryButton(onClick = viewModel::abortCancelRequest, modifier = Modifier.fillMaxWidth()) {
                    Text("Keep focusing")
                }
                DangerButton(
                    onClick = viewModel::confirmCancel,
                    enabled = cancelCountdown == 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (cancelCountdown > 0) "Cancel in ${cancelCountdown}s" else "Cancel sitting")
                }
            }
        },
    ) {
        Text(
            "Starting is one tap; leaving takes a moment. Give yourself the five seconds — the urge to stop often passes.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** "Sitting complete" — SittingCompleteSheet.tsx, with the I10 auto-continue countdown made visible. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SittingCompleteSheet(state: FocusUiState, viewModel: FocusViewModel, onTakeRest: () -> Unit) {
    val progress = sessionProgress(state.session, state.setup.sittingMinutes, state.pieces, state.now)
    val seconds = ceil((state.remainingAutoContinueMs ?: 0L) / 1000.0).toInt()

    AppSheet(
        onDismissRequest = viewModel::chooseContinue,
        title = "Sitting complete",
        subtitle = "${formatDuration(state.setup.sittingMinutes)} of focus, done. ${describeSessionProgress(progress, state.pieces)}.",
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s2), modifier = Modifier.fillMaxWidth()) {
                QuietButton(onClick = onTakeRest, modifier = Modifier.weight(1f)) {
                    Text("Take rest")
                }
                PrimaryButton(onClick = viewModel::chooseContinue, modifier = Modifier.weight(1f)) {
                    Text("Continue")
                }
            }
        },
    ) {
        Text("Continuing in ${seconds}s unless you take a rest.", style = MaterialTheme.typography.bodySmall)
    }
}

private enum class RestPhase { PICKING, RESTING, REST_COMPLETE }

/** One sheet, three phases — RestSheet.tsx. Picking is reached from [SittingCompleteSheet]'s "Take rest". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestSheet(
    phase: RestPhase,
    session: SessionState,
    now: Long,
    viewModel: FocusViewModel,
    onStartRest: (Int) -> Unit = {},
    onCancelPick: () -> Unit = {},
) {
    when (phase) {
        RestPhase.PICKING -> {
            var minutes by remember { mutableStateOf(DEFAULT_REST) }
            AppSheet(
                onDismissRequest = onCancelPick,
                title = "Take a rest",
                subtitle = "Step away. The painting waits.",
                footer = {
                    PrimaryButton(onClick = { onStartRest(minutes) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Start ${formatDuration(minutes)} rest")
                    }
                },
            ) {
                WheelPicker(values = restOptions(), selected = minutes, onSelect = { minutes = it }, label = ::formatDuration)
            }
        }
        RestPhase.RESTING, RestPhase.REST_COMPLETE -> {
            val remaining = restRemainingMs(session, now)
            val lockRemaining = restLockRemainingMs(session, now)
            val done = phase == RestPhase.REST_COMPLETE
            val locked = !done && lockRemaining > 0

            AppSheet(
                onDismissRequest = viewModel::chooseContinue,
                title = if (done) "Rest complete" else "Resting",
                subtitle = if (done) "Whenever you are ready." else "${formatDuration(session.restMinutes)} away from the easel.",
                footer = {
                    PrimaryButton(onClick = viewModel::chooseContinue, enabled = !locked, modifier = Modifier.fillMaxWidth()) {
                        Text(if (locked) "Continue in ${ceil(lockRemaining / 1000.0).toInt()}s" else "Continue to next sitting")
                    }
                },
            ) {
                Text(if (done) "Rest finished" else "${formatElapsed(remaining)} remaining", style = MaterialTheme.typography.bodyMedium)
                if (locked) {
                    androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s2))
                    Text("Settle in first — a rest cut short is not a rest.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AwaySheet(gapMs: Long, onKeep: () -> Unit, onDiscard: () -> Unit) {
    AppSheet(
        onDismissRequest = onKeep,
        title = "You were away ${formatAway(gapMs)}",
        subtitle = "Should that time count towards this sitting?",
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s2), modifier = Modifier.fillMaxWidth()) {
                QuietButton(onClick = onDiscard, modifier = Modifier.weight(1f)) { Text("Discard it") }
                PrimaryButton(onClick = onKeep, modifier = Modifier.weight(1f)) { Text("Keep it") }
            }
        },
    ) {
        Text("The timer kept running while the app was in the background.", style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaintingCompleteSheet(record: com.artoffocus.app.domain.museum.CompletedPainting, onDismiss: () -> Unit) {
    AppSheet(
        onDismissRequest = onDismiss,
        title = "Painting complete",
        subtitle = record.painting.title,
        footer = {
            PrimaryButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Move to Museum") }
        },
    ) {
        Text("Every piece uncovered. The painting is yours.", style = MaterialTheme.typography.bodyMedium)
        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s4))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            FinaleStat("Focus", formatDuration(record.totalMinutes))
            FinaleStat("Sittings", record.pieces.toString())
            FinaleStat("Each", formatDuration(record.sittingMinutes))
        }
    }
}

@Composable
private fun FinaleStat(label: String, value: String) {
    Column {
        Text(label.uppercase(), style = EyebrowStyle)
        Text(value, style = StatValueStyle)
    }
}
