package com.artoffocus.app.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.artoffocus.app.domain.focus.FocusLevel
import com.artoffocus.app.domain.focus.Painting
import com.artoffocus.app.domain.focus.SessionStatus
import com.artoffocus.app.domain.focus.TOTAL_LADDER
import com.artoffocus.app.domain.focus.describePieces
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
import com.artoffocus.app.ui.common.DangerButton
import com.artoffocus.app.ui.common.PaintingArt
import com.artoffocus.app.ui.common.PrimaryButton
import com.artoffocus.app.ui.common.QuietButton
import com.artoffocus.app.ui.common.WheelPicker
import com.artoffocus.app.ui.theme.AppColors
import com.artoffocus.app.ui.theme.AppRadius
import com.artoffocus.app.ui.theme.AppSpacing
import com.artoffocus.app.ui.theme.screenPadding
import kotlin.math.abs

@Composable
fun FocusScreen(viewModel: FocusViewModel) {
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isLoading) return@Box

        when (state.session.status) {
            SessionStatus.IDLE -> SetupContent(
                totalMinutes = state.setup.totalMinutes,
                sittingMinutes = state.setup.sittingMinutes,
                onSelect = viewModel::selectSetup,
                onStart = viewModel::startFocusSession,
            )
            else -> RunningContent(state = state, viewModel = viewModel)
        }

        state.awayGapMs?.let { gap ->
            AwayTimeDialog(gapMs = gap, onKeep = viewModel::keepAwayTime, onDiscard = viewModel::discardAwayTime)
        }

        state.justCompletedPainting?.let {
            PaintingCompleteDialog(onDismiss = viewModel::acknowledgePaintingCompletion)
        }
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

@Composable
private fun SetupContent(
    totalMinutes: Int,
    sittingMinutes: Int,
    onSelect: (Int, Int) -> Unit,
    onStart: () -> Unit,
) {
    val pieces = pieceCount(totalMinutes, sittingMinutes)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AppSpacing.screenPadding)
                .padding(bottom = 88.dp),
        ) {
            Text("Set your tempo", style = MaterialTheme.typography.headlineSmall)
            androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s1))
            Text(
                "Choose how long the whole painting takes, then how long one sitting runs.",
                style = MaterialTheme.typography.bodySmall,
            )

            if (pieces != null) {
                androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s6))
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
            }

            androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s6))

            StepHeader(1, "Total Focus", "How much deep work this masterpiece is worth.")
            androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s3))
            TierAnchorRow(
                anchors = totalAnchors().map { it.level to it.value },
                selected = totalMinutes,
                onSelect = { value -> onSelect(value, closestSitting(value, sittingMinutes)) },
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s2))
            WheelPicker(
                values = TOTAL_LADDER,
                selected = totalMinutes,
                onSelect = { value -> onSelect(value, closestSitting(value, sittingMinutes)) },
                label = ::formatDuration,
            )

            androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s6))

            StepHeader(2, "Single Sitting", "One unbroken stretch at the easel.")
            androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s3))
            TierAnchorRow(
                anchors = sittingAnchors(totalMinutes).map { it.level to it.value },
                selected = sittingMinutes,
                onSelect = { value -> onSelect(totalMinutes, value) },
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s2))
            WheelPicker(
                values = sittingsFor(totalMinutes),
                selected = sittingMinutes,
                onSelect = { value -> onSelect(totalMinutes, value) },
                label = ::formatDuration,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s2))
            Text(describePieces(totalMinutes, sittingMinutes) ?: " ", style = MaterialTheme.typography.bodySmall)
        }

        PrimaryButton(
            onClick = onStart,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.s5, vertical = AppSpacing.s4),
        ) { Text("Start") }
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
        StopwatchText(
            text = formatElapsed(elapsedMs(state.session, state.now)),
            isLive = state.session.status == SessionStatus.RUNNING,
        )

        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))

        when (state.session.status) {
            SessionStatus.RUNNING -> RunningControls(state, viewModel)
            SessionStatus.PAUSED -> PausedControls(viewModel)
            SessionStatus.SITTING_COMPLETE -> SittingCompleteControls(viewModel)
            SessionStatus.RESTING -> RestingControls(state, viewModel)
            SessionStatus.REST_COMPLETE -> RestCompleteControls(viewModel)
            else -> Unit
        }
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
    if (cancelCountdown == null) {
        TextButton(onClick = viewModel::requestCancel) { Text("Cancel") }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Cancelling in $cancelCountdown…", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = viewModel::abortCancelRequest) { Text("Keep going") }
                DangerButton(onClick = viewModel::confirmCancel, enabled = cancelCountdown == 0) { Text("Confirm cancel") }
            }
        }
    }
}

@Composable
private fun SittingCompleteControls(viewModel: FocusViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Piece revealed! Continue or take a rest?", textAlign = TextAlign.Center)
        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(onClick = viewModel::chooseContinue) { Text("Continue") }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
        Text("Take a rest", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            restOptions().take(4).forEach { minutes ->
                QuietButton(onClick = { viewModel.chooseRest(minutes) }) { Text("${minutes}m") }
            }
        }
    }
}

@Composable
private fun RestingControls(state: FocusUiState, viewModel: FocusViewModel) {
    val remaining = restRemainingMs(state.session, state.now)
    val lockRemaining = restLockRemainingMs(state.session, state.now)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Resting — ${formatElapsed(remaining)} remaining")
        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
        if (lockRemaining > 0) {
            Text("Continue available in ${(lockRemaining / 1000)}s", style = MaterialTheme.typography.bodySmall)
        } else {
            PrimaryButton(onClick = viewModel::chooseContinue) { Text("Continue now") }
        }
    }
}

@Composable
private fun RestCompleteControls(viewModel: FocusViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Rest complete")
        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
        PrimaryButton(onClick = viewModel::chooseContinue) { Text("Continue") }
    }
}

@Composable
private fun AwayTimeDialog(gapMs: Long, onKeep: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text("You were away ${formatAway(gapMs)}") },
        text = { Text("Keep this time or discard it?") },
        confirmButton = { TextButton(onClick = onKeep) { Text("Keep") } },
        dismissButton = { TextButton(onClick = onDiscard) { Text("Discard") } },
    )
}

@Composable
private fun PaintingCompleteDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Painting complete!") },
        text = { Text("Every piece is revealed. It now lives permanently in your Museum.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Wonderful") } },
    )
}
