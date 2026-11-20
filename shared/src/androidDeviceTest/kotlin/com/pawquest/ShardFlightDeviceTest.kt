package com.pawquest

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.pawquest.presentation.components.*
import com.pawquest.presentation.theme.PawQuestTheme
import org.junit.Assert.*
import org.junit.Test

/** Synthetic receipt only; no backend, account, wallet or grants. */
class ShardFlightFixtureActivity : ComponentActivity() {
    var ready by mutableStateOf(false)
    val observed=mutableListOf<Long>()
    var overlay=false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val currency=RewardCurrency.valueOf(checkNotNull(intent.getStringExtra("currency")))
        setContent {PawQuestTheme {
            CurrencyRewardHost(listOf(CurrencyRewardFlight("fixture",currency,50,150,ready))) {
                val balance=animatedCurrencyBalance(150,currency) ?: -1
                val flying=LocalCurrencyRewardMotion.current?.progress?.isNotEmpty()==true
                SideEffect {observed+=balance;overlay=flying}
                Box(Modifier.fillMaxSize().padding(16.dp)) {
                    CurrencyBalances(150,Modifier.align(Alignment.TopEnd),ShardBalances(150,150))
                    Text("+50",Modifier.align(Alignment.Center))
                }
            }
        }}
    }
}

class ShardFlightDeviceTest {
    @Test fun itemShardsFlyAndCountOnce()=checkFlight(RewardCurrency.ItemShard)
    @Test fun petShardsFlyAndCountOnce()=checkFlight(RewardCurrency.PetShard)
    @Test fun coinsFlyAndCountOnce()=checkFlight(RewardCurrency.Coin)
    private fun checkFlight(currency: RewardCurrency) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val activity=instrumentation.startActivitySync(Intent(instrumentation.context,ShardFlightFixtureActivity::class.java)
            .putExtra("currency",currency.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ShardFlightFixtureActivity
        fun await(condition: ()->Boolean) {
            val until=SystemClock.uptimeMillis()+6000
            var ok=false
            while(!ok && SystemClock.uptimeMillis()<until) {instrumentation.runOnMainSync {ok=condition()};SystemClock.sleep(20)}
            assertTrue("Expected flight state",ok)
        }
        try {
            await {activity.observed.lastOrNull()==100L && !activity.overlay}
            instrumentation.runOnMainSync {activity.ready=true}
            await {activity.overlay}
            instrumentation.uiAutomation.takeScreenshot()?.let {bitmap ->
                java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"currency-flight-${currency.name}.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                }
            }
            await {activity.observed.lastOrNull()==150L && !activity.overlay}
            instrumentation.runOnMainSync {
                assertTrue(activity.observed.any {it in 101L..149L})
                assertTrue(activity.observed.zipWithNext().all {(a,b)->b>=a})
                activity.ready=false
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {activity.ready=true}
            SystemClock.sleep(250)
            instrumentation.runOnMainSync {assertEquals(150L,activity.observed.last());assertFalse(activity.overlay)}
        } finally {instrumentation.runOnMainSync {activity.finish()}}
    }
}
