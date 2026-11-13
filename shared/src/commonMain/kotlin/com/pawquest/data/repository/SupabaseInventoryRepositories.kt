package com.pawquest.data.repository

import com.pawquest.domain.model.*
import com.pawquest.domain.repository.ItemRepository
import com.pawquest.domain.repository.InventoryRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable private data class ItemRecord(
    val id: String,val code: String,val name: String,val category: String,
    @SerialName("price_coins") val price: Int,@SerialName("asset_id") val asset: String,
    @SerialName("max_quantity") val maximum: Int,val active: Boolean,
    val description: String="",
) {
    fun domain() = CatalogItem(id,code,name,ItemCategory.valueOf(category),price,asset,maximum,active,description)
}
@Serializable private data class InventoryRecord(
    val id: String,@SerialName("user_id") val owner: String,val quantity: Int,
    @SerialName("created_at") val created: String,val item: ItemRecord,
) {
    fun domain() = InventoryItem(id,owner,item.domain(),quantity,Instant.parse(created))
}

private suspend fun <T> inventoryRequest(block: suspend () -> T): T = try { block() }
catch (cancelled: CancellationException) { throw cancelled }
catch (_: Exception) { error("Couldn't load shop or inventory. Check your connection and retry.") }

class SupabaseItemRepository(private val client: SupabaseClient) : ItemRepository {
    override suspend fun getItems(): List<CatalogItem> = inventoryRequest {
        client.auth.awaitInitialization()
        check(client.auth.currentUserOrNull()!=null)
        client.postgrest.rpc("shop_catalog").decodeAs<List<ItemRecord>>().map { it.domain() }
    }
    override suspend fun purchase(request: PurchaseRequest): PurchaseResult {
        request.validated()
        return inventoryRequest {
            client.auth.awaitInitialization()
            val owner=client.auth.currentUserOrNull()?.id ?: error("Sign in required")
            val response=client.postgrest.rpc("shop_purchase",buildJsonObject {
                put("p_item_id",request.itemId); put("p_quantity",request.quantity); put("p_request_id",request.requestId)
            }).decodeAs<PurchaseResponse>()
            val receipt=response.receipt?.let {
                check(it.owner==owner && it.request.equals(request.requestId,ignoreCase=true) &&
                    it.item.equals(request.itemId,ignoreCase=true) && it.quantity==request.quantity)
                check(it.kind=="shop_purchase" && it.price in 0..1000000 && it.coins == -it.price.toLong()*it.quantity && it.balance>=0)
                PurchaseReceipt(it.id,it.request,it.item,it.quantity,it.price,-it.coins,it.balance,Instant.parse(it.created))
            }
            PurchaseResult(PurchaseStatus.valueOf(response.status.uppercase()),receipt)
        }
    }
}
@Serializable private data class PurchaseRecord(val id: String,@SerialName("user_id") val owner: String,
    val kind: String,@SerialName("request_id") val request: String,@SerialName("item_id") val item: String,
    val quantity: Int,@SerialName("unit_price") val price: Int,val coins: Long,
    @SerialName("balance_after") val balance: Long,@SerialName("created_at") val created: String)
@Serializable private data class PurchaseResponse(val status: String,val receipt: PurchaseRecord?=null)
class SupabaseInventoryRepository(private val client: SupabaseClient) : InventoryRepository {
    override suspend fun getInventory(): List<InventoryItem> = inventoryRequest {
        client.auth.awaitInitialization()
        val owner=client.auth.currentUserOrNull()?.id ?: error("Sign in required")
        client.postgrest.rpc("inventory_items").decodeAs<List<InventoryRecord>>().map {
            check(it.owner==owner)
            it.domain()
        }
    }
}
class UnconfiguredItemRepository : ItemRepository {
    override suspend fun getItems(): List<CatalogItem> = error("Shop storage is not configured.")
    override suspend fun purchase(request: PurchaseRequest): PurchaseResult = error("Shop storage is not configured.")
}
class UnconfiguredInventoryRepository : InventoryRepository {
    override suspend fun getInventory(): List<InventoryItem> = error("Inventory storage is not configured.")
}
