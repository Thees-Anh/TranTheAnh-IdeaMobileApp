package com.pawquest

import androidx.lifecycle.ViewModelStore
import com.pawquest.domain.model.*
import com.pawquest.domain.repository.*
import com.pawquest.presentation.bistro.BistroViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class BistroViewModelTest {
    private val owner="ba290000-0000-0000-0000-000000000001"
    private val other="ba290000-0000-0000-0000-000000000002"
    private val food=BistroItem("e2900000-0000-0000-0000-000000000001","food-bone-biscuit","Biscuit",10,"BASIC","food",1,15,5,5,1)
    private inner class Fixture : PetRepository,AuthenticationRepository {
        override val state=MutableStateFlow<AuthenticationState>(signed(owner))
        val pending=mutableMapOf<String,PendingBistroMeal>();val requests=mutableListOf<PendingBistroMeal>()
        val receipts=mutableMapOf<String,BistroReceipt>();var coins=1000L;var xp=0;var lose=false;var reject=false
        var gate: CompletableDeferred<Unit>?=null;var menuGate: CompletableDeferred<Unit>?=null
        override suspend fun restore(): PetSnapshot=error("Unused")
        override suspend fun care(action: PetCareAction,requestId: String): PetSnapshot=error("Unused")
        override suspend fun bistroMenu(): List<BistroItem> {menuGate?.await();return listOf(food)}
        override suspend fun pendingMeal(owner: String)=pending[owner]
        override suspend fun saveMeal(request: PendingBistroMeal)=pending.getOrPut(request.userId) {request}
        override suspend fun clearMeal(request: PendingBistroMeal) {if(pending[request.userId]==request)pending.remove(request.userId)}
        override suspend fun bistroFeed(request: PendingBistroMeal): BistroResponse {
            requests+=request
            if(reject)return BistroResponse.Rejected("price_changed")
            val receipt=receipts.getOrPut(request.requestId) {
                coins-=request.expectedPrice;xp+=5
                BistroReceipt("bc290000-0000-0000-0000-${(receipts.size+1).toString().padStart(12,'0')}",request.userId,request.requestId,request.itemId,
                    "bd290000-0000-0000-0000-000000000001",1,food.name,food.assetId,food.type,request.expectedPrice,
                    coins,15,5,5,"be290000-0000-0000-0000-000000000001","mochi_cat","2026-11-11T00:00:00Z")
            }
            gate?.await()
            if(lose){lose=false;error("Response lost after commit")}
            return BistroResponse.Fed(receipt)
        }
        override suspend fun register(email: String,password: String)=RegistrationOutcome.SIGNED_IN
        override suspend fun login(email: String,password: String)=Unit
        override suspend fun logout()=LogoutOutcome.SIGNED_OUT
        override suspend fun retryRestoration()=Unit
    }
    private fun signed(id: String)=AuthenticationState.SignedIn(UserAccount(id,null,"Test","pet","Mochi",1000,0))
    private fun TestScope.model(f: Fixture,store: ViewModelStore): BistroViewModel = BistroViewModel(f,f,100).also {store.put("bistro",it);it.visible(true);runCurrent();it.loadMenu();runCurrent()}
    @Test fun explicitOrderUsesOneIntentAndReceiptControlsAnimation()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val f=Fixture();val store=ViewModelStore()
        try {
            val vm=model(f,store);f.gate=CompletableDeferred();vm.order(food.itemId);vm.order(food.itemId);runCurrent()
            assertEquals(1,f.requests.size);assertEquals(990L,f.coins)
            f.gate!!.complete(Unit);runCurrent()
            val receipt=assertNotNull(vm.state.value.receipt)
            assertTrue(vm.state.value.animationPending);assertEquals(5,receipt.xp)
            vm.order(food.itemId);runCurrent();assertEquals(1,f.requests.size)
            vm.animationFinished("forged");runCurrent();assertTrue(vm.state.value.animationPending)
            vm.animationFinished(receipt.id);runCurrent()
            assertTrue(vm.state.value.canOrder);assertNull(vm.state.value.pending)
            vm.animationFinished(receipt.id);runCurrent();assertEquals(1,f.requests.size)
            vm.order(food.itemId);runCurrent()
            assertEquals(2,f.requests.size);assertEquals(2,f.requests.map {it.requestId}.distinct().size)
            assertEquals(980L,f.coins);assertNotEquals(receipt.id,vm.state.value.receipt?.id)
        } finally {store.clear();Dispatchers.resetMain()}
    }
    @Test fun lostCommittedResponseRetriesSameKeyWithoutSecondChargeOrXp()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val f=Fixture();val store=ViewModelStore()
        try {
            val vm=model(f,store);f.lose=true;vm.order(food.itemId);runCurrent()
            assertNotNull(vm.state.value.error);assertNotNull(vm.state.value.pending)
            vm.retry();runCurrent()
            assertEquals(1,f.requests.map {it.requestId}.distinct().size);assertEquals(990L,f.coins);assertEquals(5,f.xp)
            assertNotNull(vm.state.value.receipt)
        } finally {store.clear();Dispatchers.resetMain()}
    }
    @Test fun timeoutReleasesBusyAndPreservesOriginalIntent()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val f=Fixture();val store=ViewModelStore()
        try {
            val vm=model(f,store);f.gate=CompletableDeferred();vm.order(food.itemId);runCurrent()
            advanceTimeBy(101);runCurrent();assertFalse(vm.state.value.busy);assertNotNull(vm.state.value.pending)
            f.gate=null;vm.retry();runCurrent();assertEquals(990L,f.coins);assertEquals(1,f.receipts.size)
        } finally {store.clear();Dispatchers.resetMain()}
    }
    @Test fun newViewModelRestoresPendingWithoutAutomaticallyPosting()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val f=Fixture();val first=ViewModelStore();val second=ViewModelStore()
        try {
            val a=model(f,first);f.lose=true;a.order(food.itemId);runCurrent();val original=a.state.value.pending
            first.clear();val b=model(f,second)
            assertEquals(original,b.state.value.pending);assertEquals(1,f.requests.size)
            b.retry();runCurrent();assertEquals(2,f.requests.size);assertEquals(990L,f.coins);assertEquals(5,f.xp)
        } finally {first.clear();second.clear();Dispatchers.resetMain()}
    }
    @Test fun lateReceiptCannotLeakIntoAnotherAccount()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val f=Fixture();val store=ViewModelStore()
        try {
            val vm=model(f,store);f.gate=CompletableDeferred();vm.order(food.itemId);runCurrent()
            f.state.value=signed(other);runCurrent();f.gate!!.complete(Unit);runCurrent()
            assertEquals(other,vm.state.value.owner);assertNull(vm.state.value.receipt);assertNull(vm.state.value.pending)
            assertNotNull(f.pending[owner])
        } finally {store.clear();Dispatchers.resetMain()}
    }
    @Test fun rejectionClearsIntentAndNeverAwardsAReceipt()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val f=Fixture();val store=ViewModelStore()
        try {
            val vm=model(f,store);f.reject=true;vm.order(food.itemId);runCurrent()
            assertNull(vm.state.value.receipt);assertNull(vm.state.value.pending);assertEquals(1000L,f.coins);assertEquals(0,f.xp)
        } finally {store.clear();Dispatchers.resetMain()}
    }
    @Test fun responseAfterNavigationCommitsOnceAndDoesNotStrandAnimation()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val f=Fixture();val store=ViewModelStore()
        try {
            val vm=model(f,store);f.gate=CompletableDeferred();vm.order(food.itemId);runCurrent()
            vm.visible(false);f.gate!!.complete(Unit);runCurrent()
            assertEquals(990L,f.coins);assertEquals(5,f.xp)
            assertNotNull(vm.state.value.receipt);assertNull(vm.state.value.pending);assertFalse(vm.state.value.animationPending)
            vm.visible(true);runCurrent();assertEquals(1,f.requests.size)
        } finally {store.clear();Dispatchers.resetMain()}
    }
}
