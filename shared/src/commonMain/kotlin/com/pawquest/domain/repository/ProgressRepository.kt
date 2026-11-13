package com.pawquest.domain.repository

import com.pawquest.domain.model.ProgressSnapshot
import kotlinx.datetime.LocalDate

interface ProgressRepository {
    suspend fun snapshot(month: LocalDate?=null): ProgressSnapshot
    suspend fun acknowledge(eventId: String)
}
