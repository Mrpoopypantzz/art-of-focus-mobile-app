package com.artoffocus.app.ui.profile


import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.artoffocus.app.domain.account.hasUnsyncedWork
import com.artoffocus.app.domain.achievements.AchievementKind
import com.artoffocus.app.domain.achievements.Achievement
import com.artoffocus.app.domain.achievements.describeEarned
import com.artoffocus.app.domain.achievements.nextAchievement
import com.artoffocus.app.domain.achievements.progressToward
import com.artoffocus.app.domain.achievements.valueFor
import com.artoffocus.app.domain.focus.DEFAULT_TOTAL_MINUTES
import com.artoffocus.app.domain.focus.FocusLevel
import com.artoffocus.app.domain.focus.formatDuration
import com.artoffocus.app.domain.friends.formatHandle
import com.artoffocus.app.domain.museum.formatFocusSpan
import com.artoffocus.app.domain.notifications.NotificationKind
import com.artoffocus.app.ui.common.AppSheet
import com.artoffocus.app.ui.common.DangerButton
import com.artoffocus.app.ui.common.PrimaryButton
import com.artoffocus.app.ui.common.QuietButton
import com.artoffocus.app.ui.common.WaxSeal
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

/**
 * Mirrors the web AchievementsSection: a legend, the earned count, a "next up" block with a
 * real progress bar, then every achievement with its progress and description. The whole tab
 * scrolls as one list so the last row clears the floating tab bar.
 */
@Composable
private fun AchievementsTab(state: ProfileUiState) {
    val stats = state.statistics
    val next = nextAchievement(stats)
    // Grouped by kind so three short, scannable sections replace one flat run of fourteen.
    // The order of ACHIEVEMENTS within each kind is preserved (ascending threshold).
    val grouped = remember(state.achievements) { state.achievements.groupBy { it.kind } }

    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            Text("ACHIEVEMENTS", style = EyebrowStyle)
            androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s3))
            Text(describeEarned(stats), style = MaterialTheme.typography.bodyMedium)
        }

        next?.let { achievement ->
            item {
                androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s4))
                NextAchievement(
                    achievement = achievement,
                    current = valueFor(stats, achievement.kind),
                    fraction = progressToward(stats, achievement),
                )
            }
        }

        AchievementKind.entries.forEach { kind ->
            val rows = grouped[kind].orEmpty()
            if (rows.isEmpty()) return@forEach

            item(key = "header-$kind") {
                androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s6))
                Text(groupLabel(kind), style = EyebrowStyle)
                androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s2))
            }
            items(rows, key = { it.id }) { achievement ->
                AchievementRow(
                    achievement = achievement,
                    earned = state.earnedAchievements.contains(achievement),
                    current = valueFor(stats, achievement.kind),
                    fraction = progressToward(stats, achievement),
                )
            }
        }

        item {
            androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s5))
            Text("Counts finished paintings only.", color = AppColors.TextFaint, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun groupLabel(kind: AchievementKind): String = when (kind) {
    AchievementKind.STREAK -> "STREAKS"
    AchievementKind.HOURS -> "HOURS"
    AchievementKind.PAINTINGS -> "PAINTINGS"
}

/** The nearest-by-proportion achievement, with the progress bar the web app shows. */
@Composable
private fun NextAchievement(achievement: Achievement, current: Int, fraction: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.md))
            .background(AppColors.SurfaceSunken)
            .padding(AppSpacing.s4),
    ) {
        Text("Next: ${achievement.title}", style = MaterialTheme.typography.bodyLarge)
        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s2))
        ProgressBar(fraction = fraction, track = AppColors.Surface)
        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s2))
        Text(
            "$current of ${achievement.threshold} · ${achievement.description}",
            color = AppColors.TextDim,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun AchievementRow(achievement: Achievement, earned: Boolean, current: Int, fraction: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = AppSpacing.s1)
            .clip(RoundedCornerShape(AppRadius.sm))
            // Earned rows sit on a raised surface — "collected", not merely listed.
            .background(if (earned) AppColors.SurfaceSunken else AppColors.Bg.copy(alpha = 0f))
            .padding(horizontal = if (earned) AppSpacing.s3 else 0.dp, vertical = AppSpacing.s2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            // Without this the 11sp seal/label rides the 15sp title's top edge and reads crooked.
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                achievement.title,
                // Full-strength ink vs. dimmed description — the title has to lead the eye.
                color = if (earned) AppColors.Text else AppColors.TextDim,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (earned) WaxSeal(size = 20.dp)
        }
        Text(
            // Earned achievements drop the counter: the number stops being news once it is met.
            if (earned) achievement.description else "$current of ${achievement.threshold} · ${achievement.description}",
            color = if (earned) AppColors.TextDim else AppColors.TextFaint,
            style = MaterialTheme.typography.bodySmall,
        )
        // Only drawn once there is progress to show. An empty track on every untouched row is
        // fourteen horizontal rules of noise, not information.
        if (!earned && fraction > 0f) {
            androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s2))
            ProgressBar(fraction = fraction, track = AppColors.SurfaceSunken)
        }
    }
}

/** The shared achievement progress bar — one implementation for the next-up card and the rows. */
@Composable
private fun ProgressBar(fraction: Float, track: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PROGRESS_BAR_HEIGHT)
            .clip(AppRadius.pill)
            .background(track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(PROGRESS_BAR_HEIGHT)
                .clip(AppRadius.pill)
                .background(AppColors.Ink),
        )
    }
}

private val PROGRESS_BAR_HEIGHT = 6.dp

@Composable
private fun SettingsTab(state: ProfileUiState, viewModel: ProfileViewModel) {
    var username by remember(state.profile.username) { mutableStateOf(state.profile.username) }
    var goal by remember(state.profile.goal) { mutableStateOf(state.profile.goal) }

    // Four sections plus the tab bar no longer fit a phone screen — this is the scrolling
    // element, and it carries the tab-bar clearance so the last control is never trapped
    // under the floating bar.
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 96.dp),
    ) {
        Text("IDENTITY", style = EyebrowStyle)
        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s3))
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = goal, onValueChange = { goal = it }, label = { Text("Long-term goal") }, modifier = Modifier.fillMaxWidth())
        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
        PrimaryButton(onClick = { viewModel.saveIdentity(username, goal) }) { Text("Save") }

        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s4))
        HandleSection(state, viewModel)

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
        NotificationSection(state, viewModel)

        // F10.3 — replaying is just clearing the completion flag; AppRoot reads it and
        // re-enters the flow, so there is no separate navigation to manage.
        androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
        Text("INTRODUCTION", style = EyebrowStyle)
        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s2))
        Text("Watch the opening slides again.", style = MaterialTheme.typography.bodySmall)
        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s2))
        QuietButton(onClick = viewModel::replayIntroduction) { Text("Replay introduction") }

        androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
        AccountSection(state, viewModel)
    }
}

/** D1 prerequisite — friends search only finds accounts with a claimed handle (see FriendsRepository.claimHandle). */
@Composable
private fun HandleSection(state: ProfileUiState, viewModel: ProfileViewModel) {
    var input by remember { mutableStateOf("") }

    if (state.handle != null) {
        Text("Handle: ${formatHandle(state.handle)}", style = MaterialTheme.typography.bodyMedium)
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s2), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Claim a handle") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        PrimaryButton(
            onClick = { viewModel.claimHandle(input) },
            enabled = input.isNotBlank() && !state.handleBusy,
        ) { Text(if (state.handleBusy) "Claiming…" else "Claim") }
    }
    state.handleNotice?.let { notice ->
        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s1))
        Text(notice, color = AppColors.TextDim, style = MaterialTheme.typography.bodySmall)
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

/**
 * M8 — notification delivery, mirroring the web NotificationSettings section.
 *
 * On API 33+ nothing can be posted until POST_NOTIFICATIONS is granted, and until this
 * section existed nothing ever asked: the workers ran, AppNotifier's permission check
 * dropped every notification, and the user had no way to find out. The request is made from
 * here rather than at launch so it arrives with an explanation attached.
 *
 * Android needs no equivalent of the web's reliability caveats — WorkManager delivers
 * regardless of app state — so every kind is offered plainly (see domain/notifications).
 */
@Composable
private fun NotificationSection(state: ProfileUiState, viewModel: ProfileViewModel) {
    val context = LocalContext.current
    val needsRuntimeGrant = Build.VERSION.SDK_INT >= 33

    fun granted(): Boolean = !needsRuntimeGrant ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    var allowed by remember { mutableStateOf(granted()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        allowed = ok
    }

    // The system dialog only appears once; after a denial the switch lives in system settings,
    // so re-check on resume rather than leaving a stale "off" after the user changes it there.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = granted()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Text("NOTIFICATIONS", style = EyebrowStyle)
    androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s2))

    if (!allowed) {
        Text(
            "Get told when a rest ends and when you earn an achievement.",
            style = MaterialTheme.typography.bodySmall,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s2))
        PrimaryButton(onClick = {
            if (needsRuntimeGrant) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                allowed = true
            }
        }) { Text("Turn on notifications") }
        return
    }

    NotificationToggle("Rest is over", state.notificationPrefs.restEnd) {
        viewModel.setNotificationPref(NotificationKind.REST_END, it)
    }
    NotificationToggle("Achievement earned", state.notificationPrefs.achievement) {
        viewModel.setNotificationPref(NotificationKind.ACHIEVEMENT, it)
    }
    NotificationToggle("Daily studio call", state.notificationPrefs.dailyCall) {
        viewModel.setNotificationPref(NotificationKind.DAILY_CALL, it)
    }
    NotificationToggle("Streak at risk", state.notificationPrefs.streakRisk) {
        viewModel.setNotificationPref(NotificationKind.STREAK_RISK, it)
    }
}

@Composable
private fun NotificationToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.s1),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppColors.OnInk,
                checkedTrackColor = AppColors.Ink,
                checkedBorderColor = AppColors.Ink,
                uncheckedThumbColor = AppColors.TextDim,
                uncheckedTrackColor = AppColors.SurfaceSunken,
                uncheckedBorderColor = AppColors.LineStrong,
            ),
        )
    }
}

/** F10.6 — account auth + clear local cache, reachable with no account. Google Sign-In only. */
@Composable
private fun AccountSection(state: ProfileUiState, viewModel: ProfileViewModel) {
    val context = LocalContext.current

    Text("ACCOUNT", style = EyebrowStyle)
    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))

    var confirmClear by remember { mutableStateOf(false) }

    when (state.account.status) {
        AccountStatus.SIGNED_IN -> {
            Text(
                "Signed in as ${state.account.email ?: "your account"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            QuietButton(onClick = viewModel::signOut) { Text("Sign out") }
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

    // Deliberately outside the when: clearing must be reachable with no account, because the
    // person with no account is the one whose device holds the only copy.
    androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s2))
    QuietButton(onClick = { confirmClear = true }) { Text("Clear local data") }

    if (confirmClear) {
        ClearLocalDataSheet(
            paintingCount = state.statistics.count,
            atRisk = hasUnsyncedWork(
                paintingCount = state.statistics.count,
                lastSyncedAt = state.account.lastSyncedAt,
                syncStatus = state.account.syncStatus,
            ),
            onDismiss = { confirmClear = false },
            onConfirm = {
                confirmClear = false
                viewModel.clearLocalData()
            },
        )
    }
}

/**
 * I12 makes this sheet's job specific: state exactly what is about to be destroyed and
 * whether a copy survives elsewhere, rather than asking a vague "are you sure?".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClearLocalDataSheet(
    paintingCount: Int,
    atRisk: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val subtitle = when {
        paintingCount == 0 -> "Your Museum is empty."
        atRisk -> "$paintingCount painting${if (paintingCount == 1) "" else "s"} in your Museum " +
            "${if (paintingCount == 1) "has" else "have"} not been saved to an account."
        else -> "Your Museum is saved to your account."
    }
    val warning = when {
        paintingCount == 0 -> "There are no finished paintings to lose."
        // Web hard-codes the plural here; singular reads wrong on a first painting, which is
        // exactly when someone is most likely to be clearing by mistake.
        atRisk -> "This permanently deletes $paintingCount finished painting" +
            "${if (paintingCount == 1) "" else "s"} from this device. " +
            "${if (paintingCount == 1) "It cannot" else "They cannot"} be recovered. " +
            "Sign in first if you want to keep ${if (paintingCount == 1) "it" else "them"} — " +
            "your paintings sync automatically once you do."
        else -> "This removes the copy on this device. Your paintings remain in your account and " +
            "return when you sign in again."
    }

    AppSheet(
        onDismissRequest = onDismiss,
        title = "Clear local data?",
        subtitle = subtitle,
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.s2)) {
                PrimaryButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Keep my data")
                }
                DangerButton(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear anyway")
                }
            }
        },
    ) {
        Text(warning, style = MaterialTheme.typography.bodySmall)
    }
}
