package com.pawquest.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import com.pawquest.localization.tr
import com.pawquest.presentation.theme.PawQuestTheme

/** Display-only values supplied by the existing authenticated case/wheel state. */
data class ShardBalances(val pet: Long?=null,val item: Long?=null)
val LocalShardBalances=staticCompositionLocalOf {ShardBalances()}

object ShardIcons {
    val Ticket=ImageVector.Builder("Lucky Wheel Ticket",24.dp,24.dp,24f,24f)
        .addPath(addPathNodes("M3,4 L21,4 L21,9 C17,9 17,15 21,15 L21,20 L3,20 L3,15 C7,15 7,9 3,9 Z"),
            fill=SolidColor(Color(0xFFFFD35D)),stroke=SolidColor(Color(0xFF93611F)),strokeLineWidth=1.5f)
        .addPath(addPathNodes("M12,7 L13.5,10 L17,10.5 L14.5,13 L15,16.5 L12,15 L9,16.5 L9.5,13 L7,10.5 L10.5,10 Z"),
            fill=SolidColor(Color(0xFF93611F))).build()
    val Pet=crystal("Pet Shards",Color(0xFF9256BE),Color(0xFFD8B4F0),
        "M9,14 C9,11 15,11 15,14 C17,18 7,18 9,14 Z M7,9 A1.3,1.7 0 1,0 9.6,9 A1.3,1.7 0 1,0 7,9 M10.7,7 A1.3,1.7 0 1,0 13.3,7 A1.3,1.7 0 1,0 10.7,7 M14.4,9 A1.3,1.7 0 1,0 17,9 A1.3,1.7 0 1,0 14.4,9")
    val Item=crystal("Item Shards",Color(0xFF258C9C),Color(0xFF9CE5DE),
        "M12,6 L13.7,10 L18,10.5 L14.8,13.3 L15.7,17.6 L12,15.4 L8.3,17.6 L9.2,13.3 L6,10.5 L10.3,10 Z")
    private fun crystal(name: String,base: Color,light: Color,glyph: String)=ImageVector.Builder(
        name,24.dp,24.dp,24f,24f)
        .addPath(addPathNodes("M12,1 L21,7 L20,17 L12,23 L4,17 L3,7 Z"),fill=SolidColor(base))
        .addPath(addPathNodes("M12,1 L21,7 L12,10 L3,7 Z M3,7 L12,10 L12,23 L4,17 Z"),fill=SolidColor(light))
        .addPath(addPathNodes(glyph),fill=SolidColor(Color.White)).build()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun CurrencyBalances(coins: Number,modifier: Modifier=Modifier,
    shards: ShardBalances=LocalShardBalances.current) {
    FlowRow(modifier,horizontalArrangement=Arrangement.spacedBy(5.dp,Alignment.End),
        verticalArrangement=Arrangement.spacedBy(5.dp)) {
        CoinBadge(coins,Modifier.align(Alignment.CenterVertically))
        val motion=LocalShardRewardMotion.current
        ShardBadge(animatedShardBalance(shards.pet,true),true,Modifier.align(Alignment.CenterVertically).onGloballyPositioned {motion?.targets?.set(true,it.boundsInWindow())})
        ShardBadge(animatedShardBalance(shards.item,false),false,Modifier.align(Alignment.CenterVertically).onGloballyPositioned {motion?.targets?.set(false,it.boundsInWindow())})
    }
}

@Composable internal fun ShardBadge(amount: Long?,pet: Boolean,modifier: Modifier=Modifier) {
    val label=if(pet) "Pet Shards" else "Item Shards"
    val text=if(pet) Color(0xFF68368C) else Color(0xFF146270)
    Surface(shape=PawQuestTheme.shapes.pill,color=if(pet) Color(0xFFF2E6FB) else Color(0xFFE0F6F1),
        border=BorderStroke(1.dp,if(pet) Color(0xFFC5A0DF) else Color(0xFF83C7BE)),shadowElevation=1.dp,
        modifier=modifier.clearAndSetSemantics {
            contentDescription=tr(if(amount==null) "$label unavailable" else "$label: $amount")
            liveRegion=LiveRegionMode.Polite
        }) {
        Row(Modifier.padding(horizontal=10.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            Icon(if(pet) ShardIcons.Pet else ShardIcons.Item,null,Modifier.size(18.dp),tint=Color.Unspecified)
            Text(amount?.toString() ?: "—",style=PawQuestTheme.typography.labelLarge.copy(fontWeight=FontWeight.ExtraBold),color=text)
        }
    }
}

/** Full currency label for balances, prices and Exchange rows. */
@Composable fun CurrencyAmount(amount: Long?,label: String,icon: ImageVector,modifier: Modifier=Modifier) {
    Row(modifier,verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Icon(icon,null,Modifier.size(22.dp),tint=Color.Unspecified)
        Text(tr(if(amount==null) "$label unavailable" else "$label: $amount"),style=PawQuestTheme.typography.labelLarge)
    }
}

@Composable fun CurrencyPrice(amount: Long?,icon: ImageVector,label: String) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp),
        modifier=Modifier.clearAndSetSemantics {contentDescription=if(amount==null) tr("Balances unavailable") else "${tr(label)}: $amount"}) {
        Text(amount?.toString() ?: "—",fontWeight=FontWeight.ExtraBold)
        Icon(icon,null,Modifier.size(22.dp),tint=if(icon==ShardIcons.Ticket) Color.Unspecified else androidx.compose.material3.LocalContentColor.current)
    }
}
