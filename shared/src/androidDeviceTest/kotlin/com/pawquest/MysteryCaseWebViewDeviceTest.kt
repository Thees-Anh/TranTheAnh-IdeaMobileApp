package com.pawquest

import android.net.Uri
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import com.pawquest.presentation.gacha.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MysteryCaseWebViewDeviceTest {
    @Test fun packagedStageRendersOfflineAtPortraitWidthsWithoutPrivilegedBridge() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        lateinit var view: MysteryCaseWebView
        val ready=CountDownLatch(1)
        var loaded: CasePreviewLoad?=null
        instrumentation.runOnMainSync {
            view=MysteryCaseWebView(instrumentation.targetContext)
            view.onLoad={ loaded=it; if(it!=CasePreviewLoad.Loading) ready.countDown() }
            view.measure(View.MeasureSpec.makeMeasureSpec(960,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(840,View.MeasureSpec.EXACTLY))
            view.layout(0,0,960,840)
        }
        try {
            assertTrue("Local page must complete",ready.await(15,TimeUnit.SECONDS))
            assertEquals(CasePreviewLoad.Ready,loaded)
            fun evaluate(script: String): String {
                val done=CountDownLatch(1); var result=""
                instrumentation.runOnMainSync { view.evaluateJavascript(script) { result=it;done.countDown() } }
                assertTrue(done.await(5,TimeUnit.SECONDS)); return result
            }
            for(width in listOf(320,360,393)) {
                instrumentation.runOnMainSync {
                    val density=instrumentation.targetContext.resources.displayMetrics.density
                    val w=(width*density).toInt(); val h=(320*density).toInt()
                    view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
                    view.layout(0,0,w,h)
                }
                val raw=evaluate("JSON.stringify({ready:window.pawquestCasePreviewReady,cards:document.querySelectorAll('.cs2-reel-card').length,width:document.documentElement.clientWidth,scroll:document.documentElement.scrollWidth,gradient:getComputedStyle(document.getElementById('gacha-stage-container')).backgroundImage,privileged:typeof Android!=='undefined'||typeof supabase!=='undefined',buttons:document.querySelectorAll('button').length})")
                val data=JSONObject(org.json.JSONTokener(raw).nextValue() as String)
                assertTrue(data.getBoolean("ready")); assertEquals(65,data.getInt("cards"))
                assertTrue(data.getInt("scroll")<=data.getInt("width"))
                assertTrue(data.getString("gradient").contains("linear-gradient"))
                assertFalse(data.getBoolean("privileged")); assertEquals(0,data.getInt("buttons"))
            }
            instrumentation.runOnMainSync {
                assertTrue(view.settings.javaScriptEnabled)
                assertFalse(view.settings.allowFileAccess); assertFalse(view.settings.allowContentAccess)
                assertTrue(view.settings.blockNetworkLoads); assertFalse(view.settings.domStorageEnabled)
                view.pausePreview(); view.resumePreview()
            }
            assertEquals("true",evaluate("window.pawquestCasePreviewReady"))
        } finally { instrumentation.runOnMainSync { view.release() } }
    }
    @Test fun loaderAllowlistDeniesExternalPrivateAndTraversalPaths() {
        assertTrue(MysteryCaseWebView.allowed(Uri.parse(MysteryCaseWebView.HOME)))
        assertTrue(MysteryCaseWebView.allowed(Uri.parse("https://appassets.androidplatform.net/assets/gacha/item_case.css")))
        listOf("https://example.com/","file:///data/data/com.pawquest/files/token","content://private/token",
            "https://appassets.androidplatform.net/assets/pet/puppy_stage.html",
            "https://appassets.androidplatform.net/assets/gacha/../private.json",
            MysteryCaseWebView.HOME+"?token=anything").forEach { assertFalse(it,MysteryCaseWebView.allowed(Uri.parse(it))) }
        assertEquals(403,MysteryCaseWebView.blocked().statusCode)
    }
}
