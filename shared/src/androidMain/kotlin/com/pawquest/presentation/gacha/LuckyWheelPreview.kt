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

internal enum class LuckyWheelLoad { Loading, Ready, Failed }

/** Local presentation only. No credentials, purchase or reward API. */
@SuppressLint("SetJavaScriptEnabled")
internal class LuckyWheelWebView(context: Context) : WebView(context) {
    var onLoad: (LuckyWheelLoad)->Unit = {}
    var onAnimationFinished: (String)->Unit = {}
    private val playbackId=Uuid.random().toString()
    private var transactionId: String?=null
    private var sectorCode: String?=null
    private var selectedPetId: String?=null
    private var completed=false
    private var lastNotifiedId: String?=null
    private var pageReady=false
    private var animation: LuckyWheelAnimation?=null
    private val messagesSupported=WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
    private var released=false
    private var paused=false
    private var started=false
    private var lastPreviewConfig: String?=null
    private val loader=WebViewAssetLoader.Builder()
        .addPathHandler("/assets/",WebViewAssetLoader.AssetsPathHandler(context.applicationContext)).build()
    companion object {
        const val HOME="https://appassets.androidplatform.net/assets/gacha/lucky_wheel.html"
        const val ORIGIN="https://appassets.androidplatform.net"
        private val paths=setOf("/assets/gacha/lucky_wheel.html","/assets/gacha/lucky_wheel.css","/assets/gacha/quicksand.ttf")
        fun allowed(uri: Uri)=uri.scheme=="https" && uri.authority=="appassets.androidplatform.net" && uri.path in paths && uri.query==null
        fun blocked()=WebResourceResponse("text/plain","UTF-8",403,"Blocked",emptyMap(),ByteArrayInputStream(ByteArray(0)))
    }
    init {
        contentDescription="Lucky Wheel animation"
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
        if(messagesSupported) WebViewCompat.addWebMessageListener(this,"PawQuestWheelEvents",setOf(ORIGIN)) { _,message,origin,mainFrame,_ ->
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
                    pageReady=false;lastPreviewConfig=null; interruptAnimation(); transactionId=null; sectorCode=null
                    onLoad(LuckyWheelLoad.Loading)
                }
            }
            override fun onPageFinished(view: WebView?,url: String?) {
                if(released || url!=HOME) return
                evaluateJavascript("Boolean(window.pawquestLuckyWheelReady)") { result ->
                    if(!released) {
                        pageReady=result=="true"
                        onLoad(if(pageReady) LuckyWheelLoad.Ready else LuckyWheelLoad.Failed)
                        applyPause(); animation?.let(::present)
                    }
                }
            }
            override fun onReceivedError(view: WebView?,request: WebResourceRequest?,error: WebResourceError?) {
                if(!released && request?.isForMainFrame==true) onLoad(LuckyWheelLoad.Failed)
            }
            override fun onReceivedHttpError(view: WebView?,request: WebResourceRequest?,response: WebResourceResponse?) {
                if(!released && allowed(request?.url ?: Uri.EMPTY)) onLoad(LuckyWheelLoad.Failed)
            }
            override fun onRenderProcessGone(view: WebView?,detail: RenderProcessGoneDetail?): Boolean {
                if(!released) { onLoad(LuckyWheelLoad.Failed); release() }
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
                field("sectorCode")==sectorCode && field("selectedPetId")==selectedPetId && field("event") in setOf("complete","error")) interruptAnimation()
        }
    }
    private fun interruptAnimation() {
        val id=transactionId ?: return
        if(!completed) {
            completed=true
            if(lastNotifiedId!=id) { lastNotifiedId=id; onAnimationFinished(id) }
        }
    }
    fun present(value: LuckyWheelAnimation) {
        animation=value
        onAnimationFinished=value.onFinished
        if(released || !pageReady) return
        val receipt=value.receipt
        if(receipt!=null && transactionId==receipt.transactionId) {
            if(value.skipRequested) evaluateJavascript("window.PawQuestLuckyWheel.skip()",null)
            return
        }
        val sectors=value.configuration?.sectors?.let { rows -> buildJsonArray {
            rows.forEach { sector -> add(buildJsonObject {
                put("sectorCode",sector.sector.name);put("weight",sector.weight);put("amount",sector.amount)
            }) }
        } }
        val config=buildJsonObject {
            put("version",1);put("playbackId",playbackId);sectors?.let {put("sectors",it)}
            put("reducedMotion",Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f)
        }
        if(receipt==null) {
            if(transactionId==null && lastPreviewConfig!=config.toString()) {
                lastPreviewConfig=config.toString()
                evaluateJavascript("window.PawQuestLuckyWheel.initialize($config)",null)
            }
            return
        }
        transactionId=receipt.transactionId;sectorCode=receipt.originalSector.name;selectedPetId=receipt.selectedPetId;completed=false
        if(!messagesSupported || paused) {interruptAnimation();return}
        val result=buildJsonObject {
            put("transactionId",receipt.transactionId);put("sectorCode",receipt.originalSector.name)
            put("selectedPetId",receipt.selectedPetId?.let(::JsonPrimitive) ?: JsonNull)
            put("actualReward",receipt.actualReward.name);put("petShardsEarned",receipt.petShardsEarned)
        }
        evaluateJavascript("window.PawQuestLuckyWheel.reset();window.PawQuestLuckyWheel.initialize($config)&&window.PawQuestLuckyWheel.play($result)") {accepted ->
            if(!released && transactionId==receipt.transactionId) {
                if(accepted!="true") interruptAnimation()
                else if(!completed) {
                    value.onStarted(receipt.transactionId)
                    if(value.skipRequested) evaluateJavascript("window.PawQuestLuckyWheel.skip()",null)
                }
            }
        }
    }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        // Lazy layouts can initially measure this view at zero. Loading then can
        // leave Chromium's viewport units at zero even after the view is sized.
        if(!started && !released && w>0 && h>0) { started=true; loadUrl(HOME) }
        else if(!released && pageReady) evaluateJavascript("window.PawQuestLuckyWheel.resize()",null)
    }
    private fun applyPause() { if(!released) if(paused) evaluateJavascript("window.PawQuestLuckyWheel.pause()",null) }
    fun resumePreview() {
        if(!released) {
            paused=false; onResume(); applyPause()
            // Native interruption may have revealed the receipt while Chromium
            // was suspended before its queued pause/skip command could run.
            if(completed) evaluateJavascript("window.PawQuestLuckyWheel.skip()",null)
        }
    }
    fun pausePreview() { if(!released) { paused=true; applyPause(); interruptAnimation(); onPause() } }
    fun release() {
        if(released) return
        interruptAnimation()
        released=true; onLoad={}; onAnimationFinished={}; animation=null
        evaluateJavascript("window.PawQuestLuckyWheel.dispose()",null);stopLoading(); onPause(); removeAllViews(); destroy()
    }
}

@Composable
fun LuckyWheelPreview(modifier: Modifier=Modifier,animation: LuckyWheelAnimation?=null) {
    val context=LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember(attempt) { mutableStateOf(LuckyWheelLoad.Loading) }
    var view by remember(attempt) { mutableStateOf<LuckyWheelWebView?>(null) }
    val currentAnimation by rememberUpdatedState(animation)
    val lifecycleView=view
    LifecycleResumeEffect(lifecycleView) { lifecycleView?.resumePreview(); onPauseOrDispose { lifecycleView?.pausePreview() } }
    LaunchedEffect(attempt,state) {
        if(state==LuckyWheelLoad.Loading) { delay(10_000); state=LuckyWheelLoad.Failed }
        if(state==LuckyWheelLoad.Failed) currentAnimation?.let { current -> current.receipt?.let {current.onFinished(it.transactionId)} }
    }
    // A stalled renderer cannot leave a committed receipt trapped in Animating.
    LaunchedEffect(animation?.receipt?.transactionId) {
        if(animation?.receipt!=null) { delay(15_000); currentAnimation?.let { current -> current.receipt?.let {current.onFinished(it.transactionId)} } }
    }
    Box(modifier) {
        if(state!=LuckyWheelLoad.Failed) key(attempt) {
            AndroidView(factory={ LuckyWheelWebView(context).also { native -> native.onLoad={state=it}; view=native } },
                update={ native -> animation?.let(native::present) },
                modifier=Modifier.matchParentSize(),onRelease={ it.release(); if(view===it) view=null })
        }
        when(state) {
            LuckyWheelLoad.Loading -> PawQuestLoading(message="Loading wheel artwork...")
            LuckyWheelLoad.Failed -> PawQuestError(message="Wheel artwork couldn't load.",onRetry={attempt++})
            LuckyWheelLoad.Ready -> Unit
        }
    }
}
