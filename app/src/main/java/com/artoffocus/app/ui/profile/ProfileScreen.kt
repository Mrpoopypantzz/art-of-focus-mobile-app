package com.artoffocus.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.artoffocus.app.domain.account.AccountStatus
import com.artoffocus.app.domain.achievements.Achievement
import com.artoffocus.app.domain.achievements.describeEarned
import com.artoffocus.app.domain.achievements.nextAchievement
import com.artoffocus.app.domain.achievements.valueFor
import com.artoffocus.app.domain.focus.DEFAULT_TOTAL_MINUTES
import com.artoffocus.app.domain.focus.FocusLevel
import com.artoffocus.app.domain.focus.formatDuration
import com.artoffocus.app.domain.museum.formatFocusSpan
import com.artoffocus.app.ui.common.DangerButton
import com.artoffocus.app.ui.common.PrimaryButton
import com.artoffocus.app.ui.theme.AppColors
import com.artoffocus.app.ui.theme.AppRadius
import com.artoffocus.app.ui.theme.AppSpacing
import com.artoffocus.app.ui.theme.EyebrowStyle
import com.artoffocus.app.ui.theme.StatValueStyle
import com.artoffocus.app.ui.theme.screenPadding

private enum class ProfileTab { STATS, ACHIEVEMENTS, SETTINGS }

@Composable
fun ProfileScreen(viewModel: ProfileViewModel) {
    val state by viewModel.state.collectAsState()
    var tab by remember { mutableStateOf(ProfileTab.STATS) }

    Column(modifier = Modifier.fillMaxWidth().padding(AppSpacing.screenPadding)) {
        Text("Profile", style = MaterialTheme.typography.headlineSmall)
        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s4))

        ProfileTabRow(selected = tab, onSelect = { tab = it })
        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s4))

        when (tab) {
            ProfileTab.STATS -> StatsTab(state)
            ProfileTab.ACHIEVEMENTS -> AchievementsTab(state)
            ProfileTab.SETTINGS -> SettingsTab(state, viewModel)
        }
    }
}

/** Profile's own tabs — the same ink/sunken-surface pill pattern as Focus's TierAnchorRow. */
@Composable
private fun ProfileTabRow(selected: ProfileTab, onSelect: (ProfileTab) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s2), modifier = Modifier.fillMaxWidth()) {
        ProfileTab.entries.forEach { entry ->
            val isSelected = entry == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(AppRadius.pill)
                    .background(if (isSelected) AppColors.Ink else AppColors.SurfaceSunken)
                    .clickable { onSelect(entry) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    entry.name.uppercase(),
                    color = if (isSelected) AppColors.OnInk else AppColors.TextDim,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun StatsTab(state: ProfileUiState) {
    val stats = state.statistics
    val entries = listOf(
        "Paintings" to stats.count.toString(),
        "Total focus" to formatFocusSpan(stats.focusMinutes),
        "Current streak" to "${stats.currentStreak} days",
        "Longest streak" to "${stats.longestStreak} days",
    )

    Column {
        Text("STATISTICS", style = EyebrowStyle)
        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s3))
        entries.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.s2),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.s4),
            ) {
                pair.forEach { (label, value) -> StatCell(label, value, Modifier.weight(1f)) }
                if (pair.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
        }
        stats.bestMonth?.let {
            androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s3))
            Text("Most focused month: $it", style = MaterialTheme.typography.bodySmall)
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s2))
        Text("Counts finished paintings only.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label.uppercase(), style = EyebrowStyle)
        Text(value, style = StatValueStyle)
    }
}

@Composable
private fun AchievementsTab(state: ProfileUiState) {
    Column {
        Text(describeEarned(state.statistics), style = MaterialTheme.typography.bodyMedium)
        nextAchievement(state.statistics)?.let {
            Text(
                "Next: ${it.title} (${valueFor(state.statistics, it.kind)}/${it.threshold})",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        LazyColumn(modifier = Modifier.padding(top = 12.dp)) {
            items(state.achievements) { achievement -> AchievementRow(achievement, state.earnedAchievements.contains(achievement)) }
        }
    }
}

@Composable
private fun AchievementRow(achievement: Achievement, earned: Boolean) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(achievement.title, style = MaterialTheme.typography.bodyLarge)
            Text(if (earned) "Earned" else "Locked", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingsTab(state: ProfileUiState, viewModel: ProfileViewModel) {
    var username by remember(state.profile.username) { mutableStateOf(state.profile.username) }
    var goal by remember(state.profile.goal) { mutableStateOf(state.profile.goal) }

    Column {
        Text("IDENTITY", style = EyebrowStyle)
        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s3))
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = goal, onValueChange = { goal = it }, label = { Text("Long-term goal") }, modifier = Modifier.fillMaxWidth())
        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
        PrimaryButton(onClick = { viewModel.saveIdentity(username, goal) }) { Text("Save") }

        androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
        Text("FOCUS LEVEL", style = EyebrowStyle)
        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s2))
        Text(
            "Sets defaults only — never limits which durations you can pick, even mid-painting.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s2), modifier = Modifier.padding(top = 8.dp)) {
            FocusLevel.entries.forEach { level -> FocusLevelCard(level, level == state.profile.level, Modifier.weight(1f)) { viewModel.saveLevel(level) } }
        }

        androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
        AccountSection(state, viewModel)
    }
}

/** Stacked name + default-duration card, matching web's .profile__level (not a pill — see ProfileScreen.css). */
@Composable
private fun FocusLevelCard(level: FocusLevel, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AppRadius.md))
            .background(if (selected) AppColors.Ink else AppColors.SurfaceSunken)
            .clickable(onClick = onClick)
            .padding(AppSpacing.s2),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            level.name.lowercase().replaceFirstChar { it.uppercase() },
            color = if (selected) AppColors.OnInk else AppColors.TextDim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            formatDuration(DEFAULT_TOTAL_MINUTES.getValue(level)),
            color = if (selected) AppColors.OnInk else AppColors.TextDim,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** F10.6 — account auth + clear local cache, reachable with no account. Google Sign-In only. */
@Composable
private fun AccountSection(state: ProfileUiState, viewModel: ProfileViewModel) {
    val context = LocalContext.current

    Text("ACCOUNT", style = EyebrowStyle)
    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))

    when (state.account.status) {
        AccountStatus.SIGNED_IN -> {
            Text(
                state.account.email ?: "Signed in",
                style = MaterialTheme.typography.bodyMedium,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            DangerButton(onClick = viewModel::signOut) { Text("Sign out & clear local data") }
        }
        else -> {
            Text(
                "Sign in to sync your paintings and connect with friends. The app works fully offline without an account.",
                style = MaterialTheme.typography.bodySmall,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            PrimaryButton(
                onClick = { viewModel.signInWithGoogle(context) },
                enabled = !state.authInProgress,
            ) { Text(if (state.authInProgress) "Signing in…" else "Sign in with Google") }
        }
    }

    state.authError?.let { error ->
        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
        Text(error, style = MaterialTheme.typography.bodySmall)
    }
}
