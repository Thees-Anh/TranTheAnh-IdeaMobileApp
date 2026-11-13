package com.pawquest.data.repository

import com.pawquest.domain.model.SectionPlaceholder
import com.pawquest.domain.repository.PlaceholderRepository

// Static navigation scaffolding only. No backend, persistence, or feature behavior.
class LocalPlaceholderRepository : PlaceholderRepository {
    private val sections = mapOf(
        "pet_home" to SectionPlaceholder("Pet Home", "Your pet's home will appear here."),
        "quests" to SectionPlaceholder("Quests", "Your quests will appear here."),
        "shop" to SectionPlaceholder("Shop", "The shop will appear here."),
        "inventory" to SectionPlaceholder("Inventory", "Your inventory will appear here."),
        "profile" to SectionPlaceholder("Profile", "Your profile will appear here."),
    )

    override suspend fun getPlaceholder(sectionId: String) = sections[sectionId]
}
