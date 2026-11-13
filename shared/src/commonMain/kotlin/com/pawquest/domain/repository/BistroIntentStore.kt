package com.pawquest.domain.repository

import com.pawquest.domain.model.PendingBistroMeal

interface BistroIntentStore {
    suspend fun load(owner: String): PendingBistroMeal?
    suspend fun save(request: PendingBistroMeal): PendingBistroMeal
    suspend fun clear(request: PendingBistroMeal)
}
class UnavailableBistroIntentStore: BistroIntentStore {
    override suspend fun load(owner: String): PendingBistroMeal?=null
    override suspend fun save(request: PendingBistroMeal): PendingBistroMeal=error("Secure meal storage unavailable")
    override suspend fun clear(request: PendingBistroMeal)=Unit
}
