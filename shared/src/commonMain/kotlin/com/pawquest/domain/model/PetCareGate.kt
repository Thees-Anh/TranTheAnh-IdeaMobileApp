package com.pawquest.domain.model

enum class PetNeed { HUNGRY, THIRSTY, HUNGRY_THIRSTY }

/**
 * Mystery Box and Lucky Wheel wait while the pet is hungry or thirsty. Mirrors the server guard
 * `pawquest_private.gacha_care_block` (same ≤25 line as reminders and the mood icon); the server
 * remains the authority, this only lets the UI explain and disable the action up front.
 */
object PetCareGate {
    const val THRESHOLD = 25
    fun need(stats: PetStats): PetNeed? {
        val hungry = stats.hunger <= THRESHOLD
        val thirsty = stats.hydration <= THRESHOLD
        return when {
            hungry && thirsty -> PetNeed.HUNGRY_THIRSTY
            hungry -> PetNeed.HUNGRY
            thirsty -> PetNeed.THIRSTY
            else -> null
        }
    }
}
