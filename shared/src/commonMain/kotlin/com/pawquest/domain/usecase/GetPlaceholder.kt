package com.pawquest.domain.usecase

import com.pawquest.domain.repository.PlaceholderRepository

class GetPlaceholder(private val repository: PlaceholderRepository) {
    suspend operator fun invoke(sectionId: String) = repository.getPlaceholder(sectionId)
}
