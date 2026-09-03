package com.artoffocus.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artoffocus.app.ui.theme.AppColors

data class TabBarItem(val label: String, val glyph: String, val selected: Boolean, val onClick: () -> Unit)

/**
 * A floating ink-filled pill, bottom-center — not a full-width Material3 NavigationBar. The
 * stock widget is full-width by construction, which fights this design's shape more than it
 * saves effort, so this is a small standalone composable instead of a restyle.
 */
@Composable
fun TabBar(items: List<TabBarItem>, modifier: Modifier = Modifier) {
    val pill = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .shadow(elevation = 12.dp, shape = pill, ambientColor = AppColors.Ink.copy(alpha = 0.22f), spotColor = AppColors.Ink.copy(alpha = 0.22f))
            .clip(pill)
            .background(AppColors.Ink)
            .padding(8.dp),
    ) {
        items.forEach { item ->
            val itemShape = RoundedCornerShape(50)
            Column(
                modifier = Modifier
                    .widthIn(min = 62.dp)
                    .height(46.dp)
                    .clip(itemShape)
                    .background(if (item.selected) AppColors.OnInk.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(onClick = item.onClick)
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                val itemColor = if (item.selected) AppColors.OnInk else AppColors.OnInk.copy(alpha = 0.55f)
                Text(text = item.glyph, color = itemColor, fontSize = 15.sp, textAlign = TextAlign.Center)
                androidx.compose.foundation.layout.Spacer(Modifier.height(2.dp))
                Text(
                    text = item.label,
                    color = itemColor,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
