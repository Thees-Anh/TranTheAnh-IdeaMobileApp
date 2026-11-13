package com.pawquest

import com.pawquest.domain.model.*
import com.pawquest.domain.repository.FocusRepository
import com.pawquest.domain.usecase.ManageFocusSessions
import com.pawquest.presentation.focus.FocusViewModel
import com.pawquest.presentation.focus.FocusState
import com.pawquest.presentation.quests.progressForQuest
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class FocusTest {
    private val base=Instant.parse("2026-11-05T00:00:00Z")
    private val questId="a2000000-0000-0000-0000-000000000001"
    private fun session()=FocusSession("a4000000-0000-0000-0000-000000000001",questId,"Original title",5,base,base+5.minutes,FocusStatus.ACTIVE)
    @Test fun questCardMatchesOnlyTheUnfinishedSessionAndUsesItsCountdown() {
        val active=FocusState(snapshot=FocusSnapshot(base,session(),false),remainingSeconds=185)
        assertNull(active.progressForQuest("another-quest"))
        val card=assertNotNull(active.progressForQuest(questId))
        assertEquals("3:05",card.timeLabel)
        assertEquals(115f/300,card.elapsedFraction,0.0001f)
        assertFalse(card.awaitingConfirmation)
        for(status in listOf(FocusStatus.CANCELLED,FocusStatus.CLAIMED))
            assertNull(active.copy(snapshot=FocusSnapshot(base,session().copy(status=status),false)).progressForQuest(questId))
        assertTrue(assertNotNull(active.copy(remainingSeconds=0).progressForQuest(questId)).awaitingConfirmation)
    }
    @Test fun returningToQuestsKeepsCountdownAndResumeDoesNotStartAnotherSession()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo=Repository {base+testScheduler.currentTime.milliseconds}
        val model=FocusViewModel(ManageFocusSessions(repo),FocusClock {testScheduler.currentTime})
        try {
            runCurrent();model.foreground(true);runCurrent();model.start(questId);runCurrent()
            val id=model.state.value.session?.id
            model.hide();advanceTimeBy(10_000);runCurrent()
            assertFalse(model.state.value.visible)
            assertEquals("4:50",model.state.value.progressForQuest(questId)?.timeLabel)
            model.show();runCurrent()
            assertTrue(model.state.value.visible)
            assertEquals(id,model.state.value.session?.id);assertEquals(1,repo.starts.size)
            assertEquals(290L,model.state.value.remainingSeconds)
        } finally {model.foreground(false);advanceUntilIdle();Dispatchers.resetMain()}
    }
    private inner class Repository(val now:()->Instant) : FocusRepository {
        var current: FocusSession?=null
        var reward: ClaimedReward?=null
        var offline=false
        var loseStartResponse=false
        var loseClaimResponse=false
        var starts=mutableListOf<String>()
        var claims=0
        var offerOverride: RewardOffer?=null
        var nextReward=ClaimedReward(10,5,10)
        var reconciles=0
        fun snapshot(): FocusSnapshot {
            val stamp=now()
            if(current?.status==FocusStatus.ACTIVE && stamp>=current!!.endsAt) current=current!!.copy(status=FocusStatus.COMPLETED)
            val eligible=current?.let { it.status==FocusStatus.COMPLETED }==true
            return FocusSnapshot(stamp,current,eligible,reward,
                current?.let { offerOverride ?: RewardOffer(10,10,5,"2026-11-05",0,false,eligible) })
        }
        override suspend fun reconcile(sessionId: String?): FocusSnapshot {
            reconciles++; if(offline) error("offline"); return snapshot()
        }
        override suspend fun start(questId: String,requestId: String): FocusSnapshot {
            starts+=requestId
            if(offline) error("offline")
            current=current ?: session()
            if(loseStartResponse) { loseStartResponse=false; error("response lost") }
            return snapshot()
        }
        override suspend fun cancel(sessionId: String): FocusSnapshot {
            if(offline) error("offline")
            current=current!!.copy(status=FocusStatus.CANCELLED)
            return snapshot()
        }
        override suspend fun claim(sessionId: String): FocusSnapshot {
            if(offline) error("offline")
            check(now()>=current!!.endsAt)
            claims++
            current=current!!.copy(status=FocusStatus.CLAIMED)
            reward=nextReward
            if(loseClaimResponse) { loseClaimResponse=false; error("response lost") }
            return snapshot()
        }
    }
    @Test fun monotonicDisplayRoundsUpAndCannotCreateEligibility() {
        var monotonic=0L
        val countdown=FocusCountdown { monotonic }
        countdown.anchor(FocusSnapshot(base,session(),false))
        assertEquals(300,countdown.remainingSeconds())
        monotonic=1001; assertEquals(299,countdown.remainingSeconds())
        monotonic=-100; assertEquals(300,countdown.remainingSeconds())
        monotonic=999_999; assertEquals(0,countdown.remainingSeconds())
    }
    @Test fun restoresAcrossBackgroundAndNewViewModelWithoutBackgroundTicker() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository=Repository { base+testScheduler.currentTime.milliseconds }.apply { current=session() }
        val model=FocusViewModel(ManageFocusSessions(repository),FocusClock { testScheduler.currentTime })
        try {
            runCurrent(); assertEquals("05",model.state.value.minutesLabel)
            model.foreground(true); runCurrent(); advanceTimeBy(30_000); runCurrent()
            model.foreground(false)
            val reads=repository.reconciles
            advanceTimeBy(180_000); runCurrent()
            assertEquals(reads,repository.reconciles)
            model.foreground(true); runCurrent()
            assertEquals(90,model.state.value.remainingSeconds)
            assertFalse(model.state.value.canClaim)
            model.foreground(false)
            advanceTimeBy(100_000)
            val reopened=FocusViewModel(ManageFocusSessions(repository),FocusClock { testScheduler.currentTime })
            runCurrent()
            assertTrue(reopened.state.value.canClaim)
            assertEquals("Original title",reopened.state.value.session?.title)
            assertEquals(QuestRewardPreview(10,5),reopened.state.value.preview)
        } finally { model.foreground(false); Dispatchers.resetMain() }
    }
    @Test fun localZeroNeverEnablesEarlyClaimAndOfflineNeedsReconciliation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var server=base
        val repo=Repository { server }.apply { current=session() }
        val model=FocusViewModel(ManageFocusSessions(repo),FocusClock { testScheduler.currentTime })
        try {
            runCurrent(); model.foreground(true); runCurrent()
            repo.offline=true
            advanceTimeBy(301_000); runCurrent()
            assertEquals(0,model.state.value.remainingSeconds)
            assertFalse(model.state.value.canClaim)
            model.claim(); runCurrent(); assertEquals(0,repo.claims)
            model.foreground(false)
            repo.offline=false; server=base+6.minutes
            model.retry(); runCurrent(); assertTrue(model.state.value.canClaim)
        } finally { model.foreground(false); Dispatchers.resetMain() }
    }
    @Test fun ambiguousStartAndClaimRecoverWithoutLocalGrants() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var server=base
        val repo=Repository { server }.apply { loseStartResponse=true }
        val model=FocusViewModel(ManageFocusSessions(repo),FocusClock { testScheduler.currentTime })
        try {
            runCurrent(); model.start(questId); model.start(questId); runCurrent()
            assertEquals(1,repo.starts.size)
            assertNotNull(model.state.value.session)
            assertNull(model.state.value.snapshot?.reward)
            server=base+6.minutes; model.reconcile(); runCurrent()
            repo.loseClaimResponse=true
            model.claim(); model.claim(); runCurrent()
            assertEquals(1,repo.claims)
            assertNull(model.state.value.snapshot?.reward)
            model.retry(); runCurrent()
            assertEquals(FocusStatus.CLAIMED,model.state.value.session?.status)
            assertEquals(ClaimedReward(10,5,10),model.state.value.snapshot?.reward)
            model.claim(); runCurrent(); assertEquals(1,repo.claims)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun retryReusesStartRequestAndCancelRequiresConfirmation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo=Repository { base }
        val model=FocusViewModel(ManageFocusSessions(repo),FocusClock { testScheduler.currentTime })
        try {
            runCurrent(); repo.offline=true
            model.start(questId); runCurrent(); assertNotNull(model.state.value.error)
            repo.offline=false; model.retry(); runCurrent()
            assertEquals(2,repo.starts.size); assertEquals(repo.starts[0],repo.starts[1])
            model.requestCancel(); assertTrue(model.state.value.confirmCancel)
            model.dismissCancel(); assertEquals(FocusStatus.ACTIVE,model.state.value.session?.status)
            model.requestCancel(); model.cancel(); runCurrent()
            assertEquals(FocusStatus.CANCELLED,model.state.value.session?.status)
            assertNull(model.state.value.snapshot?.reward)
            assertFalse(model.state.value.canClaim)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun reconciliationClearsAnAmbiguousStartBeforeANewExplicitSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo=Repository { base }
        val model=FocusViewModel(ManageFocusSessions(repo),FocusClock { testScheduler.currentTime })
        try {
            runCurrent(); repo.offline=true; model.start(questId); runCurrent()
            // The server committed while its response and the immediate restore were unavailable.
            repo.current=session(); repo.offline=false; model.reconcile(); runCurrent()
            model.cancel(); runCurrent(); model.done(); runCurrent()
            repo.current=null
            model.start(questId); runCurrent()
            assertEquals(2,repo.starts.size)
            assertNotEquals(repo.starts.first(),repo.starts.last())
        } finally { Dispatchers.resetMain() }
    }
    @Test fun unrelatedCompletedSessionDoesNotResolveAnAmbiguousNewStart() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo=Repository { base+6.minutes }.apply { current=session().copy(status=FocusStatus.COMPLETED) }
        val model=FocusViewModel(ManageFocusSessions(repo),FocusClock { testScheduler.currentTime })
        try {
            runCurrent(); repo.offline=true; model.start(questId); runCurrent()
            repo.offline=false; model.reconcile(); runCurrent()
            assertNull(model.state.value.session)
            assertNotNull(model.state.value.error)
            repo.current=null; model.retry(); runCurrent()
            assertEquals(2,repo.starts.size)
            assertEquals(repo.starts.first(),repo.starts.last())
        } finally { Dispatchers.resetMain() }
    }
    @Test fun partialServerAwardIsDisplayedWithoutReplacingItWithTheNominalTier() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo=Repository { base+76.minutes }.apply {
            current=session().copy(durationMinutes=75,endsAt=base+75.minutes)
            offerOverride=RewardOffer(80,10,50,"2026-11-05",3,false,true)
            nextReward=ClaimedReward(10,50,250)
        }
        val model=FocusViewModel(ManageFocusSessions(repo),FocusClock { testScheduler.currentTime })
        try {
            runCurrent()
            assertEquals(QuestRewardPreview(80,50),model.state.value.preview)
            assertEquals(10,model.state.value.snapshot?.offer?.coins)
            assertTrue(model.state.value.canClaimReward)
            model.claim(); model.claim(); runCurrent()
            assertEquals(1,repo.claims)
            assertEquals(ClaimedReward(10,50,250),model.state.value.snapshot?.reward)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun rewardClaimNeedsFreshServerOfferAndAvailableDailySlot() {
        val snapshot=FocusSnapshot(base+6.minutes,session().copy(status=FocusStatus.COMPLETED),true)
        val ready=com.pawquest.presentation.focus.FocusState(snapshot=snapshot,synced=true,busy=false)
        assertFalse(ready.canClaimReward)
        val offer=RewardOffer(10,10,5,"2026-11-05",0,false,true)
        assertTrue(ready.copy(snapshot=snapshot.copy(offer=offer)).canClaimReward)
        assertFalse(ready.copy(snapshot=snapshot.copy(offer=offer),synced=false).canClaimReward)
        assertFalse(ready.copy(snapshot=snapshot.copy(offer=offer.copy(rewardedSessions=5,limitReached=true,canClaim=false))).canClaimReward)
        assertFalse(ready.copy(snapshot=snapshot.copy(offer=offer,eligible=false)).canClaimReward)
    }
}
