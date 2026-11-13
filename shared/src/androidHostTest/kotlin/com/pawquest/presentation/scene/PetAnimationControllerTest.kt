package com.pawquest.presentation.scene

import com.pawquest.domain.model.PetAnimationState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PetAnimationControllerTest {
    private class Player(override val clips: List<AnimationClip>) : AnimationPlayer {
        val samples = mutableListOf<Pair<Int, Float>>()
        var restores = 0
        var bones = 0
        override fun restorePose() { restores++ }
        override fun apply(index: Int, seconds: Float) { samples += index to seconds }
        override fun updateBones() { bones++ }
    }

    @Test fun mapsNamesRatherThanClipOrder() {
        val names = listOf("PLAY", "eat", " Happy ", "Sleep", "idle")
        val player = Player(names.mapIndexed { i, name -> AnimationClip(i + 10, name, 1f) })
        val controller = PetAnimationController(player)
        val indices = mapOf(PetAnimationState.IDLE to 14, PetAnimationState.HAPPY to 12,
            PetAnimationState.EATING to 11, PetAnimationState.SLEEPING to 13, PetAnimationState.PLAYING to 10)
        indices.forEach { (state, index) ->
            controller.switchTo(state)
            assertEquals(index, player.samples.last().first)
            assertEquals(state, controller.playback.active)
        }
    }

    @Test fun missingAndInvalidClipsFallBackToIdle() {
        val player = Player(listOf(AnimationClip(8, "Idle", 2f), AnimationClip(2, "Happy", Float.NaN)))
        val controller = PetAnimationController(player)
        controller.switchTo(PetAnimationState.HAPPY)
        assertEquals(PetAnimationState.IDLE, controller.playback.active)
        assertEquals(8, player.samples.single().first)
        assertTrue(controller.playback.message.contains("missing"))
    }

    @Test fun staticAssetAndDisposedControllerNeverApplyAClip() {
        val player = Player(emptyList())
        val controller = PetAnimationController(player)
        PetAnimationState.entries.forEach { controller.switchTo(it); controller.onFrame(100) }
        assertNull(controller.playback.active)
        assertTrue(player.samples.isEmpty())
        val restores = player.restores
        controller.dispose()
        controller.switchTo(PetAnimationState.IDLE)
        controller.onFrame(200)
        assertEquals(restores, player.restores)
    }

    @Test fun switchingResetsClockAndDoesNotAccumulateClips() {
        val player = Player(listOf(AnimationClip(0, "Idle", 2f), AnimationClip(1, "Happy", 1f)))
        val controller = PetAnimationController(player)
        controller.switchTo(PetAnimationState.IDLE)
        controller.onFrame(0)
        controller.onFrame(50_000_000)
        assertEquals(0.05f, player.samples.last().second)
        controller.switchTo(PetAnimationState.HAPPY)
        controller.onFrame(500_000_000)
        assertEquals(1 to 0f, player.samples.last())
        controller.onFrame(550_000_000)
        val count = player.samples.size
        controller.switchTo(PetAnimationState.HAPPY)
        assertEquals(count, player.samples.size)
        assertEquals(player.samples.size, player.restores)
        assertEquals(player.samples.size, player.bones)
    }

    @Test fun pausedTimeDoesNotAdvanceAndLargeGapsAreBounded() {
        val player = Player(listOf(AnimationClip(0, "Idle", 2f)))
        val controller = PetAnimationController(player)
        controller.switchTo(PetAnimationState.IDLE)
        controller.onFrame(0)
        controller.onFrame(50_000_000)
        controller.pause()
        controller.onFrame(99_000_000_000)
        assertEquals(0.05f, player.samples.last().second)
        controller.onFrame(199_000_000_000)
        assertEquals(0.15f, player.samples.last().second)
    }

    @Test fun playbackLoopsWithinDuration() {
        val player = Player(listOf(AnimationClip(0, "Idle", 0.15f)))
        val controller = PetAnimationController(player)
        controller.switchTo(PetAnimationState.IDLE)
        repeat(50) { controller.onFrame(it * 100_000_000L) }
        assertTrue(player.samples.all { it.second >= 0f && it.second < 0.15f })
    }
}
