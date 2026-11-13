package com.pawquest.data.repository

import com.pawquest.domain.model.SectionPlaceholder
import com.pawquest.domain.repository.PlaceholderRepository

// Static navigation scaffolding only. No backend, persistence, or feature behavior.
class LocalPlaceholderRepository : PlaceholderRepository {
    private val sections = mapOf(
        "home" to SectionPlaceholder("Home page", "Today's quests will appear here."),
        "quests" to SectionPlaceholder("Quests", "Your quests will appear here."),
        "pet_home" to SectionPlaceholder("Pet Room", "Your pet's home will appear here."),
        "explore" to SectionPlaceholder("Explore", "Shops and activities will appear here."),
    )

    override suspend fun getPlaceholder(sectionId: String) = sections[sectionId]
}
