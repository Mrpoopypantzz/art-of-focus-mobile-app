package com.artoffocus.app.ui.common

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.artoffocus.app.ui.theme.AppColors
import com.artoffocus.app.ui.theme.AppRadius

/** Ink fill, cream text, pill shape — the primary call-to-action treatment throughout. */
@Composable
fun PrimaryButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled, shape = AppRadius.pill, content = content)
}

/** Sunken-surface fill, dim text, no border/shadow — the "quiet" secondary action treatment. */
@Composable
fun QuietButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = AppRadius.pill,
        border = null,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = AppColors.SurfaceSunken, contentColor = AppColors.TextDim),
        content = content,
    )
}

/** Ink-soft fill — the destructive/confirm-cancel treatment (this palette has no separate red). */
@Composable
fun DangerButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = AppRadius.pill,
        colors = ButtonDefaults.buttonColors(containerColor = AppColors.InkSoft, contentColor = AppColors.OnInk),
        content = content,
    )
}
