package com.pawquest

import com.pawquest.domain.model.*
import com.pawquest.domain.repository.ItemRepository
import com.pawquest.domain.usecase.PurchaseItem
import com.pawquest.presentation.shop.ShopViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ShopCatalogTest {
    @Test fun mysteryCaseSelectionPreservesCatalogAndReturnsToExistingCategories()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo=object : ItemRepository {
                override suspend fun getItems()=sampleCatalog
                override suspend fun purchase(request: PurchaseRequest): PurchaseResult=error("No purchase expected")
            }
            val vm=ShopViewModel(repo,PurchaseItem(repo)); runCurrent()
            vm.selectMysteryCase(); assertTrue(vm.state.value.mysteryCaseSelected)
            assertEquals(sampleCatalog,vm.state.value.catalog)
            ItemCategory.entries.forEach { category ->
                vm.selectCategory(category)
                assertFalse(vm.state.value.mysteryCaseSelected)
                assertEquals(ItemCategory.FOOD,vm.state.value.selectedCategory)
                assertEquals(if(category==ItemCategory.FOOD) com.pawquest.presentation.shop.ShopSection.FOOD else com.pawquest.presentation.shop.ShopSection.SHARD_EXCHANGE,vm.state.value.section)
            }
        } finally { Dispatchers.resetMain() }
    }

    private val sampleCatalog = listOf(
        CatalogItem("e1000000-0000-0000-0000-000000000001", "basic_food", "Cat food", ItemCategory.FOOD, 10, "item.food.basic", 1000000, true),
        CatalogItem("e1000000-0000-0000-0000-000000000002", "hat", "Hat", ItemCategory.ACCESSORY, 60, "item.accessory.hat", 1, true),
        CatalogItem("e1000000-0000-0000-0000-000000000005", "bed", "Bed", ItemCategory.FURNITURE, 120, "item.furniture.bed", 1, true),
    )

    @Test
    fun loadsCatalogSuccessfully() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = object : ItemRepository {
                override suspend fun getItems(): List<CatalogItem> = sampleCatalog
                override suspend fun purchase(request: PurchaseRequest): PurchaseResult =
                    PurchaseResult(PurchaseStatus.PURCHASED)
            }
            val vm = ShopViewModel(repo, PurchaseItem(repo))
            runCurrent()

            assertEquals(3, vm.state.value.catalog.size)
            assertFalse(vm.state.value.loading)
            assertNull(vm.state.value.error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun categorySelectionUpdatesState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = object : ItemRepository {
                override suspend fun getItems(): List<CatalogItem> = sampleCatalog
                override suspend fun purchase(request: PurchaseRequest): PurchaseResult =
                    PurchaseResult(PurchaseStatus.PURCHASED)
            }
            val vm = ShopViewModel(repo, PurchaseItem(repo))
            runCurrent()

            assertEquals(ItemCategory.FOOD, vm.state.value.selectedCategory)
            vm.selectCategory(ItemCategory.ACCESSORY)
            assertEquals(com.pawquest.presentation.shop.ShopSection.SHARD_EXCHANGE, vm.state.value.section)
            vm.openPurchase(sampleCatalog[1]);assertNull(vm.state.value.pendingPurchase)
            vm.openPurchase(sampleCatalog[2]);assertNull(vm.state.value.pendingPurchase)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun purchaseDialogFlow() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = object : ItemRepository {
                override suspend fun getItems(): List<CatalogItem> = sampleCatalog
                override suspend fun purchase(request: PurchaseRequest): PurchaseResult =
                    PurchaseResult(PurchaseStatus.PURCHASED)
            }
            val vm = ShopViewModel(repo, PurchaseItem(repo))
            runCurrent()

            val food = sampleCatalog[0]
            vm.openPurchase(food)
            assertEquals(food, vm.state.value.pendingPurchase)
            assertEquals(1, vm.state.value.purchaseQuantity)

            vm.setQuantity(5)
            assertEquals(5, vm.state.value.purchaseQuantity)

            vm.dismissPurchase()
            assertNull(vm.state.value.pendingPurchase)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
