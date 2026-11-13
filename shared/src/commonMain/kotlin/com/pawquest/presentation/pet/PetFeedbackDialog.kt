package com.pawquest.presentation.pet

import com.pawquest.localization.tr
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*

@Composable fun PetFeedbackDialog(state: PetState,onDismiss: ()->Unit,onShop: ()->Unit,onQuests: ()->Unit) {
    state.feedback?.let {message ->
        AlertDialog(onDismissRequest=onDismiss,title={Text(tr("Your companion"))},
            text={Text(tr(message),Modifier.verticalScroll(rememberScrollState()).semantics {liveRegion=LiveRegionMode.Polite})},
            confirmButton={TextButton(onClick=onDismiss) {Text(tr("Got it"))}},
            dismissButton={if(state.feedbackCode=="no_food") {
                val empty=state.snapshot?.coins==0L
                TextButton(onClick={onDismiss();if(empty) onQuests() else onShop()}) {Text(tr(if(empty) "Earn Coins" else "Visit Shop"))}
            }})
    }
}
