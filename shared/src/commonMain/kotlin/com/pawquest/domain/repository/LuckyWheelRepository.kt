package com.pawquest.domain.repository

import com.pawquest.domain.model.*

/** Task 03 transport contract: use the existing authenticated Supabase client and captured account token.
 * Preserve a pending request/payment across uncertainty; never generate a replacement key on timeout.
 * History uses a composite (createdAt, transactionId) cursor and a server-clamped maximum of 50.
 */
interface LuckyWheelRepository {
    suspend fun configuration(bannerId: String): LuckyWheelConfiguration
    suspend fun balances(): LuckyWheelBalances
    /** expectedOwner binds the durable client intent to the captured session; never sent to PostgreSQL. */
    suspend fun spin(request: LuckyWheelSpinRequest,expectedOwner: String): LuckyWheelResponse
    suspend fun history(beforeCreatedAt: String? = null, beforeTransactionId: String? = null, limit: Int = 20): List<LuckyWheelReceipt>
}

/** Extension of the shared Shard Exchange capability, not another wallet. */
interface PetShardExchangeRepository {
    suspend fun catalog(): List<PetShardCatalogEntry>
    suspend fun quote(request: PetShardRequest,expectedOwner: String): PetShardResponse
    suspend fun redeem(request: PetShardRequest,expectedOwner: String): PetShardResponse
}
