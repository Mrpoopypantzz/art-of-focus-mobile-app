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
import androidx.compose.foundation.layout.heightIn
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.artoffocus.app.domain.focus.AUTO_CONTINUE_MS
import com.artoffocus.app.domain.focus.CANCEL_COUNTDOWN_S
import com.artoffocus.app.domain.focus.CONTINUE_LOCK_MS
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
import com.artoffocus.app.domain.focus.formatCountdownOf
import com.artoffocus.app.domain.focus.formatElapsed
import com.artoffocus.app.domain.focus.formatElapsedOf
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
import com.artoffocus.app.ui.common.DeskMood
import com.artoffocus.app.ui.common.DeskState
import com.artoffocus.app.ui.common.StudioDesk
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
    var picturesSheetOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isLoading) return@Box

        Column(modifier = Modifier.fillMaxSize()) {
            StudioHeader()

            // Shared by idle and running: a band here subtracts height once and lets the
            // content below reflow, rather than stacking under content that cannot scroll.
            StudioDesk(state = deskStateFor(state), modifier = Modifier.padding(horizontal = AppSpacing.s5))

            Box(modifier = Modifier.fillMaxSize()) {
                when (state.session.status) {
                    SessionStatus.IDLE -> IdleContent(
                        state = state,
                        viewModel = viewModel,
                        onOpenSetup = { setupSheetOpen = true },
                        onOpenPictures = { picturesSheetOpen = true },
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

        if (picturesSheetOpen) {
            val container = (androidx.compose.ui.platform.LocalContext.current.applicationContext
                as com.artoffocus.app.ArtOfFocusApp).container
            val photoViewModel: com.artoffocus.app.ui.photos.PhotoLibraryViewModel = viewModel(
                factory = com.artoffocus.app.ui.photos.PhotoLibraryViewModel.Factory(
                    container.photoStore,
                    container.museumRepository,
                    container.artworkRepository,
                ),
            )
            com.artoffocus.app.ui.photos.PhotoLibrarySheet(
                viewModel = photoViewModel,
                onChoose = { ref ->
                    picturesSheetOpen = false
                    viewModel.startFocusSession(imageRef = ref)
                },
                onDismiss = { picturesSheetOpen = false },
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
            .padding(horizontal = AppSpacing.s5)
            .padding(top = AppSpacing.s6, bottom = AppSpacing.s4),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("THE STUDIO", style = EyebrowStyle)
        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s1))
        Text("Art of Focus", style = MaterialTheme.typography.displayMedium)
        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s3))
        // A hairline rule under the wordmark: gives the masthead a base to sit on, and
        // separates "what this is" from "what you're about to do" without adding a box.
        HairlineRule(modifier = Modifier.width(56.dp))
    }
}

/** A centred hairline that fades at both ends — the app's only divider treatment. */
@Composable
private fun HairlineRule(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        AppColors.LineStrong.copy(alpha = 0f),
                        AppColors.LineStrong,
                        AppColors.LineStrong.copy(alpha = 0f),
                    ),
                ),
            ),
    )
}

/**
 * Session state → what the desk shows. Read-only: this derives a picture from state the
 * session already owns and never writes back.
 *
 * `elapsedMs` stays frozen at the full sitting target once a sitting completes, so
 * SITTING_COMPLETE and the rest states are handled explicitly rather than falling through
 * to the sitting formula — otherwise a finished sitting would read as a full glass.
 */
private fun deskStateFor(state: FocusUiState): DeskState = when (state.session.status) {
    SessionStatus.IDLE -> DeskState(DeskMood.IDLE, fill = 1f)

    SessionStatus.RUNNING, SessionStatus.PAUSED -> {
        val targetMs = state.setup.sittingMinutes * 60_000L
        val elapsed = elapsedMs(state.session, state.now).toFloat()
        // Sand remaining, so a fresh sitting starts full and drains as the work is done.
        DeskState(DeskMood.WORKING, fill = (1f - elapsed / targetMs).coerceIn(0f, 1f))
    }

    SessionStatus.RESTING, SessionStatus.REST_COMPLETE -> {
        val restTotalMs = state.session.restMinutes * 60_000L
        val fill = if (restTotalMs > 0L) {
            (restRemainingMs(state.session, state.now).toFloat() / restTotalMs).coerceIn(0f, 1f)
        } else {
            0f
        }
        DeskState(DeskMood.RESTING, fill = fill)
    }

    SessionStatus.SITTING_COMPLETE -> DeskState(DeskMood.COMPLETE, fill = 0f)
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
private fun IdleContent(
    state: FocusUiState,
    viewModel: FocusViewModel,
    onOpenSetup: () -> Unit,
    onOpenPictures: () -> Unit,
) {
    val pieces = state.pieces
    val painting = state.painting

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AppSpacing.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The canvas is the hero: it takes the space it needs and the rest of the screen
        // arranges itself around it, rather than everything sharing one centred stack.
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))

        // Capped so a tall canvas can't squeeze the caption and action into each other.
        FramedCanvas(
            pieces = pieces,
            modifier = Modifier
                .fillMaxWidth(CANVAS_WIDTH_FRACTION)
                .heightIn(max = CANVAS_MAX_HEIGHT),
        )

        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s6))

        // Caption beneath the work, gallery-label style: the title leads, the particulars
        // follow in a quieter voice.
        if (painting != null) {
            Text(painting.title, style = MaterialTheme.typography.titleMedium, color = AppColors.Text)
            androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s1))
            Text(
                describeProgress(state.session, pieces),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextDim,
            )
        } else {
            Text(
                buildAnnotatedString {
                    withStyle(StatValueStyle.toSpanStyle()) { append(formatDuration(state.setup.totalMinutes)) }
                    append(" total · ")
                    withStyle(StatValueStyle.toSpanStyle()) { append(formatDuration(state.setup.sittingMinutes)) }
                    append(" per sitting")
                },
                style = MaterialTheme.typography.bodyMedium.copy(color = AppColors.TextDim),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s1))
            Text(
                "$pieces sittings · one puzzle piece each",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextFaint,
            )
        }

        // Fixed, not flexible: the caption belongs to the canvas above it, and the action
        // must never drift up into it however tall the canvas renders.
        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s6))
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))

        // Action block, anchored to the bottom of the composition — the eye travels
        // masthead → canvas → caption → action, in that order, every time.
        PrimaryButton(onClick = viewModel::startFocusSession, modifier = Modifier.fillMaxWidth()) {
            Text("Begin sitting")
        }

        if (painting != null) {
            androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s4))
            Text(
                "Locked to ${formatDuration(state.setup.totalMinutes)} · ${formatDuration(state.setup.sittingMinutes)} " +
                    "while this painting is unfinished.",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextFaint,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s1))
            TextButton(onClick = viewModel::abandonPainting) {
                Text("Abandon this painting", color = AppColors.TextDim, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s2))
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s4)) {
                TextButton(onClick = onOpenSetup) { Text("Change tempo") }
                TextButton(onClick = onOpenPictures) { Text("Your pictures") }
            }
        }

        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s6))
    }
}

/** The canvas fills most of the width — it is the thing the whole screen is about. */
private const val CANVAS_WIDTH_FRACTION = 0.72f
private val CANVAS_MAX_HEIGHT = 380.dp
private const val MOSAIC_WIDTH_FRACTION = 0.74f

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
                        FramedCanvas(pieces, Modifier.width(150.dp), frameWidth = 5.dp)
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
                label = durationLabel,
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
            // Remembered: sittingsFor allocates a fresh list, and an unstable `values`
            // identity retriggers the wheel's scroll-to-selection effect on every frame.
            val sittingValues = remember(draftTotal) { sittingsFor(draftTotal) }
            WheelPicker(
                values = sittingValues,
                selected = draftSitting,
                onSelect = { value -> draftSitting = value },
                label = durationLabel,
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

/**
 * The idle canvas, hung like a picture: a soft cast shadow, a gilded mat, then the tiled
 * work itself. The frame is drawn rather than shipped as an asset so it tints with the
 * palette and stays crisp at any size.
 */
@Composable
private fun FramedCanvas(pieces: Int, modifier: Modifier = Modifier, frameWidth: Dp = FRAME_WIDTH) {
    val frameShape = RoundedCornerShape(AppRadius.lg)

    Box(
        modifier = modifier
            .aspectRatio(4f / 5f)
            .shadow(
                elevation = frameWidth * 1.8f,
                shape = frameShape,
                ambientColor = AppColors.Ink.copy(alpha = 0.30f),
                spotColor = AppColors.Ink.copy(alpha = 0.30f),
            )
            .clip(frameShape)
            .background(gildedFrameBrush)
            .padding(frameWidth),
    ) {
        SetupPuzzlePreview(pieces = pieces, modifier = Modifier.fillMaxSize())
    }
}

private val FRAME_WIDTH = 10.dp

/** The setup-time preview: gilded tiles over a plain surface — no painting exists yet (I6). */
@Composable
private fun SetupPuzzlePreview(pieces: Int, modifier: Modifier = Modifier) {
    val layout = layoutFor(pieces)
    val previewShape = RoundedCornerShape(AppRadius.sm)

    Box(
        modifier = modifier
            .clip(previewShape)
            // Tile gaps show this through, so the seams read as shadow between pieces
            // rather than as white gridlines drawn on top.
            .background(AppColors.Ink.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
            repeat(layout.completeRows) { PreviewRow(cols = layout.cols) }
            if (layout.lastRowCount > 0) PreviewRow(cols = layout.lastRowCount)
        }
    }
}

private val TILE_GAP = 2.dp

@Composable
private fun ColumnScope.PreviewRow(cols: Int) {
    Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
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
        // Top-anchored, not centred: the desk band above is the composition's anchor, and
        // centring leaves a dead gap between it and the canvas whenever the card is small.
        verticalArrangement = Arrangement.spacedBy(AppSpacing.s3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PuzzleMosaic(
            painting = state.painting,
            pieces = state.pieces,
            revealed = revealed,
            // Width-constrained, not height-capped: with a 4:5 aspectRatio a heightIn cap
            // is fought by the ratio and the card overflows its own bounds onto the caption.
            // Bounding the width lets the ratio set the height honestly. Layout only — the
            // tiles, the reveal order and the piece count are untouched.
            modifier = Modifier
                .fillMaxWidth(MOSAIC_WIDTH_FRACTION)
                .padding(vertical = AppSpacing.s2),
        )

        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s2))
        Text(
            describeSessionProgress(progress, state.pieces),
            color = AppColors.TextDim,
            style = MaterialTheme.typography.bodySmall,
        )
        if (!inRest) {
            StopwatchText(
                text = formatElapsed(elapsedMs(state.session, state.now)),
                isLive = state.session.status == SessionStatus.RUNNING,
            )
        }

        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s6))

        when (state.session.status) {
            SessionStatus.RUNNING -> RunningControls(viewModel)
            SessionStatus.PAUSED -> PausedControls(viewModel)
            else -> Unit
        }
    }

    // Hoisted out of the control variants: cancelling is reachable while running AND while
    // paused, so the confirmation belongs to the request, not to one button's composable.
    state.cancelCountdown?.let { CancelSheet(cancelCountdown = it, viewModel = viewModel) }

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

/** Stable identity — `::formatDuration` allocates a new reference per composition, which
 *  makes WheelPicker's `label` param unstable and defeats skipping. */
private val durationLabel: (Int) -> String = { formatDuration(it) }

private val gildedTileBrush = Brush.linearGradient(listOf(AppColors.GildedStart, AppColors.GildedEnd))

/**
 * Frame moulding in dark wood, not gilt — the tiles are already gold, and a gold frame around
 * gold tiles has nothing to contain. Light catches the top-left and falls away, with a thin
 * bright band where the moulding turns.
 */
private val gildedFrameBrush = Brush.linearGradient(
    0.00f to AppColors.InkSoft,
    0.50f to AppColors.Ink,
    1.00f to AppColors.InkSoft,
)
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
        painting?.let {
            PaintingArt(
                painting = it,
                modifier = Modifier.matchParentSize(),
                // The card is a fixed 4:5, so a photo fills it rather than letterboxing —
                // otherwise tiles would sit over empty ground. Display-only; the stored
                // file is never altered.
                contentScale = ContentScale.Crop,
            )
        }

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
private fun RunningControls(viewModel: FocusViewModel) {
    SessionControlBar(
        primaryLabel = "Pause",
        onPrimary = viewModel::pause,
        onCancel = viewModel::requestCancel,
    )
}

@Composable
private fun PausedControls(viewModel: FocusViewModel) {
    SessionControlBar(
        primaryLabel = "Resume",
        onPrimary = viewModel::resume,
        onCancel = viewModel::requestCancel,
    )
}

/**
 * Session controls in the app's own pill idiom — the same ink-fill / sunken-surface pair the
 * tab bar and every toggle row already use. Two separate pills, not one divided capsule: the
 * actions are unrelated, and the shared outline was claiming a kinship they do not have.
 *
 * Cancel stays the quieter of the two (I9 — quitting has friction), but keeps a real pill
 * target rather than collapsing to bare text.
 */
@Composable
private fun SessionControlBar(primaryLabel: String, onPrimary: () -> Unit, onCancel: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s3)) {
        SessionPill(
            label = primaryLabel,
            fill = AppColors.Ink,
            content = AppColors.OnInk,
            onClick = onPrimary,
        )
        SessionPill(
            label = "Cancel",
            fill = AppColors.SurfaceSunken,
            content = AppColors.TextDim,
            onClick = onCancel,
        )
    }
}

@Composable
private fun SessionPill(label: String, fill: Color, content: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(SESSION_CONTROL_HEIGHT)
            .clip(AppRadius.pill)
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.s8),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = content, style = MaterialTheme.typography.bodyLarge)
    }
}

private val SESSION_CONTROL_HEIGHT = 44.dp

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
                    Text(
                        if (cancelCountdown > 0) {
                            "Cancel in ${formatCountdownOf(cancelCountdown, CANCEL_COUNTDOWN_S)}"
                        } else {
                            "Cancel sitting"
                        },
                    )
                }
            }
        },
    ) {
        Text(
            "Starting is one tap; leaving takes a moment. Give yourself the " +
                "$CANCEL_COUNTDOWN_S seconds — the urge to stop often passes.",
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
        Text(
            "Continuing in ${formatCountdownOf(seconds, (AUTO_CONTINUE_MS / 1000).toInt())} " +
                "unless you take a rest.",
            style = MaterialTheme.typography.bodySmall,
        )
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
                val restValues = remember { restOptions() }
                WheelPicker(values = restValues, selected = minutes, onSelect = { minutes = it }, label = durationLabel)
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
                        Text(
                            if (locked) {
                                val lockSeconds = ceil(lockRemaining / 1000.0).toInt()
                                "Continue in ${formatCountdownOf(lockSeconds, (CONTINUE_LOCK_MS / 1000).toInt())}"
                            } else {
                                "Continue to next sitting"
                            },
                        )
                    }
                },
            ) {
                Text(
                    if (done) {
                        "Rest finished"
                    } else {
                        "${formatElapsedOf(remaining, session.restMinutes * 60_000L)} remaining"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
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
