package com.pawquest

import com.pawquest.domain.repository.AppStatusRepository
import com.pawquest.domain.repository.AppUpdateCheck
import com.pawquest.domain.telemetry.supabaseOperation
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AppHealthTest {
    private fun repo(block: suspend (Int)->Boolean)=object: AppStatusRepository {
        override suspend fun updateRequired(versionCode: Int)=block(versionCode)
    }

    @Test fun onlyAnExplicitServerAnswerBlocksTheApp()=runTest {
        assertTrue(AppUpdateCheck(repo {it<10001}).required(10000))
        assertFalse(AppUpdateCheck(repo {it<10001}).required(10001))
    }

    @Test fun failuresAndUnknownVersionsFailOpen()=runTest {
        assertFalse(AppUpdateCheck(repo {error("offline")}).required(10000),"network or decode error")
        assertFalse(AppUpdateCheck(repo {awaitCancellation()},timeoutMillis=1_000).required(10000),"timeout")
        var called=false
        assertFalse(AppUpdateCheck(repo {called=true;true}).required(0),"debug/unknown version code")
        assertFalse(called)
    }

    @Test fun operationNamesNeverCarryQueriesOrIds() {
        assertEquals("rpc:lucky_wheel_spin",supabaseOperation("/rest/v1/rpc/lucky_wheel_spin"))
        assertEquals("table:quests",supabaseOperation("/rest/v1/quests?user_id=eq.a1000000-0000-0000-0000-000000000001"))
        assertEquals("auth:token",supabaseOperation("/auth/v1/token?grant_type=password"))
        assertEquals("rpc:unknown",supabaseOperation("/rest/v1/rpc/drop table;--"))
        assertEquals("other",supabaseOperation("/storage/x"))
    }
}
