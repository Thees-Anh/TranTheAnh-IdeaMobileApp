package com.pawquest.domain.model

import kotlin.time.Instant
import kotlinx.datetime.*

enum class PetIconMood { NORMAL, HUNGRY, BORED }
fun petIconMood(stats: PetStats): PetIconMood = when {
    stats.hunger <= 25 -> PetIconMood.HUNGRY
    stats.happiness <= 25 -> PetIconMood.BORED
    else -> PetIconMood.NORMAL
}

data class PendingQuestReminder(val id: String,val title: String,val dueAt: Instant)
data class ReminderContext(val userId: String,val serverNow: Instant,val timezone: String,
    val hasDailyQuest: Boolean,val pendingQuests: List<PendingQuestReminder>)
enum class ReminderKind { DAILY_IDEA, QUESTS_DUE, HUNGRY, BORED, THIRSTY }
/** [scope] distinguishes reminders of one kind on one day, e.g. a later deadline after an earlier one was sent. */
data class CompanionReminder(val kind: ReminderKind,val day: String,val title: String,val body: String,val destination: String,
    val scope: String="")

/** Pure presentation rules. Caller persists sent kind/day only after successful notification posting. */
object CompanionReminderRules {
    fun candidates(context: ReminderContext,pet: PetStats,petSleeping: Boolean=false): List<CompanionReminder> {
        val local=context.serverNow.toLocalDateTime(TimeZone.of(context.timezone))
        if(local.hour<8 || local.hour>=23) return emptyList()
        val day=local.date.toString()
        return buildList {
            if(!context.hasDailyQuest && local.hour in 9..11)
                add(CompanionReminder(ReminderKind.DAILY_IDEA,day,"Một ngày mới cùng PawQuest",
                    "Hôm nay bạn có muốn làm gì không? Tạo một nhiệm vụ hằng ngày nhỏ nhé.","quests"))
            val pending=context.pendingQuests.filter {it.dueAt <= context.serverNow + kotlin.time.Duration.parse("2h")}
            if(pending.isNotEmpty()) add(CompanionReminder(ReminderKind.QUESTS_DUE,day,"Một chút tập trung nữa nhé",
                if(pending.size==1) "Bạn còn nhiệm vụ “${pending.first().title.take(70)}” cần hoàn thành."
                else "Bạn còn ${pending.size} nhiệm vụ cần hoàn thành. Mở PawQuest để xem nhé.","quests",
                pending.map {it.id}.sorted().joinToString(",")))
            if(!petSleeping && pet.hunger<=25) add(CompanionReminder(ReminderKind.HUNGRY,day,"Bạn nhỏ đang đói rồi",
                "Bát thức ăn đang chờ! Bạn cho thú cưng ăn một chút nhé.","pet_home"))
            if(!petSleeping && pet.happiness<=25) add(CompanionReminder(ReminderKind.BORED,day,"Bạn nhỏ đang nhớ bạn",
                "Thú cưng hơi chán rồi. Dành một chút thời gian chơi cùng bạn nhỏ nhé.","pet_home"))
            if(!petSleeping && pet.hydration<=25) add(CompanionReminder(ReminderKind.THIRSTY,day,"Bạn nhỏ cần uống nước",
                "Cho thú cưng uống một chút nước để lại vui khỏe nhé.","pet_home"))
        }
    }
}

object ReminderDeliveryRules {
    fun key(reminder: CompanionReminder)="${reminder.day}:${reminder.kind.name}"+
        (if(reminder.scope.isEmpty()) "" else ":"+reminder.scope.hashCode().toUInt().toString(16))
    fun pending(reminders: List<CompanionReminder>,userOptIn: Boolean,systemAllowed: Boolean,delivered: Set<String>): List<CompanionReminder> =
        if(!userOptIn || !systemAllowed) emptyList() else reminders.filter {key(it) !in delivered}
}

/** Upcoming quest deadlines (next 48h) used to schedule on-device alarms. */
data class ReminderSchedule(val userId: String,val serverNow: Instant,val timezone: String,
    val hasDailyQuest: Boolean,val upcoming: List<PendingQuestReminder>)
data class ScheduledReminder(val at: Instant,val kind: ReminderKind)

/**
 * Local alarm times that mirror [CompanionReminderRules]: a deadline becomes due 2 hours before it,
 * reminders only post 08:00–22:59 local time, and the daily idea appears at 09:00.
 */
object ReminderScheduleRules {
    private val lead=kotlin.time.Duration.parse("2h")
    fun alarms(schedule: ReminderSchedule,now: Instant): List<ScheduledReminder> {
        val zone=TimeZone.of(schedule.timezone)
        val quests=schedule.upcoming.mapNotNull { quest ->
            var at=quest.dueAt-lead
            if(at<=now) return@mapNotNull null // already inside its window: the regular sync posts it
            val local=at.toLocalDateTime(zone)
            if(local.hour>=23) return@mapNotNull null // next allowed time is after the deadline
            if(local.hour<8) at=LocalDateTime(local.date,LocalTime(8,0)).toInstant(zone)
            if(at>=quest.dueAt || at<=now) null else ScheduledReminder(at,ReminderKind.QUESTS_DUE)
        }
        val idea=if(schedule.hasDailyQuest) null else {
            val today=now.toLocalDateTime(zone).date
            listOf(today,today.plus(1,DateTimeUnit.DAY)).map {LocalDateTime(it,LocalTime(9,0)).toInstant(zone)}
                .firstOrNull {it>now}?.let {ScheduledReminder(it,ReminderKind.DAILY_IDEA)}
        }
        return (quests+listOfNotNull(idea)).distinctBy {it.at to it.kind}.sortedBy {it.at}
    }

    const val FINAL_LEAD_MILLIS=12*60_000L
    private const val MIN_WINDOW=10*60_000L
    private const val MAX_WINDOW=60*60_000L
    private const val MARGIN=2*60_000L
    /** Android may deliver an inexact alarm up to max(10 min, min(75% of the wait, 1 h)) late. */
    fun inexactWindow(waitMillis: Long)=maxOf(MIN_WINDOW,minOf(waitMillis*3/4,MAX_WINDOW))

    /**
     * Without exact-alarm access: how long to wait before a "prepare" wake-up that re-arms closer to the
     * target, chosen so that even a maximally late prepare still fires before the reminder. Null when the
     * reminder itself should be armed now (it is within [FINAL_LEAD_MILLIS], i.e. ~10 minutes precision).
     */
    fun inexactPrepareWait(remainingMillis: Long): Long? {
        if(remainingMillis<=FINAL_LEAD_MILLIS) return null
        val budget=remainingMillis-MARGIN
        return when {
            budget-MAX_WINDOW>=MAX_WINDOW*4/3 -> budget-MAX_WINDOW
            budget*4/7>=MIN_WINDOW*4/3 -> budget*4/7
            else -> budget-MIN_WINDOW
        }.coerceAtLeast(0)
    }

    /** Offline fallback: the reminder context the server would have returned at [at], from the cached schedule. */
    fun contextAt(schedule: ReminderSchedule,at: Instant)=ReminderContext(schedule.userId,at,schedule.timezone,
        schedule.hasDailyQuest,schedule.upcoming.filter {it.dueAt>at && it.dueAt<=at+lead})
}

object QuestDeadlineInput {
    fun parse(value: String,zone: TimeZone=TimeZone.currentSystemDefault()): Instant {
        require(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}").matches(value)) { "Use YYYY-MM-DD HH:mm." }
        val local=LocalDateTime.parse(value.replace(' ','T'))
        val instant=local.toInstant(zone)
        require(instant.toLocalDateTime(zone)==local) { "That local time does not exist. Choose another time." }
        return instant
    }
    fun format(value: Instant,zone: TimeZone=TimeZone.currentSystemDefault()): String {
        val local=value.toLocalDateTime(zone)
        return "${local.date} ${local.hour.toString().padStart(2,'0')}:${local.minute.toString().padStart(2,'0')}"
    }
}
