package com.pawquest.data.repository

import android.content.Context
import android.util.AtomicFile
import com.pawquest.domain.model.PendingLuckyOperation
import com.pawquest.domain.repository.PendingLuckyOperationStore
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Atomic, fsynced intent in noBackupFilesDir, isolated by project and account.
 * Contains UUIDs only, never credentials. Corrupt journals fail closed. */
class AndroidPendingLuckyOperationStore(context: Context,projectUrl: String) : PendingLuckyOperationStore {
    private val root=context.applicationContext.noBackupFilesDir
    private val project=hash(projectUrl.trimEnd('/'))
    private fun file(owner: String)=AtomicFile(File(root,"lucky-operation-$project-${hash(owner)}.json"))
    private fun lock(owner: String)=synchronized(locks) { locks.getOrPut(file(owner).baseFile.absolutePath) { Mutex() } }
    private fun read(owner: String): PendingLuckyOperation? {
        val target=file(owner)
        if(!target.baseFile.exists() && !File(target.baseFile.path+".bak").exists()) return null
        return Json.decodeFromString<PendingLuckyOperation>(target.openRead().bufferedReader().use { it.readText() }).also { check(it.userId==owner) }
    }
    override suspend fun load(owner: String)=withContext(Dispatchers.IO) { lock(owner).withLock { read(owner) } }
    override suspend fun save(request: PendingLuckyOperation)=withContext(Dispatchers.IO) {
        lock(request.userId).withLock {
            val previous=read(request.userId)
            if(previous!=null && (previous.kind!=request.kind || previous.copy(confirmed=request.confirmed)!=request || previous.confirmed || !request.confirmed)) return@withLock previous
            val target=file(request.userId)
            val stream=target.startWrite()
            try { stream.write(Json.encodeToString(request).toByteArray(Charsets.UTF_8)); target.finishWrite(stream) }
            catch(error: Exception) { target.failWrite(stream); throw error }
            request
        }
    }
    override suspend fun clear(request: PendingLuckyOperation)=withContext(Dispatchers.IO) {
        lock(request.userId).withLock {
            if(read(request.userId)==request) {
                val target=file(request.userId)
                target.delete()
                check(!target.baseFile.exists()) { "Could not clear Lucky operation intent." }
            }
        }
    }
    private companion object {
        val locks=mutableMapOf<String,Mutex>()
        fun hash(value: String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
