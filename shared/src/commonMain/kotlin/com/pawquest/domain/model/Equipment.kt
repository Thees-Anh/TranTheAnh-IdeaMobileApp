package com.pawquest.domain.model

import kotlin.time.Instant

enum class EquipmentSlot { HEAD, FACE, NECK, BACK }
data class EquippedItem(val slot: EquipmentSlot,val itemId: String,val assetId: String,val equippedAt: Instant)
class EquipmentException(val retryable: Boolean) : Exception("Couldn't confirm equipment.")

fun accessorySlot(code: String): EquipmentSlot? = when(code) {
    "hat" -> EquipmentSlot.HEAD
    "glasses" -> EquipmentSlot.FACE
    "collar" -> EquipmentSlot.NECK
    else -> DecorCatalog.equipmentSlots[code]
}
