package com.pawquest

import com.pawquest.data.repository.*
import com.pawquest.domain.model.*
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.*
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.logging.LogLevel
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Instant

@OptIn(SupabaseInternal::class)
class InventoryRepositoryTest {
    @Test fun roomRpcPersistsReplacesAndClearsFixedSlots()=runTest {
        var saved="[]"
        val client=client(MockEngine { request ->
            val result=when(request.url.encodedPath) {
                "/auth/v1/token" -> session
                "/rest/v1/rpc/room_state" -> saved
                "/rest/v1/rpc/room_set" -> {
                    val intent=Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                    assertEquals(setOf("p_slot","p_item_id"),intent.keys)
                    assertEquals("BED",intent.getValue("p_slot").jsonPrimitive.content)
                    saved=if(intent["p_item_id"]==JsonNull) "[]" else """[{"slot":"BED","item_id":"bed","asset_id":"item.furniture.bed","placed_at":"2026-11-05T00:00:00Z"}]"""
                    saved
                }
                else -> error("Unexpected request")
            }
            respond(result,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try {
            client.auth.signInWith(Email) { email="test@example.invalid"; password="test-password" }
            assertTrue(SupabaseRoomRepository(client).getRoom().isEmpty())
            SupabaseRoomRepository(client).setFurniture(FurnitureSlot.BED,"bed")
            assertEquals("bed",SupabaseRoomRepository(client).getRoom().single().itemId)
            assertTrue(SupabaseRoomRepository(client).setFurniture(FurnitureSlot.BED,null).isEmpty())
        } finally { client.close() }
    }
    @Test fun equipmentRpcUsesSlotAndOwnedItemAndRestoresAcrossRepositoryInstances()=runTest {
        var saved="[]"
        val client=client(MockEngine { request ->
            val path=request.url.encodedPath
            val result=when(path) {
                "/auth/v1/token" -> session
                "/rest/v1/rpc/equipment_state" -> saved
                "/rest/v1/rpc/equipment_set" -> {
                    val intent=Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                    assertEquals(setOf("p_slot","p_item_id"),intent.keys)
                    assertEquals("HEAD",intent.getValue("p_slot").jsonPrimitive.content)
                    saved=if(intent["p_item_id"]==JsonNull) "[]" else """[{"slot":"HEAD","item_id":"hat","asset_id":"item.accessory.hat","equipped_at":"2026-11-05T00:00:00Z"}]"""
                    saved
                }
                else -> error("Unexpected request")
            }
            respond(result,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try {
            client.auth.signInWith(Email) { email="test@example.invalid"; password="test-password" }
            assertTrue(SupabaseEquipmentRepository(client).getEquipment().isEmpty())
            assertEquals("hat",SupabaseEquipmentRepository(client).setEquipment(EquipmentSlot.HEAD,"hat").single().itemId)
            assertEquals(EquipmentSlot.HEAD,SupabaseEquipmentRepository(client).getEquipment().single().slot)
            assertTrue(SupabaseEquipmentRepository(client).setEquipment(EquipmentSlot.HEAD,null).isEmpty())
        } finally { client.close() }
    }
    private val owner="10000000-0000-0000-0000-000000000001"
    private val food="""{"id":"food","code":"basic_food","name":"Cat food","category":"FOOD","price_coins":10,"asset_id":"item.food.basic","max_quantity":1000000,"active":true}"""
    private val session get()="""{"access_token":"test.access.signature","refresh_token":"test-only","expires_in":3600,"token_type":"bearer","user":{"id":"$owner","aud":"authenticated","role":"authenticated"}}"""
    private fun owned(user: String=owner,quantity: Int=3)="""[{"id":"owned-food","user_id":"$user","quantity":$quantity,"created_at":"2026-11-05T00:00:00Z","item":$food}]"""
    @Test fun purchaseSendsOnlyIntentAndDecodesAuthoritativeReceiptAndRejection()=runTest {
        val item="e1000000-0000-0000-0000-000000000001"
        val key="f2000000-0000-0000-0000-000000000001"
        var status="purchased"
        val client=client(MockEngine { request ->
            val body=if(request.url.encodedPath=="/auth/v1/token") session else {
                assertEquals("/rest/v1/rpc/shop_purchase",request.url.encodedPath)
                val intent=Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                assertEquals(setOf("p_item_id","p_quantity","p_request_id"),intent.keys)
                assertEquals(key,intent.getValue("p_request_id").jsonPrimitive.content)
                val receipt=if(status=="purchased") """{"id":"receipt","kind":"shop_purchase","user_id":"$owner","request_id":"$key","item_id":"$item","quantity":2,"unit_price":10,"coins":-20,"balance_after":80,"created_at":"2026-11-05T00:00:00Z"}""" else "null"
                """{"status":"$status","receipt":$receipt}"""
            }
            respond(body,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try {
            client.auth.signInWith(Email) { email="test@example.invalid"; password="test-password" }
            val repo=SupabaseItemRepository(client)
            val intent=PurchaseRequest(item,2,key)
            val first=repo.purchase(intent)
            assertEquals(20L,first.receipt!!.coinsSpent)
            assertEquals(80L,first.receipt.balanceAfter)
            assertEquals(first,repo.purchase(intent))
            status="insufficient_balance"
            assertEquals(PurchaseStatus.INSUFFICIENT_BALANCE,repo.purchase(intent).status)
            status="already_owned"
            assertNull(repo.purchase(intent).receipt)
            assertFailsWith<IllegalArgumentException> { repo.purchase(intent.copy(quantity=0)) }
        } finally { client.close() }
    }
    private fun TestScope.client(engine: MockEngine)=createSupabaseClient("https://inventory-test.supabase.co","sb_publishable_test_only") {
        defaultLogLevel=LogLevel.NONE
        coroutineDispatcher=StandardTestDispatcher(testScheduler)
        httpEngine=engine
        install(Auth) {
            autoSetupPlatform=false; autoLoadFromStorage=false; autoSaveToStorage=false
            alwaysAutoRefresh=false; codeVerifierCache=MemoryCodeVerifierCache()
            sessionManager=object : SessionManager {
                override suspend fun saveSession(session: io.github.jan.supabase.auth.user.UserSession) = Unit
                override suspend fun loadSession(): io.github.jan.supabase.auth.user.UserSession =
                    throw io.github.jan.supabase.auth.exception.NoSessionFoundException()
                override suspend fun deleteSession() = Unit
            }
        }
        install(Postgrest)
    }

    @Test fun catalogAndOwnedInventoryLoadThroughAuthenticatedRpc()=runTest {
        var inventory=owned()
        val paths=mutableListOf<String>()
        val client=client(MockEngine { request ->
            val path=request.url.encodedPath
            paths+=path
            val body=when(path) {
                "/auth/v1/token" -> session
                "/rest/v1/rpc/shop_catalog" -> {
                    assertEquals("Bearer test.access.signature",request.headers[HttpHeaders.Authorization]); "[$food]"
                }
                "/rest/v1/rpc/inventory_items" -> inventory
                else -> error("Unexpected request")
            }
            respond(body,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try {
            client.auth.signInWith(Email) { email="test@example.invalid"; password="test-password" }
            val item=SupabaseItemRepository(client).getItems().single()
            assertEquals(ItemCategory.FOOD,item.category)
            assertEquals(10,item.priceCoins)
            assertTrue(item.stackable)
            assertEquals(3,SupabaseInventoryRepository(client).getInventory().single().quantity)
            inventory=owned(quantity=2)
            assertEquals(2,SupabaseInventoryRepository(client).getInventory().single().quantity)
            inventory="[]"
            assertTrue(SupabaseInventoryRepository(client).getInventory().isEmpty())
            inventory=owned(user="another-owner")
            assertFailsWith<IllegalStateException> { SupabaseInventoryRepository(client).getInventory() }
            inventory=owned(quantity=-1)
            assertFailsWith<IllegalStateException> { SupabaseInventoryRepository(client).getInventory() }
            assertTrue(paths.all { it=="/auth/v1/token" || it.startsWith("/rest/v1/rpc/") })
        } finally { client.close() }
    }

    @Test fun signedOutDoesNotReadProtectedInventoryOrCatalog()=runTest {
        var calls=0
        val client=client(MockEngine { calls++; error("No HTTP expected") })
        try {
            client.auth.setSessionStatus(SessionStatus.NotAuthenticated())
            assertFailsWith<IllegalStateException> { SupabaseItemRepository(client).getItems() }
            assertFailsWith<IllegalStateException> { SupabaseInventoryRepository(client).getInventory() }
            assertEquals(0,calls)
        } finally { client.close() }
    }

    @Test fun inventoryBoundsAndUniqueOwnershipAreValidated() {
        val hat=CatalogItem("hat","hat","Hat",ItemCategory.ACCESSORY,60,"item.accessory.hat",1,true)
        val stamp=Instant.parse("2026-11-05T00:00:00Z")
        assertFalse(hat.stackable)
        assertFailsWith<IllegalArgumentException> { InventoryItem("a",owner,hat,2,stamp) }
        assertFailsWith<IllegalArgumentException> { InventoryItem("a",owner,hat,0,stamp) }
        assertFailsWith<IllegalArgumentException> { InventoryItem("a",owner,hat,-1,stamp) }
        assertFailsWith<IllegalArgumentException> { hat.copy(maxQuantity=1000000) }
        assertFailsWith<IllegalArgumentException> { hat.copy(priceCoins=-1) }
        assertEquals(1,InventoryItem("a",owner,hat,1,stamp).quantity)
    }

    @Test fun cancellationIsNotConvertedIntoConnectionError()=runTest {
        var cancel=false
        val client=client(MockEngine { request ->
            if(cancel) throw CancellationException("Cancelled test request")
            respond(session,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try {
            client.auth.signInWith(Email) { email="test@example.invalid"; password="test-password" }
            cancel=true
            assertFailsWith<CancellationException> { SupabaseInventoryRepository(client).getInventory() }
        } finally { client.close() }
    }
}
