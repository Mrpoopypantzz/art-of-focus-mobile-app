package com.artoffocus.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.artoffocus.app.ui.theme.AppColors
import com.artoffocus.app.ui.theme.AppRadius
import com.artoffocus.app.ui.theme.AppSpacing

/**
 * The app's single modal system — mirrors the web app's shared Sheet.tsx (used by setup,
 * rest selection, sitting-complete, and this cancel-confirmation flow alike). Future popups
 * should reuse this rather than a second popup pattern.
 *
 * Built on Material3's [ModalBottomSheet] for drag-to-dismiss and scrim handling for free;
 * only the container color/shape/dragHandle/scrimColor are themed to match. The web's scrim
 * also blurs the backdrop (backdrop-filter: blur(2px)) — omitted here since Compose blur
 * needs API 31+ and this app's minSdk is 26; the dim-only scrim is an acceptable simplification.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    onDismissRequest: () -> Unit,
    title: String,
    subtitle: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = AppColors.Surface,
        shape = RoundedCornerShape(topStart = AppRadius.xl, topEnd = AppRadius.xl),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AppColors.LineStrong),
            )
        },
        scrimColor = AppColors.Ink.copy(alpha = 0.34f),
    ) {
        Column(modifier = Modifier.padding(horizontal = AppSpacing.s6, vertical = AppSpacing.s2)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            subtitle?.let {
                androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s1))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s3))
            content()
            footer?.let {
                androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s4))
                it()
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(AppSpacing.s4))
        }
    }
}
