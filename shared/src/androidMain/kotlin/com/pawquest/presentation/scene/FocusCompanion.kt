package com.pawquest.presentation.scene

import com.pawquest.localization.tr
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pawquest.presentation.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun FocusCompanion(modifier: Modifier=Modifier) {
    val context=LocalContext.current
    var html by remember {mutableStateOf<String?>(null)}
    var failed by remember {mutableStateOf(false)}
    var attempt by remember {mutableIntStateOf(0)}
    var view by remember {mutableStateOf<PrototypePetView?>(null)}
    LaunchedEffect(attempt) {
        failed=false
        try {html=withContext(Dispatchers.IO) {context.assets.open("pet/focus_mochi.html").bufferedReader().use {it.readText()}}}
        catch(cancelled: kotlinx.coroutines.CancellationException) {throw cancelled}
        catch(_: Exception) {failed=true}
    }
    LifecycleResumeEffect(view) {val native=view;native?.resumeIllustration();onPauseOrDispose {native?.pauseIllustration()}}
    html?.let {source ->
        AndroidView(factory={ctx->PrototypePetView(ctx,source).also {view=it;it.contentDescription=tr("Mochi reading a book beside you")}},modifier=modifier,
            update={it.update("reading",emptyList(),emptyList())},onRelease={if(view===it)view=null;it.release()})
    } ?: if(failed) PawQuestError(message="Mochi's reading corner couldn't load.",onRetry={attempt++},modifier=modifier)
    else PawQuestLoading(message="Opening Mochi's book...",modifier=modifier)
}
