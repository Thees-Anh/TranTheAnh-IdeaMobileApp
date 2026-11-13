package com.pawquest.presentation.scene

import kotlin.math.PI
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PortraitFramingTest {
    @Test
    fun allBoundingBoxCornersFitEveryPortraitViewport() {
        for ((width, height) in listOf(1080 to 1920, 1080 to 2340, 1080 to 2400, 320 to 360)) {
            for ((x, y, z) in listOf(Triple(1f, 0.2f, 0.3f), Triple(0.4f, 1f, 0.5f), Triple(0.1f, 0.2f, 1f))) {
                val frame = portraitFrame(width, height, x, y, z)
                val tangentY = tan(45.0 * PI / 360)
                val tangentX = tangentY * width / height
                for (cornerZ in listOf(-z, z)) {
                    val depth = frame.distance - cornerZ
                    assertTrue(x / (depth * tangentX) < 1.0)
                    assertTrue(y / (depth * tangentY) < 1.0)
                    assertTrue(depth > frame.near && depth < frame.far)
                }
            }
        }
    }

    @Test
    fun tallerNarrowerViewportMovesCameraBack() {
        val standard = portraitFrame(1080, 1920, 1f, 1f, 1f)
        val tall = portraitFrame(1080, 2400, 1f, 1f, 1f)
        assertTrue(tall.distance > standard.distance)
    }

    @Test
    fun invalidViewportsAndBoundsAreRejected() {
        assertFailsWith<IllegalArgumentException> { portraitFrame(0, 100, 1f, 1f, 1f) }
        assertFailsWith<IllegalArgumentException> { portraitFrame(100, 100, Float.NaN, 1f, 1f) }
        assertFailsWith<IllegalArgumentException> { portraitFrame(100, 100, 0f, 0f, 0f) }
    }
}
