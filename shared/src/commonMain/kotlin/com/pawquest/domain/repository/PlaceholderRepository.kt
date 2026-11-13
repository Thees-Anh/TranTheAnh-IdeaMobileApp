package com.pawquest.domain.repository

import com.pawquest.domain.model.SectionPlaceholder

interface PlaceholderRepository {
    suspend fun getPlaceholder(sectionId: String): SectionPlaceholder?
}
