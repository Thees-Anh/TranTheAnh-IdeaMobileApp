package com.pawquest

import androidx.test.platform.app.InstrumentationRegistry
import com.pawquest.data.repository.AndroidPendingLuckyOperationStore
import com.pawquest.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class LuckyOperationJournalDeviceTest {
    @Test fun restartAccountProjectIsolationAndCompareAndClear()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val project="https://journal-${Uuid.random()}.invalid"
        val owner=Uuid.random().toString();val banner="e2800000-0000-0000-0000-000000000001"
        val first=PendingLuckyOperation(owner,Uuid.random().toString(),LuckyOperationKind.SPIN,banner,WheelPayment.TICKET,confirmed=true)
        val store=AndroidPendingLuckyOperationStore(context,project)
        assertEquals(first,store.save(first))
        val restored=AndroidPendingLuckyOperationStore(context,project)
        try {
            assertEquals(first,restored.load(owner));assertNull(restored.load(Uuid.random().toString()))
            assertNull(AndroidPendingLuckyOperationStore(context,project+"/other").load(owner))
            val other=PendingLuckyOperation(owner,Uuid.random().toString(),LuckyOperationKind.PET_REDEMPTION,speciesCode="rabbit")
            assertEquals(first,restored.save(other));restored.clear(other);assertEquals(first,restored.load(owner))
        } finally {store.clear(first)}
        assertNull(restored.load(owner))
    }
    @Test fun petConfirmationPersistsBeforeRedeemAndCannotDowngrade()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val project="https://journal-${Uuid.random()}.invalid"
        val owner=Uuid.random().toString()
        val request=PendingLuckyOperation(owner,Uuid.random().toString(),LuckyOperationKind.PET_REDEMPTION,speciesCode="puppy")
        val confirmed=request.copy(confirmed=true);val store=AndroidPendingLuckyOperationStore(context,project)
        try {
            assertEquals(request,store.save(request));assertEquals(confirmed,store.save(confirmed))
            assertEquals(confirmed,AndroidPendingLuckyOperationStore(context,project).load(owner))
            assertEquals(confirmed,store.save(request));store.clear(request);assertEquals(confirmed,store.load(owner))
        } finally {store.clear(confirmed)}
    }
}
