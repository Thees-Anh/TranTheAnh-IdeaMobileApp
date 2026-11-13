package com.pawquest.presentation.bistro

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pawquest.domain.model.*
import com.pawquest.localization.tr
import com.pawquest.presentation.components.*
import com.pawquest.presentation.pet.LocalPetController
import com.pawquest.presentation.theme.PawQuestTheme

val LocalBistro=compositionLocalOf<BistroViewModel?> {null}
data class BistroPresentation(val menu: List<BistroItem>,val species: String,val canOrder: Boolean,val receipt: BistroReceipt?,
    val onSelect: (String)->Unit,val onFinished: (String)->Unit)
@Composable expect fun BistroRenderer(value: BistroPresentation,modifier: Modifier=Modifier)

@Composable fun BistroContent(model: BistroViewModel,modifier: Modifier=Modifier,
    pantryItem: CatalogItem?=null,onBuyPantry: ((CatalogItem)->Unit)?=null) {
    val state by model.state.collectAsStateWithLifecycle()
    val pet=LocalPetController.current?.state
    var confirm by remember(state.owner) {mutableStateOf<String?>(null)}
    LaunchedEffect(model,state.owner) {if(state.owner!=null) model.loadMenu()}
    DisposableEffect(model) {model.visible(true);onDispose {model.visible(false)}}
    val receipt=state.receipt
    // Navigation/interruption never reverses a committed meal or starts another.
    DisposableEffect(receipt?.id) {onDispose {receipt?.let {model.animationFinished(it.id)}}}
    Column(modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(tr("PawQuest Bistro"),style=PawQuestTheme.typography.titleLarge)
        Text(tr("Choose a meal to buy and serve immediately."),style=PawQuestTheme.typography.labelSmall)
        if(state.loading) Text(tr("Loading the Bistro menu..."))
        state.error?.let {Text(tr(it),color=PawQuestTheme.colors.error);PawQuestButton("Retry",model::retry,enabled=!state.busy && !state.recovering)}
        if(state.busy) Text(tr("Confirming your meal..."))
        if(state.menu.isNotEmpty() || receipt!=null) {
            val species=if(state.animationPending && receipt!=null) {
                if(receipt.species=="mochi_cat") "prototype_cat" else receipt.species
            } else pet?.snapshot?.pet?.rendererKey ?: "prototype_cat"
            BistroRenderer(BistroPresentation(state.menu,species,
                state.canOrder && pet?.busy!=true && pet?.pendingAction==null,
                receipt?.takeIf {state.animationPending},{id->if(state.canOrder && state.menu.any {it.itemId==id}) confirm=id},model::animationFinished),
                Modifier.fillMaxWidth().weight(1f))
        } else if(!state.loading && state.error==null) {
            PawQuestButton("Refresh",model::loadMenu,enabled=state.owner!=null && !state.recovering)
            Spacer(Modifier.weight(1f))
        }
        if(state.animationPending && receipt!=null) PawQuestButton("Skip Animation",{model.animationFinished(receipt.id)},modifier=Modifier.fillMaxWidth())
        if(pantryItem!=null && onBuyPantry!=null && !state.animationPending) PawQuestButton("Buy pantry food",{onBuyPantry(pantryItem)},
            enabled=!state.busy && state.pending==null,variant=ButtonVariant.Secondary,size=ButtonSize.Small)
    }
    val item=state.menu.firstOrNull {it.itemId==confirm}
    if(item!=null) AlertDialog(onDismissRequest={confirm=null},title={Text(tr(item.name))},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(tr("Spend ${item.price} Coins and serve this meal now?"))
        Text(tr("Stats cap at 100. EXP is still granted."))
        Text("+${item.hunger} ${tr("Fullness")} · +${item.happiness} ${tr("Joy")} · +${item.xp} EXP")
    }},confirmButton={PawQuestButton("Buy & serve",{confirm=null;model.order(item.itemId)},enabled=state.canOrder)},
        dismissButton={PawQuestButton("Cancel",{confirm=null},variant=ButtonVariant.Secondary)})
}
