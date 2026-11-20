package com.pawquest.domain.repository

import com.pawquest.domain.model.PurchaseRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/**
 * A Shop purchase whose server outcome is not yet known. It is saved before the request is
 * sent and cleared only after the server answered, so a timeout followed by process death
 * still retries with the same request ID instead of looking like nothing happened.
 */
@Serializable
data class PendingPurchase(val userId: String,val itemId: String,val quantity: Int,val requestId: String) {
    val request get()=PurchaseRequest(itemId,quantity,requestId)
    companion object {
        fun of(userId: String,request: PurchaseRequest)=PendingPurchase(userId,request.itemId,request.quantity,request.requestId)
    }
}

interface PendingPurchaseStore {
    suspend fun load(owner: String): PendingPurchase?
    /** Returns the already stored purchase if one exists; never replaces it. */
    suspend fun save(purchase: PendingPurchase): PendingPurchase
    suspend fun clear(purchase: PendingPurchase)
}

/** Process-lifetime store for platforms/tests without durable storage. */
class InMemoryPendingPurchaseStore: PendingPurchaseStore {
    private val lock=Mutex()
    private val rows=mutableMapOf<String,PendingPurchase>()
    override suspend fun load(owner: String)=lock.withLock {rows[owner]}
    override suspend fun save(purchase: PendingPurchase)=lock.withLock {rows.getOrPut(purchase.userId) {purchase}}
    override suspend fun clear(purchase: PendingPurchase)=lock.withLock {if(rows[purchase.userId]==purchase) rows.remove(purchase.userId);Unit}
}
