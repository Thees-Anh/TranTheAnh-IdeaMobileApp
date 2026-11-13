package com.pawquest.presentation.quests

import androidx.compose.runtime.Composable

// iOS has the explicit screen Back action; no Android-style hardware back button.
@Composable internal actual fun QuestBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
