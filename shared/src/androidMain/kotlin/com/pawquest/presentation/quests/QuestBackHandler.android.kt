package com.pawquest.presentation.quests

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable internal actual fun QuestBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled, onBack)
}
