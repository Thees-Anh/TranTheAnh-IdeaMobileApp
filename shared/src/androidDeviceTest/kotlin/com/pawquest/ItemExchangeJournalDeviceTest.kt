package com.pawquest

import androidx.test.platform.app.InstrumentationRegistry
import com.pawquest.data.repository.AndroidPendingItemExchangeStore
import com.pawquest.domain.model.PendingItemExchange
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ItemExchangeJournalDeviceTest {
    @Test fun confirmedIntentSurvivesRecreationAndCannotBeReplacedOrDowngraded()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val project="https://exchange-test-${UUID.randomUUID()}.invalid"
        val request=PendingItemExchange(UUID.randomUUID().toString(),UUID.randomUUID().toString(),UUID.randomUUID().toString())
        val store=AndroidPendingItemExchangeStore(context,project)
        val recreated=AndroidPendingItemExchangeStore(context,project)
        try {
            assertEquals(request,store.save(request))
            val confirmed=request.copy(confirmed=true)
            assertEquals(confirmed,recreated.save(confirmed))
            assertEquals(confirmed,store.load(request.userId))
            assertEquals(confirmed,store.save(request.copy(requestId=UUID.randomUUID().toString())))
            assertEquals(confirmed,store.save(request))
            recreated.clear(request);assertNotNull(store.load(request.userId))
            assertNull(store.load(UUID.randomUUID().toString()))
            assertNull(AndroidPendingItemExchangeStore(context,project+"/other").load(request.userId))
            recreated.clear(confirmed);assertNull(store.load(request.userId))
        } finally {store.load(request.userId)?.let {store.clear(it)}}
    }
}
