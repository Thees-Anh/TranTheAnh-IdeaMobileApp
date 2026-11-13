package com.pawquest.domain.model

import kotlin.time.Instant

enum class ItemCategory { FOOD, ACCESSORY, FURNITURE }

/** assetId is a logical identifier, not a guarantee that a render asset exists. */
data class CatalogItem(
    val id: String,
    val code: String,
    val name: String,
    val category: ItemCategory,
    val priceCoins: Int,
    val assetId: String,
    val maxQuantity: Int,
    val active: Boolean,
    val description: String="",
) {
    val stackable: Boolean get() = category == ItemCategory.FOOD
    init {
        require(priceCoins in 0..1_000_000)
        require(maxQuantity == if (stackable) 1_000_000 else 1)
        require(id.isNotBlank() && code.isNotBlank() && name.isNotBlank() && assetId.isNotBlank())
    }
}

data class InventoryItem(
    val id: String,
    val userId: String,
    val item: CatalogItem,
    val quantity: Int,
    val createdAt: Instant,
) {
    init {
        require(quantity in 0..item.maxQuantity)
        require(item.stackable || quantity == 1)
    }
}
