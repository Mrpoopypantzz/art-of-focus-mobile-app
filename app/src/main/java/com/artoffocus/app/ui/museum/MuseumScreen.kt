package com.artoffocus.app.ui.museum

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.artoffocus.app.domain.museum.CompletedPainting
import com.artoffocus.app.domain.museum.MonthGroup
import com.artoffocus.app.domain.museum.formatFocusSpan
import com.artoffocus.app.domain.museum.museumStats
import com.artoffocus.app.ui.common.PaintingArt
import com.artoffocus.app.ui.common.PillToggleRow
import com.artoffocus.app.ui.theme.AppColors
import com.artoffocus.app.ui.theme.AppRadius
import com.artoffocus.app.ui.theme.AppSpacing
import com.artoffocus.app.ui.theme.FrauncesFamily
import com.artoffocus.app.ui.theme.StatValueStyle
import com.artoffocus.app.ui.theme.screenPadding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MuseumScreen(viewModel: MuseumViewModel) {
    val state by viewModel.state.collectAsState()

    if (state.paintings.isEmpty()) {
        EmptyMuseum()
        return
    }

    val stats = museumStats(state.paintings)

    Column(modifier = Modifier.fillMaxSize().padding(AppSpacing.screenPadding)) {
        Text("Museum", style = MaterialTheme.typography.headlineSmall)
        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s1))
        Text(
            "${stats.count} painting${if (stats.count == 1) "" else "s"} · ${formatFocusSpan(stats.focusMinutes)} of focus",
            style = MaterialTheme.typography.bodySmall,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s5))

        PillToggleRow(
            options = MuseumView.entries,
            selected = state.view,
            labelFor = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
            onSelect = viewModel::selectView,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s5))

        when (state.view) {
            MuseumView.WALL -> WallView(state.wallOrder)
            MuseumView.TIMELINE -> TimelineView(state.timeline)
        }
    }
}

/** Matches museum--empty: a title even with nothing in the collection, plus an italic Fraunces line. */
@Composable
private fun EmptyMuseum() {
    Column(
        modifier = Modifier.fillMaxSize().padding(AppSpacing.screenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Museum", style = MaterialTheme.typography.headlineSmall)
        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s2))
        Text(
            "Finished paintings are kept here.",
            color = AppColors.TextDim,
            fontFamily = FrauncesFamily,
            fontStyle = FontStyle.Italic,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun WallView(paintings: List<CompletedPainting>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s5)) {
        items(paintings) { painting -> PaintingCard(painting, modifier = Modifier.width(180.dp)) }
    }
}

@Composable
private fun TimelineView(groups: List<MonthGroup>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(AppSpacing.s6)) {
        items(groups) { group ->
            Column {
                Text(group.label, style = MaterialTheme.typography.titleMedium)
                androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s3))
                // A plain wrapping grid — nesting a lazy grid inside this LazyColumn would need an
                // artificial height guess, and a month's paintings are never a large list anyway.
                group.paintings.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s5), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { painting -> PaintingCard(painting, modifier = Modifier.weight(1f)) }
                        if (row.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s5))
                }
            }
        }
    }
}

private val cardShape = RoundedCornerShape(AppRadius.lg)
private val cardDateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")

@Composable
private fun PaintingCard(painting: CompletedPainting, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 5f)
                .shadow(elevation = 8.dp, shape = cardShape, ambientColor = AppColors.Ink.copy(alpha = 0.08f), spotColor = AppColors.Ink.copy(alpha = 0.08f))
                .clip(cardShape)
                .background(AppColors.Surface)
                .border(1.dp, AppColors.Ink.copy(alpha = 0.28f), cardShape),
        ) {
            PaintingArt(painting = painting.painting, modifier = Modifier.matchParentSize())
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s2))
        Text(painting.painting.title, style = StatValueStyle, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(
            "${formatFocusSpan(painting.totalMinutes)} · ${painting.pieces} pieces",
            color = AppColors.TextDim,
            style = MaterialTheme.typography.bodySmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            Instant.ofEpochMilli(painting.completedAt).atZone(ZoneId.systemDefault()).format(cardDateFormatter),
            color = AppColors.TextFaint,
            style = MaterialTheme.typography.labelSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
