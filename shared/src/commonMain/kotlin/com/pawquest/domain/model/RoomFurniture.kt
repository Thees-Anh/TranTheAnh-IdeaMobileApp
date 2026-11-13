package com.pawquest.domain.model

import kotlin.time.Instant

enum class FurnitureSlot { BED, LAMP, CARPET, WINDOW, CLOCK, LIGHTS, BOOKSHELF, CAT_TREE, PLANT, TEA_TABLE, GRAMOPHONE }
data class RoomFurniture(val slot: FurnitureSlot,val itemId: String,val assetId: String,val placedAt: Instant)
class RoomException(val retryable: Boolean) : Exception("Couldn't confirm room configuration.")
fun furnitureSlot(code: String): FurnitureSlot? = when(code) {
    "bed" -> FurnitureSlot.BED
    "lamp" -> FurnitureSlot.LAMP
    "carpet" -> FurnitureSlot.CARPET
    else -> DecorCatalog.furnitureSlots[code]
}
