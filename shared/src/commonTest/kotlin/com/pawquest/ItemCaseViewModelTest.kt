package com.pawquest

import androidx.lifecycle.ViewModelStore
import com.pawquest.domain.model.*
import com.pawquest.domain.repository.*
import com.pawquest.presentation.gacha.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ItemCaseViewModelTest {
    private val owner="fb260000-0000-0000-0000-000000000001"
    private val other="fb260000-0000-0000-0000-000000000002"
    private val version="e2400000-0000-0000-0000-000000000002"
    private val item="e2300000-0000-0000-0000-000000000005"
    private val stamp=Instant.parse("2026-11-10T05:00:00Z")
    private fun account(id: String)=UserAccount(id,null,"Test","pet","Mochi",1000,0)
    private inner class Auth : AuthenticationRepository {
        override val state=MutableStateFlow<AuthenticationState>(AuthenticationState.SignedIn(account(owner)))
        override suspend fun register(email: String,password: String)=RegistrationOutcome.SIGNED_IN
        override suspend fun login(email: String,password: String)=Unit
        override suspend fun logout(): LogoutOutcome { state.value=AuthenticationState.SignedOut; return LogoutOutcome.SIGNED_OUT }
        override suspend fun retryRestoration()=Unit
    }
    private class Journal : PendingItemCaseStore {
        val rows=mutableMapOf<String,PendingItemCase>()
        var failSave=false
        var failClear=false
        override suspend fun load(userId: String)=rows[userId]
        override suspend fun saveIfAbsent(request: PendingItemCase): PendingItemCase {
            check(!failSave)
            return rows.getOrPut(request.userId) { request }
        }
        override suspend fun clear(request: PendingItemCase) { check(!failClear); if(rows[request.userId]==request) rows.remove(request.userId) }
    }
    private fun loaded(enabled: Boolean=true)=LoadedItemCase(ItemCaseBanner(ItemCaseIds.DEFAULT_BANNER,"item_case",enabled,1),
        ItemCaseConfiguration(version,ItemCaseIds.DEFAULT_BANNER,1,100,
            ItemRarity.entries.mapIndexed { i,r -> ItemCaseTier(r,listOf(5500,3000,1200,300)[i],listOf(5,10,20,40)[i],listOf(30,60,120,240)[i]) },
            ItemRarity.entries.mapIndexed { i,r -> ItemCaseReward("e2300000-0000-0000-0000-${(i+1).toString().padStart(12,'0')}","item$i","asset$i",ItemCategory.ACCESSORY,1,r,1,true) }))
    private fun receipt(request: PendingItemCase,duplicate: Boolean=false)=ItemCaseReceipt(
        "fb270000-0000-0000-0000-000000000001",request.requestId,request.bannerId,version,1,item,"Rare glasses","acc_glasses","acc-glasses",
        ItemRarity.RARE,100,if(duplicate) ItemCaseRewardOutcome.DUPLICATE_CONVERTED else ItemCaseRewardOutcome.ITEM_GRANTED,
        if(duplicate) 0 else 1,if(duplicate) 10 else 0,900,if(duplicate) 10 else 0,stamp)

    private inner class Fixture {
        val auth=Auth()
        val journal=Journal()
        val requests=mutableListOf<PendingItemCase>()
        val committed=mutableMapOf<String,ItemCaseReceipt>()
        var duplicate=false
        var timeout=false
        var failReads=false
        var failConfiguration=false
        var enabled=true
        var coins=1000L
        var itemShards=0L
        var inventoryReads=0
        var inventoryItems=emptyList<InventoryItem>()
        var gate: CompletableDeferred<Unit>?=null
        var rejection: ItemCaseResponse.Rejected?=null
        var historyRows=emptyList<ItemCaseHistoryEntry>()
        var ledgerRows=emptyList<ShardLedgerEntry>()
        var historyFails=false
        var walletGate: CompletableDeferred<Unit>?=null
        var balanceGate: CompletableDeferred<Unit>?=null
        val cases=object : ItemCaseRepository {
            override suspend fun getHistory(): List<ItemCaseHistoryEntry> {check(!historyFails);return historyRows}
            override suspend fun getConfiguration(bannerId: String): LoadedItemCase { check(!failConfiguration); return loaded(enabled) }
            override suspend fun open(request: PendingItemCase): ItemCaseResponse {
                assertEquals(request,journal.rows[request.userId],"must persist before RPC")
                requests+=request
                gate?.await()
                rejection?.let { return it }
                val result=committed.getOrPut(request.requestId) {
                    coins=900; itemShards=if(duplicate) 10 else 0
                    if(!duplicate) inventoryItems=listOf(InventoryItem("inventory",request.userId,
                        CatalogItem(item,"acc_glasses","Rare glasses",ItemCategory.ACCESSORY,110,"acc-glasses",1,true),1,stamp))
                    receipt(request,duplicate)
                }
                if(timeout) throw IllegalStateException("network timeout after commit")
                return ItemCaseResponse.Opened(result)
            }
        }
        val shards=object : ShardRepository {
            override suspend fun getBalances(): ShardWallet {
                check(!failReads);val snapshot=itemShards
                balanceGate?.let {withContext(NonCancellable) {it.await()}}
                return ShardWallet(snapshot,0)
            }
            override suspend fun getItemLedger(): List<ShardLedgerEntry> {walletGate?.await();check(!failReads);return ledgerRows}
        }
        val pets=object : PetRepository {
            override suspend fun restore(): PetSnapshot {
                check(!failReads)
                val id=(auth.state.value as AuthenticationState.SignedIn).account.userId
                return PetSnapshot(Pet("pet",id,"Mochi",PetStats(100,100,100),PetProgress(0),stamp),coins,stamp)
            }
            override suspend fun care(action: PetCareAction,requestId: String)=restore()
        }
        val inventory=object : InventoryRepository {
            override suspend fun getInventory(): List<InventoryItem> { inventoryReads++; check(!failReads); return inventoryItems }
        }
        fun vm()=ItemCaseViewModel(cases,shards,pets,inventory,auth,journal)
    }
    private suspend fun TestScope.withModel(block: suspend TestScope.(Fixture,ItemCaseViewModel)->Unit) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f=Fixture(); val vm=f.vm(); val store=ViewModelStore().also { it.put("case",vm) }
        try { runCurrent(); block(f,vm) } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun firstAcquisitionSynchronizesExistingWalletAndInventory()=runTest {
        withModel { f,vm ->
            assertEquals(1000L,vm.state.value.coins)
            vm.openCase(); runCurrent()
            assertEquals(ItemCasePhase.ReadyToAnimate,vm.state.value.phase)
            assertEquals(item,vm.state.value.winningItemId)
            assertEquals(ItemCaseRewardOutcome.ITEM_GRANTED,vm.state.value.result?.outcome)
            assertEquals(900L,vm.state.value.coins)
            assertEquals(0L,vm.state.value.itemShards)
            assertEquals(item,vm.state.value.inventory.single().item.id)
            assertEquals(2,f.inventoryReads)
            val id=vm.state.value.result!!.transactionId
            vm.startAnimation("stale"); assertEquals(ItemCasePhase.ReadyToAnimate,vm.state.value.phase)
            vm.startAnimation(id); assertEquals(ItemCasePhase.Animating,vm.state.value.phase)
            vm.revealReward(id); vm.acknowledgeReward(); runCurrent()
            assertEquals(ItemCasePhase.Idle,vm.state.value.phase)
            assertNull(f.journal.load(owner))
        }
    }
    @Test fun duplicateKeepsOriginalWinnerAndBackendShardAward()=runTest {
        withModel { f,vm ->
            f.duplicate=true; vm.openCase(); runCurrent()
            assertEquals(item,vm.state.value.winningItemId)
            assertEquals(ItemCaseRewardOutcome.DUPLICATE_CONVERTED,vm.state.value.result?.outcome)
            assertEquals(10,vm.state.value.result?.itemShardsGranted)
            assertEquals(10L,vm.state.value.itemShards)
            assertTrue(vm.state.value.inventory.isEmpty())
        }
    }
    @Test fun latePreExchangeBalanceCannotOverwriteConfirmedRedemption()=runTest {
        withModel {f,vm->
            val gate=CompletableDeferred<Unit>();f.balanceGate=gate
            vm.refresh();runCurrent()
            f.itemShards=45;vm.refreshAfterExchange(45)
            assertEquals(45L,vm.state.value.itemShards)
            f.balanceGate=null;gate.complete(Unit);runCurrent()
            assertEquals(45L,vm.state.value.itemShards)
            assertFalse(vm.state.value.refreshing)
            assertTrue(f.requests.isEmpty())
        }
    }
    @Test fun openAgainClearsOldIntentBeforeOneExplicitNewRequestAndFailureKeepsReceipt()=runTest {
        withModel { f,vm ->
            vm.openCase(); runCurrent()
            val old=vm.state.value.result!!
            vm.revealReward(old.transactionId)
            f.journal.failClear=true
            repeat(5) {vm.openAgain()}; runCurrent()
            assertEquals(old,vm.state.value.result); assertEquals(1,f.requests.size)
            assertFalse(vm.state.value.retiring)
            f.journal.failClear=false
            repeat(5) {vm.openAgain()}; runCurrent()
            assertEquals(2,f.requests.size)
            assertNotEquals(f.requests[0].requestId,f.requests[1].requestId)
            assertEquals(f.requests[1],f.journal.load(owner))
        }
    }
    @Test fun historyAndShardWalletSupportEmptyLoadingErrorAndRefreshWithoutOpening()=runTest {
        withModel { f,vm ->
            f.historyFails=true;vm.loadHistory();assertTrue(vm.state.value.historyLoading);runCurrent()
            assertNotNull(vm.state.value.historyError)
            f.historyFails=false;vm.loadHistory();runCurrent()
            assertTrue(vm.state.value.history.isEmpty());assertNull(vm.state.value.historyError)
            f.itemShards=85
            f.ledgerRows=listOf(ShardLedgerEntry("ledger",owner,ShardCurrency.ITEM_SHARD,"request",ShardLedgerReason.ITEM_DUPLICATE,10,85,"opening",null,null,stamp))
            vm.loadShardWallet();assertTrue(vm.state.value.walletLoading);runCurrent()
            assertEquals(85L,vm.state.value.itemShards);assertEquals(10,vm.state.value.shardLedger.single().delta.toInt())
            assertNull(vm.state.value.walletError)
            f.failReads=true;vm.loadShardWallet();runCurrent()
            assertNotNull(vm.state.value.walletError);assertEquals(85L,vm.state.value.itemShards)
            assertTrue(f.requests.isEmpty())
        }
    }
    @Test fun accountSwitchClearsHistoryAndShardLedgerAndRejectsLateWalletReads()=runTest {
        withModel { f,vm ->
            f.historyRows=listOf(ItemCaseHistoryEntry("opening",item,100,10,stamp,ItemRarity.RARE,
                ItemCaseRewardOutcome.DUPLICATE_CONVERTED,"acc_glasses","acc-glasses",ItemCategory.ACCESSORY))
            vm.loadHistory();runCurrent();assertEquals(1,vm.state.value.history.size)
            f.walletGate=CompletableDeferred()
            f.ledgerRows=listOf(ShardLedgerEntry("ledger",owner,ShardCurrency.ITEM_SHARD,"request",ShardLedgerReason.ITEM_DUPLICATE,10,10,"opening",null,null,stamp))
            vm.loadShardWallet();runCurrent();assertTrue(vm.state.value.walletLoading)
            f.auth.state.value=AuthenticationState.SignedIn(account(other));runCurrent()
            f.walletGate!!.complete(Unit);runCurrent()
            assertEquals(other,vm.state.value.userId)
            assertTrue(vm.state.value.shardLedger.isEmpty());assertTrue(vm.state.value.history.isEmpty())
            assertFalse(vm.state.value.walletLoading)
        }
    }
    @Test fun animationCallbacksAndInterruptionNeverCreateAnotherOpening()=runTest {
        withModel { f,vm ->
            f.duplicate=true; vm.openCase(); runCurrent()
            val receipt=vm.state.value.result!!
            vm.revealReward("stale"); vm.startAnimation("stale")
            assertEquals(ItemCasePhase.ReadyToAnimate,vm.state.value.phase)
            vm.startAnimation(receipt.transactionId)
            repeat(5) { vm.openCase(); vm.startAnimation(receipt.transactionId) }
            assertEquals(ItemCasePhase.Animating,vm.state.value.phase)
            assertEquals(1,f.requests.size)
            // Skip/background/disposal/reload may all notify the same visual finish.
            repeat(5) { vm.revealReward(receipt.transactionId); vm.refresh() }; runCurrent()
            assertEquals(ItemCasePhase.RewardRevealed,vm.state.value.phase)
            assertEquals(receipt,vm.state.value.result)
            assertEquals(receipt.winningItemId,vm.state.value.winningItemId)
            assertEquals(10L,vm.state.value.itemShards)
            assertEquals(1,f.requests.size)
            assertNotNull(f.journal.load(owner))
            vm.acknowledgeReward(); runCurrent()
            assertEquals(1,f.requests.size) // Acknowledgment never means Open Again.
            vm.openCase(); runCurrent()
            assertEquals(2,f.requests.size)
            assertNotEquals(f.requests[0].requestId,f.requests[1].requestId)
        }
    }
    @Test fun timeoutRetryUsesExactlyTheSameKeyAndOnlyOneCommit()=runTest {
        withModel { f,vm ->
            f.timeout=true; vm.openCase(); runCurrent()
            assertEquals(ItemCasePhase.Error,vm.state.value.phase)
            val key=vm.state.value.pending!!.requestId
            vm.openCase(); assertEquals(1,f.requests.size)
            f.timeout=false; vm.retry(); runCurrent()
            assertEquals(listOf(key,key),f.requests.map { it.requestId })
            assertEquals(1,f.committed.size)
            assertEquals(900L,vm.state.value.coins)
        }
    }
    @Test fun repeatedActionsCannotSubmitWhilePendingOrRevealing()=runTest {
        withModel { f,vm ->
            f.gate=CompletableDeferred(); repeat(8) { vm.openCase(); vm.retry() }; runCurrent()
            assertEquals(1,f.requests.size)
            f.gate!!.complete(Unit); runCurrent()
            repeat(8) { vm.openCase(); vm.retry() }; runCurrent()
            assertEquals(1,f.requests.size)
        }
    }
    @Test fun failedRefreshPreservesCommittedRewardAndDoesNotReopen()=runTest {
        withModel { f,vm ->
            f.duplicate=true; f.failReads=true; vm.openCase(); runCurrent()
            assertEquals(ItemCasePhase.ReadyToAnimate,vm.state.value.phase)
            assertEquals(10L,vm.state.value.itemShards)
            assertNotNull(vm.state.value.synchronizationError)
            f.failReads=false; vm.retry(); runCurrent()
            assertEquals(1,f.requests.size)
            assertNull(vm.state.value.synchronizationError)
        }
    }
    @Test fun persistenceFailurePreventsAnyBackendRequest()=runTest {
        withModel { f,vm ->
            f.journal.failSave=true; vm.openCase(); runCurrent()
            val pending=vm.state.value.pending
            assertTrue(f.requests.isEmpty())
            f.journal.failSave=false; vm.retry(); runCurrent()
            assertEquals(pending,f.requests.single())
        }
    }
    @Test fun acknowledgmentFailureCannotCreateAnotherOpening()=runTest {
        withModel { f,vm ->
            vm.openCase(); runCurrent()
            val id=vm.state.value.result!!.transactionId
            vm.revealReward(id); f.journal.failClear=true
            vm.acknowledgeReward(); runCurrent(); vm.openCase()
            assertEquals(1,f.requests.size)
            assertEquals(ItemCasePhase.RewardRevealed,vm.state.value.phase)
            assertNotNull(vm.state.value.pending)
            f.journal.failClear=false; vm.acknowledgeReward(); runCurrent()
            assertEquals(ItemCasePhase.Idle,vm.state.value.phase)
        }
    }
    @Test fun initialReadFailureDoesNotDisplayInventedZeroAndCanRetry()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f=Fixture().also { it.failReads=true }
        val vm=f.vm(); val store=ViewModelStore().also { it.put("case",vm) }
        try {
            runCurrent()
            assertEquals(ItemCasePhase.Error,vm.state.value.phase)
            assertNull(vm.state.value.itemShards)
            assertNull(vm.state.value.coins)
            f.failReads=false; vm.retry(); runCurrent()
            assertEquals(ItemCasePhase.Idle,vm.state.value.phase)
            assertEquals(0L,vm.state.value.itemShards)
            assertTrue(f.requests.isEmpty())
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun rejectionRequiresExplicitDismissalAndBusyCannotDiscardKey()=runTest {
        withModel { f,vm ->
            f.rejection=ItemCaseResponse.Rejected(ItemCaseRejection.BUSY,1)
            vm.openCase(); runCurrent(); val key=vm.state.value.pending
            vm.dismissRejection(); runCurrent(); assertEquals(key,vm.state.value.pending)
            f.rejection=ItemCaseResponse.Rejected(ItemCaseRejection.INSUFFICIENT_COINS)
            vm.retry(); runCurrent(); vm.dismissRejection(); runCurrent()
            assertNull(vm.state.value.pending)
            assertEquals(1,f.requests.map { it.requestId }.distinct().size)
        }
    }
    @Test fun processRestorationRecoversBeforeLoadingDisabledConfiguration()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f=Fixture(); val old=f.vm(); val first=ViewModelStore().also { it.put("case",old) }
        var second: ViewModelStore?=null
        try {
            runCurrent(); f.timeout=true; old.openCase(); runCurrent()
            val pending=old.state.value.pending!!; first.clear()
            f.timeout=false; f.enabled=false; f.failConfiguration=true
            val restored=f.vm(); second=ViewModelStore().also { it.put("case",restored) }; runCurrent()
            assertEquals(pending,restored.state.value.pending)
            assertEquals(ItemCasePhase.ReadyToAnimate,restored.state.value.phase)
            assertEquals(1,f.committed.size)
            assertEquals(1,f.requests.map { it.requestId }.distinct().size)
        } finally { first.clear(); second?.clear(); Dispatchers.resetMain() }
    }
    @Test fun accountSwitchCannotExposeAnotherAccountsPendingOrResult()=runTest {
        withModel { f,vm ->
            f.gate=CompletableDeferred(); vm.openCase(); runCurrent()
            f.auth.state.value=AuthenticationState.SignedIn(account(other)); runCurrent()
            assertNull(vm.state.value.pending); assertNull(vm.state.value.result)
            assertEquals(other,vm.state.value.userId)
            assertNotNull(f.journal.rows[owner])
            f.gate=null; f.auth.state.value=AuthenticationState.SignedIn(account(owner)); runCurrent()
            assertEquals(ItemCasePhase.ReadyToAnimate,vm.state.value.phase)
            assertEquals(1,f.requests.map { it.requestId }.distinct().size)
        }
    }
}
