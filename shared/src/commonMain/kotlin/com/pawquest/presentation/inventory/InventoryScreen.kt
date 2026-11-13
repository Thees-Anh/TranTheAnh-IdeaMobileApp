package com.pawquest.presentation.inventory

import com.pawquest.localization.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.pawquest.domain.model.InventoryItem
import com.pawquest.domain.model.ItemCategory
import com.pawquest.presentation.components.ButtonSize
import com.pawquest.presentation.components.ButtonVariant
import com.pawquest.presentation.components.PawQuestButton
import com.pawquest.presentation.components.PawQuestCard
import com.pawquest.presentation.components.PawQuestEmpty
import com.pawquest.presentation.components.PawQuestError
import com.pawquest.presentation.components.PawQuestLoading
import com.pawquest.presentation.icons.PawQuestIcons
import com.pawquest.presentation.theme.PawQuestColors
import com.pawquest.presentation.theme.PawQuestTheme

@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel,
    modifier: Modifier = Modifier,
    onNavigateToShop: (() -> Unit)? = null,
    onUseItem: ((InventoryItem) -> Unit)? = null,
    showPetCollection: Boolean = false,
    onPetCollectionShown: ()->Unit = {},
    showHeader: Boolean = true,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState=rememberLazyGridState()
    LaunchedEffect(state.selectedCategory) {gridState.scrollToItem(0)}
    val colors = PawQuestTheme.colors
    var showPets by rememberSaveable {mutableStateOf(false)}
    LaunchedEffect(showPetCollection) {if(showPetCollection) {showPets=true;onPetCollectionShown()}}

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Top Header: Title, Navigation smoke subtitle, Item Count Badge
        if(showHeader) Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = tr("Inventory"),
                    style = PawQuestTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                )
                Text(
                    text = tr("Your collection of little comforts."),
                    style = PawQuestTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = PawQuestTheme.shapes.pill,
                color = colors.primaryContainer,
                border = BorderStroke(1.dp, colors.primaryDark),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = PawQuestIcons.Backpack,
                        contentDescription = tr("Items count"),
                        tint = colors.primaryDark,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = tr("${state.items.size} Items"),
                        style = PawQuestTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.primaryDark,
                    )
                }
            }
        }

        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            PawQuestButton("Items",{showPets=false},variant=if(!showPets) ButtonVariant.Primary else ButtonVariant.Outlined,modifier=Modifier.weight(1f))
            PawQuestButton("Pets",{showPets=true},variant=if(showPets) ButtonVariant.Primary else ButtonVariant.Outlined,modifier=Modifier.weight(1f))
        }
        if(showPets) {
            PetCollectionContent(Modifier.weight(1f))
            return@Column
        }
        // Action Feedback Banner
        if (state.actionMessage != null) {
            Surface(
                shape = PawQuestTheme.shapes.card,
                color = colors.primaryContainer,
                border = BorderStroke(1.dp, colors.primaryDark),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = tr(state.actionMessage!!),
                        style = PawQuestTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.primaryDark,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = viewModel::dismissActionMessage,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = PawQuestIcons.Close,
                            contentDescription = tr("Dismiss"),
                            tint = colors.primaryDark,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        if(state.equipmentBusy) Text(tr("Saving equipment…"),style=PawQuestTheme.typography.bodyMedium)
        if(state.roomBusy) Text(tr("Saving room…"),style=PawQuestTheme.typography.bodyMedium)
        if(state.error!=null && state.items.isNotEmpty()) {
            Text(tr(state.error!!),style=PawQuestTheme.typography.bodyMedium,color=colors.textPrimary)
            PawQuestButton(text="Retry",onClick={
                if(state.pendingRoom!=null) viewModel.retryRoom()
                else if(state.pendingEquipment!=null) viewModel.retryEquipment() else viewModel.loadInventory()
            },enabled=!state.equipmentBusy && !state.roomBusy)
        }
        // Category Filter Chips (All, Food, Accessories, Furniture)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InventoryCategoryChip(
                label = "All",
                icon = PawQuestIcons.Sparkle,
                selected = state.selectedCategory == null,
                onClick = { viewModel.selectCategory(null) },
                modifier = Modifier.weight(1f),
            )
            InventoryCategoryChip(
                label = "Food",
                icon = PawQuestIcons.Food,
                selected = state.selectedCategory == ItemCategory.FOOD,
                onClick = { viewModel.selectCategory(ItemCategory.FOOD) },
                modifier = Modifier.weight(1f),
            )
            InventoryCategoryChip(
                label = "Wear",
                icon = PawQuestIcons.Hat,
                selected = state.selectedCategory == ItemCategory.ACCESSORY,
                onClick = { viewModel.selectCategory(ItemCategory.ACCESSORY) },
                modifier = Modifier.weight(1f),
            )
            InventoryCategoryChip(
                label = "Home",
                icon = PawQuestIcons.Bed,
                selected = state.selectedCategory == ItemCategory.FURNITURE,
                onClick = { viewModel.selectCategory(ItemCategory.FURNITURE) },
                modifier = Modifier.weight(1f),
            )
        }

        // Main Body: Loading, Error, or Items Grid
        when {
            state.loading && state.items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    PawQuestLoading(message = "Opening backpack...")
                }
            }
            state.error != null && state.items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    PawQuestError(
                        message = state.error ?: "Unable to load inventory.",
                        onRetry = viewModel::loadInventory,
                    )
                }
            }
            state.items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    PawQuestEmpty(
                        title = "Backpack Empty",
                        message = "Your inventory will appear here once you acquire items from the Shop!",
                        icon = PawQuestIcons.Backpack,
                        actionText = "Visit Shop",
                        onActionClick = onNavigateToShop,
                    )
                }
            }
            else -> {
                val filteredItems = if (state.selectedCategory == null) {
                    state.items
                } else {
                    state.items.filter { it.item.category == state.selectedCategory }
                }

                if (filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        PawQuestEmpty(
                            title = "No Items Found",
                            message = "You don't have any items in this category yet.",
                            icon = PawQuestIcons.Backpack,
                            actionText = "Visit Shop",
                            onActionClick = onNavigateToShop,
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
                        items(filteredItems, key = { it.id }) { inventoryItem ->
                            val isEquipped = state.equippedItemIds.contains(inventoryItem.item.id)
                            val isPlaced = state.placedItemIds.contains(inventoryItem.item.id)

                            InventoryItemCard(
                                inventoryItem = inventoryItem,
                                isEquipped = isEquipped,
                                isPlaced = isPlaced,
                                equipmentEnabled = !state.equipmentBusy && state.pendingEquipment==null && !state.roomBusy && state.pendingRoom==null,
                                onUseClick = {
                                    onUseItem?.invoke(inventoryItem)
                                },
                                onEquipToggle = { viewModel.toggleEquip(inventoryItem) },
                                onPlaceToggle = { viewModel.togglePlace(inventoryItem) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryCategoryChip(
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
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        ) {
            Text(
                text = tr(label),
                style = PawQuestTheme.typography.labelSmall.copy(
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
private fun InventoryItemCard(
    inventoryItem: InventoryItem,
    isEquipped: Boolean,
    isPlaced: Boolean,
    equipmentEnabled: Boolean,
    onUseClick: () -> Unit,
    onEquipToggle: () -> Unit,
    onPlaceToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PawQuestTheme.colors
    val item = inventoryItem.item

    PawQuestCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Icon container with quantity pill overlay
            Box(contentAlignment = Alignment.TopEnd) {
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
                // Quantity Badge
                Surface(
                    shape = PawQuestTheme.shapes.pill,
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.borderLight),
                    shadowElevation = 1.dp,
                ) {
                    Text(
                        text = tr("x${inventoryItem.quantity}"),
                        style = PawQuestTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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

            // Status Badge (Equipped or Placed)
            when {
                item.category == ItemCategory.ACCESSORY && isEquipped -> {
                    Surface(
                        shape = PawQuestTheme.shapes.pill,
                        color = colors.primaryContainer,
                        border = BorderStroke(1.dp, colors.primaryDark),
                    ) {
                        Text(
                            text = tr("Equipped ✨"),
                            style = PawQuestTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.primaryDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                item.category == ItemCategory.FURNITURE && isPlaced -> {
                    Surface(
                        shape = PawQuestTheme.shapes.pill,
                        color = colors.secondary,
                        border = BorderStroke(1.dp, colors.petXpDark),
                    ) {
                        Text(
                            text = tr("Placed 🛋️"),
                            style = PawQuestTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.petXpDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                else -> {
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }

            // Contextual Action Button
            when (item.category) {
                ItemCategory.FOOD -> {
                    PawQuestButton(
                        text = "Feed Pet",
                        onClick = onUseClick,
                        variant = ButtonVariant.Primary,
                        size = ButtonSize.Small,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    )
                }
                ItemCategory.ACCESSORY -> {
                    PawQuestButton(
                        text = if (isEquipped) "Unequip" else "Equip",
                        onClick = onEquipToggle,
                        enabled = equipmentEnabled,
                        variant = if (isEquipped) ButtonVariant.Outlined else ButtonVariant.Primary,
                        size = ButtonSize.Small,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    )
                }
                ItemCategory.FURNITURE -> {
                    PawQuestButton(
                        text = if (isPlaced) "Store" else "Place",
                        onClick = onPlaceToggle,
                        enabled = equipmentEnabled,
                        variant = if (isPlaced) ButtonVariant.Outlined else ButtonVariant.Primary,
                        size = ButtonSize.Small,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    )
                }
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
