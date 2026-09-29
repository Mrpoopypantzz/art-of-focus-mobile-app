package com.artoffocus.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artoffocus.app.ui.theme.AppColors
import com.artoffocus.app.ui.theme.AppRadius
import kotlin.math.abs

/**
 * A horizontally-scrolling, snap-to-center value picker — matches the web app's Wheel
 * component: a fixed cream "band" stays centered, items scroll past it, and whichever item
 * settles under the band becomes the selection (fired on scroll settle, not just on tap).
 *
 * Smoothness rules, learned the hard way:
 * - Item text never changes size or weight. Emphasis is a `graphicsLayer` scale/alpha, which
 *   the GPU applies without re-measuring text — swapping font size mid-scroll re-runs text
 *   layout for every visible item on a variable-weight font, which is what made this janky.
 * - The centered index is derived from scroll offset continuously, so the band tracks the
 *   finger. The *value* is only committed once scrolling settles.
 * - Programmatic scrolling only reacts to selection changes that came from outside this
 *   composable; otherwise it fights the user's own fling.
 */
@Composable
fun WheelPicker(
    values: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    label: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    val itemWidth = 96.dp
    val itemHeight = 52.dp

    BoxWithConstraints(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(AppRadius.md))
            .background(AppColors.SurfaceSunken),
    ) {
        val sidePadding = ((maxWidth - itemWidth) / 2).coerceAtLeast(0.dp)
        val listState = rememberLazyListState()
        val selectedIndex = values.indexOf(selected).coerceAtLeast(0)

        // Which item currently sits under the band. Recomputed from scroll geometry as the
        // list moves, so the highlight follows the finger instead of snapping after settle.
        val centeredIndex by remember(listState) {
            derivedStateOf {
                val info = listState.layoutInfo
                val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                info.visibleItemsInfo
                    .minByOrNull { abs((it.offset + it.size / 2) - center) }
                    ?.index
                    ?: 0
            }
        }

        // Programmatic scrolling must not fight the user. `selected` changes for two reasons:
        // the user scrolled this wheel (already in the right place — do nothing), or something
        // outside set it (a tier button, a clamp after the total changed — scroll there).
        val currentCentered = rememberUpdatedState(centeredIndex)
        LaunchedEffect(selectedIndex) {
            if (selectedIndex != currentCentered.value) {
                listState.animateScrollToItem(selectedIndex)
            }
        }

        // Commit on settle only. Mid-scroll the band highlights `centeredIndex` visually, but
        // the caller's value changes once, at rest — not once per item flying past.
        val currentOnSelect = rememberUpdatedState(onSelect)
        val currentSelected = rememberUpdatedState(selected)
        val currentValues = rememberUpdatedState(values)
        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }.collect { inProgress ->
                if (!inProgress) {
                    currentValues.value.getOrNull(currentCentered.value)?.let { value ->
                        if (value != currentSelected.value) currentOnSelect.value(value)
                    }
                }
            }
        }

        // Edge fades sit *behind* the items (drawn before the LazyRow) so the scrolling
        // content is never composited through an extra translucent layer every frame.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(itemWidth)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(AppColors.SurfaceSunken, AppColors.SurfaceSunken.copy(alpha = 0f)))),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(itemWidth)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(AppColors.SurfaceSunken.copy(alpha = 0f), AppColors.SurfaceSunken))),
        )

        // The fixed cream "band" — always centered, decorative only.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(itemWidth)
                .height(itemHeight)
                .clip(RoundedCornerShape(AppRadius.sm))
                .background(AppColors.Surface),
        )

        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState, snapPosition = SnapPosition.Center),
            contentPadding = PaddingValues(horizontal = sidePadding),
            // Items are 52dp inside a 64dp box; without this they pin to the top and sit 6dp
            // above the vertically-centered band.
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(values) { index, value ->
                WheelItem(
                    text = label(value),
                    itemWidth = itemWidth,
                    itemHeight = itemHeight,
                    // Read inside the graphicsLayer lambda at draw time — never in composition,
                    // so scrolling re-draws without recomposing or re-measuring any item.
                    emphasis = { emphasisFor(listState, index) },
                    onClick = { onSelect(value) },
                )
            }
        }
    }
}

/**
 * 1f when the item is dead-centre, falling to 0f a full item-width away. Derived from live
 * scroll geometry rather than an animation, so there are no per-item animation objects
 * running during a fling — the wheel's emphasis simply *is* its distance from the band.
 */
private fun emphasisFor(listState: LazyListState, index: Int): Float {
    val info = listState.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return 0f
    if (item.size == 0) return 0f
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    val distance = abs((item.offset + item.size / 2f) - center)
    return (1f - distance / item.size).coerceIn(0f, 1f)
}

@Composable
private fun WheelItem(
    text: String,
    itemWidth: androidx.compose.ui.unit.Dp,
    itemHeight: androidx.compose.ui.unit.Dp,
    emphasis: () -> Float,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(itemWidth)
            .height(itemHeight)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = AppColors.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium,
            // Size and weight stay fixed; only this transform changes. A graphicsLayer block
            // runs at draw time, so no text re-layout happens as the wheel moves.
            modifier = Modifier.graphicsLayer {
                val e = emphasis()
                val scale = 1f + 0.2f * e
                scaleX = scale
                scaleY = scale
                alpha = 0.45f + 0.55f * e
            },
        )
    }
}
