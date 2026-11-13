package com.pawquest.domain.repository

import com.pawquest.domain.model.*

interface RoomRepository {
    suspend fun getRoom(): List<RoomFurniture>
    suspend fun setFurniture(slot: FurnitureSlot,itemId: String?): List<RoomFurniture>
}
