package com.pawquest

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.pawquest.domain.model.*
import com.pawquest.domain.repository.*
import com.pawquest.domain.usecase.PurchaseItem
import com.pawquest.localization.AppLanguage
import com.pawquest.localization.AppLocale
import com.pawquest.presentation.gacha.*
import com.pawquest.presentation.navigation.*
import com.pawquest.presentation.shop.*
import com.pawquest.presentation.state.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.time.Instant

/** Test APK only: no real account/client/network/purchases or production data. */
class MysteryShopFixtureActivity : ComponentActivity() {
    internal var testInventoryViews=0
    internal var testEquipActions=0
    internal var redemptionRequests=0
    internal lateinit var exchangeModel: ItemExchangeViewModel
    internal lateinit var itemCaseModel: ItemCaseViewModel
    internal val luckyFixture=LuckyShopFixture()
    internal lateinit var wheelModel: LuckyWheelViewModel
    internal val openingRequests=mutableListOf<PendingItemCase>()
    private val owner="fb260000-0000-0000-0000-000000000001"
    private val stamp=Instant.parse("2026-11-10T05:00:00Z")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val catalog=ItemCategory.entries.mapIndexed { i,c -> CatalogItem("e1000000-0000-0000-0000-${(i+1).toString().padStart(12,'0')}",
            "test_$i",listOf("Food fixture","Accessory fixture","Furniture fixture")[i],c,10,"test-$i",if(c==ItemCategory.FOOD) 1000000 else 1,true) } + CatalogItem("e2300000-0000-0000-0000-000000000005",
            "acc_glasses","Rare glasses",ItemCategory.ACCESSORY,100,"acc-glasses",1,true) + CatalogItem("e2300000-0000-0000-0000-000000000003",
            "acc_sprout","Sprout",ItemCategory.ACCESSORY,80,"acc-sprout",1,true)
        val itemRepo=object : ItemRepository {
            override suspend fun getItems()=catalog
            override suspend fun purchase(request: PurchaseRequest): PurchaseResult=error("No fixture purchase")
        }
        val auth=object : AuthenticationRepository {
            override val state=MutableStateFlow<AuthenticationState>(AuthenticationState.SignedIn(UserAccount(owner,null,"Test","pet","Mochi",1000,0)))
            override suspend fun register(email: String,password: String)=RegistrationOutcome.SIGNED_IN
            override suspend fun login(email: String,password: String)=Unit
            override suspend fun logout()=LogoutOutcome.SIGNED_OUT
            override suspend fun retryRestoration()=Unit
        }
        val cases=object : ItemCaseRepository {
            override suspend fun getConfiguration(bannerId: String)=LoadedItemCase(ItemCaseBanner(bannerId,"item_case",intent.getBooleanExtra("test_opening",false),1),
                ItemCaseConfiguration("e2400000-0000-0000-0000-000000000002",bannerId,1,100,
                    ItemRarity.entries.mapIndexed { i,r -> ItemCaseTier(r,listOf(5500,3000,1200,300)[i],5,30) },
                    ItemRarity.entries.mapIndexed { i,r -> ItemCaseReward("e2300000-0000-0000-0000-${listOf(3,5,2,1)[i].toString().padStart(12,'0')}","reward_$i","acc-glasses",ItemCategory.ACCESSORY,1,r,1,true) }))
            override suspend fun open(request: PendingItemCase): ItemCaseResponse {
                check(intent.getBooleanExtra("test_opening",false))
                openingRequests+=request
                return ItemCaseResponse.Opened(ItemCaseReceipt("fb280000-0000-0000-0000-${openingRequests.size.toString().padStart(12,'0')}",
                    request.requestId,request.bannerId,"e2400000-0000-0000-0000-000000000002",1,
                    "e2300000-0000-0000-0000-000000000005","Rare glasses","acc_glasses","acc-glasses",ItemRarity.RARE,
                    100,if(intent.getBooleanExtra("test_grant",false)) ItemCaseRewardOutcome.ITEM_GRANTED else ItemCaseRewardOutcome.DUPLICATE_CONVERTED,
                    if(intent.getBooleanExtra("test_grant",false)) 1 else 0,if(intent.getBooleanExtra("test_grant",false)) 0 else 10,900,85,stamp))
            }
            override suspend fun getHistory()=openingRequests.mapIndexed {index,_ -> ItemCaseHistoryEntry("history-$index",
                "e2300000-0000-0000-0000-000000000005",100,if(intent.getBooleanExtra("test_grant",false)) 0 else 10,stamp,ItemRarity.RARE,
                if(intent.getBooleanExtra("test_grant",false)) ItemCaseRewardOutcome.ITEM_GRANTED else ItemCaseRewardOutcome.DUPLICATE_CONVERTED,
                "acc_glasses","acc-glasses",ItemCategory.ACCESSORY)}
        }
        val journal=object : PendingItemCaseStore {
            override suspend fun load(userId: String): PendingItemCase?=null
            override suspend fun saveIfAbsent(request: PendingItemCase)=request
            override suspend fun clear(request: PendingItemCase)=Unit
        }
        val shardRepo=object : ShardRepository {
            override suspend fun getBalances()=ShardWallet(if(redemptionRequests>0)45 else if(openingRequests.isEmpty())75 else 85,0)
            override suspend fun getItemLedger()=if(openingRequests.isEmpty()) emptyList() else listOf(ShardLedgerEntry("ledger",owner,ShardCurrency.ITEM_SHARD,
                "request",ShardLedgerReason.ITEM_DUPLICATE,10,85,"opening",null,null,stamp))
            override suspend fun quoteItem(request: PendingItemExchange)=ItemExchangeResponse.Quoted(ItemExchangeQuote(request.itemId,request.requestId,30,stamp))
            override suspend fun redeemItem(request: PendingItemExchange): ItemExchangeResponse {
                check(request.confirmed);redemptionRequests++
                return ItemExchangeResponse.Redeemed(ItemExchangeReceipt("fc300000-0000-0000-0000-000000000001",request.requestId,request.itemId,
                    "Sprout","acc_sprout","acc-sprout",ItemCategory.ACCESSORY,ItemRarity.COMMON,30,1,45,stamp))
            }
        }
        val inventoryRepo=object: InventoryRepository {
            override suspend fun getInventory()=if(redemptionRequests==0) emptyList() else listOf(InventoryItem("row",owner,catalog.last(),1,stamp))
        }
        val model=ItemCaseViewModel(cases,shardRepo,
            object : PetRepository {
                override suspend fun restore()=PetSnapshot(Pet("pet",owner,"Mochi",PetStats(),PetProgress(0),stamp),if(openingRequests.isEmpty())1000 else 900,stamp)
                override suspend fun care(action: PetCareAction,requestId: String)=restore()
            },inventoryRepo,auth,journal)
        val exchangeStore=object: PendingItemExchangeStore {
            var pending: PendingItemExchange?=null
            override suspend fun load(userId: String)=pending
            override suspend fun save(request: PendingItemExchange): PendingItemExchange {pending=request;return request}
            override suspend fun clear(request: PendingItemExchange) {if(pending==request)pending=null}
        }
        exchangeModel=ItemExchangeViewModel(shardRepo,inventoryRepo,auth,exchangeStore)
        viewModelStore.put("exchange",exchangeModel)
        val shop=ShopViewModel(itemRepo,PurchaseItem(itemRepo))
        if(intent.getBooleanExtra("test_wheel_fallback",false)) luckyFixture.owned.addAll(redeemablePetCodes)
        wheelModel=LuckyWheelViewModel(luckyFixture.backend,luckyFixture.backend,auth,luckyFixture.journal,inventoryRepo,
            object : PetRepository {
                override suspend fun restore(): PetSnapshot=error("Unused")
                override suspend fun care(action: PetCareAction,requestId: String): PetSnapshot=error("Unused")
                override suspend fun collection()=PetCollection("mochi_cat",1,emptyList())
            })
        viewModelStore.put("wheel",wheelModel)
        itemCaseModel=model
        if(intent.getBooleanExtra("test_exchange",false)) shop.selectSection(ShopSection.SHARD_EXCHANGE)
        else if(intent.getBooleanExtra("test_opening",false)) shop.selectMysteryCase()
        viewModelStore.put("case",model); viewModelStore.put("shop",shop)
        setContent {
            val state by model.state.collectAsState()
            val exchangeState by exchangeModel.state.collectAsState()
            LaunchedEffect(exchangeState.receipt?.id) {exchangeState.receipt?.let {model.refreshAfterExchange(it.balanceAfter)}}
            var destination by remember { mutableStateOf(Destination.Shop) }
            PawQuestTheme {
                PawQuestScaffold(destination,UiState.Content(SectionPlaceholder("Shop","")),{destination=it},{},
                    shopContent={ modifier -> ShopScreen(shop,state.coins ?: 1000,modifier,itemShards=state.itemShards,itemCaseModel=model,
                        casePreview={MysteryCasePreview(it)},caseAnimation={animation,m->MysteryCasePreview(m,animation)},
                        onViewCaseItem={testInventoryViews++},onUseCaseReward={testEquipActions++},
                        actionableCaseItemIds=setOf("e2300000-0000-0000-0000-000000000005","e2300000-0000-0000-0000-000000000003"),exchangeModel=exchangeModel,
                        luckyWheelModel=wheelModel,wheelPreview={animation,m->LuckyWheelPreview(m,animation)}) })
            }
        }
    }
}

class MysteryShopUiDeviceTest {
    @Test fun existingTabsAndMysteryCaseRenderWithWalletAndBottomNavigation() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val ui=instrumentation.uiAutomation
        val previous=AppLocale.language
        AppLocale.select(AppLanguage.ENGLISH)
        val activity=instrumentation.startActivitySync(Intent(instrumentation.context,MysteryShopFixtureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        fun findText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.text?.toString() == text) return node
            for (index in 0 until node.childCount) findText(node.getChild(index), text)?.let { return it }
            return null
        }
        fun waitText(text: String): AccessibilityNodeInfo {
            repeat(200) {
                findText(ui.rootInActiveWindow, text)?.let { return it }
                SystemClock.sleep(75)
            }
            val root=ui.rootInActiveWindow
            val capture=ui.takeScreenshot()
            if(capture!=null) File(instrumentation.targetContext.getExternalFilesDir(null),"mystery-shop-failure.png").outputStream().use {
                capture.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
            fun texts(node: AccessibilityNodeInfo?): String {
                if(node==null) return "null"
                return "[${node.packageName}:${node.text}:${node.contentDescription}]"+(0 until node.childCount).joinToString { texts(node.getChild(it)) }
            }
            error("Missing visible UI: $text root=${texts(root).take(3500)}")
        }
        fun click(text: String) {
            val bounds=Rect().also { waitText(text).getBoundsInScreen(it) }
            ui.executeShellCommand("input tap ${bounds.centerX()} ${bounds.centerY()}").use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes()
            }
            instrumentation.waitForIdleSync()
        }
        try {
            waitText("Food fixture")
            val food=Rect().also { waitText("Food Shop").getBoundsInScreen(it) }
            click("Mystery Box");waitText("Case preview")
            val width=instrumentation.targetContext.resources.displayMetrics.widthPixels
            ui.executeShellCommand("input swipe ${width*3/4} ${food.centerY()} ${width/4} ${food.centerY()} 400").use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes()
            }
            SystemClock.sleep(500) // Wait for horizontal scroll/fling to settle before hit testing.
            click("Mystery Box")
            waitText("Case preview"); waitText("Item Shards: 75")
            waitText("Pet Home"); waitText("Inventory"); waitText("Profile")
            var diagnostics=""
            val diagnosticReady=java.util.concurrent.CountDownLatch(1)
            instrumentation.runOnMainSync {
                fun find(view: android.view.View): com.pawquest.presentation.gacha.MysteryCaseWebView? {
                    if(view is com.pawquest.presentation.gacha.MysteryCaseWebView) return view
                    if(view is android.view.ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i))?.let {return it}
                    return null
                }
                val web=checkNotNull(find(activity.window.decorView))
                web.evaluateJavascript("JSON.stringify({w:innerWidth,h:innerHeight,stage:document.getElementById('gacha-stage-container').getBoundingClientRect().toJSON(),body:document.body.innerHTML.length,styles:[document.documentElement,document.body,document.querySelector('main'),document.getElementById('gacha-stage-container')].map(e=>({tag:e.tagName,rect:e.getBoundingClientRect().toJSON(),height:getComputedStyle(e).height,display:getComputedStyle(e).display,background:getComputedStyle(e).backgroundImage}))})") {
                    diagnostics="${web.width}x${web.height}: $it"; diagnosticReady.countDown()
                }
            }
            check(diagnosticReady.await(5,java.util.concurrent.TimeUnit.SECONDS))
            SystemClock.sleep(700) // Allow the local WebView compositor to paint before capture.
            val screenshot=ui.takeScreenshot()
            // Readiness alone is insufficient: catch an attached but blank WebView.
            var purplePixels=0
            for(y in 500 until screenshot.height-250 step 8) for(x in 50 until screenshot.width-50 step 8) {
                val color=screenshot.getPixel(x,y)
                val r=android.graphics.Color.red(color); val g=android.graphics.Color.green(color); val b=android.graphics.Color.blue(color)
                if(b>g*1.3 && r>g*1.15 && r<180 && b>50) purplePixels++
            }
            assertTrue("The purple HTML stage must actually paint ($purplePixels pixels) $diagnostics",purplePixels>100)
            val output=File(instrumentation.targetContext.getExternalFilesDir(null),"mystery-shop.png")
            output.outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            assertTrue(output.length()>0)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            AppLocale.select(previous)
        }
    }
}
