package com.pawquest

import com.pawquest.domain.model.*
import com.pawquest.domain.repository.InMemoryPendingPurchaseStore
import com.pawquest.domain.repository.ItemRepository
import com.pawquest.domain.repository.PendingPurchase
import com.pawquest.domain.repository.PendingPurchaseStore
import com.pawquest.domain.usecase.PurchaseItem
import com.pawquest.presentation.shop.ShopViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Instant

/** A Shop purchase survives process death: same request ID, journal cleared only after an answer. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShopPendingPurchaseTest {
    private val itemId="e1000000-0000-0000-0000-000000000001"
    private val owner="a1000000-0000-0000-0000-000000000001"
    private val food=CatalogItem(itemId,"basic_food","Food",ItemCategory.FOOD,10,"item.food.basic",1000000,true)

    private class Repo(val food: CatalogItem,var failures: Int): ItemRepository {
        val requests=mutableListOf<PurchaseRequest>()
        override suspend fun getItems()=listOf(food)
        override suspend fun purchase(request: PurchaseRequest): PurchaseResult {
            requests+=request
            if(failures-->0) error("Simulated timeout after the server committed")
            return PurchaseResult(PurchaseStatus.PURCHASED,PurchaseReceipt("r-${request.requestId}",request.requestId,request.itemId,
                request.quantity,10,10L*request.quantity,90,Instant.fromEpochMilliseconds(0)))
        }
    }

    @Test fun lostResponseIsJournaledAndRecoveredAfterRestartWithSameRequest()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store=InMemoryPendingPurchaseStore()
            val first=Repo(food,failures=1)
            val vm=ShopViewModel(first,PurchaseItem(first),store,MutableStateFlow(owner));runCurrent()
            vm.purchase(itemId,2);runCurrent()
            val sent=first.requests.single()
            assertEquals(PendingPurchase.of(owner,sent),store.load(owner),"journaled before the lost response")
            assertNotNull(vm.state.value.error)

            // "Process death": a new ViewModel with the same durable store resumes on its own.
            val second=Repo(food,failures=0)
            val restored=ShopViewModel(second,PurchaseItem(second),store,MutableStateFlow(owner));runCurrent()
            assertEquals(listOf(sent),second.requests,"same request ID, no new purchase")
            assertEquals(PurchaseStatus.PURCHASED,restored.state.value.result?.status)
            assertNull(store.load(owner),"cleared after the server answered")
            assertFalse(restored.state.value.busy)
        } finally {Dispatchers.resetMain()}
    }

    @Test fun journalFailureNeverReachesServer()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val broken=object: PendingPurchaseStore {
                override suspend fun load(owner: String): PendingPurchase?=null
                override suspend fun save(purchase: PendingPurchase): PendingPurchase=error("disk full")
                override suspend fun clear(purchase: PendingPurchase)=Unit
            }
            val repo=Repo(food,failures=0)
            val vm=ShopViewModel(repo,PurchaseItem(repo),broken,MutableStateFlow(owner));runCurrent()
            vm.purchase(itemId,1);runCurrent()
            assertTrue(repo.requests.isEmpty())
            assertNotNull(vm.state.value.purchaseError)
            assertFalse(vm.state.value.busy)
        } finally {Dispatchers.resetMain()}
    }

    @Test fun pendingPurchaseOfAnotherAccountIsNotReplayed()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store=InMemoryPendingPurchaseStore()
            store.save(PendingPurchase(owner,itemId,1,"b1000000-0000-0000-0000-000000000001"))
            val repo=Repo(food,failures=0)
            val owners=MutableStateFlow<String?>("a2000000-0000-0000-0000-000000000002")
            val vm=ShopViewModel(repo,PurchaseItem(repo),store,owners);runCurrent()
            assertTrue(repo.requests.isEmpty())
            assertNull(vm.state.value.pending)
            owners.value=null;runCurrent()
            assertTrue(repo.requests.isEmpty(),"signed out: nothing replayed")
            assertNotNull(store.load(owner),"kept for its own account")
        } finally {Dispatchers.resetMain()}
    }

    @Test fun doubleTapWhileJournalingSendsOnce()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store=InMemoryPendingPurchaseStore()
            val repo=Repo(food,failures=0)
            val vm=ShopViewModel(repo,PurchaseItem(repo),store,MutableStateFlow(owner));runCurrent()
            vm.purchase(itemId,1);vm.purchase(itemId,1);runCurrent()
            assertEquals(1,repo.requests.size)
            assertNull(store.load(owner))
        } finally {Dispatchers.resetMain()}
    }
}
