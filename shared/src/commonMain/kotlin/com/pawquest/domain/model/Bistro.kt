package com.pawquest.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable data class BistroItem(@SerialName("item_id") val itemId: String,@SerialName("asset_id") val assetId: String,
    val name: String,@SerialName("price_coins") val price: Int,val tier: String,@SerialName("meal_type") val type: String,
    @SerialName("menu_order") val order: Int,@SerialName("hunger_gain") val hunger: Int,
    @SerialName("happiness_gain") val happiness: Int,@SerialName("xp_gain") val xp: Int,val version: Int) {
    init {requireCaseUuid(itemId);require(assetId in BistroAssets.ids && price>0 && tier in setOf("BASIC","TASTY","PREMIUM","ROYAL") && type in setOf("food","drink") && order in 1..12 && hunger in 0..100 && happiness in 0..100 && xp in 0..1000 && version>0)}
}
object BistroAssets {val ids=setOf("food-bone-biscuit","food-fresh-carrot","food-warm-milk","food-taiyaki","food-onigiri","food-boba-tea","food-cat-pudding","food-ramen-bowl","food-salmon-sushi","food-berry-parfait","food-wagyu-steak","food-royal-hotpot")}
@Serializable data class PendingBistroMeal(val userId: String,val itemId: String,val requestId: String,val expectedPrice: Int) {
    init {requireCaseUuid(userId);requireCaseUuid(itemId);requireCaseUuid(requestId);require(expectedPrice>0)}
}
@Serializable data class BistroReceipt(val id: String,@SerialName("user_id") val userId: String,
    @SerialName("request_id") val requestId: String,@SerialName("item_id") val itemId: String,
    @SerialName("wallet_transaction_id") val walletTransactionId: String,@SerialName("menu_version") val version: Int,
    @SerialName("item_name") val name: String,@SerialName("asset_id") val assetId: String,@SerialName("meal_type") val type: String,
    @SerialName("price_coins") val price: Int,@SerialName("coins_balance_after") val coinsAfter: Long,
    @SerialName("hunger_granted") val hunger: Int,@SerialName("happiness_granted") val happiness: Int,
    @SerialName("xp_granted") val xp: Int,@SerialName("pet_id") val petId: String,
    @SerialName("species_code") val species: String,@SerialName("created_at") val createdAt: String) {
    init {listOf(id,userId,requestId,itemId,walletTransactionId,petId).forEach(::requireCaseUuid)
        require(assetId in BistroAssets.ids && type in setOf("food","drink") && price>0 && coinsAfter>=0 && hunger in 0..100 && happiness in 0..100 && xp in 0..1000 && version>0)}
}
sealed interface BistroResponse {
    data class Fed(val receipt: BistroReceipt): BistroResponse
    data class Rejected(val reason: String): BistroResponse
}
