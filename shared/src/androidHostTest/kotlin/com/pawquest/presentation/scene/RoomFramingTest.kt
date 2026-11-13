package com.pawquest.presentation.scene

import com.pawquest.domain.model.FurnitureSlot
import kotlin.test.*
import kotlin.math.tan
import kotlin.math.abs

class RoomFramingTest {
    @Test fun fixedAnchorsStayBehindCatAndFitPortraitWithoutIncreasingPetEnvelope() {
        for(slot in FurnitureSlot.entries) {
            val a=roomAnchor(slot)
            assertTrue(a.z+a.maxHalf[2]<=-.12f)
            val max=floatArrayOf(abs(a.x)+a.maxHalf[0],abs(a.y)+a.maxHalf[1]*2,abs(a.z)+a.maxHalf[2])
            // Vertical bound here is deliberately loose; use exact floor -> height interval.
            max[1]=maxOf(abs(a.y),abs(a.y+2*a.maxHalf[1]))
            assertTrue(max.all { it<1.37f })
            for((w,h) in listOf(1080 to 1920,1080 to 2340,1080 to 2400,320 to 180)) {
                val frame=portraitFrame(w,h,1.37f,1.37f,1.37f)
                for(x in listOf(-max[0],max[0])) for(y in listOf(-max[1],max[1])) for(z in listOf(-max[2],max[2])) {
                    for(sine in listOf(0f,.18f)) {
                        val cosine=kotlin.math.sqrt(1-sine*sine)
                        val depth=frame.distance-(sine*y+cosine*z)
                        assertTrue(abs(x)/depth<tan(Math.PI/8)*w/h)
                        assertTrue(abs(cosine*y-sine*z)/depth<tan(Math.PI/8))
                    }
                }
            }
        }
    }
}
