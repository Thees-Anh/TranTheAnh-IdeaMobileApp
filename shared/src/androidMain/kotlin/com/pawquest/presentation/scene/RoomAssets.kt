package com.pawquest.presentation.scene

import android.content.Context
import com.pawquest.domain.model.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

/** Fixed normalized scene units. Positive Z faces the camera; furniture stays behind the cat. */
internal data class RoomAnchor(val x: Float,val y: Float,val z: Float,val maxHalf: FloatArray)
internal fun roomAnchor(slot: FurnitureSlot)=when(slot) {
    FurnitureSlot.BED -> RoomAnchor(-.85f,-.94f,-.70f,floatArrayOf(.34f,.22f,.25f))
    FurnitureSlot.LAMP -> RoomAnchor(.85f,-.94f,-.80f,floatArrayOf(.19f,.48f,.19f))
    FurnitureSlot.CARPET -> RoomAnchor(0f,-.97f,-.70f,floatArrayOf(.97f,.03f,.52f))
    else -> RoomAnchor(0f,-.94f,-.80f,floatArrayOf(.2f,.2f,.2f)) // New decor is SVG-only; loader skips missing GLB assets.
}
internal data class RoomModel(val slot: FurnitureSlot,val assetId: String,val buffer: ByteBuffer)
internal data class RoomAssets(val models: List<RoomModel>,val warnings: List<String>)

/** Cache at most the three allowlisted immutable APK buffers for this Compose scene. */
internal class RoomAssetLoader(private val context: Context) {
    private val cache=ConcurrentHashMap<String,ByteBuffer>()
    fun load(room: List<RoomFurniture>): RoomAssets {
        val warnings=mutableListOf<String>()
        val models=room.mapNotNull { item ->
            try {
                val name=when(item.assetId to item.slot) {
                    "item.furniture.bed" to FurnitureSlot.BED -> "bed"
                    "item.furniture.lamp" to FurnitureSlot.LAMP -> "lamp"
                    "item.furniture.carpet" to FurnitureSlot.CARPET -> "carpet"
                    else -> error("Unsupported furniture asset")
                }
                val bytes=cache.computeIfAbsent(name) {
                    val raw=context.assets.open("models/furniture/$name.glb").use { it.readBytes() }
                    ByteBuffer.allocateDirect(raw.size).order(ByteOrder.LITTLE_ENDIAN).apply {
                        put(raw);flip()
                        require(raw.size>=20 && getInt(0)==0x46546c67 && getInt(4)==2 && getInt(8)==raw.size)
                    }
                }
                RoomModel(item.slot,item.assetId,bytes)
            } catch(_: Exception) {
                warnings+="${item.slot.name.lowercase().replaceFirstChar { it.uppercase() }} preview unavailable."
                null
            }
        }
        return RoomAssets(models,warnings)
    }
}
