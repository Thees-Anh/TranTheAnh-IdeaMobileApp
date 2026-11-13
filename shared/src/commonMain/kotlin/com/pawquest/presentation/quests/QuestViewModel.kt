package com.pawquest.presentation.quests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pawquest.domain.model.*
import com.pawquest.domain.repository.QuestSessionRepository
import com.pawquest.domain.repository.StartedQuestSession
import com.pawquest.domain.usecase.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class QuestPage { LIST, CREATE, EDIT, DETAILS }
data class QuestForm(
    val title: String = "", val description: String = "", val category: String = "Focus",
    val durationMinutes: Int = 25, val repeatType: QuestRepeatType = QuestRepeatType.ONE_TIME,
    val titleError: String? = null, val descriptionError: String? = null, val categoryError: String? = null,
    val preview: QuestRewardPreview = questRewardPreview(25),
    val deadlineEnabled: Boolean = false,val deadline: String = "",val deadlineError: String? = null,
)
data class QuestUiItem(val quest: Quest, val preview: QuestRewardPreview)
data class QuestManagementState(
    val page: QuestPage = QuestPage.LIST, val loading: Boolean = true,
    val items: List<QuestUiItem> = emptyList(), val selectedId: String? = null,
    val form: QuestForm = QuestForm(), val busy: Boolean = false,
    val error: String? = null, val notice: String? = null,
    val dailyCount: Int? = null, val progressError: Boolean = false,
    val deleteConfirmation: Boolean = false, val discardConfirmation: Boolean = false,
    val requestedFocusQuestId: String? = null,
) {
    val selected get() = items.firstOrNull { it.quest.id == selectedId }
}

class QuestViewModel(
    private val getQuests: GetQuests, private val createQuest: CreateQuest,
    private val updateQuest: UpdateQuest, private val deleteQuest: DeleteQuest,
    private val sessions: QuestSessionRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(QuestManagementState())
    val state = mutableState.asStateFlow()
    private var originalForm = QuestForm()
    private var loadJob: Job? = null
    private var reminderPending=false
    init { refresh() }

    fun refresh() {
        if (state.value.busy) return
        mutableState.value = state.value.copy(loading = true, error = null)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val items = getQuests().map { QuestUiItem(it, questRewardPreview(it.durationMinutes)) }
                mutableState.value = state.value.copy(loading = false, items = items)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableState.value = state.value.copy(loading = false, error = message(error)) }
        }
        refreshProgress()
    }
    fun refreshProgress() = viewModelScope.launch {
        try {
            val count = sessions.dailyRewardCount()
            mutableState.value = state.value.copy(dailyCount = count, progressError = false)
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutableState.value = state.value.copy(dailyCount = null, progressError = true) }
    }
    fun openCreate() {
        if (state.value.busy) return
        originalForm = QuestForm()
        mutableState.value = state.value.copy(page = QuestPage.CREATE, form = originalForm, selectedId = null, error = null, notice = null)
    }
    fun openDetails(id: String) {
        if (state.value.busy || state.value.items.none { it.quest.id == id }) return
        mutableState.value = state.value.copy(page = QuestPage.DETAILS, selectedId = id, error = null, notice = null)
    }
    fun openEdit() {
        if (state.value.busy) return
        val quest = state.value.selected?.quest ?: return
        originalForm = QuestForm(quest.title, quest.description, quest.category, quest.durationMinutes,
            quest.repeatType, preview = questRewardPreview(quest.durationMinutes),
            deadlineEnabled=quest.dueAt!=null,deadline=quest.dueAt?.let {QuestDeadlineInput.format(it)} ?: "")
        mutableState.value = state.value.copy(page = QuestPage.EDIT, form = originalForm, error = null, notice = null)
    }
    fun editTitle(value: String) = changeForm { copy(title = value, titleError = if(titleError != null) titleError(value) else null) }
    fun editDescription(value: String) = changeForm { copy(description = value, descriptionError = if(descriptionError != null) descriptionError(value) else null) }
    fun editCategory(value: String) = changeForm { copy(category = value, categoryError = null) }
    fun editRepeat(value: QuestRepeatType) = changeForm { copy(repeatType = value,deadlineEnabled=if(value==QuestRepeatType.DAILY) false else deadlineEnabled,deadlineError=null) }
    fun enableDeadline(value: Boolean)=changeForm {copy(deadlineEnabled=value,deadlineError=null)}
    fun editDeadline(value: String)=changeForm {copy(deadline=value,deadlineError=null)}
    fun editDuration(value: Int) {
        if (value !in 5..90) return
        changeForm { copy(durationMinutes = value, preview = questRewardPreview(value)) }
    }
    fun validateTitle() = changeForm { copy(titleError = titleError(title)) }
    fun validateDescription() = changeForm { copy(descriptionError = descriptionError(description)) }
    private fun titleError(value: String) = runCatching { QuestDraft(value, durationMinutes=5).validated() }.exceptionOrNull()?.message
    private fun descriptionError(value: String) = runCatching { QuestDraft("Quest",value,durationMinutes=5).validated() }.exceptionOrNull()?.message
    private fun changeForm(change: QuestForm.() -> QuestForm) {
        if (!state.value.busy) mutableState.value = state.value.copy(form = state.value.form.change(), error = null)
    }
    fun save() {
        val current = state.value
        if (current.busy || current.page !in listOf(QuestPage.CREATE, QuestPage.EDIT)) return
        val form = current.form
        val checked = form.copy(titleError = titleError(form.title), descriptionError = descriptionError(form.description),
            categoryError = if (form.category.trim().isEmpty()) "Choose a category." else null,
            deadlineError=if(form.deadlineEnabled) runCatching {QuestDeadlineInput.parse(form.deadline)}.exceptionOrNull()?.message else null)
        mutableState.value = current.copy(form = checked)
        if (checked.titleError != null || checked.descriptionError != null || checked.categoryError != null || checked.deadlineError != null) return
        val draft = QuestDraft(form.title, form.description, form.category, form.durationMinutes, form.repeatType,
            if(form.deadlineEnabled) QuestDeadlineInput.parse(form.deadline) else null)
        loadJob?.cancel()
        mutableState.value = state.value.copy(loading = false)
        action {
            val saved = if (current.page == QuestPage.CREATE) createQuest(draft) else updateQuest(current.selectedId ?: return@action, draft)
            val items = state.value.items.filterNot { it.quest.id == saved.id } + QuestUiItem(saved, questRewardPreview(saved.durationMinutes))
            mutableState.value = state.value.copy(page = QuestPage.DETAILS, selectedId = saved.id,
                items = items.sortedWith(compareByDescending<QuestUiItem> { it.quest.createdAt }.thenByDescending { it.quest.id }),
                notice = "Quest saved. Rewards are earned only after completing and claiming a session.")
        }
    }
    fun requestDelete() { if (!state.value.busy) mutableState.value = state.value.copy(deleteConfirmation = true) }
    fun dismissDelete() { mutableState.value = state.value.copy(deleteConfirmation = false) }
    fun confirmDelete() {
        val id = state.value.selectedId ?: return
        if (state.value.busy) return
        mutableState.value = state.value.copy(deleteConfirmation = false)
        action {
            deleteQuest(id)
            mutableState.value = state.value.copy(page = QuestPage.LIST, selectedId = null,
                items = state.value.items.filterNot { it.quest.id == id }, notice = "Quest deleted. Session history is preserved.")
        }
    }
    fun start() {
        val id = state.value.selectedId ?: return
        if (state.value.busy) return
        mutableState.value = state.value.copy(requestedFocusQuestId = id)
    }
    fun consumeFocusRequest() { mutableState.value = state.value.copy(requestedFocusQuestId = null) }
    fun openFromReminder() {
        if(state.value.busy) return
        if(state.value.page in listOf(QuestPage.CREATE,QuestPage.EDIT) && state.value.form!=originalForm) {
            reminderPending=true
            mutableState.value=state.value.copy(discardConfirmation=true)
        } else {
            reminderPending=false
            mutableState.value=state.value.copy(page=QuestPage.LIST,selectedId=null,error=null,notice=null)
            refresh()
        }
    }
    fun back() {
        if (state.value.busy) return
        if (state.value.page in listOf(QuestPage.CREATE, QuestPage.EDIT) && state.value.form != originalForm) {
            mutableState.value = state.value.copy(discardConfirmation = true)
        } else leaveForm()
    }
    fun dismissDiscard() { reminderPending=false;mutableState.value = state.value.copy(discardConfirmation = false) }
    fun leaveForm() {
        val reminder=reminderPending;reminderPending=false
        mutableState.value = state.value.copy(page = if (!reminder && state.value.page == QuestPage.EDIT) QuestPage.DETAILS else QuestPage.LIST,
            selectedId=if(reminder) null else state.value.selectedId,
            discardConfirmation = false, error = null, notice = null)
        if(reminder) refresh()
    }
    private fun action(block: suspend () -> Unit) {
        mutableState.value = state.value.copy(busy = true, error = null, notice = null)
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableState.value = state.value.copy(error = message(error)) }
            finally { mutableState.value = state.value.copy(busy = false) }
        }
    }
    private fun message(error: Exception) = when(error) {
        is QuestException, is IllegalArgumentException, is IllegalStateException -> error.message ?: "Please try again."
        else -> "Couldn't complete this action. Please retry."
    }
}
