package com.pawquest.domain.repository

import kotlin.time.Instant

/** [done]: finished (daily: today, one-time: ever). [doneToday]: finished on today's UTC reward day. */
data class QuestCompletions(val done: Set<String> = emptySet(), val doneToday: Set<String> = emptySet())

data class StartedQuestSession(val id: String, val questId: String, val endsAt: Instant)

/** Narrow UI bridge to existing server session operations; never calculates grants. */
interface QuestSessionRepository {
    suspend fun start(questId: String): StartedQuestSession
    suspend fun dailyRewardCount(): Int
    /**
     * Quests the user has finished: daily quests on [today] (UTC reward day, like the habit calendar),
     * one-time quests on any day. Read-only; it never grants anything.
     */
    suspend fun completedQuests(dailyIds: Collection<String>, oneTimeIds: Collection<String>,
        today: kotlinx.datetime.LocalDate): QuestCompletions = QuestCompletions()
}
