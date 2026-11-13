package com.pawquest.domain.repository

import com.pawquest.domain.model.PendingLuckyOperation

interface PendingLuckyOperationStore {
    suspend fun load(owner: String): PendingLuckyOperation?
    /** CAS: preserve another intent; only the identical pet quote can upgrade to confirmed. */
    suspend fun save(request: PendingLuckyOperation): PendingLuckyOperation
    suspend fun clear(request: PendingLuckyOperation)
}
class UnavailableLuckyOperationStore : PendingLuckyOperationStore {
    override suspend fun load(owner: String): PendingLuckyOperation?=error("Secure storage unavailable")
    override suspend fun save(request: PendingLuckyOperation): PendingLuckyOperation=error("Secure storage unavailable")
    override suspend fun clear(request: PendingLuckyOperation): Unit=error("Secure storage unavailable")
}
