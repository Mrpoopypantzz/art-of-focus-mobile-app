package com.artoffocus.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.artoffocus.app.ui.theme.AppColors
import com.artoffocus.app.ui.theme.AppRadius
import com.artoffocus.app.ui.theme.AppSpacing

/**
 * The app's shared segmented-toggle look — ink fill when selected, sunken surface otherwise,
 * uppercase labels. Used wherever the web app reaches for its own bespoke pill row (Focus's
 * tier anchors, Profile's section tabs, Museum's Wall/Timeline switch) rather than a stock
 * Material3 SegmentedButton, whose default color role doesn't match this palette.
 */
@Composable
fun <T> PillToggleRow(options: List<T>, selected: T, labelFor: (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.s2), modifier = Modifier.fillMaxWidth()) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(AppRadius.pill)
                    .background(if (isSelected) AppColors.Ink else AppColors.SurfaceSunken)
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    labelFor(option).uppercase(),
                    color = if (isSelected) AppColors.OnInk else AppColors.TextDim,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
