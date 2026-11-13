package com.pawquest.presentation.scene

import com.pawquest.localization.tr
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.*
import java.io.ByteArrayInputStream
import org.json.JSONObject
import org.json.JSONArray

/** Offline SVG renderer only. Compose and shared ViewModels own every game interaction. */
@SuppressLint("SetJavaScriptEnabled")
internal class PrototypePetView(context: Context,private val html: String) : WebView(context) {
    var onPetTouch: () -> Unit = {}
    var onReady: () -> Unit = {}
    private var ready=false
    private var payload="{}"
    private var lastState: String?=null
    private var lastEquipment: List<String> = emptyList()
    private var lastRoom: List<String> = emptyList()
    private var lastViewport: List<Int>?=null
    private val resizeTask=Runnable {resizeIllustration()}
    private var released=false
    private var paused=false
    private var topInset=0
    private var bottomInset=0
    init {
        if(context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) setWebContentsDebuggingEnabled(true)
        setBackgroundColor(Color.TRANSPARENT)
        isVerticalScrollBarEnabled=false;isHorizontalScrollBarEnabled=false
        settings.apply {
            javaScriptEnabled=true // Local state projection + SVG part animations, never backend calls.
            allowFileAccess=false;allowContentAccess=false;blockNetworkLoads=true
            domStorageEnabled=false;javaScriptCanOpenWindowsAutomatically=false
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false);setSupportZoom(false)
        }
        contentDescription=tr("Animated pet illustration")
        webViewClient=object : WebViewClient() {
            override fun onPageFinished(view: WebView?,url: String?) {
                if(!released) {
                    ready=true;lastViewport=null
                    // One stylesheet, one class toggle; do not write inline style
                    // to every SVG/decor node on each lifecycle transition.
                    evaluateJavascript("(function(){if(!document.getElementById('pawquest-motion-style')){const s=document.createElement('style');s.id='pawquest-motion-style';s.textContent='.pawquest-paused,.pawquest-paused *{animation-play-state:paused!important}';document.head.appendChild(s)}})();",null)
                    resizeIllustration();render();applyPause();onReady()
                }
            }
            override fun shouldOverrideUrlLoading(view: WebView?,request: WebResourceRequest?): Boolean {
                if(request?.url.toString()=="pawquest-pet://touch") onPetTouch()
                return true // Deny all navigation; no external pages or deep-link forwarding.
            }
            override fun shouldInterceptRequest(view: WebView?,request: WebResourceRequest?): WebResourceResponse =
                WebResourceResponse("text/plain","UTF-8",ByteArrayInputStream(ByteArray(0)))
        }
        loadDataWithBaseURL("https://pawquest.invalid/pet/",html,"text/html","UTF-8",null)
    }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        if(ready && !released) {removeCallbacks(resizeTask);post(resizeTask)}
    }
    private fun resizeIllustration() {
        if(ready && !released && width>0 && height>0) {
            val viewport=listOf(width,height,topInset,bottomInset)
            if(viewport==lastViewport) return
            lastViewport=viewport
            evaluateJavascript("window.pawquestResize?.($width,$height,$topInset,$bottomInset);",null)
        }
    }
    fun setSafeInsets(top: Int,bottom: Int) {
        if(topInset==top && bottomInset==bottom) return
        topInset=top.coerceAtLeast(0);bottomInset=bottom.coerceAtLeast(0);resizeIllustration()
    }
    fun update(state: String,equipment: List<String>,room: List<String>) {
        if(released || state==lastState && equipment==lastEquipment && room==lastRoom) return
        lastState=state;lastEquipment=equipment.toList();lastRoom=room.toList()
        val next=JSONObject().put("state",state).put("equipment",JSONArray(equipment)).put("room",JSONArray(room)).toString()
        if(next!=payload) {payload=next;render()}
    }
    private fun render() {
        if(ready && !released) evaluateJavascript("window.pawquestRender($payload);",null)
    }
    fun resumeIllustration() { if(!released && paused) {paused=false;onResume();applyPause()} }
    fun pauseIllustration() { if(!released && !paused) {paused=true;applyPause();onPause()} }
    private fun applyPause() {
        if(ready && !released) evaluateJavascript("document.documentElement.classList.toggle('pawquest-paused',$paused);",null)
    }
    fun release() {
        if(released) return
        released=true;removeCallbacks(resizeTask);onPetTouch={};onReady={};stopLoading();onPause();removeAllViews();destroy()
    }
}
