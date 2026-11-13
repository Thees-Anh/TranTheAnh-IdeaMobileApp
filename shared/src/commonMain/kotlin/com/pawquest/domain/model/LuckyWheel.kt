package com.pawquest.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable enum class WheelPayment { COINS, TICKET }
@Serializable enum class WheelSector {
    FOOD, COINS_50, PET_SHARDS_5, PET_SHARDS_10, LUCKY_TICKET_1, PET_SHARDS_20, PET_SHARDS_50, PET_UNLOCK
}
@Serializable enum class WheelReward { FOOD, COINS, PET_SHARDS, TICKET, PET_UNLOCKED, PET_UNLOCK_CONVERTED_TO_SHARDS }

/** Display data only. All draw, payment, ownership and reward decisions belong to PostgreSQL. */
@Serializable data class WheelSectorConfiguration(
    @SerialName("sector_id") val sector: WheelSector,
    val position: Int,
    val weight: Int,
    val amount: Int,
    @SerialName("food_item_id") val foodItemId: String? = null,
) {
    init {
        require(position == sector.ordinal && weight in 1..10_000 && amount in 1..1_000_000)
        require((sector == WheelSector.FOOD) == (foodItemId != null))
        foodItemId?.let(::requireCaseUuid)
    }
}
@Serializable data class LuckyWheelConfiguration(
    @SerialName("banner_id") val bannerId: String,
    val enabled: Boolean,
    @SerialName("version_id") val versionId: String,
    val version: Int,
    @SerialName("price_coins") val priceCoins: Int,
    @SerialName("ticket_cost") val ticketCost: Int,
    @SerialName("fallback_pet_shards") val fallbackPetShards: Int,
    val sectors: List<WheelSectorConfiguration>,
) {
    init {
        requireCaseUuid(bannerId); requireCaseUuid(versionId)
        require(version > 0 && priceCoins in 1..1_000_000 && ticketCost == 1 && fallbackPetShards in 1..1_000_000)
        require(sectors.size == 8 && sectors.map { it.sector }.toSet().size == 8 && sectors.sumOf { it.weight } == 10_000)
    }
}
@Serializable data class LuckyWheelBalances(val coins: Long, val tickets: Long, @SerialName("pet_shards") val petShards: Long) {
    init { require(coins >= 0 && tickets >= 0 && petShards >= 0) }
}
/** Exactly the three accepted spin inputs; there is no client reward/price/seed/user field. */
@Serializable data class LuckyWheelSpinRequest(
    @SerialName("p_banner_id") val bannerId: String,
    @SerialName("p_payment_method") val payment: WheelPayment,
    @SerialName("p_request_id") val requestId: String,
) {
    init { requireCaseUuid(bannerId); requireCaseUuid(requestId) }
}
@Serializable data class PetShardRequest(
    @SerialName("p_species_code") val speciesCode: String,
    @SerialName("p_request_id") val requestId: String,
) {
    init { require(speciesCode in redeemablePetCodes); requireCaseUuid(requestId) }
}
val redeemablePetCodes: Set<String> = setOf("rabbit", "puppy", "capybara")

/** Original sector is independent of actual reward; never substitute shards for PET_UNLOCK in the animation. */
@Serializable data class LuckyWheelReceipt(
    @SerialName("transaction_id") val transactionId: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("banner_id") val bannerId: String,
    @SerialName("version_id") val versionId: String,
    @SerialName("configuration_version") val configurationVersion: Int,
    @SerialName("original_sector_id") val originalSector: WheelSector,
    @SerialName("payment_method") val payment: WheelPayment,
    @SerialName("coins_spent") val coinsSpent: Int,
    @SerialName("tickets_spent") val ticketsSpent: Int,
    @SerialName("actual_reward_type") val actualReward: WheelReward,
    @SerialName("food_item_id") val foodItemId: String? = null,
    @SerialName("food_quantity") val foodQuantity: Int,
    @SerialName("selected_pet_id") val selectedPetId: String? = null,
    @SerialName("pet_shards_earned") val petShardsEarned: Int,
    @SerialName("tickets_earned") val ticketsEarned: Int,
    @SerialName("coins_earned") val coinsEarned: Int,
    @SerialName("coins_balance_after") val coinsBalanceAfter: Long,
    @SerialName("pet_shards_balance_after") val petShardsBalanceAfter: Long,
    @SerialName("tickets_balance_after") val ticketsBalanceAfter: Long,
    @SerialName("created_at") val createdAt: String,
) {
    init {
        listOf(transactionId, requestId, bannerId, versionId).forEach(::requireCaseUuid)
        require(configurationVersion > 0)
        require(coinsSpent >= 0 && ticketsSpent in 0..1 && foodQuantity >= 0 && petShardsEarned >= 0 && ticketsEarned >= 0 && coinsEarned >= 0)
        require(coinsBalanceAfter >= 0 && petShardsBalanceAfter >= 0 && ticketsBalanceAfter >= 0)
        require(if (payment == WheelPayment.COINS) coinsSpent > 0 && ticketsSpent == 0 else coinsSpent == 0 && ticketsSpent == 1)
        require((actualReward == WheelReward.FOOD) == (foodItemId != null && foodQuantity > 0))
        require((actualReward == WheelReward.PET_UNLOCKED) == (selectedPetId != null))
        require(selectedPetId == null || selectedPetId in redeemablePetCodes)
        require((coinsEarned > 0) == (actualReward == WheelReward.COINS))
        require((ticketsEarned > 0) == (actualReward == WheelReward.TICKET))
        require((petShardsEarned > 0) == (actualReward in setOf(WheelReward.PET_SHARDS, WheelReward.PET_UNLOCK_CONVERTED_TO_SHARDS)))
        require(when (originalSector) {
            WheelSector.FOOD -> actualReward == WheelReward.FOOD
            WheelSector.COINS_50 -> actualReward == WheelReward.COINS
            WheelSector.LUCKY_TICKET_1 -> actualReward == WheelReward.TICKET
            WheelSector.PET_UNLOCK -> actualReward in setOf(WheelReward.PET_UNLOCKED, WheelReward.PET_UNLOCK_CONVERTED_TO_SHARDS)
            else -> actualReward == WheelReward.PET_SHARDS
        })
        foodItemId?.let(::requireCaseUuid)
        Instant.parse(createdAt)
    }
}
@Serializable data class PetShardQuote(
    @SerialName("request_id") val requestId: String,
    @SerialName("version_id") val versionId: String,
    @SerialName("species_code") val speciesCode: String,
    val price: Int,
    @SerialName("expires_at") val expiresAt: String,
) {
    init {
        requireCaseUuid(requestId); requireCaseUuid(versionId)
        require(speciesCode in redeemablePetCodes && price in 1..1_000_000); Instant.parse(expiresAt)
    }
}
@Serializable data class PetShardReceipt(
    @SerialName("transaction_id") val transactionId: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("version_id") val versionId: String,
    @SerialName("species_code") val speciesCode: String,
    @SerialName("shards_spent") val shardsSpent: Int,
    @SerialName("pet_shards_balance_after") val petShardsBalanceAfter: Long,
    @SerialName("created_at") val createdAt: String,
) {
    init {
        listOf(transactionId, requestId, versionId).forEach(::requireCaseUuid)
        require(speciesCode in redeemablePetCodes && shardsSpent in 1..1_000_000 && petShardsBalanceAfter >= 0)
        Instant.parse(createdAt)
    }
}
@Serializable data class PetShardCatalogEntry(
    @SerialName("version_id") val versionId: String,
    @SerialName("species_code") val speciesCode: String,
    val price: Int, val name: String,
    @SerialName("renderer_key") val rendererKey: String,
    val owned: Boolean,
)

enum class LuckyWheelRejection {
    INVALID_REQUEST, ACCOUNT_UNAVAILABLE, REQUEST_CONFLICT, RATE_LIMITED, BUSY, BANNER_UNAVAILABLE,
    CONFIGURATION_UNAVAILABLE, INSUFFICIENT_COINS, INSUFFICIENT_TICKETS, INVENTORY_FULL,
    PET_UNAVAILABLE, ALREADY_OWNED, INSUFFICIENT_SHARDS, QUOTE_REQUIRED, QUOTE_EXPIRED, PRICE_CHANGED,
    /** Hunger or Water ≤ 25: nothing charged or drawn; care for the pet first. */
    PET_NEEDS_CARE
}
sealed interface LuckyWheelResponse {
    data class Spun(val receipt: LuckyWheelReceipt) : LuckyWheelResponse
    data class Rejected(val reason: LuckyWheelRejection) : LuckyWheelResponse
}
sealed interface PetShardResponse {
    data class Quoted(val quote: PetShardQuote) : PetShardResponse
    data class Redeemed(val receipt: PetShardReceipt) : PetShardResponse
    data class Rejected(val reason: LuckyWheelRejection) : PetShardResponse
}
