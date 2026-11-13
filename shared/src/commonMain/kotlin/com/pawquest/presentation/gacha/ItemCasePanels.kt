package com.pawquest.presentation.gacha

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pawquest.domain.model.*
import com.pawquest.localization.tr
import com.pawquest.presentation.components.*
import com.pawquest.presentation.icons.PawQuestIcons
import com.pawquest.presentation.theme.PawQuestTheme

@Composable
internal fun CaseItem(display: CaseItemDisplay) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        ItemArtwork(display.assetId,PawQuestIcons.ShoppingBag,tr(display.name),PawQuestTheme.colors.primaryDark,Modifier.size(64.dp))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(tr(display.name),style=PawQuestTheme.typography.titleMedium)
            Text("${tr(display.rarity.name)} · ${tr(when(display.category) {
                ItemCategory.ACCESSORY -> "Accessories"; ItemCategory.FURNITURE -> "Furniture"; else -> "Category unavailable"
            })}",style=PawQuestTheme.typography.labelMedium)
            display.owned?.let { Text(tr(if(it) "Owned" else "Not owned"),style=PawQuestTheme.typography.labelSmall) }
        }
    }
}

@Composable
internal fun CaseRewardReveal(receipt: ItemCaseReceipt,display: CaseItemDisplay,balance: Long?,busy: Boolean,canRepeat: Boolean,notice: String?,
    onClose: ()->Unit,onAgain: ()->Unit,onInventory: (()->Unit)?,onUse: (()->Unit)?,onWallet: ()->Unit) {
    val duplicate=receipt.outcome==ItemCaseRewardOutcome.DUPLICATE_CONVERTED
    AlertDialog(onDismissRequest={if(!busy) onClose()},
        title={Text(tr(if(duplicate) "Duplicate Item" else if(display.newItem) "New Item" else "Item Granted"))},
        text={ LazyColumn(Modifier.heightIn(max=360.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {CaseItem(display)}
            item {Text(if(duplicate) tr("Duplicate converted to ${receipt.itemShardsGranted} Item Shards.") else tr("Item added to your inventory."))}
            notice?.let { item {Text(tr(it),color=PawQuestTheme.colors.error)} }
            if(duplicate) {
                item {Text(tr("Item Shards: ${balance ?: receipt.itemShardsBalanceAfter}"))}
                item {PawQuestButton("View Shard Wallet",onWallet,enabled=!busy,modifier=Modifier.fillMaxWidth())}
            } else {
                onInventory?.let { action -> item {PawQuestButton("View in Inventory",action,enabled=!busy,modifier=Modifier.fillMaxWidth())} }
                onUse?.let { action -> item {PawQuestButton(if(display.category==ItemCategory.FURNITURE) "Place" else "Equip",action,enabled=!busy,modifier=Modifier.fillMaxWidth())} }
            }
            item {PawQuestButton("Open Again",onAgain,enabled=canRepeat,modifier=Modifier.fillMaxWidth())}
        } },confirmButton={PawQuestButton("Close",onClose,enabled=!busy)})
}

@Composable
internal fun CaseDetailsPanel(panel: String,state: ItemCaseState,catalog: List<CatalogItem>,onClose: ()->Unit,
    onRetryHistory: ()->Unit,onRefreshWallet: ()->Unit,onExchange: (()->Unit)?=null) {
    val config=state.case?.configuration
    val names=catalog.associateBy {it.id}
    AlertDialog(onDismissRequest=onClose,title={Text(tr(when(panel) {
        "rates"->"Drop Rates"; "history"->"Opening History"; "wallet"->"Shard Wallet"; else->"View Rewards"
    }))},text={ LazyColumn(Modifier.fillMaxWidth().heightIn(max=380.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        when(panel) {
            "wallet" -> {
                item {Text(state.itemShards?.let {tr("Item Shards: $it")} ?: tr("Item Shards unavailable"),style=PawQuestTheme.typography.titleMedium)}
                item {PawQuestButton("Refresh",onRefreshWallet,enabled=!state.walletLoading)}
                if(state.walletLoading) item {Text(tr("Loading Shard Wallet..."))}
                else if(state.walletError!=null) item {Text(tr("Shard Wallet couldn't fully refresh."),color=PawQuestTheme.colors.error);PawQuestButton("Retry",onRefreshWallet)}
                else if(state.shardLedger.isEmpty()) item {Text(tr("No Item Shard entries yet."))}
                if(!state.walletLoading && state.walletError==null) {
                    item {Text(tr("Recent Item Shard entries (up to 20)"),style=PawQuestTheme.typography.labelMedium)}
                    items(state.shardLedger,key={it.id}) { entry ->
                        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(tr(when(entry.reason) {
                                ShardLedgerReason.ITEM_DUPLICATE->"Duplicate conversion"
                                ShardLedgerReason.ITEM_REDEMPTION->"Shard redemption"
                                ShardLedgerReason.ADMIN_ADJUSTMENT->"Administrative adjustment"
                            }),style=PawQuestTheme.typography.titleMedium)
                            Text("${if(entry.delta>0) "+" else ""}${entry.delta} ${tr("Item Shards")}")
                            Text(tr("Balance after: ${entry.balanceAfter} Item Shards"))
                            Text(caseDate(entry.createdAt),style=PawQuestTheme.typography.labelSmall)
                        }
                    }
                }
                item {PawQuestButton("Shard Exchange",{onExchange?.invoke()},enabled=onExchange!=null,modifier=Modifier.fillMaxWidth())}
            }
            "history" -> {
                item {Text(tr("Recent openings (up to 20)"),style=PawQuestTheme.typography.labelMedium)}
                when {
                    state.historyLoading -> item {Text(tr("Loading opening history..."))}
                    state.historyError!=null -> item {Text(tr("Opening history couldn't load."));PawQuestButton("Retry",onRetryHistory)}
                    state.history.isEmpty() -> item {Text(tr("No case openings yet."))}
                    else -> items(state.history,key={it.id}) { row ->
                        Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            CaseItem(CaseItemDisplay(row.itemId,names[row.itemId]?.name ?: row.itemCode,row.assetId,row.rarity,row.category))
                            Text(tr(if(row.outcome==ItemCaseRewardOutcome.DUPLICATE_CONVERTED) "Duplicate Item" else "Item Granted"))
                            if(row.outcome==ItemCaseRewardOutcome.DUPLICATE_CONVERTED) Text(tr("Duplicate converted to ${row.shardsGranted} Item Shards."))
                            Text("${caseDate(row.createdAt)} · ${row.coinsSpent} ${tr("Coins")}",style=PawQuestTheme.typography.labelSmall)
                        }
                    }
                }
            }
            else -> if(config!=null) {
                if(panel=="rates") {
                    item {Text(tr("Owned unique items convert to Item Shards. Stackable items keep their quantity behavior."))}
                    items(config.tiers,key={it.rarity}) { tier ->
                        Column {
                            Text("${tr(tier.rarity.name)} · ${casePercent(ItemCaseProbability(tier.probabilityBps.toLong(),10_000))}",style=PawQuestTheme.typography.titleMedium)
                            Text(tr("Duplicate reward: ${tier.duplicateItemShards} Item Shards"))
                        }
                    }
                    item {Text(tr("Item probabilities are rounded to four decimal places."))}
                } else if(!state.inventoryAvailable) item {Text(tr("Ownership unavailable"))}
                items(caseCollection(config,catalog,state.inventory,state.inventoryAvailable),key={it.itemId}) { display ->
                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        CaseItem(display)
                        if(panel=="rates") Text(casePercent(config.probability(display.itemId)))
                    }
                }
            }
        }
    } },confirmButton={PawQuestButton("Close",onClose)})
}
