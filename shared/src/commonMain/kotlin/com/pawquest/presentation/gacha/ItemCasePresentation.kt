package com.pawquest.presentation.gacha

import com.pawquest.domain.model.*
import kotlin.time.Instant

internal data class CaseItemDisplay(
    val itemId: String,val name: String,val assetId: String,val rarity: ItemRarity,
    val category: ItemCategory?,val owned: Boolean?=null,val newItem: Boolean=false,
)

internal fun caseCollection(config: ItemCaseConfiguration,catalog: List<CatalogItem>,inventory: List<InventoryItem>,ownershipAvailable: Boolean): List<CaseItemDisplay> {
    val names=catalog.associateBy {it.id}
    val owned=inventory.filter {it.quantity>0}.map {it.item.id}.toSet()
    return config.rewards.map { reward -> CaseItemDisplay(reward.itemId,names[reward.itemId]?.name ?: reward.itemCode,
        reward.assetId,reward.rarity,reward.category,if(ownershipAvailable) reward.itemId in owned else null) }
}

internal fun caseReveal(receipt: ItemCaseReceipt,config: ItemCaseConfiguration?,catalog: List<CatalogItem>): CaseItemDisplay {
    val item=catalog.firstOrNull {it.id==receipt.winningItemId}
    val reward=config?.rewards?.firstOrNull {it.itemId==receipt.winningItemId}
    val category=reward?.category ?: item?.category ?: when {
        accessorySlot(receipt.itemCode)!=null -> ItemCategory.ACCESSORY
        furnitureSlot(receipt.itemCode)!=null -> ItemCategory.FURNITURE
        else -> null
    }
    return CaseItemDisplay(receipt.winningItemId,receipt.itemName,receipt.assetId,receipt.rarity,category,
        newItem=receipt.outcome==ItemCaseRewardOutcome.ITEM_GRANTED && (reward?.maxQuantity ?: item?.maxQuantity)==1)
}

internal fun caseDate(value: Instant)=value.toString().replace('T',' ').substringBefore('.').removeSuffix("Z")+" UTC"
