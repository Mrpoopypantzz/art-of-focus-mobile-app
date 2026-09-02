package com.artoffocus.app.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.artoffocus.app.domain.focus.Painting
import com.artoffocus.app.domain.focus.SessionStatus
import com.artoffocus.app.domain.focus.describeSessionProgress
import com.artoffocus.app.domain.focus.elapsedMs
import com.artoffocus.app.domain.focus.formatAway
import com.artoffocus.app.domain.focus.formatDuration
import com.artoffocus.app.domain.focus.formatElapsed
import com.artoffocus.app.domain.focus.layoutFor
import com.artoffocus.app.domain.focus.restLockRemainingMs
import com.artoffocus.app.domain.focus.restRemainingMs
import com.artoffocus.app.domain.focus.revealedCount
import com.artoffocus.app.domain.focus.restOptions
import com.artoffocus.app.domain.focus.sessionProgress
import com.artoffocus.app.domain.focus.sittingsFor
import com.artoffocus.app.domain.focus.totalAnchors
import com.artoffocus.app.ui.common.DangerButton
import com.artoffocus.app.ui.common.PaintingArt
import com.artoffocus.app.ui.common.PrimaryButton
import com.artoffocus.app.ui.common.QuietButton
import com.artoffocus.app.ui.theme.AppColors
import com.artoffocus.app.ui.theme.AppRadius

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

@Composable
private fun SetupContent(
    totalMinutes: Int,
    sittingMinutes: Int,
    onSelect: (Int, Int) -> Unit,
    onStart: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Total Focus", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            totalAnchors().forEach { anchor ->
                val selected = anchor.value == totalMinutes
                val label = formatDuration(anchor.value)
                if (selected) {
                    PrimaryButton(onClick = { onSelect(anchor.value, sittingsFor(anchor.value).first()) }) { Text(label) }
                } else {
                    QuietButton(onClick = { onSelect(anchor.value, sittingsFor(anchor.value).first()) }) { Text(label) }
                }
            }
        }

        androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))

        Text("Single Sitting", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sittingsFor(totalMinutes).take(6).forEach { sitting ->
                val selected = sitting == sittingMinutes
                val label = formatDuration(sitting)
                if (selected) {
                    PrimaryButton(onClick = { onSelect(totalMinutes, sitting) }) { Text(label) }
                } else {
                    QuietButton(onClick = { onSelect(totalMinutes, sitting) }) { Text(label) }
                }
            }
        }

        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
        val pieces = totalMinutes / sittingMinutes
        Text("$pieces sittings · one puzzle piece each", style = MaterialTheme.typography.bodyMedium)

        androidx.compose.foundation.layout.Spacer(Modifier.height(32.dp))
        PrimaryButton(onClick = onStart) { Text("Start") }
    }
}

@Composable
private fun RunningContent(state: FocusUiState, viewModel: FocusViewModel) {
    val progress = sessionProgress(state.session, state.setup.sittingMinutes, state.pieces, state.now)
    val revealed = revealedCount(state.session, state.pieces)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
                    .then(if (isRevealed) Modifier else Modifier.background(gildedTileBrush).border(1.dp, AppColors.Ink.copy(alpha = 0.12f))),
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
