package com.pawquest.presentation.components

import com.pawquest.domain.model.*

fun itemPlacementLabel(item: CatalogItem): String? {
    if(item.category==ItemCategory.ACCESSORY) return accessorySlot(item.code)?.let {
        when(it) {EquipmentSlot.HEAD->"Đầu";EquipmentSlot.FACE->"Mặt";EquipmentSlot.NECK->"Cổ";EquipmentSlot.BACK->"Lưng"}+" · 1 món mỗi vị trí"
    }
    return furnitureSlot(item.code)?.let {
        when(it) {
            FurnitureSlot.BED->"Đệm ngủ";FurnitureSlot.LAMP->"Đèn đứng";FurnitureSlot.CARPET->"Thảm"
            FurnitureSlot.WINDOW->"Cửa sổ";FurnitureSlot.CLOCK->"Đồng hồ";FurnitureSlot.LIGHTS->"Dây đèn"
            FurnitureSlot.BOOKSHELF->"Kệ sách";FurnitureSlot.CAT_TREE->"Trụ cào";FurnitureSlot.PLANT->"Chậu cây"
            FurnitureSlot.TEA_TABLE->"Bàn trà";FurnitureSlot.GRAMOPHONE->"Máy đĩa"
        }+" · vị trí cố định"
    }
}
