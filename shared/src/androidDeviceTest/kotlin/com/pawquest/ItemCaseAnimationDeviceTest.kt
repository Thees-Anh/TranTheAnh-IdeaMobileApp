package com.pawquest

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.platform.app.InstrumentationRegistry
import com.pawquest.domain.model.*
import com.pawquest.presentation.gacha.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Instant

/** Test APK only. The real local renderer, no Supabase client or account. */
class CaseAnimationFixtureActivity : ComponentActivity() {
    internal lateinit var web: MysteryCaseWebView
    val ready=CountDownLatch(1)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web=MysteryCaseWebView(this)
        web.onLoad={ if(it==CasePreviewLoad.Ready) ready.countDown() }
        setContentView(FrameLayout(this).also { it.addView(web,FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,840)) })
    }
    override fun onDestroy() { web.release(); super.onDestroy() }
}

class ItemCaseAnimationDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val transaction="fb280000-0000-0000-0000-000000000001"
    private val playback="fb290000-0000-0000-0000-000000000001"
    private val unknown="fb290000-0000-0000-0000-000000000099"
    private fun withRenderer(block: (CaseAnimationFixtureActivity)->Unit) {
        val activity=instrumentation.startActivitySync(Intent(instrumentation.context,CaseAnimationFixtureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CaseAnimationFixtureActivity
        try { assertTrue(activity.ready.await(15,TimeUnit.SECONDS)); block(activity) }
        finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    private fun evaluate(web: MysteryCaseWebView,script: String): String {
        val done=CountDownLatch(1); var value=""
        instrumentation.runOnMainSync { web.evaluateJavascript(script) { value=it; done.countDown() } }
        assertTrue(done.await(5,TimeUnit.SECONDS)); return value
    }
    private fun snapshot(web: MysteryCaseWebView)=JSONObject(JSONTokener(evaluate(web,"JSON.stringify(window.PawQuestCase.status())")).nextValue() as String)
    private fun config(items: JSONArray,fast: Boolean=false,reduced: Boolean=false)=JSONObject()
        .put("version",1).put("playbackId",playback).put("items",items).put("fast",fast).put("reducedMotion",reduced)
    private fun result(id: String)=JSONObject().put("transactionId",transaction).put("winningItemId",id)
    private fun seededItems()=JSONArray(instrumentation.context.assets.open("gacha/seeded_rewards.json").bufferedReader().use { it.readText() })
    private fun assertCentered(web: MysteryCaseWebView,id: String) {
        assertEquals("complete",snapshot(web).getString("phase"))
        assertEquals(id,snapshot(web).getString("winningItemId"))
        val script="""JSON.stringify((()=>{const card=document.querySelector('#reel-card-52');const r=card.getBoundingClientRect();const p=document.querySelector('.cs2-center-laser').getBoundingClientRect();return {id:card.dataset.itemId,error:Math.abs(r.left+r.width/2-p.left-p.width/2),overflow:document.documentElement.scrollWidth>document.documentElement.clientWidth}})())"""
        val data=JSONObject(JSONTokener(evaluate(web,script)).nextValue() as String)
        assertEquals(id,data.getString("id")); assertTrue(data.toString(),data.getDouble("error")<1.5)
        assertFalse(data.getBoolean("overflow"))
    }
    @Test fun everySeededWinnerCentersAtPortraitWidthsAndRepeatedPlayIsIdempotent()=withRenderer { activity ->
        val web=activity.web; val items=seededItems(); assertEquals(20,items.length())
        for(width in listOf(320,360,393)) {
            instrumentation.runOnMainSync {
                val density=activity.resources.displayMetrics.density
                web.layoutParams=FrameLayout.LayoutParams((width*density).toInt(),(320*density).toInt())
            }
            instrumentation.waitForIdleSync(); SystemClock.sleep(100)
            for(i in 0 until items.length()) {
                val id=items.getJSONObject(i).getString("itemId")
                assertEquals("true",evaluate(web,"PawQuestCase.reset();PawQuestCase.initialize(${config(items)})&&PawQuestCase.play(${result(id)})&&PawQuestCase.skip()"))
                assertCentered(web,id)
                assertEquals("true",evaluate(web,"PawQuestCase.play(${result(id)})"))
                assertCentered(web,id)
            }
        }
    }
    @Test fun originalNormalAndFastEasingFinishOnConfirmedItem()=withRenderer { activity ->
        val web=activity.web; val items=seededItems(); val id=items.getJSONObject(6).getString("itemId")
        for(fast in listOf(false,true)) {
            assertEquals("true",evaluate(web,"PawQuestCase.reset();PawQuestCase.initialize(${config(items,fast)})&&PawQuestCase.play(${result(id)})"))
            val deadline=SystemClock.uptimeMillis()+9000
            var rolling=false
            var easing=false
            var lastStyle=""
            while(snapshot(web).getString("phase")!="complete" && SystemClock.uptimeMillis()<deadline) {
                val data=JSONObject(JSONTokener(evaluate(web,"JSON.stringify({phase:PawQuestCase.status().phase,inline:document.getElementById('cs2-reel-track').style.transition,easing:getComputedStyle(document.getElementById('cs2-reel-track')).transitionTimingFunction})")).nextValue() as String)
                lastStyle=data.toString()
                if(data.getString("phase")=="rolling") {
                    rolling=true
                    easing=easing || data.getString("easing").contains("0.82")
                }
                SystemClock.sleep(100)
            }
            assertTrue("Exercise the actual scrolling phase",rolling)
            assertTrue("Original easing must be applied: $lastStyle",easing)
            assertCentered(web,id)
        }
    }
    @Test fun invalidIdsMissingArtworkReducedMotionAndEscapedNamesAreSafe()=withRenderer { activity ->
        val web=activity.web; val items=seededItems()
        assertEquals("true",evaluate(web,"PawQuestCase.initialize(${config(items)})"))
        assertEquals("false",evaluate(web,"PawQuestCase.play(${result(unknown)})"))
        assertEquals("idle",snapshot(web).getString("phase"))
        val malicious="<img src=x onerror=window.compromised=true>\"'\\"
        val missing=JSONArray().put(JSONObject().put("itemId",unknown).put("assetId","missing-thumbnail").put("name",malicious).put("rarity","rare"))
        assertEquals("true",evaluate(web,"PawQuestCase.reset();PawQuestCase.initialize(${config(missing,reduced=true)})&&PawQuestCase.play(${result(unknown)})"))
        assertCentered(web,unknown)
        assertEquals("null",evaluate(web,"document.querySelector('img')"))
        assertEquals("\"undefined\"",evaluate(web,"typeof window.compromised"))
        val name=JSONTokener(evaluate(web,"document.querySelector('#reel-card-52 .line-clamp-2').textContent.trim()")).nextValue()
        assertEquals(malicious,name)
    }
    @Test fun nativeDuplicatePlaybackRejectsForgedMessagesAndReloadNeverRepostsEconomy()=withRenderer { activity ->
        val web=activity.web
        val winner=seededItems().getJSONObject(6)
        val id=winner.getString("itemId")
        val receipt=ItemCaseReceipt(transaction,"fb270000-0000-0000-0000-000000000001",ItemCaseIds.DEFAULT_BANNER,
            "e2400000-0000-0000-0000-000000000002",1,id,winner.getString("name"),"acc_glasses",winner.getString("assetId"),
            ItemRarity.RARE,100,ItemCaseRewardOutcome.DUPLICATE_CONVERTED,0,10,900,85,Instant.parse("2026-11-10T05:00:00Z"))
        val done=CountDownLatch(1); var completions=0
        val animation=ItemCaseAnimation(receipt,emptyList(),false,{}, { assertEquals(transaction,it);completions++;done.countDown() })
        instrumentation.runOnMainSync { web.present(animation) }
        evaluate(web,"true") // Drain presentation commands.
        instrumentation.runOnMainSync {
            for(origin in listOf("https://evil.invalid",MysteryCaseWebView.ORIGIN)) {
                web.acceptAnimationEvent("{}",origin,true)
                web.acceptAnimationEvent("[]",origin,false)
                web.acceptAnimationEvent("{\"version\":1,\"transactionId\":\"$transaction\",\"event\":\"complete\",\"playbackId\":\"$playback\",\"winningItemId\":\"$id\"}",origin,true)
            }
            assertEquals(0,completions)
        }
        assertEquals(id,snapshot(web).getString("winningItemId"))
        assertEquals("true",evaluate(web,"PawQuestCase.skip()")); assertTrue(done.await(5,TimeUnit.SECONDS))
        assertCentered(web,id); assertEquals(1,completions)
        val reloaded=CountDownLatch(1)
        instrumentation.runOnMainSync {
            web.onLoad={if(it==CasePreviewLoad.Ready) reloaded.countDown()}
            web.pausePreview(); web.resumePreview(); web.present(animation.copy(skipRequested=true)); web.reload()
        }
        assertTrue(reloaded.await(10,TimeUnit.SECONDS))
        val deadline=SystemClock.uptimeMillis()+5000
        while(SystemClock.uptimeMillis()<deadline) {
            val status=runCatching { snapshot(web) }.getOrNull()
            if(status?.optString("phase")=="complete") break
            SystemClock.sleep(100)
        }
        assertCentered(web,id); assertEquals(1,completions)
        // The renderer receives display IDs only; there is no economic bridge.
        assertEquals("\"undefined\"",evaluate(web,"typeof Android"))
        assertEquals("\"undefined\"",evaluate(web,"typeof supabase"))
        assertEquals(10,receipt.itemShardsGranted); assertEquals(id,receipt.winningItemId)
    }
    @Test fun nativePauseDuringPlaybackResumesOnOriginalWinnerWithoutRepeatedCompletion()=withRenderer { activity ->
        val web=activity.web
        val winner=seededItems().getJSONObject(0); val id=winner.getString("itemId")
        val receipt=ItemCaseReceipt(transaction,"fb270000-0000-0000-0000-000000000001",ItemCaseIds.DEFAULT_BANNER,
            "e2400000-0000-0000-0000-000000000002",1,id,winner.getString("name"),"acc_sprout",winner.getString("assetId"),
            ItemRarity.COMMON,100,ItemCaseRewardOutcome.ITEM_GRANTED,1,0,900,75,Instant.parse("2026-11-10T05:00:00Z"))
        val started=CountDownLatch(1); var completions=0
        val animation=ItemCaseAnimation(receipt,emptyList(),false,{started.countDown()},{completions++})
        instrumentation.runOnMainSync {web.present(animation)}
        assertTrue(started.await(5,TimeUnit.SECONDS))
        instrumentation.runOnMainSync {web.pausePreview();web.resumePreview();web.present(animation.copy(skipRequested=true))}
        evaluate(web,"true")
        assertCentered(web,id)
        assertEquals(1,completions)
        assertEquals(ItemCaseRewardOutcome.ITEM_GRANTED,receipt.outcome)
    }
}
