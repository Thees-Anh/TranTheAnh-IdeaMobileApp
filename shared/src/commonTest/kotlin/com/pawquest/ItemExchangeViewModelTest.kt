package com.pawquest

import androidx.lifecycle.ViewModelStore
import com.pawquest.domain.model.*
import com.pawquest.domain.repository.*
import com.pawquest.presentation.gacha.ItemExchangeViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ItemExchangeViewModelTest {
    private val owner="fc100000-0000-0000-0000-000000000001"
    private val item="e2300000-0000-0000-0000-000000000003"
    private val stamp=Instant.parse("2026-11-10T05:00:00Z")
    private inner class Fixture {
        val auth=object: AuthenticationRepository {
            override val state=MutableStateFlow<AuthenticationState>(AuthenticationState.SignedIn(UserAccount(owner,null,"Test","pet","Mochi",0,0)))
            override suspend fun register(email: String,password: String)=RegistrationOutcome.SIGNED_IN
            override suspend fun login(email: String,password: String)=Unit
            override suspend fun logout()=LogoutOutcome.SIGNED_OUT
            override suspend fun retryRestoration()=Unit
        }
        var pending: PendingItemExchange?=null
        var timeoutQuote=false;var timeoutRedeem=false;var failSave=false;var failClear=false
        var reject: ItemExchangeRejection?=null
        var quoteCalls=0;var redeemCalls=0;var inventoryReads=0
        val committed=mutableMapOf<String,ItemExchangeReceipt>()
        val store=object: PendingItemExchangeStore {
            override suspend fun load(userId: String)=pending?.takeIf {it.userId==userId}
            override suspend fun save(request: PendingItemExchange): PendingItemExchange {
                check(!failSave)
                val old=pending
                if(old==null || old.requestId==request.requestId && request.confirmed) pending=request
                return pending!!
            }
            override suspend fun clear(request: PendingItemExchange) {check(!failClear);if(pending==request) pending=null}
        }
        val repo=object: ShardRepository {
            override suspend fun getBalances()=ShardWallet(if(committed.isEmpty())75 else 45,0)
            override suspend fun quoteItem(request: PendingItemExchange): ItemExchangeResponse {
                quoteCalls++;assertEquals(request,pending);assertFalse(request.confirmed)
                if(timeoutQuote) error("lost quote response")
                return ItemExchangeResponse.Quoted(ItemExchangeQuote(item,request.requestId,30,stamp))
            }
            override suspend fun redeemItem(request: PendingItemExchange): ItemExchangeResponse {
                redeemCalls++;assertEquals(request,pending);assertTrue(pending!!.confirmed)
                reject?.let {return ItemExchangeResponse.Rejected(it)}
                val receipt=committed.getOrPut(request.requestId) {ItemExchangeReceipt("fc300000-0000-0000-0000-000000000001",
                    request.requestId,item,"Sprout","acc_sprout","acc-sprout",ItemCategory.ACCESSORY,ItemRarity.COMMON,30,1,45,stamp)}
                if(timeoutRedeem) error("lost commit response")
                return ItemExchangeResponse.Redeemed(receipt)
            }
        }
        val inventory=object: InventoryRepository {
            override suspend fun getInventory(): List<InventoryItem> {
                inventoryReads++
                return if(committed.isEmpty()) emptyList() else listOf(InventoryItem("row",owner,CatalogItem(item,"acc_sprout","Sprout",ItemCategory.ACCESSORY,80,"acc-sprout",1,true),1,stamp))
            }
        }
        fun model()=ItemExchangeViewModel(repo,inventory,auth,store)
    }
    private suspend fun TestScope.testModel(test: suspend TestScope.(Fixture,ItemExchangeViewModel)->Unit) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture=Fixture();val model=fixture.model();val store=ViewModelStore().also {it.put("exchange",model)}
        try {runCurrent();test(fixture,model)} finally {store.clear();Dispatchers.resetMain()}
    }
    @Test fun quoteRetryNeverRedeemsAndConfirmationIsPersistedBeforeOneGrant()=runTest {
        testModel {f,vm->
            f.timeoutQuote=true;vm.select(item);runCurrent();val key=vm.state.value.pending!!.requestId
            vm.retry();runCurrent();assertEquals(0,f.redeemCalls)
            f.timeoutQuote=false;vm.retry();runCurrent()
            assertEquals(key,vm.state.value.quote!!.requestId);assertEquals(75L,vm.state.value.balance)
            repeat(5) {vm.confirm()};runCurrent()
            assertEquals(1,f.redeemCalls);assertEquals(45L,vm.state.value.balance)
            assertEquals(item,vm.state.value.inventory.single().item.id);assertEquals(2,f.inventoryReads)
            vm.dismiss();runCurrent();assertNull(f.pending)
        }
    }
    @Test fun uncertainConfirmedRetryReusesKeyAndCannotBeDismissed()=runTest {
        testModel {f,vm->
            vm.select(item);runCurrent();f.timeoutRedeem=true;vm.confirm();runCurrent()
            val key=f.pending!!.requestId
            vm.dismiss();runCurrent();assertNotNull(f.pending)
            vm.select(item);assertEquals(1,f.redeemCalls)
            f.timeoutRedeem=false;vm.retry();runCurrent()
            assertEquals(key,vm.state.value.receipt!!.requestId);assertEquals(1,f.committed.size)
            assertEquals(2,f.redeemCalls)
        }
    }
    @Test fun explicitPriceChangeRejectionDoesNotAutomaticallyTryAnotherPrice()=runTest {
        testModel {f,vm->
            vm.select(item);runCurrent();f.reject=ItemExchangeRejection.PRICE_CHANGED
            vm.confirm();runCurrent();assertTrue(f.committed.isEmpty())
            vm.dismiss();runCurrent();assertNull(f.pending)
            assertEquals(1,f.quoteCalls);assertEquals(1,f.redeemCalls)
        }
    }
    @Test fun failedIntentWritePreventsAnyBackendOperation()=runTest {
        testModel {f,vm->f.failSave=true;vm.select(item);runCurrent();assertEquals(0,f.quoteCalls);assertEquals(0,f.redeemCalls)}
    }
    @Test fun processRecreationRecoversConfirmedIntentWithoutNewKeyOrGrant()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f=Fixture();val old=f.model();val first=ViewModelStore().also {it.put("exchange",old)}
        var second: ViewModelStore?=null
        try {
            runCurrent();old.select(item);runCurrent();f.timeoutRedeem=true;old.confirm();runCurrent()
            val key=f.pending!!.requestId;first.clear();f.timeoutRedeem=false
            val restored=f.model();second=ViewModelStore().also {it.put("exchange",restored)};runCurrent()
            assertEquals(key,restored.state.value.receipt!!.requestId);assertEquals(1,f.committed.size)
        } finally {first.clear();second?.clear();Dispatchers.resetMain()}
    }
}
