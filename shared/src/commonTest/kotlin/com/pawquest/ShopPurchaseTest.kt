package com.pawquest

import com.pawquest.domain.model.*
import com.pawquest.domain.repository.ItemRepository
import com.pawquest.domain.usecase.PurchaseItem
import com.pawquest.presentation.shop.ShopViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ShopPurchaseTest {
    private val id="e1000000-0000-0000-0000-000000000001"
    @Test fun ambiguousFailureRetriesSameRequestAndBlocksOtherPurchases()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val requests=mutableListOf<PurchaseRequest>()
            val repo=object : ItemRepository {
                override suspend fun getItems()=listOf(CatalogItem(id,"basic_food","Food",ItemCategory.FOOD,10,"item.food.basic",1000000,true))
                override suspend fun purchase(request: PurchaseRequest): PurchaseResult {
                    requests+=request
                    if(requests.size==1) error("Simulated lost response")
                    return PurchaseResult(PurchaseStatus.INSUFFICIENT_BALANCE)
                }
            }
            val vm=ShopViewModel(repo,PurchaseItem(repo));runCurrent()
            vm.purchase(id,2)
            vm.purchase(id,2)
            runCurrent()
            val pending=vm.state.value.pending
            assertNotNull(pending)
            assertNotNull(vm.state.value.error)
            vm.purchase(id,3)
            runCurrent()
            assertEquals(1,requests.size)
            vm.retry()
            runCurrent()
            assertEquals(listOf(pending,pending),requests)
            assertNull(vm.state.value.pending)
            assertEquals(PurchaseStatus.INSUFFICIENT_BALANCE,vm.state.value.result?.status)
            vm.purchase(id,1)
            runCurrent()
            assertNotEquals(pending.requestId,requests.last().requestId)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun invalidIntentNeverReachesRepository()=runTest {
        var calls=0
        val useCase=PurchaseItem(object : ItemRepository {
            override suspend fun getItems()=emptyList<CatalogItem>()
            override suspend fun purchase(request: PurchaseRequest): PurchaseResult {
                calls++; return PurchaseResult(PurchaseStatus.INVALID_QUANTITY)
            }
        })
        for(quantity in listOf(-1,0,101,Int.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> { useCase(PurchaseRequest(id,quantity,id)) }
        }
        assertFailsWith<IllegalArgumentException> { useCase(PurchaseRequest("bad",1,id)) }
        assertEquals(0,calls)
    }
}
