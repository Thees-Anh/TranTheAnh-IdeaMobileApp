package com.pawquest.presentation.gacha

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pawquest.domain.model.*
import com.pawquest.localization.tr
import com.pawquest.presentation.components.*

@Composable fun ShopShardExchange(wheel: LuckyWheelViewModel?,items: ItemExchangeViewModel?,cases: ItemCaseViewModel?,
    catalog: List<CatalogItem>,modifier: Modifier=Modifier,onInventory: ((String)->Unit)?=null,onUse: ((String)->Unit)?=null,
    actionableIds: Set<String> = emptySet(),onPetCollection: (()->Unit)?=null,initialCategory: String="Pets") {
    var category by remember(initialCategory) {mutableStateOf(initialCategory)}
    val itemState=items?.state?.collectAsStateWithLifecycle()?.value
    val caseState=cases?.state?.collectAsStateWithLifecycle()?.value
    val wheelState=wheel?.state?.collectAsStateWithLifecycle()?.value
    LaunchedEffect(itemState?.pending?.requestId) {
        itemState?.pending?.let {p->category=if(catalog.firstOrNull {it.id==p.itemId}?.category==ItemCategory.FURNITURE) "Furniture" else "Accessories"}
    }
    LaunchedEffect(wheelState?.pending?.kind) {if(wheelState?.pending?.kind==LuckyOperationKind.PET_REDEMPTION) category="Pets"}
    LaunchedEffect(Unit) {items?.refresh();cases?.refresh();wheel?.refresh()}
    Column(modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Pets","Accessories","Furniture").forEach {tab -> PawQuestButton(tab,{category=tab},
                variant=if(category==tab) ButtonVariant.Primary else ButtonVariant.Secondary)}
        }
        if(category=="Pets") {
            if(wheel!=null) PetShardExchangeContent(wheel,Modifier.weight(1f),onPetCollection)
            else Text(tr("Pet Exchange data unavailable."))
        } else {
            val config=caseState?.case?.configuration
            if(items!=null && config!=null) ShardExchangePanel(items,config,catalog,{},onInventory,onUse,actionableIds,
                category=if(category=="Furniture") ItemCategory.FURNITURE else ItemCategory.ACCESSORY,embedded=true)
            else {Text(tr("Item Exchange data unavailable."));PawQuestButton("Retry",{cases?.refresh();items?.refresh()})}
        }
    }
}
