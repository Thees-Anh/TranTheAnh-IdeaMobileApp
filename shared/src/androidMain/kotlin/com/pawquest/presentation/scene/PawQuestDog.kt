package com.pawquest.presentation.scene

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pawquest.domain.model.*
import com.pawquest.presentation.components.*
import com.pawquest.presentation.home.PetHomeScreen
import com.pawquest.presentation.inventory.LocalRoom
import com.pawquest.presentation.inventory.LocalEquipment
import com.pawquest.presentation.pet.PetController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Compose counterpart of <PawQuestDog state={...}>; uses exactly the supplied six SVG states. */
@Composable fun PawQuestDog(state: PuppyAnimationState,modifier: Modifier=Modifier,
    room: List<String> = emptyList(),topInsetPx: Int=0,bottomInsetPx: Int=0,
    equipment: List<String> = emptyList(),assetPath: String="pet/puppy_stage.html",
    onTouch: ()->Unit={},onVisible: (Boolean)->Unit={}) {
    val context=LocalContext.current
    var attempt by remember {mutableIntStateOf(0)}
    var html by remember(assetPath) {mutableStateOf<String?>(null)}
    var failed by remember(assetPath) {mutableStateOf(false)}
    var view by remember(assetPath) {mutableStateOf<PrototypePetView?>(null)}
    var ready by remember(assetPath) {mutableStateOf(false)}
    val visibility by rememberUpdatedState(onVisible)
    LaunchedEffect(attempt,assetPath) {
        failed=false
        try {html=withContext(Dispatchers.IO) {context.assets.open(assetPath).bufferedReader().use {it.readText()}}}
        catch(cancelled: kotlinx.coroutines.CancellationException) {throw cancelled}
        catch(_: Exception) {failed=true}
    }
    LifecycleResumeEffect(ready) {if(ready) visibility(true);onPauseOrDispose {visibility(false)}}
    LifecycleResumeEffect(view) {
        val native=view;native?.resumeIllustration()
        onPauseOrDispose {native?.pauseIllustration()}
    }
    html?.let {source ->
        key(attempt,assetPath) {
            AndroidView(factory={ctx->PrototypePetView(ctx,source).also {view=it}},modifier=modifier,
                update={native->native.onReady={ready=true};native.onPetTouch=onTouch;native.setSafeInsets(topInsetPx,bottomInsetPx);native.update(state.wire,equipment,room)},
                onRelease={native->if(view===native){view=null;ready=false};native.release()})
        }
    } ?: if(failed) PawQuestError(message="Your pet's illustration couldn't load.",onRetry={attempt++})
    else PawQuestLoading(message="Getting your companion ready...")
}

@Composable internal fun PuppyPetScene(modifier: Modifier=Modifier,controller: PetController?) {
    val state=controller?.state
    val snapshot=state?.snapshot
    val result=state?.lastActionResult
    val reaction=state?.reaction?.takeIf {result?.applied==true && result.action==it}
    val visual=PuppyEmotionRules.state(snapshot?.pet?.sleepUntil!=null,reaction,state?.celebrating==true,
        snapshot?.pet?.stats?.hunger ?: 100,state?.companionContext?.hasMissedQuest==true)
    val animation=when(visual) {
        PuppyAnimationState.EATING->PetAnimationState.EATING;PuppyAnimationState.DRINKING->PetAnimationState.DRINKING
        PuppyAnimationState.SLEEPING->PetAnimationState.SLEEPING;PuppyAnimationState.LAUGHING->PetAnimationState.HAPPY
        else->PetAnimationState.IDLE
    }
    val room=LocalRoom.current
    val equipment=LocalEquipment.current
    var topInset by remember {mutableIntStateOf(0)}
    var bottomInset by remember {mutableIntStateOf(0)}
    PetHomeScreen(modifier=modifier,currentAnimation=animation,sceneHandlesTouch=true,sceneStateLabel=visual.wire,
        onSceneInsetsChanged={top,bottom->top?.let {topInset=it};bottom?.let {bottomInset=it}},
        ) {stageModifier ->
        PawQuestDog(visual,stageModifier,room.filter {it.assetId in DecorCatalog.supportedAssets}.map {it.assetId},
            topInsetPx=topInset,bottomInsetPx=bottomInset,
            equipment=equipment.filter {it.assetId in DecorCatalog.supportedAssets}.map {it.assetId},
            assetPath=PetArtworkRegistry.find(snapshot?.pet?.rendererKey ?: "puppy")!!.assetPath,
            onTouch={controller?.care?.invoke(PetCareAction.TOUCH)},onVisible={controller?.companionVisible?.invoke(it)})
    }
}
