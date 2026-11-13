package com.pawquest.presentation.gacha

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pawquest.domain.model.PetCareAction
import com.pawquest.domain.model.PetCareGate
import com.pawquest.domain.model.PetNeed
import com.pawquest.localization.tr
import com.pawquest.presentation.components.ButtonSize
import com.pawquest.presentation.components.ButtonVariant
import com.pawquest.presentation.components.PawQuestButton
import com.pawquest.presentation.pet.LocalPetController
import com.pawquest.presentation.theme.PawQuestTheme

/** The pet's current need that blocks Mystery Box and Lucky Wheel draws, from the confirmed pet state. */
@Composable
internal fun currentPetNeed(): PetNeed? =
    LocalPetController.current?.state?.snapshot?.pet?.stats?.let(PetCareGate::need)

/** Explains why draws are paused and offers the matching care action right here. */
@Composable
internal fun PetNeedsCareCard(need: PetNeed, modifier: Modifier = Modifier) {
    val controller = LocalPetController.current
    val colors = PawQuestTheme.colors
    val busy = controller?.state?.pendingAction != null
    Surface(color = colors.hungerContainer, shape = PawQuestTheme.shapes.card, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr(when (need) {
                PetNeed.HUNGRY -> "Your pet is hungry"
                PetNeed.THIRSTY -> "Your pet is thirsty"
                PetNeed.HUNGRY_THIRSTY -> "Your pet is hungry and thirsty"
            }), style = PawQuestTheme.typography.titleMedium, color = colors.hungerDark)
            Text(tr("Mystery Box and Lucky Wheel wait until Food and Water are above {0}%."
                .replace("{0}", PetCareGate.THRESHOLD.toString())),
                style = PawQuestTheme.typography.bodyMedium, color = colors.textPrimary)
            if (controller != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (need != PetNeed.THIRSTY) PawQuestButton("Feed", { controller.care(PetCareAction.FEED) },
                    size = ButtonSize.Small, variant = ButtonVariant.Accent, enabled = !busy, modifier = Modifier.weight(1f))
                if (need != PetNeed.HUNGRY) PawQuestButton("Drink", { controller.care(PetCareAction.DRINK) },
                    size = ButtonSize.Small, variant = ButtonVariant.Secondary, enabled = !busy, modifier = Modifier.weight(1f))
            }
        }
    }
}
