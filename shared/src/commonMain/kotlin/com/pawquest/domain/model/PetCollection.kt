package com.pawquest.domain.model

/** Server-owned appearances; unlockLevel is legacy metadata, never selection authorization. */
data class PetSpecies(val code: String,val name: String,val unlockLevel: Long,val rendererKey: String,
    val unlocked: Boolean) {
    init {require(code.isNotBlank() && name.isNotBlank() && unlockLevel>=1)}
}
data class PetCollection(val activeCode: String,val level: Long,val species: List<PetSpecies>)

/** Explicit shipped-art registry. New species require their own reviewed offline renderer. */
data class PetArtwork(val assetPath: String,val supportsEquipment: Boolean,val storyEmotions: Boolean=false)
object PetArtworkRegistry {
    private val entries=mapOf("prototype_cat" to PetArtwork("pet/prototype_stage.html",true),
        "puppy" to PetArtwork("pet/puppy_stage.html",true,true),
        "capybara" to PetArtwork("pet/capybara_stage.html",true,true),
        "rabbit" to PetArtwork("pet/rabbit_stage.html",true,true))
    fun find(key: String): PetArtwork?=entries[key]
}

object PetUnlockRules {
    @Suppress("UNUSED_PARAMETER")
    fun canSelect(species: PetSpecies,level: Long)=species.unlocked && PetArtworkRegistry.find(species.rendererKey)!=null
    fun nextMilestones(level: Long): List<Long> {
        val first=(level/5+1)*5
        return List(4) {first+it*5}
    }
}
