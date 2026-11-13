package com.pawquest

import com.pawquest.domain.model.*
import kotlin.test.*

class PlayCostsTest {
    @Test fun fullMoodDoesNotBlockPlayAndCostsAllThreeNeeds() {
        assertNull(PetCareRules.rejection(PetStats(),0,false,PetCareAction.PLAY))
        assertEquals(PetStats(98,100,95,97),PetStats().care(PetCareAction.PLAY))
        val second=PetStats().care(PetCareAction.PLAY).care(PetCareAction.PLAY)
        assertEquals(PetStats(96,100,90,94),second)
    }
    @Test fun exactCostsReachZeroWithoutOverflowAndNoFoodItemIsRequired() {
        val stats=PetStats(2,100,5,3)
        assertNull(PetCareRules.rejection(stats,0,false,PetCareAction.PLAY))
        assertEquals(PetStats(0,100,0,0),stats.care(PetCareAction.PLAY))
    }
    @Test fun EachMissingResourceHasAnActionableReason() {
        assertEquals("not_enough_energy",PetCareRules.rejection(PetStats(100,100,4,100),0,false,PetCareAction.PLAY))
        assertEquals("not_enough_hunger",PetCareRules.rejection(PetStats(1,100,100,100),0,false,PetCareAction.PLAY))
        assertEquals("not_enough_water",PetCareRules.rejection(PetStats(100,100,100,2),0,false,PetCareAction.PLAY))
        assertEquals("pet_sleeping",PetCareRules.rejection(PetStats(),0,true,PetCareAction.PLAY))
        for(code in listOf("not_enough_energy","not_enough_hunger","not_enough_water"))
            assertTrue(PetActionResult("request",PetCareAction.PLAY,code,false,0).message.contains("first") ||
                PetActionResult("request",PetCareAction.PLAY,code,false,0).message.contains("recharge"))
    }
}
