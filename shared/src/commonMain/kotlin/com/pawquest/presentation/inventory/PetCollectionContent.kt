package com.pawquest.presentation.inventory

import com.pawquest.localization.tr
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pawquest.domain.model.*
import com.pawquest.presentation.pet.LocalPetController
import com.pawquest.presentation.components.*
import com.pawquest.presentation.theme.PawQuestTheme
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import com.pawquest.resources.*

@Composable fun PetCollectionContent(modifier: Modifier=Modifier) {
    val controller=LocalPetController.current
    val state=controller?.state
    LaunchedEffect(Unit) {controller?.loadCollection?.invoke()}
    LazyColumn(modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=20.dp)) {
        item {
            Text(tr("Your pets"),style=PawQuestTheme.typography.titleLarge)
            Text(tr("Unlock companions through Lucky Wheel or Pet Shard Exchange. Switching preserves your XP, care stats and inventory."),style=PawQuestTheme.typography.bodyMedium)
            PawQuestButton("Refresh pets",{controller?.loadCollection?.invoke()},enabled=state?.collectionBusy!=true)
        }
        if(state?.collectionBusy==true) item {PawQuestLoading(message="Checking your companions...")}
        state?.collectionError?.let {error -> item {Text(tr(error),color=PawQuestTheme.colors.error)} }
        val collection=state?.collection
        items(collection?.species.orEmpty(),key={it.code}) {species ->
            PawQuestCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    val portrait=when(species.rendererKey) {
                        "prototype_cat"->Res.drawable.pet_mochi
                        "puppy"->Res.drawable.pet_puppy
                        "capybara"->Res.drawable.pet_capybara
                        "rabbit"->Res.drawable.pet_rabbit
                        else->null
                    }
                    portrait?.let {Image(painterResource(it),tr("Preview of ${tr(species.name)}"),
                        Modifier.fillMaxWidth().height(160.dp),contentScale=ContentScale.Fit)}
                    Text(tr(species.name),style=PawQuestTheme.typography.titleMedium)
                    val active=collection?.activeCode==species.code
                    val art=PetArtworkRegistry.find(species.rendererKey)!=null
                    Text(tr(when {active->"Selected companion";!species.unlocked->"Unlock this pet through Lucky Wheel or Pet Shard Exchange.";!art->"Update the app to install this pet's artwork.";else->"Unlocked and ready to join you."}))
                    if(PetArtworkRegistry.find(species.rendererKey)?.supportsEquipment==false)
                        Text(tr("Your accessories stay saved; this companion doesn't have accessory illustrations yet."))
                    PawQuestButton(if(active) "Selected" else "Use ${tr(species.name)}",
                        {controller?.selectSpecies?.invoke(species.code)},
                        enabled=!active && state?.busy!=true && state?.collectionBusy!=true && PetUnlockRules.canSelect(species,collection?.level ?: 1))
                }
            }
        }
    }
}
