package com.pawquest.presentation.gacha

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.pawquest.localization.tr
import com.pawquest.presentation.components.PawQuestButton
import com.pawquest.presentation.theme.PawQuestTheme

@Composable
internal fun GachaAnimationStage(title: String, backdrop: Color, darkIcons: Boolean, onSkip: () -> Unit,
    artwork: @Composable (Modifier) -> Unit) {
    Dialog(onDismissRequest=onSkip,properties=gachaAnimationDialogProperties()) {
        GachaAnimationSystemBars(darkIcons)
        Box(Modifier.fillMaxSize().background(backdrop)) {
            Box(Modifier.matchParentSize().clearAndSetSemantics {hideFromAccessibility()}) {
                artwork(Modifier.fillMaxSize())
            }
            Box(Modifier.matchParentSize().zIndex(1f).safeDrawingPadding()) {
                Text(tr(title),Modifier.align(Alignment.TopStart).padding(horizontal=16.dp,vertical=12.dp),
                    style=PawQuestTheme.typography.titleLarge,color=if(darkIcons) Color(0xFF4A2511) else Color.White)
                PawQuestButton("Skip Animation",onSkip,
                    modifier=Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp))
            }
        }
    }
}

internal expect fun gachaAnimationDialogProperties(): DialogProperties

@Composable internal expect fun GachaAnimationSystemBars(darkIcons: Boolean)
