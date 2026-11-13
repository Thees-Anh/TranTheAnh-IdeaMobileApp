package com.pawquest.domain.model

import kotlinx.datetime.*

data class HabitDay(val day: LocalDate,val count: Int)
data class StreakBadge(val code: String,val name: String,val days: Int,val earnedOn: LocalDate?)
data class ProgressEvent(val id: String,val kind: String,val name: String,val fromLevel: Long?,val toLevel: Long?,val food: Int,val badgeCode: String?)
data class ProgressSnapshot(val today: LocalDate,val month: LocalDate,val currentStreak: Int,val bestStreak: Int,
    val days: List<HabitDay>,val week: List<HabitDay>,val todayCount: Int,val rewardedToday: Int,val coinsToday: Int,
    val badges: List<StreakBadge>,val events: List<ProgressEvent>)

object HabitCalendarRules {
    fun firstOfMonth(date: LocalDate)=LocalDate(date.year,date.month,1)
    fun shiftMonth(date: LocalDate,months: Int)=firstOfMonth(date)+DatePeriod(months=months)
    fun cells(month: LocalDate): List<LocalDate?> {
        val first=firstOfMonth(month)
        val count=(shiftMonth(first,1).toEpochDays()-first.toEpochDays()).toInt()
        val leading=first.dayOfWeek.isoDayNumber-1
        val cells=List<LocalDate?>(leading){null}+List(count){first+DatePeriod(days=it)}
        return cells+List((7-cells.size%7)%7){null}
    }
}

/**
 * [active]: the Focus screen is showing a running session. [running]: the server session is
 * still counting down, even when the Focus screen is hidden or the app is in the background.
 */
data class FocusPresence(val sessionId: String,val petName: String,val remainingSeconds: Long,val active: Boolean,
    val running: Boolean=active)

/** When the device shows the ongoing Focus countdown and the "time is up" alert. */
object FocusNotificationRules {
    fun showOngoing(permission: Boolean,background: Boolean,presence: FocusPresence?)=
        permission && background && presence?.running==true && presence.remainingSeconds>0
    fun alertAtEnd(permission: Boolean,background: Boolean,presence: FocusPresence?)=
        permission && background && presence?.running==true
}
