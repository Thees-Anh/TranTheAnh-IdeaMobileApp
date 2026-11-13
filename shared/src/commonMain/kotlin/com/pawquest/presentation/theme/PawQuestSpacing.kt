package com.pawquest.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standard 4dp/8dp grid spacing tokens for PawQuest 3D.
 */
@Immutable
data class PawQuestSpacing(
    val none: Dp = 0.dp,
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val huge: Dp = 32.dp,
    val massive: Dp = 48.dp,
)

val LocalPawQuestSpacing = staticCompositionLocalOf { PawQuestSpacing() }
