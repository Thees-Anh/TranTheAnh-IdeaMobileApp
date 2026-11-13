package com.pawquest

import com.pawquest.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class LuckyWheelTest {
    private val id = "e2800000-0000-0000-0000-000000000001"
    private val food = "e1000000-0000-0000-0000-000000000001"
    private val stamp = "2026-11-10T10:00:00Z"
    private val sectors = WheelSector.entries.mapIndexed { i, sector ->
        WheelSectorConfiguration(sector, i, listOf(2500,2000,1800,1200,1000,800,500,200)[i],
            listOf(1,50,5,10,1,20,50,1)[i], if (sector == WheelSector.FOOD) food else null)
    }
    private fun configuration(pool: List<WheelSectorConfiguration> = sectors) =
        LuckyWheelConfiguration(id,false,id,1,100,1,50,pool)
    private fun fallback() = LuckyWheelReceipt(id,id,id,id,1,WheelSector.PET_UNLOCK,WheelPayment.COINS,
        100,0,WheelReward.PET_UNLOCK_CONVERTED_TO_SHARDS,null,0,null,50,0,0,900,100,0,stamp)

    @Test fun configuredIntegerWeightsAndAllSectorIdentitiesArePreserved() {
        assertEquals(10_000,configuration().sectors.sumOf { it.weight })
        assertEquals(8,configuration().sectors.size)
        assertFailsWith<IllegalArgumentException> { configuration(sectors.dropLast(1)) }
        assertFailsWith<IllegalArgumentException> { configuration(sectors.dropLast(1) + sectors.first()) }
        assertFailsWith<IllegalArgumentException> { configuration(sectors.map { it.copy(weight = 1) }) }
        assertFailsWith<IllegalArgumentException> { sectors.first().copy(foodItemId = null) }
        assertFailsWith<IllegalArgumentException> { sectors.first().copy(position = 1) }
    }
    @Test fun replacementRetainsPetUnlockSectorAndAuthoritativeAmount() {
        val receipt=fallback()
        assertEquals(WheelSector.PET_UNLOCK,receipt.originalSector)
        assertNull(receipt.selectedPetId)
        assertEquals(50,receipt.petShardsEarned)
        assertEquals(100,receipt.petShardsBalanceAfter)
        assertFailsWith<IllegalArgumentException> { receipt.copy(originalSector = WheelSector.PET_SHARDS_50) }
        assertFailsWith<IllegalArgumentException> { receipt.copy(selectedPetId = "rabbit") }
        assertFailsWith<IllegalArgumentException> { receipt.copy(petShardsEarned = 0) }
    }
    @Test fun actualPetIsCanonicalAndSelectionDoesNotRequireLevel() {
        for ((code,renderer) in listOf("rabbit" to "rabbit","puppy" to "puppy","capybara" to "capybara")) {
            val receipt = fallback().copy(actualReward = WheelReward.PET_UNLOCKED,selectedPetId = code,petShardsEarned = 0)
            assertEquals(code,receipt.selectedPetId)
            assertTrue(PetUnlockRules.canSelect(PetSpecies(code,code,15,renderer,true),1))
            assertFalse(PetUnlockRules.canSelect(PetSpecies(code,code,15,renderer,false),99))
        }
        assertFailsWith<IllegalArgumentException> { fallback().copy(actualReward=WheelReward.PET_UNLOCKED,selectedPetId="mochi_cat",petShardsEarned=0) }
    }
    @Test fun paymentIsExplicitAndWireInputsContainNoFinancialAuthority() {
        val json=Json.encodeToString(LuckyWheelSpinRequest(id,WheelPayment.TICKET,id))
        assertTrue(json.contains("\"p_payment_method\":\"TICKET\""))
        assertFalse(json.contains("reward")); assertFalse(json.contains("price")); assertFalse(json.contains("user"))
        assertFailsWith<IllegalArgumentException> { LuckyWheelSpinRequest("bad",WheelPayment.COINS,id) }
        assertFailsWith<IllegalArgumentException> { fallback().copy(payment=WheelPayment.TICKET) }
        assertFailsWith<IllegalArgumentException> { LuckyWheelBalances(0,-1,0) }
    }
    @Test fun petRedemptionUsesSpeciesAndBackendQuoteNotClientPrice() {
        val json=Json.encodeToString(PetShardRequest("puppy",id))
        assertTrue(json.contains("p_species_code")); assertFalse(json.contains("price"))
        assertFailsWith<IllegalArgumentException> { PetShardRequest("mochi_cat",id) }
        assertFailsWith<IllegalArgumentException> { PetShardQuote(id,id,"rabbit",0,stamp) }
        assertEquals(70,PetShardReceipt(id,id,id,"puppy",70,20,stamp).shardsSpent)
    }
    @Test fun serverReceiptSerializationPreservesBothOutcomeAndOriginalSector() {
        val json=Json { ignoreUnknownKeys=true }
        val receipt=fallback()
        assertEquals(receipt,json.decodeFromString<LuckyWheelReceipt>(json.encodeToString(receipt)))
    }
}
