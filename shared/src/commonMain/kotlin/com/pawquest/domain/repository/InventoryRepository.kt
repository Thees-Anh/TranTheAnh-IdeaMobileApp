package com.pawquest.domain.repository

import com.pawquest.domain.model.InventoryItem

/** Read-only client contract. Grants and consumption are trusted server operations. */
interface InventoryRepository {
    suspend fun getInventory(): List<InventoryItem>
}
