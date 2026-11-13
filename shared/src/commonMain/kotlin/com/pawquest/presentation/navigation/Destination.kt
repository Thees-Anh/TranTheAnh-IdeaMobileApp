package com.pawquest.presentation.navigation

/** Habit-first tabs: today's tasks lead, the pet and rewards follow. */
enum class Destination(val route: String, val label: String) {
    Home("home", "Home page"),
    Quests("quests", "Quests"),
    PetRoom("pet_home", "Pet Room"),
    Explore("explore", "Explore");

    companion object {
        /** Routes from notifications, widgets and older builds still land on the matching new tab. */
        fun fromRoute(route: String): Destination = when (route) {
            "shop" -> Explore
            "inventory" -> PetRoom
            "profile" -> Home
            else -> entries.firstOrNull { it.route == route } ?: Home
        }
    }
}
