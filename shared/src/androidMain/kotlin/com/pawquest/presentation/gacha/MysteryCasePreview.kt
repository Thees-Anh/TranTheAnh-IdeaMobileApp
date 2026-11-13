package com.pawquest.presentation.gacha

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import android.provider.Settings
import kotlinx.serialization.json.*
import kotlin.uuid.Uuid
import com.pawquest.presentation.components.PawQuestError
import com.pawquest.presentation.components.PawQuestLoading
import java.io.ByteArrayInputStream
import kotlinx.coroutines.delay

internal enum class CasePreviewLoad { Loading, Ready, Failed }

/** Local presentation only. No credentials, purchase or reward API. */
@SuppressLint("SetJavaScriptEnabled")
internal class MysteryCaseWebView(context: Context) : WebView(context) {
    var onLoad: (CasePreviewLoad)->Unit = {}
    var onAnimationFinished: (String)->Unit = {}
    private val playbackId=Uuid.random().toString()
    private var transactionId: String?=null
    private var winningItemId: String?=null
    private var completed=false
    private var lastNotifiedId: String?=null
    private var pageReady=false
    private var animation: ItemCaseAnimation?=null
    private val messagesSupported=WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
    private var released=false
    private var paused=false
    private var started=false
    private val loader=WebViewAssetLoader.Builder()
        .addPathHandler("/assets/",WebViewAssetLoader.AssetsPathHandler(context.applicationContext)).build()
    companion object {
        const val HOME="https://appassets.androidplatform.net/assets/gacha/item_case_opening.html"
        const val ORIGIN="https://appassets.androidplatform.net"
        private val paths=setOf("/assets/gacha/item_case_opening.html","/assets/gacha/item_case.css","/assets/gacha/quicksand.ttf")
        fun allowed(uri: Uri)=uri.scheme=="https" && uri.authority=="appassets.androidplatform.net" && uri.path in paths && uri.query==null
        fun blocked()=WebResourceResponse("text/plain","UTF-8",403,"Blocked",emptyMap(),ByteArrayInputStream(ByteArray(0)))
    }
    init {
        contentDescription="Mystery Case preview"
        isVerticalScrollBarEnabled=false; isHorizontalScrollBarEnabled=false
        settings.apply {
            javaScriptEnabled=true
            allowFileAccess=false; allowContentAccess=false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs=false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs=false
            blockNetworkLoads=true; blockNetworkImage=true
            domStorageEnabled=false
            javaScriptCanOpenWindowsAutomatically=false
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(true); setSupportZoom(false)
            mediaPlaybackRequiresUserGesture=true
            cacheMode=WebSettings.LOAD_NO_CACHE
            setGeolocationEnabled(false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this,false)
        if(messagesSupported) WebViewCompat.addWebMessageListener(this,"PawQuestCaseEvents",setOf(ORIGIN)) { _,message,origin,mainFrame,_ ->
            acceptAnimationEvent(runCatching { message.data }.getOrNull(),origin.toString(),mainFrame)
        }
        webChromeClient=object : WebChromeClient() {
            override fun onCreateWindow(view: WebView?,dialog: Boolean,gesture: Boolean,result: android.os.Message?)=false
            override fun onPermissionRequest(request: PermissionRequest?) { request?.deny() }
            override fun onGeolocationPermissionsShowPrompt(origin: String?,callback: GeolocationPermissions.Callback?) { callback?.invoke(origin,false,false) }
            override fun onShowFileChooser(view: WebView?,callback: android.webkit.ValueCallback<Array<Uri>>?,params: FileChooserParams?)=false
            override fun onJsAlert(view: WebView?,url: String?,message: String?,result: JsResult?): Boolean { result?.cancel(); return true }
        }
        webViewClient=object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?,request: WebResourceRequest?)=true
            override fun shouldInterceptRequest(view: WebView?,request: WebResourceRequest?): WebResourceResponse {
                val uri=request?.url ?: return blocked()
                return if(allowed(uri)) loader.shouldInterceptRequest(uri) ?: blocked() else blocked()
            }
            override fun onPageStarted(view: WebView?,url: String?,favicon: Bitmap?) {
                if(!released) {
                    pageReady=false; interruptAnimation(); transactionId=null; winningItemId=null
                    onLoad(CasePreviewLoad.Loading)
                }
            }
            override fun onPageFinished(view: WebView?,url: String?) {
                if(released || url!=HOME) return
                evaluateJavascript("Boolean(window.pawquestCasePreviewReady)") { result ->
                    if(!released) {
                        pageReady=result=="true"
                        onLoad(if(pageReady) CasePreviewLoad.Ready else CasePreviewLoad.Failed)
                        applyPause(); animation?.let(::present)
                    }
                }
            }
            override fun onReceivedError(view: WebView?,request: WebResourceRequest?,error: WebResourceError?) {
                if(!released && request?.isForMainFrame==true) onLoad(CasePreviewLoad.Failed)
            }
            override fun onReceivedHttpError(view: WebView?,request: WebResourceRequest?,response: WebResourceResponse?) {
                if(!released && allowed(request?.url ?: Uri.EMPTY)) onLoad(CasePreviewLoad.Failed)
            }
            override fun onRenderProcessGone(view: WebView?,detail: RenderProcessGoneDetail?): Boolean {
                if(!released) { onLoad(CasePreviewLoad.Failed); release() }
                return true
            }
        }
    }
    /** UI notifications only. Identity/origin checks cannot authorize any grant. */
    internal fun acceptAnimationEvent(data: String?,origin: String,mainFrame: Boolean) {
        if(released || !mainFrame || origin!=ORIGIN || data==null || data.length>4096 || url!=HOME) return
        val event=runCatching { Json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return
        fun field(name: String)=event[name]?.jsonPrimitive?.contentOrNull
        runCatching {
            if(field("version")=="1" && field("playbackId")==playbackId && field("transactionId")==transactionId &&
                field("winningItemId")==winningItemId && field("event") in setOf("complete","error")) interruptAnimation()
        }
    }
    private fun interruptAnimation() {
        val id=transactionId ?: return
        if(!completed) {
            completed=true
            if(lastNotifiedId!=id) { lastNotifiedId=id; onAnimationFinished(id) }
        }
    }
    fun present(value: ItemCaseAnimation) {
        animation=value
        onAnimationFinished=value.onFinished
        if(released || !pageReady) return
        val receipt=value.receipt
        if(transactionId==receipt.transactionId) {
            if(value.skipRequested) evaluateJavascript("window.PawQuestCase.skip()",null)
            return
        }
        transactionId=receipt.transactionId; winningItemId=receipt.winningItemId; completed=false
        if(!messagesSupported || paused) { interruptAnimation(); return }
        val metadata=buildJsonArray {
            value.rewards.filter { it.itemId!=receipt.winningItemId }.forEach { reward -> add(buildJsonObject {
                put("itemId",reward.itemId); put("assetId",reward.assetId); put("name",value.itemNames[reward.itemId] ?: reward.itemCode); put("rarity",reward.rarity.name.lowercase())
            }) }
            // Always include the immutable receipt identity, even after a banner changes.
            add(buildJsonObject { put("itemId",receipt.winningItemId); put("assetId",receipt.assetId)
                put("name",receipt.itemName); put("rarity",receipt.rarity.name.lowercase()) })
        }
        val config=buildJsonObject {
            put("version",1); put("playbackId",playbackId); put("items",metadata)
            put("reducedMotion",Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f)
        }
        val result=buildJsonObject { put("transactionId",receipt.transactionId); put("winningItemId",receipt.winningItemId) }
        // JSON is data, never raw server text interpolated into executable code.
        val command="window.PawQuestCase.reset();window.PawQuestCase.initialize($config)&&window.PawQuestCase.play($result)"
        evaluateJavascript(command) { accepted ->
            if(!released && transactionId==receipt.transactionId) {
                if(accepted!="true") interruptAnimation()
                else if(!completed) {
                    value.onStarted(receipt.transactionId)
                    if(value.skipRequested) evaluateJavascript("window.PawQuestCase.skip()",null)
                }
            }
        }
    }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        // Lazy layouts can initially measure this view at zero. Loading then can
        // leave Chromium's viewport units at zero even after the view is sized.
        if(!started && !released && w>0 && h>0) { started=true; loadUrl(HOME) }
    }
    private fun applyPause() { if(!released) evaluateJavascript("window.pawquestCasePreviewPause?.(${if(paused) "true" else "false"});",null) }
    fun resumePreview() {
        if(!released) {
            paused=false; onResume(); applyPause()
            // Native interruption may have revealed the receipt while Chromium
            // was suspended before its queued pause/skip command could run.
            if(completed) evaluateJavascript("window.PawQuestCase.skip()",null)
        }
    }
    fun pausePreview() { if(!released) { paused=true; applyPause(); interruptAnimation(); onPause() } }
    fun release() {
        if(released) return
        interruptAnimation()
        released=true; onLoad={}; onAnimationFinished={}; animation=null
        stopLoading(); onPause(); removeAllViews(); destroy()
    }
}

@Composable
fun MysteryCasePreview(modifier: Modifier=Modifier,animation: ItemCaseAnimation?=null) {
    val context=LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember(attempt) { mutableStateOf(CasePreviewLoad.Loading) }
    var view by remember(attempt) { mutableStateOf<MysteryCaseWebView?>(null) }
    val currentAnimation by rememberUpdatedState(animation)
    val lifecycleView=view
    LifecycleResumeEffect(lifecycleView) { lifecycleView?.resumePreview(); onPauseOrDispose { lifecycleView?.pausePreview() } }
    LaunchedEffect(attempt,state) {
        if(state==CasePreviewLoad.Loading) { delay(10_000); state=CasePreviewLoad.Failed }
        if(state==CasePreviewLoad.Failed) currentAnimation?.let { it.onFinished(it.receipt.transactionId) }
    }
    // A stalled renderer cannot leave a committed receipt trapped in Animating.
    LaunchedEffect(animation?.receipt?.transactionId) {
        if(animation!=null) { delay(15_000); currentAnimation?.let { it.onFinished(it.receipt.transactionId) } }
    }
    Box(modifier) {
        if(state!=CasePreviewLoad.Failed) key(attempt) {
            AndroidView(factory={ MysteryCaseWebView(context).also { native -> native.onLoad={state=it}; view=native } },
                update={ native -> animation?.let(native::present) },
                modifier=Modifier.matchParentSize(),onRelease={ it.release(); if(view===it) view=null })
        }
        when(state) {
            CasePreviewLoad.Loading -> PawQuestLoading(message="Loading case artwork...")
            CasePreviewLoad.Failed -> PawQuestError(message="Case artwork couldn't load.",onRetry={attempt++})
            CasePreviewLoad.Ready -> Unit
        }
    }
}
