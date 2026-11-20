package com.pawquest

import com.pawquest.presentation.components.*
import kotlin.test.*

class ShardRewardMotionTest {
    @Test fun arrivalCountersUseOnlyConfirmedDeltaAndFinishExactlyAtReceiptBalance() {
        for(currency in RewardCurrency.entries) for(amount in listOf(1L,5L,10L,20L,40L,50L)) {
            val receipt=CurrencyRewardFlight("receipt",currency,amount,100+amount,true)
            val count=amount.coerceAtMost(12).toInt()
            val balances=(0..count).map {currencyFlightBalance(receipt,it,count)}
            assertEquals(100L,balances.first());assertEquals(receipt.balanceAfter,balances.last())
            assertTrue(balances.zipWithNext().all {(a,b)->b>=a})
            assertTrue(balances.all {it in 100..receipt.balanceAfter})
        }
    }
    @Test fun itemAndPetCountersRemainSeparateAndLargeBalancesDoNotOverflow() {
        val item=CurrencyRewardFlight("item",RewardCurrency.ItemShard,40,Long.MAX_VALUE)
        val pet=CurrencyRewardFlight("pet",RewardCurrency.PetShard,5,65)
        assertEquals(Long.MAX_VALUE,currencyFlightBalance(item,12,12))
        assertEquals(60L,currencyFlightBalance(pet,0,5))
        assertFailsWith<IllegalArgumentException> {CurrencyRewardFlight("invalid",RewardCurrency.PetShard,50,20)}
        assertFailsWith<IllegalArgumentException> {currencyFlightBalance(pet,6,5)}
    }
}
