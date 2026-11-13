package com.pawquest

import com.pawquest.domain.model.*
import com.pawquest.domain.repository.InventoryRepository
import com.pawquest.presentation.inventory.InventoryViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModelTest {
    private class Room : com.pawquest.domain.repository.RoomRepository {
        var items=emptyList<RoomFurniture>()
        var fail=false
        override suspend fun getRoom()=items
        override suspend fun setFurniture(slot: FurnitureSlot,itemId: String?): List<RoomFurniture> {
            if(fail) throw RoomException(true)
            items=items.filter { it.slot!=slot }+listOfNotNull(itemId?.let {
                RoomFurniture(slot,it,"item.furniture.bed",Instant.parse("2026-01-01T00:00:00Z"))
            })
            return items
        }
    }
    private class Equipment : com.pawquest.domain.repository.EquipmentRepository {
        var items=emptyList<EquippedItem>()
        var fail=false
        override suspend fun getEquipment()=items
        override suspend fun setEquipment(slot: EquipmentSlot,itemId: String?): List<EquippedItem> {
            if(fail) throw EquipmentException(true)
            items=items.filter { it.slot!=slot }+listOfNotNull(itemId?.let {
                EquippedItem(slot,it,"item.accessory.hat",Instant.parse("2026-01-01T00:00:00Z"))
            })
            return items
        }
    }

    private val testItemFood = CatalogItem(
        id = "e1000000-0000-0000-0000-000000000001",
        code = "basic_food",
        name = "Cat food",
        category = ItemCategory.FOOD,
        priceCoins = 10,
        assetId = "item.food.basic",
        maxQuantity = 1000000,
        active = true,
    )

    private val testItemHat = CatalogItem(
        id = "e1000000-0000-0000-0000-000000000002",
        code = "hat",
        name = "Fancy Hat",
        category = ItemCategory.ACCESSORY,
        priceCoins = 60,
        assetId = "item.accessory.hat",
        maxQuantity = 1,
        active = true,
    )

    private val testItemBed = CatalogItem(
        id = "e1000000-0000-0000-0000-000000000005",
        code = "bed",
        name = "Cozy Bed",
        category = ItemCategory.FURNITURE,
        priceCoins = 120,
        assetId = "item.furniture.bed",
        maxQuantity = 1,
        active = true,
    )

    private val sampleInventory = listOf(
        InventoryItem("inv-1", "user-1", testItemFood, 5, Instant.parse("2026-01-01T00:00:00Z")),
        InventoryItem("inv-2", "user-1", testItemHat, 1, Instant.parse("2026-01-01T00:00:00Z")),
        InventoryItem("inv-3", "user-1", testItemBed, 1, Instant.parse("2026-01-01T00:00:00Z")),
    )

    @Test
    fun loadsInventorySuccessfully() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = object : InventoryRepository {
                override suspend fun getInventory(): List<InventoryItem> = sampleInventory
            }
            val vm = InventoryViewModel(repo,Equipment(),Room())
            runCurrent()

            assertEquals(3, vm.state.value.items.size)
            assertFalse(vm.state.value.loading)
            assertNull(vm.state.value.error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun categoryFilterWorks() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = object : InventoryRepository {
                override suspend fun getInventory(): List<InventoryItem> = sampleInventory
            }
            val vm = InventoryViewModel(repo,Equipment(),Room())
            runCurrent()

            assertNull(vm.state.value.selectedCategory)
            vm.selectCategory(ItemCategory.ACCESSORY)
            assertEquals(ItemCategory.ACCESSORY, vm.state.value.selectedCategory)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun equipAndUnequipTogglesState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = object : InventoryRepository {
                override suspend fun getInventory(): List<InventoryItem> = sampleInventory
            }
            val vm = InventoryViewModel(repo,Equipment(),Room())
            runCurrent()

            val hat = sampleInventory[1]
            vm.toggleEquip(hat)
            assertFalse(vm.state.value.equippedItemIds.contains(hat.item.id))
            runCurrent()
            assertTrue(vm.state.value.equippedItemIds.contains(hat.item.id))
            assertNotNull(vm.state.value.actionMessage)

            vm.toggleEquip(hat)
            runCurrent()
            assertFalse(vm.state.value.equippedItemIds.contains(hat.item.id))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun placeAndStoreTogglesState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = object : InventoryRepository {
                override suspend fun getInventory(): List<InventoryItem> = sampleInventory
            }
            val vm = InventoryViewModel(repo,Equipment(),Room())
            runCurrent()

            val bed = sampleInventory[2]
            vm.togglePlace(bed);runCurrent()
            assertTrue(vm.state.value.placedItemIds.contains(bed.item.id))
            assertNotNull(vm.state.value.actionMessage)

            vm.togglePlace(bed);runCurrent()
            assertFalse(vm.state.value.placedItemIds.contains(bed.item.id))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun repositoryErrorSetsErrorMessage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = object : InventoryRepository {
                override suspend fun getInventory(): List<InventoryItem> = error("Network timeout")
            }
            val vm = InventoryViewModel(repo,Equipment(),Room())
            runCurrent()

            assertFalse(vm.state.value.loading)
            assertNotNull(vm.state.value.error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun equipmentRestoresAndUnconfirmedChangeNeverPretendsSuccess()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val equipment=Equipment()
            val repo=object : InventoryRepository { override suspend fun getInventory()=sampleInventory }
            val vm=InventoryViewModel(repo,equipment,Room())
            runCurrent()
            vm.toggleEquip(sampleInventory[1]); runCurrent()
            val restored=InventoryViewModel(repo,equipment,Room());runCurrent()
            assertEquals(setOf(testItemHat.id),restored.state.value.equippedItemIds)
            equipment.fail=true
            restored.toggleEquip(sampleInventory[1]);runCurrent()
            assertEquals(setOf(testItemHat.id),restored.state.value.equippedItemIds)
            assertNotNull(restored.state.value.pendingEquipment)
            restored.toggleEquip(sampleInventory[1]);runCurrent()
            assertNotNull(restored.state.value.pendingEquipment)
            equipment.fail=false
            restored.retryEquipment();runCurrent()
            assertTrue(restored.state.value.equippedItemIds.isEmpty())
        } finally { Dispatchers.resetMain() }
    }
    @Test fun roomRestoresAndRetriesExactUnconfirmedStore()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val room=Room()
            val repo=object : InventoryRepository { override suspend fun getInventory()=sampleInventory }
            val vm=InventoryViewModel(repo,Equipment(),room);runCurrent()
            vm.togglePlace(sampleInventory[2])
            assertTrue(vm.state.value.placedItemIds.isEmpty())
            vm.togglePlace(sampleInventory[2]);runCurrent()
            assertEquals(setOf(testItemBed.id),vm.state.value.placedItemIds)
            val restored=InventoryViewModel(repo,Equipment(),room);runCurrent()
            assertEquals(vm.state.value.room,restored.state.value.room)
            room.fail=true
            restored.togglePlace(sampleInventory[2]);runCurrent()
            assertEquals(setOf(testItemBed.id),restored.state.value.placedItemIds)
            assertNotNull(restored.state.value.pendingRoom)
            restored.toggleEquip(sampleInventory[1]);runCurrent()
            assertTrue(restored.state.value.equippedItemIds.isEmpty())
            room.fail=false
            restored.retryRoom();runCurrent()
            assertTrue(restored.state.value.placedItemIds.isEmpty())
        } finally { Dispatchers.resetMain() }
    }
}
