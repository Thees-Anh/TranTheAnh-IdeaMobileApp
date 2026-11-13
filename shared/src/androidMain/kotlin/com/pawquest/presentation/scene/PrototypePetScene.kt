package com.pawquest.presentation.scene

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pawquest.domain.model.*
import com.pawquest.presentation.home.PetHomeScreen
import com.pawquest.presentation.inventory.LocalEquipment
import com.pawquest.presentation.inventory.LocalRoom
import com.pawquest.presentation.pet.LocalPetController
import com.pawquest.presentation.components.PawQuestLoading
import com.pawquest.presentation.components.PawQuestError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

@Composable
fun PrototypePetScene(modifier: Modifier=Modifier,showDebugControls: Boolean=false) {
    val context=LocalContext.current
    val controller=LocalPetController.current
    if(PetArtworkRegistry.find(controller?.state?.snapshot?.pet?.rendererKey ?: "")?.storyEmotions==true) {
        PuppyPetScene(modifier,controller)
        return
    }
    val command=petAnimationCommand(controller?.state)
    val equipment=LocalEquipment.current
    val room=LocalRoom.current
    val artwork=PetArtworkRegistry.find(controller?.state?.snapshot?.pet?.rendererKey ?: "prototype_cat")
    val assetPath=artwork?.assetPath
    var html by remember(assetPath) { mutableStateOf<String?>(null) }
    var failed by remember(assetPath) { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<PetAnimationState?>(null) }
    var view by remember { mutableStateOf<PrototypePetView?>(null) }
    var topInset by remember {mutableIntStateOf(0)}
    var bottomInset by remember {mutableIntStateOf(0)}
    LaunchedEffect(attempt,assetPath) {
        failed=false
        try { html=withContext(Dispatchers.IO) {context.assets.open(assetPath ?: error("Artwork unavailable")).bufferedReader().use {it.readText()}} }
        catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch(_: Exception) { failed=true }
    }
    LaunchedEffect(preview) { if(preview!=null) {delay(2500);preview=null} }
    LaunchedEffect(command) {if(command.state!=PetAnimationState.IDLE) preview=null}
    LifecycleResumeEffect(view) {
        val native=view
        native?.resumeIllustration()
        onPauseOrDispose {native?.pauseIllustration()}
    }
    val unavailableArt=equipment.any {it.assetId !in DecorCatalog.supportedAssets} ||
        room.any {it.assetId !in DecorCatalog.supportedAssets}
    val state=if(command.state==PetAnimationState.IDLE) preview ?: command.state else command.state
    PetHomeScreen(modifier=modifier,currentAnimation=state,sceneHandlesTouch=true,
        onSceneInsetsChanged={top,bottom->top?.let {topInset=it};bottom?.let {bottomInset=it}},
        playbackMessage=if(unavailableArt) "Some owned decorations have no illustration yet. Your saved choices are safe." else null,
        showDebugControls=showDebugControls,onActionSelected={if(controller?.state?.busy!=true) preview=it}) { stageModifier ->
        val source=html
        if(source!=null) key(attempt,assetPath) {
            AndroidView(factory={ctx->PrototypePetView(ctx,source).also {view=it}},modifier=stageModifier,
                update={native ->
                    native.onPetTouch={controller?.care?.invoke(PetCareAction.TOUCH)}
                    native.setSafeInsets(topInset,bottomInset)
                    native.update(state.name,
                        equipment.filter {artwork?.supportsEquipment==true && it.assetId in DecorCatalog.supportedAssets}.map {it.assetId},
                        room.filter {it.assetId in DecorCatalog.supportedAssets}.map {it.assetId})
                },onRelease={native -> if(view===native) view=null;native.release()})
        } else if(failed) PawQuestError(message="Your pet illustration couldn't load.",onRetry={attempt++})
        else PawQuestLoading(message="Preparing your little room…")
    }
}
