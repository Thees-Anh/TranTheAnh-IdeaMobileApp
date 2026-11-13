package com.pawquest.presentation.scene

import com.google.android.filament.Engine
import io.github.sceneview.node.ModelNode

internal class FilamentAnimationPlayer(engine: Engine, node: ModelNode) : AnimationPlayer {
    private val animator = node.animator
    private val transforms = engine.transformManager
    // gltfio animation leaves untargeted properties unchanged. Restore authored local
    // transforms before each clip sample, so disjoint clips cannot accumulate poses.
    private val restTransforms = node.modelInstance.asset.entities.asSequence().mapNotNull { entity ->
        transforms.getInstance(entity).takeIf { it != 0 }?.let { instance ->
            instance to FloatArray(16).also { transforms.getTransform(instance, it) }
        }
    }.toList()
    override val clips = (0 until animator.animationCount).map {
        AnimationClip(it, animator.getAnimationName(it), animator.getAnimationDuration(it))
    }
    override fun restorePose() {
        restTransforms.forEach { (instance, matrix) -> transforms.setTransform(instance, matrix) }
        animator.resetBoneMatrices()
    }
    override fun apply(index: Int, seconds: Float) = animator.applyAnimation(index, seconds)
    override fun updateBones() = animator.updateBoneMatrices()
}
