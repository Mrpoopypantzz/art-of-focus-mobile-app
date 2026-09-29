package com.artoffocus.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.artoffocus.app.domain.focus.DEFAULT_TOTAL_MINUTES
import com.artoffocus.app.domain.focus.FocusLevel
import com.artoffocus.app.domain.focus.formatDuration
import com.artoffocus.app.domain.onboarding.OnboardingStep
import com.artoffocus.app.domain.onboarding.SLIDES
import com.artoffocus.app.domain.onboarding.isLastSlide
import com.artoffocus.app.ui.common.rememberNotificationPermission
import com.artoffocus.app.domain.profile.MAX_GOAL
import com.artoffocus.app.domain.profile.MAX_USERNAME
import com.artoffocus.app.ui.common.PrimaryButton
import com.artoffocus.app.ui.common.QuietButton
import com.artoffocus.app.ui.theme.AppColors
import com.artoffocus.app.ui.theme.AppRadius
import com.artoffocus.app.ui.theme.AppSpacing
import com.artoffocus.app.ui.theme.EyebrowStyle
import com.artoffocus.app.ui.theme.screenPadding

/**
 * F12.1/F12.2 — the first-run introduction.
 *
 * Premise, not a feature tour: three slides about what the app is for, then one optional
 * identity step. Skip is offered on every screen and counts as completing, so nobody is
 * trapped in it and nobody is asked twice.
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel) {
    val state by viewModel.state.collectAsState()

    when (state.step) {
        OnboardingStep.SETUP -> SetupStep(
            onSkip = viewModel::skip,
            onFinish = viewModel::finishSetup,
        )
        else -> SlideStep(
            index = state.slideIndex,
            isLast = isLastSlide(state),
            onBack = viewModel::back,
            onSkip = viewModel::skip,
            onNext = viewModel::next,
        )
    }
}

@Composable
private fun SlideStep(
    index: Int,
    isLast: Boolean,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    val slide = SLIDES[index.coerceIn(SLIDES.indices)]

    Column(modifier = Modifier.fillMaxSize().padding(AppSpacing.screenPadding)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text("THE STUDIO", style = EyebrowStyle)
            Spacer(Modifier.height(AppSpacing.s2))
            Text(slide.title, style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(AppSpacing.s3))
            Text(
                slide.body,
                color = AppColors.TextDim,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        // Position, not decoration: three slides, and this says which one you are on.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            SLIDES.indices.forEach { i ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (i == index) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (i == index) AppColors.Ink else AppColors.LineStrong),
                )
            }
        }
        Spacer(Modifier.height(AppSpacing.s5))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The first slide has nothing to go back to, so that slot offers the exit instead.
            TextButton(onClick = if (index > 0) onBack else onSkip) {
                Text(if (index > 0) "Back" else "Skip")
            }
            PrimaryButton(onClick = onNext, modifier = Modifier.weight(1f)) {
                Text(if (isLast) "Continue" else "Next")
            }
        }
    }
}

@Composable
private fun SetupStep(onSkip: () -> Unit, onFinish: (String, String, FocusLevel) -> Unit) {
    var username by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    var level by remember { mutableStateOf(FocusLevel.DEFAULT) }

    Column(modifier = Modifier.fillMaxSize().padding(AppSpacing.screenPadding)) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        ) {
            Text("A little about you", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(AppSpacing.s2))
            Text(
                "All optional — you can change any of it later, or skip it entirely.",
                color = AppColors.TextDim,
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(AppSpacing.s6))
            Text("What should we call you?", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(AppSpacing.s1))
            OutlinedTextField(
                value = username,
                onValueChange = { if (it.length <= MAX_USERNAME) username = it },
                placeholder = { Text("The Artist") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(AppSpacing.s4))
            Text("What is all this focus for?", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(AppSpacing.s1))
            OutlinedTextField(
                value = goal,
                onValueChange = { if (it.length <= MAX_GOAL) goal = it },
                placeholder = { Text("Finish the dissertation") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(AppSpacing.s5))
            Text("How long do you usually focus?", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(AppSpacing.s2))
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s2)) {
                FocusLevel.entries.forEach { option ->
                    LevelCard(option, option == level, Modifier.weight(1f)) { level = option }
                }
            }
            Spacer(Modifier.height(AppSpacing.s2))
            // I4 — a courtesy, not a cage. Saying so stops the three options reading as
            // difficulty tiers that lock durations away.
            Text(
                "Only sets where the setup wheels start. Every duration stays available.",
                color = AppColors.TextDim,
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(Modifier.height(AppSpacing.s5))
            NotificationAsk()
        }

        Spacer(Modifier.height(AppSpacing.s4))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onSkip) { Text("Skip") }
            PrimaryButton(
                onClick = { onFinish(username, goal, level) },
                modifier = Modifier.weight(1f),
            ) { Text("Enter the studio") }
        }
    }
}

/**
 * Asks for notification permission here, at the end of the introduction.
 *
 * Until now the request lived in Profile alone, which meant a user who never opened that tab was
 * never asked — and every notification was then silently discarded. This is the "catch everyone"
 * half of the fix; the Focus screen asks again at the first rest, where the reason is concrete,
 * for anyone who declines or skips past this.
 *
 * Deliberately not a blocking step and not a slide of its own: the introduction is premise, not
 * a permissions gate (F12.1), and nothing here stops the user pressing on. It disappears once
 * granted rather than showing a satisfied checkbox, since the only useful state is the unfixed one.
 */
@Composable
private fun NotificationAsk() {
    val permission = rememberNotificationPermission()
    if (permission.granted) return

    Text("One thing to allow", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(AppSpacing.s1))
    Text(
        "So the app can tell you when a sitting finishes and when a rest is over — the two " +
            "moments you would otherwise have to watch the clock for.",
        color = AppColors.TextDim,
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(AppSpacing.s2))
    QuietButton(onClick = permission.request) {
        Text(if (permission.mustUseSettings) "Open notification settings" else "Allow notifications")
    }
}

/** The same stacked name + default-duration card Profile settings uses. */
@Composable
private fun LevelCard(level: FocusLevel, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
