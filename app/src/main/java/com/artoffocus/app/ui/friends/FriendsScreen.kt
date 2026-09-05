package com.artoffocus.app.ui.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.artoffocus.app.domain.friends.Friend
import com.artoffocus.app.domain.friends.Group
import com.artoffocus.app.domain.friends.PendingRequest
import com.artoffocus.app.domain.friends.SortBy
import com.artoffocus.app.domain.friends.formatHandle
import com.artoffocus.app.domain.friends.formatInviteCode
import com.artoffocus.app.domain.friends.friendMeasure
import com.artoffocus.app.domain.friends.rankFriends
import com.artoffocus.app.domain.friends.rankStandings
import com.artoffocus.app.domain.museum.formatFocusSpan
import com.artoffocus.app.ui.common.PillToggleRow
import com.artoffocus.app.ui.common.PrimaryButton
import com.artoffocus.app.ui.common.QuietButton
import com.artoffocus.app.ui.theme.AppColors
import com.artoffocus.app.ui.theme.AppSpacing
import com.artoffocus.app.ui.theme.EyebrowStyle
import com.artoffocus.app.ui.theme.FrauncesFamily
import com.artoffocus.app.ui.theme.StatValueStyle
import com.artoffocus.app.ui.theme.screenPadding

@Composable
fun FriendsScreen(viewModel: FriendsViewModel) {
    val state by viewModel.state.collectAsState()

    when {
        !state.configured -> QuietNote(
            "Comparing focus hours needs an account. Connect one in Profile — everything else in the app works without it.",
        )
        !state.signedIn -> QuietNote("Sign in from Profile to add friends and compare focus hours.")
        else -> FriendsContent(state, viewModel)
    }
}

@Composable
private fun QuietNote(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(AppSpacing.screenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Friends", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(AppSpacing.s2))
        Text(
            message,
            color = AppColors.TextDim,
            fontFamily = FrauncesFamily,
            fontStyle = FontStyle.Italic,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun formatMeasure(friend: Friend, sortBy: SortBy): String {
    val value = friendMeasure(friend, sortBy)
    return when (sortBy) {
        SortBy.HOURS -> formatFocusSpan(value)
        SortBy.STREAK -> "$value day${if (value == 1) "" else "s"}"
    }
}

@Composable
fun FriendsContent(state: FriendsUiState, viewModel: FriendsViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(AppSpacing.screenPadding),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item {
            Text("Friends", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(AppSpacing.s4))
            PillToggleRow(
                options = FriendsScope.entries,
                selected = state.scope,
                labelFor = { if (it == FriendsScope.FRIENDS) "Friends" else "Groups" },
                onSelect = viewModel::setScope,
            )
            Spacer(Modifier.height(AppSpacing.s5))
        }

        state.notice?.let { notice ->
            item {
                Text(notice, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(AppSpacing.s3))
            }
        }

        if (state.scope == FriendsScope.FRIENDS) {
            friendsScope(state, viewModel)
        } else {
            groupsScope(state, viewModel)
        }
    }
}

private fun LazyListScope.friendsScope(state: FriendsUiState, viewModel: FriendsViewModel) {
    item {
        Text("ADD A FRIEND", style = EyebrowStyle)
        Spacer(Modifier.height(AppSpacing.s3))
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s2), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text("@handle") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(onClick = viewModel::search, enabled = state.query.isNotBlank()) { Text("Find") }
        }

        state.match?.let { match ->
            Spacer(Modifier.height(AppSpacing.s3))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(match.displayName.ifBlank { formatHandle(match.handle) }, style = MaterialTheme.typography.bodyLarge)
                    Text(formatHandle(match.handle), color = AppColors.TextDim, style = MaterialTheme.typography.bodySmall)
                }
                PrimaryButton(onClick = { viewModel.addFriend(match) }) { Text("Add") }
            }
        }
        if (state.searched && state.match == null) {
            Spacer(Modifier.height(AppSpacing.s2))
            Text("No one with that handle.", color = AppColors.TextDim, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(AppSpacing.s5))
    }

    if (state.pending.isNotEmpty()) {
        item {
            Text("REQUESTS", style = EyebrowStyle)
            Spacer(Modifier.height(AppSpacing.s3))
        }
        items(state.pending, key = { it.id }) { request ->
            PendingRequestRow(request, onAccept = { viewModel.accept(request) }, onDecline = { viewModel.decline(request) })
        }
        item { Spacer(Modifier.height(AppSpacing.s5)) }
    }

    item {
        Text("LEADERBOARD", style = EyebrowStyle)
        Spacer(Modifier.height(AppSpacing.s3))
        PillToggleRow(
            options = SortBy.entries,
            selected = state.sortBy,
            labelFor = { if (it == SortBy.HOURS) "Focus hours" else "Streak" },
            onSelect = viewModel::setSortBy,
        )
        Spacer(Modifier.height(AppSpacing.s3))
    }

    val ranked = rankFriends(state.friends, state.sortBy)
    if (ranked.isEmpty()) {
        item {
            Text(
                "Only you so far. Add a friend by their handle above.",
                color = AppColors.TextDim,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    } else {
        itemsIndexed(ranked) { index, friend -> LeaderboardRow(index + 1, friend, state.sortBy) }
    }

    item {
        Spacer(Modifier.height(AppSpacing.s3))
        Text(
            "Friends see only your handle, name, focus hours and streak.",
            color = AppColors.TextFaint,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun LazyListScope.groupsScope(state: FriendsUiState, viewModel: FriendsViewModel) {
    item {
        Text("JOIN A GROUP", style = EyebrowStyle)
        Spacer(Modifier.height(AppSpacing.s3))
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s2), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.joinCodeInput,
                onValueChange = viewModel::setJoinCodeInput,
                label = { Text("Invite code") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(onClick = viewModel::joinGroup, enabled = state.joinCodeInput.isNotBlank()) { Text("Join") }
        }
        Spacer(Modifier.height(AppSpacing.s5))

        Text("OR START A NEW GROUP", style = EyebrowStyle)
        Spacer(Modifier.height(AppSpacing.s3))
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s2), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.groupNameInput,
                onValueChange = viewModel::setGroupNameInput,
                label = { Text("Group name") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(onClick = viewModel::createGroup, enabled = state.groupNameInput.isNotBlank()) { Text("Create") }
        }
        Spacer(Modifier.height(AppSpacing.s5))

        Text("YOUR GROUPS", style = EyebrowStyle)
        Spacer(Modifier.height(AppSpacing.s3))
    }

    if (state.groups.isEmpty()) {
        item {
            Text("Not in any groups yet.", color = AppColors.TextDim, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(AppSpacing.s2))
        }
    } else {
        items(state.groups, key = { it.id }) { group ->
            GroupRow(
                group = group,
                open = state.openGroupId == group.id,
                standings = state.standings,
                sortBy = state.sortBy,
                onToggleStandings = { viewModel.openStandings(group.id) },
                onLeave = { viewModel.leaveGroup(group) },
                onSortBy = viewModel::setSortBy,
            )
        }
    }

    item {
        Spacer(Modifier.height(AppSpacing.s3))
        Text(
            "Group members see only your handle, name, focus hours and streak. Leaving a group ends that.",
            color = AppColors.TextFaint,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun PendingRequestRow(request: PendingRequest, onAccept: () -> Unit, onDecline: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.s2),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(request.displayName.ifBlank { formatHandle(request.handle) }, style = MaterialTheme.typography.bodyMedium)
            Text(
                formatHandle(request.handle) + if (request.outgoing) " · waiting" else "",
                color = AppColors.TextDim,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (request.outgoing) {
            QuietButton(onClick = onDecline) { Text("Withdraw") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s2)) {
                QuietButton(onClick = onDecline) { Text("Decline") }
                PrimaryButton(onClick = onAccept) { Text("Accept") }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(rank: Int, friend: Friend, sortBy: SortBy) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.s2),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$rank", color = AppColors.TextDim, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(AppSpacing.s1))
            Column(modifier = Modifier.padding(start = AppSpacing.s3)) {
                Text(friend.displayName.ifBlank { formatHandle(friend.handle) }, style = MaterialTheme.typography.bodyMedium)
                Text(formatHandle(friend.handle), color = AppColors.TextDim, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(formatMeasure(friend, sortBy), style = StatValueStyle)
    }
}

@Composable
private fun GroupRow(
    group: Group,
    open: Boolean,
    standings: List<Friend>,
    sortBy: SortBy,
    onToggleStandings: () -> Unit,
    onLeave: () -> Unit,
    onSortBy: (SortBy) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.s2)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(group.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${formatInviteCode(group.inviteCode)} · ${group.memberCount} member${if (group.memberCount == 1) "" else "s"}",
                    color = AppColors.TextDim,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s2)) {
                QuietButton(onClick = onLeave) { Text("Leave") }
                QuietButton(onClick = onToggleStandings) { Text(if (open) "Hide" else "Standings") }
            }
        }

        if (open) {
            Spacer(Modifier.height(AppSpacing.s3))
            PillToggleRow(
                options = SortBy.entries,
                selected = sortBy,
                labelFor = { if (it == SortBy.HOURS) "Focus hours" else "Streak" },
                onSelect = onSortBy,
            )
            Spacer(Modifier.height(AppSpacing.s2))
            val ranked = rankStandings(standings, sortBy)
            if (ranked.isEmpty()) {
                Text("No standings yet.", color = AppColors.TextDim, style = MaterialTheme.typography.bodySmall)
            } else {
                ranked.forEachIndexed { index, friend -> LeaderboardRow(index + 1, friend, sortBy) }
            }
        }
    }
}
