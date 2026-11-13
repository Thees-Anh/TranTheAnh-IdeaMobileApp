package com.pawquest.presentation.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pawquest.domain.model.*
import com.pawquest.domain.repository.ItemRepository
import com.pawquest.domain.usecase.PurchaseItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

enum class ShopSection(val label: String) { FOOD("Food Shop"), ITEM_CASE("Mystery Box"), LUCKY_WHEEL("Lucky Wheel"), SHARD_EXCHANGE("Shard Exchange") }
data class ShopState(
    val catalog: List<CatalogItem> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val selectedCategory: ItemCategory = ItemCategory.FOOD,
    val pendingPurchase: CatalogItem? = null,
    val purchaseQuantity: Int = 1,
    val busy: Boolean = false,
    val lastPurchaseResult: PurchaseResult? = null,
    val purchaseError: String? = null,
    val pendingRequest: PurchaseRequest? = null,
    val mysteryCaseSelected: Boolean = false,
    val section: ShopSection = ShopSection.FOOD,
) {
    val pending: PurchaseRequest? get() = pendingRequest
    val result: PurchaseResult? get() = lastPurchaseResult
}

/** Scope to the authenticated user. An ambiguous failure retains its retry identity. */
class ShopViewModel(
    private val itemRepository: ItemRepository,
    private val purchaseItem: PurchaseItem,
) : ViewModel() {
    constructor(purchaseItem: PurchaseItem) : this(
        itemRepository = object : ItemRepository {
            override suspend fun getItems(): List<CatalogItem> = emptyList()
            override suspend fun purchase(request: PurchaseRequest): PurchaseResult = purchaseItem(request)
        },
        purchaseItem = purchaseItem,
    )

    private val mutable = MutableStateFlow(ShopState())
    val state = mutable.asStateFlow()

    init {
        loadCatalog()
    }

    fun loadCatalog() {
        mutable.value = state.value.copy(loading = state.value.catalog.isEmpty(), error = null)
        viewModelScope.launch {
            try {
                val items = itemRepository.getItems().filter { it.active }
                mutable.value = state.value.copy(catalog = items, loading = false, error = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.value = state.value.copy(
                    loading = false,
                    error = "Couldn't load shop items. Please check your connection and retry.",
                )
            }
        }
    }

    fun selectCategory(category: ItemCategory) {
        // Legacy callers route non-food acquisition to the existing Exchange.
        mutable.value = state.value.copy(selectedCategory = ItemCategory.FOOD,mysteryCaseSelected=false,
            section=if(category==ItemCategory.FOOD) ShopSection.FOOD else ShopSection.SHARD_EXCHANGE)
    }
    fun selectSection(section: ShopSection) {mutable.value=state.value.copy(section=section,selectedCategory=ItemCategory.FOOD,mysteryCaseSelected=section==ShopSection.ITEM_CASE)}
    fun selectMysteryCase() {selectSection(ShopSection.ITEM_CASE)}

    fun openPurchase(item: CatalogItem) {
        if(item.category!=ItemCategory.FOOD) return
        mutable.value = state.value.copy(
            pendingPurchase = item,
            purchaseQuantity = 1,
            purchaseError = null,
        )
    }

    fun setQuantity(quantity: Int) {
        val max = if (state.value.pendingPurchase?.stackable == true) 100 else 1
        mutable.value = state.value.copy(
            purchaseQuantity = quantity.coerceIn(1, max),
        )
    }

    fun dismissPurchase() {
        if (!state.value.busy) {
            mutable.value = state.value.copy(
                pendingPurchase = null,
                purchaseQuantity = 1,
                purchaseError = null,
            )
        }
    }

    fun confirmPurchase() {
        val item = state.value.pendingPurchase ?: return
        if (state.value.busy || state.value.pendingRequest != null) return

        val request = PurchaseRequest(item.id, state.value.purchaseQuantity, Uuid.random().toString())
        try {
            request.validated()
        } catch (_: IllegalArgumentException) {
            mutable.value = state.value.copy(purchaseError = "Choose a valid quantity (1–100).")
            return
        }
        submit(request)
    }

    fun purchase(itemId: String, quantity: Int) {
        if(state.value.catalog.none {it.id==itemId && it.category==ItemCategory.FOOD}) return
        if (state.value.busy || state.value.pendingRequest != null) return
        val request = PurchaseRequest(itemId, quantity, Uuid.random().toString())
        try {
            request.validated()
        } catch (_: IllegalArgumentException) {
            mutable.value = state.value.copy(purchaseError = "Choose a valid item and quantity (1–100).")
            return
        }
        submit(request)
    }

    fun retry() {
        if (!state.value.busy) state.value.pendingRequest?.let(::submit)
    }

    fun dismissResult() {
        mutable.value = state.value.copy(
            lastPurchaseResult = null,
            purchaseError = null,
        )
    }

    private fun submit(request: PurchaseRequest) {
        mutable.value = state.value.copy(busy = true, pendingRequest = request, purchaseError = null, error = null)
        viewModelScope.launch {
            try {
                val result = purchaseItem(request)
                mutable.value = state.value.copy(
                    busy = false,
                    pendingRequest = null,
                    pendingPurchase = null,
                    lastPurchaseResult = result,
                    purchaseError = null,
                    error = null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val errorMsg = "Couldn't confirm your purchase. Retry to check the same request safely."
                mutable.value = state.value.copy(
                    busy = false,
                    pendingRequest = request,
                    purchaseError = errorMsg,
                    error = errorMsg,
                )
            }
        }
    }
}
