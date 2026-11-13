package com.pawquest.presentation.inventory

import androidx.compose.runtime.compositionLocalOf
import com.pawquest.domain.model.EquippedItem

val LocalEquipment = compositionLocalOf<List<EquippedItem>> { emptyList() }
val LocalRoom = compositionLocalOf<List<com.pawquest.domain.model.RoomFurniture>> { emptyList() }
