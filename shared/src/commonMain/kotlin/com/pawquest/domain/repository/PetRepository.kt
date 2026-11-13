package com.pawquest.domain.repository

import com.pawquest.domain.model.*

interface PetRepository {
    suspend fun bistroMenu(): List<BistroItem> = error("Bistro unavailable")
    suspend fun bistroFeed(request: PendingBistroMeal): BistroResponse = error("Bistro unavailable")
    suspend fun pendingMeal(owner: String): PendingBistroMeal?=null
    suspend fun saveMeal(request: PendingBistroMeal): PendingBistroMeal=error("Secure meal storage unavailable")
    suspend fun clearMeal(request: PendingBistroMeal)=Unit
    suspend fun restore(): PetSnapshot
    suspend fun care(action: PetCareAction,requestId: String): PetSnapshot
    suspend fun collection(): PetCollection=error("Pet collection is not configured.")
    suspend fun selectSpecies(code: String): PetSnapshot=error("Pet collection is not configured.")
    suspend fun companionContext(timezone: String): PetCompanionContext?=null
}
