package com.pawquest.data.repository

import com.pawquest.domain.model.*
import com.pawquest.domain.repository.ReminderRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.*
import kotlin.time.Instant

@Serializable private data class PendingReminderRecord(val id: String,val title: String,@SerialName("due_at") val dueAt: String)
@Serializable private data class ReminderRecord(@SerialName("user_id") val userId: String,
    @SerialName("server_now") val now: String,val timezone: String,
    @SerialName("has_daily_quest") val hasDailyQuest: Boolean,
    @SerialName("pending_quests") val pending: List<PendingReminderRecord>)
@Serializable private data class ScheduleRecord(@SerialName("user_id") val userId: String,
    @SerialName("server_now") val now: String,val timezone: String,
    @SerialName("has_daily_quest") val hasDailyQuest: Boolean,
    val upcoming: List<PendingReminderRecord>)
class SupabaseReminderRepository(private val client: SupabaseClient): ReminderRepository {
    override suspend fun schedule(timezone: String): ReminderSchedule {
        client.auth.awaitInitialization()
        val owner=client.auth.currentUserOrNull()?.id ?: error("Please sign in again.")
        val record=client.postgrest.rpc("reminder_schedule",buildJsonObject {put("p_timezone",timezone)}).decodeAs<ScheduleRecord>()
        check(record.userId==owner && record.timezone==timezone) {"Unexpected reminder schedule"}
        return ReminderSchedule(owner,Instant.parse(record.now),record.timezone,record.hasDailyQuest,
            record.upcoming.map {PendingQuestReminder(it.id,it.title,Instant.parse(it.dueAt))})
    }
    override suspend fun widgetContext(timezone: String): WidgetQuestContext {
        client.auth.awaitInitialization()
        val owner=client.auth.currentUserOrNull()?.id ?: error("Please sign in again.")
        val record=client.postgrest.rpc("widget_context",buildJsonObject {put("p_timezone",timezone)}).decodeAs<WidgetRecord>()
        check(record.owner==owner && record.zone==timezone && record.pending>=0)
        return WidgetQuestContext(owner,Instant.parse(record.now),record.zone,record.pending,
            record.done?.takeIf {it>=0},record.streak?.takeIf {it>=0})
    }
    override suspend fun context(timezone: String): ReminderContext {
        client.auth.awaitInitialization()
        val owner=client.auth.currentUserOrNull()?.id ?: error("Please sign in again.")
        val record=client.postgrest.rpc("reminder_context",buildJsonObject {put("p_timezone",timezone)}).decodeAs<ReminderRecord>()
        check(record.userId==owner && record.timezone==timezone) {"Unexpected reminder context"}
        return ReminderContext(owner,Instant.parse(record.now),record.timezone,record.hasDailyQuest,
            record.pending.map {PendingQuestReminder(it.id,it.title,Instant.parse(it.dueAt))})
    }
}
@Serializable private data class WidgetRecord(@SerialName("user_id") val owner: String,@SerialName("server_now") val now: String,
    @SerialName("timezone") val zone: String,@SerialName("pending_count") val pending: Int,
    @SerialName("completed_today") val done: Int?=null,@SerialName("current_streak") val streak: Int?=null)
class UnconfiguredReminderRepository: ReminderRepository {
    override suspend fun context(timezone: String): ReminderContext=error("Reminder storage is not configured.")
}
