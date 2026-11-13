package com.pawquest.presentation.gacha

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pawquest.domain.model.*
import com.pawquest.localization.tr
import com.pawquest.presentation.components.*

@Composable
internal fun ShardExchangePanel(model: ItemExchangeViewModel,config: ItemCaseConfiguration,catalog: List<CatalogItem>,
    onClose: ()->Unit,onInventory: ((String)->Unit)?,onUse: ((String)->Unit)?,actionableIds: Set<String>,
    category: ItemCategory?=null,embedded: Boolean=false) {
    val state by model.state.collectAsStateWithLifecycle()
    val canLeave=!state.busy && (state.pending==null || state.receipt!=null || state.pending?.confirmed!=true || state.rejection?.canDismiss==true)
    val content: @Composable ()->Unit = {LazyColumn(Modifier.fillMaxWidth().then(if(embedded) Modifier.fillMaxHeight() else Modifier.heightIn(max=380.dp)),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {CurrencyAmount(state.balance,"Item Shards",ShardIcons.Item)}
            if(state.busy) item {Text(tr("Synchronizing exchange..."))}
            if(state.syncError) item {Text(tr("Some balances could not refresh."));PawQuestButton("Retry",model::refresh,enabled=!state.busy)}
            val receipt=state.receipt
            val quote=state.quote
            when {
                receipt!=null -> {
                    item {Text(tr("Item redeemed successfully."))}
                    item {CaseItem(CaseItemDisplay(receipt.itemId,receipt.name,receipt.assetId,receipt.rarity,receipt.category))}
                    item {Text(tr("Spent ${receipt.spent} Item Shards"))}
                    onInventory?.let {action->item {PawQuestButton("View in Inventory",{action(receipt.itemId)},enabled=!state.busy)}}
                    onUse?.takeIf {receipt.itemId in actionableIds}?.let {action->item {PawQuestButton(if(receipt.category==ItemCategory.FURNITURE) "Place" else "Equip",{action(receipt.itemId)},enabled=!state.busy)}}
                    item {PawQuestButton("Done",model::dismiss,enabled=!state.busy)}
                }
                state.error!=null || state.rejection!=null -> {
                    item {Text(tr(when(state.rejection) {
                        ItemExchangeRejection.INSUFFICIENT_SHARDS->"Not enough Item Shards."
                        ItemExchangeRejection.ALREADY_OWNED->"You already own this item."
                        ItemExchangeRejection.PRICE_CHANGED->"Price changed. Select the item again to confirm the new price."
                        ItemExchangeRejection.QUOTE_EXPIRED->"This quote expired. Select the item again."
                        ItemExchangeRejection.ITEM_UNAVAILABLE,ItemExchangeRejection.INVENTORY_FULL->"This item is unavailable or your inventory is full."
                        else->"Exchange recovery failed. Retry safely."
                    }))}
                    item {PawQuestButton("Retry",model::retry,enabled=!state.busy)}
                    if(canLeave) item {PawQuestButton("Choose another item",model::dismiss,enabled=!state.busy)}
                }
                quote!=null -> {
                    val display=caseCollection(config,catalog,state.inventory,state.ownershipAvailable).firstOrNull {it.itemId==quote.itemId}
                    display?.let {item {CaseItem(it)}}
                    item {Text(tr("Confirm redemption for ${quote.price} Item Shards?"))}
                    item {PawQuestButton("Confirm Redemption",model::confirm,enabled=!state.busy,modifier=Modifier.fillMaxWidth())}
                    item {PawQuestButton("Cancel",model::dismiss,enabled=!state.busy)}
                }
                state.pending==null -> {
                    item {PawQuestButton("Refresh",model::refresh,enabled=!state.busy)}
                    items(config.rewards.filter {it.redeemable && (category==null || it.category==category)},key={it.itemId}) {reward ->
                        val price=config.tiers.single {it.rarity==reward.rarity}.redemptionItemShards
                        val owned=state.inventory.firstOrNull {it.item.id==reward.itemId}?.quantity ?: 0
                        val item=catalog.firstOrNull {it.id==reward.itemId}
                        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            CaseItem(CaseItemDisplay(reward.itemId,item?.name ?: reward.itemCode,reward.assetId,reward.rarity,reward.category,
                                if(state.ownershipAvailable) owned>0 else null))
                            CurrencyPrice(price.toLong(),ShardIcons.Item,"Item Shards")
                            PawQuestButton("Redeem",{model.select(reward.itemId)},enabled=!state.busy && state.balance!=null &&
                                state.balance!!>=price && (!state.ownershipAvailable || owned<reward.maxQuantity),modifier=Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }}
    if(embedded) content() else AlertDialog(onDismissRequest={if(canLeave) {if(state.pending!=null) model.dismiss();onClose()}},
        title={Text(tr("Shard Exchange"))},text=content,
        confirmButton={PawQuestButton("Close",{if(state.pending!=null) model.dismiss();onClose()},enabled=canLeave)})
}
