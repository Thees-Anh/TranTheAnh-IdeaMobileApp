package com.pawquest.domain.repository

import com.pawquest.domain.model.*

interface ItemCaseRepository {
    suspend fun getConfiguration(bannerId: String): LoadedItemCase
    suspend fun open(request: PendingItemCase): ItemCaseResponse
    suspend fun getHistory(): List<ItemCaseHistoryEntry> = error("Opening history is unavailable.")
}

/** Backend read only. No mutation, conversion, grant or redemption method. */
interface ShardRepository {
    suspend fun getBalances(): ShardWallet
    suspend fun getItemLedger(): List<ShardLedgerEntry> = error("Shard history is unavailable.")
    suspend fun quoteItem(request: PendingItemExchange): ItemExchangeResponse = error("Shard Exchange is unavailable.")
    suspend fun redeemItem(request: PendingItemExchange): ItemExchangeResponse = error("Shard Exchange is unavailable.")
}

interface PendingItemExchangeStore {
    suspend fun load(userId: String): PendingItemExchange?
    /** CAS: preserve another key; the same intent may only upgrade confirmed. */
    suspend fun save(request: PendingItemExchange): PendingItemExchange
    suspend fun clear(request: PendingItemExchange)
}
class UnavailableItemExchangeStore: PendingItemExchangeStore {
    override suspend fun load(userId: String): PendingItemExchange?=error("Exchange storage unavailable")
    override suspend fun save(request: PendingItemExchange): PendingItemExchange=error("Exchange storage unavailable")
    override suspend fun clear(request: PendingItemExchange): Unit=error("Exchange storage unavailable")
}

interface PendingItemCaseStore {
    suspend fun load(userId: String): PendingItemCase?
    /** Atomically preserve an existing intent instead of replacing its key. */
    suspend fun saveIfAbsent(request: PendingItemCase): PendingItemCase
    /** Compare-and-remove: never delete a newer or another account's request. */
    suspend fun clear(request: PendingItemCase)
}

class UnavailablePendingItemCaseStore : PendingItemCaseStore {
    override suspend fun load(userId: String): PendingItemCase? = error("Durable Item Case storage is unavailable.")
    override suspend fun saveIfAbsent(request: PendingItemCase): PendingItemCase = error("Durable Item Case storage is unavailable.")
    override suspend fun clear(request: PendingItemCase): Unit = error("Durable Item Case storage is unavailable.")
}
