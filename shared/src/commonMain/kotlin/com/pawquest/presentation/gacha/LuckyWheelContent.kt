package com.pawquest.presentation.gacha

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pawquest.domain.model.*
import com.pawquest.localization.tr
import com.pawquest.presentation.components.*
import com.pawquest.presentation.theme.PawQuestTheme
import com.pawquest.resources.*
import org.jetbrains.compose.resources.painterResource

internal fun petDisplayName(code: String)=when(code) {"rabbit"->"Rabbit";"puppy"->"Dog";"capybara"->"Capybara";else->code}
internal fun wheelRewardText(r: LuckyWheelReceipt)=when(r.actualReward) {
    WheelReward.FOOD -> "+${r.foodQuantity} Food"
    WheelReward.COINS -> "+${r.coinsEarned} Coins"
    WheelReward.TICKET -> "+${r.ticketsEarned} Lucky Wheel Ticket"
    WheelReward.PET_SHARDS -> "+${r.petShardsEarned} Pet Shards"
    WheelReward.PET_UNLOCKED -> "Pet unlocked: ${petDisplayName(checkNotNull(r.selectedPetId))}"
    WheelReward.PET_UNLOCK_CONVERTED_TO_SHARDS -> "All pets owned: +${r.petShardsEarned} Pet Shards"
}
internal fun sectorRewardText(s: WheelSectorConfiguration)=when(s.sector) {
    WheelSector.FOOD -> "${s.amount} Food"
    WheelSector.COINS_50 -> "${s.amount} Coins"
    WheelSector.LUCKY_TICKET_1 -> "${s.amount} Lucky Wheel Ticket"
    WheelSector.PET_UNLOCK -> "Unlock one unowned pet"
    else -> "${s.amount} Pet Shards"
}
internal fun wheelError(state: LuckyWheelState)=when(state.rejection) {
    LuckyWheelRejection.INSUFFICIENT_COINS -> "Not enough Coins."
    LuckyWheelRejection.INSUFFICIENT_TICKETS -> "Not enough Lucky Wheel Tickets."
    LuckyWheelRejection.INSUFFICIENT_SHARDS -> "Not enough Pet Shards."
    LuckyWheelRejection.ALREADY_OWNED -> "You already own this pet."
    LuckyWheelRejection.BANNER_UNAVAILABLE -> "Lucky Wheel is not available yet."
    LuckyWheelRejection.PRICE_CHANGED -> "Price changed. Close and select the pet again."
    LuckyWheelRejection.QUOTE_EXPIRED -> "Quote expired. Close and select the pet again."
    LuckyWheelRejection.INVENTORY_FULL -> "Food inventory is full. Use some food before spinning."
    LuckyWheelRejection.PET_NEEDS_CARE -> "Your pet is hungry or thirsty. Care for it before spinning."
    LuckyWheelRejection.RATE_LIMITED,LuckyWheelRejection.BUSY -> "Please wait, then retry the same request."
    else -> state.error ?: "Couldn't confirm the operation. Retry the same request safely."
}

@Composable internal fun PetRewardPortrait(code: String,modifier: Modifier=Modifier) {
    val image=when(code) {"rabbit"->Res.drawable.pet_rabbit;"puppy"->Res.drawable.pet_puppy;"capybara"->Res.drawable.pet_capybara;else->Res.drawable.pet_mochi}
    Image(painterResource(image),tr(petDisplayName(code)),modifier.height(104.dp).fillMaxWidth())
}

@Composable fun LuckyWheelContent(model: LuckyWheelViewModel,modifier: Modifier=Modifier,
    preview: (@Composable (LuckyWheelAnimation,Modifier)->Unit)?=null,onPetExchange: ()->Unit={}) {
    val state by model.state.collectAsStateWithLifecycle()
    var panel by remember {mutableStateOf<String?>(null)}
    val petNeed=currentPetNeed()
    val petController=com.pawquest.presentation.pet.LocalPetController.current
    // The server refused because the pet got hungry/thirsty since the last sync: show its current state.
    LaunchedEffect(state.rejection) {if(state.rejection==LuckyWheelRejection.PET_NEEDS_CARE) petController?.retry?.invoke()}
    val playing=state.phase in setOf(LuckyWheelPhase.ReadyToAnimate,LuckyWheelPhase.Animating)
    val result=state.result
    val scroll=rememberLazyListState()
    val animation=LuckyWheelAnimation(state.configuration,if(playing || state.skipRequested) result else null,state.skipRequested,model::animationStarted,model::animationFinished)
    BoxWithConstraints(modifier.fillMaxSize()) {
    val stageHeight=(maxHeight-56.dp).coerceIn(180.dp,330.dp)
    LazyColumn(Modifier.fillMaxSize(),state=scroll,verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=20.dp)) {
        item {Text(tr("Lucky Wheel"),style=PawQuestTheme.typography.titleLarge);Text(tr("Spin for Food, Coins, Tickets, Pet Shards or a new companion."))}
        item {
            if(preview!=null && !playing) preview(animation.copy(receipt=null),Modifier.fillMaxWidth().height(stageHeight))
            else {Text(tr("Wheel preview unavailable."));if(playing) PawQuestButton("Show confirmed reward",model::skip)}
        }
        item {PawQuestCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            val c=state.configuration;val b=state.balances
            if(c==null) Text(tr(if(state.busy) "Loading Wheel configuration..." else "Wheel configuration unavailable. Please retry."))
            CurrencyAmount(b?.petShards,"Pet Shards",ShardIcons.Pet,Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally))
            CurrencyAmount(b?.tickets,"Lucky Wheel Tickets",ShardIcons.Ticket,Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally))
            if(c?.enabled==false) Text(tr("Lucky Wheel is not available yet."))
            if(b!=null && c!=null && b.coins<c.priceCoins) Text(tr("Not enough Coins."))
            if(b!=null && b.tickets<1) Text(tr("No Lucky Wheel Tickets. Choose Coins or earn a Ticket from the wheel."))
            petNeed?.let {PetNeedsCareCard(it)}
            PawQuestButton("Spin using Coins",{model.spin(WheelPayment.COINS)},enabled=state.canSpin && petNeed==null && b!=null && c!=null && b.coins>=c.priceCoins,modifier=Modifier.fillMaxWidth(),
                trailingContent={CurrencyPrice(c?.priceCoins?.toLong(),com.pawquest.presentation.icons.PawQuestIcons.Coin,"Coins")})
            PawQuestButton("Spin using Ticket",{model.spin(WheelPayment.TICKET)},enabled=state.canSpin && petNeed==null && (b?.tickets ?: 0)>0,modifier=Modifier.fillMaxWidth(),variant=ButtonVariant.Secondary,
                trailingContent={CurrencyPrice(1,ShardIcons.Ticket,"Lucky Wheel Ticket")})
            if(state.busy) Text(tr("Synchronizing..."))
            if(state.syncError) Text(tr("Some balances could not refresh."))
            if(state.error!=null || state.rejection!=null) Text(tr(wheelError(state)))
            if(!state.busy) PawQuestButton(if(state.pending!=null && !state.hasReceipt) "Recover pending operation" else "Refresh",model::retry,variant=ButtonVariant.Ghost)
            if(state.pending!=null && !state.hasReceipt && state.canDismiss) PawQuestButton("Close pending request",model::dismiss,variant=ButtonVariant.Ghost)
        }}}
        item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            PawQuestButton("View Rewards",{panel="rewards"},enabled=state.configuration!=null,modifier=Modifier.weight(1f),variant=ButtonVariant.Secondary)
            PawQuestButton("Drop Rates",{panel="rates"},enabled=state.configuration!=null,modifier=Modifier.weight(1f),variant=ButtonVariant.Secondary)
        }}
        item {PawQuestButton("Spin History",{model.loadHistory();panel="history"},modifier=Modifier.fillMaxWidth(),variant=ButtonVariant.Secondary)}
        item {PawQuestButton("Pet Shard Exchange",onPetExchange,modifier=Modifier.fillMaxWidth(),variant=ButtonVariant.Secondary)}
    }
    if(playing && result!=null && preview!=null) Dialog(onDismissRequest=model::skip,
        properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnClickOutside=false)) {
        Column(Modifier.fillMaxSize().background(PawQuestTheme.colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Text(tr("Lucky Wheel"),Modifier.padding(horizontal=16.dp,vertical=12.dp),style=PawQuestTheme.typography.titleLarge)
            preview(animation,Modifier.fillMaxWidth().weight(1f))
            PawQuestButton("Skip Animation",model::skip,modifier=Modifier.fillMaxWidth().padding(16.dp))
        }
    }
    if(result!=null && state.phase==LuckyWheelPhase.RewardRevealed) AlertDialog(onDismissRequest={if(state.canDismiss) model.dismiss()},
        title={Text(tr("Wheel Reward"))},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            result.selectedPetId?.let {PetRewardPortrait(it)}
            Text(tr(wheelRewardText(result)))
            Text(tr("Pet Shards: ${state.balances?.petShards ?: result.petShardsBalanceAfter}"))
            Text(tr("Coins: ${state.balances?.coins ?: result.coinsBalanceAfter} · Tickets: ${state.balances?.tickets ?: result.ticketsBalanceAfter}"))
        }},confirmButton={PawQuestButton("Open Again",model::dismiss,enabled=state.canDismiss)},
        dismissButton={PawQuestButton("Close",model::dismiss,enabled=state.canDismiss,variant=ButtonVariant.Ghost)})
    panel?.let {kind -> AlertDialog(onDismissRequest={panel=null},title={Text(tr(when(kind) {"rates"->"Drop Rates";"history"->"Spin History";else->"View Rewards"}))},
        text={LazyColumn(Modifier.fillMaxWidth().heightIn(max=400.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(kind=="history") {
                if(state.historyLoading) item {Text(tr("Loading history..."))}
                if(state.historyError) item {Text(tr("History unavailable."));PawQuestButton("Retry",{model.loadHistory()})}
                if(!state.historyLoading && !state.historyError && state.history.isEmpty()) item {Text(tr("No spins yet."))}
                items(state.history,key={it.transactionId}) {r -> Column {
                    Text(tr(wheelRewardText(r)));Text(r.createdAt);Text(tr("Paid: ${r.coinsSpent} Coins · ${r.ticketsSpent} Ticket"))
                }}
                if(!state.historyEnd && !state.historyLoading && !state.historyError) item {PawQuestButton("Load more",{model.loadHistory(true)})}
            } else {
                items(state.configuration?.sectors.orEmpty(),key={it.sector.name}) {sector ->
                    Column {Text(tr(sectorRewardText(sector)));if(kind=="rates") Text("${sector.weight/100.0}%")}
                }
                item {Text(tr("PET_UNLOCK selects an unowned Rabbit, Dog or Capybara. Cat is your default pet."))}
                state.configuration?.let {c->item {Text(tr("If all pets are owned, PET_UNLOCK gives ${c.fallbackPetShards} Pet Shards instead."))}}
                item {Text(tr("Wheel sectors have equal visual sizes. The configured probabilities determine your reward."))}
            }
        }},confirmButton={PawQuestButton("Close",{panel=null})})}
    }
}

@Composable internal fun PetShardExchangeContent(model: LuckyWheelViewModel,modifier: Modifier=Modifier,onPetCollection: (()->Unit)?=null) {
    val state by model.state.collectAsStateWithLifecycle()
    LazyColumn(modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=20.dp)) {
        item {CurrencyAmount(state.balances?.petShards,"Pet Shards",ShardIcons.Pet);Text(tr("Cat is free by default. Choose a companion to unlock; your active pet stays selected."))}
        if(state.busy) item {Text(tr("Synchronizing..."))}
        if(state.syncError || state.error!=null || state.rejection!=null) item {
            Text(tr(if(state.error!=null || state.rejection!=null) wheelError(state) else "Some balances could not refresh."))
            PawQuestButton("Retry",model::retry,enabled=!state.busy)
            if(state.pending!=null && state.canDismiss) PawQuestButton("Close",model::dismiss)
        }
        if(state.pending?.kind==LuckyOperationKind.SPIN) item {Text(tr("A Wheel result is pending. Return to Lucky Wheel to reveal or recover it."))}
        if(state.pets.isEmpty() && !state.busy) item {Text(tr("Pet Exchange data unavailable."));PawQuestButton("Refresh",model::refresh)}
        items(state.pets,key={it.speciesCode}) {pet ->
            PawQuestCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                PetRewardPortrait(pet.speciesCode);Text(tr(petDisplayName(pet.speciesCode)),style=PawQuestTheme.typography.titleMedium)
                CurrencyPrice(pet.price.toLong(),ShardIcons.Pet,"Pet Shards")
                val available=!pet.owned && (state.balances?.petShards ?: -1)>=pet.price
                Text(tr(if(pet.owned) "Owned" else if(available) "Available" else "Locked — not enough Pet Shards"))
                PawQuestButton("Redeem",{model.selectPet(pet.speciesCode)},enabled=available && !state.busy && state.pending==null && !state.hasReceipt,modifier=Modifier.fillMaxWidth())
            }}
        }
        item {PawQuestButton("Refresh",model::refresh,enabled=!state.busy)}
    }
    state.quote?.let {q -> AlertDialog(onDismissRequest={if(state.canDismiss) model.dismiss()},title={Text(tr("Confirm Pet Redemption"))},
        text={Column {PetRewardPortrait(q.speciesCode);Text(tr("Unlock ${petDisplayName(q.speciesCode)} for ${q.price} Pet Shards?"))}},
        confirmButton={PawQuestButton("Confirm Redemption",model::confirmPet,enabled=!state.busy)},dismissButton={PawQuestButton("Cancel",model::dismiss,enabled=state.canDismiss)})}
    state.redemption?.let {r -> AlertDialog(onDismissRequest={if(state.canDismiss) model.dismiss()},title={Text(tr("Pet Unlocked"))},
        text={Column {PetRewardPortrait(r.speciesCode);Text(tr("${petDisplayName(r.speciesCode)} is ready to select."));Text(tr("Pet Shards: ${state.balances?.petShards ?: r.petShardsBalanceAfter}"))}},
        confirmButton={PawQuestButton("Done",model::dismiss,enabled=state.canDismiss)},
        dismissButton={if(onPetCollection!=null) PawQuestButton("Your pets",onPetCollection,enabled=!state.busy)})}
}
