package com.pawquest

import com.pawquest.domain.model.*
import com.pawquest.domain.repository.PetRepository
import com.pawquest.domain.usecase.ManagePet
import com.pawquest.presentation.pet.PetViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Instant
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class PuppyTest {
    private val now=Instant.parse("2026-11-06T02:00:00Z")
    @Test fun sixStatesMapAndPriorityPreserveCareAndSleep() {
        assertEquals(setOf("idle","eating","drinking","sleeping","laughing","sad"),PuppyAnimationState.entries.map {it.wire}.toSet())
        fun state(sleep: Boolean=false,reaction: PetCareAction?=null,celebrate: Boolean=false,hunger: Int=100,missed: Boolean=false)=
            PuppyEmotionRules.state(sleep,reaction,celebrate,hunger,missed)
        assertEquals(PuppyAnimationState.IDLE,state())
        assertEquals(PuppyAnimationState.SAD,state(hunger=25))
        assertEquals(PuppyAnimationState.SAD,state(missed=true))
        assertEquals(PuppyAnimationState.IDLE,state(hunger=26))
        assertEquals(PuppyAnimationState.EATING,state(reaction=PetCareAction.FEED,hunger=0,missed=true))
        assertEquals(PuppyAnimationState.DRINKING,state(reaction=PetCareAction.DRINK))
        assertEquals(PuppyAnimationState.LAUGHING,state(reaction=PetCareAction.PLAY))
        assertEquals(PuppyAnimationState.LAUGHING,state(reaction=PetCareAction.TOUCH))
        assertEquals(PuppyAnimationState.LAUGHING,state(celebrate=true,missed=true))
        assertEquals(PuppyAnimationState.SLEEPING,state(sleep=true,celebrate=true,reaction=PetCareAction.TOUCH))
        assertNotNull(PetArtworkRegistry.find("puppy"))
        assertTrue(PetArtworkRegistry.find("puppy")!!.supportsEquipment)
    }
    private inner class Repository : PetRepository {
        var context=PetCompanionContext(now,false,"daily-session",now)
        var offline=false
        var species="puppy"
        override suspend fun restore()=PetSnapshot(Pet("pet","owner",if(species=="puppy") "Puppy" else "Mochi",PetStats(),PetProgress(400),now,
            speciesCode=species,rendererKey=if(species=="puppy") "puppy" else "prototype_cat"),0,now)
        override suspend fun collection()=PetCollection(species,5,listOf(
            PetSpecies("mochi_cat","Mochi",1,"prototype_cat",true),PetSpecies("puppy","Puppy",5,"puppy",true)))
        override suspend fun selectSpecies(code: String): PetSnapshot {species=code;return restore()}
        override suspend fun care(action: PetCareAction,requestId: String)=restore().copy(
            actionResult=PetActionResult(requestId,action,"touched",true,0))
        override suspend fun companionContext(timezone: String): PetCompanionContext {
            if(offline) error("offline")
            return context
        }
    }
    @Test fun dailyCelebrationWaitsForVisibilityThenLastsExactlyFourSecondsAndDoesNotReplay()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo=Repository();val vm=PetViewModel(ManagePet(repo));runCurrent()
            assertFalse(vm.state.value.celebrating)
            vm.companionVisible(true);runCurrent();assertTrue(vm.state.value.celebrating)
            advanceTimeBy(3999);runCurrent();assertTrue(vm.state.value.celebrating)
            vm.refresh();runCurrent();assertTrue(vm.state.value.celebrating)
            advanceTimeBy(1);runCurrent();assertFalse(vm.state.value.celebrating)
            vm.refresh();runCurrent();assertFalse(vm.state.value.celebrating)
            repo.context=repo.context.copy(completionId="another-daily")
            vm.refresh();runCurrent();assertTrue(vm.state.value.celebrating)
            advanceUntilIdle()
        } finally {Dispatchers.resetMain()}
    }
    @Test fun hiddenOrBackgroundPuppyDoesNotConsumeItsCelebration()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm=PetViewModel(ManagePet(Repository()));runCurrent()
            vm.companionVisible(true);runCurrent();advanceTimeBy(1000)
            vm.companionVisible(false);runCurrent();assertFalse(vm.state.value.celebrating)
            advanceTimeBy(5000);vm.companionVisible(true);runCurrent();assertTrue(vm.state.value.celebrating)
            advanceTimeBy(4000);runCurrent();assertFalse(vm.state.value.celebrating)
        } finally {Dispatchers.resetMain()}
    }
    @Test fun oldCompletionsDoNotCelebrateAndOfflineDoesNotInventMissedQuests()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo=Repository().apply {context=context.copy(completedAt=now-6.minutes)}
            val vm=PetViewModel(ManagePet(repo));runCurrent();vm.companionVisible(true);runCurrent()
            assertFalse(vm.state.value.celebrating)
            repo.offline=true;vm.refresh();runCurrent()
            assertFalse(vm.state.value.companionContext!!.hasMissedQuest)
            assertNotNull(vm.state.value.snapshot)
        } finally {Dispatchers.resetMain()}
    }
    @Test fun dailyCelebrationGetsFourFullSecondsAfterAConfirmedCareReaction()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo=Repository().apply {context=context.copy(completionId=null,completedAt=null)}
            val vm=PetViewModel(ManagePet(repo));runCurrent();vm.companionVisible(true)
            vm.care(PetCareAction.TOUCH);runCurrent()
            repo.context=PetCompanionContext(now,false,"new-daily",now)
            vm.refresh();runCurrent();assertFalse(vm.state.value.celebrating)
            advanceTimeBy(PetCareBalance.reactionMilliseconds.toLong());runCurrent()
            assertTrue(vm.state.value.celebrating)
            advanceTimeBy(3999);runCurrent();assertTrue(vm.state.value.celebrating)
            advanceTimeBy(1);runCurrent();assertFalse(vm.state.value.celebrating)
        } finally {Dispatchers.resetMain()}
    }
    @Test fun switchingToPuppyImmediatelyAllowsCareWithoutRestartOrRedundantRestore()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo=Repository().apply {species="mochi_cat"}
            val vm=PetViewModel(ManagePet(repo));runCurrent();vm.loadCollection();runCurrent()
            vm.selectSpecies("puppy");runCurrent()
            assertEquals("puppy",vm.state.value.snapshot!!.pet.rendererKey)
            assertFalse(vm.state.value.busy);assertFalse(vm.state.value.collectionBusy)
            vm.care(PetCareAction.TOUCH);runCurrent()
            assertEquals(PetCareAction.TOUCH,vm.state.value.reaction)
            assertFalse(vm.state.value.busy)
            advanceUntilIdle()
        } finally {Dispatchers.resetMain()}
    }
}
