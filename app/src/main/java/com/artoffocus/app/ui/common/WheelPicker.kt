package com.artoffocus.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    BoxWithConstraints(modifier = modifier.height(64.dp)) {
        val sidePadding = ((maxWidth - itemWidth) / 2).coerceAtLeast(0.dp)
        val listState = rememberLazyListState()
        val selectedIndex = values.indexOf(selected).coerceAtLeast(0)

        LaunchedEffect(selectedIndex, values) {
            listState.animateScrollToItem(selectedIndex)
        }

        // LaunchedEffect(listState) never restarts (listState's identity is stable across
        // recompositions), so this coroutine lives for the composable's whole lifetime — it
        // must read onSelect/selected/values through rememberUpdatedState, not capture them
        // directly, or it keeps using whatever those were on the very first composition.
        val currentOnSelect = rememberUpdatedState(onSelect)
        val currentSelected = rememberUpdatedState(selected)
        val currentValues = rememberUpdatedState(values)
        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }.collect { inProgress ->
                if (!inProgress) {
                    val info = listState.layoutInfo
                    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                    val centered = info.visibleItemsInfo.minByOrNull { item ->
                        abs((item.offset + item.size / 2) - center)
                    }
                    centered?.let { item ->
                        currentValues.value.getOrNull(item.index)?.let { value ->
                            if (value != currentSelected.value) currentOnSelect.value(value)
                        }
                    }
                }
            }
        }

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
            modifier = Modifier.fillMaxSize(),
        ) {
            items(values) { value ->
                val isSelected = value == selected
                Box(
                    modifier = Modifier.width(itemWidth).height(itemHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(value),
                        color = if (isSelected) AppColors.Text else AppColors.TextFaint,
                        fontSize = if (isSelected) 18.sp else 15.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
