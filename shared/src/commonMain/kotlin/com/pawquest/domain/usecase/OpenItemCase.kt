package com.pawquest.domain.usecase

import com.pawquest.domain.model.PendingItemCase
import com.pawquest.domain.repository.ItemCaseRepository

/** Only backend-confirmed results grant rewards; no client reward calculation. */
class OpenItemCase(private val repository: ItemCaseRepository) {
    suspend operator fun invoke(request: PendingItemCase)=repository.open(request)
}
