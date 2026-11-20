package com.pawquest.presentation.gacha

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

internal actual fun gachaAnimationDialogProperties()=DialogProperties(
    usePlatformDefaultWidth=false,dismissOnClickOutside=false)

@Composable internal actual fun GachaAnimationSystemBars(darkIcons: Boolean) = Unit
