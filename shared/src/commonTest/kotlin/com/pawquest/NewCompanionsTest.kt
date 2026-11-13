package com.pawquest

import com.pawquest.domain.model.*
import kotlin.test.*

class NewCompanionsTest {
    @Test fun ownedSpeciesHaveArtAndNoLevelGate() {
        for((key,level) in listOf("capybara" to 10L,"rabbit" to 15L)) {
            assertTrue(PetArtworkRegistry.find(key)!!.supportsEquipment)
            assertTrue(PetArtworkRegistry.find(key)!!.storyEmotions)
            val species=PetSpecies(key,key,level,key,true)
            assertTrue(PetUnlockRules.canSelect(species,1))
            assertTrue(PetUnlockRules.canSelect(species,level))
            assertFalse(PetUnlockRules.canSelect(species.copy(unlocked=false),level))
        }
    }
}
