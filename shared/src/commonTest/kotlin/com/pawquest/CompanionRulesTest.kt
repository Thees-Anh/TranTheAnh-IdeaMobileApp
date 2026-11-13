package com.pawquest

import com.pawquest.domain.model.*
import kotlinx.datetime.TimeZone
import kotlin.time.Instant
import kotlin.test.*

class CompanionRulesTest {
    private fun context(stamp: String="2026-11-06T02:30:00Z",zone: String="Asia/Bangkok",daily: Boolean=false,
        pending: List<PendingQuestReminder> = emptyList())=ReminderContext("owner",Instant.parse(stamp),zone,daily,pending)
    @Test fun hydrationNeverEscapesBoundsAndDrinkingDoesNotChangeOtherStats() {
        assertFailsWith<IllegalArgumentException> {PetStats(hydration=-1)}
        assertFailsWith<IllegalArgumentException> {PetStats(hydration=101)}
        assertEquals(PetStats(40,50,60,100),PetStats(40,50,60,95).care(PetCareAction.DRINK))
        assertEquals(0,PetStats(hydration=1).afterHours(Long.MAX_VALUE).hydration)
        assertEquals(94,PetStats().afterHours(2).hydration)
    }
    @Test fun drinkRejectsFullWaterOrSleepingAndNeverNeedsFood() {
        assertEquals("already_hydrated",PetCareRules.rejection(PetStats(),0,false,PetCareAction.DRINK))
        assertEquals("pet_sleeping",PetCareRules.rejection(PetStats(hydration=0),0,true,PetCareAction.DRINK))
        assertNull(PetCareRules.rejection(PetStats(hydration=0),0,false,PetCareAction.DRINK))
    }
    @Test fun iconThresholdAndPriorityAreDeterministic() {
        assertEquals(PetIconMood.NORMAL,petIconMood(PetStats(26,26)))
        assertEquals(PetIconMood.BORED,petIconMood(PetStats(26,25)))
        assertEquals(PetIconMood.HUNGRY,petIconMood(PetStats(25,0)))
    }
    @Test fun dailyIdeaUsesLocalMorningAndSkipsExistingDailyDefinitions() {
        assertEquals(listOf(ReminderKind.DAILY_IDEA),CompanionReminderRules.candidates(context(),PetStats()).map {it.kind})
        assertTrue(CompanionReminderRules.candidates(context(daily=true),PetStats()).isEmpty())
        assertTrue(CompanionReminderRules.candidates(context("2026-11-06T05:00:00Z"),PetStats()).isEmpty())
        assertTrue(CompanionReminderRules.candidates(context("2026-11-06T01:59:59Z"),PetStats()).isEmpty())
    }
    @Test fun quietHoursSuppressAllReminderTypes() {
        val low=PetStats(0,0,0,0)
        assertTrue(CompanionReminderRules.candidates(context("2026-11-06T16:00:00Z"),low).isEmpty())
        assertTrue(CompanionReminderRules.candidates(context("2026-11-07T00:59:59Z"),low).isEmpty())
        assertEquals(3,CompanionReminderRules.candidates(context("2026-11-07T01:00:00Z",daily=true),low).size)
    }
    @Test fun careNeedsAreSuppressedWhileSleeping() {
        assertTrue(CompanionReminderRules.candidates(context(daily=true),PetStats(0,0,0,0),petSleeping=true).isEmpty())
    }
    @Test fun groupedDueRemindersUseTwoHourWindowAndExplicitTitlesForOneQuest() {
        val due=PendingQuestReminder("id","Read a chapter",Instant.parse("2026-11-06T04:30:00Z"))
        val result=CompanionReminderRules.candidates(context(daily=true,pending=listOf(due)),PetStats()).single()
        assertEquals(ReminderKind.QUESTS_DUE,result.kind);assertTrue(result.body.contains("Read a chapter"))
        val grouped=CompanionReminderRules.candidates(context(daily=true,pending=listOf(due,due.copy(id="other"))),PetStats()).single()
        assertTrue(grouped.body.contains("2"))
        assertTrue(CompanionReminderRules.candidates(context(daily=true,pending=listOf(due.copy(dueAt=Instant.parse("2026-11-06T04:30:01Z")))),PetStats()).isEmpty())
    }
    @Test fun permissionAndExplicitConsentBothRequiredWithoutMarkingSuppressedAsSent() {
        val reminder=CompanionReminderRules.candidates(context(),PetStats())
        assertTrue(ReminderDeliveryRules.pending(reminder,false,true,emptySet()).isEmpty())
        assertTrue(ReminderDeliveryRules.pending(reminder,true,false,emptySet()).isEmpty())
        assertEquals(reminder,ReminderDeliveryRules.pending(reminder,true,true,emptySet()))
        assertTrue(ReminderDeliveryRules.pending(reminder,true,true,reminder.map(ReminderDeliveryRules::key).toSet()).isEmpty())
    }
    @Test fun sentReceiptsArePerLocalDayAcrossUtcMidnight() {
        val old=CompanionReminderRules.candidates(context("2026-11-06T23:00:00Z","America/New_York",true),PetStats(hunger=0)).single()
        val next=CompanionReminderRules.candidates(context("2026-11-07T14:00:00Z","America/New_York",true),PetStats(hunger=0)).single()
        assertEquals("2026-11-06",old.day);assertEquals("2026-11-07",next.day)
        assertEquals(listOf(next),ReminderDeliveryRules.pending(listOf(next),true,true,setOf(ReminderDeliveryRules.key(old))))
    }
    @Test fun localDeadlineRoundTripsAndRejectsDstGapOrMalformedInput() {
        val bangkok=TimeZone.of("Asia/Bangkok")
        assertEquals(Instant.parse("2026-11-07T11:30:00Z"),QuestDeadlineInput.parse("2026-11-07 18:30",bangkok))
        assertEquals("2026-11-07 18:30",QuestDeadlineInput.format(Instant.parse("2026-11-07T11:30:00Z"),bangkok))
        assertFailsWith<IllegalArgumentException> {QuestDeadlineInput.parse("2026-02-30 10:00",bangkok)}
        assertFailsWith<IllegalArgumentException> {QuestDeadlineInput.parse("2026-03-08 02:30",TimeZone.of("America/New_York"))}
        assertFailsWith<IllegalArgumentException> {QuestDraft("Daily",durationMinutes=5,repeatType=QuestRepeatType.DAILY,dueAt=Instant.parse("2026-11-07T00:00:00Z")).validated()}
    }
}
