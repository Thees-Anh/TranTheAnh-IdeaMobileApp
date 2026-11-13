package com.pawquest

import com.pawquest.domain.model.*
import com.pawquest.domain.repository.QuestRepository
import com.pawquest.domain.usecase.*
import com.pawquest.di.createAppContainer
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Instant

class QuestDomainTest {
    private val id = "10000000-0000-0000-0000-000000000001"
    private val timestamp = Instant.parse("2026-11-05T00:00:00Z")
    private fun draft(minutes: Int = 25) = QuestDraft("  Read Kotlin  ", "Chapter one", " Study ", minutes)

    @Test fun durationAndTextValidationRejectsInvalidDefinitions() {
        for (minutes in listOf(Int.MIN_VALUE, -1, 0, 4, 91, Int.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> { draft(minutes).validated() }
            assertFailsWith<IllegalArgumentException> { questRewardPreview(minutes) }
        }
        for (minutes in listOf(5, 90)) assertEquals(minutes, draft(minutes).validated().durationMinutes)
        for (invalid in listOf(draft().copy(title="  "), draft().copy(title="a".repeat(121)),
            draft().copy(description="a".repeat(2001)), draft().copy(category=" "),
            draft().copy(category="a".repeat(41)))) {
            assertFailsWith<IllegalArgumentException> { invalid.validated() }
        }
        assertEquals("🐱".repeat(120), draft().copy(title="🐱".repeat(120)).validated().title)
    }

    @Test fun everyRewardTierBoundaryMatchesSharedRules() {
        val tiers = listOf(5..14 to (10 to 5),15..29 to (20 to 10),30..44 to (35 to 20),
            45..59 to (50 to 30),60..74 to (65 to 40),75..90 to (80 to 50))
        tiers.forEach { (range, reward) -> range.forEach {
            assertEquals(QuestRewardPreview(reward.first,reward.second), questRewardPreview(it))
        } }
    }

    @Test fun useCasesValidateBeforeRepositoryAndSupportBothRepeatTypes() = runTest {
        val repo = FakeRepository()
        val create = CreateQuest(repo)
        val update = UpdateQuest(repo)
        assertFailsWith<IllegalArgumentException> { create(draft(4)) }
        assertFailsWith<IllegalArgumentException> { update(id, draft(91)) }
        assertFailsWith<IllegalArgumentException> { update("bad", draft()) }
        assertFailsWith<IllegalArgumentException> { DeleteQuest(repo)("bad") }
        assertEquals(0,repo.writes)
        val first = create(draft())
        assertEquals("Read Kotlin",first.title)
        assertEquals("Study",first.category)
        assertEquals(QuestRepeatType.ONE_TIME,first.repeatType)
        val daily = update(id,draft(90).copy(repeatType=QuestRepeatType.DAILY))
        assertEquals(first.id,daily.id)
        assertEquals(first.createdAt,daily.createdAt)
        assertEquals(QuestRepeatType.DAILY,daily.repeatType)
        assertEquals(listOf(daily),GetQuests(repo)())
        DeleteQuest(repo)(id)
        assertTrue(GetQuests(repo)().isEmpty())
    }

    @Test fun diResolvesQuestUseCasesAndDoesNotFallBackToFakePersistence() = runTest {
        val container = createAppContainer()
        try {
            container.koin.get<CreateQuest>()
            container.koin.get<UpdateQuest>()
            container.koin.get<DeleteQuest>()
            val error=assertFailsWith<QuestException> { container.koin.get<GetQuests>()() }
            assertEquals(QuestFailure.NOT_CONFIGURED,error.failure)
        } finally { container.close() }
    }

    private inner class FakeRepository : QuestRepository {
        var writes=0
        var value: Quest?=null
        override suspend fun create(draft: QuestDraft): Quest {
            writes++
            return Quest(id,"owner",draft.title,draft.description,draft.category,draft.durationMinutes,
                draft.repeatType,timestamp).also { value=it }
        }
        override suspend fun update(id: String,draft: QuestDraft): Quest = create(draft)
        override suspend fun delete(id: String) { writes++; value=null }
        override suspend fun getQuests() = listOfNotNull(value)
    }
}
