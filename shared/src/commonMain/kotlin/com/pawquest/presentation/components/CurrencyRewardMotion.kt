package com.pawquest.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.*

internal enum class RewardCurrency { Coin, ItemShard, PetShard }

internal data class CurrencyRewardFlight(val id: String,val currency: RewardCurrency,val amount: Long,val balanceAfter: Long,val ready: Boolean=false) {
    init {require(amount>0 && balanceAfter>=amount)}
    val before get()=balanceAfter-amount
}
internal fun currencyFlightBalance(event: CurrencyRewardFlight,arrived: Int,count: Int): Long {
    require(count>0 && arrived in 0..count)
    return event.before+(event.amount/count)*arrived+(event.amount%count)*arrived/count
}
internal class CurrencyRewardMotion {
    val targets=mutableStateMapOf<RewardCurrency,Rect>()
    val values=mutableStateMapOf<String,Long>()
    val completed=mutableStateMapOf<RewardCurrency,String>()
    val progress=mutableStateMapOf<String,Float>()
}
internal val LocalCurrencyRewardMotion=staticCompositionLocalOf<CurrencyRewardMotion?> {null}
private val LocalCurrencyFlights=compositionLocalOf {emptyList<CurrencyRewardFlight>()}

@Composable internal fun animatedCurrencyBalance(actual: Long?,currency: RewardCurrency): Long? {
    val motion=LocalCurrencyRewardMotion.current ?: return actual
    val event=LocalCurrencyFlights.current.firstOrNull {it.currency==currency} ?: return actual
    return if(motion.completed[currency]==event.id) actual else motion.values[event.id] ?: event.before
}

@Composable internal fun CurrencyRewardHost(events: List<CurrencyRewardFlight>,modifier: Modifier=Modifier,content: @Composable ()->Unit) {
    val motion=remember {CurrencyRewardMotion()}
    CompositionLocalProvider(LocalCurrencyRewardMotion provides motion,LocalCurrencyFlights provides events) {
        events.forEach {event -> key(event.id) {
            LaunchedEffect(event.ready) {
                if(!event.ready || motion.completed[event.currency]==event.id) return@LaunchedEffect
                try {
                    val target=withTimeoutOrNull(600) {snapshotFlow {motion.targets[event.currency]}.filterNotNull().first()}
                    if(target!=null) {
                        val count=event.amount.coerceAtMost(12).toInt()
                        motion.values[event.id]=event.before
                        Animatable(0f).animateTo(1f,tween(1800,easing=LinearEasing)) {
                            motion.progress[event.id]=value
                            val arrived=(0 until count).count {value>=0.55f+it*0.45f/count}
                            motion.values[event.id]=currencyFlightBalance(event,arrived,count)
                        }
                    }
                } finally {
                    motion.completed[event.currency]=event.id
                    motion.values.remove(event.id);motion.progress.remove(event.id)
                }
            }
        }}
        Box(modifier) {
            content()
            val active=events.filter {motion.progress[it.id]!=null}
            var origin by remember {mutableStateOf(Offset.Zero)}
            if(active.isNotEmpty()) BoxWithConstraints(Modifier.matchParentSize().clearAndSetSemantics {}
                .onGloballyPositioned {origin=it.positionInRoot()}) {
                val density=androidx.compose.ui.platform.LocalDensity.current
                val startX=with(density) {maxWidth.toPx()/2}
                val startY=with(density) {maxHeight.toPx()*0.52f}
                active.forEach {event ->
                    val target=(motion.targets[event.currency]?.center ?: return@forEach)-origin
                    val progress=motion.progress[event.id] ?: return@forEach
                    val icon=when(event.currency) {
                        RewardCurrency.Coin -> ShardIcons.Coin
                        RewardCurrency.ItemShard -> ShardIcons.Item
                        RewardCurrency.PetShard -> ShardIcons.Pet
                    }
                    val count=event.amount.coerceAtMost(12).toInt()
                    repeat(count) {i ->
                        val t=((progress-i*0.45f/count)/0.55f).coerceIn(0f,1f)
                        if(t>0f && t<1f) Icon(icon,null,tint=Color.Unspecified,
                            modifier=Modifier.size(28.dp).graphicsLayer {
                                val spread=(i%5-2)*18f
                                translationX=(startX+spread)*(1-t)+target.x*t-size.width/2
                                translationY=startY*(1-t)+target.y*t-sin(t*PI).toFloat()*80-size.height/2
                                rotationZ=(1-t)*(i%3-1)*35
                                alpha=min(1f,t*8)
                            })
                    }
                }
            }
        }
    }
}

