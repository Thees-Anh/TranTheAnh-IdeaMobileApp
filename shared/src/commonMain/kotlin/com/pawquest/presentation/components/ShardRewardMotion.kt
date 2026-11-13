package com.pawquest.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.semantics.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.*

/** A confirmed receipt only; never an instruction to change a wallet. */
data class ShardRewardFlight(val id: String,val pet: Boolean,val amount: Long,val balanceAfter: Long,val ready: Boolean) {
    init {require(amount>0 && balanceAfter>=amount)}
    val before get()=balanceAfter-amount
}
internal fun shardFlightBalance(event: ShardRewardFlight,arrived: Int,count: Int): Long {
    require(count>0 && arrived in 0..count)
    return event.before+(event.amount/count)*arrived+(event.amount%count)*arrived/count
}
internal class ShardRewardMotion {
    val targets=mutableStateMapOf<Boolean,Rect>()
    val values=mutableStateMapOf<Boolean,Long>()
    val completed=mutableStateMapOf<String,Boolean>()
    val progress=mutableStateMapOf<Boolean,Float>()
}
internal val LocalShardRewardMotion=staticCompositionLocalOf<ShardRewardMotion?> {null}
private val LocalShardFlights=staticCompositionLocalOf {emptyList<ShardRewardFlight>()}

@Composable internal fun animatedShardBalance(actual: Long?,pet: Boolean): Long? {
    val motion=LocalShardRewardMotion.current ?: return actual
    val event=LocalShardFlights.current.firstOrNull {it.pet==pet} ?: return actual
    return if(motion.completed[event.id]==true) actual else motion.values[pet] ?: event.before
}

@Composable fun ShardRewardHost(events: List<ShardRewardFlight>,content: @Composable ()->Unit) {
    val motion=remember {ShardRewardMotion()}
    CompositionLocalProvider(LocalShardRewardMotion provides motion,LocalShardFlights provides events) {
        content()
        events.forEach {event -> key(event.id) {
            LaunchedEffect(event.ready) {
                if(!event.ready || motion.completed[event.id]==true) return@LaunchedEffect
                val target=withTimeoutOrNull(600) {snapshotFlow {motion.targets[event.pet]}.filterNotNull().first()}
                try {
                    if(target!=null) {
                        val count=event.amount.coerceAtMost(12).toInt()
                        motion.values[event.pet]=event.before
                        Animatable(0f).animateTo(1f,tween(1800,easing=LinearEasing)) {
                            motion.progress[event.pet]=value
                            val arrived=(0 until count).count {value>=0.55f+it*0.45f/count}
                            motion.values[event.pet]=shardFlightBalance(event,arrived,count)
                        }
                    }
                } finally {
                    motion.completed[event.id]=true
                    motion.values.remove(event.pet);motion.progress.remove(event.pet)
                }
            }
        }}
        val active=events.filter {motion.progress[it.pet]!=null}
        if(active.isNotEmpty()) Popup(popupPositionProvider=remember {object : androidx.compose.ui.window.PopupPositionProvider {
            override fun calculatePosition(anchorBounds: androidx.compose.ui.unit.IntRect,windowSize: androidx.compose.ui.unit.IntSize,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection,popupContentSize: androidx.compose.ui.unit.IntSize)=IntOffset.Zero
        }},
            properties=PopupProperties(focusable=false,dismissOnBackPress=false,dismissOnClickOutside=false,clippingEnabled=false)) {
            BoxWithConstraints(Modifier.fillMaxSize().clearFlightSemantics()) {
                val density=androidx.compose.ui.platform.LocalDensity.current
                val startX=with(density) {maxWidth.toPx()/2}
                val startY=with(density) {maxHeight.toPx()*0.52f}
                active.forEach {event ->
                    val target=motion.targets[event.pet] ?: return@forEach
                    val progress=motion.progress[event.pet] ?: return@forEach
                    val count=event.amount.coerceAtMost(12).toInt()
                    repeat(count) {i ->
                        val t=((progress-i*0.45f/count)/0.55f).coerceIn(0f,1f)
                        if(t>0f && t<1f) Icon(if(event.pet) ShardIcons.Pet else ShardIcons.Item,null,tint=Color.Unspecified,
                            modifier=Modifier.size(28.dp).graphicsLayer {
                                val spread=(i%5-2)*18f
                                translationX=(startX+spread)*(1-t)+target.center.x*t-size.width/2
                                translationY=startY*(1-t)+target.center.y*t-sin(t*PI).toFloat()*80-size.height/2
                                rotationZ=(1-t)*(i%3-1)*35
                                alpha=min(1f,t*8)
                            })
                    }
                    // Keep the receiving badge legible above the reward dialog's dim layer.
                    ShardBadge(motion.values[event.pet],event.pet,Modifier.offset {IntOffset(target.left.roundToInt(),target.top.roundToInt())})
                }
            }
        }
    }
}

private fun Modifier.clearFlightSemantics()=this.then(Modifier.clearAndSetSemantics {})
