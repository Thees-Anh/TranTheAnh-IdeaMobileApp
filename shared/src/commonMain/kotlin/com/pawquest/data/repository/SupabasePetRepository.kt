package com.pawquest.data.repository

import com.pawquest.domain.model.*
import com.pawquest.domain.repository.PetRepository
import com.pawquest.domain.repository.BistroIntentStore
import com.pawquest.domain.repository.UnavailableBistroIntentStore
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.api.authenticatedSupabaseApi
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.*
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlin.time.TimeSource
import kotlin.time.Instant
import com.pawquest.data.offline.save
import com.pawquest.data.offline.savedAt

@Serializable private data class PetRecord(val id: String,@SerialName("user_id") val userId: String,
    val name: String,val hydration: Int=100,val hunger: Int,val happiness: Int,val energy: Int,val xp: Long,
    @SerialName("stats_updated_at") val updated: String,@SerialName("sleep_until") val sleepUntil: String?=null,
    @SerialName("species_code") val speciesCode: String="mochi_cat",@SerialName("renderer_key") val rendererKey: String="prototype_cat")
@Serializable private data class CareRecord(@SerialName("request_id") val id: String,val action: String,val code: String,
    val applied: Boolean,@SerialName("food_consumed") val food: Int)
@Serializable private data class PetResponse(val pet: PetRecord,val coins: Long,@SerialName("server_now") val now: String,
    @SerialName("food_quantity") val food: Int,@SerialName("care_result") val result: CareRecord?=null) {
    fun domain()=PetSnapshot(Pet(pet.id,pet.userId,pet.name,PetStats(pet.hunger,pet.happiness,pet.energy,pet.hydration),
        PetProgress(pet.xp),Instant.parse(pet.updated),pet.sleepUntil?.let(Instant::parse),pet.speciesCode,pet.rendererKey),coins,Instant.parse(now),food,
        result?.let { PetActionResult(it.id,PetCareAction.valueOf(it.action.uppercase()),it.code,it.applied,it.food) })
}
@OptIn(SupabaseInternal::class)
class SupabasePetRepository(private val client: SupabaseClient,private val diagnostic: (String)->Unit={},
    private val bistroStore: BistroIntentStore=UnavailableBistroIntentStore(),
    private val offline: com.pawquest.data.offline.OfflineCache=com.pawquest.data.offline.MemoryOfflineCache()) : PetRepository {
    private val json=Json {ignoreUnknownKeys=true}
    override suspend fun pendingMeal(owner: String)=bistroStore.load(owner)
    override suspend fun saveMeal(request: PendingBistroMeal)=bistroStore.save(request)
    override suspend fun clearMeal(request: PendingBistroMeal)=bistroStore.clear(request)
    private suspend fun bistroRpc(route: String,params: JsonObject=buildJsonObject {},expectedOwner: String?=null): JsonElement {
        client.auth.awaitInitialization()
        val session=checkNotNull(client.auth.currentSessionOrNull()) {"Sign in required"}
        val owner=checkNotNull(session.user?.id)
        check(expectedOwner==null || expectedOwner==owner) {"Account changed"}
        val api=client.authenticatedSupabaseApi(client.postgrest,requireSession=true) {jwtToken=session.accessToken}
        val body=api.post("rpc/$route") {contentType(ContentType.Application.Json);setBody(params.toString())}.bodyAsText()
        check(client.auth.currentUserOrNull()?.id==owner) {"Account changed"}
        return json.parseToJsonElement(body)
    }
    override suspend fun bistroMenu(): List<BistroItem> = json.decodeFromJsonElement<List<BistroItem>>(bistroRpc("bistro_menu")).also {rows ->
        check(rows.size<=12 && rows.map {it.itemId}.distinct().size==rows.size && rows.map {it.assetId}.distinct().size==rows.size)
    }.sortedWith(compareBy({it.price},{it.order}))
    override suspend fun bistroFeed(request: PendingBistroMeal): BistroResponse {
        val body=bistroRpc("bistro_feed",buildJsonObject {put("p_item_id",request.itemId);put("p_request_id",request.requestId);put("p_expected_price",request.expectedPrice)},request.userId).jsonObject
        return if(body.getValue("status").jsonPrimitive.content=="fed") {
            val receipt=json.decodeFromJsonElement<BistroReceipt>(body.getValue("receipt"))
            check(receipt.userId==request.userId && receipt.itemId==request.itemId && receipt.requestId==request.requestId && receipt.price==request.expectedPrice)
            BistroResponse.Fed(receipt)
        } else BistroResponse.Rejected(body.getValue("status").jsonPrimitive.content)
    }
    override suspend fun companionContext(timezone: String): PetCompanionContext {
        client.auth.awaitInitialization()
        val owner=client.auth.currentUserOrNull()?.id ?: error("Please sign in again.")
        return client.postgrest.rpc("pet_companion_context",buildJsonObject {put("p_timezone",timezone)})
            .decodeAs<CompanionRecord>().also {check(it.owner==owner)}.let {
                PetCompanionContext(Instant.parse(it.now),it.missed,it.completionId,it.completedAt?.let(Instant::parse))
            }
    }
    override suspend fun collection(): PetCollection {
        client.auth.awaitInitialization()
        check(client.auth.currentUserOrNull()!=null) {"Please sign in again."}
        return client.postgrest.rpc("pet_collection",buildJsonObject {}).decodeAs<CollectionRecord>().domain()
    }
    override suspend fun selectSpecies(code: String): PetSnapshot {
        return request(buildJsonObject {put("p_code",code)},"pet_select_species")
    }
    private fun cachedSnapshot(): PetSnapshot? {
        val body=offline.read(com.pawquest.data.offline.OfflineKeys.PET) ?: return null
        val snapshot=runCatching {json.decodeFromString<PetResponse>(body).domain().copy(actionResult=null)}.getOrNull() ?: return null
        com.pawquest.data.offline.ConnectionStatus.usingCache(offline.savedAt(com.pawquest.data.offline.OfflineKeys.PET))
        return snapshot
    }
    private suspend fun request(params: JsonObject,route: String="pet_state"): PetSnapshot {
        val started=TimeSource.Monotonic.markNow()
        val operation=if(route=="pet_select_species") "select" else if(params["p_action"]!=null) "care" else "restore"
        diagnostic("pet_$operation started")
        // No usable session yet (initializing or refresh failing offline): never wait on the SDK's retry loop.
        if(operation=="restore" && client.auth.sessionStatus.value !is io.github.jan.supabase.auth.status.SessionStatus.Authenticated)
            cachedSnapshot()?.let {return it}
        try {
            client.auth.awaitInitialization()
            val session=checkNotNull(client.auth.currentSessionOrNull()) {"Please sign in again."}
            val owner=checkNotNull(session.user?.id)
            diagnostic("pet_$operation session_ready")
            val api=client.authenticatedSupabaseApi(client.postgrest,requireSession=true) {jwtToken=session.accessToken}
            val body=api.post("rpc/$route") {contentType(ContentType.Application.Json);setBody(params.toString())}.bodyAsText()
            val response=json.decodeFromString<PetResponse>(body)
            check(client.auth.currentUserOrNull()?.id==owner && response.pet.userId==owner) {"Unexpected pet owner"}
            params["p_action"]?.let {action ->
                val result=checkNotNull(response.result)
                check(result.id==params.getValue("p_request_id").jsonPrimitive.content && result.action==action.jsonPrimitive.content)
            }
            diagnostic("pet_$operation confirmed elapsed_ms=${started.elapsedNow().inWholeMilliseconds}")
            // Every route returns the full confirmed state; keep the latest for offline viewing.
            offline.save(com.pawquest.data.offline.OfflineKeys.PET,body)
            com.pawquest.data.offline.ConnectionStatus.online()
            return response.domain()
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(error: RestException) {
            diagnostic("pet_$operation failed type=${error::class.simpleName} http=${error.statusCode} elapsed_ms=${started.elapsedNow().inWholeMilliseconds}")
            error("Couldn't sync your pet. Please retry.")
        }
        catch(error: Exception) {
            diagnostic("pet_$operation failed type=${error::class.simpleName} elapsed_ms=${started.elapsedNow().inWholeMilliseconds}")
            // Offline viewing only: a restore may show the last confirmed state; care/select never pretend to succeed.
            if(operation=="restore") cachedSnapshot()?.let {return it}
            error("Connection interrupted. Your saved pet is safe; retry when online.")
        }
    }
    override suspend fun restore()=request(buildJsonObject {})
    override suspend fun care(action: PetCareAction,requestId: String)=request(buildJsonObject {
        put("p_action",action.name.lowercase()); put("p_request_id",requestId)
    })
}
@Serializable private data class CompanionRecord(@SerialName("user_id") val owner: String,
    @SerialName("server_now") val now: String,@SerialName("has_missed_quest") val missed: Boolean,
    @SerialName("completion_id") val completionId: String?=null,@SerialName("completed_at") val completedAt: String?=null)
@Serializable private data class SpeciesRecord(val code: String,val name: String,
    @SerialName("unlock_level") val level: Long,@SerialName("renderer_key") val renderer: String,val unlocked: Boolean)
@Serializable private data class CollectionRecord(@SerialName("active_code") val active: String,val level: Long,val species: List<SpeciesRecord>) {
    fun domain()=PetCollection(active,level,species.map {PetSpecies(it.code,it.name,it.level,it.renderer,it.unlocked)})
}
class UnconfiguredPetRepository : PetRepository {
    override suspend fun restore(): PetSnapshot=error("Pet storage is not configured.")
    override suspend fun care(action: PetCareAction,requestId: String): PetSnapshot=error("Pet storage is not configured.")
}
