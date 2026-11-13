package com.pawquest.presentation.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.compositionLocalOf
import com.pawquest.domain.model.*
import com.pawquest.domain.repository.ProgressRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.LocalDate

data class ProgressState(val snapshot: ProgressSnapshot?=null,val loading: Boolean=false,val error: String?=null,
    val acknowledging: Boolean=false,val deferred: Set<String> = emptySet()) {
    val event get()=snapshot?.events?.firstOrNull {it.id !in deferred}
}
class ProgressViewModel(private val repository: ProgressRepository): ViewModel() {
    private val mutable=MutableStateFlow(ProgressState())
    val state=mutable.asStateFlow()
    private var load: Job?=null
    private var revision=0
    private val acknowledged=mutableSetOf<String>()
    init {refresh()}
    fun refresh(month: LocalDate?=state.value.snapshot?.month) {
        load?.cancel();val expected=++revision
        mutable.value=state.value.copy(loading=true,error=null)
        load=viewModelScope.launch {
            try {
                val snapshot=repository.snapshot(month)
                if(expected==revision) mutable.value=state.value.copy(snapshot=snapshot.copy(events=snapshot.events.filterNot {it.id in acknowledged}),loading=false)
            } catch(cancelled: CancellationException) {throw cancelled}
            catch(_: Exception) {if(expected==revision) mutable.value=state.value.copy(loading=false,error="Couldn't sync your habit calendar. Your saved progress is safe.")}
        }
    }
    fun moveMonth(delta: Int) {state.value.snapshot?.month?.let {refresh(HabitCalendarRules.shiftMonth(it,delta))}}
    fun acknowledge(id: String) {
        if(state.value.acknowledging) return
        mutable.value=state.value.copy(acknowledging=true,error=null)
        viewModelScope.launch {
            try {
                repository.acknowledge(id)
                acknowledged.add(id)
                mutable.value=state.value.copy(snapshot=state.value.snapshot?.let {it.copy(events=it.events.filterNot {e->e.id==id})})
            } catch(cancelled: CancellationException) {throw cancelled}
            catch(_: Exception) {mutable.value=state.value.copy(error="Your gift is saved. Retry to dismiss this celebration, or choose Later.")}
            finally {mutable.value=state.value.copy(acknowledging=false)}
        }
    }
    fun later(id: String) {mutable.value=state.value.copy(deferred=state.value.deferred+id,error=null)}
}
data class ProgressController(val state: ProgressState,val refresh: ()->Unit,val month: (Int)->Unit)
val LocalProgress=compositionLocalOf<ProgressController?> {null}
