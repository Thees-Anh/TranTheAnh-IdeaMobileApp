package com.pawquest.di

import com.pawquest.data.repository.LocalPlaceholderRepository
import com.pawquest.domain.repository.PlaceholderRepository
import com.pawquest.domain.usecase.GetPlaceholder
import com.pawquest.domain.usecase.CreateQuest
import com.pawquest.domain.usecase.UpdateQuest
import com.pawquest.domain.usecase.DeleteQuest
import com.pawquest.domain.usecase.GetQuests
import com.pawquest.domain.repository.QuestRepository
import com.pawquest.data.repository.UnconfiguredQuestRepository
import com.pawquest.data.repository.UnconfiguredQuestSessionRepository
import com.pawquest.domain.repository.QuestSessionRepository
import com.pawquest.presentation.quests.QuestViewModel
import com.pawquest.domain.repository.FocusRepository
import com.pawquest.data.repository.UnconfiguredFocusRepository
import com.pawquest.domain.usecase.ManageFocusSessions
import com.pawquest.presentation.focus.FocusViewModel
import com.pawquest.domain.repository.PetRepository
import com.pawquest.data.repository.UnconfiguredPetRepository
import com.pawquest.domain.usecase.ManagePet
import com.pawquest.presentation.pet.PetViewModel
import com.pawquest.domain.repository.ItemRepository
import com.pawquest.domain.repository.InventoryRepository
import com.pawquest.data.repository.UnconfiguredItemRepository
import com.pawquest.data.repository.UnconfiguredInventoryRepository
import com.pawquest.presentation.placeholder.PlaceholderViewModel
import com.pawquest.data.auth.UnconfiguredAuthenticationRepository
import com.pawquest.domain.repository.AuthenticationRepository
import com.pawquest.domain.usecase.ManageAuthentication
import com.pawquest.presentation.auth.AuthenticationViewModel
import org.koin.dsl.koinApplication
import org.koin.dsl.module

val dataModule = module {
    single<PlaceholderRepository> { LocalPlaceholderRepository() }
}

val domainModule = module {
    factory { com.pawquest.domain.usecase.OpenItemCase(get()) }
    factory { com.pawquest.domain.usecase.PurchaseItem(get()) }
    factory { GetPlaceholder(get()) }
    factory { ManageAuthentication(get()) }
    factory { CreateQuest(get()) }
    factory { UpdateQuest(get()) }
    factory { DeleteQuest(get()) }
    factory { GetQuests(get()) }
    factory { ManageFocusSessions(get()) }
    factory { ManagePet(get()) }
}

val presentationModule = module {
    factory {com.pawquest.presentation.bistro.BistroViewModel(get(),get())}
    factory {com.pawquest.presentation.gacha.LuckyWheelViewModel(get(),get(),get(),get(),get(),get())}
    factory {com.pawquest.presentation.gacha.ItemExchangeViewModel(get(),get(),get(),get())}
    factory { com.pawquest.presentation.gacha.ItemCaseViewModel(get(),get(),get(),get(),get(),get(),openItemCase=get()) }
    factory { com.pawquest.presentation.shop.ShopViewModel(get(), get()) }
    factory { com.pawquest.presentation.inventory.InventoryViewModel(get(),get(),get()) }
    // The lifecycle ViewModelStore owns instances, not the application singleton scope.
    factory { (sectionId: String) -> PlaceholderViewModel(sectionId, get()) }
    factory { AuthenticationViewModel(get()) }
    factory { QuestViewModel(get(), get(), get(), get(), get()) }
    factory { FocusViewModel(get()) }
    factory { PetViewModel(get()) }
    factory { com.pawquest.presentation.progress.ProgressViewModel(get()) }
}

fun createAppContainer(
    authentication: AuthenticationRepository = UnconfiguredAuthenticationRepository(),
    quests: QuestRepository = UnconfiguredQuestRepository(),
    questSessions: QuestSessionRepository = UnconfiguredQuestSessionRepository(),
    focus: FocusRepository = UnconfiguredFocusRepository(),
    pets: PetRepository = UnconfiguredPetRepository(),
    items: ItemRepository = UnconfiguredItemRepository(),
    inventory: InventoryRepository = UnconfiguredInventoryRepository(),
    equipment: com.pawquest.domain.repository.EquipmentRepository = com.pawquest.data.repository.UnconfiguredEquipmentRepository(),
    room: com.pawquest.domain.repository.RoomRepository = com.pawquest.data.repository.UnconfiguredRoomRepository(),
    progress: com.pawquest.domain.repository.ProgressRepository = com.pawquest.data.repository.UnconfiguredProgressRepository(),
    itemCases: com.pawquest.domain.repository.ItemCaseRepository = com.pawquest.data.repository.UnconfiguredItemCaseRepository(),
    shards: com.pawquest.domain.repository.ShardRepository = com.pawquest.data.repository.UnconfiguredShardRepository(),
    pendingItemCases: com.pawquest.domain.repository.PendingItemCaseStore = com.pawquest.domain.repository.UnavailablePendingItemCaseStore(),
    pendingItemExchange: com.pawquest.domain.repository.PendingItemExchangeStore = com.pawquest.domain.repository.UnavailableItemExchangeStore(),
    luckyWheel: com.pawquest.domain.repository.LuckyWheelRepository = com.pawquest.data.repository.UnconfiguredLuckyWheelRepository(),
    petExchange: com.pawquest.domain.repository.PetShardExchangeRepository = com.pawquest.data.repository.UnconfiguredLuckyWheelRepository(),
    pendingLucky: com.pawquest.domain.repository.PendingLuckyOperationStore = com.pawquest.domain.repository.UnavailableLuckyOperationStore(),
) = koinApplication {
    modules(dataModule, domainModule, presentationModule, module {
        single<AuthenticationRepository> { authentication }
        single<QuestRepository> { quests }
        single<QuestSessionRepository> { questSessions }
        single<FocusRepository> { focus }
        single<PetRepository> { pets }
        single<ItemRepository> { items }
        single<InventoryRepository> { inventory }
        single<com.pawquest.domain.repository.EquipmentRepository> { equipment }
        single<com.pawquest.domain.repository.RoomRepository> { room }
        single<com.pawquest.domain.repository.ProgressRepository> { progress }
        single<com.pawquest.domain.repository.ItemCaseRepository> { itemCases }
        single<com.pawquest.domain.repository.ShardRepository> { shards }
        single<com.pawquest.domain.repository.PendingItemCaseStore> { pendingItemCases }
        single<com.pawquest.domain.repository.PendingItemExchangeStore> {pendingItemExchange}
        single<com.pawquest.domain.repository.LuckyWheelRepository> {luckyWheel}
        single<com.pawquest.domain.repository.PetShardExchangeRepository> {petExchange}
        single<com.pawquest.domain.repository.PendingLuckyOperationStore> {pendingLucky}
    })
}
