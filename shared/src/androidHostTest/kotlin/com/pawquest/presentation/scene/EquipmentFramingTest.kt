package com.pawquest.presentation.scene

import kotlin.test.*
import kotlin.math.tan

class EquipmentFramingTest {
    @Test fun expandedAccessoryEnvelopeFitsAllPortraitRatiosAndShortScenePanels() {
        // Conservative normalized envelope larger than the bundled hat + ears envelope.
        for((width,height) in listOf(1080 to 1920,1080 to 2340,1080 to 2400,320 to 180)) {
            val half=2f
            val frame=portraitFrame(width,height,half,half,half)
            val vertical=tan(Math.PI/8)
            val horizontal=vertical*width/height
            for(x in listOf(-half,half)) for(y in listOf(-half,half)) for(z in listOf(-half,half)) {
                assertTrue(kotlin.math.abs(x)/(frame.distance-z)<horizontal)
                assertTrue(kotlin.math.abs(y)/(frame.distance-z)<vertical)
                assertTrue(frame.distance-z>frame.near && frame.distance-z<frame.far)
            }
        }
    }
}
