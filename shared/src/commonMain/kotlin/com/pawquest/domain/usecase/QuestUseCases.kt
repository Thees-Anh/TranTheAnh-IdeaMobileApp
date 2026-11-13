package com.pawquest.domain.usecase

import com.pawquest.domain.model.QuestDraft
import com.pawquest.domain.model.validateQuestId
import com.pawquest.domain.repository.QuestRepository

class CreateQuest(private val repository: QuestRepository) {
    suspend operator fun invoke(draft: QuestDraft) = repository.create(draft.validated())
}
class UpdateQuest(private val repository: QuestRepository) {
    suspend operator fun invoke(id: String, draft: QuestDraft) = run {
        validateQuestId(id)
        repository.update(id, draft.validated())
    }
}
class DeleteQuest(private val repository: QuestRepository) {
    suspend operator fun invoke(id: String) { validateQuestId(id); repository.delete(id) }
}
class GetQuests(private val repository: QuestRepository) {
    suspend operator fun invoke() = repository.getQuests()
}
