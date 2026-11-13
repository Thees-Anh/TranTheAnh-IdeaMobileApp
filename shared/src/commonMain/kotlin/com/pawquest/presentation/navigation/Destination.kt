package com.pawquest.presentation.navigation

enum class Destination(val route: String, val label: String) {
    PetHome("pet_home", "Pet Home"),
    Quests("quests", "Quests"),
    Shop("shop", "Shop"),
    Inventory("inventory", "Inventory"),
    Profile("profile", "Profile");

    companion object {
        fun fromRoute(route: String): Destination = entries.firstOrNull { it.route == route } ?: PetHome
    }
}
