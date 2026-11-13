package com.pawquest.domain.usecase

import com.pawquest.domain.model.PurchaseRequest
import com.pawquest.domain.repository.ItemRepository

class PurchaseItem(private val repository: ItemRepository) {
    suspend operator fun invoke(request: PurchaseRequest)=repository.purchase(request.validated())
}
