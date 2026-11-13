package com.pawquest.data.repository

import com.pawquest.domain.model.*
import com.pawquest.domain.repository.EquipmentRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.*
import kotlin.time.Instant

@Serializable private data class EquipmentRecord(val slot: String,@SerialName("item_id") val item: String,
    @SerialName("asset_id") val asset: String,@SerialName("equipped_at") val time: String)
class SupabaseEquipmentRepository(private val client: SupabaseClient) : EquipmentRepository {
    override suspend fun getEquipment()=request("equipment_state",buildJsonObject {})
    override suspend fun setEquipment(slot: EquipmentSlot,itemId: String?)=request("equipment_set",buildJsonObject {
        put("p_slot",slot.name); put("p_item_id",itemId?.let(::JsonPrimitive) ?: JsonNull)
    })
    private suspend fun request(rpc: String,args: JsonObject): List<EquippedItem> {
        try {
            client.auth.awaitInitialization()
            check(client.auth.currentUserOrNull()!=null)
            return client.postgrest.rpc(rpc,args).decodeAs<List<EquipmentRecord>>().map {
                EquippedItem(EquipmentSlot.valueOf(it.slot),it.item,it.asset,Instant.parse(it.time))
            }.also { check(it.map { row -> row.slot }.distinct().size==it.size) }
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(error: RestException) { throw EquipmentException(error.statusCode !in listOf(400,401,403,404)) }
        catch(_: Exception) { throw EquipmentException(true) }
    }
}
class UnconfiguredEquipmentRepository : EquipmentRepository {
    override suspend fun getEquipment(): List<EquippedItem> = error("Equipment storage is not configured.")
    override suspend fun setEquipment(slot: EquipmentSlot,itemId: String?): List<EquippedItem> = getEquipment()
}
