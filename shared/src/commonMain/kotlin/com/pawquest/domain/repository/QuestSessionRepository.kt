package com.pawquest.domain.repository

import kotlin.time.Instant

data class StartedQuestSession(val id: String, val questId: String, val endsAt: Instant)

/** Narrow UI bridge to existing server session operations; never calculates grants. */
interface QuestSessionRepository {
    suspend fun start(questId: String): StartedQuestSession
    suspend fun dailyRewardCount(): Int
}
