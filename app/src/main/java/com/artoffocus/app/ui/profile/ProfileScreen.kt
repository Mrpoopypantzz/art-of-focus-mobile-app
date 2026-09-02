package com.artoffocus.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.artoffocus.app.domain.achievements.Achievement
import com.artoffocus.app.domain.achievements.describeEarned
import com.artoffocus.app.domain.achievements.nextAchievement
import com.artoffocus.app.domain.achievements.valueFor
import com.artoffocus.app.domain.focus.FocusLevel
import com.artoffocus.app.domain.museum.formatFocusSpan
import com.artoffocus.app.domain.profile.displayName
import com.artoffocus.app.ui.common.PrimaryButton
import com.artoffocus.app.ui.common.QuietButton

private enum class ProfileTab { STATS, ACHIEVEMENTS, SETTINGS }

@Composable
fun ProfileScreen(viewModel: ProfileViewModel) {
    val state by viewModel.state.collectAsState()
    var tab by remember { mutableStateOf(ProfileTab.STATS) }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(displayName(state.profile), style = MaterialTheme.typography.headlineSmall)

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            ProfileTab.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = tab == entry,
                    onClick = { tab = entry },
                    shape = SegmentedButtonDefaults.itemShape(index, ProfileTab.entries.size),
                ) { Text(entry.name.lowercase().replaceFirstChar { it.uppercase() }) }
            }
        }

        when (tab) {
            ProfileTab.STATS -> StatsTab(state)
            ProfileTab.ACHIEVEMENTS -> AchievementsTab(state)
            ProfileTab.SETTINGS -> SettingsTab(state, viewModel)
        }
    }
}

@Composable
private fun StatsTab(state: ProfileUiState) {
    val stats = state.statistics
    Column {
        Text("Statistics count finished paintings only.", style = MaterialTheme.typography.bodySmall)
        androidx.compose.foundation.layout.Spacer(Modifier.height(if (false) 0.dp else 12.dp))
        StatRow("Paintings completed", stats.count.toString())
        StatRow("Total focus", formatFocusSpan(stats.focusMinutes))
        StatRow("Longest painting", formatFocusSpan(stats.longestMinutes))
        StatRow("Average painting", formatFocusSpan(stats.averageMinutes))
        StatRow("Current streak", "${stats.currentStreak} days")
        StatRow("Longest streak", "${stats.longestStreak} days")
        stats.bestMonth?.let { StatRow("Best month", it) }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
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
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = goal, onValueChange = { goal = it }, label = { Text("Long-term goal") }, modifier = Modifier.fillMaxWidth())
        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
        PrimaryButton(onClick = { viewModel.saveIdentity(username, goal) }) { Text("Save") }

        androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
        Text("Focus level", style = MaterialTheme.typography.titleMedium)
        Text(
            "Sets defaults only — never limits which durations you can pick, even mid-painting.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            FocusLevel.entries.forEach { level ->
                val selected = level == state.profile.level
                if (selected) {
                    PrimaryButton(onClick = { viewModel.saveLevel(level) }) { Text(level.name.lowercase().replaceFirstChar { it.uppercase() }) }
                } else {
                    QuietButton(onClick = { viewModel.saveLevel(level) }) { Text(level.name.lowercase().replaceFirstChar { it.uppercase() }) }
                }
            }
        }
    }
}
