package com.pawquest.presentation.pet

import androidx.compose.runtime.*
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pawquest.domain.model.*
import kotlinx.coroutines.delay
import kotlinx.datetime.*
import kotlin.time.Clock

@Composable fun rememberPetDialogue(animation: PetAnimationState,stats: PetStats): String {
    var active by remember {mutableStateOf(false)}
    var text by remember {mutableStateOf("")}
    LifecycleResumeEffect(Unit) {active=true;onPauseOrDispose {active=false}}
    LaunchedEffect(animation,stats.hunger<=25,stats.hydration<=25,active) {
        if(!active) return@LaunchedEffect
        while(true) {
            val hour=Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
            text=PetDialogues.choose(PetDialogues.topic(hour,animation,stats),text)
            delay(30_000)
        }
    }
    return text
}
