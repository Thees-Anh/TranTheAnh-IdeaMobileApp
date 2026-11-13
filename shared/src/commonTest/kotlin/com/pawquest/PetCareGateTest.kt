package com.pawquest

import com.pawquest.domain.model.*
import kotlin.test.*

class PetCareGateTest {
    @Test fun drawsWaitAtTheSameLineAsTheHungryAndThirstyMoods() {
        assertNull(PetCareGate.need(PetStats(hunger = 26, hydration = 26)))
        assertEquals(PetNeed.HUNGRY, PetCareGate.need(PetStats(hunger = 25, hydration = 26)))
        assertEquals(PetNeed.THIRSTY, PetCareGate.need(PetStats(hunger = 26, hydration = 25)))
        assertEquals(PetNeed.HUNGRY_THIRSTY, PetCareGate.need(PetStats(hunger = 0, hydration = 0)))
        // Same threshold as the widget/app-icon mood rules.
        assertEquals(WidgetPetMood.HUNGRY, WidgetPetRules.mood(PetStats(hunger = PetCareGate.THRESHOLD), false))
    }

    @Test fun serverRefusalIsADismissableNoChargeRejection() {
        assertTrue(ItemCaseRejection.PET_NEEDS_CARE.canDismiss)
        assertEquals(LuckyWheelRejection.PET_NEEDS_CARE, LuckyWheelRejection.valueOf("pet_needs_care".uppercase()))
        assertEquals(ItemCaseRejection.PET_NEEDS_CARE, ItemCaseRejection.valueOf("pet_needs_care".uppercase()))
    }
}
