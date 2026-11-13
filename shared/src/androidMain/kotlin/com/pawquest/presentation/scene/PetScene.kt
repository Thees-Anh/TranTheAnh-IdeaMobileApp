package com.pawquest.presentation.scene

import com.pawquest.localization.tr
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pawquest.domain.model.PetAnimationState
import com.pawquest.presentation.components.PawQuestError
import com.pawquest.presentation.components.PawQuestLoading
import com.pawquest.presentation.home.PetHomeScreen
import com.pawquest.presentation.pet.LocalPetController
import com.pawquest.domain.model.PetCareAction
import com.pawquest.domain.model.PetCareBalance
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.ByteOrder

private sealed interface AssetState {
    data object Loading : AssetState
    data object Missing : AssetState
    data object Failed : AssetState
    data class Ready(val buffer: ByteBuffer, val bounds: AnimationBounds?,val hash: String) : AssetState
}

/**
 * Android 3D PetScene integrating PetSceneView and Filament animation playback
 * within the Compose Multiplatform PetHomeScreen layout.
 */
@Composable
fun PetScene(
    modifier: Modifier = Modifier,
    assetPath: String = "models/cat.glb",
    showDebugControls: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var attempt by remember { mutableStateOf(0) }
    var state by remember(assetPath, attempt) { mutableStateOf<AssetState>(AssetState.Loading) }
    var rendered by remember(assetPath, attempt) { mutableStateOf(false) }
    val controller = LocalPetController.current
    val confirmed = petAnimationCommand(controller?.state)
    var debugCommandState by rememberSaveable(assetPath) { mutableStateOf<String?>(null) }
    val debugCommand = debugCommandState?.let {
        PetAnimationCommand(PetAnimationState.valueOf(it), "debug:$it")
    }
    val command = if (confirmed.state != PetAnimationState.IDLE) confirmed else debugCommand ?: confirmed
    LaunchedEffect(confirmed) {
        if (confirmed.state != PetAnimationState.IDLE) debugCommandState = null
    }
    var playback by remember(assetPath, attempt) { mutableStateOf(AnimationPlayback()) }
    val equipment=com.pawquest.presentation.inventory.LocalEquipment.current
    var equipmentAssets by remember { mutableStateOf<AccessoryAssets?>(null) }
    var equipmentWarning by remember { mutableStateOf<String?>(null) }
    val room=com.pawquest.presentation.inventory.LocalRoom.current
    val roomLoader=remember(context) { RoomAssetLoader(context.applicationContext) }
    var roomAssets by remember { mutableStateOf<RoomAssets?>(null) }
    var roomWarning by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(room,attempt) {
        roomAssets=withContext(Dispatchers.IO) { roomLoader.load(room) }
    }
    LaunchedEffect(equipment,state,attempt) {
        val ready=state as? AssetState.Ready
        equipmentAssets=if(ready==null) null else withContext(Dispatchers.IO) {
            AccessoryAssets.load(context,equipment,ready.hash)
        }
    }

    LaunchedEffect(assetPath, attempt) {
        state = try {
            val ready = withContext(Dispatchers.IO) {
                val bytes = context.assets.open(assetPath).use { it.readBytes() }
                require(bytes.size >= 20) { "Truncated GLB" }
                val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.LITTLE_ENDIAN).apply {
                    put(bytes)
                    flip()
                    require(getInt(0) == 0x46546C67 && getInt(4) == 2 && getInt(8) == bytes.size) {
                        "Expected binary glTF 2.0"
                    }
                }
                val bounds = try {
                    context.assets.open(assetPath.removeSuffix(".glb") + ".animation-bounds.json").bufferedReader().use {
                        AnimationBounds.parse(it.readText())
                    }
                } catch (_: FileNotFoundException) { null }
                val hash=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                AssetState.Ready(buffer, bounds,hash)
            }
            ready
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: FileNotFoundException) {
            AssetState.Missing
        } catch (error: Exception) {
            Log.e("PawQuestScene", "Cannot read model asset", error)
            AssetState.Failed
        }
    }

    PetHomeScreen(
        modifier = modifier,
        currentAnimation = command.state,
        onActionSelected = {
            if (confirmed.state == PetAnimationState.IDLE && controller?.state?.busy != true && controller?.state?.pendingAction == null) {
                debugCommandState = it.name
            }
        },
        sceneHandlesTouch = true,
        showDebugControls = showDebugControls,
        playbackMessage = playback.message,
    ) { sceneModifier ->
        Box(
            modifier = sceneModifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val current = state
            if (current is AssetState.Ready) {
                key(assetPath, attempt) {
                    AndroidView<View>(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            try {
                                PetSceneView(viewContext, lifecycle).also { scene ->
                                    // File IO is off-main; Filament calls stay on the UI thread.
                                    scene.post {
                                        if (scene.isAttachedToWindow) {
                                            try {
                                                scene.load(current.buffer, current.bounds)
                                                rendered = true
                                            } catch (error: Exception) {
                                                Log.e("PawQuestScene", "Cannot render model", error)
                                                state = AssetState.Failed
                                            }
                                        }
                                    }
                                }
                            } catch (error: Exception) {
                                Log.e("PawQuestScene", "Cannot initialize renderer", error)
                                FrameLayout(viewContext).also { it.post { state = AssetState.Failed } }
                            } catch (error: LinkageError) {
                                Log.e("PawQuestScene", "Native renderer unavailable", error)
                                FrameLayout(viewContext).also { it.post { state = AssetState.Failed } }
                            }
                        },
                        update = { view ->
                            (view as? PetSceneView)?.let {
                                if (it.lifecycle !== lifecycle) it.lifecycle = lifecycle
                                it.onPlayback = { update -> playback = update }
                                it.onPetTouch = { controller?.care?.invoke(PetCareAction.TOUCH) }
                                it.onEquipmentStatus = { warning -> equipmentWarning=warning }
                                it.setEquipment(equipmentAssets)
                                it.onRoomStatus = { warning -> roomWarning=warning }
                                it.setRoom(roomAssets)
                                it.setAnimationState(command.state,command.token)
                            }
                        },
                        onRelease = { (it as? PetSceneView)?.destroy() },
                    )
                }
            }

            listOfNotNull(equipmentWarning,roomWarning).joinToString(" ").takeIf { it.isNotBlank() }?.let { warning ->
                Text(tr(warning),modifier=Modifier.align(Alignment.BottomCenter).padding(8.dp))
            }
            if (current !is AssetState.Ready || !rendered) {
                when (current) {
                    AssetState.Loading, is AssetState.Ready -> {
                        PawQuestLoading(message = "Waking up your cat…")
                    }
                    AssetState.Missing -> {
                        PawQuestError(
                            title = "Cat Model Not Found",
                            message = "Add cat.glb to assets to display your 3D pet.",
                            onRetry = { attempt++ },
                        )
                    }
                    AssetState.Failed -> {
                        PawQuestError(
                            title = "Unable to Display Cat",
                            message = "3D rendering unavailable or model is corrupted.",
                            onRetry = { attempt++ },
                        )
                    }
                }
            }
        }
    }
}
