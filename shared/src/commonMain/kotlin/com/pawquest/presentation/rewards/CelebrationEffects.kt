package com.pawquest.presentation.rewards

import com.pawquest.localization.tr
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.pawquest.presentation.progress.ProgressState
import com.pawquest.presentation.components.*
import com.pawquest.presentation.icons.CartoonIcons
import com.pawquest.presentation.icons.PawQuestIcons
import com.pawquest.presentation.platform.LocalCompanionPlatform
import com.pawquest.presentation.theme.PawQuestTheme
import kotlin.math.*
import kotlinx.coroutines.delay

@Composable fun CoinFlight(trigger: String?,from: Offset?,to: Offset?,modifier: Modifier=Modifier) {
    if(trigger==null || from==null || to==null) return
    val progress=remember(trigger) {Animatable(0f)}
    LaunchedEffect(trigger) {progress.animateTo(1f,tween(1400))}
    Canvas(modifier.clearAndSetSemantics {}) {
        val p=progress.value
        if(p>=1f) return@Canvas
        repeat(12) {index ->
            val t=((p-index*.025f)/.7f).coerceIn(0f,1f)
            if(t>0f&&t<1f) {
                val x=from.x+(to.x-from.x)*t+sin(t*PI).toFloat()*(index-6)*7f
                val y=from.y+(to.y-from.y)*t-70f*sin(PI*t).toFloat()
                val r=7.dp.toPx()
                drawCircle(Color(0xFFFFC928),r,Offset(x,y))
                drawCircle(Color(0xFF9B6324),r*.5f,Offset(x,y),style=androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            }
        }
    }
}

@Composable private fun Confetti(key: String,modifier: Modifier=Modifier) {
    val progress=remember(key) {Animatable(0f)}
    LaunchedEffect(key) {progress.animateTo(1f,tween(2200))}
    val palette=listOf(Color(0xFFF9A8D4),Color(0xFFFACC15),Color(0xFF93D5F3),Color(0xFFC4B5FD),Color(0xFFFB923C))
    Canvas(modifier.clearAndSetSemantics {}) {
        val t=progress.value
        if(t>=1f) return@Canvas
        repeat(60) {i ->
            val seed=(i*37%101)/100f
            val x=size.width*seed+sin(t*12+i)*14.dp.toPx()
            val y=-24.dp.toPx()+size.height*((t+i*.013f)%1f)
            rotate(i*19f+t*400f,Offset(x,y)) {drawRect(palette[i%palette.size].copy(alpha=1f-t*.7f),Offset(x,y),Size(5.dp.toPx(),9.dp.toPx()))}
        }
    }
}

@Composable fun ProgressCelebration(state: ProgressState,onAcknowledge: (String)->Unit,onLater: (String)->Unit,afterCoins: Boolean) {
    val event=state.event ?: return
    var visible by remember(event.id) {mutableStateOf(false)}
    var opened by rememberSaveable(event.id) {mutableStateOf(event.kind!="level_up")}
    var sounded by rememberSaveable(event.id) {mutableStateOf(false)}
    val platform=LocalCompanionPlatform.current
    LaunchedEffect(event.id) {
        if(afterCoins) delay(1600)
        visible=true
        if(event.kind=="level_up"&&!sounded) {sounded=true;platform?.playLevelUpSound()}
    }
    if(!visible) return
    AlertDialog(onDismissRequest={onLater(event.id)},title={Text(tr(if(event.kind=="level_up") "Level up!" else "A new streak badge!"))},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(150.dp),contentAlignment=Alignment.Center) {
                Confetti(event.id,Modifier.fillMaxSize())
                Icon(if(event.kind=="level_up"&&!opened) CartoonIcons.Food else PawQuestIcons.Trophy,null,Modifier.size(70.dp),tint=PawQuestTheme.colors.accentGoldDark)
            }
            if(event.kind=="level_up") {
                Text(tr("Lv. ${event.fromLevel} → Lv. ${event.toLevel}"),style=PawQuestTheme.typography.headlineMedium)
                Text(tr(if(!opened) "A surprise for your companion is waiting!" else if(event.food>0) "+${event.food} Pet food · saved in Inventory" else "Your Inventory is full. Your new level is saved!"))
            } else Text(tr(event.name),style=PawQuestTheme.typography.titleLarge)
            state.error?.let {Text(tr(it),color=PawQuestTheme.colors.error)}
        }},
        confirmButton={TextButton(onClick={if(!opened) opened=true else onAcknowledge(event.id)},enabled=!state.acknowledging) {Text(tr(if(!opened) "Open gift" else if(state.acknowledging) "Saving..." else "Continue"))}},
        dismissButton={TextButton(onClick={onLater(event.id)},enabled=!state.acknowledging) {Text(tr("Later"))}})
}
