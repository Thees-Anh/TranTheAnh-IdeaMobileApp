package com.pawquest.presentation.bistro

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.webkit.*
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.webkit.*
import com.pawquest.localization.tr
import com.pawquest.presentation.components.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import java.io.ByteArrayInputStream
import kotlin.uuid.Uuid

@SuppressLint("SetJavaScriptEnabled")
internal class BistroWebView(context: Context): WebView(context) {
    var onLoad: (Boolean)->Unit={}
    private var value: BistroPresentation?=null
    private val nonce=Uuid.random().toString()
    private var ready=false;private var started=false;private var released=false;private var paused=false
    private var config: String?=null;private var playing: String?=null;private var reported: String?=null
    private val messages=WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
    private val loader=WebViewAssetLoader.Builder().addPathHandler("/assets/",WebViewAssetLoader.AssetsPathHandler(context.applicationContext)).build()
    companion object {
        const val ORIGIN="https://appassets.androidplatform.net"
        const val HOME="$ORIGIN/assets/food/bistro.html"
        private val paths=setOf("/assets/food/bistro.html","/assets/food/bistro.css","/assets/gacha/quicksand.ttf")
        fun allowed(uri: Uri)=uri.scheme=="https" && uri.authority=="appassets.androidplatform.net" && uri.path in paths && uri.query==null
        fun blocked()=WebResourceResponse("text/plain","UTF-8",403,"Blocked",emptyMap(),ByteArrayInputStream(ByteArray(0)))
    }
    init {
        contentDescription=tr("PawQuest Bistro")
        settings.apply {
            javaScriptEnabled=true;allowFileAccess=false;allowContentAccess=false;blockNetworkLoads=true;blockNetworkImage=true
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs=false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs=false
            domStorageEnabled=false;javaScriptCanOpenWindowsAutomatically=false;mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(true);setSupportZoom(false);setGeolocationEnabled(false);mediaPlaybackRequiresUserGesture=true
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this,false)
        if(messages) WebViewCompat.addWebMessageListener(this,"PawQuestBistroEvents",setOf(ORIGIN)) {_,message,origin,mainFrame,_->
            if(!mainFrame || origin.toString()!=ORIGIN || released) return@addWebMessageListener
            val body=runCatching {Json.parseToJsonElement(message.data.orEmpty()).jsonObject}.getOrNull() ?: return@addWebMessageListener
            if(body["version"]?.jsonPrimitive?.intOrNull!=1 || body["viewId"]?.jsonPrimitive?.contentOrNull!=nonce) return@addWebMessageListener
            when(body["event"]?.jsonPrimitive?.contentOrNull) {
                "select" -> body["itemId"]?.jsonPrimitive?.contentOrNull?.let {id ->
                    val latest=value
                    if(latest?.canOrder==true && latest.menu.any {it.itemId==id}) latest.onSelect(id)
                }
                "complete" -> if(body["transactionId"]?.jsonPrimitive?.contentOrNull==playing) finish()
            }
        }
        webChromeClient=object : WebChromeClient() {
            override fun onCreateWindow(view: WebView?,dialog: Boolean,gesture: Boolean,result: android.os.Message?)=false
            override fun onPermissionRequest(request: PermissionRequest?) {request?.deny()}
            override fun onGeolocationPermissionsShowPrompt(origin: String?,callback: GeolocationPermissions.Callback?) {callback?.invoke(origin,false,false)}
            override fun onShowFileChooser(view: WebView?,callback: android.webkit.ValueCallback<Array<Uri>>?,params: FileChooserParams?)=false
            override fun onJsAlert(view: WebView?,url: String?,message: String?,result: JsResult?): Boolean {result?.cancel();return true}
        }
        webViewClient=object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?,request: WebResourceRequest?)=true
            override fun shouldInterceptRequest(view: WebView?,request: WebResourceRequest?): WebResourceResponse {
                val uri=request?.url ?: return blocked()
                return if(allowed(uri)) loader.shouldInterceptRequest(uri) ?: blocked() else blocked()
            }
            override fun onPageStarted(view: WebView?,url: String?,favicon: Bitmap?) {finish();ready=false;config=null;playing=null}
            override fun onPageFinished(view: WebView?,url: String?) {
                if(released || url!=HOME) return
                evaluateJavascript("Boolean(window.pawquestBistroReady)") {result ->
                    if(!released) {ready=result=="true";onLoad(ready);value?.let(::present);if(paused) evaluateJavascript("window.PawQuestBistro.pause()",null)}
                }
            }
            override fun onReceivedError(view: WebView?,request: WebResourceRequest?,error: WebResourceError?) {if(request?.isForMainFrame==true) {finish();onLoad(false)}}
            override fun onRenderProcessGone(view: WebView?,detail: RenderProcessGoneDetail?): Boolean {finish();onLoad(false);release();return true}
        }
    }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {super.onSizeChanged(w,h,oldw,oldh);if(!started && !released && w>0 && h>0) {started=true;loadUrl(HOME)}}
    private fun finish() {val id=playing ?: return;if(reported!=id) {reported=id;value?.onFinished?.invoke(id)}}
    fun present(data: BistroPresentation) {
        value=data
        if(released || !ready) return
        val serialized=buildJsonObject {
            put("version",1);put("viewId",nonce);put("canOrder",data.canOrder && messages)
            put("reducedMotion",Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f)
            put("species",when(data.species) {"puppy"->"dog";"rabbit"->"bunny";"capybara"->"capy";else->"cat"})
            put("labels",buildJsonObject {put("fullness",tr("Fullness"));put("joy",tr("Joy"));put("buy",tr("Buy & serve"));put("coins",tr("Coins"))})
            put("items",buildJsonArray {data.menu.forEach {item ->add(buildJsonObject {
                put("itemId",item.itemId);put("assetId",item.assetId);put("name",tr(item.name));put("tier",tr(when(item.tier) {"BASIC"->"Everyday";"TASTY"->"Tasty";"PREMIUM"->"Premium";else->"Royal"}))
                put("type",item.type);put("price",item.price);put("hunger",item.hunger);put("happiness",item.happiness);put("xp",item.xp)
            })}})
        }.toString()
        if(serialized!=config) {config=serialized;evaluateJavascript("window.PawQuestBistro.initialize($serialized)",null)}
        val receipt=data.receipt
        if(receipt==null) {if(playing!=null) {evaluateJavascript("window.PawQuestBistro.skip()",null);playing=null};return}
        if(playing==receipt.id) return
        playing=receipt.id
        if(!messages || paused) {finish();return}
        val result=buildJsonObject {put("transactionId",receipt.id);put("itemId",receipt.itemId);put("assetId",receipt.assetId);put("type",receipt.type);put("name",tr(receipt.name));put("hunger",receipt.hunger);put("happiness",receipt.happiness);put("xp",receipt.xp)}
        evaluateJavascript("window.PawQuestBistro.play($result)") {accepted ->if(!released && accepted!="true") finish()}
    }
    fun pauseBistro() {if(!released) {paused=true;if(ready) evaluateJavascript("window.PawQuestBistro.pause()",null);finish();onPause()}}
    fun resumeBistro() {if(!released) {paused=false;onResume();if(ready) evaluateJavascript("window.PawQuestBistro.resume()",null)}}
    fun release() {if(released)return;finish();released=true;onLoad={};value=null;evaluateJavascript("window.PawQuestBistro.dispose()",null);stopLoading();onPause();removeAllViews();destroy()}
}

@Composable actual fun BistroRenderer(value: BistroPresentation,modifier: Modifier) {
    val context=LocalContext.current
    var attempt by remember {mutableIntStateOf(0)}
    var loaded by remember(attempt) {mutableStateOf<Boolean?>(null)}
    var view by remember(attempt) {mutableStateOf<BistroWebView?>(null)}
    val latest by rememberUpdatedState(value)
    val nativeView=view
    LifecycleResumeEffect(nativeView) {nativeView?.resumeBistro();onPauseOrDispose {nativeView?.pauseBistro()}}
    LaunchedEffect(attempt,loaded) {if(loaded==null) {delay(10_000);if(loaded==null) {loaded=false;latest.receipt?.let {latest.onFinished(it.id)}}}}
    LaunchedEffect(value.receipt?.id) {if(value.receipt!=null) {delay(15_000);latest.receipt?.let {latest.onFinished(it.id)}}}
    Box(modifier) {
        if(loaded!=false) key(attempt) {AndroidView(factory={BistroWebView(context).also {native->native.onLoad={loaded=it};view=native}},
            update={it.present(value)},modifier=Modifier.matchParentSize(),onRelease={it.release();if(view===it)view=null})}
        if(loaded==null) PawQuestLoading(message="Loading the Bistro menu...")
        if(loaded==false) PawQuestError(message="Bistro artwork couldn't load.",onRetry={attempt++})
    }
}
