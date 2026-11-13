package com.pawquest.domain.model

import kotlin.time.Instant

/** Hunger is a fullness meter: 100 = fed, 0 = hungry. No health/death system. */
data class PetStats(val hunger: Int=100,val happiness: Int=100,val energy: Int=100,val hydration: Int=100) {
    init { require(hunger in 0..100 && happiness in 0..100 && energy in 0..100 && hydration in 0..100) }
    fun afterHours(hours: Long): PetStats {
        require(hours>=0)
        val bounded=hours.coerceAtMost(100).toInt()
        return PetStats((hunger-PetCareBalance.hungerDecayPerHour*bounded).coerceAtLeast(0),
            (happiness-PetCareBalance.happinessDecayPerHour*bounded).coerceAtLeast(0),
            (energy-PetCareBalance.energyDecayPerHour*bounded).coerceAtLeast(0),
            (hydration-HydrationBalance.hydrationDecayPerHour*bounded).coerceAtLeast(0))
    }
    fun care(action: PetCareAction): PetStats=when(action) {
        PetCareAction.DRINK -> copy(hydration=(hydration+HydrationBalance.drinkGain).coerceAtMost(PetCareBalance.statMax))
        PetCareAction.FEED -> copy(hunger=(hunger+PetCareBalance.feedGain).coerceAtMost(PetCareBalance.statMax))
        PetCareAction.PLAY -> copy(happiness=(happiness+PetCareBalance.playHappinessGain).coerceAtMost(PetCareBalance.statMax),
            energy=(energy-PlayBalance.energyCost).coerceAtLeast(0),hunger=(hunger-PlayBalance.hungerCost).coerceAtLeast(0),
            hydration=(hydration-PlayBalance.waterCost).coerceAtLeast(0))
        PetCareAction.SLEEP,PetCareAction.TOUCH -> this
    }
}
enum class PetCareAction { FEED,PLAY,SLEEP,TOUCH,DRINK }
data class PetProgress(val totalXp: Long) {
    init { require(totalXp>=0) }
    val level: Long get()=totalXp/PetCareBalance.xpPerLevel+1
    val xpInLevel: Int get()=(totalXp%PetCareBalance.xpPerLevel).toInt()
    val xpToNextLevel: Int get()=PetCareBalance.xpPerLevel-xpInLevel
}
data class Pet(val id: String,val userId: String,val name: String,val stats: PetStats,
    val progress: PetProgress,val statsUpdatedAt: Instant,val sleepUntil: Instant?=null,
    val speciesCode: String="mochi_cat",val rendererKey: String="prototype_cat")
data class PetActionResult(val requestId: String,val action: PetCareAction,val code: String,
    val applied: Boolean,val foodConsumed: Int) {
    val message get()=when(code) {
        "drank" -> "A refreshing drink! Water increased. No Coins or food used."
        "already_hydrated" -> "Your pet has had enough water."
        "fed" -> "Your pet enjoyed a meal. One food used."
        "played" -> "Playtime! Used ${PlayBalance.energyCost} Energy, ${PlayBalance.hungerCost} Hunger and ${PlayBalance.waterCost} Water."
        "sleep_started" -> "Your pet is resting. Energy recovers over time."
        "touched" -> "Your pet is happy to see you!"
        "woken" -> "Good morning! Your pet is awake. Energy recovered so far has been saved."
        "no_food" -> "You're out of food."
        "already_full" -> "Your pet is already full. No food was used."
        "not_enough_energy" -> "Your pet needs at least ${PlayBalance.energyCost} Energy to play. Let them sleep to recharge."
        "not_enough_hunger" -> "Your pet needs at least ${PlayBalance.hungerCost} Hunger to play. Feed them first."
        "not_enough_water" -> "Your pet needs at least ${PlayBalance.waterCost} Water to play. Give them a free drink first."
        "already_happy" -> "Play is no longer limited by Joy. Please update the server with migration 018."
        "fully_rested" -> "Your pet is fully rested."
        "already_sleeping", "pet_sleeping" -> "Your pet is sleeping. Tap your pet to wake them first."
        else -> "Pet care synchronized."
    }
}
data class PetSnapshot(val pet: Pet,val coins: Long,val serverNow: Instant,val foodQuantity: Int=0,
    val actionResult: PetActionResult?=null)

object PetCareRules {
    fun sleepEnergy(initial: Int,elapsedMinutes: Long): Int {
        require(initial in 0..PetCareBalance.statMax && elapsedMinutes>=0)
        return (initial+elapsedMinutes.coerceAtMost(PetCareBalance.statMax.toLong()).toInt()*PetCareBalance.sleepEnergyPerMinute)
            .coerceAtMost(PetCareBalance.statMax)
    }
    fun rejection(stats: PetStats,food: Int,sleeping: Boolean,action: PetCareAction): String?=when {
        sleeping && action!=PetCareAction.TOUCH -> if(action==PetCareAction.SLEEP) "already_sleeping" else "pet_sleeping"
        action==PetCareAction.DRINK && stats.hydration==PetCareBalance.statMax -> "already_hydrated"
        action==PetCareAction.FEED && stats.hunger==PetCareBalance.statMax -> "already_full"
        action==PetCareAction.FEED && food<=0 -> "no_food"
        action==PetCareAction.PLAY && stats.energy<PlayBalance.energyCost -> "not_enough_energy"
        action==PetCareAction.PLAY && stats.hunger<PlayBalance.hungerCost -> "not_enough_hunger"
        action==PetCareAction.PLAY && stats.hydration<PlayBalance.waterCost -> "not_enough_water"
        action==PetCareAction.SLEEP && stats.energy==PetCareBalance.statMax -> "fully_rested"
        else -> null
    }
}
