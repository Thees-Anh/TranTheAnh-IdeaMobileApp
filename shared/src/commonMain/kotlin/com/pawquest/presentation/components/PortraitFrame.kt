package com.pawquest.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pawquest.presentation.theme.PawQuestTheme

/** Windows at or below this width get the full-width phone layout. */
internal val PortraitFrameThreshold = 600.dp

/**
 * Width of the centered portrait column for a wide window, or null when the window
 * is phone-sized. Never stretches the phone design: at least 9:16 of the height,
 * clamped to a readable 360–520dp column.
 */
internal fun portraitFrameWidth(windowWidth: Dp, windowHeight: Dp): Dp? {
    if (windowWidth <= PortraitFrameThreshold) return null
    val target = (windowHeight * 9f / 16f).coerceIn(360.dp, 520.dp)
    return if (target >= windowWidth) null else target
}

/**
 * PawQuest is portrait-only, but Android 16+ ignores orientation locks for apps targeting
 * API 36+ on large screens (tablets, foldables, desktop windows). Wide windows show the
 * phone UI as a centered portrait column instead of stretching it.
 */
@Composable
fun PortraitFrame(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(PawQuestTheme.colors.backgroundAlt)) {
        val frameWidth = portraitFrameWidth(maxWidth, maxHeight)
        if (frameWidth == null) content()
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.width(frameWidth).fillMaxHeight().background(PawQuestTheme.colors.background)) { content() }
        }
    }
}
