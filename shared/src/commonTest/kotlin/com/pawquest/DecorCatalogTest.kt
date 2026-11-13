package com.pawquest

import com.pawquest.domain.model.*
import kotlin.test.*

class DecorCatalogTest {
    @Test fun allNewItemsResolveToTheExpectedSlotWithoutReplacingLegacyMappings() {
        assertEquals(20,DecorCatalog.assetIds.size)
        assertEquals(10,DecorCatalog.equipmentSlots.size)
        assertEquals(10,DecorCatalog.furnitureSlots.size)
        for(code in listOf("acc_crown","acc_wizard_hat","acc_sprout","acc_headphones")) assertEquals(EquipmentSlot.HEAD,accessorySlot(code))
        for(code in listOf("acc_glasses","acc_monocle")) assertEquals(EquipmentSlot.FACE,accessorySlot(code))
        for(code in listOf("acc_bowtie","acc_scarf")) assertEquals(EquipmentSlot.NECK,accessorySlot(code))
        for(code in listOf("acc_wings","acc_backpack")) assertEquals(EquipmentSlot.BACK,accessorySlot(code))
        assertEquals(EquipmentSlot.HEAD,accessorySlot("hat"));assertEquals(FurnitureSlot.BED,furnitureSlot("bed"))
        assertEquals(FurnitureSlot.BED,furnitureSlot("furn_bed"));assertEquals(FurnitureSlot.WINDOW,furnitureSlot("furn_window"))
        assertEquals(FurnitureSlot.CAT_TREE,furnitureSlot("furn_cat_tree"))
        assertNull(accessorySlot("unknown"));assertNull(furnitureSlot("unknown"))
    }
    @Test fun accessoriesAndFurnitureRemainUniqueWhileFoodStacks() {
        for((code,category) in listOf("acc_wings" to ItemCategory.ACCESSORY,"furn_window" to ItemCategory.FURNITURE)) {
            val item=CatalogItem(code,code,code,category,200,code.replace('_','-'),1,true,"Supplied artwork")
            assertFalse(item.stackable)
            assertFailsWith<IllegalArgumentException> {InventoryItem("i","owner",item,2,kotlin.time.Instant.parse("2026-11-09T00:00:00Z"))}
        }
    }
}
