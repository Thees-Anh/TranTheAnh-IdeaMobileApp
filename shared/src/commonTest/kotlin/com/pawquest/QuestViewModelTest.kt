package com.pawquest

import com.pawquest.domain.model.*
import com.pawquest.domain.repository.*
import com.pawquest.domain.usecase.*
import com.pawquest.presentation.quests.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class QuestViewModelTest {
    private val id = "20000000-0000-0000-0000-000000000001"
    private val time = Instant.parse("2026-11-05T00:00:00Z")
    private inner class Repository : QuestRepository {
        var values = emptyList<Quest>()
        var fail = false
        var writes = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun getQuests(): List<Quest> {
            if (fail) throw QuestException(QuestFailure.NETWORK)
            return values
        }
        override suspend fun create(draft: QuestDraft): Quest {
            writes++; gate?.await()
            if(fail) throw QuestException(QuestFailure.NETWORK)
            return Quest(id,"owner",draft.title,draft.description,draft.category,draft.durationMinutes,draft.repeatType,time)
                .also { values=listOf(it) }
        }
        override suspend fun update(id: String, draft: QuestDraft) = create(draft)
        override suspend fun delete(id: String) { writes++; values=emptyList() }
    }
    private inner class Sessions : QuestSessionRepository {
        var count = 0
        var starts = 0
        var fail = false
        var completions = QuestCompletions()
        override suspend fun dailyRewardCount(): Int { delay(1); if(fail) error("offline"); return count }
        override suspend fun completedQuests(dailyIds: Collection<String>, oneTimeIds: Collection<String>,
            today: kotlinx.datetime.LocalDate) = completions
        override suspend fun start(questId: String): StartedQuestSession {
            starts++
            return StartedQuestSession("session",questId,time)
        }
    }
    private fun model(repo: Repository, sessions: Sessions) = QuestViewModel(
        GetQuests(repo), CreateQuest(repo), UpdateQuest(repo), DeleteQuest(repo),sessions)

    @Test fun todayProgressFiltersAndDirectStartFollowCompletions() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            fun quest(n: Int, repeat: QuestRepeatType, category: String = "Study") =
                Quest("20000000-0000-0000-0000-00000000000$n","owner","Quest $n","",category,25,repeat,time)
            val daily=quest(1,QuestRepeatType.DAILY); val dailyDone=quest(2,QuestRepeatType.DAILY)
            val oneTimeOld=quest(3,QuestRepeatType.ONE_TIME,"Focus"); val oneTimeToday=quest(4,QuestRepeatType.ONE_TIME)
            val repo=Repository().apply { values=listOf(daily,dailyDone,oneTimeOld,oneTimeToday) }
            val sessions=Sessions().apply {
                completions=QuestCompletions(setOf(dailyDone.id,oneTimeOld.id,oneTimeToday.id),setOf(dailyDone.id,oneTimeToday.id))
            }
            val model=model(repo,sessions)
            advanceUntilIdle()
            val state=model.state.value
            // A one-time quest finished on an earlier day leaves today's tally but still shows as done.
            assertEquals(listOf(daily.id,dailyDone.id,oneTimeToday.id),state.todayItems.map { it.quest.id })
            assertEquals(2,state.doneCount)
            assertEquals(daily.id,state.nextQuest?.quest?.id)
            model.selectFilter(QuestFilter.TO_DO)
            assertEquals(listOf(daily.id),model.state.value.visibleItems.map { it.quest.id })
            model.selectFilter(QuestFilter.DONE)
            assertEquals(setOf(dailyDone.id,oneTimeOld.id,oneTimeToday.id),model.state.value.visibleItems.map { it.quest.id }.toSet())
            model.selectFilter(QuestFilter.DAILY)
            assertEquals(listOf(daily.id,dailyDone.id),model.state.value.visibleItems.map { it.quest.id })
            assertEquals("Other",displayCategory("Focus")); assertEquals("Health",displayCategory("health"))
            model.startQuest(daily.id)
            assertEquals(daily.id,model.state.value.requestedFocusQuestId)
            model.consumeFocusRequest(); model.startQuest("unknown")
            assertNull(model.state.value.requestedFocusQuestId)
            assertEquals(0,sessions.starts)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun loadRetryAndUnknownProgressDoNotPretendToBeZero() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo=Repository().apply { fail=true }
            val sessions=Sessions().apply { fail=true }
            val model=model(repo,sessions)
            assertTrue(model.state.value.loading)
            advanceUntilIdle()
            assertNotNull(model.state.value.error)
            assertNull(model.state.value.dailyCount)
            assertTrue(model.state.value.progressError)
            repo.fail=false; sessions.fail=false; sessions.count=3
            model.refresh(); advanceUntilIdle()
            assertTrue(model.state.value.items.isEmpty())
            assertEquals(3,model.state.value.dailyCount)
            assertNull(model.state.value.error)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun createEditDetailsAndConfirmedDeleteUseExistingCrudAndPreviewRules() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo=Repository(); val model=model(repo,Sessions()); advanceUntilIdle()
            model.openCreate(); model.save(); advanceUntilIdle()
            assertNotNull(model.state.value.form.titleError)
            assertEquals(0,repo.writes)
            model.editTitle("Read"); model.editDuration(75); model.editRepeat(QuestRepeatType.DAILY)
            assertEquals(QuestRewardPreview(80,50),model.state.value.form.preview)
            model.editDuration(91)
            assertEquals(75,model.state.value.form.durationMinutes)
            model.save(); advanceUntilIdle()
            assertEquals(QuestPage.DETAILS,model.state.value.page)
            assertEquals(QuestRepeatType.DAILY,model.state.value.selected?.quest?.repeatType)
            assertEquals(QuestRewardPreview(80,50),model.state.value.selected?.preview)
            model.openEdit(); model.editTitle("Read more"); model.editDuration(5); model.save(); advanceUntilIdle()
            assertEquals("Read more",model.state.value.selected?.quest?.title)
            assertEquals(QuestRewardPreview(10,5),model.state.value.selected?.preview)
            model.requestDelete(); model.dismissDelete()
            assertEquals(1,repo.values.size)
            model.requestDelete(); model.confirmDelete(); advanceUntilIdle()
            assertEquals(QuestPage.LIST,model.state.value.page)
            assertTrue(model.state.value.items.isEmpty())
        } finally { Dispatchers.resetMain() }
    }
    @Test fun busyGuardPreventsDuplicateSavesAndFailedSaveRetainsForm() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo=Repository(); val model=model(repo,Sessions()); advanceUntilIdle()
            model.openCreate(); model.editTitle("Study")
            repo.gate=CompletableDeferred()
            model.save(); model.save(); runCurrent()
            assertTrue(model.state.value.busy)
            assertEquals(1,repo.writes)
            model.back(); assertEquals(QuestPage.CREATE,model.state.value.page)
            repo.fail=true; repo.gate!!.complete(Unit); advanceUntilIdle()
            assertFalse(model.state.value.busy)
            assertEquals("Study",model.state.value.form.title)
            assertNotNull(model.state.value.error)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun dirtyFormNeedsConfirmationAndStartCallsServerWithoutChangingProgress() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo=Repository(); val sessions=Sessions(); val model=model(repo,sessions); advanceUntilIdle()
            model.openCreate(); model.editTitle("Walk"); model.back()
            assertTrue(model.state.value.discardConfirmation)
            model.dismissDiscard(); assertEquals("Walk",model.state.value.form.title)
            model.save(); advanceUntilIdle()
            model.start(); model.start(); advanceUntilIdle()
            assertEquals(0,sessions.starts)
            assertEquals(0,model.state.value.dailyCount)
            assertEquals(id,model.state.value.requestedFocusQuestId)
            model.openEdit(); model.editTitle("Changed"); model.back(); model.leaveForm()
            assertEquals(QuestPage.DETAILS,model.state.value.page)
            assertEquals("Walk",model.state.value.selected?.quest?.title)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun reminderOpensListButDoesNotSilentlyDiscardAnEditedQuest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo=Repository();val model=model(repo,Sessions());advanceUntilIdle()
            model.openCreate();model.editTitle("Walk");model.save();advanceUntilIdle()
            assertEquals(QuestPage.DETAILS,model.state.value.page)
            model.openFromReminder();advanceUntilIdle()
            assertEquals(QuestPage.LIST,model.state.value.page)
            model.openDetails(id);model.openEdit();model.editTitle("Unfinished edit")
            model.openFromReminder()
            assertTrue(model.state.value.discardConfirmation)
            assertEquals("Unfinished edit",model.state.value.form.title)
            model.dismissDiscard();assertEquals(QuestPage.EDIT,model.state.value.page)
            model.openFromReminder();model.leaveForm();advanceUntilIdle()
            assertEquals(QuestPage.LIST,model.state.value.page)
            assertEquals(1,repo.writes)
        } finally {Dispatchers.resetMain()}
    }
}
