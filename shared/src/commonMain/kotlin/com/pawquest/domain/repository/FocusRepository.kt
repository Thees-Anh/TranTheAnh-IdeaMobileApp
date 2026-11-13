package com.pawquest.domain.repository

import com.pawquest.domain.model.FocusSnapshot

interface FocusRepository {
    suspend fun start(questId: String, requestId: String): FocusSnapshot
    suspend fun reconcile(sessionId: String? = null): FocusSnapshot
    suspend fun cancel(sessionId: String): FocusSnapshot
    suspend fun claim(sessionId: String): FocusSnapshot
}
