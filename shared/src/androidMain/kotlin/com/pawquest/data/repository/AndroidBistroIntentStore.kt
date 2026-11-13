package com.pawquest.data.repository

import android.content.Context
import android.util.AtomicFile
import com.pawquest.domain.model.PendingBistroMeal
import com.pawquest.domain.repository.BistroIntentStore
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/** Same no-backup AtomicFile intent pattern as existing Case/Wheel operations. */
class AndroidBistroIntentStore(context: Context,projectUrl: String): BistroIntentStore {
    private val root=context.applicationContext.noBackupFilesDir
    private val project=hash(projectUrl.trimEnd('/'))
    private fun file(owner: String)=AtomicFile(File(root,"bistro-$project-${hash(owner)}.json"))
    private fun lock(owner: String)=synchronized(locks) {locks.getOrPut(file(owner).baseFile.path) {Mutex()}}
    private fun read(owner: String): PendingBistroMeal? {
        val file=file(owner)
        if(!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists()) return null
        return Json.decodeFromString<PendingBistroMeal>(file.openRead().bufferedReader().use {it.readText()}).also {check(it.userId==owner)}
    }
    override suspend fun load(owner: String)=withContext(Dispatchers.IO) {lock(owner).withLock {read(owner)}}
    override suspend fun save(request: PendingBistroMeal)=withContext(Dispatchers.IO) {
        lock(request.userId).withLock {
            read(request.userId)?.let {return@withLock it}
            val file=file(request.userId);val stream=file.startWrite()
            try {stream.write(Json.encodeToString(request).toByteArray(Charsets.UTF_8));file.finishWrite(stream)}
            catch(error: Exception) {file.failWrite(stream);throw error}
            request
        }
    }
    override suspend fun clear(request: PendingBistroMeal)=withContext(Dispatchers.IO) {
        lock(request.userId).withLock {
            if(read(request.userId)==request) {val file=file(request.userId);file.delete();check(!file.baseFile.exists())}
        }
    }
    private companion object {
        val locks=mutableMapOf<String,Mutex>()
        fun hash(s: String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") {"%02x".format(it)}
    }
}
