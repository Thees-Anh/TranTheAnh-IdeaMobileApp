package com.pawquest.presentation.scene

import com.pawquest.localization.tr
import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.pawquest.domain.model.PetAnimationState
import com.google.android.filament.Camera
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.model.Model
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import java.nio.ByteBuffer
import kotlin.math.max

/** Owns all native resources; also handles detach/lifecycle destruction before onRelease. */
internal class PetSceneView(context: Context, lifecycle: Lifecycle) : SceneView(
    context = context,
    sharedLifecycle = lifecycle,
    cameraManipulator = null,
) {
    private var cat: ModelNode? = null
    private var asset: Model? = null
    private var animations: PetAnimationController? = null
    private var requestedAnimation = PetAnimationState.IDLE
    private var requestedToken: String? = null
    var onPetTouch: () -> Unit = {}
    private var frameHalf = floatArrayOf(1f, 1f, 1f)
    private var baseHalf=frameHalf
    private var framingCenter=FloatArray(3)
    private var catScale=1f
    private var equipmentAssets: AccessoryAssets?=null
    private val accessories=mutableListOf<Pair<ModelNode,Model>>()
    var onEquipmentStatus: (String?) -> Unit = {}
    private data class FurnitureNode(val id: String,val node: ModelNode,val model: Model,val half: FloatArray)
    private val furniture=mutableMapOf<com.pawquest.domain.model.FurnitureSlot,FurnitureNode>()
    private var roomAssets: RoomAssets?=null
    var onRoomStatus: (String?) -> Unit = {}
    var onPlayback: (AnimationPlayback) -> Unit = {}
    private val animationLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onPause(owner: LifecycleOwner) { animations?.pause() }
    }

    init {
        // SceneView supplies a directional key light and bundled neutral image-based lighting.
        // Gestures cannot move the camera outside the authored animation envelope.
        contentDescription = tr("3D cat preview")
        // Basic PBR rendering also works on software GPUs where postprocessing can
        // produce a blank frame. No postprocessing effects are needed for this scene.
        view.isPostProcessingEnabled = false
        skybox?.setColor(0.88f, 0.94f, 1.0f, 1.0f)
        lifecycle.addObserver(animationLifecycleObserver)
        onFrame = { animations?.onFrame(it) }
        // SceneView collision picking excludes the surrounding viewport. Camera gestures stay disabled.
        setOnGestureListener(onSingleTapConfirmed = { _, hitNode ->
            val root=cat
            if(root!=null && isPetHit(hitNode,root)) onPetTouch()
        })
    }

    fun load(buffer: ByteBuffer, animationBounds: AnimationBounds? = null) {
        check(!isDestroyed)
        check(cat==null) { "Model already loaded" }
        val instance = modelLoader.createModelInstance(buffer.duplicate())
        asset = instance.asset
        val bounds = instance.asset.boundingBox
        val half = bounds.halfExtent
        val center = bounds.center
        require(half.all { it.isFinite() && it >= 0f } && center.all { it.isFinite() }) {
            "Invalid GLB bounds"
        }
        val largest = max(half[0], max(half[1], half[2]))
        require(largest > 0f) { "Empty GLB geometry" }
        val uniformScale = 1f / largest
        val envelope=animationBounds?.including(center,half)
        framingCenter = envelope?.center ?: center
        catScale=uniformScale
        // Unspecified envelopes get conservative extra space, but authoring an
        // all-pose envelope is required to guarantee arbitrary replacement clips.
        frameHalf = FloatArray(3) { (envelope?.half?.get(it) ?: (half[it] * 2f)) * uniformScale }
        baseHalf=frameHalf.copyOf()
        val node = ModelNode(instance, autoAnimate = false).apply {
            scale = Scale(uniformScale)
            position = Position(-framingCenter[0] * uniformScale, -framingCenter[1] * uniformScale, -framingCenter[2] * uniformScale)
            isEditable = false
        }
        cat = node
        // Animated/skinned pose bounds can exceed accessor AABBs. Camera fitting
        // uses the authored all-pose envelope; do not cull the pet with stale AABBs.
        node.setCulling(false)
        addChildNode(node)
        node.playingAnimations.clear()
        animations = PetAnimationController(FilamentAnimationPlayer(engine, node)) { playback ->
            Log.d("PawQuestAnimation", "${playback.requested}: ${playback.message}")
            onPlayback(playback)
        }.also { it.switchTo(requestedAnimation,requestedToken) }
        // This asset won't be instantiated again; GPU resources remain owned by modelLoader.
        instance.asset.releaseSourceData()
        applyEquipment()
        applyRoom()
        frameCat()
        Log.d("PawQuestScene", "Model loaded")
    }

    fun setAnimationState(state: PetAnimationState, token: String? = null) {
        requestedAnimation = state
        requestedToken=token
        animations?.switchTo(state,token)
    }

    fun setEquipment(value: AccessoryAssets?) {
        if(isDestroyed || equipmentAssets===value) return
        equipmentAssets=value
        if(cat!=null) applyEquipment()
    }
    fun setRoom(value: RoomAssets?) {
        if(isDestroyed || roomAssets===value) return
        roomAssets=value
        if(cat!=null) applyRoom()
    }
    private fun removeFurniture(slot: com.pawquest.domain.model.FurnitureSlot) {
        furniture.remove(slot)?.let {
            removeChildNode(it.node)
            modelLoader.destroyModel(it.model)
        }
    }
    private fun applyRoom() {
        val requested=roomAssets?.models.orEmpty().associateBy { it.slot }
        furniture.keys.toList().forEach { slot ->
            if(requested[slot]?.assetId!=furniture[slot]?.id) removeFurniture(slot)
        }
        val warnings=roomAssets?.warnings?.toMutableList() ?: mutableListOf()
        requested.forEach { (slot,loaded) ->
            if(furniture[slot]?.id==loaded.assetId) return@forEach
            var model: Model?=null
            var node: ModelNode?=null
            try {
                val instance=modelLoader.createModelInstance(loaded.buffer.duplicate())
                model=instance.asset
                val box=instance.asset.boundingBox
                val center=box.center; val half=box.halfExtent; val anchor=roomAnchor(slot)
                require((0..2).all { center[it].isFinite() && half[it].isFinite() && half[it]>=0 && half[it]<=anchor.maxHalf[it] })
                // Authored props stand on their local floor; reject oversized/offset replacement art.
                require(kotlin.math.abs(center[0])<.02f && kotlin.math.abs(center[2])<.02f &&
                    kotlin.math.abs(center[1]-half[1])<.02f && anchor.z+center[2]+half[2]<=-.12f)
                node=ModelNode(instance,autoAnimate=false).apply {
                    position=Position(anchor.x,anchor.y,anchor.z)
                    isEditable=false
                }
                addChildNode(node)
                instance.asset.releaseSourceData()
                val p=floatArrayOf(anchor.x,anchor.y,anchor.z)
                val extent=FloatArray(3) { kotlin.math.abs(p[it]+center[it])+half[it] }
                furniture[slot]=FurnitureNode(loaded.assetId,node,instance.asset,extent)
                Log.d("PawQuestRoom","$slot loaded")
            } catch(_: Exception) {
                node?.let(::removeChildNode)
                model?.let { modelLoader.destroyModel(it) }
                warnings+="${slot.name.lowercase()} preview unavailable."
            }
        }
        onRoomStatus(warnings.distinct().joinToString(" ").ifBlank { null })
        frameCat()
    }
    private fun clearAccessories() {
        accessories.forEach { (node,model) ->
            node.parent=null // remove from scene before releasing native entities
            modelLoader.destroyModel(model)
        }
        accessories.clear()
    }
    private fun applyEquipment() {
        clearAccessories()
        frameHalf=baseHalf.copyOf()
        val root=cat ?: return
        val value=equipmentAssets
        val warnings=value?.warnings?.toMutableList() ?: mutableListOf()
        value?.models?.forEach { loaded ->
            var created: Model?=null
            var accessory: ModelNode?=null
            try {
                val parent=root.nodes.firstOrNull { (it as? ModelNode.ChildNode)?.name==loaded.parent }
                    ?: error("Attachment transform unavailable")
                val instance=modelLoader.createModelInstance(loaded.buffer.duplicate())
                created=instance.asset
                accessory=ModelNode(instance,autoAnimate=false).apply {
                    position=Position(loaded.position[0],loaded.position[1],loaded.position[2])
                    isEditable=false
                    setCulling(false)
                    this.parent=parent
                }
                instance.asset.releaseSourceData()
                accessories+=accessory to instance.asset
                Log.d("PawQuestEquipment","${loaded.slot}: attached to ${loaded.parent}")
                for(i in 0..2) frameHalf[i]=maxOf(frameHalf[i],
                    (kotlin.math.abs(loaded.bounds.center[i]-framingCenter[i])+loaded.bounds.half[i])*catScale)
            } catch(_: Exception) {
                accessory?.parent=null
                created?.let { modelLoader.destroyModel(it) }
                warnings+="${loaded.slot.name.lowercase()} accessory preview unavailable."
            }
        }
        onEquipmentStatus(warnings.distinct().joinToString(" ").ifBlank { null })
        frameCat()
    }

    private fun isPetHit(node: Node?,root: Node): Boolean {
        var candidate=node
        while(candidate!=null) {
            if(candidate===root) return true
            candidate=candidate.parent
        }
        return false
    }

    override fun onResized(width: Int, height: Int) {
        super.onResized(width, height)
        frameCat()
    }

    private fun frameCat() {
        if (cat == null) return
        val viewport = view.viewport
        if (viewport.width <= 0 || viewport.height <= 0) return
        val half = frameHalf.copyOf()
        furniture.values.forEach { prop -> for(i in 0..2) half[i]=maxOf(half[i],prop.half[i]) }
        val frame = portraitFrame(viewport.width, viewport.height, half[0], half[1], half[2])
        // A small elevated room view reveals the carpet surface. Sphere fitting
        // remains valid under this rotation; camera distance/aspect stay unchanged.
        val elevation=if(furniture.isEmpty()) 0f else .18f
        cameraNode.position = Position(y=frame.distance*elevation,
            z=frame.distance*kotlin.math.sqrt(1f-elevation*elevation))
        cameraNode.lookAt(Position(0f, 0f, 0f))
        cameraNode.camera.setProjection(
            45.0,
            viewport.width.toDouble() / viewport.height,
            frame.near.toDouble(), frame.far.toDouble(), Camera.Fov.VERTICAL,
        )
        Log.d("PawQuestScene", "Framed ${viewport.width}x${viewport.height}, half=${half.contentToString()}, distance=${frame.distance}")
    }

    override fun destroy() {
        if (!isDestroyed) {
            lifecycle?.removeObserver(animationLifecycleObserver)
            onFrame = null
            animations?.dispose()
            animations = null
            onPlayback = {}
            onPetTouch = {}
            onEquipmentStatus = {}
            onRoomStatus = {}
            furniture.keys.toList().forEach(::removeFurniture)
            roomAssets=null
            clearAccessories()
            equipmentAssets=null
            setOnGestureListener()
            // Remove entities from the scene before gltfio releases their materials/buffers.
            clearChildNodes()
            cat = null
            asset?.let { modelLoader.destroyModel(it) }
            asset = null
            // The default environment is constructed outside EnvironmentLoader's tracked list.
            val reflections = environment.indirectLight?.reflectionsTexture
            scene.indirectLight = null
            scene.skybox = null
            environmentLoader.destroyEnvironment(environment)
            reflections?.let { engine.destroyTexture(it) }
            val grading = view.colorGrading
            view.colorGrading = null
            grading?.let { engine.destroyColorGrading(it) }
            collisionSystem.destroy()
            // SceneView releases loaders (including partially loaded assets), camera, lights,
            // render surface, scene, engine, and EGL context exactly once.
            super.destroy()
            Log.d("PawQuestScene", "Scene destroyed")
        }
    }
}
