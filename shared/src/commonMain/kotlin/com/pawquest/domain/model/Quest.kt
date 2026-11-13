package com.pawquest.domain.model

import kotlin.time.Instant

enum class QuestRepeatType { ONE_TIME, DAILY }

/** A definition, not a running session. IDs and timestamps are server assigned. */
data class Quest(
    val id: String,
    val userId: String,
    val title: String,
    val description: String,
    val category: String,
    val durationMinutes: Int,
    val repeatType: QuestRepeatType,
    val createdAt: Instant,
    val dueAt: Instant? = null,
)

/** Deliberately excludes ownership, timestamps, Coins and XP. */
data class QuestDraft(
    val title: String,
    val description: String = "",
    val category: String = "Focus",
    val durationMinutes: Int,
    val repeatType: QuestRepeatType = QuestRepeatType.ONE_TIME,
    val dueAt: Instant? = null,
) {
    fun validated(): QuestDraft {
        val clean = copy(title = title.trim(), category = category.trim())
        require(clean.title.codePointLength() in 1..120) { "Title must contain 1–120 characters." }
        require(clean.description.codePointLength() <= 2000) { "Description must not exceed 2000 characters." }
        require(clean.category.codePointLength() in 1..40) { "Category must contain 1–40 characters." }
        require(clean.durationMinutes in 5..90) { "Duration must be between 5 and 90 minutes." }
        require(clean.repeatType != QuestRepeatType.DAILY || clean.dueAt == null) { "Daily quests use the end of the local day." }
        return clean
    }
}

// PostgreSQL length(text) counts Unicode code points rather than UTF-16 units.
private fun String.codePointLength(): Int = indices.count { index ->
    !(this[index].isLowSurrogate() && index > 0 && this[index - 1].isHighSurrogate())
}

data class QuestRewardPreview(val coins: Int, val xp: Int)

/** Advisory tier only. Server claim RPCs apply eligibility and daily caps. */
fun questRewardPreview(durationMinutes: Int): QuestRewardPreview {
    require(durationMinutes in 5..90) { "Duration must be between 5 and 90 minutes." }
    return when (durationMinutes) {
        in 5..14 -> QuestRewardPreview(10, 5)
        in 15..29 -> QuestRewardPreview(20, 10)
        in 30..44 -> QuestRewardPreview(35, 20)
        in 45..59 -> QuestRewardPreview(50, 30)
        in 60..74 -> QuestRewardPreview(65, 40)
        else -> QuestRewardPreview(80, 50)
    }
}

enum class QuestFailure { NOT_CONFIGURED, AUTHENTICATION_REQUIRED, NOT_FOUND, NETWORK, SERVICE }
class QuestException(val failure: QuestFailure) : Exception(when (failure) {
    QuestFailure.NOT_CONFIGURED -> "Quest storage is not configured."
    QuestFailure.AUTHENTICATION_REQUIRED -> "Please sign in to manage quests."
    QuestFailure.NOT_FOUND -> "This quest is no longer available."
    QuestFailure.NETWORK -> "Couldn't connect. Please retry."
    QuestFailure.SERVICE -> "Couldn't save or load quests. Please retry."
})

fun validateQuestId(id: String) {
    require(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}").matches(id)) {
        "A valid quest UUID is required."
    }
}
