package com.pawquest.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
    /** Glossy gold paw coin, drawn in the same layered style as the shard crystals. */
    val Coin=ImageVector.Builder("Coins",24.dp,24.dp,24f,24f).apply {
        fun circle(cx: Float,cy: Float,r: Float)="M${cx-r},$cy A$r,$r 0 1,0 ${cx+r},$cy A$r,$r 0 1,0 ${cx-r},$cy Z"
        fun ellipse(cx: Float,cy: Float,rx: Float,ry: Float)="M${cx-rx},$cy A$rx,$ry 0 1,0 ${cx+rx},$cy A$rx,$ry 0 1,0 ${cx-rx},$cy Z"
        fun grad(top: Long,bottom: Long)=Brush.linearGradient(listOf(Color(top),Color(bottom)),Offset(6f,3f),Offset(18f,21f))
        addPath(addPathNodes(circle(12f,12.9f,10.4f)),fill=SolidColor(Color(0xFFA9661A)))          // coin edge / thickness
        addPath(addPathNodes(circle(12f,11.6f,10.4f)),fill=grad(0xFFFFE89A,0xFFE3A52B))            // outer rim
        addPath(addPathNodes(circle(12f,11.6f,7.6f)),fill=grad(0xFFE09A24,0xFFFFD45C))             // recessed face
        addPath(addPathNodes(circle(12f,11.6f,7.6f)),fill=null,stroke=SolidColor(Color(0xFFB8741C)),strokeLineWidth=0.7f)
        val paw=listOf(
            "M12,11.6 C10.1,11.6 9,13.4 9.3,14.8 C9.5,15.9 10.7,16.2 12,15.9 C13.3,16.2 14.5,15.9 14.7,14.8 C15,13.4 13.9,11.6 12,11.6 Z",
            ellipse(8.9f,10.6f,1.05f,1.35f),ellipse(10.8f,8.5f,1.05f,1.35f),
            ellipse(13.2f,8.5f,1.05f,1.35f),ellipse(15.1f,10.6f,1.05f,1.35f)).joinToString(" ")
        addPath(addPathNodes(paw),fill=SolidColor(Color(0xFFB8741C)))                              // engraved shadow
        addPath(addPathNodes(paw),fill=SolidColor(Color(0xFFFFF4C9)))
        addPath(addPathNodes("M4.6,9.2 A8.2,8.2 0 0,1 10.4,3.7 L11,5 A6.8,6.8 0 0,0 6,9.7 Z"),
            fill=SolidColor(Color.White.copy(alpha=0.75f)))                                        // gloss
        addPath(addPathNodes("M19,2.2 L19.6,3.9 L21.3,4.5 L19.6,5.1 L19,6.8 L18.4,5.1 L16.7,4.5 L18.4,3.9 Z"),
            fill=SolidColor(Color.White))                                                          // sparkle
    }.build()
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
        Icon(icon,null,Modifier.size(22.dp),tint=Color.Unspecified)
    }
}
