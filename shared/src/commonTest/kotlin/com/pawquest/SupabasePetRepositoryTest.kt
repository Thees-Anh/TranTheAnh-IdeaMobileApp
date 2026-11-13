package com.pawquest

import com.pawquest.data.repository.SupabasePetRepository
import com.pawquest.domain.model.PetCareAction
import io.github.jan.supabase.auth.*
import io.github.jan.supabase.auth.providers.builtin.Email
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

@OptIn(io.github.jan.supabase.annotations.SupabaseInternal::class)
class SupabasePetRepositoryTest {
    @Test fun careBindsSessionAndValidatesOriginalRequestReceipt()=runTest {
        val owner="ab280000-0000-0000-0000-000000000001"
        val key="ab280000-0000-0000-0000-000000000002"
        var wrongReceipt=false
        val diagnostics=mutableListOf<String>()
        val sdk=createSupabaseClient("https://pet-test.supabase.co","sb_publishable_test_only") {
            defaultLogLevel=LogLevel.NONE;coroutineDispatcher=StandardTestDispatcher(testScheduler)
            httpEngine=MockEngine {request ->
                val body=if(request.url.encodedPath=="/auth/v1/token") {
                    """{"access_token":"test.access.signature","refresh_token":"test-only","expires_in":3600,"token_type":"bearer","user":{"id":"$owner","aud":"authenticated","role":"authenticated"}}"""
                } else {
                    assertEquals("/rest/v1/rpc/pet_state",request.url.encodedPath)
                    assertEquals(listOf("Bearer test.access.signature"),request.headers.getAll(HttpHeaders.Authorization))
                    val params=Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                    assertEquals(setOf("p_action","p_request_id"),params.keys)
                    assertEquals("feed",params.getValue("p_action").jsonPrimitive.content)
                    assertEquals(key,params.getValue("p_request_id").jsonPrimitive.content)
                    """{"pet":{"id":"pet","user_id":"$owner","name":"Mochi","hunger":65,"happiness":50,"energy":50,"xp":0,"stats_updated_at":"2026-11-11T00:00:00Z"},"coins":100,"food_quantity":0,"server_now":"2026-11-11T00:00:00Z","care_result":{"request_id":"${if(wrongReceipt) owner else key}","action":"feed","code":"fed","applied":true,"food_consumed":1}}"""
                }
                respond(body,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
            }
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
        try {
            sdk.auth.signInWith(Email) {email="test@example.invalid";password="test-only"}
            val repo=SupabasePetRepository(sdk,diagnostics::add)
            assertEquals(key,repo.care(PetCareAction.FEED,key).actionResult?.requestId)
            wrongReceipt=true;assertFailsWith<IllegalStateException> {repo.care(PetCareAction.FEED,key)}
            assertTrue(diagnostics.none {it.contains("signature") || it.contains(owner) || it.contains(key)})
        } finally {sdk.close()}
    }
}
