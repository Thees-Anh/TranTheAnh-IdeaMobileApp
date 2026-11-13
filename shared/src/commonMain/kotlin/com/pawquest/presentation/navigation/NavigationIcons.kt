package com.pawquest.presentation.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.pawquest.presentation.icons.CartoonIcons

val Destination.icon: ImageVector get() = when(this) {
    Destination.Home -> CartoonIcons.Home
    Destination.Quests -> CartoonIcons.Quest
    Destination.PetRoom -> CartoonIcons.Profile
    Destination.Explore -> CartoonIcons.Shop
}
