package com.pawquest.presentation.home

import com.pawquest.localization.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pawquest.domain.model.*
import com.pawquest.presentation.components.*
import com.pawquest.presentation.icons.PawQuestIcons
import com.pawquest.presentation.icons.CartoonIcons
import com.pawquest.presentation.pet.LocalPetController
import com.pawquest.presentation.theme.PawQuestTheme

/** Prototype-inspired native portrait UI; the supplied renderer draws only the pet room. */
@Composable
fun PetHomeScreen(
    modifier: Modifier=Modifier,
    petName: String="Mochi",level: Int=2,xp: Int=65,coins: Int=120,
    currentAnimation: PetAnimationState=PetAnimationState.IDLE,
    onActionSelected: (PetAnimationState)->Unit={},
    showDebugControls: Boolean=false,playbackMessage: String?=null,
    sceneHandlesTouch: Boolean=false,
    sceneStateLabel: String?=null,sceneMessage: String?=null,
    onSceneInsetsChanged: (Int?,Int?)->Unit={_,_->},
    sceneContent: @Composable (Modifier)->Unit,
) {
    val c=PawQuestTheme.colors
    val controller=LocalPetController.current
    val state=controller?.state
    val snapshot=state?.snapshot
    val pet=snapshot?.pet
    var studio by remember {mutableStateOf(false)}
    var bistroOpen by remember {mutableStateOf(false)}
    val bistro=com.pawquest.presentation.bistro.LocalBistro.current
    if(controller!=null && pet==null) {
        Box(modifier.fillMaxSize().padding(24.dp),contentAlignment=Alignment.Center) {
            if(state?.error!=null) PawQuestError(title="Your room is taking a little longer",message=state.error,onRetry=controller.retry)
            else PawQuestLoading(message="Waking up your little companion...")
        }
        return
    }
    val busy=state?.busy==true || state?.pendingAction!=null
    val sleeping=pet?.sleepUntil!=null
    val name=pet?.name ?: petName
    val progress=pet?.progress
    val inLevel=progress?.xpInLevel ?: xp%PetCareBalance.xpPerLevel
    val hunger=pet?.stats?.hunger ?: 80
    val energy=pet?.stats?.energy ?: 75
    val food=snapshot?.foodQuantity ?: 3
    val dialogue=com.pawquest.presentation.pet.rememberPetDialogue(currentAnimation,pet?.stats ?: PetStats())
    fun care(action: PetCareAction,animation: PetAnimationState) {
        if(controller!=null) controller.care(action) else onActionSelected(animation)
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val fontScale=LocalDensity.current.fontScale
        val roomHeight=(maxHeight - if(fontScale>1.3f) 118.dp else 88.dp).coerceAtLeast(332.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=8.dp,vertical=6.dp),
            verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min=44.dp).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Pet Room"),style=PawQuestTheme.typography.titleLarge,color=c.textPrimary,
                        modifier=Modifier.semantics {heading()})
                    Text(tr("$name's little world"),style=PawQuestTheme.typography.labelSmall,color=c.textSecondary)
                }
                // The wallet lives on Home and Explore; the room header stays compact so care controls fit.
                if(LocalOpenSettings.current==null) CurrencyBalances(snapshot?.coins ?: coins,Modifier.widthIn(max=230.dp))
                if(showDebugControls) IconButton(onClick={studio=true}) {
                    Icon(PawQuestIcons.Settings,"Animation preview",Modifier.size(20.dp),tint=c.textSecondary)
                }
                LocalOpenSettings.current?.let {AvatarButton(it,Modifier.size(44.dp))}
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(tr("Lv. ${progress?.level ?: level}"),style=PawQuestTheme.typography.labelMedium,color=c.textPrimary)
                com.pawquest.presentation.progress.StreakBadge()
                LinearProgressIndicator(progress={inLevel.toFloat()/PetCareBalance.xpPerLevel},
                    modifier=Modifier.weight(1f).height(6.dp),color=c.petXp,trackColor=c.petXpContainer)
                Text(tr("$inLevel/${PetCareBalance.xpPerLevel} XP"),style=PawQuestTheme.typography.labelSmall,color=c.petXpDark)
            }
            state?.error?.let {error ->
                PawQuestCard(variant=CardVariant.SoftTint) {
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(tr(error),color=c.error,style=PawQuestTheme.typography.bodyMedium)
                        PawQuestButton("Retry",{controller?.retry?.invoke()},enabled=!state.busy)
                    }
                }
            }
            Surface(modifier=Modifier.fillMaxWidth().height(roomHeight),shape=PawQuestTheme.shapes.xxl,
                color=c.surface,border=BorderStroke(2.dp,c.borderLight)) {
                Column {
                    Row(Modifier.fillMaxWidth().background(c.surfaceElevated).padding(horizontal=10.dp,vertical=7.dp),
                        horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        MiniStat("Hunger",hunger,CartoonIcons.Food,c.hunger,Modifier.weight(1f))
                        MiniStat("Water",pet?.stats?.hydration ?: 100,CartoonIcons.Water,c.energy,Modifier.weight(1f))
                        MiniStat("Energy",energy,CartoonIcons.Energy,c.energy,Modifier.weight(1f))
                    }
                    HorizontalDivider(color=c.borderLight,thickness=1.5.dp)
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        Box(Modifier.fillMaxSize().semantics {
                            contentDescription=tr("$name, ${tr(sceneStateLabel ?: currentAnimation.name.lowercase())}. Tap to pet.")
                            onClick(label=tr("Pet $name")) {care(PetCareAction.TOUCH,PetAnimationState.HAPPY);true}
                        }) {sceneContent(Modifier.fillMaxSize())}
                        Surface(modifier=Modifier.align(Alignment.TopCenter).onSizeChanged {onSceneInsetsChanged(it.height,null)}.padding(horizontal=12.dp,vertical=10.dp),
                            shape=PawQuestTheme.shapes.md,color=c.surface.copy(alpha=.95f),border=BorderStroke(1.dp,c.borderLight)) {
                            Text(tr(sceneMessage ?: dialogue),Modifier.padding(horizontal=12.dp,vertical=6.dp),style=PawQuestTheme.typography.labelSmall,color=c.textPrimary,
                                maxLines=3,overflow=TextOverflow.Ellipsis)
                        }
                        Surface(modifier=Modifier.align(Alignment.BottomCenter).onSizeChanged {onSceneInsetsChanged(null,it.height)}.padding(8.dp),shape=PawQuestTheme.shapes.lg,
                            color=c.surfaceElevated.copy(alpha=.97f),border=BorderStroke(1.5.dp,c.borderLight)) {
                            Column(Modifier.padding(horizontal=8.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                    Text(tr("Food: $food"),style=PawQuestTheme.typography.labelSmall,color=c.textSecondary)
                                    Text(tr(if(state?.pendingAction!=null && !state.busy) "Action pending · tap Retry" else if(busy) "Caring..." else if(sleeping) "Tap your pet to wake up" else "Tap your pet to say hello"),
                                        style=PawQuestTheme.typography.labelSmall,color=c.textSecondary)
                                }
                                if(state?.error!=null && !state.busy) PawQuestButton("Retry",{controller?.retry?.invoke()},
                                    modifier=Modifier.fillMaxWidth(),size=ButtonSize.Small)
                                if(bistro!=null) TextButton(onClick={bistroOpen=true},modifier=Modifier.align(Alignment.CenterHorizontally)) {Text(tr("PawQuest Bistro"))}
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                    listOf(PetCareAction.FEED,PetCareAction.DRINK,PetCareAction.PLAY,PetCareAction.SLEEP).forEach {action ->
                                        val label=when(action) {PetCareAction.FEED->"Feed";PetCareAction.DRINK->"Drink";PetCareAction.PLAY->"Play";else->"Sleep"}
                                        val icon=when(action) {PetCareAction.FEED->CartoonIcons.Food;PetCareAction.DRINK->CartoonIcons.Water;PetCareAction.PLAY->CartoonIcons.Ball;else->CartoonIcons.Moon}
                                        val tint=when(action) {PetCareAction.FEED->c.hungerContainer;PetCareAction.DRINK->c.energyContainer;PetCareAction.PLAY->c.primaryContainer;else->c.petXpContainer}
                                        // Full stats and empty stock still accept taps so the server can explain why.
                                        val enabled=!busy
                                        CareTile(label,icon,tint,Modifier.weight(1f),enabled) {
                                            care(action,when(action) {PetCareAction.DRINK->PetAnimationState.DRINKING;PetCareAction.FEED->PetAnimationState.EATING;PetCareAction.PLAY->PetAnimationState.PLAYING;else->PetAnimationState.SLEEPING})
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            state?.lastActionResult?.takeIf {it.applied}?.let {result ->
                Text(tr(result.message),style=PawQuestTheme.typography.labelMedium,color=if(result.applied)c.textSecondary else c.error,
                    modifier=Modifier.padding(horizontal=8.dp).semantics {liveRegion=LiveRegionMode.Polite})
            }
            playbackMessage?.let {Text(tr(it),style=PawQuestTheme.typography.labelMedium,color=c.textSecondary)}
        }
    }
    if(bistroOpen && bistro!=null) androidx.compose.ui.window.Dialog(onDismissRequest={bistroOpen=false},
        properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false,dismissOnClickOutside=false)) {
        Column(Modifier.fillMaxSize().background(c.background).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
            PawQuestButton("Close",{bistroOpen=false},variant=ButtonVariant.Ghost)
            com.pawquest.presentation.bistro.BistroContent(bistro,Modifier.weight(1f))
        }
    }
    if(studio && showDebugControls) AlertDialog(onDismissRequest={studio=false},
        title={Text(tr("Animation preview"))},text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(tr("Visual preview only - no stats or items change"),style=PawQuestTheme.typography.labelMedium)
                PetAnimationState.entries.forEach {item ->
                    PawQuestButton(item.name.lowercase(),{onActionSelected(item);studio=false},variant=ButtonVariant.Secondary,
                        modifier=Modifier.fillMaxWidth(),enabled=!busy)
                }
            }
        },confirmButton={TextButton(onClick={studio=false}) {Text(tr("Close"))}})
}

@Composable private fun MiniStat(label: String,value: Int,icon: ImageVector,tint: Color,modifier: Modifier,displayLabel: String=label) {
    val c=PawQuestTheme.colors
    Column(modifier.semantics(mergeDescendants=true) {contentDescription=tr("${tr(label)} $value percent")},verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            Icon(icon,null,Modifier.size(20.dp),tint=Color.Unspecified)
            Column {
                Text(tr(displayLabel),style=PawQuestTheme.typography.labelSmall,color=c.textPrimary,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(tr("$value%"),style=PawQuestTheme.typography.labelSmall,color=c.textSecondary)
            }
        }
        LinearProgressIndicator(progress={value/100f},modifier=Modifier.fillMaxWidth().height(4.dp),color=tint,trackColor=c.backgroundAlt)
    }
}

@Composable private fun CareTile(label: String,icon: ImageVector,tint: Color,modifier: Modifier,enabled: Boolean,onClick: ()->Unit) {
    val c=PawQuestTheme.colors
    Surface(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=52.dp),shape=PawQuestTheme.shapes.md,
        color=if(enabled)tint else c.backgroundAlt,contentColor=if(enabled)c.textPrimary else c.textMuted,
        border=BorderStroke(1.dp,c.borderLight.copy(alpha=if(enabled)1f else .35f))) {
        Column(Modifier.padding(horizontal=4.dp,vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Icon(icon,null,Modifier.size(28.dp),tint=Color.Unspecified)
            Text(tr(label),style=PawQuestTheme.typography.labelMedium)
        }
    }
}
