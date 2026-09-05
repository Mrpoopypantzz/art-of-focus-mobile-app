package com.artoffocus.app.ui.photos

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.artoffocus.app.domain.focus.canDeletePicture
import com.artoffocus.app.ui.common.AppSheet
import com.artoffocus.app.ui.common.PrimaryButton
import com.artoffocus.app.ui.common.QuietButton
import com.artoffocus.app.ui.theme.AppColors
import com.artoffocus.app.ui.theme.AppRadius
import com.artoffocus.app.ui.theme.AppSpacing
import com.artoffocus.app.ui.theme.FrauncesFamily

/**
 * Your Pictures — every saved photo, with two ways to turn one into a painting: pick a
 * specific picture, or shuffle for one at random.
 *
 * Nothing here is hidden and nothing is used up. A picture can back any number of paintings,
 * and abandoning a painting leaves it exactly where it was.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoLibrarySheet(
    viewModel: PhotoLibraryViewModel,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    // The system photo picker: no storage permission required, so the app's permission
    // count stays exactly where it was.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(viewModel::import) }

    AppSheet(
        onDismissRequest = onDismiss,
        title = "Your pictures",
        subtitle = "Choose one to uncover, or shuffle for a surprise.",
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s2), modifier = Modifier.fillMaxWidth()) {
                QuietButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f),
                ) { Text("Add a picture") }
                PrimaryButton(
                    onClick = { viewModel.shuffled()?.let(onChoose) },
                    enabled = state.photos.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("Shuffle") }
            }
        },
    ) {
        state.notice?.let { notice ->
            Text(notice, color = AppColors.TextDim, style = MaterialTheme.typography.bodySmall)
            androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s2))
        }

        if (state.photos.isEmpty() && !state.loading) {
            Text(
                "No pictures yet. Add one and it becomes the painting you uncover as you focus.",
                color = AppColors.TextDim,
                fontFamily = FrauncesFamily,
                fontStyle = FontStyle.Italic,
                style = MaterialTheme.typography.bodyMedium,
            )
            return@AppSheet
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.s2),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.s2),
            modifier = Modifier.heightIn(max = GRID_MAX_HEIGHT),
        ) {
            items(state.photos, key = { it }) { ref ->
                PhotoTile(
                    ref = ref,
                    locked = !canDeletePicture(ref, state.inUse),
                    onChoose = { onChoose(ref) },
                    onRemove = { viewModel.remove(ref) },
                )
            }
        }
    }
}

@Composable
private fun PhotoTile(ref: String, locked: Boolean, onChoose: () -> Unit, onRemove: () -> Unit) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(AppRadius.sm))
                .background(AppColors.SurfaceSunken)
                .clickable(onClick = onChoose),
        ) {
            AsyncImage(
                model = ref,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        // A picture a painting depends on cannot be removed (I12) — but it stays choosable,
        // because using a picture never consumes it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (locked) Modifier else Modifier.clickable(onClick = onRemove))
                .padding(top = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (locked) "In your Museum" else "Remove",
                color = AppColors.TextFaint,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private val GRID_MAX_HEIGHT = 320.dp
