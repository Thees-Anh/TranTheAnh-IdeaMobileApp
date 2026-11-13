package com.pawquest.presentation.scene

import android.content.Context
import com.pawquest.domain.model.*
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class LoadedAccessory(val slot: EquipmentSlot,val parent: String,val position: FloatArray,
    val buffer: ByteBuffer,val bounds: AnimationBounds)
internal data class AccessoryAssets(val models: List<LoadedAccessory>,val warnings: List<String>) {
    companion object {
        /** Only local authored IDs are resolved. Server strings never become arbitrary paths. */
        fun load(context: Context,equipment: List<EquippedItem>,catHash: String): AccessoryAssets {
            val warnings=mutableListOf<String>()
            val models=equipment.mapNotNull { equipped ->
                try {
                    val name=when(equipped.assetId) {
                        "item.accessory.hat" -> "hat"
                        "item.accessory.glasses" -> "glasses"
                        "item.accessory.collar" -> "collar"
                        else -> error("Unknown asset")
                    }
                    val path="models/accessories/$name"
                    val meta=context.assets.open("$path.attachment.json").bufferedReader().use { JSONObject(it.readText()) }
                    require(meta.getString("cat_sha256")==catHash && meta.getString("slot")==equipped.slot.name)
                    val p=meta.getJSONArray("position")
                    require(p.length()==3)
                    val position=FloatArray(3) { p.getDouble(it).toFloat().also { n -> require(n.isFinite()) } }
                    val bytes=context.assets.open("$path.glb").use { it.readBytes() }
                    val buffer=ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.LITTLE_ENDIAN).apply { put(bytes);flip() }
                    require(bytes.size>=20 && buffer.getInt(0)==0x46546c67 && buffer.getInt(4)==2 && buffer.getInt(8)==bytes.size)
                    LoadedAccessory(equipped.slot,meta.getString("parent"),position,buffer,AnimationBounds.parse(meta.toString()))
                } catch(_: Exception) {
                    warnings+="${equipped.slot.name.lowercase().replaceFirstChar { it.uppercase() }} accessory preview unavailable."
                    null
                }
            }
            return AccessoryAssets(models,warnings)
        }
    }
}
