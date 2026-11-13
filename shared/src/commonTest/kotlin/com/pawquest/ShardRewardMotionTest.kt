package com.pawquest

import com.pawquest.presentation.components.*
import kotlin.test.*

class ShardRewardMotionTest {
    @Test fun arrivalCountersUseOnlyConfirmedDeltaAndFinishExactlyAtReceiptBalance() {
        for(amount in listOf(1L,5L,10L,20L,40L,50L)) {
            val receipt=ShardRewardFlight("receipt",true,amount,100+amount,true)
            val count=amount.coerceAtMost(12).toInt()
            val balances=(0..count).map {shardFlightBalance(receipt,it,count)}
            assertEquals(100L,balances.first());assertEquals(receipt.balanceAfter,balances.last())
            assertTrue(balances.zipWithNext().all {(a,b)->b>=a})
            assertTrue(balances.all {it in 100..receipt.balanceAfter})
        }
    }
    @Test fun itemAndPetCountersRemainSeparateAndLargeBalancesDoNotOverflow() {
        val item=ShardRewardFlight("item",false,40,Long.MAX_VALUE,true)
        val pet=ShardRewardFlight("pet",true,5,65,true)
        assertEquals(Long.MAX_VALUE,shardFlightBalance(item,12,12))
        assertEquals(60L,shardFlightBalance(pet,0,5))
        assertFailsWith<IllegalArgumentException> {ShardRewardFlight("invalid",true,50,20,true)}
        assertFailsWith<IllegalArgumentException> {shardFlightBalance(pet,6,5)}
    }
}
