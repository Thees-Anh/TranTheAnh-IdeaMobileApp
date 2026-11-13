package com.pawquest.data.repository

import com.pawquest.domain.model.*
import com.pawquest.domain.repository.FocusRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Instant

@Serializable private data class FocusRecord(val id: String,@SerialName("quest_id") val questId: String,
    @SerialName("title_snapshot") val title: String,@SerialName("duration_minutes") val duration: Int,
    @SerialName("started_at") val started: String,@SerialName("ends_at") val ends: String,val status: String,
    @SerialName("request_id") val requestId: String? = null)
@Serializable private data class RewardRecord(val coins: Int,@SerialName("pet_xp") val xp: Int,
    @SerialName("balance_after") val balance: Long)
@Serializable private data class OfferRecord(@SerialName("tier_coins") val tier: Int,val coins: Int,
    @SerialName("pet_xp") val xp: Int,@SerialName("reward_day") val day: String,
    @SerialName("rewarded_sessions") val used: Int,@SerialName("limit_reached") val limited: Boolean,
    @SerialName("can_claim") val canClaim: Boolean)
@Serializable private data class SnapshotRecord(@SerialName("server_now") val now: String,
    val session: FocusRecord?,val eligible: Boolean,val reward: RewardRecord? = null,
    @SerialName("reward_offer") val offer: OfferRecord? = null) {
    fun domain()=FocusSnapshot(Instant.parse(now),session?.let { FocusSession(it.id,it.questId,it.title,it.duration,
        Instant.parse(it.started),Instant.parse(it.ends),FocusStatus.valueOf(it.status.uppercase()),it.requestId) },eligible,
        reward?.let { ClaimedReward(it.coins,it.xp,it.balance) },
        offer?.let { RewardOffer(it.tier,it.coins,it.xp,it.day,it.used,it.limited,it.canClaim) })
}
class SupabaseFocusRepository(private val client: SupabaseClient) : FocusRepository {
    private suspend fun rpc(name: String, parameters: JsonObject): FocusSnapshot {
        // Signed in from the offline cache while the SDK still retries the network: fail fast instead of hanging.
        if (client.auth.sessionStatus.value !is io.github.jan.supabase.auth.status.SessionStatus.Authenticated &&
            com.pawquest.data.offline.ConnectionStatus.offlineDataFrom != null)
            throw IllegalStateException("Connection interrupted. Sync to check the latest session state.")
        client.auth.awaitInitialization()
        check(client.auth.currentUserOrNull()!=null) { "Please sign in again." }
        try { return client.postgrest.rpc(name,parameters).decodeAs<SnapshotRecord>().domain() }
        catch(error: CancellationException) { throw error }
        catch(error: RestException) {
            val text=error.message.orEmpty()
            throw IllegalStateException(when {
                "Reward already claimed" in text -> "This reward was already claimed. Sync to view your receipt."
                "Daily rewarded session limit" in text -> "You've reached five rewarded sessions for this UTC reward day."
                "not eligible" in text -> "This session is not eligible yet. Sync and try again."
                "active session" in text -> "You already have an active session. Sync to restore it."
                else -> "The server couldn't complete this action. Sync and try again."
            })
        }
        catch(_: Exception) { throw IllegalStateException("Connection interrupted. Sync to check the latest session state.") }
    }
    override suspend fun start(questId: String,requestId: String)=rpc("focus_start",buildJsonObject {
        put("p_quest_id",questId); put("p_request_id",requestId)
    })
    override suspend fun reconcile(sessionId: String?)=rpc("reward_reconcile",buildJsonObject {
        if(sessionId!=null) put("p_session_id",sessionId)
    })
    override suspend fun cancel(sessionId: String)=rpc("focus_cancel",buildJsonObject { put("p_session_id",sessionId) })
    override suspend fun claim(sessionId: String)=rpc("reward_claim",buildJsonObject { put("p_session_id",sessionId) })
}
class UnconfiguredFocusRepository : FocusRepository {
    override suspend fun start(questId: String,requestId: String): FocusSnapshot=error("Focus sessions are not configured.")
    override suspend fun reconcile(sessionId: String?): FocusSnapshot=error("Focus sessions are not configured.")
    override suspend fun cancel(sessionId: String): FocusSnapshot=error("Focus sessions are not configured.")
    override suspend fun claim(sessionId: String): FocusSnapshot=error("Focus sessions are not configured.")
}
