package com.pawquest.domain.usecase

import com.pawquest.domain.model.EquipmentSlot
import com.pawquest.domain.repository.EquipmentRepository

class EquipItem(private val repository: EquipmentRepository) {
    suspend operator fun invoke(slot: EquipmentSlot,itemId: String) = repository.setEquipment(slot,itemId)
}
class UnequipItem(private val repository: EquipmentRepository) {
    suspend operator fun invoke(slot: EquipmentSlot) = repository.setEquipment(slot,null)
}
