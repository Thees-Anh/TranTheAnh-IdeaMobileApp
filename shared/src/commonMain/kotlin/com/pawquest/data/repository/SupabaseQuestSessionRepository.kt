package com.pawquest.data.repository

import com.pawquest.domain.model.validateQuestId
import com.pawquest.domain.repository.QuestSessionRepository
import com.pawquest.domain.repository.StartedQuestSession
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable private data class SessionRecord(val id: String,
    @SerialName("quest_id") val questId: String, @SerialName("ends_at") val endsAt: String)

class SupabaseQuestSessionRepository(private val client: SupabaseClient) : QuestSessionRepository {
    private suspend fun <T> request(block: suspend () -> T): T {
        client.auth.awaitInitialization()
        check(client.auth.currentUserOrNull() != null) { "Please sign in again." }
        return try { block() }
        catch (error: CancellationException) { throw error }
        catch (error: RestException) {
            throw IllegalStateException(if (error.message?.contains("23505") == true) "You already have an active quest session."
                else "Couldn't complete this action. Please retry.")
        }
        catch (_: Exception) { throw IllegalStateException("Couldn't connect. Please retry.") }
    }
    override suspend fun start(questId: String): StartedQuestSession = request {
        validateQuestId(questId)
        client.postgrest.rpc("start_quest_session", buildJsonObject { put("p_quest_id", questId) })
            .decodeAs<SessionRecord>().let { StartedQuestSession(it.id, it.questId, Instant.parse(it.endsAt)) }
    }
    override suspend fun dailyRewardCount(): Int = request {
        client.postgrest.rpc("quest_daily_reward_count").decodeAs<Int>()
    }
}

class UnconfiguredQuestSessionRepository : QuestSessionRepository {
    override suspend fun start(questId: String): StartedQuestSession = error("Quest sessions are not configured.")
    override suspend fun dailyRewardCount(): Int = error("Daily progress is unavailable.")
}
