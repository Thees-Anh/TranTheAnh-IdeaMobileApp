package com.pawquest.presentation.shop

import com.pawquest.localization.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pawquest.domain.model.CatalogItem
import com.pawquest.domain.model.ItemCategory
import com.pawquest.domain.model.PurchaseStatus
import com.pawquest.presentation.components.ButtonSize
import com.pawquest.presentation.components.ButtonVariant
import com.pawquest.presentation.components.CoinBadge
import com.pawquest.presentation.components.PawQuestButton
import com.pawquest.presentation.components.PawQuestCard
import com.pawquest.presentation.components.PawQuestEmpty
import com.pawquest.presentation.components.PawQuestError
import com.pawquest.presentation.components.PawQuestLoading
import com.pawquest.presentation.icons.PawQuestIcons
import com.pawquest.presentation.theme.PawQuestColors
import com.pawquest.presentation.theme.PawQuestTheme

@Composable
fun ShopScreen(
    viewModel: ShopViewModel,
    userCoins: Long,
    modifier: Modifier = Modifier,
    ownedItemIds: Set<String> = emptySet(),
    onNavigateToInventory: (() -> Unit)? = null,
    onNavigateToQuests: (() -> Unit)? = null,
    itemShards: Long? = null,
    shardsLoading: Boolean = false,
    itemCaseModel: com.pawquest.presentation.gacha.ItemCaseViewModel? = null,
    casePreview: (@Composable (Modifier)->Unit)? = null,
    caseAnimation: (@Composable (com.pawquest.presentation.gacha.ItemCaseAnimation, Modifier)->Unit)? = null,
    onViewCaseItem: ((String)->Unit)? = null,
    onUseCaseReward: ((String)->Unit)? = null,
    actionableCaseItemIds: Set<String> = emptySet(),
    exchangeModel: com.pawquest.presentation.gacha.ItemExchangeViewModel? = null,
    luckyWheelModel: com.pawquest.presentation.gacha.LuckyWheelViewModel? = null,
    wheelPreview: (@Composable (com.pawquest.presentation.gacha.LuckyWheelAnimation,Modifier)->Unit)? = null,
    onPetCollection: (()->Unit)? = null,
    /** Explore opens one activity at a time, so the section chips are hidden there. */
    showSectionTabs: Boolean = true,
    exchangeCategory: String = "Pets",
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState=rememberLazyGridState()
    androidx.compose.runtime.LaunchedEffect(state.selectedCategory) {gridState.scrollToItem(0)}
    val colors = PawQuestTheme.colors
    val effectiveCoins = state.lastPurchaseResult?.receipt?.balanceAfter ?: userCoins

    val caseState=itemCaseModel?.state?.collectAsStateWithLifecycle()?.value
    val wheelState=luckyWheelModel?.state?.collectAsStateWithLifecycle()?.value
    val flights=buildList {
        caseState?.result?.takeIf {it.itemShardsGranted>0}?.let {r -> add(com.pawquest.presentation.components.ShardRewardFlight(
            r.transactionId,false,r.itemShardsGranted.toLong(),r.itemShardsBalanceAfter,
            caseState.phase==com.pawquest.presentation.gacha.ItemCasePhase.RewardRevealed))}
        wheelState?.result?.takeIf {it.petShardsEarned>0}?.let {r -> add(com.pawquest.presentation.components.ShardRewardFlight(
            r.transactionId,true,r.petShardsEarned.toLong(),r.petShardsBalanceAfter,
            wheelState.phase==com.pawquest.presentation.gacha.LuckyWheelPhase.RewardRevealed))}
    }
    com.pawquest.presentation.components.ShardRewardHost(flights) {

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Top Header: Title, Navigation test subtitle, Coins balance
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = tr("Shop"),
                    style = PawQuestTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                )
                Text(
                    text = tr("Little treats for a cozy world."),
                    style = PawQuestTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                com.pawquest.presentation.components.CurrencyBalances(effectiveCoins,Modifier.widthIn(max=230.dp),
                    com.pawquest.presentation.components.LocalShardBalances.current.copy(item=itemShards,
                        pet=luckyWheelModel?.state?.collectAsStateWithLifecycle()?.value?.balances?.petShards
                            ?: com.pawquest.presentation.components.LocalShardBalances.current.pet))
            }
        }

        if(effectiveCoins==0L) {
            Text(tr("Your wallet is empty. Complete a Quest and claim its reward to buy food and decorations. Drinking water is free."),
                color=colors.error,style=PawQuestTheme.typography.bodyMedium,
                modifier=Modifier.semantics {liveRegion=LiveRegionMode.Polite})
            onNavigateToQuests?.let {PawQuestButton("Earn Coins",it)}
        }
        if(showSectionTabs) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            ShopSection.entries.forEach {section -> CategoryTabChip(section.label,PawQuestIcons.ShoppingBag,
                state.section==section,{viewModel.selectSection(section)},Modifier.widthIn(min=100.dp))}
        }

        // Main Body: Loading, Error, or Item Grid
        when {
            state.section==ShopSection.FOOD && com.pawquest.presentation.bistro.LocalBistro.current!=null -> {
                com.pawquest.presentation.bistro.BistroContent(checkNotNull(com.pawquest.presentation.bistro.LocalBistro.current),Modifier.weight(1f),
                    state.catalog.firstOrNull {it.code=="basic_food"},viewModel::openPurchase)
            }
            state.section==ShopSection.LUCKY_WHEEL -> {
                if(luckyWheelModel!=null) com.pawquest.presentation.gacha.LuckyWheelContent(luckyWheelModel,Modifier.weight(1f),wheelPreview,
                    onPetExchange={viewModel.selectSection(ShopSection.SHARD_EXCHANGE)})
                else Text(tr("Lucky Wheel data unavailable."))
            }
            state.section==ShopSection.SHARD_EXCHANGE -> com.pawquest.presentation.gacha.ShopShardExchange(luckyWheelModel,exchangeModel,
                itemCaseModel,state.catalog,Modifier.weight(1f),onViewCaseItem,onUseCaseReward,actionableCaseItemIds,onPetCollection,exchangeCategory)
            state.section==ShopSection.ITEM_CASE -> {
                if(itemCaseModel!=null) com.pawquest.presentation.gacha.MysteryCaseContent(itemCaseModel,effectiveCoins,
                    state.catalog.associate { it.id to it.name },casePreview,Modifier.weight(1f),caseAnimation,
                    catalog=state.catalog,onViewInventory=onViewCaseItem,onUseReward=onUseCaseReward,actionableItemIds=actionableCaseItemIds,exchangeModel=exchangeModel)
                else Text(tr("Case data is unavailable."),color=colors.error)
            }
            state.loading && state.catalog.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    PawQuestLoading(message = "Stocking shop shelves...")
                }
            }
            state.error != null && state.catalog.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    PawQuestError(
                        message = state.error ?: "Unable to load shop catalog.",
                        onRetry = viewModel::loadCatalog,
                    )
                }
            }
            else -> {
                val filteredItems = state.catalog.filter { it.category == ItemCategory.FOOD }
                if (filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        PawQuestEmpty(
                            message = "No items in this category right now.",
                            title = "Shelves Empty",
                            icon = PawQuestIcons.ShoppingBag,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        state=gridState,
                        columns = GridCells.Adaptive(150.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(filteredItems, key = { it.id }) { item ->
                            val isOwned = !item.stackable && ownedItemIds.contains(item.id)
                            ShopItemCard(
                                item = item,
                                isOwned = isOwned,
                                onBuyClick = { viewModel.openPurchase(item) },
                            )
                        }
                    }
                }
            }
        }
    }

    // Purchase Confirmation Modal
    if (state.pendingPurchase != null) {
        val pending = state.pendingPurchase!!
        val totalCost = pending.priceCoins.toLong() * state.purchaseQuantity
        val canAfford = effectiveCoins >= totalCost

        AlertDialog(
            onDismissRequest = { if (!state.busy) viewModel.dismissPurchase() },
            title = {
                Text(
                    text = tr("Buy ${tr(pending.name)}"),
                    style = PawQuestTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                )
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape = PawQuestTheme.shapes.card,
                            color = getItemBgColor(pending.category, colors),
                            modifier = Modifier.size(54.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                com.pawquest.presentation.components.ItemArtwork(assetId=pending.assetId,
                                    imageVector = getItemIcon(pending),
                                    contentDescription = null,
                                    tint = getItemTintColor(pending.category, colors),
                                    modifier = Modifier.size(76.dp),
                                )
                            }
                        }
                        Column {
                            Text(
                                text = tr(pending.name),
                                style = PawQuestTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = tr("${pending.priceCoins} Coins each"),
                                style = PawQuestTheme.typography.labelMedium,
                                color = colors.textSecondary,
                            )
                        }
                    }

                    if (pending.stackable) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Text(
                                text = tr("Quantity:"),
                                style = PawQuestTheme.typography.labelLarge,
                                color = colors.textPrimary,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Surface(
                                    shape = PawQuestTheme.shapes.pill,
                                    color = colors.primaryContainer,
                                    onClick = { viewModel.setQuantity(state.purchaseQuantity - 1) },
                                    enabled = state.purchaseQuantity > 1 && !state.busy,
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = PawQuestIcons.Minus,
                                            contentDescription = tr("Decrease"),
                                            tint = colors.primaryDark,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = tr("${state.purchaseQuantity}"),
                                    style = PawQuestTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.defaultMinSize(minWidth = 32.dp),
                                    textAlign = TextAlign.Center,
                                    color = colors.textPrimary,
                                )
                                Surface(
                                    shape = PawQuestTheme.shapes.pill,
                                    color = colors.primaryContainer,
                                    onClick = { viewModel.setQuantity(state.purchaseQuantity + 1) },
                                    enabled = state.purchaseQuantity < 100 && !state.busy,
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = PawQuestIcons.Plus,
                                            contentDescription = tr("Increase"),
                                            tint = colors.primaryDark,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        shape = PawQuestTheme.shapes.card,
                        color = colors.backgroundAlt,
                        border = BorderStroke(1.dp, colors.borderLight),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(tr("Total cost:"), style = PawQuestTheme.typography.bodyMedium, color = colors.textSecondary)
                                Text(tr("$totalCost Coins"), style = PawQuestTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.accentGoldDark)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(tr("Your balance:"), style = PawQuestTheme.typography.bodyMedium, color = colors.textSecondary)
                                Text(tr("$effectiveCoins Coins"), style = PawQuestTheme.typography.bodyMedium, color = colors.textPrimary)
                            }
                            if (canAfford) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(tr("Remaining:"), style = PawQuestTheme.typography.bodyMedium, color = colors.textSecondary)
                                    Text(tr("${effectiveCoins - totalCost} Coins"), style = PawQuestTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                                }
                            }
                        }
                    }

                    if (!canAfford) {
                        Text(
                            text = tr("Insufficient coins! Complete quests to earn more coins."),
                            style = PawQuestTheme.typography.labelSmall,
                            color = colors.error,
                        )
                    }

                    if (state.purchaseError != null) {
                        Text(
                            text = tr(state.purchaseError!!),
                            style = PawQuestTheme.typography.labelSmall,
                            color = colors.error,
                        )
                    }
                }
            },
            confirmButton = {
                PawQuestButton(
                    text = if (state.busy) "Purchasing..." else "Confirm ($totalCost 🪙)",
                    onClick = viewModel::confirmPurchase,
                    enabled = !state.busy,
                    loading = state.busy,
                    size = ButtonSize.Small,
                )
            },
            dismissButton = {
                PawQuestButton(
                    text = "Cancel",
                    onClick = viewModel::dismissPurchase,
                    variant = ButtonVariant.Ghost,
                    enabled = !state.busy,
                    size = ButtonSize.Small,
                )
            },
        )
    }

    // Purchase Result Dialog
    if (state.lastPurchaseResult != null) {
        val result = state.lastPurchaseResult!!
        AlertDialog(
            onDismissRequest = viewModel::dismissResult,
            title = {
                Text(
                    text = tr(if (result.status == PurchaseStatus.PURCHASED) "Purchase Successful! 🎉" else "Purchase Failed"),
                    style = PawQuestTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                )
            },
            text = {
                if (result.status == PurchaseStatus.PURCHASED && result.receipt != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = tr("Item added to your inventory!"),
                            style = PawQuestTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = tr("Spent ${result.receipt.coinsSpent} Coins. Remaining balance: ${result.receipt.balanceAfter} Coins."),
                            style = PawQuestTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                    }
                } else {
                    Text(
                        text = tr(when (result.status) {
                            PurchaseStatus.INSUFFICIENT_BALANCE -> "You don't have enough coins for this purchase."
                            PurchaseStatus.ALREADY_OWNED -> "You already own this unique item."
                            PurchaseStatus.ITEM_UNAVAILABLE -> "This item is currently unavailable."
                            PurchaseStatus.INVALID_QUANTITY -> "The requested quantity is invalid."
                            PurchaseStatus.ACCOUNT_UNAVAILABLE -> "Account unavailable. Please try again."
                            PurchaseStatus.INVENTORY_FULL -> "Your backpack is full."
                            PurchaseStatus.REQUEST_CONFLICT -> "Purchase conflict occurred. Please retry."
                            PurchaseStatus.PURCHASED -> "Purchase complete."
                        }),
                        style = PawQuestTheme.typography.bodyMedium,
                        color = colors.error,
                    )
                }
            },
            confirmButton = {
                PawQuestButton(
                    text = "Got it!",
                    onClick = viewModel::dismissResult,
                    size = ButtonSize.Small,
                )
            },
        )
    }
    }
}

@Composable
private fun CategoryTabChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PawQuestTheme.colors
    Surface(
        onClick = onClick,
        shape = PawQuestTheme.shapes.pill,
        color = if (selected) colors.primaryContainer else colors.surface,
        border = BorderStroke(2.dp, colors.borderLight),
        shadowElevation = if (selected) 2.dp else 0.dp,
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            Text(
                text = tr(label),
                style = PawQuestTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) colors.primaryDark else colors.textSecondary,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ShopItemCard(
    item: CatalogItem,
    isOwned: Boolean,
    onBuyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PawQuestTheme.colors

    PawQuestCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Icon container
            Surface(
                shape = PawQuestTheme.shapes.card,
                color = getItemBgColor(item.category, colors),
                modifier = Modifier.size(84.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    com.pawquest.presentation.components.ItemArtwork(assetId=item.assetId,
                        imageVector = getItemIcon(item),
                        contentDescription = tr(item.name),
                        tint = getItemTintColor(item.category, colors),
                        modifier = Modifier.size(76.dp),
                    )
                }
            }

            // Name
            Text(
                text = tr(item.name),
                style = PawQuestTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )

            if(item.description.isNotBlank()) Text(tr(item.description),style=PawQuestTheme.typography.labelSmall,
                color=colors.textSecondary,maxLines=3,overflow=TextOverflow.Ellipsis,textAlign=TextAlign.Center)
            com.pawquest.presentation.components.itemPlacementLabel(item)?.let {
                Text(tr(it),style=PawQuestTheme.typography.labelSmall,color=colors.textSecondary,textAlign=TextAlign.Center)
            }

            // Price Chip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = PawQuestIcons.Coin,
                    contentDescription = null,
                    tint = colors.accentGoldDark,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = tr("${item.priceCoins}"),
                    style = PawQuestTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = colors.accentGoldDark,
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Action Button
            if (isOwned) {
                Surface(
                    shape = PawQuestTheme.shapes.button,
                    color = colors.secondary,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = tr("Owned ✓"),
                            style = PawQuestTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textSecondary,
                        )
                    }
                }
            } else {
                PawQuestButton(
                    text = "Buy",
                    onClick = onBuyClick,
                    variant = ButtonVariant.Primary,
                    size = ButtonSize.Small,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                )
            }
        }
    }
}

private fun getItemIcon(item: CatalogItem): ImageVector {
    return when (item.code.lowercase()) {
        "basic_food", "food" -> PawQuestIcons.Food
        "hat" -> PawQuestIcons.Hat
        "glasses" -> PawQuestIcons.Glasses
        "collar" -> PawQuestIcons.Collar
        "bed" -> PawQuestIcons.Bed
        "lamp" -> PawQuestIcons.Lamp
        "carpet" -> PawQuestIcons.Carpet
        else -> when (item.category) {
            ItemCategory.FOOD -> PawQuestIcons.Food
            ItemCategory.ACCESSORY -> PawQuestIcons.Hat
            ItemCategory.FURNITURE -> PawQuestIcons.Bed
        }
    }
}

private fun getItemBgColor(category: ItemCategory, colors: PawQuestColors): Color {
    return when (category) {
        ItemCategory.FOOD -> colors.accentGoldContainer // warm amber
        ItemCategory.ACCESSORY -> colors.primaryContainer // soft sky blue
        ItemCategory.FURNITURE -> colors.secondary // soft lavender
    }
}

private fun getItemTintColor(category: ItemCategory, colors: PawQuestColors): Color {
    return when (category) {
        ItemCategory.FOOD -> colors.accentGoldDark
        ItemCategory.ACCESSORY -> colors.primaryDark
        ItemCategory.FURNITURE -> colors.petXpDark
    }
}
