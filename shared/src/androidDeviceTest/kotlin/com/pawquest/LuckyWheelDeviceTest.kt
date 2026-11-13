package com.pawquest

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.platform.app.InstrumentationRegistry
import com.pawquest.domain.model.*
import com.pawquest.presentation.gacha.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Test

/** Test-only activity: real packaged WebView, no Supabase client or economic operations. */
class LuckyWheelFixtureActivity : ComponentActivity() {
    internal lateinit var web: LuckyWheelWebView
    val ready=CountDownLatch(1)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web=LuckyWheelWebView(this);web.onLoad={if(it==LuckyWheelLoad.Ready) ready.countDown()}
        setContentView(FrameLayout(this).also {it.addView(web,FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,900))})
    }
    override fun onDestroy() {web.release();super.onDestroy()}
}

class LuckyWheelDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun renderer(block: (LuckyWheelWebView)->Unit) {
        val activity=instrumentation.startActivitySync(Intent(instrumentation.context,LuckyWheelFixtureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LuckyWheelFixtureActivity
        try {assertTrue("Wheel asset ready",activity.ready.await(15,TimeUnit.SECONDS));block(activity.web)}
        finally {instrumentation.runOnMainSync {activity.finish()}}
    }
    private fun evaluate(web: LuckyWheelWebView,script: String): String {
        val done=CountDownLatch(1);var result=""
        instrumentation.runOnMainSync {web.evaluateJavascript(script) {result=it;done.countDown()}}
        assertTrue(done.await(5,TimeUnit.SECONDS));return result
    }
    private fun init(web: LuckyWheelWebView,reduced: Boolean=false) {
        assertEquals("true",evaluate(web,"window.PawQuestLuckyWheel.reset();window.PawQuestLuckyWheel.initialize({version:1,playbackId:'device',reducedMotion:$reduced})"))
    }
    @Test fun allEightSectorsCenterAndStayInsidePortraitViewportOffline()=renderer {web ->
        for(width in listOf(320,360,393)) {
            val density=web.resources.displayMetrics.density
            instrumentation.runOnMainSync {web.layout(0,0,(width*density).toInt(),(330*density).toInt())}
            android.os.SystemClock.sleep(150)
            for((i,sector) in WheelSector.entries.withIndex()) {
                init(web)
                val actual=listOf("FOOD","COINS","PET_SHARDS","PET_SHARDS","TICKET","PET_SHARDS","PET_SHARDS","PET_UNLOCK_CONVERTED_TO_SHARDS")[i]
                assertEquals("true",evaluate(web,"window.PawQuestLuckyWheel.play({transactionId:'$width-$i',sectorCode:'${sector.name}',selectedPetId:null,actualReward:'$actual',petShardsEarned:50})"))
                assertEquals("true",evaluate(web,"window.PawQuestLuckyWheel.skip()"))
                val data=JSONObject(JSONTokener(evaluate(web,"JSON.stringify({angle:parseFloat(document.getElementById('lucky-wheel-rotor').style.transform.slice(7)),overflow:document.documentElement.scrollWidth>innerWidth,svg:document.getElementById('lucky-wheel-svg').getBoundingClientRect().toJSON()})")).nextValue() as String)
                assertEquals(((360-i*45)%360).toDouble(),data.getDouble("angle"),.01)
                assertFalse(data.getBoolean("overflow"));assertTrue(data.getJSONObject("svg").getDouble("width")<=width+1)
                assertTrue("SVG must have visible height: $data",data.getJSONObject("svg").getDouble("height")>100)
            }
        }
        instrumentation.runOnMainSync {
            assertFalse(web.settings.allowFileAccess);assertFalse(web.settings.allowContentAccess);assertTrue(web.settings.blockNetworkLoads)
        }
        assertEquals("true",evaluate(web,"typeof window.Android === 'undefined'"))
        assertEquals("true",evaluate(web,"typeof window.playerResources === 'undefined'"))
        assertEquals("true",evaluate(web,"typeof window.rollWeightedSlotIndex === 'undefined'"))
    }
    @Test fun authoritativePetCardsSkipFallbackAndReducedMotion()=renderer {web ->
        for((code,alias) in listOf("rabbit" to "bunny","puppy" to "dog","capybara" to "capy")) {
            init(web)
            evaluate(web,"window.PawQuestLuckyWheel.play({transactionId:'$code',sectorCode:'PET_UNLOCK',selectedPetId:'$code',actualReward:'PET_UNLOCKED'});window.PawQuestLuckyWheel.skip()")
            assertEquals("true",evaluate(web,"document.getElementById('roulette-card-$alias').classList.contains('roulette-active-card')"))
        }
        init(web,true)
        evaluate(web,"window.PawQuestLuckyWheel.play({transactionId:'fallback',sectorCode:'PET_UNLOCK',selectedPetId:null,actualReward:'PET_UNLOCK_CONVERTED_TO_SHARDS',petShardsEarned:50})")
        assertEquals("true",evaluate(web,"document.getElementById('jackpot-final-message').textContent.includes('+50 Pet Shards')"))
        assertEquals("false",evaluate(web,"['bunny','dog','capy'].some(k=>document.getElementById('roulette-card-'+k).classList.contains('roulette-active-card'))"))
    }
    @Test fun trustedCompletionAndInterruptionOnlyNotifyConfirmedTransactionOnce()=renderer {web ->
        val id="ab280000-0000-0000-0000-000000000001"
        val receipt=LuckyWheelReceipt(id,id,LuckyWheelViewModel.BANNER,id,1,WheelSector.PET_UNLOCK,WheelPayment.COINS,100,0,
            WheelReward.PET_UNLOCKED,null,0,"puppy",0,0,0,900,100,1,"2026-11-10T10:00:00Z")
        var completions=0;val started=CountDownLatch(1);val finished=CountDownLatch(1)
        instrumentation.runOnMainSync {web.present(LuckyWheelAnimation(null,receipt,false,{started.countDown()},{completions++;finished.countDown()}))}
        assertTrue(started.await(5,TimeUnit.SECONDS))
        instrumentation.runOnMainSync {
            web.acceptAnimationEvent("{}","https://attacker.invalid",true)
            web.acceptAnimationEvent("{}",LuckyWheelWebView.ORIGIN,false)
        }
        assertEquals(0,completions)
        assertTrue("Native callback waits for real two-stage animation",finished.await(12,TimeUnit.SECONDS))
        assertEquals(1,completions)
        assertEquals("true",evaluate(web,"document.getElementById('roulette-card-dog').classList.contains('roulette-active-card')"))
        instrumentation.runOnMainSync {web.pausePreview();web.resumePreview()}
        assertEquals(1,completions)
    }
    @Test fun pauseAndReloadKeepConfirmedIdentityWithoutRepeatedCompletion()=renderer {web ->
        val id="ab280000-0000-0000-0000-000000000003"
        val receipt=LuckyWheelReceipt(id,id,LuckyWheelViewModel.BANNER,id,1,WheelSector.PET_UNLOCK,WheelPayment.TICKET,0,1,
            WheelReward.PET_UNLOCKED,null,0,"rabbit",0,0,0,1000,100,0,"2026-11-10T10:00:00Z")
        val started=CountDownLatch(1);var completions=0
        val animation=LuckyWheelAnimation(null,receipt,false,{started.countDown()},{completions++})
        instrumentation.runOnMainSync {web.present(animation)}
        assertTrue(started.await(5,TimeUnit.SECONDS))
        instrumentation.runOnMainSync {web.pausePreview();web.resumePreview()}
        assertEquals(1,completions)
        val ready=CountDownLatch(1)
        instrumentation.runOnMainSync {web.onLoad={if(it==LuckyWheelLoad.Ready)ready.countDown()};web.reload()}
        assertTrue(ready.await(10,TimeUnit.SECONDS))
        instrumentation.runOnMainSync {web.present(animation.copy(skipRequested=true))}
        assertEquals("true",evaluate(web,"document.getElementById('roulette-card-bunny').classList.contains('roulette-active-card')"))
        assertEquals(1,completions)
    }
}
