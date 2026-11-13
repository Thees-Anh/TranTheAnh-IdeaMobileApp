package com.pawquest.presentation.gacha

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pawquest.domain.model.*
import com.pawquest.domain.repository.*
import com.pawquest.domain.usecase.OpenItemCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.uuid.Uuid

enum class ItemCasePhase { Idle, Loading, ReadyToAnimate, Animating, RewardRevealed, Error }
data class ItemCaseState(
    val phase: ItemCasePhase=ItemCasePhase.Idle,
    val userId: String?=null,
    val case: LoadedItemCase?=null,
    val coins: Long?=null,
    val shards: ShardWallet?=null,
    val itemShards: Long?=null,
    val inventory: List<InventoryItem> = emptyList(),
    val inventoryAvailable: Boolean=false,
    val pending: PendingItemCase?=null,
    val result: ItemCaseReceipt?=null,
    val rejection: ItemCaseResponse.Rejected?=null,
    val error: String?=null,
    val synchronizationError: String?=null,
    val refreshing: Boolean=false,
    val history: List<ItemCaseHistoryEntry> = emptyList(),
    val historyLoading: Boolean=false,
    val historyError: String?=null,
    val shardLedger: List<ShardLedgerEntry> = emptyList(),
    val walletLoading: Boolean=false,
    val walletError: String?=null,
    val retiring: Boolean=false,
) {
    val winningItemId: String? get()=result?.winningItemId
}

/** Financial recovery and visual state are independent. A confirmed receipt is
 * never discarded due to failed reads or animation, and only acknowledgment
 * retires its durable request. Never automatically generate another opening. */
class ItemCaseViewModel(
    private val cases: ItemCaseRepository,
    private val shards: ShardRepository,
    private val pets: PetRepository,
    private val inventory: InventoryRepository,
    private val authentication: AuthenticationRepository,
    private val journal: PendingItemCaseStore,
    private val bannerId: String=ItemCaseIds.DEFAULT_BANNER,
    private val openItemCase: OpenItemCase=OpenItemCase(cases),
) : ViewModel() {
    private val mutable=MutableStateFlow(ItemCaseState())
    val state=mutable.asStateFlow()
    private var job: Job?=null
    private var epoch=0
    private var historyJob: Job?=null
    private var walletJob: Job?=null
    private var walletRevision=0
    private var balanceRevision=0
    init {
        viewModelScope.launch {
            authentication.state.map { (it as? AuthenticationState.SignedIn)?.account?.userId }.distinctUntilChanged().collect { owner ->
                epoch++; job?.cancel(); historyJob?.cancel(); walletJob?.cancel()
                mutable.value=ItemCaseState(userId=owner)
                if(owner!=null) initialize()
            }
        }
    }
    fun loadShardWallet() {
        val owner=state.value.userId ?: return
        if(walletJob?.isActive==true) return
        val revision=epoch
        val walletRead=++walletRevision
        mutable.value=state.value.copy(walletLoading=true,walletError=null)
        walletJob=viewModelScope.launch {
            fun current()=valid(owner,revision) && walletRead==walletRevision
            var failed=false
            try {
                val balance=shards.getBalances()
                if(current()) mutable.value=state.value.copy(shards=balance,itemShards=balance.itemShards)
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) {failed=true}
            try {
                val ledger=shards.getItemLedger()
                check(ledger.all { it.userId==owner && it.currency==ShardCurrency.ITEM_SHARD })
                if(current()) mutable.value=state.value.copy(shardLedger=ledger)
            } catch(cancelled: CancellationException) {throw cancelled}
            catch(_: Exception) {failed=true}
            if(current()) mutable.value=state.value.copy(walletLoading=false,
                walletError=if(failed) "Shard Wallet couldn't fully refresh." else null)
        }
    }
    fun loadHistory() {
        val owner=state.value.userId ?: return
        if(historyJob?.isActive==true) return
        val revision=epoch
        mutable.value=state.value.copy(historyLoading=true,historyError=null)
        historyJob=viewModelScope.launch {
            try {
                val rows=cases.getHistory()
                if(valid(owner,revision)) mutable.value=state.value.copy(history=rows,historyLoading=false)
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { if(valid(owner,revision)) mutable.value=state.value.copy(historyLoading=false,historyError="Opening history couldn't load.") }
        }
    }
    private fun valid(owner: String,revision: Int)=epoch==revision && state.value.userId==owner &&
        (authentication.state.value as? AuthenticationState.SignedIn)?.account?.userId==owner
    private fun initialize() {
        val owner=state.value.userId ?: return
        val revision=epoch
        mutable.value=state.value.copy(phase=ItemCasePhase.Loading)
        job=viewModelScope.launch {
            try {
                val pending=journal.load(owner)
                check(pending==null || pending.userId==owner)
                if(!valid(owner,revision)) return@launch
                mutable.value=state.value.copy(pending=pending)
                if(pending!=null) submit(pending,revision) else {
                    synchronize(owner,revision)
                    if(valid(owner,revision)) mutable.value=state.value.copy(phase=if(state.value.error==null) ItemCasePhase.Idle else ItemCasePhase.Error)
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { if(valid(owner,revision)) mutable.value=state.value.copy(phase=ItemCasePhase.Error,error="Couldn't restore Mystery Box data. Retry before opening.") }
        }
    }
    fun openCase() {
        val current=state.value
        val owner=current.userId ?: return
        if(job?.isActive==true || current.pending!=null || current.result!=null || current.phase!=ItemCasePhase.Idle || current.case?.banner?.enabled!=true) return
        val request=PendingItemCase(owner,bannerId,Uuid.random().toString())
        walletRevision++; walletJob?.cancel()
        mutable.value=current.copy(phase=ItemCasePhase.Loading,pending=request,error=null,rejection=null,walletLoading=false)
        launchSubmit(request)
    }
    private fun launchSubmit(request: PendingItemCase) {
        val revision=epoch
        mutable.value=state.value.copy(phase=ItemCasePhase.Loading,error=null,rejection=null)
        job=viewModelScope.launch {
            try {
                // The CAS also reconciles a request saved by an earlier instance.
                val saved=journal.saveIfAbsent(request)
                check(saved.userId==request.userId)
                if(!valid(saved.userId,revision)) return@launch
                mutable.value=state.value.copy(pending=saved)
                submit(saved,revision)
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { uncertain(request.userId,revision) }
        }
    }
    private suspend fun submit(request: PendingItemCase,revision: Int) {
        try {
            when(val response=openItemCase(request)) {
                is ItemCaseResponse.Opened -> {
                    val receipt=response.receipt
                    check(receipt.bannerId.equals(request.bannerId,true) && receipt.requestId.equals(request.requestId,true))
                    if(!valid(request.userId,revision)) return
                    mutable.value=state.value.copy(phase=ItemCasePhase.ReadyToAnimate,result=receipt,error=null,rejection=null,
                        coins=receipt.coinsBalanceAfter,itemShards=receipt.itemShardsBalanceAfter,
                        shards=state.value.shards?.copy(itemShards=receipt.itemShardsBalanceAfter))
                    synchronize(request.userId,revision)
                }
                is ItemCaseResponse.Rejected -> if(valid(request.userId,revision)) mutable.value=state.value.copy(
                    phase=ItemCasePhase.Error,rejection=response,error="Mystery Box opening was not confirmed: ${response.reason.name}.")
            }
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { uncertain(request.userId,revision) }
    }
    private fun uncertain(owner: String,revision: Int) {
        if(valid(owner,revision)) mutable.value=state.value.copy(phase=ItemCasePhase.Error,
            error="Couldn't confirm your opening. Retry to recover the same request safely.",rejection=null)
    }
    /** Reuses the original key; confirmed-result retries only refresh reads. */
    fun retry() {
        if(job?.isActive==true) return
        if(state.value.result!=null) { refresh(); return }
        state.value.pending?.let(::launchSubmit) ?: initialize()
    }
    fun refresh() {
        if(job?.isActive==true) return
        val owner=state.value.userId ?: return
        if(state.value.pending!=null && state.value.result==null) { retry(); return }
        val revision=epoch
        job=viewModelScope.launch { synchronize(owner,revision) }
    }
    /** Invalidates pre-redemption reads without cancelling an uncertain opening. */
    fun refreshAfterExchange(balanceAfter: Long) {
        require(balanceAfter>=0)
        val owner=state.value.userId ?: return
        val revision=epoch
        balanceRevision++;walletRevision++;walletJob?.cancel()
        mutable.value=state.value.copy(itemShards=balanceAfter,shards=state.value.shards?.copy(itemShards=balanceAfter),walletLoading=false)
        val active=job
        viewModelScope.launch {
            active?.join()
            if(valid(owner,revision)) {refresh();loadShardWallet()}
        }
    }
    private suspend fun synchronize(owner: String,revision: Int) {
        if(!valid(owner,revision)) return
        val readVersion=balanceRevision
        mutable.value=state.value.copy(refreshing=true,synchronizationError=null)
        var failed=false
        suspend fun <T> read(fetch: suspend ()->T,apply: (T)->Unit) {
            try { val value=fetch(); if(valid(owner,revision) && readVersion==balanceRevision) apply(value) }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { failed=true }
        }
        read({ pets.restore().also { check(it.pet.userId==owner) } }) { mutable.value=state.value.copy(coins=it.coins) }
        read({ shards.getBalances() }) { mutable.value=state.value.copy(shards=it,itemShards=it.itemShards) }
        mutable.value=state.value.copy(inventoryAvailable=false)
        read({ inventory.getInventory().also { check(it.all { row -> row.userId==owner }) } }) { mutable.value=state.value.copy(inventory=it,inventoryAvailable=true) }
        read({ cases.getConfiguration(bannerId) }) { mutable.value=state.value.copy(case=it) }
        if(valid(owner,revision) && readVersion==balanceRevision) mutable.value=state.value.copy(refreshing=false,
            synchronizationError=if(failed) "Some balances or inventory couldn't refresh. Retry synchronization." else null,
            error=if(failed && state.value.result==null) "Couldn't load Mystery Box data. Retry." else if(state.value.result==null) null else state.value.error)
    }
    fun startAnimation(transactionId: String) {
        if(state.value.result?.transactionId==transactionId && state.value.phase==ItemCasePhase.ReadyToAnimate)
            mutable.value=state.value.copy(phase=ItemCasePhase.Animating)
    }
    fun revealReward(transactionId: String) {
        if(state.value.result?.transactionId==transactionId && state.value.phase in setOf(ItemCasePhase.ReadyToAnimate,ItemCasePhase.Animating))
            mutable.value=state.value.copy(phase=ItemCasePhase.RewardRevealed)
    }
    fun acknowledgeReward() {
        if(state.value.phase==ItemCasePhase.RewardRevealed) retire()
    }
    /** Explicit new purchase only after the previous receipt is durably retired. */
    fun openAgain() {
        val current=state.value
        val config=current.case?.configuration ?: return
        if(current.phase!=ItemCasePhase.RewardRevealed || current.case?.banner?.enabled!=true ||
            current.refreshing || (current.coins ?: -1)<config.priceCoins) return
        retire(openAgain=true)
    }
    fun dismissRejection() {
        if(state.value.rejection?.reason?.canDismiss==true) retire()
    }
    private fun retire(openAgain: Boolean=false) {
        if(job?.isActive==true || state.value.retiring) return
        val pending=state.value.pending ?: return
        val revision=epoch
        mutable.value=state.value.copy(retiring=true)
        job=viewModelScope.launch {
            try {
                journal.clear(pending)
                if(valid(pending.userId,revision)) {
                    mutable.value=state.value.copy(phase=ItemCasePhase.Idle,pending=null,result=null,rejection=null,error=null,retiring=false)
                    if(openAgain) { job=null; openCase() }
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { if(valid(pending.userId,revision)) mutable.value=state.value.copy(retiring=false,synchronizationError="Couldn't save acknowledgment. Retry before another opening.") }
        }
    }
}
