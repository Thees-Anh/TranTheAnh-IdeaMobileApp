package com.pawquest.data.repository

import android.content.Context
import android.util.AtomicFile
import com.pawquest.domain.repository.PendingPurchase
import com.pawquest.domain.repository.PendingPurchaseStore
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Same project/account-scoped no-backup AtomicFile pattern as the Bistro and gacha journals. */
class AndroidPendingPurchaseStore(context: Context,projectUrl: String): PendingPurchaseStore {
    private val root=context.applicationContext.noBackupFilesDir
    private val project=hash(projectUrl.trimEnd('/'))
    private fun file(owner: String)=AtomicFile(File(root,"purchase-$project-${hash(owner)}.json"))
    private fun lock(owner: String)=synchronized(locks) {locks.getOrPut(file(owner).baseFile.path) {Mutex()}}
    private fun read(owner: String): PendingPurchase? {
        val file=file(owner)
        if(!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists()) return null
        return Json.decodeFromString<PendingPurchase>(file.openRead().bufferedReader().use {it.readText()}).also {check(it.userId==owner)}
    }
    override suspend fun load(owner: String)=withContext(Dispatchers.IO) {lock(owner).withLock {read(owner)}}
    override suspend fun save(purchase: PendingPurchase)=withContext(Dispatchers.IO) {
        lock(purchase.userId).withLock {
            read(purchase.userId)?.let {return@withLock it}
            val file=file(purchase.userId);val stream=file.startWrite()
            try {stream.write(Json.encodeToString(purchase).toByteArray(Charsets.UTF_8));file.finishWrite(stream)}
            catch(error: Exception) {file.failWrite(stream);throw error}
            purchase
        }
    }
    override suspend fun clear(purchase: PendingPurchase)=withContext(Dispatchers.IO) {
        lock(purchase.userId).withLock {
            if(read(purchase.userId)==purchase) {val file=file(purchase.userId);file.delete();check(!file.baseFile.exists())}
        }
    }
    private companion object {
        val locks=mutableMapOf<String,Mutex>()
        fun hash(s: String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") {"%02x".format(it)}
    }
}
