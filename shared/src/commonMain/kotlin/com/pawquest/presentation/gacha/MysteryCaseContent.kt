package com.pawquest.presentation.gacha

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import kotlin.math.roundToLong

internal fun casePercent(probability: ItemCaseProbability): String {
    val value=(probability.fraction*1_000_000).roundToLong()
    return "${value/10000}.${(value%10000).toString().padStart(4,'0')}%"
}

@Composable
fun MysteryCaseContent(model: ItemCaseViewModel,coins: Long,itemNames: Map<String,String>,
    preview: (@Composable (Modifier)->Unit)?,modifier: Modifier=Modifier,
    animation: (@Composable (ItemCaseAnimation,Modifier)->Unit)?=null,
    catalog: List<CatalogItem> = emptyList(),
    onViewInventory: ((String)->Unit)?=null,
    onUseReward: ((String)->Unit)?=null,
    actionableItemIds: Set<String> = emptySet(),exchangeModel: ItemExchangeViewModel?=null) {
    val state by model.state.collectAsStateWithLifecycle()
    val colors=PawQuestTheme.colors
    var panel by remember(state.userId) { mutableStateOf<String?>(null) }
    val config=state.case?.configuration
    val enabled=state.case?.banner?.enabled==true
    val exchangeState=exchangeModel?.state?.collectAsStateWithLifecycle()?.value
    val petNeed=currentPetNeed()
    val petController=com.pawquest.presentation.pet.LocalPetController.current
    LaunchedEffect(state.rejection?.reason) {if(state.rejection?.reason==ItemCaseRejection.PET_NEEDS_CARE) petController?.retry?.invoke()}
    val canOpen=petNeed==null && enabled && config!=null && coins>=config.priceCoins && state.phase==ItemCasePhase.Idle && state.pending==null && !state.refreshing && exchangeState?.pending==null && exchangeState?.busy!=true
    val receipt=state.result
    val playing=state.phase in setOf(ItemCasePhase.ReadyToAnimate,ItemCasePhase.Animating)
    val canBrowse=state.phase !in setOf(ItemCasePhase.Loading,ItemCasePhase.ReadyToAnimate,ItemCasePhase.Animating) && !state.retiring
    val canRepeat=petNeed==null && receipt!=null && state.phase==ItemCasePhase.RewardRevealed && enabled && config!=null &&
        (state.coins ?: -1)>=config.priceCoins && !state.refreshing && !state.retiring
    val listState=rememberLazyListState()
    LaunchedEffect(receipt?.transactionId) { if(receipt!=null) listState.scrollToItem(1) }
    // Leaving the screen must never strand a committed purchase in Animating.
    DisposableEffect(receipt?.transactionId) {
        onDispose { receipt?.let { model.revealReward(it.transactionId) } }
    }
    LaunchedEffect(receipt?.transactionId,animation==null) {
        if(animation==null) receipt?.let { model.revealReward(it.transactionId) }
    }
    LazyColumn(modifier.fillMaxSize(),state=listState,verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=20.dp)) {
        item {
            Text(tr("Mystery Box"),style=PawQuestTheme.typography.titleLarge,color=colors.textPrimary)
            Text(tr("Discover a new accessory or furniture piece. Owned unique items become Item Shards."),
                style=PawQuestTheme.typography.bodyMedium,color=colors.textSecondary)
        }
        item {
            Text(tr("Case preview"),style=PawQuestTheme.typography.labelMedium,color=colors.textSecondary)
            if(playing) Spacer(Modifier.fillMaxWidth().height(320.dp))
            else if(preview!=null) preview(Modifier.fillMaxWidth().height(320.dp))
            else Text(tr("Case preview is unavailable on this platform."),color=colors.textSecondary)
        }
        item {
            PawQuestCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    if(config==null) Text(tr("Loading case configuration..."),color=colors.textSecondary)
                    CurrencyAmount(state.itemShards,"Item Shards",ShardIcons.Item,Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally))
                    if(config!=null && coins<config.priceCoins) Text(tr("Not enough Coins to open this case."),color=colors.error)
                    if(config!=null && !enabled) Text(tr("This case is not available yet."),color=colors.textSecondary)
                    if(state.phase==ItemCasePhase.Loading || state.refreshing) Text(tr("Synchronizing case data..."),color=colors.textSecondary)
                    state.error?.let { Text(tr("Case data needs attention. Retry to safely check the same request."),color=colors.error) }
                    state.synchronizationError?.let { Text(tr("Some balances could not refresh."),color=colors.error) }
                    petNeed?.let { PetNeedsCareCard(it) }
                    if(state.rejection?.reason==ItemCaseRejection.PET_NEEDS_CARE && petNeed==null)
                        Text(tr("Your pet is hungry or thirsty. Care for it before opening a Mystery Box."),color=colors.error)
                    PawQuestButton(text="Open Case",onClick={panel="confirm"},enabled=canOpen,modifier=Modifier.fillMaxWidth(),
                        trailingContent={CurrencyPrice(config?.priceCoins?.toLong(),com.pawquest.presentation.icons.PawQuestIcons.Coin,"Coins")})
                    if(state.error!=null || state.synchronizationError!=null) PawQuestButton("Retry",model::retry,variant=ButtonVariant.Secondary)
                    if(state.rejection?.reason?.canDismiss==true) PawQuestButton("Close",model::dismissRejection,variant=ButtonVariant.Secondary)
                }
            }
        }
        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            PawQuestButton("View Rewards",{panel="rewards"},variant=ButtonVariant.Secondary,enabled=config!=null && canBrowse,modifier=Modifier.weight(1f))
            PawQuestButton("Drop Rates",{panel="rates"},variant=ButtonVariant.Secondary,enabled=config!=null && canBrowse,modifier=Modifier.weight(1f))
        } }
        item { PawQuestButton("Opening History",{model.loadHistory();panel="history"},variant=ButtonVariant.Secondary,enabled=canBrowse,modifier=Modifier.fillMaxWidth()) }
        item { PawQuestButton("Shard Wallet",{model.loadShardWallet();panel="wallet"},variant=ButtonVariant.Secondary,enabled=canBrowse,modifier=Modifier.fillMaxWidth()) }
        item {PawQuestButton("Shard Exchange",{exchangeModel?.refresh();panel="exchange"},enabled=canBrowse && config!=null && exchangeModel!=null,modifier=Modifier.fillMaxWidth())}
    }
    if(receipt!=null && animation!=null && state.phase in setOf(ItemCasePhase.ReadyToAnimate,ItemCasePhase.Animating)) {
        // A separate full-window surface covers Shop and bottom navigation without
        // changing navigation or issuing another economic operation. Back skips.
        Dialog(onDismissRequest={model.revealReward(receipt.transactionId)},
            properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnClickOutside=false)) {
            Column(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
                Text(tr("Mystery Box"),Modifier.padding(horizontal=16.dp,vertical=12.dp),
                    style=PawQuestTheme.typography.titleLarge,color=colors.textPrimary)
                animation(ItemCaseAnimation(receipt,config?.rewards.orEmpty(),false,
                    model::startAnimation,model::revealReward,itemNames),Modifier.fillMaxWidth().weight(1f))
                PawQuestButton("Skip Animation",{model.revealReward(receipt.transactionId)},
                    modifier=Modifier.fillMaxWidth().padding(16.dp))
            }
        }
    }
    if(receipt!=null && state.phase==ItemCasePhase.RewardRevealed && panel==null) CaseRewardReveal(
        receipt,caseReveal(receipt,config,catalog),state.itemShards,
        busy=state.refreshing || state.retiring,canRepeat=canRepeat,notice=state.synchronizationError,
        onClose=model::acknowledgeReward,onAgain={panel="again"},
        onInventory=onViewInventory?.let { callback -> { callback(receipt.winningItemId) } },
        onUse=onUseReward?.takeIf {receipt.winningItemId in actionableItemIds}?.let { callback -> { callback(receipt.winningItemId) } },
        onWallet={model.loadShardWallet();panel="wallet"})
    if(panel=="again" && config!=null) AlertDialog(onDismissRequest={panel=null},
        title={Text(tr("Open Mystery Box?"))},text={Text(tr("Spend ${config.priceCoins} Coins to open one case?"))},
        confirmButton={PawQuestButton("Open Again",{panel=null;model.openAgain()},enabled=canRepeat)},
        dismissButton={PawQuestButton("Cancel",{panel=null},variant=ButtonVariant.Secondary)})
    if(panel=="confirm" && config!=null) AlertDialog(onDismissRequest={panel=null},
        title={Text(tr("Open Mystery Box?"))},text={Text(tr("Spend ${config.priceCoins} Coins to open one case?"))},
        confirmButton={PawQuestButton("Open Case",{panel=null;model.openCase()},enabled=canOpen)},
        dismissButton={PawQuestButton("Cancel",{panel=null},variant=ButtonVariant.Secondary)})
    if(panel in setOf("rewards","rates","history","wallet")) CaseDetailsPanel(
        panel=panel!!,state=state,catalog=catalog,onClose={panel=null},onRetryHistory=model::loadHistory,onRefreshWallet=model::loadShardWallet,
        onExchange=exchangeModel?.takeIf {config!=null}?.let {exchange -> {exchange.refresh();panel="exchange"}})
    if(panel=="exchange" && exchangeModel!=null && config!=null) ShardExchangePanel(exchangeModel,config,catalog,
        onClose={model.loadShardWallet();panel="wallet"},onInventory=onViewInventory,onUse=onUseReward,actionableIds=actionableItemIds)
}
