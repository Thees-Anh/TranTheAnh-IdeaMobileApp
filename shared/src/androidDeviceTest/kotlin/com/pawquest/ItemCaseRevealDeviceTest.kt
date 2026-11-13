package com.pawquest

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.pawquest.localization.AppLanguage
import com.pawquest.localization.AppLocale
import com.pawquest.presentation.gacha.ItemCasePhase
import org.junit.Assert.*
import org.junit.Test

class ItemCaseRevealDeviceTest {
    @Test fun explicitOpeningSkipsToAuthoritativeComposeDuplicateRevealWithoutResubmission()=checkReveal(false)
    @Test fun newItemRevealReusesInventoryAndEquipActions()=checkReveal(true)
    private fun checkReveal(grant: Boolean) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val ui=instrumentation.uiAutomation
        val previous=AppLocale.language; AppLocale.select(AppLanguage.ENGLISH)
        val activity=instrumentation.startActivitySync(Intent(instrumentation.context,MysteryShopFixtureActivity::class.java)
            .putExtra("test_opening",true).putExtra("test_grant",grant).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MysteryShopFixtureActivity
        fun find(node: AccessibilityNodeInfo?,text: String): AccessibilityNodeInfo? {
            if(node==null) return null
            if(node.text?.toString()==text) return node
            for(i in 0 until node.childCount) find(node.getChild(i),text)?.let {return it}
            return null
        }
        fun waitFor(test: ()->Boolean) {
            val deadline=SystemClock.uptimeMillis()+15_000
            while(!test() && SystemClock.uptimeMillis()<deadline) SystemClock.sleep(75)
            fun texts(node: AccessibilityNodeInfo?): String {
                if(node==null) return ""
                if(node.className?.toString()=="android.webkit.WebView") return "[WebView]"
                return "[${node.text}]"+(0 until node.childCount).joinToString {texts(node.getChild(it))}
            }
            assertTrue("phase=${activity.itemCaseModel.state.value.phase}, history=${activity.itemCaseModel.state.value.history}, error=${activity.itemCaseModel.state.value.historyError}, ui=${texts(ui.rootInActiveWindow).take(3000)}",test())
        }
        fun click(text: String) {
            if(text=="Skip Animation") {
                // Already visible in the full-screen surface; a scroll-to-node
                // request can race natural completion and target the next window.
                var skip=find(ui.rootInActiveWindow,text)
                while(skip!=null && !skip.isClickable) skip=skip.parent
                if(skip==null && activity.itemCaseModel.state.value.phase==ItemCasePhase.RewardRevealed) return
                assertTrue("Skip confirmed animation",skip?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true)
                instrumentation.waitForIdleSync();return
            }
            waitFor {find(ui.rootInActiveWindow,text)!=null}
            find(ui.rootInActiveWindow,text)!!.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            instrumentation.waitForIdleSync();SystemClock.sleep(300)
            var node: AccessibilityNodeInfo?=find(ui.rootInActiveWindow,text)
            while(node!=null && !node.isClickable) node=node.parent
            if(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true) {
                instrumentation.waitForIdleSync();return
            }
            val bounds=Rect().also {find(ui.rootInActiveWindow,text)!!.getBoundsInScreen(it)}
            ui.executeShellCommand("input tap ${bounds.centerX()} ${bounds.centerY()}").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
        }
        fun scrollToActions() {
            val metrics=activity.resources.displayMetrics
            ui.executeShellCommand("input swipe ${metrics.widthPixels/2} ${metrics.heightPixels*3/4} ${metrics.widthPixels/2} ${metrics.heightPixels*3/8} 400").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
            SystemClock.sleep(500)
        }
        try {
            waitFor {find(ui.rootInActiveWindow,"Case preview")!=null}
            // Scroll the native case panel to its purchase control, keeping bottom nav intact.
            scrollToActions()
            if(grant) {
                click("View Rewards")
                waitFor {find(ui.rootInActiveWindow,"Rare glasses")!=null}
                assertNotNull(find(ui.rootInActiveWindow,"Not owned"))
                click("Close")
                click("Drop Rates")
                waitFor {find(ui.rootInActiveWindow,"Duplicate reward: 5 Item Shards")!=null}
                assertNotNull(find(ui.rootInActiveWindow,"COMMON \u00b7 55.0000%"))
                click("Close")
                click("Opening History")
                waitFor {find(ui.rootInActiveWindow,"No case openings yet.")!=null}
                click("Close")
            }
            click("Open Case")
            waitFor {find(ui.rootInActiveWindow,"Open Mystery Box?")!=null}
            click("Open Case") // Explicit native confirmation.
            waitFor {activity.itemCaseModel.state.value.phase==ItemCasePhase.Animating}
            assertEquals(1,activity.openingRequests.size)
            fun webView(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if(node==null) return null
                if(node.className?.toString()=="android.webkit.WebView") return node
                for(i in 0 until node.childCount) webView(node.getChild(i))?.let {return it}
                return null
            }
            val stage=Rect().also {checkNotNull(webView(ui.rootInActiveWindow)).getBoundsInScreen(it)}
            val screen=activity.resources.displayMetrics
            assertTrue("Opening must use the full phone width: $stage",stage.width()>=screen.widthPixels*0.9)
            assertTrue("Opening must be taller than the Shop preview: $stage",stage.height()>=screen.heightPixels*0.65)
            click("Skip Animation")
            waitFor {find(ui.rootInActiveWindow,if(grant) "New Item" else "Duplicate Item")!=null}
            assertNotNull(find(ui.rootInActiveWindow,"Rare glasses"))
            if(grant) {
                click("View in Inventory");assertEquals(1,activity.testInventoryViews)
                click("Equip");assertEquals(1,activity.testEquipActions)
                assertEquals(1,activity.openingRequests.size)
            } else {
                assertNotNull(find(ui.rootInActiveWindow,"Duplicate converted to 10 Item Shards."))
                click("View Shard Wallet")
                waitFor {find(ui.rootInActiveWindow,"Balance after: 85 Item Shards")!=null}
                assertEquals(1,activity.openingRequests.size)
                click("Close")
                waitFor {find(ui.rootInActiveWindow,"Duplicate Item")!=null}
                click("Open Again")
                waitFor {find(ui.rootInActiveWindow,"Open Mystery Box?")!=null}
                assertEquals(1,activity.openingRequests.size)
                click("Open Again")
                waitFor {activity.openingRequests.size==2 && activity.itemCaseModel.state.value.phase==ItemCasePhase.Animating}
                click("Skip Animation")
                waitFor {find(ui.rootInActiveWindow,"Duplicate Item")!=null}
                assertNotEquals(activity.openingRequests[0].requestId,activity.openingRequests[1].requestId)
            }
            assertEquals("e2300000-0000-0000-0000-000000000005",activity.itemCaseModel.state.value.winningItemId)
            val count=activity.openingRequests.size
            click("Close")
            waitFor {activity.itemCaseModel.state.value.phase==ItemCasePhase.Idle}
            assertEquals(count,activity.openingRequests.size)
            if(grant) {
                scrollToActions()
                click("Opening History")
                waitFor {find(ui.rootInActiveWindow,"Item Granted")!=null}
                assertNotNull(find(ui.rootInActiveWindow,"Rare glasses"))
                click("Close")
            }
        } finally {
            instrumentation.runOnMainSync {activity.finish()}; AppLocale.select(previous)
        }
    }
}
