package com.pawquest.domain.usecase

import com.pawquest.domain.model.validateQuestId
import com.pawquest.domain.repository.FocusRepository

class ManageFocusSessions(private val repository: FocusRepository) {
    suspend fun start(questId: String,requestId: String) = run {
        validateQuestId(questId); validateQuestId(requestId); repository.start(questId,requestId)
    }
    suspend fun reconcile(sessionId: String? = null) = repository.reconcile(sessionId)
    suspend fun cancel(sessionId: String) = repository.cancel(sessionId)
    suspend fun claim(sessionId: String) = repository.claim(sessionId)
}
