package com.pawquest.presentation.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Friendly rounded corner and shape tokens for PawQuest 3D.
 * Mimics playful, tactile 3D game geometry with smooth curved corners.
 */
@Immutable
data class PawQuestShapes(
    val xs: Shape = RoundedCornerShape(8.dp),
    val sm: Shape = RoundedCornerShape(12.dp),
    val md: Shape = RoundedCornerShape(16.dp),
    val lg: Shape = RoundedCornerShape(20.dp),
    val xl: Shape = RoundedCornerShape(24.dp),
    val xxl: Shape = RoundedCornerShape(28.dp),
    val pill: Shape = CircleShape,
    val card: Shape = RoundedCornerShape(20.dp),
    val button: Shape = RoundedCornerShape(16.dp),
    val badge: Shape = RoundedCornerShape(12.dp),
    val statBar: Shape = RoundedCornerShape(10.dp),
)

val LocalPawQuestShapes = staticCompositionLocalOf { PawQuestShapes() }
