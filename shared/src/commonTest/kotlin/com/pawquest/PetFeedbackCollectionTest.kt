package com.pawquest

import com.pawquest.domain.model.*
import com.pawquest.domain.repository.PetRepository
import com.pawquest.domain.usecase.ManagePet
import com.pawquest.presentation.pet.PetViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PetFeedbackCollectionTest {
    private val now=Instant.parse("2026-11-06T00:00:00Z")
    private val starter=PetSpecies("mochi_cat","Mochi",1,"prototype_cat",true)
    private val extra=PetSpecies("fixture","Fixture",5,"prototype_cat",true)
    private inner class Repository : PetRepository {
        var snapshot=PetSnapshot(Pet("pet","owner","Mochi",PetStats(),PetProgress(400),now),0,now)
        var species=starter.code
        var selectFails=false
        override suspend fun restore()=snapshot.copy(actionResult=null)
        override suspend fun collection()=PetCollection(species,5,listOf(starter,extra))
        override suspend fun selectSpecies(code: String): PetSnapshot {
            if(selectFails) error("offline")
            species=code;snapshot=snapshot.copy(pet=snapshot.pet.copy(speciesCode=code))
            return snapshot
        }
        override suspend fun care(action: PetCareAction,requestId: String): PetSnapshot {
            val sleeping=snapshot.pet.sleepUntil!=null
            val code=PetCareRules.rejection(snapshot.pet.stats,snapshot.foodQuantity,sleeping,action)
                ?: if(sleeping && action==PetCareAction.TOUCH) "woken" else "touched"
            if(code=="woken") snapshot=snapshot.copy(pet=snapshot.pet.copy(sleepUntil=null))
            return snapshot.copy(actionResult=PetActionResult(requestId,action,code,code in listOf("touched","woken"),0))
        }
    }
    @Test fun fullStatsExplainFoodDrinkAndSleepWithoutChangingStatsOrInventory()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo=Repository();val vm=PetViewModel(ManagePet(repo));runCurrent()
            for((action,code) in listOf(PetCareAction.FEED to "already_full",PetCareAction.DRINK to "already_hydrated",
                PetCareAction.SLEEP to "fully_rested")) {
                vm.care(action);runCurrent()
                assertEquals(code,vm.state.value.feedbackCode)
                assertFalse(vm.state.value.feedback.isNullOrBlank())
                assertNull(vm.state.value.reaction)
                assertEquals(PetStats(),vm.state.value.snapshot!!.pet.stats)
                vm.dismissFeedback();assertNull(vm.state.value.feedback)
            }
        } finally {Dispatchers.resetMain()}
    }
    @Test fun noFoodExplainsEmptyWalletOrShopBalanceAndWaterIsFree()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo=Repository().apply {snapshot=snapshot.copy(pet=snapshot.pet.copy(stats=PetStats(hunger=20)))}
            val vm=PetViewModel(ManagePet(repo));runCurrent()
            vm.care(PetCareAction.FEED);runCurrent()
            assertEquals("no_food",vm.state.value.feedbackCode)
            assertTrue(vm.state.value.feedback!!.contains("claim its reward"))
            assertTrue(vm.state.value.feedback!!.contains("water is free"))
            repo.snapshot=repo.snapshot.copy(coins=100);vm.refresh();runCurrent()
            vm.care(PetCareAction.FEED);runCurrent()
            assertTrue(vm.state.value.feedback!!.contains("100 Coins"))
        } finally {Dispatchers.resetMain()}
    }
    @Test fun touchWakesAndSelectsHappyReactionWithoutGrantingEnergy()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo=Repository().apply {snapshot=snapshot.copy(pet=snapshot.pet.copy(stats=PetStats(50,50,20),sleepUntil=now))}
            val vm=PetViewModel(ManagePet(repo));runCurrent()
            vm.care(PetCareAction.TOUCH);runCurrent()
            assertNull(vm.state.value.snapshot!!.pet.sleepUntil)
            assertEquals(20,vm.state.value.snapshot!!.pet.stats.energy)
            assertEquals(PetCareAction.TOUCH,vm.state.value.reaction)
            assertEquals("woken",vm.state.value.lastActionResult!!.code)
            advanceUntilIdle()
            assertNull(vm.state.value.reaction)
        } finally {Dispatchers.resetMain()}
    }
    @Test fun selectionRequiresServerOwnershipAndInstalledArtRegardlessOfLevel() {
        assertTrue(PetUnlockRules.canSelect(extra,1))
        assertTrue(PetUnlockRules.canSelect(extra,PetProgress(400).level))
        assertFalse(PetUnlockRules.canSelect(extra.copy(unlocked=false),10))
        assertFalse(PetUnlockRules.canSelect(extra.copy(rendererKey="not_installed"),10))
        assertEquals(listOf(5L,10,15,20),PetUnlockRules.nextMilestones(1))
        assertEquals(listOf(10L,15,20,25),PetUnlockRules.nextMilestones(5))
    }
    @Test fun selectionPersistsAcrossViewModelRestartAndPreservesProgressAndStats()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo=Repository();val vm=PetViewModel(ManagePet(repo));runCurrent();vm.loadCollection();runCurrent()
            vm.selectSpecies(extra.code);runCurrent()
            assertEquals(extra.code,vm.state.value.collection!!.activeCode)
            assertEquals(PetProgress(400),vm.state.value.snapshot!!.pet.progress)
            assertEquals(PetStats(),vm.state.value.snapshot!!.pet.stats)
            val reopened=PetViewModel(ManagePet(repo));runCurrent();reopened.loadCollection();runCurrent()
            assertEquals(extra.code,reopened.state.value.collection!!.activeCode)
            repo.selectFails=true;reopened.selectSpecies(starter.code);runCurrent()
            assertEquals(extra.code,reopened.state.value.collection!!.activeCode)
            assertNotNull(reopened.state.value.feedback)
        } finally {Dispatchers.resetMain()}
    }
}
