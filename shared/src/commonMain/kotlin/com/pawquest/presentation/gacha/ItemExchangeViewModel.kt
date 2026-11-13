package com.pawquest.presentation.gacha

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pawquest.domain.model.*
import com.pawquest.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.uuid.Uuid

data class ItemExchangeState(val userId: String?=null,val busy: Boolean=false,val pending: PendingItemExchange?=null,
    val quote: ItemExchangeQuote?=null,val receipt: ItemExchangeReceipt?=null,val balance: Long?=null,
    val inventory: List<InventoryItem> = emptyList(),val ownershipAvailable: Boolean=false,
    val rejection: ItemExchangeRejection?=null,val error: String?=null,val syncError: Boolean=false)

class ItemExchangeViewModel(private val shards: ShardRepository,private val inventory: InventoryRepository,
    private val auth: AuthenticationRepository,private val journal: PendingItemExchangeStore): ViewModel() {
    private val mutable=MutableStateFlow(ItemExchangeState())
    val state=mutable.asStateFlow()
    private var job: Job?=null
    private var epoch=0
    init {viewModelScope.launch {
        auth.state.map {(it as? AuthenticationState.SignedIn)?.account?.userId}.distinctUntilChanged().collect {owner ->
            epoch++;job?.cancel();mutable.value=ItemExchangeState(userId=owner)
            if(owner!=null) {
                val revision=epoch
                job=viewModelScope.launch {
                    mutable.value=state.value.copy(busy=true)
                    try {
                        val pending=journal.load(owner);check(pending==null || pending.userId==owner)
                        if(!valid(owner,revision)) return@launch
                        mutable.value=state.value.copy(pending=pending)
                        if(pending!=null) submit(pending,revision) else synchronize(owner,revision)
                    } catch(cancelled: CancellationException) {throw cancelled}
                    catch(_: Exception) {if(valid(owner,revision)) mutable.value=state.value.copy(error="Exchange recovery failed. Retry safely.")}
                    finally {if(valid(owner,revision)) mutable.value=state.value.copy(busy=false)}
                }
            }
        }
    }}
    private fun valid(owner: String,revision: Int)=epoch==revision && state.value.userId==owner &&
        (auth.state.value as? AuthenticationState.SignedIn)?.account?.userId==owner
    fun refresh() {
        if(job?.isActive==true) return
        if(state.value.pending!=null && state.value.receipt==null) {retry();return}
        val owner=state.value.userId ?: return;val revision=epoch
        mutable.value=state.value.copy(busy=true)
        job=viewModelScope.launch {try {synchronize(owner,revision)} finally {if(valid(owner,revision)) mutable.value=state.value.copy(busy=false)}}
    }
    fun select(itemId: String) {
        if(job?.isActive==true || state.value.pending!=null || state.value.busy) return
        val owner=state.value.userId ?: return
        launchRequest(PendingItemExchange(owner,itemId,Uuid.random().toString()))
    }
    fun confirm() {
        val current=state.value
        if(current.busy || current.quote==null || current.receipt!=null) return
        val pending=current.pending ?: return
        launchRequest(pending.copy(confirmed=true))
    }
    fun retry() {
        if(job?.isActive==true) return
        if(state.value.receipt!=null) {refresh();return}
        state.value.pending?.let(::launchRequest) ?: refresh()
    }
    private fun launchRequest(request: PendingItemExchange) {
        if(job?.isActive==true) return
        val revision=epoch
        mutable.value=state.value.copy(busy=true,error=null,rejection=null,pending=request)
        job=viewModelScope.launch {
            try {
                // Persist confirmation BEFORE redeeming; unknown retries keep the same key.
                val saved=journal.save(request);check(saved.userId==request.userId)
                if(valid(saved.userId,revision)) {mutable.value=state.value.copy(pending=saved);submit(saved,revision)}
            } catch(cancelled: CancellationException) {throw cancelled}
            catch(_: Exception) {if(valid(request.userId,revision)) mutable.value=state.value.copy(error="Exchange recovery failed. Retry safely.")}
            finally {if(valid(request.userId,revision)) mutable.value=state.value.copy(busy=false)}
        }
    }
    private suspend fun submit(request: PendingItemExchange,revision: Int) {
        val response=if(request.confirmed) shards.redeemItem(request) else shards.quoteItem(request)
        if(!valid(request.userId,revision)) return
        when(response) {
            is ItemExchangeResponse.Quoted -> {
                check(!request.confirmed && response.quote.itemId==request.itemId && response.quote.requestId==request.requestId)
                mutable.value=state.value.copy(quote=response.quote,error=null)
            }
            is ItemExchangeResponse.Redeemed -> {
                check(response.receipt.itemId==request.itemId && response.receipt.requestId==request.requestId)
                mutable.value=state.value.copy(receipt=response.receipt,quote=null,balance=response.receipt.balanceAfter,error=null)
                synchronize(request.userId,revision)
            }
            is ItemExchangeResponse.Rejected -> mutable.value=state.value.copy(rejection=response.reason,quote=null)
        }
    }
    private suspend fun synchronize(owner: String,revision: Int) {
        var failed=false
        try {val balance=shards.getBalances();if(valid(owner,revision)) mutable.value=state.value.copy(balance=balance.itemShards)}
        catch(cancelled: CancellationException) {throw cancelled} catch(_: Exception) {failed=true}
        try {
            val items=inventory.getInventory();check(items.all {it.userId==owner})
            if(valid(owner,revision)) mutable.value=state.value.copy(inventory=items,ownershipAvailable=true)
        } catch(cancelled: CancellationException) {throw cancelled} catch(_: Exception) {failed=true}
        if(valid(owner,revision)) mutable.value=state.value.copy(syncError=failed)
    }
    fun dismiss() {
        if(job?.isActive==true || state.value.busy) return
        val current=state.value;val pending=current.pending ?: return
        // Unknown confirmed transactions can NEVER be discarded as ordinary errors.
        if(pending.confirmed && current.receipt==null && current.rejection?.canDismiss!=true) return
        val revision=epoch
        mutable.value=current.copy(busy=true)
        job=viewModelScope.launch {
            try {
                journal.clear(pending)
                if(valid(pending.userId,revision)) mutable.value=state.value.copy(pending=null,quote=null,receipt=null,error=null,rejection=null)
            } catch(cancelled: CancellationException) {throw cancelled}
            catch(_: Exception) {if(valid(pending.userId,revision)) mutable.value=state.value.copy(error="Exchange recovery failed. Retry safely.")}
            finally {if(valid(pending.userId,revision)) mutable.value=state.value.copy(busy=false)}
        }
    }
}
