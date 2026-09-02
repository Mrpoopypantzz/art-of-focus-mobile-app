package com.artoffocus.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** M3's [Shapes] has no pill slot — buttons that need a true pill use this directly. */
object AppRadius {
    val sm = 10.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val pill = RoundedCornerShape(50)
}
