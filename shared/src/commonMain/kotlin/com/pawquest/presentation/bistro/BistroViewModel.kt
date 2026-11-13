package com.pawquest.presentation.bistro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pawquest.domain.model.*
import com.pawquest.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.uuid.Uuid

data class BistroState(val owner: String?=null,val menu: List<BistroItem> = emptyList(),val loading: Boolean=false,
    val busy: Boolean=false,val recovering: Boolean=true,val storageError: Boolean=false,val pending: PendingBistroMeal?=null,
    val receipt: BistroReceipt?=null,val animationPending: Boolean=false,val error: String?=null) {
    val canOrder get()=owner!=null && menu.isNotEmpty() && !loading && !busy && !recovering && !storageError && pending==null && !animationPending
}
class BistroViewModel(private val pets: PetRepository,auth: AuthenticationRepository,private val timeoutMillis: Long=20_000): ViewModel() {
    private val mutable=MutableStateFlow(BistroState());val state=mutable.asStateFlow()
    private var revision=0L
    private var presented=false
    fun visible(active: Boolean) {presented=active;if(!active) state.value.receipt?.let {animationFinished(it.id)}}
    init {
        viewModelScope.launch {
            auth.state.map {(it as? AuthenticationState.SignedIn)?.account?.userId}.distinctUntilChanged().collectLatest {owner ->
                val version=++revision;mutable.value=BistroState(owner=owner,recovering=owner!=null)
                if(owner!=null) recover(owner,version)
            }
        }
    }
    private fun valid(owner: String,version: Long)=revision==version && state.value.owner==owner
    private suspend fun recover(owner: String,version: Long) {
        try {
            val pending=withTimeout(timeoutMillis) {pets.pendingMeal(owner)}
            if(valid(owner,version)) mutable.value=state.value.copy(recovering=false,pending=pending,storageError=false,
                error=if(pending!=null) "A meal needs confirmation. Retry the same request." else null)
        } catch(cancelled: CancellationException) {
            if(cancelled !is TimeoutCancellationException) throw cancelled
            if(valid(owner,version)) mutable.value=state.value.copy(recovering=false,storageError=true,error="Bistro storage needs attention. Retry safely.")
        } catch(_: Exception) {if(valid(owner,version)) mutable.value=state.value.copy(recovering=false,storageError=true,error="Bistro storage needs attention. Retry safely.")}
    }
    fun loadMenu() {
        val owner=state.value.owner ?: return
        if(state.value.loading) return
        val version=revision;mutable.value=state.value.copy(loading=true)
        viewModelScope.launch {
            try {val rows=withTimeout(timeoutMillis) {pets.bistroMenu()}
                if(valid(owner,version)) mutable.value=state.value.copy(menu=rows,error=if(rows.isEmpty()) "Bistro is unavailable. Please retry." else if(state.value.pending!=null) state.value.error else null)
            } catch(cancelled: CancellationException) {if(cancelled !is TimeoutCancellationException) throw cancelled
                if(valid(owner,version)) mutable.value=state.value.copy(error="Bistro is unavailable. Please retry.")
            } catch(_: Exception) {if(valid(owner,version)) mutable.value=state.value.copy(error="Bistro is unavailable. Please retry.")}
            finally {if(valid(owner,version)) mutable.value=state.value.copy(loading=false)}
        }
    }
    fun order(itemId: String) {
        if(!state.value.canOrder) return
        val item=state.value.menu.firstOrNull {it.itemId==itemId} ?: return
        perform(PendingBistroMeal(checkNotNull(state.value.owner),item.itemId,Uuid.random().toString(),item.price))
    }
    fun retry() {
        if(state.value.busy || state.value.recovering) return
        state.value.pending?.let {perform(it);return}
        val owner=state.value.owner ?: return
        if(state.value.storageError) {val version=revision;mutable.value=state.value.copy(recovering=true);viewModelScope.launch {recover(owner,version)}}
        else loadMenu()
    }
    private fun perform(request: PendingBistroMeal) {
        if(state.value.busy || state.value.animationPending) return
        val version=revision
        mutable.value=state.value.copy(busy=true,pending=request,receipt=null,error=null)
        viewModelScope.launch {
            try {
                withTimeout(timeoutMillis) {
                    val saved=pets.saveMeal(request)
                    if(!valid(request.userId,version)) return@withTimeout
                    check(saved.userId==request.userId)
                    mutable.value=state.value.copy(pending=saved,storageError=false)
                    if(saved!=request) {mutable.value=state.value.copy(error="A meal needs confirmation. Retry the same request.");return@withTimeout}
                    when(val result=pets.bistroFeed(saved)) {
                        is BistroResponse.Fed -> if(valid(saved.userId,version)) {
                            check(result.receipt.userId==saved.userId && result.receipt.requestId==saved.requestId && result.receipt.itemId==saved.itemId)
                            mutable.value=state.value.copy(receipt=result.receipt,animationPending=true)
                        }
                        is BistroResponse.Rejected -> {
                            pets.clearMeal(saved)
                            if(valid(saved.userId,version)) mutable.value=state.value.copy(pending=null,error=when(result.reason) {
                                "insufficient_balance" -> "Not enough Coins for this meal."
                                "pet_sleeping" -> "Wake your pet before ordering a meal."
                                "price_changed" -> "The meal price changed. Refresh the menu before ordering."
                                else -> "This meal is unavailable. Refresh the menu."
                            })
                        }
                    }
                }
            } catch(cancelled: CancellationException) {if(cancelled !is TimeoutCancellationException) throw cancelled
                if(valid(request.userId,version)) mutable.value=state.value.copy(error="The meal is not confirmed yet. Retry the same request.")
            } catch(_: Exception) {if(valid(request.userId,version)) mutable.value=state.value.copy(error="The meal is not confirmed yet. Retry the same request.")}
            finally {if(valid(request.userId,version)) {
                mutable.value=state.value.copy(busy=false)
                if(!presented) state.value.receipt?.let {animationFinished(it.id)}
            }}
        }
    }
    fun animationFinished(id: String) {
        val request=state.value.pending ?: return
        if(state.value.receipt?.id!=id || !state.value.animationPending) return
        val version=revision
        mutable.value=state.value.copy(animationPending=false,busy=true)
        viewModelScope.launch {
            try {withTimeout(timeoutMillis) {pets.clearMeal(request)}
                if(valid(request.userId,version)) mutable.value=state.value.copy(pending=null,error=null)
            } catch(cancelled: CancellationException) {if(cancelled !is TimeoutCancellationException) throw cancelled
                if(valid(request.userId,version)) mutable.value=state.value.copy(error="The meal is saved. Retry to finish synchronization.")
            } catch(_: Exception) {if(valid(request.userId,version)) mutable.value=state.value.copy(error="The meal is saved. Retry to finish synchronization.")}
            finally {if(valid(request.userId,version)) mutable.value=state.value.copy(busy=false)}
        }
    }
}
