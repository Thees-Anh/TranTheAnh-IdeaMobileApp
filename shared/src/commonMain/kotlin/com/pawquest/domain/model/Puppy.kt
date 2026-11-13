package com.pawquest.domain.model

import kotlin.time.Instant

enum class PuppyAnimationState(val wire: String) {
    IDLE("idle"),EATING("eating"),DRINKING("drinking"),SLEEPING("sleeping"),LAUGHING("laughing"),SAD("sad")
}
data class PetCompanionContext(val serverNow: Instant,val hasMissedQuest: Boolean,
    val completionId: String?=null,val completedAt: Instant?=null)

object PuppyEmotionRules {
    const val celebrationMilliseconds=4000L
    const val hungerThreshold=25
    fun state(sleeping: Boolean,confirmedReaction: PetCareAction?,celebrating: Boolean,hunger: Int,missed: Boolean): PuppyAnimationState=when {
        sleeping -> PuppyAnimationState.SLEEPING
        confirmedReaction==PetCareAction.FEED -> PuppyAnimationState.EATING
        confirmedReaction==PetCareAction.DRINK -> PuppyAnimationState.DRINKING
        confirmedReaction in listOf(PetCareAction.PLAY,PetCareAction.TOUCH) || celebrating -> PuppyAnimationState.LAUGHING
        hunger<=hungerThreshold || missed -> PuppyAnimationState.SAD
        else -> PuppyAnimationState.IDLE
    }
}
