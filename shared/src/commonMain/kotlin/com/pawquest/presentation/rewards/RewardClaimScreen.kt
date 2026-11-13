package com.pawquest.presentation.rewards

import com.pawquest.localization.tr
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import com.pawquest.domain.model.FocusStatus
import com.pawquest.presentation.components.*
import com.pawquest.presentation.focus.FocusState
import com.pawquest.presentation.focus.FocusViewModel
import com.pawquest.presentation.icons.PawQuestIcons
import com.pawquest.presentation.quests.QuestBackHandler
import com.pawquest.presentation.theme.PawQuestTheme

@Composable fun RewardClaimScreen(state: FocusState,model: FocusViewModel,onPetHome:()->Unit,walletBalance: Long=0) {
    val claimed=state.session?.status==FocusStatus.CLAIMED && state.snapshot?.reward!=null
    val offer=state.snapshot?.offer
    val receipt=state.snapshot?.reward
    val colors=PawQuestTheme.colors
    var celebrated by rememberSaveable(state.session?.id) { mutableStateOf(false) }
    var requested by rememberSaveable(state.session?.id) {mutableStateOf(false)}
    var flight by remember(state.session?.id) {mutableStateOf<String?>(null)}
    var root by remember {mutableStateOf(Offset.Zero)}
    var source by remember {mutableStateOf<Offset?>(null)}
    var wallet by remember {mutableStateOf<Offset?>(null)}
    val scale=remember { Animatable(1f) }
    LaunchedEffect(claimed,state.session?.id) {
        if(claimed && !celebrated) {
            celebrated=true
            if(requested && (receipt?.coins ?: 0)>0) flight=state.session?.id
            scale.snapTo(.75f)
            scale.animateTo(1f,spring(dampingRatio=.65f,stiffness=220f))
        }
    }
    QuestBackHandler(true,onPetHome)
    Surface(color=colors.background,modifier=Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().onGloballyPositioned {root=it.positionInRoot()},contentAlignment=Alignment.TopCenter) {
            Column(Modifier.widthIn(max=480.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    CoinBadge(receipt?.balanceAfter ?: walletBalance,Modifier.onGloballyPositioned {wallet=it.boundsInRoot().center})
                }
                Surface(color=if(claimed) colors.successContainer else colors.primaryContainer,
                    shape=PawQuestTheme.shapes.pill,modifier=Modifier.size(96.dp).graphicsLayer {
                        scaleX=scale.value; scaleY=scale.value
                    }) {
                    Box(contentAlignment=Alignment.Center) {
                        Icon(PawQuestIcons.Checkmark,null,Modifier.size(48.dp),tint=colors.textPrimary)
                    }
                }
                Text(tr(if(claimed) "Rewards claimed!" else "Quest complete!"),
                    style=PawQuestTheme.typography.headlineLarge,color=colors.textPrimary,
                    modifier=Modifier.semantics { heading(); liveRegion=LiveRegionMode.Polite })
                Text(tr(state.session?.title.orEmpty()),style=PawQuestTheme.typography.titleMedium,color=colors.textPrimary)
                Text(tr(if(claimed) "A little focus. A happier companion." else "Nice work making time for what matters."),color=colors.textSecondary)
                if(receipt!=null && claimed) {
                    RewardValue("Coins received",receipt.coins.toString(),Modifier.onGloballyPositioned {source=it.boundsInRoot().center})
                    RewardValue("Pet XP received",receipt.petXp.toString())
                    Text(tr("Wallet balance: ${receipt.balanceAfter} Coins"),color=colors.textPrimary)
                } else if(offer!=null) {
                    RewardValue(if(offer.limitReached) "Tier Coins (limit reached)" else "Coins available",
                        (if(offer.limitReached) offer.tierCoins else offer.coins).toString())
                    RewardValue(if(offer.limitReached) "Tier Pet XP (limit reached)" else "Pet XP available",offer.petXp.toString())
                    Text(tr("Reward day: ${offer.rewardDay} UTC · ${offer.rewardedSessions}/5 sessions rewarded"),color=colors.textSecondary)
                    if(offer.limitReached) Text(tr("The five-session reward limit for this completion day has been reached."),color=colors.textPrimary)
                    else if(offer.coins<offer.tierCoins) Text(tr("Coins are adjusted to the remaining daily allowance. Pet XP stays at the full tier."),color=colors.textPrimary)
                    Text(tr("The server checks limits again when you claim. No reward is added until Claim Reward succeeds."),color=colors.textSecondary)
                } else Text(tr("Sync to load your reward details."),color=colors.textSecondary)
                state.error?.let { message ->
                    Surface(color=colors.errorContainer,shape=PawQuestTheme.shapes.card) {
                        Text(tr(message),Modifier.padding(12.dp).semantics { liveRegion=LiveRegionMode.Assertive },color=colors.textPrimary)
                    }
                }
                if(state.busy && !state.claiming) Text(tr("Syncing reward details…"),color=colors.textSecondary)
                if(!claimed) PawQuestButton(if(state.claiming) "Claiming…" else "Claim Reward",{requested=true;model.claim()},
                    Modifier.fillMaxWidth(),enabled=state.canClaimReward && !state.busy,loading=state.claiming,
                    labelColor=colors.textPrimary)
                if(!claimed && (!state.synced || offer==null || state.error!=null)) TextButton(onClick=model::retry,enabled=!state.busy) {
                    Text(tr("Sync reward"),color=colors.textPrimary)
                }
                PawQuestButton("Return to Pet Home",{
                    if(claimed) model.done()
                    onPetHome()
                },Modifier.fillMaxWidth(),variant=ButtonVariant.Secondary,enabled=!state.busy,labelColor=colors.textPrimary)
                Spacer(Modifier.height(12.dp))
            }
            CoinFlight(flight,source?.minus(root),wallet?.minus(root),Modifier.matchParentSize())
        }
    }
}
@Composable private fun RewardValue(label: String,value: String,modifier: Modifier=Modifier) {
    val colors=PawQuestTheme.colors
    Surface(color=colors.surfaceVariant,shape=PawQuestTheme.shapes.card,
        border=BorderStroke(1.dp,colors.borderLight),modifier=modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(tr(label),style=PawQuestTheme.typography.labelLarge,color=colors.textSecondary)
            Text(tr(value),style=PawQuestTheme.typography.headlineLarge,color=colors.textPrimary)
        }
    }
}
