package com.pawquest.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class PendingItemExchange(val userId: String,val itemId: String,val requestId: String,val confirmed: Boolean=false) {
    init {listOf(userId,itemId,requestId).forEach(::requireCaseUuid)}
}
data class ItemExchangeQuote(val itemId: String,val requestId: String,val price: Int,val expiresAt: Instant) {
    init {requireCaseUuid(itemId);requireCaseUuid(requestId);require(price in 1..1_000_000)}
}
data class ItemExchangeReceipt(val id: String,val requestId: String,val itemId: String,val name: String,
    val code: String,val assetId: String,val category: ItemCategory,val rarity: ItemRarity,
    val spent: Int,val quantity: Int,val balanceAfter: Long,val createdAt: Instant) {
    init {
        listOf(id,requestId,itemId).forEach(::requireCaseUuid)
        require(name.isNotBlank() && code.isNotBlank() && assetId.isNotBlank())
        require(category in setOf(ItemCategory.ACCESSORY,ItemCategory.FURNITURE))
        require(spent in 1..1_000_000 && quantity==1 && balanceAfter>=0)
    }
}
enum class ItemExchangeRejection {
    INVALID_REQUEST,ACCOUNT_UNAVAILABLE,REQUEST_CONFLICT,RATE_LIMITED,BUSY,ITEM_UNAVAILABLE,ALREADY_OWNED,
    INVENTORY_FULL,INSUFFICIENT_SHARDS,QUOTE_REQUIRED,QUOTE_EXPIRED,PRICE_CHANGED;
    val canDismiss get()=this !in setOf(ACCOUNT_UNAVAILABLE,REQUEST_CONFLICT,RATE_LIMITED,BUSY)
}
sealed interface ItemExchangeResponse {
    data class Quoted(val quote: ItemExchangeQuote): ItemExchangeResponse
    data class Redeemed(val receipt: ItemExchangeReceipt): ItemExchangeResponse
    data class Rejected(val reason: ItemExchangeRejection): ItemExchangeResponse
}
