package com.pawquest

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.pawquest.localization.AppLanguage
import com.pawquest.localization.AppLocale
import org.junit.Assert.*
import org.junit.Test

class LuckyWheelShopDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val ui=instrumentation.uiAutomation
    private fun find(node: AccessibilityNodeInfo?,text: String): AccessibilityNodeInfo? {
        if(node==null)return null
        if(node.text?.toString()==text)return node
        for(i in 0 until node.childCount)find(node.getChild(i),text)?.let {return it}
        return null
    }
    private fun waitFor(block: ()->Boolean) {
        val end=SystemClock.uptimeMillis()+15_000
        while(!block() && SystemClock.uptimeMillis()<end)SystemClock.sleep(75)
        assertTrue("UI condition",block())
    }
    private fun show(text: String) {
        fun vertical(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if(node==null)return null
            if(node.actionList.any {it.id==AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id})return node
            for(i in 0 until node.childCount)vertical(node.getChild(i))?.let {return it}
            return null
        }
        repeat(16) {
            if(find(ui.rootInActiveWindow,text)!=null)return
            vertical(ui.rootInActiveWindow)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            instrumentation.waitForIdleSync();SystemClock.sleep(150)
        }
        waitFor {find(ui.rootInActiveWindow,text)!=null}
    }
    private fun click(text: String) {
        show(text)
        val original=find(ui.rootInActiveWindow,text)!!
        original.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        instrumentation.waitForIdleSync();SystemClock.sleep(200)
        var node=find(ui.rootInActiveWindow,text)
        while(node!=null && !node.isClickable)node=node.parent
        assertTrue("Click $text",node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true)
        instrumentation.waitForIdleSync()
    }
    private fun fixture(fallback: Boolean=false,block: (MysteryShopFixtureActivity)->Unit) {
        val old=AppLocale.language;AppLocale.select(AppLanguage.ENGLISH)
        val activity=instrumentation.startActivitySync(Intent(instrumentation.context,MysteryShopFixtureActivity::class.java)
            .putExtra("test_wheel_fallback",fallback).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MysteryShopFixtureActivity
        try {block(activity)} finally {instrumentation.runOnMainSync {activity.finish()};AppLocale.select(old)}
    }
    @Test fun fourShopSectionsFoodFlowAndNoDirectNonFoodPurchases()=fixture {
        waitFor {find(ui.rootInActiveWindow,"Food fixture")!=null}
        assertNull(find(ui.rootInActiveWindow,"Accessory fixture"));assertNull(find(ui.rootInActiveWindow,"Furniture fixture"))
        click("Buy");waitFor {find(ui.rootInActiveWindow,"Cancel")!=null};click("Cancel")
        click("Mystery Box");waitFor {find(ui.rootInActiveWindow,"Case preview")!=null}
        click("Lucky Wheel");show("Spin using Coins")
        click("Shard Exchange");waitFor {find(ui.rootInActiveWindow,"Pets")!=null}
        click("Accessories");waitFor {find(ui.rootInActiveWindow,"Item Shards: 75")!=null}
        click("Furniture");waitFor {find(ui.rootInActiveWindow,"Item Shards: 75")!=null}
        waitFor {find(ui.rootInActiveWindow,"Pet Home")!=null && find(ui.rootInActiveWindow,"Inventory")!=null}
    }
    @Test fun coinAndTicketSpinsRevealConfirmedPetAndNeverAutoOpenAgain()=fixture {activity ->
        click("Lucky Wheel");click("Spin using Coins")
        waitFor {find(ui.rootInActiveWindow,"Skip Animation")!=null}
        SystemClock.sleep(900) // Wait for scroll and Chromium's compositor, not only the JS-ready flag.
        fun webNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if(node==null)return null
            if(node.className?.toString()=="android.webkit.WebView")return node
            for(i in 0 until node.childCount)webNode(node.getChild(i))?.let {return it}
            return null
        }
        val webBounds=android.graphics.Rect().also {checkNotNull(webNode(ui.rootInActiveWindow)).getBoundsInScreen(it)}
        val metrics=activity.resources.displayMetrics
        assertTrue("Full-screen wheel width: $webBounds",webBounds.width()>=metrics.widthPixels*0.9)
        assertTrue("Full-screen wheel height: $webBounds",webBounds.height()>=metrics.heightPixels*0.65)
        val diagnostics="full-screen local renderer"
        ui.takeScreenshot()?.let {bitmap -> java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"lucky-wheel-shop.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            var yellow=0
            for(y in webBounds.top.coerceAtLeast(0) until webBounds.bottom.coerceAtMost(bitmap.height) step 8)
                for(x in webBounds.left.coerceAtLeast(0) until webBounds.right.coerceAtMost(bitmap.width) step 8) {
                    val color=bitmap.getPixel(x,y)
                    if(android.graphics.Color.red(color)>180 && android.graphics.Color.green(color)>130 && android.graphics.Color.blue(color)<180) yellow++
                }
            assertTrue("Wheel must paint in Compose host: yellow=$yellow bounds=$webBounds DOM=$diagnostics",yellow>100)
        }}
        waitFor {find(ui.rootInActiveWindow,"Wheel Reward")!=null}
        waitFor {find(ui.rootInActiveWindow,"Pet unlocked: Rabbit")!=null}
        assertEquals(1,activity.luckyFixture.spinCalls)
        click("Open Again");assertEquals(1,activity.luckyFixture.spinCalls)
        click("Spin using Ticket");waitFor {find(ui.rootInActiveWindow,"Wheel Reward")!=null}
        assertEquals(2,activity.luckyFixture.spinCalls);assertEquals(900,activity.luckyFixture.coins);assertEquals(0,activity.luckyFixture.tickets)
        click("Close")
    }
    @Test fun petExchangeQuotesConfirmsAndRefreshesPetShardBalance()=fixture {activity ->
        click("Shard Exchange");click("Redeem")
        waitFor {find(ui.rootInActiveWindow,"Confirm Pet Redemption")!=null}
        assertEquals(0,activity.luckyFixture.redeemCalls)
        click("Confirm Redemption");waitFor {find(ui.rootInActiveWindow,"Pet Unlocked")!=null}
        assertEquals(1,activity.luckyFixture.redeemCalls);assertTrue("rabbit" in activity.luckyFixture.owned)
        waitFor {find(ui.rootInActiveWindow,"Pet Shards: 160")!=null};click("Done")
    }
    @Test fun allOwnedFallbackDisplaysShardsWithoutNewPet()=fixture(true) {activity ->
        click("Lucky Wheel");click("Spin using Coins")
        waitFor {find(ui.rootInActiveWindow,"All pets owned: +50 Pet Shards")!=null}
        assertEquals(260,activity.luckyFixture.shards);assertEquals(3,activity.luckyFixture.owned.size);click("Close")
    }
}
