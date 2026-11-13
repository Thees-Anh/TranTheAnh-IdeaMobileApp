package com.pawquest.presentation.scene

import org.json.JSONObject

internal data class AnimationBounds(val center: FloatArray, val half: FloatArray) {
    fun including(staticCenter: FloatArray,staticHalf: FloatArray): AnimationBounds {
        val low=FloatArray(3) { minOf(center[it]-half[it],staticCenter[it]-staticHalf[it]) }
        val high=FloatArray(3) { maxOf(center[it]+half[it],staticCenter[it]+staticHalf[it]) }
        return AnimationBounds(FloatArray(3) { (low[it]+high[it])/2 },FloatArray(3) { (high[it]-low[it])/2 })
    }
    companion object {
        fun parse(json: String): AnimationBounds {
            val value = JSONObject(json)
            val min = value.getJSONArray("min")
            val max = value.getJSONArray("max")
            require(min.length() == 3 && max.length() == 3)
            val low = FloatArray(3) { min.getDouble(it).toFloat() }
            val high = FloatArray(3) { max.getDouble(it).toFloat() }
            require((0..2).all { low[it].isFinite() && high[it].isFinite() && high[it] > low[it] })
            return AnimationBounds(FloatArray(3) { (low[it] + high[it]) / 2 }, FloatArray(3) { (high[it] - low[it]) / 2 })
        }
    }
}
