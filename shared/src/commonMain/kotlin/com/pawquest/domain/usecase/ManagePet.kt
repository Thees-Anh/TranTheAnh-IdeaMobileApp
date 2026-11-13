package com.pawquest.domain.usecase

import com.pawquest.domain.model.*
import com.pawquest.domain.repository.PetRepository

class ManagePet(private val repository: PetRepository) {
    suspend fun restore()=repository.restore()
    suspend fun collection()=repository.collection()
    suspend fun companionContext(timezone: String)=repository.companionContext(timezone)
    suspend fun selectSpecies(species: PetSpecies,level: Long): PetSnapshot {
        require(PetUnlockRules.canSelect(species,level)) {"This pet is locked or its artwork is not installed in this app version."}
        return repository.selectSpecies(species.code)
    }
    suspend fun care(action: PetCareAction,requestId: String)=run {
        validateQuestId(requestId)
        repository.care(action,requestId)
    }
}
