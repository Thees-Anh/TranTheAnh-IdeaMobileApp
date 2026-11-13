package com.pawquest.presentation.focus

import com.pawquest.localization.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pawquest.domain.model.FocusStatus
import com.pawquest.presentation.components.PawQuestButton
import com.pawquest.presentation.components.ButtonVariant
import com.pawquest.presentation.quests.QuestBackHandler
import com.pawquest.presentation.theme.PawQuestTheme
import com.pawquest.presentation.rewards.RewardClaimScreen

@Composable fun FocusScreen(state: FocusState, model: FocusViewModel, onBack: () -> Unit = model::hide,
    onPetHome: () -> Unit = model::hide,companion: (@Composable (Modifier)->Unit)?=null,walletBalance: Long=0) {
    if(state.session?.status in listOf(FocusStatus.COMPLETED,FocusStatus.CLAIMED) || state.canClaim) {
        RewardClaimScreen(state,model,onPetHome,walletBalance)
        return
    }
    val colors=PawQuestTheme.colors
    QuestBackHandler(true,onBack)
    Surface(color=colors.background,modifier=Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Text(tr("Focus Mode"),style=PawQuestTheme.typography.labelLarge,color=colors.primaryDark)
            Text(state.session?.title ?: tr("Restoring your focus"),style=PawQuestTheme.typography.headlineMedium,
                color=colors.textPrimary,modifier=Modifier.semantics { heading() })
            if(state.session == null) {
                if(state.busy) { CircularProgressIndicator(); Text(tr("Checking your session…")) }
            } else {
                val terminal=state.session?.status in listOf(FocusStatus.CANCELLED,FocusStatus.CLAIMED)
                Surface(shape=PawQuestTheme.shapes.xxl,color=colors.primaryContainer,modifier=Modifier.widthIn(max=380.dp).fillMaxWidth().aspectRatio(400f/330f)) {
                    companion?.invoke(Modifier.fillMaxSize()) ?: Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {Text(tr("Mochi is studying with you."))}
                }
                Text(tr("A little focus, together with Mochi."),style=PawQuestTheme.typography.labelMedium,color=colors.textSecondary)
                Box(Modifier.widthIn(max=380.dp).fillMaxWidth(),contentAlignment=Alignment.Center) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(tr(if(terminal) { if(state.session?.status==FocusStatus.CLAIMED) "Finished" else "Stopped" } else state.minutesLabel),
                            style=PawQuestTheme.typography.headlineLarge.copy(fontSize=if(terminal) 32.sp else 64.sp),
                            color=colors.textPrimary,modifier=Modifier.semantics {
                                contentDescription=tr(if(terminal) "Focus session finished" else "${state.minutesLabel} minutes remaining")
                            })
                        Text(tr(when {
                            state.session?.status==FocusStatus.CLAIMED -> "Reward claimed"
                            state.session?.status==FocusStatus.CANCELLED -> "Quest cancelled"
                            state.canClaim -> "Ready to claim"
                            state.remainingSeconds==0L -> "Verifying completion"
                            else -> "minutes remaining"
                        }),color=colors.textPrimary)
                    }
                }
                LinearProgressIndicator(progress={if(terminal) 1f else 1f-state.progress},modifier=Modifier.widthIn(max=380.dp).fillMaxWidth().clearAndSetSemantics {},
                    color=colors.primaryDark,trackColor=colors.primaryContainer)
                state.preview?.let { Text(tr("${it.coins} Coins · ${it.xp} Pet XP preview"),style=PawQuestTheme.typography.titleMedium) }
                if(!terminal) Text(tr("Rewards are granted only after server verification and Claim Reward."),color=colors.textSecondary)
                state.snapshot?.reward?.let { reward ->
                    Text(tr("Claimed: ${reward.coins} Coins + ${reward.petXp} Pet XP\nWallet: ${reward.balanceAfter} Coins"),
                        modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite })
                }
                if(state.session?.status==FocusStatus.CANCELLED) Text(tr("No rewards were granted."))
            }
            state.error?.let {
                Surface(color=colors.errorContainer,shape=PawQuestTheme.shapes.card) {
                    Text(tr(it),Modifier.padding(12.dp).semantics { liveRegion=LiveRegionMode.Assertive },color=colors.textPrimary)
                }
            }
            if(!state.synced && state.session != null) Text(tr("Waiting for server sync. Countdown is an estimate."))
            if(state.canClaim) FocusAction("Claim Reward",model::claim,state.busy)
            if(state.session?.status==FocusStatus.ACTIVE && state.remainingSeconds>0)
                FocusAction("Cancel Quest",model::requestCancel,state.busy,secondary=true)
            if(state.session?.status in listOf(FocusStatus.CLAIMED,FocusStatus.CANCELLED))
                FocusAction("Done",{ model.done(); onBack() },state.busy)
            if(state.error!=null || state.remainingSeconds==0L && !state.canClaim && state.session?.status==FocusStatus.ACTIVE)
                FocusAction("Sync session",model::retry,state.busy,secondary=true)
            FocusAction("Back to quests",onBack,state.busy,secondary=true)
            Spacer(Modifier.height(12.dp))
        }
    }
    if(state.confirmCancel) AlertDialog(onDismissRequest=model::dismissCancel,
        title={ Text(tr("Cancel this focus session?")) },text={ Text(tr("No Coins or XP will be awarded.")) },
        confirmButton={ TextButton(onClick=model::cancel) { Text(tr("Cancel Quest")) } },
        dismissButton={ TextButton(onClick=model::dismissCancel) { Text(tr("Keep focusing")) } })
}
@Composable private fun FocusAction(text: String,onClick:()->Unit,busy: Boolean,secondary: Boolean=false) {
    PawQuestButton(text,onClick,modifier=Modifier.widthIn(max=480.dp).fillMaxWidth(),enabled=!busy,
        variant=if(secondary) ButtonVariant.Secondary else ButtonVariant.Primary,labelColor=PawQuestTheme.colors.textPrimary)
}
