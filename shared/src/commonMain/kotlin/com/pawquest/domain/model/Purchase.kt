package com.pawquest.domain.model

import kotlin.time.Instant

/** Reuse requestId only for retries of the same purchase. No client prices/balances. */
data class PurchaseRequest(val itemId: String,val quantity: Int,val requestId: String) {
    fun validated(): PurchaseRequest {
        val uuid=Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
        require(uuid.matches(itemId) && uuid.matches(requestId)) { "Valid item and request UUIDs required." }
        require(quantity in 1..100) { "Quantity must be between 1 and 100." }
        return this
    }
}
enum class PurchaseStatus {
    PURCHASED, INVALID_QUANTITY, ACCOUNT_UNAVAILABLE, ITEM_UNAVAILABLE,
    ALREADY_OWNED, INVENTORY_FULL, INSUFFICIENT_BALANCE, REQUEST_CONFLICT,
}
data class PurchaseReceipt(
    val id: String,val requestId: String,val itemId: String,val quantity: Int,
    val unitPrice: Int,val coinsSpent: Long,val balanceAfter: Long,val createdAt: Instant,
)
data class PurchaseResult(val status: PurchaseStatus,val receipt: PurchaseReceipt?=null) {
    init { require((status==PurchaseStatus.PURCHASED)==(receipt!=null)) }
}
