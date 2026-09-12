package com.artoffocus.app.ui.photos

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.artoffocus.app.domain.focus.canDeletePicture
import com.artoffocus.app.ui.common.PrimaryButton
import com.artoffocus.app.ui.common.QuietButton
import com.artoffocus.app.ui.theme.AppColors
import com.artoffocus.app.ui.theme.AppRadius
import com.artoffocus.app.ui.theme.AppSpacing
import com.artoffocus.app.ui.theme.FrauncesFamily
import com.artoffocus.app.ui.theme.screenPadding

/**
 * Pictures — the user's own photographs, and the door to contributing one.
 *
 * This was a bottom sheet inside the Focus screen. As its own tab it can hold the
 * contribution guidelines and breathe on a tall screen, and — more importantly — choosing a
 * picture here no longer starts a timer. It lines one up; starting stays a deliberate tap on
 * the Focus tab. A choice made in one tab that silently starts a clock in another is the kind
 * of surprise this app is supposed to be the opposite of.
 *
 * Nothing here is hidden and nothing is used up. A picture can back any number of paintings,
 * and abandoning a painting leaves it exactly where it was.
 */
@Composable
fun PhotoLibraryScreen(
    viewModel: PhotoLibraryViewModel,
    onChoose: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var guidelinesOpen by remember { mutableStateOf(false) }

    // The lock set depends on the Museum and the active painting, both of which change on
    // other tabs. As a long-lived tab this would otherwise still offer "Remove" on a picture
    // that a painting started depending on while the user was away (I12).
    LaunchedEffect(Unit) { viewModel.refresh() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(viewModel::import) }

    Column(modifier = Modifier.fillMaxSize().padding(AppSpacing.screenPadding)) {
        Text("Pictures", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(AppSpacing.s1))
        Text(
            "Your own photographs, ready to be uncovered a piece at a time.",
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextDim,
        )
        Spacer(Modifier.height(AppSpacing.s4))

        state.notice?.let { notice ->
            NoticeBar(notice = notice, onDismiss = viewModel::dismissNotice)
            Spacer(Modifier.height(AppSpacing.s3))
        }

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

        Spacer(Modifier.height(AppSpacing.s4))
        GuidelinesPanel(expanded = guidelinesOpen, onToggle = { guidelinesOpen = !guidelinesOpen })
        Spacer(Modifier.height(AppSpacing.s4))

        if (state.photos.isEmpty() && !state.loading) {
            Text(
                "No pictures yet. Add one and it becomes the painting you uncover as you focus.",
                color = AppColors.TextDim,
                fontFamily = FrauncesFamily,
                fontStyle = FontStyle.Italic,
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        // No height cap here, unlike the sheet this replaced: a tab has the whole screen.
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.s2),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.s2),
            modifier = Modifier.fillMaxSize(),
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
private fun NoticeBar(notice: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.sm))
            .background(AppColors.SurfaceSunken)
            .padding(horizontal = AppSpacing.s3, vertical = AppSpacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(notice, color = AppColors.TextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(
            "Dismiss",
            color = AppColors.TextFaint,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.clickable(role = Role.Button, onClick = onDismiss),
        )
    }
}

/**
 * The rules for what belongs in a shared museum, written plainly and stated before anyone
 * can submit anything. Collapsed by default so it informs without lecturing.
 */
@Composable
private fun GuidelinesPanel(expanded: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.sm))
            .border(1.dp, AppColors.SurfaceSunken, RoundedCornerShape(AppRadius.sm))
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(AppSpacing.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "What makes a good painting",
                style = MaterialTheme.typography.titleSmall,
                color = AppColors.Text,
                modifier = Modifier.weight(1f),
            )
            Text(if (expanded) "−" else "+", color = AppColors.TextFaint, style = MaterialTheme.typography.titleSmall)
        }
        if (expanded) {
            Spacer(Modifier.height(AppSpacing.s2))
            GUIDELINES.forEach { line ->
                Text(
                    "· $line",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextDim,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Spacer(Modifier.height(AppSpacing.s1))
            Text(
                "Sharing to the community museum is not open yet — these are the rules it will follow.",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextFaint,
                fontFamily = FrauncesFamily,
                fontStyle = FontStyle.Italic,
            )
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
                .clickable(role = Role.Button, onClick = onChoose),
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
                .then(if (locked) Modifier else Modifier.clickable(role = Role.Button, onClick = onRemove))
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

private val GUIDELINES = listOf(
    "Your own work, or work you have the right to share.",
    "No people who have not agreed to appear.",
    "Nothing explicit, hateful, or aimed at a particular person.",
    "Something worth looking at slowly — it is revealed one piece at a time.",
)
