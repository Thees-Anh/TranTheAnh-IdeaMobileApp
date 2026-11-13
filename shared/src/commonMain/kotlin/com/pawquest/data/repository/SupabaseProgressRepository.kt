package com.pawquest.data.repository

import com.pawquest.domain.model.*
import com.pawquest.domain.repository.ProgressRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.*

@Serializable private data class DayRecord(val day: String,val count: Int) {
    fun domain()=HabitDay(LocalDate.parse(day),count)
}
@Serializable private data class BadgeRecord(val code: String,val name: String,val days: Int,
    @SerialName("earned_on") val earned: String?=null)
@Serializable private data class EventRecord(val id: String,val kind: String,val name: String,
    @SerialName("from_level") val from: Long?=null,@SerialName("to_level") val to: Long?=null,
    @SerialName("food_quantity") val food: Int=0,@SerialName("badge_code") val badge: String?=null)
@Serializable private data class ProgressRecord(@SerialName("user_id") val owner: String,val today: String,val month: String,
    @SerialName("current_streak") val current: Int,@SerialName("best_streak") val best: Int,
    val days: List<DayRecord>,val week: List<DayRecord>,@SerialName("today_count") val todayCount: Int,
    @SerialName("rewarded_today") val rewarded: Int,@SerialName("coins_today") val coins: Int,
    val badges: List<BadgeRecord>,val events: List<EventRecord>) {
    fun domain()=ProgressSnapshot(LocalDate.parse(today),LocalDate.parse(month),current,best,days.map {it.domain()},week.map {it.domain()},
        todayCount,rewarded,coins,badges.map {StreakBadge(it.code,it.name,it.days,it.earned?.let(LocalDate::parse))},
        events.map {ProgressEvent(it.id,it.kind,it.name,it.from,it.to,it.food,it.badge)})
}
class SupabaseProgressRepository(private val client: SupabaseClient): ProgressRepository {
    override suspend fun snapshot(month: LocalDate?): ProgressSnapshot {
        client.auth.awaitInitialization()
        val owner=client.auth.currentUserOrNull()?.id ?: error("Please sign in again.")
        return client.postgrest.rpc("progress_snapshot",buildJsonObject {month?.let {put("p_month",it.toString())}})
            .decodeAs<ProgressRecord>().also {check(it.owner==owner)}.domain()
    }
    override suspend fun acknowledge(eventId: String) {
        client.auth.awaitInitialization()
        check(client.auth.currentUserOrNull()!=null)
        client.postgrest.rpc("progress_ack",buildJsonObject {put("p_event_id",eventId)})
    }
}
class UnconfiguredProgressRepository: ProgressRepository {
    override suspend fun snapshot(month: LocalDate?): ProgressSnapshot=error("Progress storage is not configured.")
    override suspend fun acknowledge(eventId: String)=error("Progress storage is not configured.")
}
