package com.pawquest.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

object ItemCaseIds { const val DEFAULT_BANNER = "e2400000-0000-0000-0000-000000000001" }
internal fun requireCaseUuid(value: String) {
    require(Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}").matches(value))
}

/** Durable intent only. Never persist client-computed rewards, prices or ownership. */
@Serializable
data class PendingItemCase(val userId: String, val bannerId: String, val requestId: String) {
    init { requireCaseUuid(userId); requireCaseUuid(bannerId); requireCaseUuid(requestId) }
}

data class LoadedItemCase(val banner: ItemCaseBanner, val configuration: ItemCaseConfiguration)
data class ItemCaseHistoryEntry(
    val id: String,val itemId: String,val coinsSpent: Int,val shardsGranted: Int,val createdAt: Instant,
    val rarity: ItemRarity,val outcome: ItemCaseRewardOutcome,val itemCode: String,val assetId: String,val category: ItemCategory,
)
enum class ItemCaseRewardOutcome { ITEM_GRANTED, DUPLICATE_CONVERTED }

/** API receipt: original rolled item and actual granted reward are distinct. */
data class ItemCaseReceipt(
    val transactionId: String,
    val requestId: String,
    val bannerId: String,
    val configurationVersionId: String,
    val configurationVersion: Int,
    val winningItemId: String,
    val itemName: String,
    val itemCode: String,
    val assetId: String,
    val rarity: ItemRarity,
    val openingCostCoins: Int,
    val outcome: ItemCaseRewardOutcome,
    val quantityGranted: Int,
    val itemShardsGranted: Int,
    val coinsBalanceAfter: Long,
    val itemShardsBalanceAfter: Long,
    val createdAt: Instant,
) {
    init {
        listOf(transactionId,requestId,bannerId,configurationVersionId,winningItemId).forEach(::requireCaseUuid)
        require(configurationVersion>0 && openingCostCoins in 1..1_000_000)
        require(itemName.isNotBlank() && itemCode.isNotBlank() && assetId.isNotBlank())
        require(coinsBalanceAfter>=0 && itemShardsBalanceAfter>=0)
        require(when(outcome) {
            ItemCaseRewardOutcome.ITEM_GRANTED -> quantityGranted==1 && itemShardsGranted==0
            ItemCaseRewardOutcome.DUPLICATE_CONVERTED -> quantityGranted==0 && itemShardsGranted>0 && itemShardsBalanceAfter>=itemShardsGranted
        })
    }
}

enum class ItemCaseRejection {
    INVALID_REQUEST, ACCOUNT_UNAVAILABLE, REQUEST_CONFLICT, BANNER_UNAVAILABLE,
    INSUFFICIENT_COINS, CONFIGURATION_UNAVAILABLE, INVENTORY_FULL, RATE_LIMITED, BUSY,
    /** Hunger or Water ≤ 25: nothing charged or drawn; care for the pet first. */
    PET_NEEDS_CARE;

    // Only an explicit dismissal of a definitive no-charge rejection may retire
    // a key. Busy, conflict and account failures always require reconciliation.
    val canDismiss: Boolean get() = this in setOf(INVALID_REQUEST,BANNER_UNAVAILABLE,INSUFFICIENT_COINS,CONFIGURATION_UNAVAILABLE,INVENTORY_FULL,PET_NEEDS_CARE)
}
sealed interface ItemCaseResponse {
    data class Opened(val receipt: ItemCaseReceipt) : ItemCaseResponse
    data class Rejected(val reason: ItemCaseRejection, val retryAfterSeconds: Int? = null) : ItemCaseResponse
}

/** Missing balance rows represent authoritative zero; failed reads are not zero. */
data class ShardWallet(val itemShards: Long, val petShards: Long) {
    init { require(itemShards>=0 && petShards>=0) }
}
