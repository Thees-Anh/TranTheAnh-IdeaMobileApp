package com.pawquest.data.repository

import com.pawquest.domain.model.*
import com.pawquest.domain.repository.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.api.authenticatedSupabaseApi
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.*

/** Same SDK/client/session transport as Item Case; each response is bound to the captured account. */
@OptIn(SupabaseInternal::class)
class SupabaseLuckyWheelRepository(private val client: SupabaseClient) : LuckyWheelRepository, PetShardExchangeRepository {
    private val json=Json { ignoreUnknownKeys=true }
    private suspend fun rpc(name: String,parameters: JsonObject=buildJsonObject {},expectedOwner: String?=null): JsonElement {
        client.auth.awaitInitialization()
        val session=checkNotNull(client.auth.currentSessionOrNull()) { "Sign in required." }
        val owner=checkNotNull(session.user?.id)
        check(expectedOwner==null || expectedOwner==owner) {"Pending operation belongs to another account."}
        val api=client.authenticatedSupabaseApi(client.postgrest,requireSession=true) {jwtToken=session.accessToken}
        val body=api.post("rpc/$name") {contentType(ContentType.Application.Json);setBody(parameters.toString())}.bodyAsText()
        check(client.auth.currentUserOrNull()?.id==owner) {"Account changed."}
        val result=json.parseToJsonElement(body)
        val receipt=(result as? JsonObject)?.get("receipt") as? JsonObject
        receipt?.get("user_id")?.let {check(it.jsonPrimitive.content==owner)}
        return result
    }
    override suspend fun configuration(bannerId: String): LuckyWheelConfiguration {
        requireCaseUuid(bannerId)
        return json.decodeFromJsonElement<LuckyWheelConfiguration>(rpc("lucky_wheel_config",buildJsonObject {put("p_banner_id",bannerId)}))
            .also {check(it.bannerId==bannerId)}
    }
    override suspend fun balances()=json.decodeFromJsonElement<LuckyWheelBalances>(rpc("lucky_wheel_balances"))
    override suspend fun spin(request: LuckyWheelSpinRequest,expectedOwner: String): LuckyWheelResponse {
        val body=rpc("lucky_wheel_spin",json.encodeToJsonElement(request).jsonObject,expectedOwner).jsonObject
        return if(body.getValue("status").jsonPrimitive.content=="spun") {
            val receipt=json.decodeFromJsonElement<LuckyWheelReceipt>(body.getValue("receipt"))
            check(receipt.requestId==request.requestId && receipt.bannerId==request.bannerId && receipt.payment==request.payment)
            LuckyWheelResponse.Spun(receipt)
        } else LuckyWheelResponse.Rejected(rejection(body))
    }
    override suspend fun history(beforeCreatedAt: String?,beforeTransactionId: String?,limit: Int): List<LuckyWheelReceipt> {
        require((beforeCreatedAt==null)==(beforeTransactionId==null))
        beforeTransactionId?.let(::requireCaseUuid)
        val records=rpc("lucky_wheel_history",buildJsonObject {
            put("p_limit",limit.coerceIn(1,50));beforeCreatedAt?.let {put("p_before",it)};beforeTransactionId?.let {put("p_before_id",it)}
        }).jsonArray
        check(records.size<=limit.coerceIn(1,50))
        val owner=checkNotNull(client.auth.currentUserOrNull()?.id)
        return records.map {check(it.jsonObject.getValue("user_id").jsonPrimitive.content==owner);json.decodeFromJsonElement<LuckyWheelReceipt>(it)}
    }
    override suspend fun catalog()=json.decodeFromJsonElement<List<PetShardCatalogEntry>>(rpc("pet_shard_catalog")).also { rows ->
        check(rows.map {it.speciesCode}.toSet().size==rows.size && rows.all {it.speciesCode in redeemablePetCodes && it.price>0})
    }
    override suspend fun quote(request: PetShardRequest,expectedOwner: String)=exchange("pet_shard_quote",request,expectedOwner)
    override suspend fun redeem(request: PetShardRequest,expectedOwner: String)=exchange("pet_shard_redeem",request,expectedOwner)
    private suspend fun exchange(name: String,request: PetShardRequest,expectedOwner: String): PetShardResponse {
        val body=rpc(name,json.encodeToJsonElement(request).jsonObject,expectedOwner).jsonObject
        return when(body.getValue("status").jsonPrimitive.content) {
            "quoted" -> PetShardResponse.Quoted(json.decodeFromJsonElement<PetShardQuote>(body.getValue("quote")).also {
                check(it.speciesCode==request.speciesCode && it.requestId==request.requestId)
            })
            "redeemed" -> PetShardResponse.Redeemed(json.decodeFromJsonElement<PetShardReceipt>(body.getValue("receipt")).also {
                check(it.speciesCode==request.speciesCode && it.requestId==request.requestId)
            })
            else -> PetShardResponse.Rejected(rejection(body))
        }
    }
    private fun rejection(body: JsonObject)=LuckyWheelRejection.valueOf(body.getValue("status").jsonPrimitive.content.uppercase())
}

class UnconfiguredLuckyWheelRepository : LuckyWheelRepository, PetShardExchangeRepository {
    override suspend fun configuration(bannerId: String): LuckyWheelConfiguration=error("Lucky Wheel unavailable")
    override suspend fun balances(): LuckyWheelBalances=error("Lucky Wheel unavailable")
    override suspend fun spin(request: LuckyWheelSpinRequest,expectedOwner: String): LuckyWheelResponse=error("Lucky Wheel unavailable")
    override suspend fun history(beforeCreatedAt: String?,beforeTransactionId: String?,limit: Int): List<LuckyWheelReceipt> = error("History unavailable")
    override suspend fun catalog(): List<PetShardCatalogEntry> = error("Pet Exchange unavailable")
    override suspend fun quote(request: PetShardRequest,expectedOwner: String): PetShardResponse=error("Pet Exchange unavailable")
    override suspend fun redeem(request: PetShardRequest,expectedOwner: String): PetShardResponse=error("Pet Exchange unavailable")
}
