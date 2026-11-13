package com.pawquest

import com.pawquest.domain.model.*
import kotlin.test.*

class ItemCaseTest {
    private val tiers = listOf(
        ItemCaseTier(ItemRarity.COMMON,5500,5,30), ItemCaseTier(ItemRarity.RARE,3000,10,60),
        ItemCaseTier(ItemRarity.EPIC,1200,20,120), ItemCaseTier(ItemRarity.LEGENDARY,300,40,240),
    )
    private fun reward(id: String, rarity: ItemRarity, weight: Int = 1, maximum: Int = 1) =
        ItemCaseReward(id,id,id,ItemCategory.FURNITURE,maximum,rarity,weight,true)
    private val rewards = ItemRarity.entries.map { reward(it.name,it) }
    private fun config(t: List<ItemCaseTier> = tiers, r: List<ItemCaseReward> = rewards) =
        ItemCaseConfiguration("version-id","banner-id",1,100,t,r)

    @Test fun weightedOddsPreserveTierMass() {
        val c = config(r = rewards + reward("common-heavy",ItemRarity.COMMON,3))
        assertEquals(ItemCaseProbability(5500,40000), c.probability("COMMON"))
        assertEquals(ItemCaseProbability(16500,40000), c.probability("common-heavy"))
        assertEquals(1.0,c.rewards.sumOf { c.probability(it.itemId).fraction },0.000000001)
        assertEquals(0.03,c.probability("LEGENDARY").fraction,0.000000001)
    }

    @Test fun invalidConfigurationsAreRejected() {
        assertFailsWith<IllegalArgumentException> { config(t=tiers.dropLast(1)) }
        assertFailsWith<IllegalArgumentException> { config(t=tiers.dropLast(1)+tiers.first()) }
        assertFailsWith<IllegalArgumentException> { config(t=tiers.map { it.copy(probabilityBps=100) }) }
        assertFailsWith<IllegalArgumentException> { config(r=rewards.dropLast(1)) }
        assertFailsWith<IllegalArgumentException> { config(r=rewards+rewards.first()) }
        assertFailsWith<IllegalArgumentException> { reward("bad",ItemRarity.COMMON,0) }
        assertFailsWith<IllegalArgumentException> { tiers.first().copy(duplicateItemShards=0) }
        assertFailsWith<IllegalArgumentException> { tiers.first().copy(redemptionItemShards=-1) }
    }

    @Test fun onlyUniqueOwnershipConvertsToItemShards() {
        val unique=reward("unique",ItemRarity.COMMON)
        val multiCopy=reward("future-multi-copy",ItemRarity.COMMON,maximum=10)
        assertEquals(0,unique.duplicateShardPreview(0,tiers.first()))
        assertEquals(5,unique.duplicateShardPreview(1,tiers.first()))
        assertEquals(0,multiCopy.duplicateShardPreview(1,tiers.first()))
        assertFailsWith<IllegalArgumentException> { unique.duplicateShardPreview(2,tiers.first()) }
        assertFailsWith<IllegalArgumentException> { unique.duplicateShardPreview(1,tiers.last()) }
    }

    @Test fun shardCurrenciesRemainIndependent() {
        val balances=mapOf(ShardCurrency.ITEM_SHARD to ShardBalance(ShardCurrency.ITEM_SHARD,40),
            ShardCurrency.PET_SHARD to ShardBalance(ShardCurrency.PET_SHARD,0))
        assertEquals(40L,balances.getValue(ShardCurrency.ITEM_SHARD).balance)
        assertEquals(0L,balances.getValue(ShardCurrency.PET_SHARD).balance)
        assertFailsWith<IllegalArgumentException> { ShardBalance(ShardCurrency.ITEM_SHARD,-1) }
    }
}
