package com.pawquest

import com.pawquest.data.repository.SupabaseQuestRepository
import com.pawquest.data.repository.SupabaseQuestSessionRepository
import com.pawquest.data.repository.SupabaseFocusRepository
import com.pawquest.data.repository.SupabasePetRepository
import com.pawquest.domain.model.*
import com.pawquest.domain.usecase.*
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.*
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.logging.LogLevel
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(SupabaseInternal::class)
class SupabaseQuestRepositoryTest {
    private val uid="10000000-0000-0000-0000-000000000001"
    private val id="20000000-0000-0000-0000-000000000001"
    private val session get()="""{"access_token":"test.access.signature","refresh_token":"test-only","expires_in":3600,"token_type":"bearer","user":{"id":"$uid","email":"quest@example.invalid","aud":"authenticated","role":"authenticated"}}"""
    private fun TestScope.client(engine: MockEngine) = createSupabaseClient("https://quest-test.supabase.co","sb_publishable_test_only") {
        defaultLogLevel=LogLevel.NONE
        coroutineDispatcher=StandardTestDispatcher(testScheduler)
        httpEngine=engine
        install(Auth) {
            autoSetupPlatform=false
            autoLoadFromStorage=false
            autoSaveToStorage=false
            alwaysAutoRefresh=false
            codeVerifierCache=MemoryCodeVerifierCache()
            sessionManager=object : SessionManager {
                override suspend fun saveSession(session: UserSession) = Unit
                override suspend fun loadSession(): UserSession = throw NoSessionFoundException()
                override suspend fun deleteSession() = Unit
            }
        }
        install(Postgrest)
    }

    @Test fun realSdkCrudUsesOnlyDefinitionFieldsAndReadsBackAcrossRepositoryInstances() = runTest(timeout=10.seconds) {
        var stored: JsonObject?=null
        val paths=mutableListOf<String>()
        val client=client(MockEngine { request ->
            paths+=request.url.encodedPath
            if (request.url.encodedPath=="/auth/v1/token") {
                respond(session,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
            } else {
                assertEquals("/rest/v1/quests",request.url.encodedPath)
                assertEquals("Bearer test.access.signature",request.headers[HttpHeaders.Authorization])
                val parameters=request.url.parameters
                val response=when(request.method) {
                    HttpMethod.Post -> {
                        val encoded=Json.parseToJsonElement(request.body.toByteArray().decodeToString())
                        val body=(if(encoded is JsonArray) encoded.single() else encoded).jsonObject
                        assertEquals(setOf("title","description","category","duration_minutes","repeat_type","due_at"),body.keys)
                        assertEquals("one_time",body.getValue("repeat_type").jsonPrimitive.content)
                        stored=JsonObject(body + mapOf("id" to JsonPrimitive(id),"user_id" to JsonPrimitive(uid),
                            "created_at" to JsonPrimitive("2026-11-05T00:00:00Z"),"archived" to JsonPrimitive(false)))
                        JsonArray(listOfNotNull(stored)).toString()
                    }
                    HttpMethod.Patch -> {
                        assertEquals("eq.$id",parameters["id"])
                        assertEquals("eq.$uid",parameters["user_id"])
                        val body=Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                        if ("archived" in body) assertEquals(setOf("archived"),body.keys)
                        else { assertEquals("eq.false",parameters["archived"]); assertEquals(6,body.size) }
                        stored=JsonObject(stored!!+body)
                        JsonArray(listOfNotNull(stored)).toString()
                    }
                    HttpMethod.Get -> {
                        assertEquals("eq.$uid",parameters["user_id"])
                        assertEquals("eq.false",parameters["archived"])
                        val rows=stored?.takeUnless { it["archived"]?.jsonPrimitive?.boolean==true }
                        JsonArray(listOfNotNull(rows)).toString()
                    }
                    else -> error("Quest definitions must not issue DELETE or call reward/session RPCs")
                }
                respond(response,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
            }
        })
        try {
            client.auth.setSessionStatus(SessionStatus.NotAuthenticated())
            client.auth.signInWith(Email) { email="quest@example.invalid"; password="test-only" }
            val repository=SupabaseQuestRepository(client)
            val created=CreateQuest(repository)(QuestDraft("  Read  ",durationMinutes=25,dueAt=kotlin.time.Instant.parse("2026-11-07T12:00:00Z")))
            assertEquals(kotlin.time.Instant.parse("2026-11-07T12:00:00Z"),created.dueAt)
            assertEquals("Read",created.title)
            assertEquals(uid,created.userId)
            val recreated=SupabaseQuestRepository(client)
            assertEquals(listOf(created),GetQuests(recreated)())
            val updated=UpdateQuest(recreated)(id,QuestDraft("Walk","Outside","Health",90,QuestRepeatType.DAILY))
            assertNull(updated.dueAt)
            assertEquals(JsonNull,stored?.get("due_at"))
            assertEquals(QuestRepeatType.DAILY,updated.repeatType)
            assertEquals(created.createdAt,updated.createdAt)
            assertEquals(listOf(updated),GetQuests(repository)())
            DeleteQuest(repository)(id)
            DeleteQuest(repository)(id) // Repeated delete is safe.
            assertTrue(GetQuests(recreated)().isEmpty())
            assertTrue(paths.all { it in listOf("/auth/v1/token","/rest/v1/quests") })
        } finally { client.close() }
    }

    @Test fun signedOutAndInvalidDefinitionsNeverIssueQuestRequests() = runTest(timeout=10.seconds) {
        var requests=0
        val client=client(MockEngine { requests++; error("No HTTP expected") })
        try {
            client.auth.setSessionStatus(SessionStatus.NotAuthenticated())
            val repo=SupabaseQuestRepository(client)
            assertEquals(QuestFailure.AUTHENTICATION_REQUIRED,assertFailsWith<QuestException> { repo.getQuests() }.failure)
            assertFailsWith<IllegalArgumentException> { repo.create(QuestDraft("Invalid",durationMinutes=4)) }
            assertFailsWith<IllegalArgumentException> { repo.update(id,QuestDraft("Invalid",durationMinutes=91)) }
            assertFailsWith<IllegalArgumentException> { repo.delete("invalid") }
            assertEquals(0,requests)
        } finally { client.close() }
    }

    @Test fun sessionUiBridgeUsesExistingStartRpcAndReadOnlyDailyProgress() = runTest(timeout=10.seconds) {
        val paths=mutableListOf<String>()
        val client=client(MockEngine { request ->
            paths+=request.url.encodedPath
            val response=when(request.url.encodedPath) {
                "/auth/v1/token" -> session
                "/rest/v1/rpc/quest_daily_reward_count" -> "3"
                "/rest/v1/rpc/start_quest_session" -> {
                    assertEquals(buildJsonObject { put("p_quest_id",id) },
                        Json.parseToJsonElement(request.body.toByteArray().decodeToString()))
                    """{"id":"session-id","quest_id":"$id","ends_at":"2026-11-05T01:00:00Z"}"""
                }
                else -> error("No wallet or reward mutation expected")
            }
            respond(response,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try {
            client.auth.setSessionStatus(SessionStatus.NotAuthenticated())
            client.auth.signInWith(Email) { email="quest@example.invalid"; password="test-only" }
            val repo=SupabaseQuestSessionRepository(client)
            assertEquals(3,repo.dailyRewardCount())
            assertEquals(id,repo.start(id).questId)
            assertEquals(3,paths.size)
        } finally { client.close() }
    }

    @Test fun focusSdkMapsServerSnapshotsAndSendsNoClientTimestampsOrRewards() = runTest(timeout=10.seconds) {
        var status="active"
        var stamp="2026-11-05T00:00:00Z"
        val client=client(MockEngine { request ->
            val path=request.url.encodedPath
            if(path=="/auth/v1/token") respond(session,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
            else {
                val body=Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                when(path) {
                    "/rest/v1/rpc/focus_start" -> assertEquals(setOf("p_quest_id","p_request_id"),body.keys)
                    "/rest/v1/rpc/reward_reconcile" -> { assertTrue(body.isEmpty()); stamp="2026-11-05T00:06:00Z"; status="completed" }
                    "/rest/v1/rpc/focus_cancel" -> { assertEquals(setOf("p_session_id"),body.keys); status="cancelled" }
                    "/rest/v1/rpc/reward_claim" -> { assertEquals(setOf("p_session_id"),body.keys); status="claimed" }
                    else -> error("No direct wallet/session table writes")
                }
                val reward=if(status=="claimed") """{"coins":10,"pet_xp":5,"balance_after":10}""" else "null"
                val response="""{"server_now":"$stamp","eligible":${status=="completed"},"reward":$reward,"reward_offer":{"tier_coins":10,"coins":10,"pet_xp":5,"reward_day":"2026-11-05","rewarded_sessions":0,"limit_reached":false,"can_claim":${status=="completed"}},"session":{"id":"$id","quest_id":"$id","title_snapshot":"Focus","duration_minutes":5,"started_at":"2026-11-05T00:00:00Z","ends_at":"2026-11-05T00:05:00Z","status":"$status"}}"""
                respond(response,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
            }
        })
        try {
            client.auth.setSessionStatus(SessionStatus.NotAuthenticated())
            client.auth.signInWith(Email) { email="quest@example.invalid"; password="test-only" }
            val repo=SupabaseFocusRepository(client)
            assertFalse(repo.start(id,uid).eligible)
            val quoted=repo.reconcile()
            assertTrue(quoted.eligible)
            assertEquals(RewardOffer(10,10,5,"2026-11-05",0,false,true),quoted.offer)
            assertEquals(ClaimedReward(10,5,10),repo.claim(id).reward)
            assertEquals(FocusStatus.CANCELLED,repo.cancel(id).session?.status)
        } finally { client.close() }
    }

    @Test fun getQuestsReadsBeyondFirstPage() = runTest(timeout=10.seconds) {
        val offsets=mutableListOf<String?>()
        val client=client(MockEngine { request ->
            val response=if(request.url.encodedPath=="/auth/v1/token") session else {
                offsets+=request.url.parameters["offset"]
                assertEquals("200",request.url.parameters["limit"])
                val indexes=if(offsets.size==1) 0..199 else 200..200
                indexes.joinToString(prefix="[",postfix="]") { index ->
                    """{"id":"20000000-0000-0000-0000-${index.toString().padStart(12,'0')}","user_id":"$uid","title":"Read","description":"","category":"Focus","duration_minutes":5,"repeat_type":"daily","created_at":"2026-11-05T00:00:00Z"}"""
                }
            }
            respond(response,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try {
            client.auth.setSessionStatus(SessionStatus.NotAuthenticated())
            client.auth.signInWith(Email) { email="quest@example.invalid"; password="test-only" }
            val quests=SupabaseQuestRepository(client).getQuests()
            assertEquals(201,quests.size)
            assertEquals(201,quests.map { it.id }.toSet().size)
            assertEquals(listOf("0","200"),offsets.filterNotNull())
        } finally { client.close() }
    }

    @Test fun petSdkPersistsCareAndRestoresServerXpAcrossRepositoryInstances() = runTest(timeout=10.seconds) {
        var hunger=20
        var xp=99
        val client=client(MockEngine { request ->
            if(request.url.encodedPath=="/auth/v1/token") respond(session,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
            else {
                assertEquals("/rest/v1/rpc/pet_state",request.url.encodedPath)
                val body=Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                if(body.isNotEmpty()) {
                    assertEquals(setOf("p_action","p_request_id"),body.keys)
                    assertEquals("feed",body.getValue("p_action").jsonPrimitive.content)
                    hunger=35
                }
                val result=if(body.isEmpty()) "null" else """{"request_id":"$id","action":"feed","code":"fed","applied":true,"food_consumed":1}"""
                val sleep=if(xp==100) "\"2026-11-05T01:00:00Z\"" else "null"
                respond("""{"pet":{"id":"$id","user_id":"$uid","name":"Mochi","hunger":$hunger,"happiness":90,"energy":80,"xp":$xp,"sleep_until":$sleep,"stats_updated_at":"2026-11-05T00:00:00Z"},"coins":10,"server_now":"2026-11-05T00:01:00Z","food_quantity":2,"care_result":$result}""",
                    HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
            }
        })
        try {
            client.auth.setSessionStatus(SessionStatus.NotAuthenticated())
            client.auth.signInWith(Email) { email="pet@example.invalid"; password="test-only" }
            val repo=SupabasePetRepository(client)
            assertEquals(1,repo.restore().pet.progress.level)
            val saved=repo.care(PetCareAction.FEED,id)
            assertEquals(35,saved.pet.stats.hunger)
            assertEquals(2,saved.foodQuantity)
            assertEquals(1,saved.actionResult?.foodConsumed)
            assertEquals(saved.copy(actionResult=null),SupabasePetRepository(client).restore())
            xp=100
            val restored=SupabasePetRepository(client).restore()
            assertEquals(2,restored.pet.progress.level)
            assertNotNull(restored.pet.sleepUntil)
        } finally { client.close() }
    }

    @Test fun missingRowsNetworkErrorsAndCancellationAreHandled() = runTest(timeout=10.seconds) {
        var mode="empty"
        val client=client(MockEngine { request ->
            when {
                request.url.encodedPath=="/auth/v1/token" -> respond(session,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
                mode=="network" -> throw IllegalStateException("test network")
                mode=="cancel" -> throw CancellationException("cancelled")
                mode=="service" -> respond("""{"code":"42501","message":"denied","details":null,"hint":null}""",HttpStatusCode.Forbidden,headersOf(HttpHeaders.ContentType,"application/json"))
                else -> respond("[]",HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
            }
        })
        try {
            client.auth.setSessionStatus(SessionStatus.NotAuthenticated())
            client.auth.signInWith(Email) { email="quest@example.invalid"; password="test-only" }
            val repo=SupabaseQuestRepository(client)
            assertEquals(QuestFailure.NOT_FOUND,assertFailsWith<QuestException> { repo.update(id,QuestDraft("Read",durationMinutes=5)) }.failure)
            assertEquals(QuestFailure.NOT_FOUND,assertFailsWith<QuestException> { repo.delete(id) }.failure)
            mode="network"
            assertEquals(QuestFailure.NETWORK,assertFailsWith<QuestException> { repo.getQuests() }.failure)
            mode="service"
            assertEquals(QuestFailure.SERVICE,assertFailsWith<QuestException> { repo.getQuests() }.failure)
            mode="cancel"
            assertFailsWith<CancellationException> { repo.getQuests() }
        } finally { client.close() }
    }
}
