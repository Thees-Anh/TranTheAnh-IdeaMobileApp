package com.pawquest.presentation.scene

import kotlin.math.atan
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

internal data class PortraitFrame(val distance: Float, val near: Float, val far: Float)

// Bounding sphere encloses every corner, including depth, at any viewport aspect.
// Uniform normalization makes units predictable without changing model proportions.
internal fun portraitFrame(
    width: Int,
    height: Int,
    halfX: Float,
    halfY: Float,
    halfZ: Float,
    verticalFovDegrees: Double = 45.0,
): PortraitFrame {
    require(width > 0 && height > 0)
    require(listOf(halfX, halfY, halfZ).all { it.isFinite() && it >= 0f })
    require(verticalFovDegrees > 0.0 && verticalFovDegrees < 180.0)
    val radius = sqrt(halfX * halfX + halfY * halfY + halfZ * halfZ)
    require(radius.isFinite() && radius > 0f)
    val verticalHalf = verticalFovDegrees * Math.PI / 360.0
    val horizontalHalf = atan(tan(verticalHalf) * width.toDouble() / height)
    val limitingHalf = minOf(verticalHalf, horizontalHalf)
    val distance = (radius * 1.12 / sin(limitingHalf)).toFloat()
    return PortraitFrame(
        distance = distance,
        near = max(0.001f, (distance - radius) * 0.5f),
        far = distance + radius * 3f,
    )
}
