package com.pawquest.presentation.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pawquest.domain.model.InventoryItem
import com.pawquest.domain.model.ItemCategory
import com.pawquest.domain.repository.InventoryRepository
import com.pawquest.domain.repository.EquipmentRepository
import com.pawquest.domain.model.*
import com.pawquest.domain.usecase.EquipItem
import com.pawquest.domain.usecase.UnequipItem
import com.pawquest.domain.repository.RoomRepository
import com.pawquest.domain.usecase.PlaceFurniture
import com.pawquest.domain.usecase.StoreFurniture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InventoryState(
    val items: List<InventoryItem> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val selectedCategory: ItemCategory? = null, // null = All
    val equippedItemIds: Set<String> = emptySet(),
    val placedItemIds: Set<String> = emptySet(),
    val actionMessage: String? = null,
    val equipment: List<EquippedItem> = emptyList(),
    val equipmentBusy: Boolean = false,
    val pendingEquipment: EquipmentCommand? = null,
    val room: List<RoomFurniture> = emptyList(),
    val roomBusy: Boolean = false,
    val pendingRoom: RoomCommand? = null,
)
data class RoomCommand(val slot: FurnitureSlot,val itemId: String?)
data class EquipmentCommand(val slot: EquipmentSlot,val itemId: String?)

class InventoryViewModel(
    private val inventoryRepository: InventoryRepository,
    private val equipmentRepository: EquipmentRepository,
    private val roomRepository: RoomRepository,
) : ViewModel() {
    private val mutable = MutableStateFlow(InventoryState())
    val state = mutable.asStateFlow()
    private var revision=0

    init {
        loadInventory()
    }

    fun loadInventory() {
        if(actionsBlocked()) return
        val expected=++revision
        mutable.value = state.value.copy(loading = state.value.items.isEmpty(), error = null)
        viewModelScope.launch {
            try {
                val inventory = inventoryRepository.getInventory()
                val equipment = equipmentRepository.getEquipment()
                val room = roomRepository.getRoom()
                if(expected!=revision) return@launch
                mutable.value = state.value.copy(
                    items = inventory,
                    loading = false,
                    error = null,
                    equipment = equipment,
                    equippedItemIds = equipment.map { it.itemId }.toSet(),
                    room=room,placedItemIds=room.map { it.itemId }.toSet(),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if(expected!=revision) return@launch
                mutable.value = state.value.copy(
                    loading = false,
                    error = "Couldn't load your inventory. Please check your connection and retry.",
                )
            }
        }
    }

    fun selectCategory(category: ItemCategory?) {
        mutable.value = state.value.copy(selectedCategory = category)
    }

    fun toggleEquip(item: InventoryItem) {
        if(actionsBlocked()) return
        val slot=accessorySlot(item.item.code) ?: return
        if(item.item.category!=ItemCategory.ACCESSORY || item.quantity!=1) return
        submitEquipment(EquipmentCommand(slot,if(item.item.id in state.value.equippedItemIds) null else item.item.id))
    }

    fun retryEquipment() {
        if(!state.value.equipmentBusy) state.value.pendingEquipment?.let(::submitEquipment)
    }
    private fun submitEquipment(command: EquipmentCommand) {
        revision++
        mutable.value=state.value.copy(equipmentBusy=true,pendingEquipment=command,actionMessage=null,error=null)
        viewModelScope.launch {
            try {
                val equipment=command.itemId?.let { EquipItem(equipmentRepository)(command.slot,it) }
                    ?: UnequipItem(equipmentRepository)(command.slot)
                mutable.value=state.value.copy(equipment=equipment,equippedItemIds=equipment.map { it.itemId }.toSet(),
                    equipmentBusy=false,pendingEquipment=null,actionMessage="Equipment saved.")
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(error: Exception) {
                val retryable=(error as? EquipmentException)?.retryable!=false
                mutable.value=state.value.copy(equipmentBusy=false,
                    pendingEquipment=if(retryable) command else null,
                    error=if(retryable) "Couldn't confirm equipment. Retry to sync the same change."
                        else "Equipment wasn't changed. Check that you own an accessory for this slot.")
            }
        }
    }

    fun togglePlace(item: InventoryItem) {
        if(actionsBlocked() || item.item.category!=ItemCategory.FURNITURE || item.quantity!=1) return
        val slot=furnitureSlot(item.item.code) ?: return
        submitRoom(RoomCommand(slot,if(item.item.id in state.value.placedItemIds) null else item.item.id))
    }
    private fun actionsBlocked()=state.value.equipmentBusy || state.value.pendingEquipment!=null ||
        state.value.roomBusy || state.value.pendingRoom!=null
    fun retryRoom() { if(!state.value.roomBusy) state.value.pendingRoom?.let(::submitRoom) }
    private fun submitRoom(command: RoomCommand) {
        revision++
        mutable.value=state.value.copy(roomBusy=true,pendingRoom=command,error=null,actionMessage=null)
        viewModelScope.launch {
            try {
                val room=command.itemId?.let { PlaceFurniture(roomRepository)(command.slot,it) }
                    ?: StoreFurniture(roomRepository)(command.slot)
                mutable.value=state.value.copy(room=room,placedItemIds=room.map { it.itemId }.toSet(),
                    roomBusy=false,pendingRoom=null,actionMessage="Room saved.")
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(error: Exception) {
                val retryable=(error as? RoomException)?.retryable!=false
                mutable.value=state.value.copy(roomBusy=false,pendingRoom=if(retryable) command else null,
                    error=if(retryable) "Couldn't confirm room changes. Retry to sync the same change."
                        else "Room wasn't changed. Check that you own furniture for this slot.")
            }
        }
    }

    fun dismissActionMessage() {
        mutable.value = state.value.copy(actionMessage = null)
    }
}
