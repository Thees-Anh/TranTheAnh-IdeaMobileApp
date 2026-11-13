package com.pawquest.domain.repository

import com.pawquest.domain.model.*

interface EquipmentRepository {
    suspend fun getEquipment(): List<EquippedItem>
    suspend fun setEquipment(slot: EquipmentSlot,itemId: String?): List<EquippedItem>
}
