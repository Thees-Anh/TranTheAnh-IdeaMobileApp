package com.pawquest

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.pawquest.localization.AppLanguage
import com.pawquest.localization.AppLocale
import org.junit.Assert.*
import org.junit.Test

class ShardExchangeUiDeviceTest {
    @Test fun confirmedExchangeRefreshesBalanceInventoryAndExistingActions() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val ui=instrumentation.uiAutomation
        val previous=AppLocale.language;AppLocale.select(AppLanguage.ENGLISH)
        val activity=instrumentation.startActivitySync(Intent(instrumentation.context,MysteryShopFixtureActivity::class.java)
            .putExtra("test_opening",true).putExtra("test_exchange",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MysteryShopFixtureActivity
        fun find(node: AccessibilityNodeInfo?,text: String): AccessibilityNodeInfo? {
            if(node==null)return null
            if(node.text?.toString()==text)return node
            for(i in 0 until node.childCount)find(node.getChild(i),text)?.let {return it}
            return null
        }
        fun waitFor(block: ()->Boolean) {
            val end=SystemClock.uptimeMillis()+15_000
            while(!block() && SystemClock.uptimeMillis()<end)SystemClock.sleep(75)
            assertTrue(block())
        }
        fun click(text: String) {
            waitFor {find(ui.rootInActiveWindow,text)!=null}
            find(ui.rootInActiveWindow,text)!!.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            instrumentation.waitForIdleSync();SystemClock.sleep(300)
            var node=find(ui.rootInActiveWindow,text)
            while(node!=null && !node.isClickable)node=node.parent
            if(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true) {instrumentation.waitForIdleSync();return}
            val bounds=Rect().also {find(ui.rootInActiveWindow,text)!!.getBoundsInScreen(it)}
            ui.executeShellCommand("input tap ${bounds.centerX()} ${bounds.centerY()}").use {android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()}
        }
        try {
            waitFor {find(ui.rootInActiveWindow,"Pets")!=null}
            // Start at the new shared Exchange; avoid confusing its tab with the Case convenience link.
            click("Accessories")
            waitFor {find(ui.rootInActiveWindow,"Redeem")!=null}
            click("Redeem")
            waitFor {find(ui.rootInActiveWindow,"Confirm Redemption")!=null}
            assertEquals(0,activity.redemptionRequests)
            assertEquals(75L,activity.exchangeModel.state.value.balance)
            click("Confirm Redemption")
            waitFor {find(ui.rootInActiveWindow,"Item redeemed successfully.")!=null}
            assertEquals(1,activity.redemptionRequests)
            assertEquals(45L,activity.exchangeModel.state.value.balance)
            assertEquals("e2300000-0000-0000-0000-000000000003",activity.exchangeModel.state.value.inventory.single().item.id)
            assertEquals(1000L,activity.itemCaseModel.state.value.coins)
            click("View in Inventory");assertEquals(1,activity.testInventoryViews)
            click("Equip");assertEquals(1,activity.testEquipActions)
            click("Done")
            waitFor {activity.exchangeModel.state.value.pending==null}
            assertEquals(1,activity.redemptionRequests)
        } finally {instrumentation.runOnMainSync {activity.finish()};AppLocale.select(previous)}
    }
}
