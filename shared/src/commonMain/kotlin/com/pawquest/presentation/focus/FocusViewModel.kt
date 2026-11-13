package com.pawquest.presentation.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pawquest.domain.model.*
import com.pawquest.domain.usecase.ManageFocusSessions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.Uuid

data class FocusState(
    val snapshot: FocusSnapshot? = null, val remainingSeconds: Long = 0,
    val preview: QuestRewardPreview? = null, val visible: Boolean = true,
    val busy: Boolean = true, val synced: Boolean = false, val error: String? = null,
    val confirmCancel: Boolean = false,
    val claiming: Boolean = false,
) {
    val session get() = snapshot?.session
    val canClaim get() = synced && snapshot?.eligible == true && session?.status in listOf(FocusStatus.ACTIVE,FocusStatus.COMPLETED)
    val canClaimReward get() = canClaim && snapshot?.offer?.canClaim == true
    val minutesLabel get() = ((remainingSeconds+59)/60).toString().padStart(2,'0')
    val progress get() = session?.let { (remainingSeconds.toFloat()/(it.durationMinutes*60)).coerceIn(0f,1f) } ?: 0f
}

class FocusViewModel(private val repository: ManageFocusSessions, private val clock: FocusClock = MonotonicFocusClock()) : ViewModel() {
    private val mutableState = MutableStateFlow(FocusState())
    val state = mutableState.asStateFlow()
    private val countdown = FocusCountdown(clock)
    private var foreground = false
    private var ticker: Job? = null
    private var pendingStart: Pair<String,String>? = null
    private var lastAttempt = clock.milliseconds()
    private var operation: Job? = null
    init { reconcile(startup = true) }

    fun foreground(active: Boolean) {
        if (foreground == active) return
        foreground = active
        ticker?.cancel()
        mutableState.value = state.value.copy(synced = false)
        if (active) {
            reconcile()
            ticker = viewModelScope.launch {
                while (isActive) {
                    updateDisplay()
                    val current = state.value
                    if (current.session?.status in listOf(FocusStatus.ACTIVE,FocusStatus.COMPLETED) && !current.busy &&
                        (clock.milliseconds()-lastAttempt >= 15_000 ||
                            current.remainingSeconds == 0L && current.synced && current.snapshot?.eligible == false)) reconcile()
                    delay(250)
                }
            }
        }
    }
    private fun accept(snapshot: FocusSnapshot, show: Boolean = state.value.visible) {
        if (snapshot.session != null) pendingStart = null
        countdown.anchor(snapshot)
        mutableState.value = state.value.copy(snapshot = snapshot, synced = true,
            remainingSeconds = countdown.remainingSeconds(),
            preview = snapshot.session?.let { questRewardPreview(it.durationMinutes) },
            visible = snapshot.session != null && show, error = null)
        lastAttempt = clock.milliseconds()
    }
    private fun updateDisplay() {
        if (state.value.session != null) mutableState.value = state.value.copy(remainingSeconds = countdown.remainingSeconds())
    }
    fun start(questId: String) {
        if (state.value.busy) return
        if (state.value.session?.status == FocusStatus.ACTIVE && state.value.snapshot?.eligible != true) { show(); return }
        val request = pendingStart?.takeIf { it.first == questId } ?: (questId to Uuid.random().toString())
        pendingStart = request
        mutableState.value = state.value.copy(visible = true,snapshot = null,preview = null,remainingSeconds = 0,synced = false)
        action {
            try { accept(repository.start(request.first,request.second),true); pendingStart = null }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                // A committed request may have lost its HTTP response, or another device may own a session.
                val restored = try { repository.reconcile() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                if (restored?.session != null && (restored.session.status == FocusStatus.ACTIVE || restored.session.requestId == request.second)) {
                    accept(restored,true); pendingStart = null
                }
                else throw error
            }
        }
    }
    /**
     * [startup]: the automatic check at launch. If it fails while no session is known (e.g. offline),
     * the app is not blocked behind Focus; Quests still offers "Resume Focus" to retry.
     */
    fun reconcile(startup: Boolean = false) {
        if (state.value.busy && state.value.snapshot != null) return
        if (operation?.isActive == true) return
        val sessionId = state.value.session?.id
        action {
            val restored=try { repository.reconcile(sessionId) }
            catch (error: Exception) {
                if (error !is CancellationException && startup && state.value.session == null && pendingStart == null)
                    mutableState.value = state.value.copy(visible = false)
                throw error
            }
            val pending=pendingStart
            if (pending!=null && sessionId==null && restored.session?.status!=FocusStatus.ACTIVE && restored.session?.requestId!=pending.second)
                error("Start result is not confirmed. Retry to check the same request safely.")
            accept(restored)
        }
    }
    fun retry() {
        if (state.value.busy) return
        val pending = pendingStart
        if (pending != null && state.value.session == null) start(pending.first) else reconcile()
    }
    fun show() {
        mutableState.value = state.value.copy(visible = true)
        if(pendingStart!=null && state.value.session==null) retry() else reconcile()
    }
    fun hide() { if (!state.value.busy) mutableState.value = state.value.copy(visible = false) }
    fun requestCancel() { if (!state.value.busy) mutableState.value = state.value.copy(confirmCancel = true) }
    fun dismissCancel() { mutableState.value = state.value.copy(confirmCancel = false) }
    fun cancel() {
        val session = state.value.session ?: return
        if (state.value.busy) return
        mutableState.value = state.value.copy(confirmCancel = false)
        action { accept(repository.cancel(session.id),true) }
    }
    fun claim() {
        val current = state.value
        if (!current.canClaimReward || current.busy) return
        action(claiming=true) { accept(repository.claim(current.session!!.id),true) }
    }
    fun done() {
        if (state.value.busy) return
        mutableState.value = FocusState(visible = false,busy = false)
        reconcile()
    }
    private fun action(claiming: Boolean=false,block: suspend () -> Unit) {
        if (operation?.isActive == true) return
        mutableState.value = state.value.copy(busy = true,error = null,claiming=claiming)
        lastAttempt = clock.milliseconds()
        operation = viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutableState.value = state.value.copy(synced = false,
                    error = if(error is IllegalStateException) error.message else "Couldn't sync. Please retry.")
            }
            finally { mutableState.value = state.value.copy(busy = false,claiming=false) }
        }
    }
}
