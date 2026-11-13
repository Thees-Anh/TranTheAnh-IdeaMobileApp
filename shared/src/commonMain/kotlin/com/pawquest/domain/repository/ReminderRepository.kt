package com.pawquest.domain.repository

import com.pawquest.domain.model.ReminderContext

interface ReminderRepository {
    suspend fun context(timezone: String): ReminderContext
    suspend fun widgetContext(timezone: String): com.pawquest.domain.model.WidgetQuestContext=error("Widget context unavailable")
    suspend fun schedule(timezone: String): com.pawquest.domain.model.ReminderSchedule=error("Reminder schedule unavailable")
}
