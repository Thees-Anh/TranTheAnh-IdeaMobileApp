package com.pawquest.presentation.scene

import com.pawquest.domain.model.*
import com.pawquest.presentation.pet.PetState
import kotlin.test.*
import kotlin.time.Instant

class PetCareAnimationTest {
    private val stamp=Instant.parse("2026-11-05T00:00:00Z")
    private fun state(action: PetCareAction,applied: Boolean=true,token: String="request")=PetState(
        snapshot=PetSnapshot(Pet("pet","owner","Mochi",PetStats(),PetProgress(0),stamp),0,stamp),
        lastActionResult=PetActionResult(token,action,"result",applied,0),reaction=action)

    @Test fun onlyConfirmedReactionsSelectTheirClipAndIdleAfterExpiry() {
        for((action,animation) in listOf(PetCareAction.DRINK to PetAnimationState.DRINKING,PetCareAction.FEED to PetAnimationState.EATING,
            PetCareAction.PLAY to PetAnimationState.PLAYING,PetCareAction.TOUCH to PetAnimationState.HAPPY)) {
            assertEquals(PetAnimationCommand(animation,"request"),petAnimationCommand(state(action)))
            assertEquals(PetAnimationState.IDLE,petAnimationCommand(state(action,false)).state)
            assertEquals(PetAnimationState.IDLE,petAnimationCommand(state(action).copy(reaction=null)).state)
        }
        assertEquals(PetAnimationState.IDLE,petAnimationCommand(null).state)
        assertEquals(PetAnimationState.IDLE,petAnimationCommand(state(PetCareAction.FEED).copy(
            lastActionResult=PetActionResult("other",PetCareAction.PLAY,"played",true,0))).state)
    }
    @Test fun persistedSleepWinsOverTouchOrOtherTemporaryCommands() {
        val initial=state(PetCareAction.TOUCH)
        val sleeping=initial.copy(snapshot=initial.snapshot!!.copy(pet=initial.snapshot.pet.copy(sleepUntil=stamp)))
        assertEquals(PetAnimationState.SLEEPING,petAnimationCommand(sleeping).state)
        assertEquals(PetAnimationState.SLEEPING,petAnimationCommand(sleeping.copy(reaction=null)).state)
        assertEquals(PetAnimationState.IDLE,petAnimationCommand(state(PetCareAction.SLEEP)).state)
    }
    private class Player : AnimationPlayer {
        override val clips=listOf(AnimationClip(0,"Idle",1f),AnimationClip(1,"Eating",1f),AnimationClip(2,"Happy",1f))
        val samples=mutableListOf<Pair<Int,Float>>()
        override fun restorePose()=Unit
        override fun updateBones()=Unit
        override fun apply(index: Int,seconds: Float) { samples+=index to seconds }
    }
    @Test fun sameRequestDoesNotRestartButANewSameActionDoes() {
        val player=Player(); val controller=PetAnimationController(player)
        controller.switchTo(PetAnimationState.EATING,"one"); val count=player.samples.size
        controller.switchTo(PetAnimationState.EATING,"one"); assertEquals(count,player.samples.size)
        controller.switchTo(PetAnimationState.EATING,"two")
        assertEquals(count+1,player.samples.size)
        assertEquals(1 to 0f,player.samples.last())
    }
    @Test fun rapidSwitchingAndMissingClipsNeverOverlapOrRunAfterDispose() {
        val player=Player(); val controller=PetAnimationController(player)
        repeat(1000) { index ->
            val state=PetAnimationState.entries[index%PetAnimationState.entries.size]
            controller.switchTo(state,index.toString())
            val selected=when(state) { PetAnimationState.EATING->1; PetAnimationState.HAPPY->2; else->0 }
            controller.onFrame(index.toLong()*16_000_000)
            assertEquals(selected,player.samples.last().first)
        }
        controller.dispose(); val count=player.samples.size
        controller.switchTo(PetAnimationState.HAPPY,"later"); controller.onFrame(Long.MAX_VALUE)
        assertEquals(count,player.samples.size)
    }
    @Test fun accessoryGeometryIsIncludedEvenWithAnUndersizedSidecar() {
        val envelope=AnimationBounds(floatArrayOf(0f,0f,0f),floatArrayOf(1f,1f,1f))
            .including(floatArrayOf(3f,2f,0f),floatArrayOf(1f,4f,2f))
        assertContentEquals(floatArrayOf(1.5f,2f,0f),envelope.center)
        assertContentEquals(floatArrayOf(2.5f,4f,2f),envelope.half)
        for((w,h) in listOf(1080 to 1920,1080 to 2340,1080 to 2400,320 to 260)) {
            val fit=portraitFrame(w,h,envelope.half[0],envelope.half[1],envelope.half[2])
            val tanY=kotlin.math.tan(Math.PI/8)
            val tanX=tanY*w/h
            for(z in listOf(-2f,2f)) {
                val depth=fit.distance-z
                assertTrue(2.5/(depth*tanX)<1 && 4/(depth*tanY)<1)
                assertTrue(depth>fit.near && depth<fit.far)
            }
        }
    }
}
