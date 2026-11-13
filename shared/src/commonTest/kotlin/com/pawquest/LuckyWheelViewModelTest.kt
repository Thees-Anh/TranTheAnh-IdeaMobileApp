package com.pawquest

import com.pawquest.domain.model.*
import com.pawquest.domain.repository.*
import com.pawquest.data.repository.UnconfiguredPetRepository
import com.pawquest.presentation.gacha.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class LuckyWheelViewModelTest {
    private interface Backend : LuckyWheelRepository, PetShardExchangeRepository
    private val owner="ab280000-0000-0000-0000-000000000001"
    private val id="ab280000-0000-0000-0000-000000000002"
    private val stamp="2026-11-10T10:00:00Z"
    private fun config()=LuckyWheelConfiguration(LuckyWheelViewModel.BANNER,true,id,1,100,1,50,
        WheelSector.entries.mapIndexed {i,s->WheelSectorConfiguration(s,i,listOf(2500,2000,1800,1200,1000,800,500,200)[i],1,
            if(i==0) "e1000000-0000-0000-0000-000000000001" else null)})
    private inner class Auth: AuthenticationRepository {
        override val state=MutableStateFlow<AuthenticationState>(AuthenticationState.SignedIn(UserAccount(owner,null,"Test","pet","Mochi",1000,0)))
        override suspend fun register(email: String,password: String)=RegistrationOutcome.SIGNED_IN
        override suspend fun login(email: String,password: String)=Unit
        override suspend fun logout(): LogoutOutcome {state.value=AuthenticationState.SignedOut;return LogoutOutcome.SIGNED_OUT}
        override suspend fun retryRestoration()=Unit
    }
    private class Journal: PendingLuckyOperationStore {
        var row: PendingLuckyOperation?=null
        var fail=false
        override suspend fun load(owner: String)=row?.takeIf {it.userId==owner}
        override suspend fun save(request: PendingLuckyOperation): PendingLuckyOperation {
            check(!fail)
            val prior=row
            if(prior==null || prior.kind==request.kind && prior.requestId==request.requestId && !prior.confirmed && request.confirmed) row=request
            return checkNotNull(row)
        }
        override suspend fun clear(request: PendingLuckyOperation) {if(row==request) row=null}
    }
    private inner class Fixture {
        val auth=Auth();val journal=Journal();var balances=LuckyWheelBalances(1000,1,100)
        val requests=mutableListOf<LuckyWheelSpinRequest>();val commits=mutableMapOf<String,LuckyWheelReceipt>()
        var timeout=false;var gate: CompletableDeferred<Unit>?=null;var reads=0;var petReads=0;var grants=0;var redemptionCalls=0
        var reward=WheelReward.PET_UNLOCKED;var selected="puppy";var owned=false;var failReads=false
        val backend=object : Backend {
            override suspend fun configuration(bannerId: String)=config()
            override suspend fun balances(): LuckyWheelBalances {check(!failReads);return balances}
            override suspend fun spin(request: LuckyWheelSpinRequest,expectedOwner: String): LuckyWheelResponse {
                check(journal.row?.requestId==request.requestId);requests+=request;gate?.await()
                val result=commits.getOrPut(request.requestId) {
                    grants++;balances=LuckyWheelBalances(if(request.payment==WheelPayment.COINS) 900 else 1000,if(request.payment==WheelPayment.TICKET) 0 else 1,if(reward==WheelReward.PET_UNLOCK_CONVERTED_TO_SHARDS) 150 else 100)
                    LuckyWheelReceipt(id,request.requestId,request.bannerId,id,1,WheelSector.PET_UNLOCK,request.payment,
                        if(request.payment==WheelPayment.COINS) 100 else 0,if(request.payment==WheelPayment.TICKET) 1 else 0,
                        reward,null,0,if(reward==WheelReward.PET_UNLOCKED) selected else null,
                        if(reward==WheelReward.PET_UNLOCK_CONVERTED_TO_SHARDS) 50 else 0,0,0,balances.coins,balances.petShards,balances.tickets,stamp)
                }
                if(timeout) error("Lost committed response")
                return LuckyWheelResponse.Spun(result)
            }
            override suspend fun history(beforeCreatedAt: String?,beforeTransactionId: String?,limit: Int)=commits.values.toList()
            override suspend fun catalog()=listOf(PetShardCatalogEntry(id,"rabbit",50,"Rabbit","rabbit",owned))
            override suspend fun quote(request: PetShardRequest,expectedOwner: String)=PetShardResponse.Quoted(PetShardQuote(request.requestId,id,request.speciesCode,50,stamp))
            override suspend fun redeem(request: PetShardRequest,expectedOwner: String): PetShardResponse {
                check(journal.row?.confirmed==true);redemptionCalls++;owned=true;balances=balances.copy(petShards=50)
                return PetShardResponse.Redeemed(PetShardReceipt(id,request.requestId,id,request.speciesCode,50,50,stamp))
            }
        }
        val inventory=object : InventoryRepository {override suspend fun getInventory(): List<InventoryItem> {reads++;return emptyList()}}
        val pets=object : PetRepository by UnconfiguredPetRepository() {override suspend fun collection(): PetCollection {petReads++;return PetCollection("mochi_cat",1,emptyList())}}
        fun model()=LuckyWheelViewModel(backend,backend,auth,journal,inventory,pets)
    }
    private fun checkTest(block: suspend TestScope.()->Unit)=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));try {block()} finally {Dispatchers.resetMain()}
    }
    @Test fun confirmedCoinsSpinAnimationAndExplicitNextOpening()=checkTest {
        val f=Fixture();val vm=f.model();runCurrent();val before=f.reads
        vm.spin(WheelPayment.COINS);vm.spin(WheelPayment.COINS);runCurrent()
        assertEquals(1,f.requests.size);assertEquals(1,f.grants);assertEquals(LuckyWheelPhase.ReadyToAnimate,vm.state.value.phase)
        assertEquals(900,vm.state.value.balances!!.coins);assertTrue(f.reads>before && f.petReads>1)
        vm.animationStarted(id);assertEquals(LuckyWheelPhase.Animating,vm.state.value.phase)
        vm.animationFinished("wrong");assertEquals(LuckyWheelPhase.Animating,vm.state.value.phase)
        vm.animationFinished(id);assertEquals(LuckyWheelPhase.RewardRevealed,vm.state.value.phase)
        vm.spin(WheelPayment.TICKET);assertEquals(1,f.requests.size)
        vm.dismiss();runCurrent();assertEquals(1,f.requests.size);assertNull(f.journal.row)
        vm.spin(WheelPayment.TICKET);runCurrent();assertEquals(2,f.requests.size);assertNotEquals(f.requests[0].requestId,f.requests[1].requestId)
    }
    @Test fun lostResponseRetriesSameTicketPaymentAndRestoresConfirmedResultWithoutReplayAnimation()=checkTest {
        val f=Fixture();val vm=f.model();runCurrent();f.timeout=true
        vm.spin(WheelPayment.TICKET);runCurrent();assertEquals(LuckyWheelPhase.Error,vm.state.value.phase)
        val key=checkNotNull(f.journal.row).requestId;assertFalse(vm.state.value.canDismiss)
        vm.dismiss();vm.spin(WheelPayment.COINS);assertEquals(1,f.requests.size)
        f.timeout=false;val restored=f.model();runCurrent()
        assertEquals(key,f.requests.last().requestId);assertEquals(WheelPayment.TICKET,f.requests.last().payment)
        assertEquals(1,f.grants);assertEquals(LuckyWheelPhase.RewardRevealed,restored.state.value.phase)
        assertEquals(0,restored.state.value.balances!!.tickets)
    }
    @Test fun fallbackPreservesPetUnlockAndSkipNeverCallsBackend()=checkTest {
        val f=Fixture();f.reward=WheelReward.PET_UNLOCK_CONVERTED_TO_SHARDS;val vm=f.model();runCurrent()
        vm.spin(WheelPayment.COINS);runCurrent();vm.skip()
        assertEquals(LuckyWheelPhase.RewardRevealed,vm.state.value.phase);assertEquals(1,f.grants)
        assertEquals(WheelSector.PET_UNLOCK,vm.state.value.result!!.originalSector);assertNull(vm.state.value.result!!.selectedPetId)
        assertEquals(150,vm.state.value.balances!!.petShards);assertEquals(50,vm.state.value.result!!.petShardsEarned)
    }
    @Test fun petQuoteRequiresConfirmationThenRefreshesOwnershipAndShardBalance()=checkTest {
        val f=Fixture();val vm=f.model();runCurrent();val reads=f.petReads
        vm.selectPet("rabbit");runCurrent();assertNotNull(vm.state.value.quote);assertEquals(0,f.redemptionCalls)
        vm.spin(WheelPayment.COINS);assertEquals(0,f.requests.size)
        vm.confirmPet();vm.confirmPet();runCurrent();assertEquals(1,f.redemptionCalls)
        assertEquals(50,vm.state.value.balances!!.petShards);assertTrue(vm.state.value.pets.single().owned && f.petReads>reads)
        assertNotNull(vm.state.value.redemption);assertTrue(vm.state.value.canDismiss)
    }
    @Test fun accountSwitchDiscardsLateResultAndJournalFailurePreventsDispatch()=checkTest {
        val f=Fixture();val vm=f.model();runCurrent();f.journal.fail=true
        vm.spin(WheelPayment.COINS);runCurrent();assertTrue(f.requests.isEmpty())
        f.auth.logout();runCurrent();assertNull(vm.state.value.owner);assertNull(vm.state.value.result)
    }
    @Test fun pendingNetworkCallBlocksRepeatedTapsAndHistoryIsReadOnly()=checkTest {
        val f=Fixture();val vm=f.model();runCurrent();f.gate=CompletableDeferred()
        vm.spin(WheelPayment.COINS);runCurrent();repeat(5) {vm.spin(WheelPayment.TICKET)}
        assertEquals(1,f.requests.size);f.gate!!.complete(Unit);runCurrent()
        vm.loadHistory();runCurrent();assertEquals(1,vm.state.value.history.size);assertEquals(1,f.requests.size)
    }
    @Test fun signingOutWhileRequestIsPendingCannotPublishAnotherAccountsResult()=checkTest {
        val f=Fixture();val vm=f.model();runCurrent();f.gate=CompletableDeferred()
        vm.spin(WheelPayment.COINS);runCurrent();val saved=f.journal.row
        f.auth.logout();runCurrent();f.gate!!.complete(Unit);runCurrent()
        assertNull(vm.state.value.owner);assertNull(vm.state.value.result);assertEquals(saved,f.journal.row)
    }
}
