package com.pawquest.domain.model

import kotlin.time.Instant

/** Separate from Coins and from one another. No implicit currency conversion. */
enum class ShardCurrency { ITEM_SHARD, PET_SHARD }
enum class ItemRarity { COMMON, RARE, EPIC, LEGENDARY }
enum class ItemCaseOutcome { ITEM, ITEM_SHARDS }

data class ItemCaseBanner(val id: String, val code: String, val enabled: Boolean, val currentVersion: Int?)

/** PET_SHARD's proposed price does not enable pet redemption or grant a pet. */
data class ShardCurrencyConfiguration(val currency: ShardCurrency, val proposedPetRedemptionPrice: Int?)

data class ShardBalance(val currency: ShardCurrency, val balance: Long) {
    init { require(balance >= 0) }
}

data class ItemCaseTier(
    val rarity: ItemRarity,
    val probabilityBps: Int,
    val duplicateItemShards: Int,
    val redemptionItemShards: Int,
) {
    init {
        require(probabilityBps in 1..10_000)
        require(duplicateItemShards in 1..1_000_000)
        require(redemptionItemShards in 1..1_000_000)
    }
}

/** Snapshot of the real catalog identity, not an HTML index or localized name. */
data class ItemCaseReward(
    val itemId: String,
    val itemCode: String,
    val assetId: String,
    val category: ItemCategory,
    val maxQuantity: Int,
    val rarity: ItemRarity,
    val weight: Int,
    val redeemable: Boolean,
) {
    val uniqueOwned: Boolean get() = maxQuantity == 1

    init {
        require(itemId.isNotBlank() && itemCode.isNotBlank() && assetId.isNotBlank())
        require(category == ItemCategory.ACCESSORY || category == ItemCategory.FURNITURE)
        require(maxQuantity > 0 && weight in 1..1_000_000)
    }

    /** Display/preview only: the backend alone posts authoritative conversion. */
    fun duplicateShardPreview(ownedQuantity: Int, tier: ItemCaseTier): Int {
        require(ownedQuantity in 0..maxQuantity && tier.rarity == rarity)
        return if (uniqueOwned && ownedQuantity > 0) tier.duplicateItemShards else 0
    }
}

/** Exact rational probability; convert to Double only for display. */
data class ItemCaseProbability(val numerator: Long, val denominator: Long) {
    init { require(denominator > 0 && numerator in 1..denominator) }
    val fraction: Double get() = numerator.toDouble() / denominator
}

data class ItemCaseConfiguration(
    val versionId: String,
    val bannerId: String,
    val version: Int,
    val priceCoins: Int,
    val tiers: List<ItemCaseTier>,
    val rewards: List<ItemCaseReward>,
) {
    init {
        require(versionId.isNotBlank() && bannerId.isNotBlank())
        require(version > 0 && priceCoins in 1..1_000_000)
        require(tiers.size == ItemRarity.entries.size && tiers.map { it.rarity }.toSet().size == tiers.size)
        require(tiers.sumOf { it.probabilityBps } == 10_000)
        require(rewards.isNotEmpty() && rewards.map { it.itemId }.toSet().size == rewards.size)
        require(ItemRarity.entries.all { rarity -> rewards.any { it.rarity == rarity } })
    }

    fun probability(itemId: String): ItemCaseProbability {
        val reward = rewards.single { it.itemId == itemId }
        val tier = tiers.single { it.rarity == reward.rarity }
        val totalWeight = rewards.filter { it.rarity == reward.rarity }.sumOf { it.weight.toLong() }
        return ItemCaseProbability(tier.probabilityBps.toLong() * reward.weight, 10_000L * totalWeight)
    }
}

/** Mirrors the committed database receipt. No public opening adapter exists yet. */
data class ItemCaseOpening(
    val id: String,
    val userId: String,
    val requestId: String,
    val versionId: String,
    val itemId: String,
    val outcome: ItemCaseOutcome,
    val inventoryBefore: Int,
    val quantityGranted: Int,
    val coinsSpent: Int,
    val coinsBalanceAfter: Long,
    val itemShardsGranted: Int,
    val walletTransactionId: String,
    val shardLedgerId: String?,
    val createdAt: Instant,
)

data class ItemShardRedemption(
    val id: String,
    val userId: String,
    val requestId: String,
    val versionId: String,
    val itemId: String,
    val shardsSpent: Int,
    val quantityGranted: Int,
    val shardLedgerId: String,
    val createdAt: Instant,
)

enum class ShardLedgerReason { ITEM_DUPLICATE, ITEM_REDEMPTION, ADMIN_ADJUSTMENT }
data class ShardLedgerEntry(
    val id: String,
    val userId: String,
    val currency: ShardCurrency,
    val requestId: String,
    val reason: ShardLedgerReason,
    val delta: Long,
    val balanceAfter: Long,
    val openingId: String?,
    val redemptionId: String?,
    val note: String?,
    val createdAt: Instant,
)
