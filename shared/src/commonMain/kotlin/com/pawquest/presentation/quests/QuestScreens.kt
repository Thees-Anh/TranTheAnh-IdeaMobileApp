package com.pawquest.presentation.quests

import com.pawquest.localization.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pawquest.domain.model.QuestDeadlineInput
import kotlinx.datetime.*
import kotlin.time.Instant
import com.pawquest.domain.model.QuestRepeatType
import com.pawquest.domain.model.QuestRewardPreview
import com.pawquest.presentation.components.*
import com.pawquest.presentation.theme.PawQuestTheme

@Composable
fun QuestManagementScreen(model: QuestViewModel, modifier: Modifier = Modifier, resumeFocus: (() -> Unit)? = null,
    focusState: com.pawquest.presentation.focus.FocusState?=null) {
    val state by model.state.collectAsStateWithLifecycle()
    QuestBackHandler(state.page != QuestPage.LIST) { model.back() }
    Box(modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 600.dp).fillMaxSize()) {
            if(state.page!=QuestPage.LIST || state.items.none {focusState?.progressForQuest(it.quest.id)!=null})
                resumeFocus?.let { TextButton(onClick = it) { Text(tr("Resume Focus"), color = PawQuestTheme.colors.textPrimary) } }
            if (state.page != QuestPage.LIST) TextButton(onClick = model::back, enabled = !state.busy) {
                Text(tr("Back"), color = PawQuestTheme.colors.textPrimary)
            }
            when (state.page) {
                QuestPage.LIST -> QuestList(state, model, Modifier.weight(1f),focusState,resumeFocus)
                QuestPage.CREATE, QuestPage.EDIT -> QuestFormScreen(state, model, Modifier.weight(1f))
                QuestPage.DETAILS -> QuestDetails(state, model, Modifier.weight(1f),focusState,resumeFocus)
            }
        }
    }
    if (state.deleteConfirmation) AlertDialog(
        onDismissRequest = model::dismissDelete,
        title = { Text(tr("Delete this quest?")) },
        text = { Text(tr("It will leave your quest list. Your existing session history will stay safe.")) },
        confirmButton = { TextButton(onClick = model::confirmDelete) { Text(tr("Delete quest")) } },
        dismissButton = { TextButton(onClick = model::dismissDelete) { Text(tr("Keep quest")) } },
    )
    if (state.discardConfirmation) AlertDialog(
        onDismissRequest = model::dismissDiscard,
        title = { Text(tr("Discard changes?")) },
        text = { Text(tr("Your unsaved changes will be lost.")) },
        confirmButton = { TextButton(onClick = model::leaveForm) { Text(tr("Discard")) } },
        dismissButton = { TextButton(onClick = model::dismissDiscard) { Text(tr("Keep editing")) } },
    )
}

@Composable private fun QuestList(state: QuestManagementState, model: QuestViewModel, modifier: Modifier,
    focus: com.pawquest.presentation.focus.FocusState?,resume: (()->Unit)?) {
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Title("Your quests")
            Text(tr("Small steps, happy paws."), color = PawQuestTheme.colors.textSecondary)
            Action("Create Quest", model::openCreate)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { DailyProgress(state, model) }
            state.notice?.let { item { Notice(it) } }
            state.error?.let { item { Failure(it); Action("Retry", model::refresh, secondary = true) } }
            if (state.loading) item { Loading("Loading your quests…") }
            else if (state.items.isEmpty() && state.error == null) item {
                Panel {
                    Title("Your first small step")
                    Text(tr("Create a quest for something you want to focus on. Choose 5–90 minutes and make time for you."))
                    Text(tr("No quests yet"), style = PawQuestTheme.typography.labelLarge)
                }
            }
            items(state.items.sortedByDescending {focus?.progressForQuest(it.quest.id)!=null}, key = { it.quest.id }) { item ->
                val active=focus?.progressForQuest(item.quest.id)
                Panel(highlight=active!=null) {
                    if(active!=null) QuestCountdown(active)
                    Text(tr(item.quest.category), style = PawQuestTheme.typography.labelLarge, color = PawQuestTheme.colors.textSecondary)
                    Text(item.quest.title, style = PawQuestTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(tr("${item.quest.durationMinutes} min · ${if(item.quest.repeatType == QuestRepeatType.DAILY) "Daily" else "One-time"}"))
                    Rewards(item.preview)
                    if(active!=null && resume!=null) Action("Resume Focus",resume)
                    else Action("View quest", { model.openDetails(item.quest.id) }, secondary = true)
                }
            }
            item { TextButton(onClick = model::refresh, enabled = !state.loading) { Text(tr("Refresh quests")) } }
        }
    }
}

@Composable private fun DailyProgress(state: QuestManagementState, model: QuestViewModel) {
    Panel {
        Text(tr("Daily reward progress"), style = PawQuestTheme.typography.titleMedium)
        when {
            state.dailyCount != null -> {
                Text(tr("${state.dailyCount}/5 rewarded sessions today (UTC)"))
                LinearProgressIndicator(progress = { state.dailyCount.coerceIn(0,5) / 5f },
                    modifier = Modifier.fillMaxWidth(), color = PawQuestTheme.colors.primaryDark,
                    trackColor = PawQuestTheme.colors.primaryContainer)
            }
            state.progressError -> {
                Text(tr("Daily progress is unavailable."))
                TextButton(onClick = { model.refreshProgress() }) { Text(tr("Retry progress")) }
            }
            else -> Text(tr("Loading daily progress…"))
        }
    }
}

@Composable private fun QuestFormScreen(state: QuestManagementState, model: QuestViewModel, modifier: Modifier) {
    val form = state.form
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var titleFocused by remember { mutableStateOf(false) }
    var descriptionFocused by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title(if (state.page == QuestPage.CREATE) "Create a quest" else "Edit quest")
        Text(tr("Pick one thing. Give it your focus."), color = PawQuestTheme.colors.textSecondary)
        OutlinedTextField(form.title, model::editTitle, modifier = Modifier.fillMaxWidth().onFocusChanged {
            if (titleFocused && !it.isFocused) model.validateTitle()
            titleFocused = it.isFocused
        }, label = { Text(tr("Quest title")) }, singleLine = true, enabled = !state.busy,
            isError = form.titleError != null, supportingText = { form.titleError?.let { Text(tr(it)) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), colors = fieldColors())
        OutlinedTextField(form.description, model::editDescription, modifier = Modifier.fillMaxWidth().onFocusChanged {
            if (descriptionFocused && !it.isFocused) model.validateDescription()
            descriptionFocused = it.isFocused
        }, label = { Text(tr("Description (optional)")) }, minLines = 3, maxLines = 5, enabled = !state.busy,
            isError = form.descriptionError != null, supportingText = { form.descriptionError?.let { Text(tr(it)) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focus.clearFocus() }), colors = fieldColors())
        CategorySelector(form.category, !state.busy, model::editCategory)
        Text(tr("Duration · ${form.durationMinutes} minutes"), style = PawQuestTheme.typography.titleMedium)
        Text(tr("5–90 minutes"), color = PawQuestTheme.colors.textSecondary)
        Slider(value = form.durationMinutes.toFloat(), onValueChange = { model.editDuration(it.toInt()) },
            valueRange = 5f..90f, steps = 84, enabled = !state.busy, modifier = Modifier.fillMaxWidth()
                .semantics { contentDescription = tr("Duration in minutes") },
            colors = SliderDefaults.colors(thumbColor = PawQuestTheme.colors.primaryDark,
                activeTrackColor = PawQuestTheme.colors.primaryDark))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { model.editDuration(form.durationMinutes - 1) }, enabled = !state.busy && form.durationMinutes > 5) { Text(tr("− 1 min")) }
            OutlinedButton(onClick = { model.editDuration(form.durationMinutes + 1) }, enabled = !state.busy && form.durationMinutes < 90) { Text(tr("+ 1 min")) }
        }
        Text(tr("Repeat"), style = PawQuestTheme.typography.titleMedium)
        Column {
            QuestRepeatType.entries.forEach { type ->
                Surface(onClick = { model.editRepeat(type) }, enabled = !state.busy,
                    color = PawQuestTheme.colors.surface, modifier = Modifier.fillMaxWidth().semantics {
                        selected = form.repeatType == type
                        role = Role.RadioButton
                    },
                    shape = PawQuestTheme.shapes.card) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = form.repeatType == type, onClick = null, enabled = !state.busy)
                        Text(tr(if(type == QuestRepeatType.DAILY) "Daily" else "One-time"), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        if(form.repeatType==QuestRepeatType.ONE_TIME) {
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants=true) {contentDescription=tr("Add a deadline")}
                .toggleable(form.deadlineEnabled,enabled=!state.busy,role=Role.Checkbox,onValueChange=model::enableDeadline),verticalAlignment=Alignment.CenterVertically) {
                Checkbox(checked=form.deadlineEnabled,onCheckedChange=null,enabled=!state.busy)
                Text(tr("Add a deadline"),style=PawQuestTheme.typography.titleMedium)
            }
            if(form.deadlineEnabled) {
                OutlinedTextField(form.deadline,model::editDeadline,modifier=Modifier.fillMaxWidth(),
                    label={Text(tr("Due date & time"))},singleLine=true,enabled=!state.busy,
                    isError=form.deadlineError!=null,supportingText={Text(tr(form.deadlineError ?: "YYYY-MM-DD HH:mm - ${TimeZone.currentSystemDefault().id}"))},
                    colors=fieldColors(),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done))
                DeadlineChooser(form.deadline,!state.busy,model::editDeadline)
            }
        } else Text(tr("Daily quests end at local midnight. Reminders use your device timezone."),color=PawQuestTheme.colors.textSecondary)
        Panel { Text(tr("Reward preview"), style = PawQuestTheme.typography.titleMedium); Rewards(form.preview)
            Text(tr("Estimated before daily limits. Creating a quest does not grant rewards."), style = PawQuestTheme.typography.bodyMedium) }
        state.error?.let { Failure(it) }
        Action(if (state.busy) "Saving…" else "Save quest", {
            keyboard?.hide(); focus.clearFocus(); model.save()
        }, enabled = !state.busy, loading = state.busy)
        Action("Cancel", model::back, enabled = !state.busy, secondary = true)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable private fun CategorySelector(value: String, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(tr("Category: ${tr(value)}"), color = PawQuestTheme.colors.textPrimary)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            (listOf("Focus", "Study", "Work", "Health", "Personal", "Other") + value).distinct().forEach { category ->
                DropdownMenuItem(text = { Text(tr(category)) }, onClick = { onSelect(category); expanded = false })
            }
        }
    }
}

@Composable private fun QuestDetails(state: QuestManagementState, model: QuestViewModel, modifier: Modifier,
    focus: com.pawquest.presentation.focus.FocusState?,resume: (()->Unit)?) {
    val item = state.selected
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (item == null) { Text(tr("This quest is no longer available.")); return@Column }
        Title(item.quest.title, systemCopy = false)
        Text(tr("${tr(item.quest.category)} · ${if(item.quest.repeatType == QuestRepeatType.DAILY) "Daily" else "One-time"}"))
        Text(tr("${item.quest.durationMinutes} minutes"), style = PawQuestTheme.typography.titleMedium)
        item.quest.dueAt?.let {Text(tr("Due ${QuestDeadlineInput.format(it)} (${TimeZone.currentSystemDefault().id})"))}
        if(item.quest.repeatType==QuestRepeatType.DAILY) Text(tr("Due each local day at midnight"))
        Text(if (item.quest.description.isBlank()) tr("No description added.") else item.quest.description)
        Panel { Text(tr("Reward preview"), style = PawQuestTheme.typography.titleMedium); Rewards(item.preview)
            Text(tr("Actual rewards depend on session completion and daily limits.")) }
        state.notice?.let { Notice(it) }
        state.error?.let { Failure(it) }
        val active=focus?.progressForQuest(item.quest.id)
        if(active!=null && resume!=null) {QuestCountdown(active);Action("Resume Focus",resume)}
        else Action("Start Quest", model::start, enabled = !state.busy, loading = state.busy)
        Action("Edit quest", model::openEdit, enabled = !state.busy, secondary = true)
        Action("Delete quest", model::requestDelete, enabled = !state.busy, secondary = true)
        Text(tr("Created ${item.quest.createdAt.toString().take(10)} · UTC"), style = PawQuestTheme.typography.bodyMedium)
    }
}

@Composable private fun Rewards(preview: QuestRewardPreview) {
    Text(tr("${preview.coins} Coins  ·  ${preview.xp} Pet XP"), style = PawQuestTheme.typography.titleMedium,
        color = PawQuestTheme.colors.textPrimary)
}
@Composable private fun Title(text: String, systemCopy: Boolean = true) {
    Text(if (systemCopy) tr(text) else text, style = PawQuestTheme.typography.headlineMedium, color = PawQuestTheme.colors.textPrimary,
        modifier = Modifier.semantics { heading() })
}
@Composable private fun QuestCountdown(progress: QuestFocusProgress) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(tr(if(progress.awaitingConfirmation) "Focus finished" else "In progress"),
            color=PawQuestTheme.colors.primaryDark,style=PawQuestTheme.typography.labelLarge)
        Text(progress.timeLabel,style=PawQuestTheme.typography.labelLarge)
    }
    LinearProgressIndicator(progress={progress.elapsedFraction},modifier=Modifier.fillMaxWidth().height(6.dp),
        color=PawQuestTheme.colors.primaryDark,trackColor=PawQuestTheme.colors.surface)
    if(progress.awaitingConfirmation) Text(tr("Resume Focus to confirm completion and rewards."),style=PawQuestTheme.typography.labelSmall)
}

@Composable private fun Panel(highlight: Boolean=false,content: @Composable ColumnScope.() -> Unit) {
    Surface(color = if(highlight) PawQuestTheme.colors.primaryContainer else PawQuestTheme.colors.surface, shape = PawQuestTheme.shapes.card,
        border = BorderStroke(2.dp, if(highlight) PawQuestTheme.colors.primaryDark else PawQuestTheme.colors.borderLight), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
@Composable private fun Action(text: String, onClick: () -> Unit, enabled: Boolean = true,
    loading: Boolean = false, secondary: Boolean = false) {
    PawQuestButton(text = text, onClick = onClick, modifier = Modifier.fillMaxWidth(), enabled = enabled,
        loading = loading, variant = if(secondary) ButtonVariant.Secondary else ButtonVariant.Primary,
        labelColor = PawQuestTheme.colors.textPrimary)
}
@Composable private fun Notice(text: String) {
    Text(tr(text), color = PawQuestTheme.colors.textPrimary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}
@Composable private fun Failure(text: String) {
    Surface(color = PawQuestTheme.colors.errorContainer, shape = PawQuestTheme.shapes.card) {
        Text(tr(text), Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Assertive }, color = PawQuestTheme.colors.textPrimary)
    }
}
@Composable private fun Loading(text: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(); Text(tr(text)) }
}
@Composable private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = PawQuestTheme.colors.textPrimary, unfocusedTextColor = PawQuestTheme.colors.textPrimary,
    focusedLabelColor = PawQuestTheme.colors.textPrimary, unfocusedLabelColor = PawQuestTheme.colors.textSecondary,
    cursorColor = PawQuestTheme.colors.textPrimary, focusedBorderColor = PawQuestTheme.colors.primaryDark)

@Composable internal expect fun QuestBackHandler(enabled: Boolean, onBack: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DeadlineChooser(value: String,enabled: Boolean,onChange: (String)->Unit) {
    var dateOpen by remember {mutableStateOf(false)}
    var date by remember {mutableStateOf<LocalDate?>(null)}
    var timeOpen by remember {mutableStateOf(false)}
    val initial=remember(value) {runCatching {QuestDeadlineInput.parse(value).toLocalDateTime(TimeZone.currentSystemDefault())}.getOrNull()}
    val dateState=rememberDatePickerState(initialSelectedDateMillis=initial?.date?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds())
    val timeState=rememberTimePickerState(initialHour=initial?.hour ?: 18,initialMinute=initial?.minute ?: 0,is24Hour=true)
    OutlinedButton(onClick={dateOpen=true},enabled=enabled) {Text(tr("Choose date & time"))}
    if(dateOpen) DatePickerDialog(onDismissRequest={dateOpen=false},confirmButton={
        TextButton(onClick={
            dateState.selectedDateMillis?.let { date=Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date;dateOpen=false;timeOpen=true }
        },enabled=dateState.selectedDateMillis!=null) {Text(tr("Next"))}
    },dismissButton={TextButton(onClick={dateOpen=false}) {Text(tr("Cancel"))}}) {DatePicker(dateState)}
    if(timeOpen) AlertDialog(onDismissRequest={timeOpen=false},title={Text(tr("Choose time"))},
        text={Column(Modifier.verticalScroll(rememberScrollState())) {TimeInput(timeState)}},
        confirmButton={TextButton(onClick={
            date?.let {onChange("$it ${timeState.hour.toString().padStart(2,'0')}:${timeState.minute.toString().padStart(2,'0')}")};timeOpen=false
        }) {Text(tr("Set deadline"))}},dismissButton={TextButton(onClick={timeOpen=false}) {Text(tr("Cancel"))}})
}
