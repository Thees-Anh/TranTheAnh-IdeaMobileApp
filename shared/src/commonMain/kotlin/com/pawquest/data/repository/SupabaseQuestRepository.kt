package com.pawquest.data.repository

import com.pawquest.domain.model.*
import com.pawquest.domain.repository.QuestRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Instant
import com.pawquest.data.offline.save
import com.pawquest.data.offline.savedAt

@Serializable private data class Archived(val archived: Boolean)
@Serializable
private data class QuestRecord(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val description: String,
    val category: String,
    @SerialName("duration_minutes") val durationMinutes: Int,
    @SerialName("repeat_type") val repeatType: String,
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("created_at") val createdAt: String,
) {
    fun domain() = Quest(id, userId, title, description, category, durationMinutes,
        QuestRepeatType.valueOf(repeatType.uppercase()), Instant.parse(createdAt), dueAt?.let(Instant::parse))
}

class SupabaseQuestRepository(private val client: SupabaseClient,
    private val offline: com.pawquest.data.offline.OfflineCache = com.pawquest.data.offline.MemoryOfflineCache()) : QuestRepository {
    private val cacheJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private suspend fun owner(): String {
        client.auth.awaitInitialization()
        return client.auth.currentUserOrNull()?.id
            ?: throw QuestException(QuestFailure.AUTHENTICATION_REQUIRED)
    }
    private fun QuestDraft.write(): JsonObject = validated().let {
        buildJsonObject {
            put("title",it.title);put("description",it.description);put("category",it.category)
            put("duration_minutes",it.durationMinutes);put("repeat_type",it.repeatType.name.lowercase())
            put("due_at",it.dueAt?.toString()?.let(::JsonPrimitive) ?: JsonNull)
        }
    }

    override suspend fun create(draft: QuestDraft): Quest = request {
        val payload = draft.write()
        val userId = owner()
        // user_id, UUID and created_at use database defaults; RLS verifies ownership.
        client.postgrest["quests"].insert(payload) { select() }.decodeSingle<QuestRecord>()
            .also { check(it.userId == userId) }.domain()
    }

    override suspend fun update(id: String, draft: QuestDraft): Quest = request {
        validateQuestId(id)
        val payload = draft.write()
        val userId = owner()
        val rows = client.postgrest["quests"].update(payload) {
            select()
            filter { eq("id", id); eq("user_id", userId); eq("archived", false) }
        }.decodeList<QuestRecord>()
        rows.singleOrNull()?.domain() ?: throw QuestException(QuestFailure.NOT_FOUND)
    }

    override suspend fun delete(id: String) = request {
        validateQuestId(id)
        val userId = owner()
        // Never remove sessions or receipts. Starting archived definitions is refused by the server.
        val rows = client.postgrest["quests"].update(Archived(true)) {
            select()
            filter { eq("id", id); eq("user_id", userId) }
        }.decodeList<QuestRecord>()
        if (rows.isEmpty()) throw QuestException(QuestFailure.NOT_FOUND)
    }

    private fun cachedQuests(): List<Quest>? {
        val cached = offline.read(com.pawquest.data.offline.OfflineKeys.QUESTS)?.let {
            runCatching { cacheJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(QuestRecord.serializer()), it).map { r -> r.domain() } }.getOrNull()
        } ?: return null
        com.pawquest.data.offline.ConnectionStatus.usingCache(offline.savedAt(com.pawquest.data.offline.OfflineKeys.QUESTS))
        return cached
    }
    override suspend fun getQuests(): List<Quest> {
        // No usable session yet (initializing or refresh failing offline): don't wait on the SDK's retry loop.
        if (client.auth.sessionStatus.value !is io.github.jan.supabase.auth.status.SessionStatus.Authenticated)
            cachedQuests()?.let { return it }
        return loadQuests()
    }
    private suspend fun loadQuests(): List<Quest> = try {
        request {
            val userId = owner()
            val records = mutableListOf<QuestRecord>()
            var offset = 0L
            do {
                val page = client.postgrest["quests"].select {
                    filter { eq("user_id", userId); eq("archived", false) }
                    order("created_at", Order.DESCENDING)
                    order("id", Order.DESCENDING)
                    range(offset, offset + 199)
                }.decodeList<QuestRecord>()
                records += page
                offset += page.size
            } while (page.size == 200)
            offline.save(com.pawquest.data.offline.OfflineKeys.QUESTS,
                cacheJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(QuestRecord.serializer()), records))
            com.pawquest.data.offline.ConnectionStatus.online()
            records.map { it.domain() }
        }
    } catch (error: QuestException) {
        // Offline viewing: the last confirmed list; creating/editing still requires the server.
        if (error.failure != QuestFailure.NETWORK) throw error
        cachedQuests() ?: throw error
    }

    private suspend fun <T> request(block: suspend () -> T): T = try { block() }
    catch (error: CancellationException) { throw error }
    catch (error: QuestException) { throw error }
    catch (error: IllegalArgumentException) { throw error }
    catch (error: RestException) {
        throw QuestException(if (error.statusCode == 401) QuestFailure.AUTHENTICATION_REQUIRED else QuestFailure.SERVICE)
    }
    catch (_: Exception) { throw QuestException(QuestFailure.NETWORK) }
}

class UnconfiguredQuestRepository : QuestRepository {
    private fun unavailable(): Nothing = throw QuestException(QuestFailure.NOT_CONFIGURED)
    override suspend fun create(draft: QuestDraft): Quest = unavailable()
    override suspend fun update(id: String, draft: QuestDraft): Quest = unavailable()
    override suspend fun delete(id: String): Unit = unavailable()
    override suspend fun getQuests(): List<Quest> = unavailable()
}
