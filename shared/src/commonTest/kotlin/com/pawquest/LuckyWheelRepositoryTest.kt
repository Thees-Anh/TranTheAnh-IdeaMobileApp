package com.pawquest

import com.pawquest.data.repository.SupabaseLuckyWheelRepository
import com.pawquest.domain.model.*
import com.pawquest.presentation.gacha.LuckyWheelViewModel
import io.github.jan.supabase.auth.*
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.logging.LogLevel
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(SupabaseInternal::class)
class LuckyWheelRepositoryTest {
    private val owner="ab280000-0000-0000-0000-000000000001"
    private val key="ab280000-0000-0000-0000-000000000002"
    private val session get()="""{"access_token":"test.access.signature","refresh_token":"test-only","expires_in":3600,"token_type":"bearer","user":{"id":"$owner","aud":"authenticated","role":"authenticated"}}"""
    private fun receipt(request: String=key)=LuckyWheelReceipt(key,request,LuckyWheelViewModel.BANNER,key,1,WheelSector.PET_UNLOCK,WheelPayment.TICKET,
        0,1,WheelReward.PET_UNLOCK_CONVERTED_TO_SHARDS,null,0,null,50,0,0,1000,260,0,"2026-11-10T10:00:00Z")
    private fun TestScope.client(engine: MockEngine)=createSupabaseClient("https://wheel-test.supabase.co","sb_publishable_test_only") {
        defaultLogLevel=LogLevel.NONE;coroutineDispatcher=StandardTestDispatcher(testScheduler);httpEngine=engine
        install(Auth) {
            autoSetupPlatform=false;autoLoadFromStorage=false;autoSaveToStorage=false;alwaysAutoRefresh=false;codeVerifierCache=MemoryCodeVerifierCache()
            sessionManager=object : SessionManager {
                override suspend fun saveSession(session: UserSession)=Unit
                override suspend fun loadSession(): UserSession=throw NoSessionFoundException()
                override suspend fun deleteSession()=Unit
            }
        }
        install(Postgrest)
    }
    @Test fun authenticatedSpinUsesOnlyThreeInputsAndRejectsMismatchedResult()=runTest {
        var invalid=false
        val sdk=client(MockEngine {request ->
            val response=if(request.url.encodedPath=="/auth/v1/token") session else {
                assertEquals(listOf("Bearer test.access.signature"),request.headers.getAll(HttpHeaders.Authorization))
                assertEquals("/rest/v1/rpc/lucky_wheel_spin",request.url.encodedPath)
                val body=Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                assertEquals(setOf("p_banner_id","p_request_id","p_payment_method"),body.keys)
                assertEquals("TICKET",body.getValue("p_payment_method").jsonPrimitive.content)
                buildJsonObject {put("status","spun");put("receipt",Json.encodeToJsonElement(receipt(if(invalid) owner else key)))}.toString()
            }
            respond(response,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try {
            sdk.auth.signInWith(Email) {email="test@example.invalid";password="test-only"}
            val repo=SupabaseLuckyWheelRepository(sdk);val request=LuckyWheelSpinRequest(LuckyWheelViewModel.BANNER,WheelPayment.TICKET,key)
            assertEquals(WheelSector.PET_UNLOCK,(repo.spin(request,owner) as LuckyWheelResponse.Spun).receipt.originalSector)
            assertFailsWith<IllegalStateException> {repo.spin(request,key)}
            invalid=true;assertFailsWith<IllegalStateException> {repo.spin(request,owner)}
        } finally {sdk.close()}
    }
    @Test fun petQuoteAndRedemptionSendOnlyCanonicalSpeciesAndStableKey()=runTest {
        val sdk=client(MockEngine {request ->
            val response=if(request.url.encodedPath=="/auth/v1/token") session else {
                val body=Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                assertEquals(setOf("p_species_code","p_request_id"),body.keys)
                assertEquals("puppy",body.getValue("p_species_code").jsonPrimitive.content)
                assertEquals(key,body.getValue("p_request_id").jsonPrimitive.content)
                if(request.url.encodedPath.endsWith("pet_shard_quote")) """{"status":"quoted","quote":{"request_id":"$key","version_id":"$key","species_code":"puppy","price":70,"expires_at":"2026-11-10T10:10:00Z"}}"""
                else """{"status":"redeemed","receipt":{"transaction_id":"$key","request_id":"$key","version_id":"$key","species_code":"puppy","shards_spent":70,"pet_shards_balance_after":20,"created_at":"2026-11-10T10:00:00Z"}}"""
            };respond(response,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try {
            sdk.auth.signInWith(Email) {email="test@example.invalid";password="test-only"}
            val repo=SupabaseLuckyWheelRepository(sdk);val request=PetShardRequest("puppy",key)
            assertEquals(70,(repo.quote(request,owner) as PetShardResponse.Quoted).quote.price)
            assertEquals(20L,(repo.redeem(request,owner) as PetShardResponse.Redeemed).receipt.petShardsBalanceAfter)
        } finally {sdk.close()}
    }
    @Test fun historyIsBoundedAndLateCrossAccountResponsesAreRejected()=runTest {
        var switch=false
        lateinit var sdk: io.github.jan.supabase.SupabaseClient
        sdk=client(MockEngine {request ->
            val response=if(request.url.encodedPath=="/auth/v1/token") session else {
                val body=Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                assertEquals(20,body.getValue("p_limit").jsonPrimitive.int)
                if(switch) sdk.auth.setSessionStatus(SessionStatus.NotAuthenticated())
                "[]"
            };respond(response,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try {
            sdk.auth.signInWith(Email) {email="test@example.invalid";password="test-only"}
            val repo=SupabaseLuckyWheelRepository(sdk);assertTrue(repo.history().isEmpty())
            switch=true;assertFailsWith<IllegalStateException> {repo.history()}
        } finally {sdk.close()}
    }
}
