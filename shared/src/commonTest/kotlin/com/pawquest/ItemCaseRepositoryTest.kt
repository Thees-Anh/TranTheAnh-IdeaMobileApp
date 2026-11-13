package com.pawquest

import com.pawquest.data.repository.*
import com.pawquest.domain.model.*
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.*
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.logging.LogLevel
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(SupabaseInternal::class)
class ItemCaseRepositoryTest {
    @Test fun exchangeUsesTwoInputsCapturedSessionAndRejectsWrongReceiptIdentity()=runTest {
        var invalid=false
        val sdk=client(MockEngine { request ->
            val body=if(request.url.encodedPath=="/auth/v1/token") session else {
                assertEquals(listOf("Bearer test.access.signature"),request.headers.getAll(HttpHeaders.Authorization))
                val fields=Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                assertEquals(setOf("p_item_id","p_request_id"),fields.keys)
                if(request.url.encodedPath.endsWith("item_shard_quote")) """{"status":"quoted","quote":{"item_id":"$item","request_id":"$key","price_item_shards":60,"expires_at":"2026-11-10T05:10:00Z"}}"""
                else """{"status":"redeemed","receipt":{"transaction_id":"$key","item_id":"${if(invalid) banner else item}","request_id":"$key","item_name":"Glasses","item_code":"acc_glasses","asset_id":"acc-glasses","category":"ACCESSORY","rarity":"RARE","shards_spent":60,"quantity_granted":1,"item_shards_balance_after":15,"created_at":"2026-11-10T05:00:00Z"}}"""
            }
            respond(body,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try {
            sdk.auth.signInWith(Email) {email="test@example.invalid";password="test-only"}
            val repo=SupabaseShardRepository(sdk);val pending=PendingItemExchange(owner,item,key)
            assertEquals(60,(repo.quoteItem(pending) as ItemExchangeResponse.Quoted).quote.price)
            assertEquals(15L,(repo.redeemItem(pending.copy(confirmed=true)) as ItemExchangeResponse.Redeemed).receipt.balanceAfter)
            invalid=true;assertFailsWith<IllegalStateException> {repo.redeemItem(pending.copy(confirmed=true))}
        } finally {sdk.close()}
    }
    @Test fun shardLedgerIsBoundedReadOnlyOwnerAndItemCurrencyFiltered()=runTest {
        var foreign=false;var pet=false
        val client=client(MockEngine { request ->
            val body=if(request.url.encodedPath=="/auth/v1/token") session else {
                assertEquals(HttpMethod.Get,request.method)
                assertEquals("/rest/v1/shard_ledger",request.url.encodedPath)
                assertEquals("eq.$owner",request.url.parameters["user_id"])
                assertEquals("eq.ITEM_SHARD",request.url.parameters["currency"])
                assertEquals("20",request.url.parameters["limit"])
                """[{"id":"$key","user_id":"${if(foreign) "other" else owner}","currency":"${if(pet) "PET_SHARD" else "ITEM_SHARD"}","request_id":"$key","reason":"ITEM_DUPLICATE","delta":10,"balance_after":85,"opening_id":"$key","redemption_id":null,"note":null,"created_at":"2026-11-10T05:00:00Z"}]"""
            }
            respond(body,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try {
            client.auth.signInWith(Email) {email="test@example.invalid";password="test-only"}
            val repo=SupabaseShardRepository(client)
            assertEquals(85L,repo.getItemLedger().single().balanceAfter)
            foreign=true;assertFailsWith<IllegalStateException> {repo.getItemLedger()}
            foreign=false;pet=true;assertFailsWith<IllegalStateException> {repo.getItemLedger()}
            client.auth.setSessionStatus(SessionStatus.NotAuthenticated())
            assertFailsWith<IllegalStateException> {repo.getItemLedger()}
        } finally {client.close()}
    }
    @Test fun historyReadIsOwnerFilteredAndUsesRealOpeningRows()=runTest {
        var foreign=false
        val client=client(MockEngine { request ->
            val body=if(request.url.encodedPath=="/auth/v1/token") session else {
                assertEquals(HttpMethod.Get,request.method)
                assertEquals("/rest/v1/item_case_openings",request.url.encodedPath)
                assertEquals("eq.$owner",request.url.parameters["user_id"])
                assertEquals("20",request.url.parameters["limit"])
                assertTrue(request.url.parameters["select"]!!.contains("item_case_pool!inner"))
                """[{"id":"$key","user_id":"${if(foreign) "fb260000-0000-0000-0000-000000000002" else owner}","item_id":"$item","coins_spent":100,"item_shards_granted":10,"created_at":"2026-11-10T05:00:00Z","outcome":"ITEM_SHARDS","item_case_pool":{"item_code":"acc_glasses","asset_id":"acc-glasses","category":"ACCESSORY","rarity":"RARE"}}]"""
            }
            respond(body,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try {
            client.auth.signInWith(Email) { email="test@example.invalid"; password="test-only" }
            val repo=SupabaseItemCaseRepository(client)
            val history=repo.getHistory().single()
            assertEquals(10,history.shardsGranted)
            assertEquals(ItemRarity.RARE,history.rarity)
            assertEquals(ItemCaseRewardOutcome.DUPLICATE_CONVERTED,history.outcome)
            assertEquals("acc-glasses",history.assetId)
            foreign=true; assertFailsWith<IllegalStateException> { repo.getHistory() }
        } finally { client.close() }
    }
    private val owner="fb260000-0000-0000-0000-000000000001"
    private val banner=ItemCaseIds.DEFAULT_BANNER
    private val version="e2400000-0000-0000-0000-000000000002"
    private val item="e2300000-0000-0000-0000-000000000005"
    private val key="fb270000-0000-0000-0000-000000000001"
    private val session get()="""{"access_token":"test.access.signature","refresh_token":"test-only","expires_in":3600,"token_type":"bearer","user":{"id":"$owner","aud":"authenticated","role":"authenticated"}}"""
    private fun response(duplicate: Boolean=false,request: String=key)="""{"status":"opened","receipt":{
      "transaction_id":"fb280000-0000-0000-0000-000000000001","request_id":"$request","banner_id":"$banner",
      "configuration_version_id":"$version","configuration_version":1,"winning_item_id":"$item",
      "item_name":"Rare glasses","item_code":"acc_glasses","asset_id":"acc-glasses","rarity":"RARE",
      "opening_cost_coins":100,"reward_outcome":"${if(duplicate) "DUPLICATE_CONVERTED" else "ITEM_GRANTED"}",
      "quantity_granted":${if(duplicate) 0 else 1},"item_shards_granted":${if(duplicate) 10 else 0},
      "coins_balance_after":900,"item_shards_balance_after":${if(duplicate) 10 else 0},"created_at":"2026-11-10T05:00:00.000000Z"}}"""
    private fun TestScope.client(engine: MockEngine)=createSupabaseClient("https://case-test.supabase.co","sb_publishable_test_only") {
        defaultLogLevel=LogLevel.NONE; coroutineDispatcher=StandardTestDispatcher(testScheduler); httpEngine=engine
        install(Auth) {
            autoSetupPlatform=false; autoLoadFromStorage=false; autoSaveToStorage=false; alwaysAutoRefresh=false
            codeVerifierCache=MemoryCodeVerifierCache()
            sessionManager=object : SessionManager {
                override suspend fun saveSession(session: UserSession)=Unit
                override suspend fun loadSession(): UserSession=throw NoSessionFoundException()
                override suspend fun deleteSession()=Unit
            }
        }
        install(Postgrest)
    }

    @Test fun authenticatedOpeningSendsOnlyTwoFieldsAndPreservesBothRewardTypes()=runTest {
        var duplicate=false
        val client=client(MockEngine { request ->
            val body=if(request.url.encodedPath=="/auth/v1/token") session else {
                assertEquals("/rest/v1/rpc/item_case_open",request.url.encodedPath)
                assertEquals(HttpMethod.Post,request.method)
                assertEquals("Bearer test.access.signature",request.headers[HttpHeaders.Authorization])
                assertEquals(1,request.headers.getAll(HttpHeaders.Authorization)?.size)
                val fields=Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                assertEquals(setOf("p_banner_id","p_request_id"),fields.keys)
                assertEquals(banner,fields.getValue("p_banner_id").jsonPrimitive.content)
                assertEquals(key,fields.getValue("p_request_id").jsonPrimitive.content)
                response(duplicate)
            }
            respond(body,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try {
            client.auth.signInWith(Email) { email="test@example.invalid"; password="test-only" }
            val repo=SupabaseItemCaseRepository(client)
            val request=PendingItemCase(owner,banner,key)
            val acquired=(repo.open(request) as ItemCaseResponse.Opened).receipt
            assertEquals(ItemCaseRewardOutcome.ITEM_GRANTED,acquired.outcome)
            assertEquals(900L,acquired.coinsBalanceAfter)
            duplicate=true
            val converted=(repo.open(request) as ItemCaseResponse.Opened).receipt
            assertEquals(item,converted.winningItemId)
            assertEquals(ItemCaseRewardOutcome.DUPLICATE_CONVERTED,converted.outcome)
            assertEquals(10,converted.itemShardsGranted)
            assertEquals(10L,converted.itemShardsBalanceAfter)
        } finally { client.close() }
    }
    @Test fun rejectedAndMalformedResponsesNeverBecomeRewards()=runTest {
        var body="""{"status":"busy","receipt":null,"retry_after_seconds":1}"""
        val client=client(MockEngine { request -> respond(if(request.url.encodedPath=="/auth/v1/token") session else body,
            HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json")) })
        try {
            client.auth.signInWith(Email) { email="test@example.invalid"; password="test-only" }
            val repo=SupabaseItemCaseRepository(client); val request=PendingItemCase(owner,banner,key)
            assertEquals(ItemCaseResponse.Rejected(ItemCaseRejection.BUSY,1),repo.open(request))
            body=response(request="fb270000-0000-0000-0000-000000000002")
            assertFailsWith<IllegalStateException> { repo.open(request) }
            body=response(true).replace("\"item_shards_granted\":10","\"item_shards_granted\":-1")
            assertFailsWith<IllegalStateException> { repo.open(request) }
            body="""{"status":"new_unknown_status","receipt":null}"""
            assertFailsWith<IllegalStateException> { repo.open(request) }
        } finally { client.close() }
    }
    @Test fun shardReadUsesOwnerFilterAndZeroOnlyForMissingRows()=runTest {
        var rows="[]"
        val client=client(MockEngine { request ->
            if(request.url.encodedPath=="/auth/v1/token") respond(session,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
            else {
                assertEquals(HttpMethod.Get,request.method)
                assertEquals("/rest/v1/shard_balances",request.url.encodedPath)
                assertEquals("eq.$owner",request.url.parameters["user_id"])
                respond(rows,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
            }
        })
        try {
            client.auth.signInWith(Email) { email="test@example.invalid"; password="test-only" }
            val repo=SupabaseShardRepository(client)
            assertEquals(ShardWallet(0,0),repo.getBalances())
            rows="""[{"user_id":"$owner","currency":"ITEM_SHARD","balance":75},{"user_id":"$owner","currency":"PET_SHARD","balance":50}]"""
            assertEquals(ShardWallet(75,50),repo.getBalances())
            rows=rows.replace(owner,"fb260000-0000-0000-0000-000000000002")
            assertFailsWith<IllegalStateException> { repo.getBalances() }
        } finally { client.close() }
    }
    @Test fun loadsExactPublishedConfigurationThroughExistingReadOnlyViews()=runTest {
        val rows=ItemRarity.entries.mapIndexed { index,rarity -> """{"version_id":"$version","item_id":"e2300000-0000-0000-0000-${(index+1).toString().padStart(12,'0')}","item_code":"item$index","asset_id":"asset$index","category":"ACCESSORY","max_quantity":1,"rarity":"${rarity.name}","weight":1,"redeemable":true,"probability_bps":${listOf(5500,3000,1200,300)[index]},"duplicate_item_shards":${listOf(5,10,20,40)[index]},"redemption_item_shards":${listOf(30,60,120,240)[index]}}""" }
        val client=client(MockEngine { request ->
            val body=when(request.url.encodedPath) {
                "/auth/v1/token" -> session
                "/rest/v1/item_case_banners" -> { assertEquals("eq.$banner",request.url.parameters["id"]); """[{"id":"$banner","code":"item_case","enabled":false,"current_version":1}]""" }
                "/rest/v1/item_case_versions" -> { assertEquals("eq.$banner",request.url.parameters["banner_id"]); """[{"id":"$version","banner_id":"$banner","version":1,"price_coins":100}]""" }
                "/rest/v1/item_case_probabilities" -> { assertEquals("eq.$version",request.url.parameters["version_id"]); rows.joinToString(",","[","]") }
                else -> error("Unexpected request")
            }
            respond(body,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try {
            client.auth.signInWith(Email) { email="test@example.invalid"; password="test-only" }
            val loaded=SupabaseItemCaseRepository(client).getConfiguration(banner)
            assertFalse(loaded.banner.enabled)
            assertEquals(100,loaded.configuration.priceCoins)
            assertEquals(4,loaded.configuration.rewards.size)
            assertEquals(10000,loaded.configuration.tiers.sumOf { it.probabilityBps })
        } finally { client.close() }
    }
    @Test fun signedOutOrWrongOwnerCannotSubmitAndCancellationPropagates()=runTest {
        var calls=0; var cancelled=false
        val client=client(MockEngine {
            calls++
            if(cancelled) throw CancellationException("cancelled request")
            respond(session,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try {
            client.auth.setSessionStatus(SessionStatus.NotAuthenticated())
            val repo=SupabaseItemCaseRepository(client)
            assertFailsWith<IllegalStateException> { repo.open(PendingItemCase(owner,banner,key)) }
            assertEquals(0,calls)
            client.auth.signInWith(Email) { email="test@example.invalid"; password="test-only" }
            assertFailsWith<IllegalStateException> { repo.open(PendingItemCase("fb260000-0000-0000-0000-000000000002",banner,key)) }
            assertEquals(1,calls)
            cancelled=true
            assertFailsWith<CancellationException> { repo.open(PendingItemCase(owner,banner,key)) }
        } finally { client.close() }
    }
    @Test fun responseAfterSessionSwitchIsRejectedAndHttpFailureCanRetrySameKey()=runTest {
        var fail=true
        var signOut=false
        val keys=mutableListOf<String>()
        lateinit var sdk: io.github.jan.supabase.SupabaseClient
        sdk=client(MockEngine { request ->
            if(request.url.encodedPath=="/auth/v1/token") respond(session,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
            else {
                assertEquals(listOf("Bearer test.access.signature"),request.headers.getAll(HttpHeaders.Authorization))
                keys+=Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject.getValue("p_request_id").jsonPrimitive.content
                if(signOut) sdk.auth.setSessionStatus(SessionStatus.NotAuthenticated())
                respond(if(fail) """{"message":"timeout"}""" else response(),
                    if(fail) HttpStatusCode.GatewayTimeout else HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
            }
        })
        try {
            sdk.auth.signInWith(Email) { email="test@example.invalid"; password="test-only" }
            val repo=SupabaseItemCaseRepository(sdk); val request=PendingItemCase(owner,banner,key)
            assertFailsWith<IllegalStateException> { repo.open(request) }
            fail=false
            assertIs<ItemCaseResponse.Opened>(repo.open(request))
            assertEquals(listOf(key,key),keys)
            signOut=true
            assertFailsWith<IllegalStateException> { repo.open(request) }
        } finally { sdk.close() }
    }
}
