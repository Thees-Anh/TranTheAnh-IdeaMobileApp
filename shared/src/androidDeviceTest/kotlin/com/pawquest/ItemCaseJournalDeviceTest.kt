package com.pawquest

import androidx.test.platform.app.InstrumentationRegistry
import com.pawquest.data.repository.AndroidPendingItemCaseStore
import com.pawquest.domain.model.ItemCaseIds
import com.pawquest.domain.model.PendingItemCase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.io.File
import java.security.MessageDigest

class ItemCaseJournalDeviceTest {
    @Test fun corruptJournalFailsClosedWithoutReplacingTheRequest() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val project="https://journal-corrupt-${UUID.randomUUID()}.invalid"
        val request=PendingItemCase(UUID.randomUUID().toString(),ItemCaseIds.DEFAULT_BANNER,UUID.randomUUID().toString())
        fun hash(value: String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val target=File(context.noBackupFilesDir,"item-case-${hash(project)}-${hash(request.userId)}.json")
        try {
            target.writeText("incomplete journal")
            val store=AndroidPendingItemCaseStore(context,project)
            try { store.load(request.userId); fail("Corrupt journal must not look empty") } catch(_: kotlinx.serialization.SerializationException) { }
            try { store.saveIfAbsent(request); fail("Corrupt journal must not be replaced") } catch(_: kotlinx.serialization.SerializationException) { }
            assertEquals("incomplete journal",target.readText())
        } finally { target.delete() }
    }
    @Test fun journalSurvivesAdapterRecreationAndPreservesOneKeyPerAccount() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val project="https://journal-test-${UUID.randomUUID()}.invalid"
        val first=AndroidPendingItemCaseStore(context,project)
        val recreated=AndroidPendingItemCaseStore(context,project)
        val request=PendingItemCase(UUID.randomUUID().toString(),ItemCaseIds.DEFAULT_BANNER,UUID.randomUUID().toString())
        val competing=request.copy(requestId=UUID.randomUUID().toString())
        val other=request.copy(userId=UUID.randomUUID().toString())
        try {
            val saved=listOf(async { first.saveIfAbsent(request) },async { recreated.saveIfAbsent(competing) }).awaitAll()
            assertEquals(saved.first(),saved.last())
            assertEquals(saved.first(),recreated.load(request.userId))
            recreated.clear(if(saved.first()==request) competing else request)
            assertEquals(saved.first(),first.load(request.userId)) // compare-and-clear
            assertNull(first.load(other.userId))
            assertEquals(other,recreated.saveIfAbsent(other))
            first.clear(saved.first())
            assertNull(recreated.load(request.userId))
            assertEquals(other,first.load(other.userId))
        } finally {
            first.load(request.userId)?.let { first.clear(it) }
            first.clear(other)
        }
    }
}
