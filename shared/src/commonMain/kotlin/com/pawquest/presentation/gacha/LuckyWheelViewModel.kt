package com.pawquest.presentation.gacha

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pawquest.domain.model.*
import com.pawquest.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.uuid.Uuid

enum class LuckyWheelPhase { Idle, Loading, ReadyToAnimate, Animating, RewardRevealed, Error }
data class LuckyWheelState(
    val owner: String?=null,val phase: LuckyWheelPhase=LuckyWheelPhase.Idle,val busy: Boolean=false,
    val configuration: LuckyWheelConfiguration?=null,val balances: LuckyWheelBalances?=null,
    val pending: PendingLuckyOperation?=null,val result: LuckyWheelReceipt?=null,val quote: PetShardQuote?=null,
    val redemption: PetShardReceipt?=null,val pets: List<PetShardCatalogEntry> = emptyList(),
    val error: String?=null,val syncError: Boolean=false,val rejection: LuckyWheelRejection?=null,
    val skipRequested: Boolean=false,val history: List<LuckyWheelReceipt> = emptyList(),
    val historyLoading: Boolean=false,val historyError: Boolean=false,val historyEnd: Boolean=false,
) {
    val hasReceipt get()=result!=null || redemption!=null
    val canDismiss get()=!busy && (pending==null || hasReceipt || pending.confirmed==false || rejection in setOf(
        LuckyWheelRejection.INVALID_REQUEST,LuckyWheelRejection.BANNER_UNAVAILABLE,LuckyWheelRejection.CONFIGURATION_UNAVAILABLE,
        LuckyWheelRejection.INSUFFICIENT_COINS,LuckyWheelRejection.INSUFFICIENT_TICKETS,LuckyWheelRejection.INVENTORY_FULL,
        LuckyWheelRejection.PET_UNAVAILABLE,LuckyWheelRejection.ALREADY_OWNED,LuckyWheelRejection.INSUFFICIENT_SHARDS,
        LuckyWheelRejection.QUOTE_REQUIRED,LuckyWheelRejection.QUOTE_EXPIRED,LuckyWheelRejection.PRICE_CHANGED,
        LuckyWheelRejection.PET_NEEDS_CARE))
    val canSpin get()=!busy && pending==null && result==null && redemption==null && configuration?.enabled==true && balances!=null
}

/** One durable intent covers Wheel and Pet Exchange. Callbacks only reveal an already committed receipt. */
class LuckyWheelViewModel(private val wheel: LuckyWheelRepository,private val exchange: PetShardExchangeRepository,
    private val auth: AuthenticationRepository,private val journal: PendingLuckyOperationStore,
    private val inventory: InventoryRepository,private val pets: PetRepository): ViewModel() {
    private val mutable=MutableStateFlow(LuckyWheelState())
    val state=mutable.asStateFlow()
    private var job: Job?=null
    private var epoch=0
    init {viewModelScope.launch {
        auth.state.map {(it as? AuthenticationState.SignedIn)?.account?.userId}.distinctUntilChanged().collect {owner ->
            epoch++;job?.cancel();mutable.value=LuckyWheelState(owner=owner)
            if(owner!=null) run(owner) {
                val pending=journal.load(owner);check(pending==null || pending.userId==owner)
                if(valid(owner,it)) {
                    mutable.value=state.value.copy(pending=pending)
                    if(pending!=null) submit(pending,it,restoring=true) else synchronize(owner,it)
                }
            }
        }
    }}
    private fun valid(owner: String,revision: Int)=revision==epoch && state.value.owner==owner &&
        (auth.state.value as? AuthenticationState.SignedIn)?.account?.userId==owner
    private fun run(owner: String,block: suspend (Int)->Unit) {
        if(job?.isActive==true) return
        val revision=epoch
        mutable.value=state.value.copy(busy=true,error=null)
        job=viewModelScope.launch {
            try {block(revision)} catch(cancelled: CancellationException) {throw cancelled}
            catch(_: Exception) {if(valid(owner,revision)) mutable.value=state.value.copy(phase=if(state.value.hasReceipt) state.value.phase else LuckyWheelPhase.Error,
                error="Couldn't confirm the operation. Retry the same request safely.")}
            finally {if(valid(owner,revision)) mutable.value=state.value.copy(busy=false)}
        }
    }
    fun refresh() {
        if(state.value.busy) return
        val pending=state.value.pending
        if(pending!=null && !state.value.hasReceipt) {retry();return}
        val owner=state.value.owner ?: return
        run(owner) {synchronize(owner,it)}
    }
    private suspend fun synchronize(owner: String,revision: Int) {
        var failed=false
        suspend fun attempt(block: suspend ()->Unit) {try {block()} catch(c: CancellationException) {throw c} catch(_: Exception) {failed=true}}
        attempt {val data=wheel.configuration(BANNER);if(valid(owner,revision)) mutable.value=state.value.copy(configuration=data)}
        attempt {val data=wheel.balances();if(valid(owner,revision)) mutable.value=state.value.copy(balances=data)}
        attempt {val data=exchange.catalog();if(valid(owner,revision)) mutable.value=state.value.copy(pets=data)}
        attempt {val rows=inventory.getInventory();check(rows.all {it.userId==owner})}
        attempt {pets.collection()}
        if(valid(owner,revision)) mutable.value=state.value.copy(syncError=failed)
    }
    fun spin(payment: WheelPayment) {
        val current=state.value;val config=current.configuration ?: return;val balance=current.balances ?: return
        if(!current.canSpin || payment==WheelPayment.COINS && balance.coins<config.priceCoins || payment==WheelPayment.TICKET && balance.tickets<1) return
        start(PendingLuckyOperation(checkNotNull(current.owner),Uuid.random().toString(),LuckyOperationKind.SPIN,config.bannerId,payment,confirmed=true))
    }
    fun selectPet(code: String) {
        val current=state.value
        if(current.busy || current.pending!=null || current.hasReceipt || current.pets.none {it.speciesCode==code && !it.owned && it.price<= (current.balances?.petShards ?: -1)}) return
        start(PendingLuckyOperation(checkNotNull(current.owner),Uuid.random().toString(),LuckyOperationKind.PET_REDEMPTION,speciesCode=code))
    }
    fun confirmPet() {
        val current=state.value
        if(!current.busy && current.quote!=null && current.redemption==null) current.pending?.let {start(it.copy(confirmed=true))}
    }
    private fun start(request: PendingLuckyOperation) {
        if(state.value.busy) return
        mutable.value=state.value.copy(pending=request,rejection=null,quote=null,phase=LuckyWheelPhase.Loading)
        run(request.userId) {revision ->
            val saved=journal.save(request);check(saved.userId==request.userId)
            if(valid(request.userId,revision)) {mutable.value=state.value.copy(pending=saved);submit(saved,revision)}
        }
    }
    fun retry() {
        if(state.value.busy) return
        val pending=state.value.pending
        if(state.value.hasReceipt || pending==null) {refresh();return}
        start(pending)
    }
    private suspend fun submit(request: PendingLuckyOperation,revision: Int,restoring: Boolean=false) {
        if(request.kind==LuckyOperationKind.SPIN) {
            val response=wheel.spin(request.spinRequest(),request.userId)
            if(!valid(request.userId,revision)) return
            when(response) {
                is LuckyWheelResponse.Spun -> {
                    check(response.receipt.requestId==request.requestId && response.receipt.bannerId==request.bannerId && response.receipt.payment==request.payment)
                    val r=response.receipt
                    mutable.value=state.value.copy(result=r,rejection=null,error=null,skipRequested=false,
                        balances=LuckyWheelBalances(r.coinsBalanceAfter,r.ticketsBalanceAfter,r.petShardsBalanceAfter),
                        phase=if(restoring) LuckyWheelPhase.RewardRevealed else LuckyWheelPhase.ReadyToAnimate)
                    synchronize(request.userId,revision)
                }
                is LuckyWheelResponse.Rejected -> mutable.value=state.value.copy(phase=LuckyWheelPhase.Error,rejection=response.reason)
            }
        } else {
            val response=if(request.confirmed) exchange.redeem(request.petRequest(),request.userId) else exchange.quote(request.petRequest(),request.userId)
            if(!valid(request.userId,revision)) return
            when(response) {
                is PetShardResponse.Quoted -> {
                    check(!request.confirmed && response.quote.requestId==request.requestId && response.quote.speciesCode==request.speciesCode)
                    mutable.value=state.value.copy(quote=response.quote,phase=LuckyWheelPhase.Idle,error=null,rejection=null)
                }
                is PetShardResponse.Redeemed -> {
                    check(response.receipt.requestId==request.requestId && response.receipt.speciesCode==request.speciesCode)
                    mutable.value=state.value.copy(redemption=response.receipt,quote=null,phase=LuckyWheelPhase.RewardRevealed,error=null,rejection=null,
                        balances=state.value.balances?.copy(petShards=response.receipt.petShardsBalanceAfter))
                    synchronize(request.userId,revision)
                }
                is PetShardResponse.Rejected -> mutable.value=state.value.copy(quote=null,phase=LuckyWheelPhase.Error,rejection=response.reason)
            }
        }
    }
    fun animationStarted(id: String) {if(state.value.result?.transactionId==id && state.value.phase==LuckyWheelPhase.ReadyToAnimate) mutable.value=state.value.copy(phase=LuckyWheelPhase.Animating)}
    fun animationFinished(id: String) {if(state.value.result?.transactionId==id && state.value.phase in setOf(LuckyWheelPhase.ReadyToAnimate,LuckyWheelPhase.Animating)) mutable.value=state.value.copy(phase=LuckyWheelPhase.RewardRevealed)}
    fun skip() {state.value.result?.let {mutable.value=state.value.copy(skipRequested=true);animationFinished(it.transactionId)}}
    fun dismiss() {
        val current=state.value
        if(!current.canDismiss) return
        val owner=current.owner ?: return
        run(owner) {revision ->
            current.pending?.let {journal.clear(it)}
            if(valid(owner,revision)) mutable.value=state.value.copy(pending=null,result=null,redemption=null,quote=null,rejection=null,error=null,skipRequested=false,phase=LuckyWheelPhase.Idle)
        }
    }
    fun loadHistory(more: Boolean=false) {
        if(state.value.historyLoading || more && state.value.historyEnd) return
        val owner=state.value.owner ?: return;val revision=epoch
        val cursor=if(more) state.value.history.lastOrNull() else null
        mutable.value=state.value.copy(historyLoading=true,historyError=false)
        viewModelScope.launch {
            try {
                val rows=wheel.history(cursor?.createdAt,cursor?.transactionId,20)
                if(valid(owner,revision)) mutable.value=state.value.copy(history=(if(more) state.value.history else emptyList())+rows,historyEnd=rows.size<20)
            } catch(c: CancellationException) {throw c} catch(_: Exception) {if(valid(owner,revision)) mutable.value=state.value.copy(historyError=true)}
            finally {if(valid(owner,revision)) mutable.value=state.value.copy(historyLoading=false)}
        }
    }
    companion object {const val BANNER="e2800000-0000-0000-0000-000000000001"}
}

data class LuckyWheelAnimation(val configuration: LuckyWheelConfiguration?,val receipt: LuckyWheelReceipt?,val skipRequested: Boolean,
    val onStarted: (String)->Unit,val onFinished: (String)->Unit)
