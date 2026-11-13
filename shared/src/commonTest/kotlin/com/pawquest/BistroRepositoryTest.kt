package com.pawquest

import com.pawquest.data.repository.SupabasePetRepository
import com.pawquest.domain.model.*
import io.github.jan.supabase.auth.*
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.logging.LogLevel
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(io.github.jan.supabase.annotations.SupabaseInternal::class)
class BistroRepositoryTest {
    private val owner="ba290000-0000-0000-0000-000000000001"
    private val item="e2900000-0000-0000-0000-000000000001"
    private val key="bc290000-0000-0000-0000-000000000001"
    private fun TestScope.client(engine: MockEngine)=createSupabaseClient("https://bistro-test.supabase.co","sb_publishable_test_only") {
        defaultLogLevel=LogLevel.NONE;coroutineDispatcher=StandardTestDispatcher(testScheduler);httpEngine=engine
        install(Auth) {
            autoSetupPlatform=false;autoLoadFromStorage=false;autoSaveToStorage=false;alwaysAutoRefresh=false;codeVerifierCache=MemoryCodeVerifierCache()
            sessionManager=object : SessionManager {
                override suspend fun saveSession(session: UserSession)=Unit
                override suspend fun loadSession(): UserSession=throw NoSessionFoundException()
                override suspend fun deleteSession()=Unit
            }
        };install(Postgrest)
    }
    private fun session()="""{"access_token":"test.access.signature","refresh_token":"test-only","expires_in":3600,"token_type":"bearer","user":{"id":"$owner","aud":"authenticated","role":"authenticated"}}"""
    private fun receipt(requestId: String=key)=BistroReceipt(key,owner,requestId,item,key,1,"Biscuit","food-bone-biscuit","food",10,990,15,5,5,
        "be290000-0000-0000-0000-000000000001","mochi_cat","2026-11-11T00:00:00Z")
    @Test fun onlyItemKeyAndStalePriceGuardAreSentAndMismatchedReceiptsAreRejected()=runTest {
        var wrong=false;var calls=0
        val sdk=client(MockEngine {request ->
            val response=if(request.url.encodedPath=="/auth/v1/token") session() else {
                calls++
                assertEquals("/rest/v1/rpc/bistro_feed",request.url.encodedPath)
                assertEquals(listOf("Bearer test.access.signature"),request.headers.getAll(HttpHeaders.Authorization))
                val params=Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                assertEquals(setOf("p_item_id","p_request_id","p_expected_price"),params.keys)
                assertEquals(item,params.getValue("p_item_id").jsonPrimitive.content)
                assertEquals(10,params.getValue("p_expected_price").jsonPrimitive.int)
                buildJsonObject {put("status","fed");put("receipt",Json.encodeToJsonElement(receipt(if(wrong) owner else key)))}.toString()
            }
            respond(response,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try {
            sdk.auth.signInWith(Email) {email="test@example.invalid";password="test-only"}
            val repo=SupabasePetRepository(sdk);val request=PendingBistroMeal(owner,item,key,10)
            assertEquals(key,(repo.bistroFeed(request) as BistroResponse.Fed).receipt.requestId)
            wrong=true;assertFailsWith<IllegalStateException> {repo.bistroFeed(request)}
            assertFailsWith<IllegalStateException> {repo.bistroFeed(request.copy(userId=key))}
            assertEquals(2,calls)
        } finally {sdk.close()}
    }
    @Test fun responseAfterLogoutIsNotPublished()=runTest {
        lateinit var sdk: io.github.jan.supabase.SupabaseClient
        sdk=client(MockEngine {request ->
            val response=if(request.url.encodedPath=="/auth/v1/token") session() else {
                sdk.auth.setSessionStatus(SessionStatus.NotAuthenticated())
                buildJsonObject {put("status","fed");put("receipt",Json.encodeToJsonElement(receipt()))}.toString()
            };respond(response,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try {
            sdk.auth.signInWith(Email) {email="test@example.invalid";password="test-only"}
            assertFailsWith<IllegalStateException> {SupabasePetRepository(sdk).bistroFeed(PendingBistroMeal(owner,item,key,10))}
        } finally {sdk.close()}
    }
}
