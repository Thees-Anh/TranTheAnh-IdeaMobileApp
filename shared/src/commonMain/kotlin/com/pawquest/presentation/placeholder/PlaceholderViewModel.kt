package com.pawquest.presentation.placeholder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pawquest.domain.model.SectionPlaceholder
import com.pawquest.domain.usecase.GetPlaceholder
import com.pawquest.presentation.state.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlaceholderViewModel(
    private val sectionId: String,
    private val getPlaceholder: GetPlaceholder,
) : ViewModel() {
    private val mutableState = MutableStateFlow<UiState<SectionPlaceholder>>(UiState.Loading)
    val state: StateFlow<UiState<SectionPlaceholder>> = mutableState.asStateFlow()
    private var loadJob: Job? = null

    init { retry() }

    fun retry() {
        loadJob?.cancel()
        mutableState.value = UiState.Loading
        loadJob = viewModelScope.launch {
            try {
                val content = getPlaceholder(sectionId)
                mutableState.value = content?.let { UiState.Content(it) }
                    ?: UiState.Empty("Nothing here yet.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = UiState.Error("This screen couldn't be loaded. Please try again.")
            }
        }
    }
}
