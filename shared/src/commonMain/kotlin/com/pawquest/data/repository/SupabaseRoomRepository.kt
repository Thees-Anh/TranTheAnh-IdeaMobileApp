package com.pawquest.data.repository

import com.pawquest.domain.model.*
import com.pawquest.domain.repository.RoomRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.*
import kotlin.time.Instant

@Serializable private data class RoomRecord(val slot: String,@SerialName("item_id") val item: String,
    @SerialName("asset_id") val asset: String,@SerialName("placed_at") val time: String)
class SupabaseRoomRepository(private val client: SupabaseClient) : RoomRepository {
    override suspend fun getRoom()=request("room_state",buildJsonObject {})
    override suspend fun setFurniture(slot: FurnitureSlot,itemId: String?)=request("room_set",buildJsonObject {
        put("p_slot",slot.name); put("p_item_id",itemId?.let(::JsonPrimitive) ?: JsonNull)
    })
    private suspend fun request(rpc: String,args: JsonObject): List<RoomFurniture> {
        try {
            client.auth.awaitInitialization()
            check(client.auth.currentUserOrNull()!=null)
            return client.postgrest.rpc(rpc,args).decodeAs<List<RoomRecord>>().map {
                RoomFurniture(FurnitureSlot.valueOf(it.slot),it.item,it.asset,Instant.parse(it.time))
            }.also { check(it.map { row -> row.slot }.distinct().size==it.size) }
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(error: RestException) { throw RoomException(error.statusCode !in listOf(400,401,403,404)) }
        catch(_: Exception) { throw RoomException(true) }
    }
}
class UnconfiguredRoomRepository : RoomRepository {
    override suspend fun getRoom(): List<RoomFurniture> = error("Room storage is not configured.")
    override suspend fun setFurniture(slot: FurnitureSlot,itemId: String?): List<RoomFurniture> = getRoom()
}
