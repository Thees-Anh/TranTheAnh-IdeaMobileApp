package com.pawquest.domain.model

/** Generated from design/assets/decor/catalog.json; prices always come from the server. */
object DecorCatalog {
    val equipmentSlots=mapOf(
        "acc_crown" to EquipmentSlot.HEAD,
        "acc_wizard_hat" to EquipmentSlot.HEAD,
        "acc_sprout" to EquipmentSlot.HEAD,
        "acc_headphones" to EquipmentSlot.HEAD,
        "acc_glasses" to EquipmentSlot.FACE,
        "acc_monocle" to EquipmentSlot.FACE,
        "acc_bowtie" to EquipmentSlot.NECK,
        "acc_scarf" to EquipmentSlot.NECK,
        "acc_wings" to EquipmentSlot.BACK,
        "acc_backpack" to EquipmentSlot.BACK
    )
    val furnitureSlots=mapOf(
        "furn_window" to FurnitureSlot.WINDOW,
        "furn_clock" to FurnitureSlot.CLOCK,
        "furn_lights" to FurnitureSlot.LIGHTS,
        "furn_bookshelf" to FurnitureSlot.BOOKSHELF,
        "furn_cat_tree" to FurnitureSlot.CAT_TREE,
        "furn_bed" to FurnitureSlot.BED,
        "furn_plant" to FurnitureSlot.PLANT,
        "furn_tea_table" to FurnitureSlot.TEA_TABLE,
        "furn_gramophone" to FurnitureSlot.GRAMOPHONE,
        "furn_lamp" to FurnitureSlot.LAMP
    )
    val assetIds=setOf("acc-crown","acc-wizard-hat","acc-sprout","acc-headphones","acc-glasses","acc-monocle","acc-bowtie","acc-scarf","acc-wings","acc-backpack","furn-window","furn-clock","furn-lights","furn-bookshelf","furn-cat-tree","furn-bed","furn-plant","furn-tea-table","furn-gramophone","furn-lamp")
    val supportedAssets=assetIds+setOf("item.accessory.hat","item.accessory.glasses","item.accessory.collar","item.furniture.bed","item.furniture.lamp","item.furniture.carpet")
}
