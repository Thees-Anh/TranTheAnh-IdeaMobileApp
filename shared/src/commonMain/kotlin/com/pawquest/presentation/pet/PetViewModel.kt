package com.pawquest.presentation.pet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pawquest.domain.model.*
import com.pawquest.domain.usecase.ManagePet
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.Uuid
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.minutes

data class PetState(val snapshot: PetSnapshot?=null,val loading: Boolean=true,val busy: Boolean=false,
    val error: String?=null,val pendingAction: PetCareAction?=null,val lastActionResult: PetActionResult?=null,
    val reaction: PetCareAction?=null,val feedback: String?=null,val feedbackCode: String?=null,
    val collection: PetCollection?=null,val collectionBusy: Boolean=false,val collectionError: String?=null,
    val companionContext: PetCompanionContext?=null,val celebrating: Boolean=false)
class PetViewModel(private val manage: ManagePet,private val requestTimeoutMillis: Long=20_000) : ViewModel() {
    private val mutableState=MutableStateFlow(PetState())
    val state=mutableState.asStateFlow()
    private var requestId: String?=null
    private var polling: Job?=null
    private var refreshQueued=false
    private var reactionJob: Job?=null
    private var companionJob: Job?=null
    private var celebrationJob: Job?=null
    private var companionShown=false
    private var pendingCelebration: String?=null
    private var seenCompletion: String?=null
    init {require(requestTimeoutMillis>0);refresh()}
    fun foreground(active: Boolean) {
        polling?.cancel()
        if(active) {
            refresh()
            polling=viewModelScope.launch { while(isActive) { delay(60_000); refresh() } }
        }
    }
    fun refresh() {
        if(state.value.busy || state.value.pendingAction!=null) { refreshQueued=true; return }
        load { manage.restore() }
    }
    fun care(action: PetCareAction) {
        if(state.value.busy || state.value.snapshot==null) {
            // A touch on the illustration can arrive while disabled care tiles
            // are synchronizing. Ignore repeated taps without stacking dialogs.
            return
        }
        // Resolve an ambiguous operation before accepting a different action.
        if(state.value.pendingAction!=null && state.value.pendingAction!=action) {
            mutableState.value=state.value.copy(feedback="Your previous action needs confirmation. Tap Retry before starting another action.",feedbackCode=null)
            return
        }
        val id=requestId ?: Uuid.random().toString().also { requestId=it }
        mutableState.value=state.value.copy(pendingAction=action,lastActionResult=null)
        load(onConfirmed={requestId=null;mutableState.value=state.value.copy(pendingAction=null)}) {manage.care(action,id)}
    }
    fun retry() { state.value.pendingAction?.let { care(it) } ?: refresh() }
    fun dismissFeedback() {mutableState.value=state.value.copy(feedback=null,feedbackCode=null)}
    fun companionVisible(active: Boolean) {
        companionShown=active
        if(!active) {celebrationJob?.cancel();mutableState.value=state.value.copy(celebrating=false)}
        else startCelebration()
    }
    private fun startCelebration() {
        if(!companionShown || pendingCelebration==null || celebrationJob?.isActive==true ||
            state.value.snapshot?.pet?.sleepUntil!=null || state.value.reaction!=null) return
        val expected=pendingCelebration
        mutableState.value=state.value.copy(celebrating=true)
        celebrationJob=viewModelScope.launch {
            delay(PuppyEmotionRules.celebrationMilliseconds)
            if(pendingCelebration==expected) pendingCelebration=null
            mutableState.value=state.value.copy(celebrating=false)
        }
    }
    private fun refreshCompanion(snapshot: PetSnapshot) {
        companionJob?.cancel()
        if(PetArtworkRegistry.find(snapshot.pet.rendererKey)?.storyEmotions!=true) {
            celebrationJob?.cancel();pendingCelebration=null;seenCompletion=null
            mutableState.value=state.value.copy(companionContext=null,celebrating=false)
            return
        }
        companionJob=viewModelScope.launch {
            try {
                val context=manage.companionContext(TimeZone.currentSystemDefault().id) ?: return@launch
                if(PetArtworkRegistry.find(state.value.snapshot?.pet?.rendererKey ?: "")?.storyEmotions!=true) return@launch
                mutableState.value=state.value.copy(companionContext=context)
                val completion=context.completionId
                if(completion!=null && completion!=seenCompletion) {
                    seenCompletion=completion
                    val ended=context.completedAt
                    if(ended!=null && ended<=context.serverNow && context.serverNow-ended<=5.minutes) pendingCelebration=completion
                }
                startCelebration()
            } catch(cancelled: CancellationException) {throw cancelled}
            catch(_: Exception) { /* Offline does not invent a missed quest or completion event. */ }
        }
    }
    fun loadCollection() {
        if(state.value.collectionBusy) return
        mutableState.value=state.value.copy(collectionBusy=true,collectionError=null)
        viewModelScope.launch {
            try {
                // Never capture state.value as the copy receiver before a suspend:
                // care/restore may finish while collection is in flight.
                val collection=withTimeout(requestTimeoutMillis) {manage.collection()}
                mutableState.value=state.value.copy(collection=collection)
            }
            catch(_: TimeoutCancellationException) {mutableState.value=state.value.copy(collectionError="Couldn't load your pets. Check your connection and retry.")}
            catch(cancelled: CancellationException) {throw cancelled}
            catch(_: Exception) {mutableState.value=state.value.copy(collectionError="Couldn't load your pets. Check your connection and retry.")}
            finally {mutableState.value=state.value.copy(collectionBusy=false)}
        }
    }
    fun selectSpecies(code: String) {
        if(state.value.busy || state.value.pendingAction!=null || state.value.collectionBusy) return
        val collection=state.value.collection ?: return
        val species=collection.species.find {it.code==code} ?: return
        mutableState.value=state.value.copy(busy=true,collectionBusy=true,collectionError=null)
        viewModelScope.launch {
            var selected=false
            try {
                val snapshot=withTimeout(requestTimeoutMillis) {manage.selectSpecies(species,collection.level)}
                reactionJob?.cancel()
                mutableState.value=state.value.copy(snapshot=snapshot,collection=collection.copy(activeCode=snapshot.pet.speciesCode,level=snapshot.pet.progress.level),
                    busy=false,collectionBusy=false,
                    reaction=null,lastActionResult=null,feedback="${species.name} is now your companion. Progress and care stats are preserved.",feedbackCode=null)
                // Selection already returns reconciled care state; don't lock the new scene for a redundant restore.
                selected=true
                refreshCompanion(snapshot)
            } catch(_: TimeoutCancellationException) {
                mutableState.value=state.value.copy(feedback="Couldn't confirm this pet. The collection will refresh to show the saved selection. Locked pets or missing artwork cannot be selected.",feedbackCode=null)
            } catch(cancelled: CancellationException) {throw cancelled}
            catch(_: Exception) {mutableState.value=state.value.copy(feedback="Couldn't confirm this pet. The collection will refresh to show the saved selection. Locked pets or missing artwork cannot be selected.",feedbackCode=null)}
            finally {
                mutableState.value=state.value.copy(busy=false,collectionBusy=false)
                if(!selected && currentCoroutineContext().isActive) {refresh();loadCollection()}
                else if(refreshQueued && currentCoroutineContext().isActive) {refreshQueued=false;refresh()}
            }
        }
    }
    private fun load(onConfirmed: ()->Unit={},block: suspend ()->PetSnapshot) {
        mutableState.value=state.value.copy(busy=true,loading=state.value.snapshot==null,error=null)
        viewModelScope.launch {
            var confirmed=false
            try {
                val snapshot=withTimeout(requestTimeoutMillis) {block()}
                currentCoroutineContext().ensureActive()
                onConfirmed();confirmed=true
                mutableState.value=state.value.copy(snapshot=snapshot,error=null,
                    lastActionResult=snapshot.actionResult ?: state.value.lastActionResult)
                refreshCompanion(snapshot)
                snapshot.actionResult?.takeUnless {it.applied}?.let {result ->
                    val message=if(result.code=="no_food") {
                        if(snapshot.coins==0L) "No food left and your wallet is empty. Complete a Quest and claim its reward, then buy food in the Shop. Drinking water is free."
                        else "No food left. Visit the Shop to buy food with your ${snapshot.coins} Coins. Drinking water is free."
                    } else result.message
                    mutableState.value=state.value.copy(feedback=message,feedbackCode=result.code)
                }
                snapshot.actionResult?.takeIf { it.applied }?.let { result ->
                    reactionJob?.cancel()
                    celebrationJob?.cancel()
                    mutableState.value=state.value.copy(reaction=result.action,celebrating=false)
                    reactionJob=viewModelScope.launch {
                        delay(PetCareBalance.reactionMilliseconds.toLong())
                        mutableState.value=state.value.copy(reaction=null)
                        startCelebration()
                    }
                }
            }
            catch(_: TimeoutCancellationException) {
                mutableState.value=state.value.copy(error="Your pet is taking longer to sync. Tap Retry to safely confirm the previous request.")
            }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(error: Exception) { mutableState.value=state.value.copy(error=if(error is IllegalStateException) error.message else "Couldn't sync your pet. Please retry.") }
            finally {
                mutableState.value=state.value.copy(loading=false,busy=false)
                if(!confirmed && state.value.pendingAction==null) refreshQueued=false
                if(confirmed && refreshQueued && state.value.pendingAction==null && currentCoroutineContext().isActive) { refreshQueued=false; refresh() }
            }
        }
    }
}
