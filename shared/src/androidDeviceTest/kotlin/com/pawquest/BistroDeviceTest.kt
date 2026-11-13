package com.pawquest

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.pawquest.domain.model.*
import com.pawquest.domain.repository.*
import com.pawquest.localization.*
import com.pawquest.presentation.bistro.*
import com.pawquest.presentation.pet.*
import com.pawquest.presentation.theme.PawQuestTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Instant

/** Isolated catalog/receipt fixture: no account, Supabase client or real purchase. */
class BistroFixtureActivity : ComponentActivity() {
    lateinit var model: BistroViewModel
    var calls=0;var coins=1000L;var xp=0L
    val owner="ba290000-0000-0000-0000-000000000001"
    val menu by lazy {Json.decodeFromString<List<BistroItem>>(assets.open("bistro_menu.json").bufferedReader().use {it.readText()})}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val species=intent.getStringExtra("species") ?: "puppy"
        var snapshot by mutableStateOf(PetSnapshot(Pet("be290000-0000-0000-0000-000000000001",owner,"Mochi",PetStats(20,30,60,55),PetProgress(0),
            Instant.parse("2026-11-11T00:00:00Z"),speciesCode=if(species=="prototype_cat") "mochi_cat" else species,rendererKey=species),coins,Instant.parse("2026-11-11T00:00:00Z"),5))
        val pending=mutableMapOf<String,PendingBistroMeal>();val receipts=mutableMapOf<String,BistroReceipt>()
        val repo=object : PetRepository {
            override suspend fun restore()=snapshot
            override suspend fun care(action: PetCareAction,requestId: String)=snapshot
            override suspend fun bistroMenu()=menu
            override suspend fun pendingMeal(owner: String)=pending[owner]
            override suspend fun saveMeal(request: PendingBistroMeal)=pending.getOrPut(request.userId) {request}
            override suspend fun clearMeal(request: PendingBistroMeal) {if(pending[request.userId]==request)pending.remove(request.userId)}
            override suspend fun bistroFeed(request: PendingBistroMeal): BistroResponse {
                calls++
                val receipt=receipts.getOrPut(request.requestId) {
                    val item=menu.single {it.itemId==request.itemId};coins-=item.price;xp+=item.xp
                    val hunger=minOf(item.hunger,100-snapshot.pet.stats.hunger);val happy=minOf(item.happiness,100-snapshot.pet.stats.happiness)
                    snapshot=snapshot.copy(coins=coins,pet=snapshot.pet.copy(stats=snapshot.pet.stats.copy(hunger=snapshot.pet.stats.hunger+hunger,happiness=snapshot.pet.stats.happiness+happy),progress=PetProgress(xp)))
                    val suffix=(receipts.size+1).toString().padStart(12,'0')
                    BistroReceipt("bc290000-0000-0000-0000-$suffix",owner,request.requestId,item.itemId,
                        "bd290000-0000-0000-0000-$suffix",1,item.name,item.assetId,item.type,item.price,coins,hunger,happy,item.xp,
                        snapshot.pet.id,snapshot.pet.speciesCode,"2026-11-11T00:00:00Z")
                };return BistroResponse.Fed(receipt)
            }
        }
        val auth=object : AuthenticationRepository {
            override val state=MutableStateFlow<AuthenticationState>(AuthenticationState.SignedIn(UserAccount(owner,null,"Test","pet","Mochi",1000,0)))
            override suspend fun register(email: String,password: String)=RegistrationOutcome.SIGNED_IN
            override suspend fun login(email: String,password: String)=Unit
            override suspend fun logout()=LogoutOutcome.SIGNED_OUT
            override suspend fun retryRestoration()=Unit
        }
        model=BistroViewModel(repo,auth);viewModelStore.put("bistro",model)
        setContent {PawQuestTheme {
            CompositionLocalProvider(LocalPetController provides PetController(PetState(snapshot=snapshot,loading=false),{},{})) {
                Column(Modifier.fillMaxSize().padding(12.dp)) {
                    Text("Bistro fixture · no real Coins used")
                    BistroContent(model,Modifier.weight(1f))
                }
            }
        }}
    }
}

class BistroDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val ui=instrumentation.uiAutomation
    private fun web(root: View): BistroWebView? {
        if(root is BistroWebView)return root
        if(root is ViewGroup) for(i in 0 until root.childCount)web(root.getChildAt(i))?.let {return it}
        return null
    }
    private fun evaluate(activity: BistroFixtureActivity,script: String): String {
        val done=CountDownLatch(1);var result="null"
        instrumentation.runOnMainSync {web(activity.window.decorView)?.evaluateJavascript(script) {result=it;done.countDown()} ?: done.countDown()}
        // Reload destroys the old JS context and may discard its pending callback.
        // Let await() retry against the new document rather than fail that race.
        return if(done.await(2,TimeUnit.SECONDS)) result else "null"
    }
    private fun await(check: ()->Boolean) {val end=SystemClock.uptimeMillis()+20_000;while(!check() && SystemClock.uptimeMillis()<end)SystemClock.sleep(50);assertTrue("Bistro state",check())}
    private fun fixture(species: String="puppy",block: (BistroFixtureActivity)->Unit) {
        val old=AppLocale.language;AppLocale.select(AppLanguage.ENGLISH)
        val activity=instrumentation.startActivitySync(Intent(instrumentation.context,BistroFixtureActivity::class.java)
            .putExtra("species",species).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as BistroFixtureActivity
        try {await {evaluate(activity,"Boolean(window.pawquestBistroReady&&document.querySelectorAll('button[data-item]').length===12)")=="true"};block(activity)}
        finally {instrumentation.runOnMainSync {activity.finish()};AppLocale.select(old)}
    }
    private fun find(node: AccessibilityNodeInfo?,text: String): AccessibilityNodeInfo? {
        if(node==null)return null
        if(node.text?.toString()==text)return node
        for(i in 0 until node.childCount)find(node.getChild(i),text)?.let {return it}
        return null
    }
    private fun click(text: String) {
        await {find(ui.rootInActiveWindow,text)!=null}
        var node=find(ui.rootInActiveWindow,text)
        while(node!=null && !node.isClickable)node=node.parent
        assertTrue("Click $text",node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true)
    }
    @Test fun allTwelveMealsUseExactSvgAndTimingOnAllFourPets() {
        for(species in listOf("prototype_cat","puppy","rabbit","capybara")) fixture(species) {activity ->
            assertEquals("0",evaluate(activity,"document.getElementById('stage-food-slot').childElementCount"))
            assertEquals("true",evaluate(activity,"document.documentElement.scrollWidth<=innerWidth+1 && !!document.getElementById('pet-head-rig')"))
            evaluate(activity,"window.__oldTimer=setTimeout;window.__oldClear=clearTimeout;window.setTimeout=(fn,ms)=>{window.__nextTimer={fn,ms};return 1};window.clearTimeout=()=>{};true")
            activity.menu.forEachIndexed {i,item ->
                val tx="bf290000-0000-0000-0000-${(i+1).toString().padStart(12,'0')}"
                val result=JSONObject().put("transactionId",tx).put("itemId",item.itemId).put("assetId",item.assetId).put("type",item.type).put("name",item.name).put("hunger",item.hunger).put("happiness",item.happiness).put("xp",item.xp)
                assertEquals("true",evaluate(activity,"window.PawQuestBistro.play($result)"))
                assertEquals(if(item.type=="drink") "drinking" else "eating",JSONTokener(evaluate(activity,"window.PawQuestBistro.getState()")).nextValue())
                assertEquals(item.assetId,JSONTokener(evaluate(activity,"document.querySelector('#stage-food-slot use').getAttribute('href').slice(1)")).nextValue())
                assertEquals("3200",evaluate(activity,"window.__nextTimer.ms"))
                evaluate(activity,"window.__nextTimer.fn();true")
                if(item.type=="food") {
                    assertEquals("\"happy\"",evaluate(activity,"window.PawQuestBistro.getState()"))
                    assertEquals("2000",evaluate(activity,"window.__nextTimer.ms"));evaluate(activity,"window.__nextTimer.fn();true")
                }
                assertEquals("\"complete\"",evaluate(activity,"window.PawQuestBistro.getState()"))
            }
            assertEquals(0,activity.calls)
            evaluate(activity,"window.setTimeout=window.__oldTimer;window.clearTimeout=window.__oldClear;true")
        }
    }
    @Test fun nativeConfirmationCallsBackendOnceAndSkipReloadNeverPurchasesAgain()=fixture {activity ->
        evaluate(activity,"document.querySelector('button[data-item]').click();true")
        await {find(ui.rootInActiveWindow,"Buy & serve")!=null};assertEquals(0,activity.calls)
        click("Buy & serve")
        await {activity.model.state.value.receipt!=null}
        assertEquals(1,activity.calls);assertEquals(990L,activity.coins);assertEquals(5L,activity.xp)
        if(activity.model.state.value.animationPending) click("Skip Animation")
        await {activity.model.state.value.pending==null}
        instrumentation.runOnMainSync {web(activity.window.decorView)!!.reload()}
        await {evaluate(activity,"Boolean(window.pawquestBistroReady&&document.querySelectorAll('button[data-item]').length===12)")=="true"}
        assertEquals(1,activity.calls);assertEquals(990L,activity.coins);assertEquals(5L,activity.xp)
    }
}
