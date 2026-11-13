package com.pawquest

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pawquest.presentation.focus.FocusViewModel
import com.pawquest.presentation.focus.FocusScreen
import com.pawquest.presentation.pet.*
import com.pawquest.domain.model.FocusStatus
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.pawquest.di.createAppContainer
import com.pawquest.domain.model.SectionPlaceholder
import com.pawquest.domain.model.AuthenticationState
import com.pawquest.domain.model.UserAccount
import com.pawquest.presentation.auth.AuthenticationScreen
import com.pawquest.presentation.auth.AuthenticationViewModel
import com.pawquest.presentation.auth.AuthActionState
import com.pawquest.presentation.navigation.Destination
import com.pawquest.presentation.navigation.PawQuestScaffold
import com.pawquest.presentation.placeholder.PlaceholderViewModel
import com.pawquest.presentation.quests.QuestViewModel
import com.pawquest.presentation.quests.QuestManagementScreen
import com.pawquest.presentation.state.UiState
import com.pawquest.presentation.theme.PawQuestTheme as CorePawQuestTheme
import org.koin.core.KoinApplication
import org.koin.core.parameter.parametersOf

@Composable
fun App(
    container: KoinApplication? = null,
    petScene: (@Composable (Modifier) -> Unit)? = null,
    focusCompanion: (@Composable (Modifier) -> Unit)? = null,
    requestedRoute: String? = null,
    onRouteConsumed: ()->Unit = {},
    casePreview: (@Composable (Modifier)->Unit)? = null,
    caseAnimation: (@Composable (com.pawquest.presentation.gacha.ItemCaseAnimation, Modifier)->Unit)? = null,
    wheelPreview: (@Composable (com.pawquest.presentation.gacha.LuckyWheelAnimation, Modifier)->Unit)? = null,
) {
    val resolvedContainer = container ?: remember { createAppContainer() }
    DisposableEffect(resolvedContainer) {
        onDispose { if (container == null) resolvedContainer.close() }
    }
    val authentication = viewModel<AuthenticationViewModel> {
        resolvedContainer.koin.get<AuthenticationViewModel>()
    }
    val platform=com.pawquest.presentation.platform.LocalCompanionPlatform.current
    val session by authentication.authenticationState.collectAsStateWithLifecycle()
    LaunchedEffect(session,platform) {platform?.authenticationChanged(session)}
    val action by authentication.action.collectAsStateWithLifecycle()
    PawQuestTheme { com.pawquest.presentation.components.PortraitFrame {
        val signedIn = session as? AuthenticationState.SignedIn
        if (signedIn == null) {
            AuthenticationScreen(session, action, authentication::submit, authentication::retry,
                authentication::logout, authentication::clearMessages,
                authentication::requestPasswordReset, authentication::completePasswordReset)
        } else {
            key(signedIn.account.userId) {
                // Destroy every protected ViewModel and navigation state on logout/account switch.
                val owner = remember { object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                } }
                DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    ProtectedApp(resolvedContainer, signedIn.account, action, authentication::logout, authentication::deleteAccount, petScene,focusCompanion,requestedRoute,onRouteConsumed,casePreview,caseAnimation,wheelPreview)
                }
            }
        }
    } }
}

@Composable
private fun ProtectedApp(
    container: KoinApplication,
    account: UserAccount,
    action: AuthActionState,
    onLogout: () -> Unit,
    onDeleteAccount: (String) -> Unit,
    petScene: (@Composable (Modifier) -> Unit)?,
    focusCompanion: (@Composable (Modifier) -> Unit)?,
    requestedRoute: String?,
    onRouteConsumed: ()->Unit,
    casePreview: (@Composable (Modifier)->Unit)?,
    caseAnimation: (@Composable (com.pawquest.presentation.gacha.ItemCaseAnimation, Modifier)->Unit)?,
    wheelPreview: (@Composable (com.pawquest.presentation.gacha.LuckyWheelAnimation, Modifier)->Unit)?,
) {
    var selectedRoute by rememberSaveable { mutableStateOf(Destination.Home.route) }
    var petRoomTab by rememberSaveable { mutableStateOf(com.pawquest.presentation.home.PetRoomTab.ROOM.name) }
    var exploreCard by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var progressOpen by rememberSaveable { mutableStateOf(false) }
    fun openWardrobe() { petRoomTab = com.pawquest.presentation.home.PetRoomTab.WARDROBE.name; selectedRoute = Destination.PetRoom.route }
    fun openExplore(card: com.pawquest.presentation.explore.ExploreCard?) { exploreCard = card?.name; selectedRoute = Destination.Explore.route }
    val destination = Destination.fromRoute(selectedRoute)
    val model = viewModel<PlaceholderViewModel>(key = destination.route) {
        container.koin.get<PlaceholderViewModel> { parametersOf(destination.route) }
    }
    val state by model.state.collectAsStateWithLifecycle()
    val quests = viewModel<QuestViewModel>(key = "quest-management") { container.koin.get<QuestViewModel>() }
    val pet=viewModel<PetViewModel>(key="pet-state") { container.koin.get<PetViewModel>() }
    val petState by pet.state.collectAsStateWithLifecycle()
    val bistro=viewModel<com.pawquest.presentation.bistro.BistroViewModel>(key="bistro") {container.koin.get<com.pawquest.presentation.bistro.BistroViewModel>()}
    val bistroState by bistro.state.collectAsStateWithLifecycle()
    val platform=com.pawquest.presentation.platform.LocalCompanionPlatform.current
    LaunchedEffect(petState.snapshot,platform) {petState.snapshot?.let {platform?.petChanged(it)}}
    val focus = viewModel<FocusViewModel>(key = "focus-session") { container.koin.get<FocusViewModel>() }
    val focusState by focus.state.collectAsStateWithLifecycle()
    val progress=viewModel<com.pawquest.presentation.progress.ProgressViewModel>(key="habit-progress") {container.koin.get<com.pawquest.presentation.progress.ProgressViewModel>()}
    val progressState by progress.state.collectAsStateWithLifecycle()
    LaunchedEffect(focusState.session?.id,focusState.session?.status,focusState.remainingSeconds,focusState.visible,petState.snapshot?.pet?.name) {
        platform?.focusChanged(focusState.session?.let {
            com.pawquest.domain.model.FocusPresence(it.id,petState.snapshot?.pet?.name ?: account.petName,focusState.remainingSeconds,
                focusState.visible && it.status==FocusStatus.ACTIVE && focusState.remainingSeconds>0,
                running=it.status==FocusStatus.ACTIVE && focusState.remainingSeconds>0)
        })
    }
    // Null means "the UI went away", not "the session stopped": the device keeps its end-of-Focus alert.
    DisposableEffect(platform) {onDispose {platform?.focusChanged(null)}}
    LaunchedEffect(requestedRoute) {
        requestedRoute?.let {
            val target=Destination.fromRoute(it)
            selectedRoute=target.route
            if(it=="inventory") petRoomTab=com.pawquest.presentation.home.PetRoomTab.WARDROBE.name
            if(it=="pet_home") petRoomTab=com.pawquest.presentation.home.PetRoomTab.ROOM.name
            if(target==Destination.Explore) exploreCard=null
            if(it=="focus") focus.show() else focus.hide()
            if(target==Destination.Quests) quests.openFromReminder()
            onRouteConsumed()
        }
    }
    val questState by quests.state.collectAsStateWithLifecycle()
    LaunchedEffect(questState.items) {
        if(com.pawquest.domain.model.PetArtworkRegistry.find(petState.snapshot?.pet?.rendererKey ?: "")?.storyEmotions==true) pet.refresh()
    }
    LaunchedEffect(questState.items,focusState.session?.status) {platform?.questsChanged()}
    LifecycleResumeEffect(focus) {
        focus.foreground(true)
        pet.foreground(true)
        onPauseOrDispose { focus.foreground(false); pet.foreground(false) }
    }
    LaunchedEffect(questState.requestedFocusQuestId) {
        questState.requestedFocusQuestId?.let { focus.start(it); quests.consumeFocusRequest() }
    }
    LaunchedEffect(focusState.session?.status,focusState.snapshot?.eligible) {
        if (focusState.session?.status == FocusStatus.CLAIMED) { quests.refreshProgress(); pet.refresh() }
        else if(focusState.snapshot?.eligible==true) { pet.refresh(); quests.refreshProgress() }
    }
    val shop = viewModel<com.pawquest.presentation.shop.ShopViewModel>(key = "shop-catalog") {
        container.koin.get<com.pawquest.presentation.shop.ShopViewModel>()
    }
    val shopState by shop.state.collectAsStateWithLifecycle()
    val inventory = viewModel<com.pawquest.presentation.inventory.InventoryViewModel>(key = "inventory-items") {
        container.koin.get<com.pawquest.presentation.inventory.InventoryViewModel>()
    }
    val inventoryState by inventory.state.collectAsStateWithLifecycle()
    LaunchedEffect(bistroState.receipt?.id) {
        if(bistroState.receipt!=null) {shop.dismissResult();pet.refresh();inventory.loadInventory();progress.refresh()}
    }
    val itemCases=viewModel<com.pawquest.presentation.gacha.ItemCaseViewModel>(key="item-case") { container.koin.get<com.pawquest.presentation.gacha.ItemCaseViewModel>() }
    val exchange=viewModel<com.pawquest.presentation.gacha.ItemExchangeViewModel>(key="item-exchange") {container.koin.get<com.pawquest.presentation.gacha.ItemExchangeViewModel>()}
    val wheel=viewModel<com.pawquest.presentation.gacha.LuckyWheelViewModel>(key="lucky-wheel") {container.koin.get<com.pawquest.presentation.gacha.LuckyWheelViewModel>()}
    val wheelState by wheel.state.collectAsStateWithLifecycle()
    // Back online after showing cached data: replace every cached view with confirmed server state.
    val offlineFrom = com.pawquest.data.offline.ConnectionStatus.offlineDataFrom
    var wasOffline by remember { mutableStateOf(false) }
    LaunchedEffect(offlineFrom) {
        if (offlineFrom != null) wasOffline = true
        else if (wasOffline) { wasOffline = false; pet.refresh(); quests.refresh(); progress.refresh(); inventory.loadInventory(); focus.reconcile(); itemCases.refresh(); wheel.refresh() }
    }
    LifecycleResumeEffect(wheel) {wheel.refresh();onPauseOrDispose {wheel.state.value.result?.let {wheel.animationFinished(it.transactionId)}}}
    LaunchedEffect(wheelState.result?.transactionId,wheelState.redemption?.transactionId) {
        if(wheelState.result!=null || wheelState.redemption!=null) {
            shop.dismissResult();pet.refresh();pet.loadCollection();inventory.loadInventory();itemCases.refresh();exchange.refresh()
        }
    }
    LaunchedEffect(petState.snapshot?.coins) {wheel.refresh()}
    val exchangeState by exchange.state.collectAsStateWithLifecycle()
    LaunchedEffect(exchangeState.receipt?.id) {
        exchangeState.receipt?.let {receipt->itemCases.refreshAfterExchange(receipt.balanceAfter);inventory.loadInventory();wheel.refresh()}
    }
    val itemCaseState by itemCases.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(itemCases) { itemCases.refresh(); onPauseOrDispose { } }
    LaunchedEffect(itemCaseState.result?.transactionId) {
        if(itemCaseState.result!=null) {
            wheel.refresh()
            shop.dismissResult() // Do not let an old Shop receipt mask a later balance.
            pet.refresh()
            inventory.loadInventory()
        }
    }
    LaunchedEffect(petState.snapshot?.pet?.progress?.totalXp,focusState.session?.status,focusState.snapshot?.eligible) {
        progress.refresh()
        if(focusState.session?.status==FocusStatus.CLAIMED) inventory.loadInventory()
    }
    LifecycleResumeEffect(progress) {progress.refresh();onPauseOrDispose {}}
    LaunchedEffect(petState.lastActionResult?.requestId) {
        if(petState.lastActionResult?.applied==true) inventory.loadInventory()
    }
    LifecycleResumeEffect(inventory) {
        inventory.loadInventory()
        onPauseOrDispose { }
    }

    LaunchedEffect(selectedRoute) {
        if (selectedRoute == Destination.Explore.route) { shop.loadCatalog(); itemCases.refresh();wheel.refresh() }
        if (selectedRoute == Destination.PetRoom.route) inventory.loadInventory()
        if (selectedRoute == Destination.Home.route) progress.refresh()
    }

    LaunchedEffect(shopState.lastPurchaseResult) {
        if (shopState.lastPurchaseResult?.status == com.pawquest.domain.model.PurchaseStatus.PURCHASED) {
            inventory.loadInventory()
            pet.refresh()
        }
    }

    if(!bistroState.animationPending) com.pawquest.presentation.rewards.ProgressCelebration(progressState,
        onAcknowledge={progress.acknowledge(it);pet.refresh();inventory.loadInventory()},onLater=progress::later,
        afterCoins=focusState.session?.status==FocusStatus.CLAIMED)
    if (focusState.visible) {
        FocusScreen(focusState,focus,
            onBack={ selectedRoute=Destination.Quests.route; if(questState.page==com.pawquest.presentation.quests.QuestPage.DETAILS) quests.back();focus.hide() },
            onPetHome={ petRoomTab=com.pawquest.presentation.home.PetRoomTab.ROOM.name; selectedRoute=Destination.PetRoom.route; focus.hide() },companion=focusCompanion,
            walletBalance=petState.snapshot?.coins ?: account.coins)
        return
    }
    val currentCoins = petState.snapshot?.coins ?: account.coins
    CompositionLocalProvider(com.pawquest.presentation.components.LocalOpenSettings provides { settingsOpen = true },
        LocalPetController provides PetController(petState,pet::care,pet::retry,pet::loadCollection,pet::selectSpecies,pet::companionVisible),
        com.pawquest.presentation.bistro.LocalBistro provides bistro,
        com.pawquest.presentation.components.LocalShardBalances provides com.pawquest.presentation.components.ShardBalances(
            pet=wheelState.balances?.petShards,item=itemCaseState.itemShards),
        com.pawquest.presentation.inventory.LocalEquipment provides inventoryState.equipment,
        com.pawquest.presentation.inventory.LocalRoom provides inventoryState.room) {
    androidx.compose.runtime.CompositionLocalProvider(com.pawquest.presentation.progress.LocalProgress provides
        com.pawquest.presentation.progress.ProgressController(progressState,{progress.refresh()},progress::moveMonth)) {
    PetFeedbackDialog(petState,pet::dismissFeedback,
        onShop={openExplore(com.pawquest.presentation.explore.ExploreCard.BISTRO)},onQuests={selectedRoute=Destination.Quests.route})
    PawQuestScaffold(
        destination = destination,
        state = state,
        onNavigate = { selectedRoute = it.route },
        onRetry = model::retry,
        homeContent = { modifier ->
            com.pawquest.presentation.home.TodayScreen(
                displayName = account.displayName,
                coins = currentCoins,
                quests = questState,
                onStartQuest = quests::startQuest,
                onSeeAllQuests = { quests.selectFilter(com.pawquest.presentation.quests.QuestFilter.ALL); selectedRoute = Destination.Quests.route },
                onCreateQuest = { quests.openCreate(); selectedRoute = Destination.Quests.route },
                onVisitRoom = { petRoomTab = com.pawquest.presentation.home.PetRoomTab.ROOM.name; selectedRoute = Destination.PetRoom.route },
                onOpenProgress = { progress.refresh(); progressOpen = true },
                modifier = modifier,
            )
        },
        questContent = { QuestManagementScreen(quests, it, if(focusState.session!=null || focusState.error!=null) focus::show else null,focusState) },
        petRoomContent = { modifier ->
            com.pawquest.presentation.home.PetRoomScreen(
                tab = com.pawquest.presentation.home.PetRoomTab.valueOf(petRoomTab),
                onTabChange = { petRoomTab = it.name; if (it == com.pawquest.presentation.home.PetRoomTab.WARDROBE) inventory.loadInventory() },
                modifier = modifier,
                room = { roomModifier ->
                    if (petScene != null) petScene(roomModifier)
                    else com.pawquest.presentation.home.PetHomeScreen(modifier = roomModifier) {}
                },
                wardrobe = { wardrobeModifier ->
                    com.pawquest.presentation.inventory.InventoryScreen(
                        viewModel = inventory,
                        onNavigateToShop = { openExplore(com.pawquest.presentation.explore.ExploreCard.COZY_SHOP) },
                        onUseItem = { invItem ->
                            if (invItem.item.category == com.pawquest.domain.model.ItemCategory.FOOD) {
                                pet.care(com.pawquest.domain.model.PetCareAction.FEED)
                            }
                        },
                        modifier = wardrobeModifier,
                        showHeader = false,
                    )
                },
            )
        },
        exploreContent = { modifier ->
            val openCard = exploreCard?.let { name -> com.pawquest.presentation.explore.ExploreCard.entries.firstOrNull { it.name == name } }
            com.pawquest.presentation.explore.ExploreScreen(
                coins = currentCoins,
                openCard = openCard,
                onOpen = { card ->
                    when (card) {
                        com.pawquest.presentation.explore.ExploreCard.BISTRO -> shop.selectSection(com.pawquest.presentation.shop.ShopSection.FOOD)
                        com.pawquest.presentation.explore.ExploreCard.MYSTERY_BOX -> shop.selectSection(com.pawquest.presentation.shop.ShopSection.ITEM_CASE)
                        com.pawquest.presentation.explore.ExploreCard.LUCKY_WHEEL -> shop.selectSection(com.pawquest.presentation.shop.ShopSection.LUCKY_WHEEL)
                        com.pawquest.presentation.explore.ExploreCard.COZY_SHOP, com.pawquest.presentation.explore.ExploreCard.EXCHANGE ->
                            shop.selectSection(com.pawquest.presentation.shop.ShopSection.SHARD_EXCHANGE)
                        com.pawquest.presentation.explore.ExploreCard.COLLECTION -> { inventory.loadInventory(); pet.loadCollection() }
                    }
                    exploreCard = card.name
                },
                onBack = { exploreCard = null },
                modifier = modifier,
            ) { card, cardModifier ->
              if (card == com.pawquest.presentation.explore.ExploreCard.COLLECTION) {
                com.pawquest.presentation.inventory.PetCollectionContent(cardModifier.padding(horizontal = 16.dp))
              } else {
            com.pawquest.presentation.shop.ShopScreen(
                viewModel = shop,
                userCoins = currentCoins,
                ownedItemIds = inventoryState.items.map { item -> item.item.id }.toSet(),
                onNavigateToInventory = { openWardrobe() },
                onNavigateToQuests = { selectedRoute = Destination.Quests.route },
                modifier = cardModifier,
                showSectionTabs = false,
                exchangeCategory = if (card == com.pawquest.presentation.explore.ExploreCard.COZY_SHOP) "Accessories" else "Pets",
                itemShards = itemCaseState.itemShards,
                shardsLoading = itemCaseState.refreshing || itemCaseState.phase==com.pawquest.presentation.gacha.ItemCasePhase.Loading,
                itemCaseModel = itemCases,
                casePreview = casePreview,
                caseAnimation = caseAnimation,
                exchangeModel=exchange,
                luckyWheelModel=wheel,
                wheelPreview=wheelPreview,
                onPetCollection={inventory.loadInventory();pet.loadCollection();exploreCard=com.pawquest.presentation.explore.ExploreCard.COLLECTION.name},
                onViewCaseItem = { id ->
                    inventory.selectCategory(inventory.state.value.items.firstOrNull {row->row.item.id==id}?.item?.category)
                    inventory.loadInventory(); openWardrobe()
                },
                actionableCaseItemIds = if(inventoryState.equipmentBusy || inventoryState.roomBusy ||
                    inventoryState.pendingEquipment!=null || inventoryState.pendingRoom!=null) emptySet() else inventoryState.items.filter { row ->
                    row.quantity==1 && when(row.item.category) {
                        com.pawquest.domain.model.ItemCategory.ACCESSORY -> com.pawquest.domain.model.accessorySlot(row.item.code)!=null && row.item.id !in inventoryState.equippedItemIds
                        com.pawquest.domain.model.ItemCategory.FURNITURE -> com.pawquest.domain.model.furnitureSlot(row.item.code)!=null && row.item.id !in inventoryState.placedItemIds
                        else -> false
                    }
                }.map {row->row.item.id}.toSet(),
                onUseCaseReward = { id ->
                    val current=inventory.state.value
                    current.items.firstOrNull {row->row.item.id==id}?.let { row ->
                        when(row.item.category) {
                            com.pawquest.domain.model.ItemCategory.ACCESSORY -> if(id !in current.equippedItemIds) inventory.toggleEquip(row)
                            com.pawquest.domain.model.ItemCategory.FURNITURE -> if(id !in current.placedItemIds) inventory.togglePlace(row)
                            else -> Unit
                        }
                        inventory.selectCategory(row.item.category); openWardrobe()
                    }
                },
            )
              }
            }
        },
        onReconnect = { pet.refresh(); quests.refresh(); progress.refresh(); inventory.loadInventory(); focus.reconcile(); itemCases.refresh(); wheel.refresh() },
    )
    if (settingsOpen) com.pawquest.presentation.components.PawQuestSheet(onDismiss = { settingsOpen = false }) {
        com.pawquest.presentation.profile.SettingsSheetContent(
            account = account,
            petState = petState,
            userCoins = currentCoins,
            onLogout = onLogout,
            logoutBusy = action.busy,
            logoutError = action.error,
            onDeleteAccount = onDeleteAccount,
        )
    }
    if (progressOpen) com.pawquest.presentation.components.PawQuestSheet(onDismiss = { progressOpen = false }) {
        com.pawquest.presentation.profile.ProgressSheetContent()
    }
    }
    }
}

/**
 * Top-level theme wrapper preserving backwards compatibility with existing calls.
 */
@Composable
fun PawQuestTheme(content: @Composable () -> Unit) {
    CorePawQuestTheme(content = content)
}

@Composable
fun PreviewPawQuest() {
    PawQuestTheme {
        PawQuestScaffold(
            destination = Destination.Home,
            state = UiState.Content(SectionPlaceholder("Home page", "Today's quests will appear here.")),
            onNavigate = {},
            onRetry = {},
        )
    }
}

@Composable
fun PreviewShowcase() {
    PawQuestTheme {
        PawQuestScaffold(
            destination = Destination.Explore,
            state = UiState.Content(SectionPlaceholder("Design System", "PawQuest 3D mobile design system showcase.")),
            onNavigate = {},
            onRetry = {},
        )
    }
}
