package com.pawquest.presentation.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.pawquest.presentation.icons.CartoonIcons

val Destination.icon: ImageVector get() = when(this) {
    Destination.PetHome -> CartoonIcons.Home
    Destination.Quests -> CartoonIcons.Quest
    Destination.Shop -> CartoonIcons.Shop
    Destination.Inventory -> CartoonIcons.Inventory
    Destination.Profile -> CartoonIcons.Profile
}
