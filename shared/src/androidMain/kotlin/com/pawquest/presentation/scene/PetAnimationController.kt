package com.pawquest.presentation.scene

import com.pawquest.domain.model.PetAnimationState

internal data class AnimationClip(val index: Int, val name: String, val duration: Float)
internal data class AnimationPlayback(
    val requested: PetAnimationState = PetAnimationState.IDLE,
    val active: PetAnimationState? = null,
    val clip: String? = null,
    val message: String = "Waiting for model",
)

/** Adapter keeps native ownership out of the state machine and permits JVM tests. */
internal interface AnimationPlayer {
    val clips: List<AnimationClip>
    fun restorePose()
    fun apply(index: Int, seconds: Float)
    fun updateBones()
}

/** Main-thread only. One clip, one clock, no independent animation jobs or autoplay. */
internal class PetAnimationController(
    private val player: AnimationPlayer,
    private val onPlayback: (AnimationPlayback) -> Unit = {},
) {
    private val mapped = PetAnimationState.entries.associateWith { state ->
        val names = when (state) {
            PetAnimationState.EATING -> listOf("eating", "eat")
            PetAnimationState.SLEEPING -> listOf("sleeping", "sleep")
            PetAnimationState.DRINKING -> listOf("drinking", "drink")
            PetAnimationState.PLAYING -> listOf("playing", "play")
            else -> listOf(state.name.lowercase())
        }
        names.firstNotNullOfOrNull { name ->
            player.clips.firstOrNull { it.name.trim().lowercase() == name && it.duration.isFinite() && it.duration > 0f }
        }
    }
    var playback = AnimationPlayback()
        private set
    private var selected: AnimationClip? = null
    private var previousFrame: Long? = null
    private var elapsed = 0.0
    private var disposed = false
    private var commandToken: String? = null

    fun switchTo(state: PetAnimationState, token: String? = null) {
        if (disposed || playback.requested == state && commandToken == token && playback.message != "Waiting for model") return
        commandToken=token
        val clip = mapped[state]
        val fallback = clip ?: mapped[PetAnimationState.IDLE]
        selected = fallback
        elapsed = 0.0
        previousFrame = null
        player.restorePose()
        fallback?.let { player.apply(it.index, 0f) }
        player.updateBones()
        playback = AnimationPlayback(
            requested = state,
            active = if (clip != null) state else if (fallback != null) PetAnimationState.IDLE else null,
            clip = fallback?.name,
            message = if (clip != null) "Playing ${clip.name}"
                else if (fallback != null) "${state.name} clip missing; playing ${fallback.name}"
                else "${state.name} clip missing; showing rest pose",
        )
        onPlayback(playback)
    }

    fun onFrame(nanos: Long) {
        if (disposed) return
        val clip = selected ?: return
        previousFrame?.let { previous ->
            // Pauses/background gaps do not fast-forward the pet or jump poses.
            elapsed += ((nanos - previous).coerceAtLeast(0L) / 1_000_000_000.0).coerceAtMost(0.1)
        }
        previousFrame = nanos
        elapsed %= clip.duration.toDouble()
        player.restorePose()
        player.apply(clip.index, elapsed.toFloat())
        player.updateBones()
    }

    fun pause() { previousFrame = null }

    fun dispose() {
        disposed = true
        selected = null
        previousFrame = null
    }
}
