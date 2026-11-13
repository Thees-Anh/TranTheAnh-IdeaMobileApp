package com.pawquest.domain.repository

import com.pawquest.domain.model.Quest
import com.pawquest.domain.model.QuestDraft

interface QuestRepository {
    suspend fun create(draft: QuestDraft): Quest
    suspend fun update(id: String, draft: QuestDraft): Quest
    /** Archives a definition, retaining sessions and their reward audit trail. */
    suspend fun delete(id: String)
    suspend fun getQuests(): List<Quest>
}
