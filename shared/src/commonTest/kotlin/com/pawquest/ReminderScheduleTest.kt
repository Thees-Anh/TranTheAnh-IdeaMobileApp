package com.pawquest

import com.pawquest.domain.model.*
import kotlin.test.*
import kotlin.time.Instant

class ReminderScheduleTest {
    private val now = Instant.parse("2026-03-10T02:00:00Z") // 09:00 in Asia/Bangkok (UTC+7)
    private fun quest(id: String, due: String) = PendingQuestReminder(id, "Quest $id", Instant.parse(due))
    private fun schedule(daily: Boolean, vararg quests: PendingQuestReminder) =
        ReminderSchedule("owner", now, "Asia/Bangkok", daily, quests.toList())

    @Test fun alarmsFireTwoHoursBeforeDeadlinesInsideLocalWakingHours() {
        val alarms = ReminderScheduleRules.alarms(schedule(true,
            quest("evening", "2026-03-10T15:00:00Z"),      // 22:00 local -> 20:00
            quest("past-midnight", "2026-03-10T17:30:00Z"), // 00:30 local -> 22:30
            quest("night", "2026-03-10T18:30:00Z"),        // would be 23:30 -> skipped
            quest("soon", "2026-03-10T03:00:00Z"),         // window already open -> regular sync
            quest("early", "2026-03-11T01:30:00Z"),        // 06:30 local -> moved to 08:00
            quest("too-early", "2026-03-11T00:30:00Z"),    // 08:00 is after its 07:30 deadline -> skipped
        ), now)
        assertEquals(listOf(
            Instant.parse("2026-03-10T13:00:00Z"),
            Instant.parse("2026-03-10T15:30:00Z"),
            Instant.parse("2026-03-11T01:00:00Z"),
        ), alarms.map { it.at })
        assertTrue(alarms.all { it.kind == ReminderKind.QUESTS_DUE })
    }

    @Test fun dailyIdeaIsScheduledForTheNextNineAmOnlyWithoutDailyQuest() {
        assertEquals(listOf(ScheduledReminder(Instant.parse("2026-03-11T02:00:00Z"), ReminderKind.DAILY_IDEA)),
            ReminderScheduleRules.alarms(schedule(false), now))
        assertEquals(Instant.parse("2026-03-10T02:00:00Z"),
            ReminderScheduleRules.alarms(schedule(false), Instant.parse("2026-03-10T01:59:00Z")).single().at)
        assertTrue(ReminderScheduleRules.alarms(schedule(true), now).isEmpty())
    }

    @Test fun offlineContextMatchesTheServerTwoHourWindow() {
        val cached = schedule(true, quest("evening", "2026-03-10T15:00:00Z"), quest("later", "2026-03-10T17:30:00Z"))
        val at = Instant.parse("2026-03-10T13:00:00Z")
        val context = ReminderScheduleRules.contextAt(cached, at)
        assertEquals(listOf("evening"), context.pendingQuests.map { it.id })
        val reminder = CompanionReminderRules.candidates(context, PetStats()).single()
        assertEquals(ReminderKind.QUESTS_DUE, reminder.kind)
    }

    @Test fun laterDeadlineOnTheSameDayIsNotSuppressedByAnEarlierReminder() {
        val zone = "Asia/Bangkok"
        val first = CompanionReminderRules.candidates(ReminderContext("o", Instant.parse("2026-03-10T03:00:00Z"), zone, true,
            listOf(quest("a", "2026-03-10T04:00:00Z"))), PetStats()).single()
        val second = CompanionReminderRules.candidates(ReminderContext("o", Instant.parse("2026-03-10T13:00:00Z"), zone, true,
            listOf(quest("b", "2026-03-10T15:00:00Z"))), PetStats()).single()
        assertEquals(first.day, second.day)
        assertEquals(listOf(second), ReminderDeliveryRules.pending(listOf(second), true, true, setOf(ReminderDeliveryRules.key(first))))
        assertTrue(ReminderDeliveryRules.pending(listOf(first), true, true, setOf(ReminderDeliveryRules.key(first))).isEmpty())
    }

    @Test fun inexactHopsAlwaysWakeBeforeTheReminderAndEndWithinTwelveMinutes() {
        val minute = 60_000L
        for (start in listOf(17 * 60L, 140L, 90L, 45L, 23L, 14L, 13L).map { it * minute }) {
            // Worst case: every prepare alarm is delivered as late as Android allows.
            var remaining = start
            var hops = 0
            while (true) {
                val wait = ReminderScheduleRules.inexactPrepareWait(remaining) ?: break
                val latest = wait + ReminderScheduleRules.inexactWindow(wait)
                assertTrue(latest < remaining, "a late prepare at $remaining ms still fires before the reminder")
                remaining -= latest
                assertTrue(++hops < 10, "converges")
            }
            assertTrue(remaining <= ReminderScheduleRules.FINAL_LEAD_MILLIS)
        }
        assertNull(ReminderScheduleRules.inexactPrepareWait(5 * minute))
    }
}
