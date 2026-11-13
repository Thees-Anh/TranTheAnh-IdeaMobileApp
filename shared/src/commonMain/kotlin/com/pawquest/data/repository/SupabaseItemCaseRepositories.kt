package com.pawquest.data.repository

import com.pawquest.domain.model.*
import com.pawquest.domain.repository.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.api.authenticatedSupabaseApi
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Instant

private suspend fun SupabaseClient.caseOwner(): String {
    auth.awaitInitialization()
    return auth.currentUserOrNull()?.id ?: error("Sign in required.")
}
private suspend fun <T> caseRequest(block: suspend () -> T): T = try { block() }
catch (cancelled: CancellationException) { throw cancelled }
catch (_: Exception) { error("Couldn't confirm Item Case data. Retry the same request safely.") }
private val itemCaseJson=Json { ignoreUnknownKeys=true }

@Serializable private data class BannerRecord(val id: String,val code: String,val enabled: Boolean,
    @SerialName("current_version") val version: Int?)
@Serializable private data class VersionRecord(val id: String,@SerialName("banner_id") val bannerId: String,val version: Int,
    @SerialName("price_coins") val price: Int)
@Serializable private data class PoolRecord(@SerialName("version_id") val version: String,@SerialName("item_id") val item: String,
    @SerialName("item_code") val code: String,@SerialName("asset_id") val asset: String,val category: String,
    @SerialName("max_quantity") val maximum: Int,val rarity: String,val weight: Int,val redeemable: Boolean,
    @SerialName("probability_bps") val bps: Int,@SerialName("duplicate_item_shards") val duplicate: Int,
    @SerialName("redemption_item_shards") val redemption: Int)
@Serializable private data class CaseResponseRecord(val status: String,val receipt: CaseReceiptRecord?=null,
    @SerialName("retry_after_seconds") val retryAfter: Int?=null)
@Serializable private data class HistoryRecord(val id: String,@SerialName("user_id") val owner: String,
    @SerialName("item_id") val itemId: String,@SerialName("coins_spent") val coinsSpent: Int,
    @SerialName("item_shards_granted") val shards: Int,@SerialName("created_at") val created: String,
    val outcome: String,@SerialName("item_case_pool") val reward: HistoryRewardRecord)
@Serializable private data class HistoryRewardRecord(@SerialName("item_code") val code: String,
    @SerialName("asset_id") val asset: String,val category: String,val rarity: String)
@Serializable private data class CaseReceiptRecord(
    @SerialName("transaction_id") val transaction: String,@SerialName("request_id") val request: String,
    @SerialName("banner_id") val banner: String,@SerialName("configuration_version_id") val versionId: String,
    @SerialName("configuration_version") val version: Int,@SerialName("winning_item_id") val item: String,
    @SerialName("item_name") val name: String,@SerialName("item_code") val code: String,@SerialName("asset_id") val asset: String,
    val rarity: String,@SerialName("opening_cost_coins") val price: Int,@SerialName("reward_outcome") val outcome: String,
    @SerialName("quantity_granted") val quantity: Int,@SerialName("item_shards_granted") val shards: Int,
    @SerialName("coins_balance_after") val coinsAfter: Long,@SerialName("item_shards_balance_after") val shardsAfter: Long,
    @SerialName("created_at") val created: String,
) {
    fun domain() = ItemCaseReceipt(transaction,request,banner,versionId,version,item,name,code,asset,ItemRarity.valueOf(rarity),
        price,ItemCaseRewardOutcome.valueOf(outcome),quantity,shards,coinsAfter,shardsAfter,Instant.parse(created))
}

@OptIn(SupabaseInternal::class)
class SupabaseItemCaseRepository(private val client: SupabaseClient) : ItemCaseRepository {
    override suspend fun getHistory(): List<ItemCaseHistoryEntry> = caseRequest {
        val owner=client.caseOwner()
        // Composite (version_id,item_id) FK resolves the historical pool snapshot,
        // not the banner's current rarity or configuration.
        val records=client.postgrest["item_case_openings"].select(Columns.raw("*,item_case_pool!inner(item_code,asset_id,category,rarity)")) {
            filter { eq("user_id",owner) }; order("created_at",Order.DESCENDING); order("id",Order.DESCENDING); limit(20)
        }.decodeList<HistoryRecord>()
        check(client.caseOwner()==owner && records.all { it.owner==owner && it.coinsSpent>0 && it.shards>=0 })
        records.map {
            val outcome=when(it.outcome) {
                "ITEM" -> ItemCaseRewardOutcome.ITEM_GRANTED
                "ITEM_SHARDS" -> ItemCaseRewardOutcome.DUPLICATE_CONVERTED
                else -> error("Unknown opening outcome")
            }
            check((outcome==ItemCaseRewardOutcome.DUPLICATE_CONVERTED)==(it.shards>0))
            ItemCaseHistoryEntry(it.id,it.itemId,it.coinsSpent,it.shards,Instant.parse(it.created),
                ItemRarity.valueOf(it.reward.rarity),outcome,it.reward.code,it.reward.asset,ItemCategory.valueOf(it.reward.category))
        }
    }
    override suspend fun getConfiguration(bannerId: String): LoadedItemCase = caseRequest {
        requireCaseUuid(bannerId)
        val owner=client.caseOwner()
        val banner=client.postgrest["item_case_banners"].select { filter { eq("id",bannerId) } }.decodeSingle<BannerRecord>()
        check(banner.id.equals(bannerId,true))
        val current=checkNotNull(banner.version)
        val version=client.postgrest["item_case_versions"].select {
            filter { eq("banner_id",banner.id); eq("version",current) }
        }.decodeSingle<VersionRecord>()
        check(version.bannerId==banner.id && version.version==current)
        val pool=client.postgrest["item_case_probabilities"].select {
            filter { eq("version_id",version.id) }
        }.decodeList<PoolRecord>()
        check(pool.all { it.version==version.id })
        val tiers=pool.map { ItemCaseTier(ItemRarity.valueOf(it.rarity),it.bps,it.duplicate,it.redemption) }.distinct()
        val rewards=pool.map { ItemCaseReward(it.item,it.code,it.asset,ItemCategory.valueOf(it.category),it.maximum,
            ItemRarity.valueOf(it.rarity),it.weight,it.redeemable) }
        check(client.caseOwner()==owner)
        LoadedItemCase(ItemCaseBanner(banner.id,banner.code,banner.enabled,current),
            ItemCaseConfiguration(version.id,banner.id,current,version.price,tiers,rewards))
    }

    override suspend fun open(request: PendingItemCase): ItemCaseResponse = caseRequest {
        val owner=client.caseOwner()
        check(owner==request.userId)
        val session=checkNotNull(client.auth.currentSessionOrNull())
        check(session.user?.id==owner)
        val parameters=buildJsonObject {
            put("p_banner_id",request.bannerId); put("p_request_id",request.requestId)
        }
        // The RPC DSL appends custom headers to its session Authorization. Bind
        // the SDK authenticated API's token instead: exactly one account token,
        // same client/transport/error parser, no global auth configuration change.
        val api=client.authenticatedSupabaseApi(client.postgrest,requireSession=true) { jwtToken=session.accessToken }
        val body=api.post("rpc/item_case_open") { contentType(ContentType.Application.Json); setBody(parameters.toString()) }.bodyAsText()
        val response=itemCaseJson.decodeFromString<CaseResponseRecord>(body)
        check(client.caseOwner()==owner)
        if(response.status=="opened") {
            val receipt=checkNotNull(response.receipt).domain()
            check(receipt.requestId.equals(request.requestId,true) && receipt.bannerId.equals(request.bannerId,true))
            // Do not compare with CURRENT catalog/price/ownership: a recovered
            // receipt can legitimately refer to an older published version.
            ItemCaseResponse.Opened(receipt)
        } else {
            check(response.receipt==null)
            val reason=ItemCaseRejection.valueOf(response.status.uppercase())
            if(reason==ItemCaseRejection.BUSY || reason==ItemCaseRejection.RATE_LIMITED) check((response.retryAfter ?: 0)>0)
            ItemCaseResponse.Rejected(reason,response.retryAfter)
        }
    }
}

@Serializable private data class ShardRecord(@SerialName("user_id") val owner: String,val currency: String,val balance: Long)
@Serializable private data class LedgerRecord(val id: String,@SerialName("user_id") val owner: String,val currency: String,
    @SerialName("request_id") val request: String,val reason: String,val delta: Long,@SerialName("balance_after") val balance: Long,
    @SerialName("opening_id") val opening: String?,@SerialName("redemption_id") val redemption: String?,
    val note: String?,@SerialName("created_at") val created: String)
@OptIn(SupabaseInternal::class)
class SupabaseShardRepository(private val client: SupabaseClient) : ShardRepository {
    override suspend fun quoteItem(request: PendingItemExchange)=exchange(request,"item_shard_quote")
    override suspend fun redeemItem(request: PendingItemExchange)=exchange(request,"item_shard_redeem")
    private suspend fun exchange(request: PendingItemExchange,endpoint: String): ItemExchangeResponse = caseRequest {
        val owner=client.caseOwner();check(owner==request.userId)
        val session=checkNotNull(client.auth.currentSessionOrNull());check(session.user?.id==owner)
        val api=client.authenticatedSupabaseApi(client.postgrest,requireSession=true) {jwtToken=session.accessToken}
        val parameters=buildJsonObject {put("p_item_id",request.itemId);put("p_request_id",request.requestId)}
        val body=api.post("rpc/$endpoint") {contentType(ContentType.Application.Json);setBody(parameters.toString())}.bodyAsText()
        check(client.caseOwner()==owner)
        val json=Json.parseToJsonElement(body).jsonObject
        fun JsonObject.string(key: String)=getValue(key).jsonPrimitive.content
        when(val status=json.string("status")) {
            "quoted" -> {
                val q=json.getValue("quote").jsonObject
                check(q.string("item_id")==request.itemId && q.string("request_id")==request.requestId)
                ItemExchangeResponse.Quoted(ItemExchangeQuote(request.itemId,request.requestId,q.string("price_item_shards").toInt(),Instant.parse(q.string("expires_at"))))
            }
            "redeemed" -> {
                val r=json.getValue("receipt").jsonObject
                check(r.string("item_id")==request.itemId && r.string("request_id")==request.requestId)
                ItemExchangeResponse.Redeemed(ItemExchangeReceipt(r.string("transaction_id"),request.requestId,request.itemId,
                    r.string("item_name"),r.string("item_code"),r.string("asset_id"),ItemCategory.valueOf(r.string("category")),
                    ItemRarity.valueOf(r.string("rarity")),r.string("shards_spent").toInt(),r.string("quantity_granted").toInt(),
                    r.string("item_shards_balance_after").toLong(),Instant.parse(r.string("created_at"))))
            }
            else -> ItemExchangeResponse.Rejected(ItemExchangeRejection.valueOf(status.uppercase()))
        }
    }
    override suspend fun getItemLedger(): List<ShardLedgerEntry> = caseRequest {
        val owner=client.caseOwner()
        val rows=client.postgrest["shard_ledger"].select {
            filter { eq("user_id",owner); eq("currency",ShardCurrency.ITEM_SHARD.name) }
            order("created_at",Order.DESCENDING); order("id",Order.DESCENDING); limit(20)
        }.decodeList<LedgerRecord>()
        check(client.caseOwner()==owner && rows.all { it.owner==owner && it.currency=="ITEM_SHARD" && it.delta!=0L && it.balance>=0 })
        rows.map { ShardLedgerEntry(it.id,it.owner,ShardCurrency.ITEM_SHARD,it.request,ShardLedgerReason.valueOf(it.reason),
            it.delta,it.balance,it.opening,it.redemption,it.note,Instant.parse(it.created)) }
    }
    override suspend fun getBalances(): ShardWallet = caseRequest {
        val owner=client.caseOwner()
        val rows=client.postgrest["shard_balances"].select { filter { eq("user_id",owner) } }.decodeList<ShardRecord>()
        check(rows.all { it.owner==owner && it.balance>=0 })
        val values=rows.associate { ShardCurrency.valueOf(it.currency) to it.balance }
        check(values.size==rows.size && client.caseOwner()==owner)
        ShardWallet(values[ShardCurrency.ITEM_SHARD] ?: 0,values[ShardCurrency.PET_SHARD] ?: 0)
    }
}
class UnconfiguredItemCaseRepository : ItemCaseRepository {
    override suspend fun getConfiguration(bannerId: String): LoadedItemCase = error("Item Cases are not configured.")
    override suspend fun open(request: PendingItemCase): ItemCaseResponse = error("Item Cases are not configured.")
}
class UnconfiguredShardRepository : ShardRepository {
    override suspend fun getBalances(): ShardWallet = error("Shard storage is not configured.")
}
