package com.pawquest

import com.pawquest.domain.model.*
import com.pawquest.domain.repository.ProgressRepository
import com.pawquest.presentation.progress.ProgressViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressionTest {
    private val today=LocalDate(2026,11,9)
    private val event=ProgressEvent("event","level_up","Gift",1,2,1,null)
    private fun fixtureSnapshot(month: LocalDate=today)=ProgressSnapshot(today,month,7,7,emptyList(),emptyList(),1,1,10,emptyList(),listOf(event))
    private inner class Repository: ProgressRepository {
        var fail=false
        var ackFail=false
        var calls=0
        override suspend fun snapshot(month: LocalDate?): ProgressSnapshot {
            calls++;if(fail) error("offline")
            return fixtureSnapshot(month?:today)
        }
        override suspend fun acknowledge(eventId: String) {if(ackFail) error("offline")}
    }
    @Test fun calendarUsesMondayWeeksAndLeapYearWithoutInventingDays() {
        val cells=HabitCalendarRules.cells(LocalDate(2024,2,15))
        assertEquals(35,cells.size)
        assertEquals(3,cells.takeWhile {it==null}.size)
        assertEquals(29,cells.filterNotNull().size)
        assertEquals(LocalDate(2024,2,29),cells.filterNotNull().last())
        assertEquals(LocalDate(2027,1,1),HabitCalendarRules.shiftMonth(LocalDate(2026,12,31),1))
        assertEquals(LocalDate(2025,12,1),HabitCalendarRules.shiftMonth(LocalDate(2026,1,9),-1))
    }
    @Test fun focusNotificationsNeedPermissionBackgroundAndRunningSession() {
        val running=FocusPresence("s","Puppy",60,active=false,running=true)
        assertTrue(FocusNotificationRules.showOngoing(true,true,running),"hidden Focus screen still counts down")
        assertFalse(FocusNotificationRules.showOngoing(false,true,running))
        assertFalse(FocusNotificationRules.showOngoing(true,false,running))
        assertFalse(FocusNotificationRules.showOngoing(true,true,running.copy(remainingSeconds=0)))
        assertFalse(FocusNotificationRules.showOngoing(true,true,running.copy(running=false)))
        assertFalse(FocusNotificationRules.showOngoing(true,true,null))
        assertTrue(FocusNotificationRules.alertAtEnd(true,true,running.copy(remainingSeconds=0)))
        assertFalse(FocusNotificationRules.alertAtEnd(true,false,running),"no alert while the app is open")
    }
    @Test fun offlineCalendarKeepsPreviouslyVerifiedProgressAndCanRetry()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo=Repository();val vm=ProgressViewModel(repo);runCurrent()
            repo.fail=true;vm.refresh();runCurrent()
            assertEquals(7,vm.state.value.snapshot!!.currentStreak)
            assertNotNull(vm.state.value.error)
            repo.fail=false;vm.moveMonth(1);runCurrent()
            assertEquals(LocalDate(2026,12,1),vm.state.value.snapshot!!.month)
            assertNull(vm.state.value.error)
        } finally {Dispatchers.resetMain()}
    }
    @Test fun failedAcknowledgementRetainsGiftAndSuccessfulAckCannotResurrectFromStaleSnapshot()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo=Repository();val vm=ProgressViewModel(repo);runCurrent()
            repo.ackFail=true;vm.acknowledge("event");runCurrent()
            assertEquals(event,vm.state.value.event);assertNotNull(vm.state.value.error)
            repo.ackFail=false;vm.acknowledge("event");runCurrent();vm.refresh();runCurrent()
            assertNull(vm.state.value.event)
            assertFalse(vm.state.value.acknowledging)
        } finally {Dispatchers.resetMain()}
    }
    @Test fun laterDefersPresentationOnlyAndRestartRestoresUnacknowledgedGift()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo=Repository();val vm=ProgressViewModel(repo);runCurrent()
            vm.later("event");vm.refresh();runCurrent();assertNull(vm.state.value.event)
            val reopened=ProgressViewModel(repo);runCurrent();assertEquals(event,reopened.state.value.event)
        } finally {Dispatchers.resetMain()}
    }
    @Test fun slowerCancelledMonthRequestCannotOverwriteNewerSelection()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var call=0
            val repo=object: ProgressRepository {
                override suspend fun snapshot(month: LocalDate?): ProgressSnapshot {
                    val n=++call
                    withContext(NonCancellable) {delay(if(n==1) 200 else 20)}
                    return fixtureSnapshot(month?:today)
                }
                override suspend fun acknowledge(eventId: String) {}
            }
            val vm=ProgressViewModel(repo);runCurrent()
            vm.refresh(LocalDate(2026,12,1));runCurrent();advanceUntilIdle()
            assertEquals(LocalDate(2026,12,1),vm.state.value.snapshot!!.month)
        } finally {Dispatchers.resetMain()}
    }
}
