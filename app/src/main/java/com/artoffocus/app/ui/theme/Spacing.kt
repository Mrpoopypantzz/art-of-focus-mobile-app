package com.artoffocus.app.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/** Ported 1:1 from the web app's 4px spacing scale (tokens.css --s-1..--s-12). */
object AppSpacing {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 24.dp
    val s8 = 32.dp
    val s10 = 40.dp
    val s12 = 48.dp
}

/** Matches the web app's screen-root rhythm: heavier top, lighter sides, moderate bottom. */
val AppSpacing.screenPadding: PaddingValues
    get() = PaddingValues(start = s5, top = s8, end = s5, bottom = s6)
