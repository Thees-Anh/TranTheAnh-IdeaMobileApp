package com.pawquest

import com.pawquest.data.auth.SupabaseAuthenticationRepository
import com.pawquest.domain.model.*
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/** Uses the real pinned SDK with deterministic HTTP responses; no cloud project or secrets. */
@OptIn(SupabaseInternal::class)
class SupabaseAuthenticationRepositoryTest {
    private class Store : SessionManager {
        var session: UserSession? = null
        override suspend fun saveSession(session: UserSession) { this.session = session }
        override suspend fun loadSession() = session ?: throw NoSessionFoundException()
        override suspend fun deleteSession() { session = null }
    }
    private val uid = "10000000-0000-0000-0000-000000000001"
    private val user get() = """{"id":"$uid","aud":"authenticated","email":"test@example.invalid","role":"authenticated"}"""
    private val session get() = """{"access_token":"test.access.signature","refresh_token":"test-refresh-only","expires_in":3600,"token_type":"bearer","user":$user}"""
    private val account get() = """{"profile":{"id":"$uid","display_name":"Adventurer","created_at":"2026-01-01T00:00:00Z"},"pet":{"id":"cat-one","name":"Mochi","xp":0},"wallet":{"coins":0}}"""

    @Test fun explicitRetryRecoversExpiredSessionWithoutWaitingForInitializationLoop()=runTest(timeout=10.seconds) {
        val store=Store().apply {session=Json {ignoreUnknownKeys=true}.decodeFromString<UserSession>(this@SupabaseAuthenticationRepositoryTest.session)
            .copy(expiresAt=Instant.parse("2000-01-01T00:00:00Z"))}
        var offline=true;var accountReads=0;val diagnostics=mutableListOf<String>()
        val client=createSupabaseClient("https://pawquest-test.supabase.co","sb_publishable_test_only") {
            coroutineDispatcher=StandardTestDispatcher(testScheduler)
            httpEngine=MockEngine {request ->
                when(request.url.encodedPath) {
                    "/auth/v1/token" -> {
                        if(offline) error("Connection refused test-only-token-must-not-be-logged")
                        respond(session,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
                    }
                    "/rest/v1/rpc/ensure_account" -> {accountReads++;respond(account,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))}
                    else -> error("Unexpected fixture request")
                }
            }
            install(Auth) {sessionManager=store;codeVerifierCache=MemoryCodeVerifierCache();autoSetupPlatform=false;retryDelay=5.seconds}
            install(Postgrest)
        }
        try {
            val repository=SupabaseAuthenticationRepository(client,backgroundScope,diagnostics::add)
            repository.state.first {it is AuthenticationState.Unavailable}
            assertNotNull(store.session);offline=false
            withTimeout(1.seconds) {repository.retryRestoration()}
            val restored=repository.state.first {it is AuthenticationState.SignedIn} as AuthenticationState.SignedIn
            assertEquals(uid,restored.account.userId);assertEquals(1,accountReads)
            assertTrue(diagnostics.any {it.contains("transport=connection")})
            assertTrue(diagnostics.none {it.contains("test-only-token") || it.contains("test-refresh-only") || it.contains("https://")})
        } finally {client.close()}
    }

    @Test fun realSdkLoginRestorationAndOfflineLogout() = runTest(timeout = 10.seconds) {
        val store = Store()
        var accountReads = 0
        var offlineLogout = false
        val paths = mutableListOf<String>()
        fun client() = createSupabaseClient("https://pawquest-test.supabase.co", "sb_publishable_test_only") {
            coroutineDispatcher = StandardTestDispatcher(testScheduler)
            httpEngine = MockEngine { request ->
                paths += request.url.encodedPath
                when (request.url.encodedPath) {
                    "/auth/v1/token" -> respond(session, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType,"application/json"))
                    "/rest/v1/rpc/ensure_account" -> {
                        accountReads++
                        assertEquals("Bearer test.access.signature", request.headers[HttpHeaders.Authorization])
                        respond(account, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType,"application/json"))
                    }
                    "/auth/v1/logout" -> {
                        if (offlineLogout) error("Simulated connection loss")
                        respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType,"application/json"))
                    }
                    else -> error("Unexpected request path")
                }
            }
            install(Auth) { sessionManager=store; codeVerifierCache=MemoryCodeVerifierCache(); autoSetupPlatform=false; alwaysAutoRefresh=false }
            install(Postgrest)
        }.also { if (store.session == null) it.auth.setSessionStatus(SessionStatus.NotAuthenticated()) }
        val firstClient = client()
        val firstRepository = SupabaseAuthenticationRepository(firstClient, backgroundScope)
        firstRepository.state.first { it is AuthenticationState.SignedOut }
        firstRepository.login("test@example.invalid", "test-password")
        val first = firstRepository.state.first { it is AuthenticationState.SignedIn } as AuthenticationState.SignedIn
        assertEquals("cat-one", first.account.petId)
        assertNotNull(store.session)
        firstClient.close()
        val secondClient = client()
        try {
            val second = SupabaseAuthenticationRepository(secondClient, backgroundScope)
            val restored = second.state.first { it is AuthenticationState.SignedIn } as AuthenticationState.SignedIn
            assertEquals(first.account, restored.account)
            assertEquals(2, accountReads)
            offlineLogout = true
            assertEquals(LogoutOutcome.LOCAL_ONLY, second.logout())
            assertNull(store.session)
            second.state.first { it is AuthenticationState.SignedOut }
            assertTrue(paths.none { it == "/rest/v1/profiles" || it == "/rest/v1/pets" || it == "/rest/v1/wallets" })
        } finally { secondClient.close() }
    }

    @Test fun confirmationDoesNotUnlockAppAndInvalidCredentialsAreSafe() = runTest(timeout = 10.seconds) {
        val store = Store()
        val client = createSupabaseClient("https://pawquest-test.supabase.co", "sb_publishable_test_only") {
            coroutineDispatcher = StandardTestDispatcher(testScheduler)
            httpEngine = MockEngine { request ->
                when (request.url.encodedPath) {
                    "/auth/v1/signup" -> respond(user, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType,"application/json"))
                    "/auth/v1/token" -> respond("""{"code":"invalid_credentials","error_code":"invalid_credentials","msg":"Invalid login credentials"}""",
                        HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType,"application/json"))
                    else -> error("Protected account RPC must not run before authentication")
                }
            }
            install(Auth) { sessionManager=store; codeVerifierCache=MemoryCodeVerifierCache(); autoSetupPlatform=false; alwaysAutoRefresh=false }
            install(Postgrest)
        }
        try {
            client.auth.setSessionStatus(SessionStatus.NotAuthenticated())
            val repository = SupabaseAuthenticationRepository(client, backgroundScope)
            repository.state.first { it is AuthenticationState.SignedOut }
            assertEquals(RegistrationOutcome.CONFIRM_EMAIL, repository.register("test@example.invalid", "test-password"))
            assertNull(store.session)
            assertIs<AuthenticationState.SignedOut>(repository.state.value)
            val error = assertFailsWith<AuthenticationException> { repository.login("test@example.invalid", "incorrect") }
            assertEquals(AuthFailure.INVALID_CREDENTIALS, error.failure)
            assertIs<AuthenticationState.SignedOut>(repository.state.value)
        } finally { client.close() }
    }

    @Test fun failedAccountInitializationNeverUnlocksProtectedNavigation() = runTest(timeout = 10.seconds) {
        val client = createSupabaseClient("https://pawquest-test.supabase.co", "sb_publishable_test_only") {
            coroutineDispatcher = StandardTestDispatcher(testScheduler)
            httpEngine = MockEngine { request ->
                if (request.url.encodedPath == "/auth/v1/token") {
                    respond(session, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType,"application/json"))
                } else respond("{}", HttpStatusCode.ServiceUnavailable, headersOf(HttpHeaders.ContentType,"application/json"))
            }
            install(Auth) { sessionManager=Store(); codeVerifierCache=MemoryCodeVerifierCache(); autoSetupPlatform=false; alwaysAutoRefresh=false }
            install(Postgrest)
        }
        try {
            client.auth.setSessionStatus(SessionStatus.NotAuthenticated())
            val repository = SupabaseAuthenticationRepository(client, backgroundScope)
            repository.state.first { it is AuthenticationState.SignedOut }
            repository.login("test@example.invalid", "test-password")
            assertIs<AuthenticationState.Unavailable>(repository.state.first { it is AuthenticationState.Unavailable })
        } finally { client.close() }
    }
}
