package com.pawquest.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pawquest.domain.telemetry.NoOpTelemetry
import com.pawquest.domain.telemetry.Telemetry
import com.pawquest.localization.tr
import com.pawquest.presentation.theme.PawQuestTheme

/** Crash reporting for the composition (screen context and the Settings switch). */
val LocalTelemetry=staticCompositionLocalOf<Telemetry> {NoOpTelemetry}

/** Shown when the server's minimum version is above this build; nothing else is reachable. */
@Composable
fun UpdateRequiredScreen(storeUrl: String,modifier: Modifier=Modifier) {
    val colors=PawQuestTheme.colors
    val uri=LocalUriHandler.current
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp,Alignment.CenterVertically),
        horizontalAlignment=Alignment.CenterHorizontally) {
        Text(tr("Please update PawQuest"),style=PawQuestTheme.typography.headlineLarge,color=colors.textPrimary,
            textAlign=TextAlign.Center,modifier=Modifier.semantics {heading()})
        Text(tr("This version is no longer supported. Update to keep your pet, Coins and quests safe."),
            style=PawQuestTheme.typography.bodyLarge,color=colors.textSecondary,textAlign=TextAlign.Center)
        if(storeUrl.startsWith("https://")) PawQuestButton("Update now",{runCatching {uri.openUri(storeUrl)}},
            modifier=Modifier.fillMaxWidth().height(52.dp))
    }
}
