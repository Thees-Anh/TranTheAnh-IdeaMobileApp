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
class PetStateTest {
    private val instant=Instant.parse("2026-11-05T00:00:00Z")
    private fun snapshot(xp: Long=0)=PetSnapshot(Pet("pet","owner","Mochi",PetStats(),PetProgress(xp),instant),0,instant)
    @Test fun boundariesAndCareNeverLeaveTheValidRange() {
        assertFailsWith<IllegalArgumentException> { PetStats(-1,0,0) }
        assertFailsWith<IllegalArgumentException> { PetStats(0,101,0) }
        assertFailsWith<IllegalArgumentException> { PetStats(0,0,101) }
        for(hunger in 0..100) for(action in PetCareAction.entries) {
            val result=PetStats(hunger,hunger,hunger).care(action)
            assertTrue(result.hunger in 0..100 && result.happiness in 0..100 && result.energy in 0..100)
        }
        assertEquals(PetStats(100,100,100),PetStats().care(PetCareAction.FEED))
        assertEquals(PetStats(0,15,0,97),PetStats(0,0,0).care(PetCareAction.PLAY))
        assertEquals(PetStats(15,0,0),PetStats(0,0,0).care(PetCareAction.FEED))
    }
    @Test fun inactivityClampsAtZeroWithoutRemovingPetOrXp() {
        assertEquals(PetStats(98,99,99,97),PetStats().afterHours(1))
        assertEquals(PetStats(),PetStats().afterHours(0))
        assertEquals(PetStats(0,0,0,0),PetStats().afterHours(Long.MAX_VALUE))
        assertFailsWith<IllegalArgumentException> { PetStats().afterHours(-1) }
        val pet=snapshot(250).pet
        val inactive=pet.copy(stats=pet.stats.afterHours(1000))
        assertEquals(pet.id,inactive.id)
        assertEquals(250,inactive.progress.totalXp)
        assertEquals(3,inactive.progress.level)
    }
    @Test fun xpBoundariesAndLargeTotalsHaveNoOverflow() {
        for(xp in listOf(0L,1,99,100,101,199,200,Long.MAX_VALUE)) {
            val value=PetProgress(xp)
            assertEquals(xp/100+1,value.level)
            assertEquals((xp%100).toInt(),value.xpInLevel)
            assertEquals(100-(xp%100).toInt(),value.xpToNextLevel)
        }
        assertFailsWith<IllegalArgumentException> { PetProgress(-1) }
    }
    private inner class Repository : PetRepository {
        var value=snapshot()
        var offline=false
        var reads=0
        var gate: CompletableDeferred<Unit>?=null
        val requests=mutableListOf<String>()
        val applied=mutableSetOf<String>()
        override suspend fun restore(): PetSnapshot {
            reads++; if(offline) error("offline"); return value
        }
        override suspend fun care(action: PetCareAction,requestId: String): PetSnapshot {
            requests+=requestId
            gate?.await()
            if(offline) error("offline")
            if(applied.add(requestId)) value=value.copy(pet=value.pet.copy(stats=value.pet.stats.care(action)))
            return value
        }
    }
    @Test fun viewModelRestoresRetainsStateOfflineAndRetryUsesTheSameRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo=Repository().apply { value=snapshot(99).copy(pet=snapshot(99).pet.copy(stats=PetStats(0,0,0))) }
        val model=PetViewModel(ManagePet(repo))
        try {
            runCurrent(); assertEquals(99,model.state.value.snapshot?.pet?.progress?.totalXp)
            repo.offline=true; model.care(PetCareAction.FEED); model.care(PetCareAction.FEED); runCurrent()
            assertEquals(1,repo.requests.size)
            assertEquals(0,model.state.value.snapshot?.pet?.stats?.hunger)
            assertNotNull(model.state.value.error)
            model.refresh(); runCurrent()
            assertNotNull(model.state.value.error) // Refresh cannot hide an unresolved care request.
            model.care(PetCareAction.PLAY); runCurrent(); assertEquals(1,repo.requests.size)
            repo.offline=false; model.retry(); runCurrent()
            assertEquals(repo.requests[0],repo.requests[1])
            assertEquals(15,model.state.value.snapshot?.pet?.stats?.hunger)
            assertEquals(99,model.state.value.snapshot?.pet?.progress?.totalXp)
            val reopened=PetViewModel(ManagePet(repo)); runCurrent()
            assertEquals(model.state.value.snapshot,reopened.state.value.snapshot)
            repo.value=snapshot(100); model.refresh(); runCurrent()
            assertEquals(2,model.state.value.snapshot?.pet?.progress?.level)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun foregroundPollingStopsInBackgroundAndRefreshesOnReturn() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo=Repository(); val model=PetViewModel(ManagePet(repo))
        try {
            runCurrent(); model.foreground(true); runCurrent()
            val reads=repo.reads
            advanceTimeBy(60_001); runCurrent(); assertEquals(reads+1,repo.reads)
            model.foreground(false); val stopped=repo.reads
            advanceTimeBy(180_000); runCurrent(); assertEquals(stopped,repo.reads)
            repo.value=snapshot(200); model.foreground(true); runCurrent()
            assertEquals(3,model.state.value.snapshot?.pet?.progress?.level)
        } finally { model.foreground(false); Dispatchers.resetMain() }
    }
    @Test fun refreshRequestedDuringCareIsNotDropped() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo=Repository(); val model=PetViewModel(ManagePet(repo))
        try {
            runCurrent(); val reads=repo.reads
            repo.gate=CompletableDeferred()
            model.care(PetCareAction.PLAY); runCurrent()
            model.refresh(); assertEquals(reads,repo.reads)
            repo.gate!!.complete(Unit); runCurrent()
            assertEquals(reads+1,repo.reads)
            assertFalse(model.state.value.busy)
        } finally { Dispatchers.resetMain() }
    }
}
