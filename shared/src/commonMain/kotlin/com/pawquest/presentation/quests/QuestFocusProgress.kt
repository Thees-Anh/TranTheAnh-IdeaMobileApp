package com.pawquest.presentation.quests

import com.pawquest.domain.model.FocusStatus
import com.pawquest.presentation.focus.FocusState

data class QuestFocusProgress(val remainingSeconds: Long,val elapsedFraction: Float,val awaitingConfirmation: Boolean) {
    val timeLabel get()="${remainingSeconds/60}:${(remainingSeconds%60).toString().padStart(2,'0')}"
}

/** A view of the existing server-anchored countdown, never a second timer. */
internal fun FocusState.progressForQuest(questId: String): QuestFocusProgress? {
    val current=session?.takeIf {it.questId==questId && it.status in setOf(FocusStatus.ACTIVE,FocusStatus.COMPLETED)} ?: return null
    val remaining=remainingSeconds.coerceIn(0,current.durationMinutes*60L)
    return QuestFocusProgress(remaining,1f-remaining.toFloat()/(current.durationMinutes*60L),remaining==0L)
}
