package com.pawquest

import com.pawquest.data.offline.ConnectionStatus
import com.pawquest.data.offline.MemoryOfflineCache
import com.pawquest.data.repository.SupabaseQuestRepository
import com.pawquest.domain.model.QuestException
import com.pawquest.domain.model.QuestFailure
import io.github.jan.supabase.auth.*
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.logging.LogLevel
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(io.github.jan.supabase.annotations.SupabaseInternal::class)
class OfflineCacheTest {
    private val uid = "10000000-0000-0000-0000-000000000001"
    private val session = """{"access_token":"test.access.signature","refresh_token":"test-only","expires_in":3600,"token_type":"bearer","user":{"id":"$uid","email":"quest@example.invalid","aud":"authenticated","role":"authenticated"}}"""
    private val quests = """[{"id":"20000000-0000-0000-0000-000000000001","user_id":"$uid","title":"Read","description":"",
        "category":"Focus","duration_minutes":25,"repeat_type":"daily","due_at":null,"created_at":"2026-11-05T00:00:00Z"}]"""

    private enum class Mode { ONLINE, OFFLINE, SERVER_ERROR }

    private fun TestScope.client(mode: () -> Mode) = createSupabaseClient("https://offline-test.supabase.co", "sb_publishable_test_only") {
        defaultLogLevel = LogLevel.NONE
        coroutineDispatcher = StandardTestDispatcher(testScheduler)
        httpEngine = MockEngine { request ->
            if (request.url.encodedPath == "/auth/v1/token") respond(session, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            else when (mode()) {
                Mode.ONLINE -> respond(quests, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                Mode.OFFLINE -> throw kotlinx.io.IOException("Unable to resolve host")
                Mode.SERVER_ERROR -> respond("""{"message":"boom"}""", HttpStatusCode.InternalServerError, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        install(Auth) {
            autoSetupPlatform = false; autoLoadFromStorage = false; autoSaveToStorage = false; alwaysAutoRefresh = false
            codeVerifierCache = MemoryCodeVerifierCache()
            sessionManager = object : SessionManager {
                override suspend fun saveSession(session: UserSession) = Unit
                override suspend fun loadSession(): UserSession = throw NoSessionFoundException()
                override suspend fun deleteSession() = Unit
            }
        }
        install(Postgrest)
    }

    @Test fun networkLossShowsLastConfirmedQuestsButServerErrorsStillFail() = runTest(timeout = 10.seconds) {
        ConnectionStatus.online()
        var mode = Mode.ONLINE
        val client = client { mode }
        client.auth.signInWith(Email) { email = "quest@example.invalid"; password = "test-only" }
        val cache = MemoryOfflineCache()
        val repository = SupabaseQuestRepository(client, cache)

        assertFailsWith<QuestException> { SupabaseQuestRepository(client, MemoryOfflineCache()).also { mode = Mode.OFFLINE }.getQuests() }
            .also { assertEquals(QuestFailure.NETWORK, it.failure, "no cache: offline is still an error") }
        ConnectionStatus.online()

        mode = Mode.ONLINE
        assertEquals(listOf("Read"), repository.getQuests().map { it.title })
        assertNull(ConnectionStatus.offlineDataFrom)

        mode = Mode.OFFLINE
        assertEquals(listOf("Read"), repository.getQuests().map { it.title })
        assertNotNull(ConnectionStatus.offlineDataFrom, "banner shows cached data")

        mode = Mode.ONLINE
        repository.getQuests()
        assertNull(ConnectionStatus.offlineDataFrom, "a confirmed response clears the banner")

        mode = Mode.SERVER_ERROR
        assertEquals(QuestFailure.SERVICE, assertFailsWith<QuestException> { repository.getQuests() }.failure)
        ConnectionStatus.online()
    }

    @Test fun clearingTheCacheRemovesEverything() {
        val cache = MemoryOfflineCache()
        cache.write("quests", "[]"); cache.clear()
        assertNull(cache.read("quests"))
    }
}
