package com.artoffocus.app.ui.museum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.artoffocus.app.domain.focus.paintingGradient
import com.artoffocus.app.domain.museum.CompletedPainting
import com.artoffocus.app.domain.museum.formatFocusSpan

@Composable
fun MuseumScreen(viewModel: MuseumViewModel) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.view == MuseumView.WALL,
                onClick = { viewModel.selectView(MuseumView.WALL) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("Wall") }
            SegmentedButton(
                selected = state.view == MuseumView.TIMELINE,
                onClick = { viewModel.selectView(MuseumView.TIMELINE) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("Timeline") }
        }

        if (state.paintings.isEmpty()) {
            EmptyMuseum()
        } else when (state.view) {
            MuseumView.WALL -> WallView(state.wallOrder)
            MuseumView.TIMELINE -> TimelineView(state.timeline)
        }
    }
}

@Composable
private fun EmptyMuseum() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Finish your first painting to start your collection.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun WallView(paintings: List<CompletedPainting>) {
    LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.padding(top = 16.dp)) {
        items(paintings) { painting -> PaintingTile(painting) }
    }
}

@Composable
private fun TimelineView(groups: List<com.artoffocus.app.domain.museum.MonthGroup>) {
    LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
        items(groups) { group ->
            Text(group.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
            group.paintings.forEach { painting -> PaintingRow(painting) }
        }
    }
}

@Composable
private fun PaintingTile(painting: CompletedPainting) {
    val gradientBrush = gradientFromCss(paintingGradient(painting.painting))
    Box(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(0.8f)
            .clip(RoundedCornerShape(8.dp))
            .background(gradientBrush),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            painting.painting.title,
            color = androidx.compose.ui.graphics.Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(8.dp),
        )
    }
}

@Composable
private fun PaintingRow(painting: CompletedPainting) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(painting.painting.title, style = MaterialTheme.typography.bodyLarge)
        Text(
            "${formatFocusSpan(painting.totalMinutes)} · ${painting.pieces} pieces",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Approximates the CSS linear-gradient string with a Compose brush (angle/stops are not pixel-matched). */
private fun gradientFromCss(css: String): androidx.compose.ui.graphics.Brush {
    val hexColors = Regex("#[0-9a-fA-F]{6}").findAll(css).map {
        androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(it.value))
    }.toList()
    return androidx.compose.ui.graphics.Brush.linearGradient(
        colors = hexColors.ifEmpty { listOf(androidx.compose.ui.graphics.Color.Gray) },
    )
}
