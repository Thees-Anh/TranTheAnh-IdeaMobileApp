package com.pawquest.domain.repository

import com.pawquest.domain.model.CatalogItem
import com.pawquest.domain.model.PurchaseRequest
import com.pawquest.domain.model.PurchaseResult

interface ItemRepository {
    suspend fun getItems(): List<CatalogItem>
    suspend fun purchase(request: PurchaseRequest): PurchaseResult
}
