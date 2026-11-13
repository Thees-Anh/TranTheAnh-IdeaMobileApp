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
class PetCareTest {
    private val stamp=Instant.parse("2026-11-05T00:00:00Z")
    @Test fun invalidActionsHaveSpecificReasons() {
        assertEquals("already_full",PetCareRules.rejection(PetStats(),1,false,PetCareAction.FEED))
        assertEquals("no_food",PetCareRules.rejection(PetStats(50,50,50),0,false,PetCareAction.FEED))
        assertEquals("not_enough_energy",PetCareRules.rejection(PetStats(50,50,4),1,false,PetCareAction.PLAY))
        assertNull(PetCareRules.rejection(PetStats(50,100,50),1,false,PetCareAction.PLAY))
        assertEquals("fully_rested",PetCareRules.rejection(PetStats(),1,false,PetCareAction.SLEEP))
        assertEquals("already_sleeping",PetCareRules.rejection(PetStats(),1,true,PetCareAction.SLEEP))
        assertNull(PetCareRules.rejection(PetStats(),1,true,PetCareAction.TOUCH))
        assertNull(PetCareRules.rejection(PetStats(50,50,5),1,false,PetCareAction.PLAY))
    }
    @Test fun sleepIsGradualAndTouchDoesNotChangeStats() {
        assertEquals(20,PetCareRules.sleepEnergy(20,0))
        assertEquals(30,PetCareRules.sleepEnergy(20,5))
        assertEquals(100,PetCareRules.sleepEnergy(20,40))
        assertEquals(100,PetCareRules.sleepEnergy(0,Long.MAX_VALUE))
        assertFailsWith<IllegalArgumentException> { PetCareRules.sleepEnergy(20,-1) }
        val stats=PetStats(50,50,50)
        assertEquals(stats,stats.care(PetCareAction.SLEEP))
        assertEquals(stats,stats.care(PetCareAction.TOUCH))
    }
    private inner class Repository : PetRepository {
        var value=PetSnapshot(Pet("pet","owner","Mochi",PetStats(50,50,50),PetProgress(0),stamp),0,stamp,1)
        var lostResponse=false
        var responseGate: CompletableDeferred<Unit>?=null
        var restoreGate: CompletableDeferred<Unit>?=null
        var collectionGate: CompletableDeferred<Unit>?=null
        var reads=0
        val requests=mutableListOf<String>()
        val results=mutableMapOf<String,PetActionResult>()
        override suspend fun restore(): PetSnapshot {reads++;restoreGate?.await();return value.copy(actionResult=null)}
        override suspend fun collection(): PetCollection {
            collectionGate?.await()
            return PetCollection("mochi_cat",1,emptyList())
        }
        override suspend fun care(action: PetCareAction,requestId: String): PetSnapshot {
            requests+=requestId
            val result=results.getOrPut(requestId) {
                val rejected=PetCareRules.rejection(value.pet.stats,value.foodQuantity,value.pet.sleepUntil!=null,action)
                if(rejected!=null) PetActionResult(requestId,action,rejected,false,0)
                else {
                    value=value.copy(pet=value.pet.copy(stats=value.pet.stats.care(action)),
                        foodQuantity=value.foodQuantity-if(action==PetCareAction.FEED) 1 else 0)
                    PetActionResult(requestId,action,when(action) {
                        PetCareAction.DRINK -> "drank"; PetCareAction.FEED -> "fed"; PetCareAction.PLAY -> "played"
                        PetCareAction.SLEEP -> "sleep_started"; PetCareAction.TOUCH -> "touched"
                    },true,if(action==PetCareAction.FEED) 1 else 0)
                }
            }
            if(lostResponse) { lostResponse=false; error("response lost") }
            responseGate?.await()
            return value.copy(actionResult=result)
        }
    }
    @Test fun lateCollectionCannotRestoreBusyOrUndoConfirmedCare()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo=Repository().apply {value=value.copy(pet=value.pet.copy(stats=value.pet.stats.copy(hydration=20)))}
        val model=PetViewModel(ManagePet(repo))
        try {
            runCurrent();repo.responseGate=CompletableDeferred();repo.collectionGate=CompletableDeferred()
            model.care(PetCareAction.FEED);runCurrent();model.loadCollection();runCurrent()
            repo.responseGate!!.complete(Unit);runCurrent()
            assertFalse(model.state.value.busy);assertNull(model.state.value.pendingAction)
            val confirmed=model.state.value.snapshot
            repo.collectionGate!!.complete(Unit);runCurrent()
            assertFalse(model.state.value.busy);assertFalse(model.state.value.collectionBusy)
            assertNull(model.state.value.pendingAction);assertEquals(confirmed,model.state.value.snapshot)
            assertEquals("fed",model.state.value.lastActionResult?.code)
            model.care(PetCareAction.DRINK);runCurrent()
            assertEquals("drank",model.state.value.lastActionResult?.code)
            assertEquals(0,model.state.value.snapshot?.foodQuantity)
        } finally {advanceUntilIdle();Dispatchers.resetMain()}
    }
    @Test fun collectionStartedDuringRestoreCannotUndoRestoredSnapshot()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo=Repository().apply {restoreGate=CompletableDeferred();collectionGate=CompletableDeferred()}
        val model=PetViewModel(ManagePet(repo))
        try {
            runCurrent();model.loadCollection();runCurrent()
            repo.restoreGate!!.complete(Unit);runCurrent()
            assertNotNull(model.state.value.snapshot)
            repo.collectionGate!!.complete(Unit);runCurrent()
            assertFalse(model.state.value.busy);assertFalse(model.state.value.loading)
            assertNotNull(model.state.value.snapshot);assertNotNull(model.state.value.collection)
        } finally {advanceUntilIdle();Dispatchers.resetMain()}
    }
    @Test fun stalledRestoreReleasesControlsAndRequiresExplicitRetry()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo=Repository().apply {restoreGate=CompletableDeferred()}
        val model=PetViewModel(ManagePet(repo),100)
        try {
            runCurrent();model.refresh();model.care(PetCareAction.TOUCH)
            assertNull(model.state.value.feedback)
            advanceTimeBy(101);runCurrent()
            assertFalse(model.state.value.busy);assertFalse(model.state.value.loading)
            assertNotNull(model.state.value.error);assertEquals(1,repo.reads)
            repo.restoreGate=null;model.retry();runCurrent()
            assertNotNull(model.state.value.snapshot);assertNull(model.state.value.error)
        } finally {advanceUntilIdle();Dispatchers.resetMain()}
    }
    @Test fun committedFeedTimeoutRetriesSameReceiptWithoutConsumingAnotherFood()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo=Repository();val model=PetViewModel(ManagePet(repo),100)
        try {
            runCurrent();repo.responseGate=CompletableDeferred()
            model.care(PetCareAction.FEED);runCurrent();model.care(PetCareAction.TOUCH)
            assertNull(model.state.value.feedback)
            advanceTimeBy(101);runCurrent()
            assertFalse(model.state.value.busy);assertNotNull(model.state.value.error)
            assertEquals(PetCareAction.FEED,model.state.value.pendingAction)
            model.care(PetCareAction.DRINK);runCurrent();assertEquals(1,repo.requests.size)
            repo.responseGate=null;model.retry();runCurrent()
            assertEquals(2,repo.requests.size);assertEquals(1,repo.requests.distinct().size)
            assertEquals(1,repo.results.size);assertEquals(0,model.state.value.snapshot?.foodQuantity)
            assertEquals(65,model.state.value.snapshot?.pet?.stats?.hunger)
            assertNull(model.state.value.pendingAction);assertNull(model.state.value.error)
        } finally {advanceUntilIdle();Dispatchers.resetMain()}
    }
    @Test fun touchReactionExpiresWithoutPermanentHappinessBoost() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo=Repository(); val model=PetViewModel(ManagePet(repo))
        try {
            runCurrent(); model.care(PetCareAction.TOUCH); runCurrent()
            assertEquals("touched",model.state.value.lastActionResult?.code)
            assertEquals(PetCareAction.TOUCH,model.state.value.reaction)
            assertEquals(50,model.state.value.snapshot?.pet?.stats?.happiness)
            advanceTimeBy(PetCareBalance.reactionMilliseconds.toLong()+1); runCurrent()
            assertNull(model.state.value.reaction)
        } finally { advanceUntilIdle(); Dispatchers.resetMain() }
    }
    @Test fun rejectedCareIsTerminalAndAllowsTheNextAction() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo=Repository().apply { value=value.copy(foodQuantity=0) }; val model=PetViewModel(ManagePet(repo))
        try {
            runCurrent(); model.care(PetCareAction.FEED); runCurrent()
            assertEquals("no_food",model.state.value.lastActionResult?.code)
            assertNull(model.state.value.pendingAction)
            assertNull(model.state.value.reaction)
            model.care(PetCareAction.PLAY); runCurrent()
            assertEquals("played",model.state.value.lastActionResult?.code)
            assertEquals(45,model.state.value.snapshot?.pet?.stats?.energy)
        } finally { advanceUntilIdle(); Dispatchers.resetMain() }
    }
    @Test fun lostFeedResponseRetryConsumesOnlyOneFoodAndRestartRestoresIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo=Repository(); val model=PetViewModel(ManagePet(repo))
        try {
            runCurrent(); repo.lostResponse=true; model.care(PetCareAction.FEED); runCurrent()
            assertNotNull(model.state.value.error)
            model.retry(); runCurrent()
            assertEquals(1,repo.results.size)
            assertEquals(0,model.state.value.snapshot?.foodQuantity)
            assertEquals(65,model.state.value.snapshot?.pet?.stats?.hunger)
            assertEquals(1,model.state.value.lastActionResult?.foodConsumed)
            val reopened=PetViewModel(ManagePet(repo)); runCurrent()
            assertEquals(0,reopened.state.value.snapshot?.foodQuantity)
            assertEquals(65,reopened.state.value.snapshot?.pet?.stats?.hunger)
        } finally { advanceUntilIdle(); Dispatchers.resetMain() }
    }
    @Test fun lostDrinkResponseReusesRequestAndRestoresWaterWithoutSpendingFoodOrCoins() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo=Repository().apply {value=value.copy(pet=value.pet.copy(stats=value.pet.stats.copy(hydration=20)))}
        val model=PetViewModel(ManagePet(repo))
        try {
            runCurrent();repo.lostResponse=true;model.care(PetCareAction.DRINK);runCurrent()
            assertNotNull(model.state.value.error)
            model.retry();runCurrent()
            assertEquals(1,repo.results.size)
            assertEquals(40,model.state.value.snapshot?.pet?.stats?.hydration)
            assertEquals(1,model.state.value.snapshot?.foodQuantity)
            assertEquals(0L,model.state.value.snapshot?.coins)
            assertEquals(PetCareAction.DRINK,model.state.value.reaction)
            val reopened=PetViewModel(ManagePet(repo));runCurrent()
            assertEquals(40,reopened.state.value.snapshot?.pet?.stats?.hydration)
        } finally {advanceUntilIdle();Dispatchers.resetMain()}
    }
}
