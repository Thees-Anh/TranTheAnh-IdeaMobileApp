package com.pawquest.domain.usecase

import com.pawquest.domain.model.FurnitureSlot
import com.pawquest.domain.repository.RoomRepository

class PlaceFurniture(private val repository: RoomRepository) {
    suspend operator fun invoke(slot: FurnitureSlot,itemId: String) = repository.setFurniture(slot,itemId)
}
class StoreFurniture(private val repository: RoomRepository) {
    suspend operator fun invoke(slot: FurnitureSlot) = repository.setFurniture(slot,null)
}
