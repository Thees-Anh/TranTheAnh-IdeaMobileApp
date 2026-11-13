package com.pawquest

import com.pawquest.domain.model.*
import com.pawquest.presentation.gacha.*
import kotlin.test.*
import kotlin.time.Instant

class ItemCasePresentationTest {
    private val stamp=Instant.parse("2026-11-10T05:00:00Z")
    private val version="e2400000-0000-0000-0000-000000000002"
    private val rewards=ItemRarity.entries.mapIndexed { i,r -> ItemCaseReward("e2300000-0000-0000-0000-${listOf(3,5,2,1)[i].toString().padStart(12,'0')}",
        "item_$i",listOf("acc-sprout","acc-glasses","acc-wizard-hat","acc-crown")[i],ItemCategory.ACCESSORY,1,r,1,true) }
    private val config=ItemCaseConfiguration(version,ItemCaseIds.DEFAULT_BANNER,1,100,
        ItemRarity.entries.mapIndexed {i,r->ItemCaseTier(r,listOf(4000,3000,2000,1000)[i],listOf(7,13,29,53)[i],100)},rewards)
    private val catalog=rewards.map {CatalogItem(it.itemId,it.itemCode,"Name ${it.itemCode}",it.category,100,it.assetId,1,true)}
    private fun receipt(duplicate: Boolean)=ItemCaseReceipt("fb280000-0000-0000-0000-000000000001","fb270000-0000-0000-0000-000000000001",
        ItemCaseIds.DEFAULT_BANNER,version,1,rewards[1].itemId,"Historical original name",rewards[1].itemCode,rewards[1].assetId,ItemRarity.RARE,100,
        if(duplicate) ItemCaseRewardOutcome.DUPLICATE_CONVERTED else ItemCaseRewardOutcome.ITEM_GRANTED,
        if(duplicate) 0 else 1,if(duplicate) 37 else 0,900,93,stamp)
    @Test fun collectionMapsRealIdsAndDoesNotInventOwnershipOrTwentyFourEntries() {
        val owned=InventoryItem("inventory","owner",catalog[1],1,stamp)
        val display=caseCollection(config,catalog.reversed(),listOf(owned),true)
        assertEquals(4,display.size)
        assertEquals(catalog[1].name,display[1].name);assertEquals(true,display[1].owned)
        assertEquals(false,display[0].owned)
        assertTrue(caseCollection(config,catalog,emptyList(),false).all {it.owned==null})
        assertEquals(rewards.map {it.itemId},display.map {it.itemId})
    }
    @Test fun grantAndDuplicateKeepImmutableIdentityAndNewLabelRequiresUniqueGrant() {
        val grant=caseReveal(receipt(false),config,catalog)
        assertTrue(grant.newItem);assertEquals("Historical original name",grant.name)
        assertEquals(ItemCategory.ACCESSORY,grant.category)
        val duplicate=caseReveal(receipt(true),config,catalog)
        assertFalse(duplicate.newItem);assertEquals(grant.assetId,duplicate.assetId);assertEquals(grant.itemId,duplicate.itemId)
        val multi=config.copy(rewards=config.rewards.map {if(it.itemId==grant.itemId) it.copy(maxQuantity=5) else it})
        assertFalse(caseReveal(receipt(false),multi,emptyList()).newItem)
    }
    @Test fun effectiveAndTierPercentagesReflectBackendWeightsWithoutFixedOdds() {
        val extra=rewards[0].copy(itemId="extra",weight=1)
        val weighted=config.copy(rewards=config.rewards.mapIndexed {i,r->if(i==0) r.copy(weight=3) else r}+extra)
        assertEquals("30.0000%",casePercent(weighted.probability(rewards[0].itemId)))
        assertEquals("10.0000%",casePercent(weighted.probability("extra")))
        assertEquals("40.0000%",casePercent(ItemCaseProbability(weighted.tiers[0].probabilityBps.toLong(),10000)))
        assertEquals(1.0,weighted.rewards.sumOf {weighted.probability(it.itemId).fraction},0.000001)
    }
}
