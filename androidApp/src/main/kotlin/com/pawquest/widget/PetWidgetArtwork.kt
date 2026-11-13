package com.pawquest.widget

import com.pawquest.R
import com.pawquest.domain.model.WidgetPetMood

/** Resource-only projection. Mood/ownership/stats continue to come from the verified cache. */
internal object PetWidgetArtwork {
    fun portrait(renderer: String?,mood: WidgetPetMood,known: Boolean=true): Int {
        if(!known) return R.drawable.pawquest_portrait_cat_normal
        return when(renderer) {
            "puppy" -> R.drawable.pawquest_portrait_puppy
            "rabbit" -> R.drawable.pawquest_portrait_rabbit
            "capybara" -> R.drawable.pawquest_portrait_capybara
            else -> when(mood) {
                WidgetPetMood.HUNGRY,WidgetPetMood.THIRSTY -> R.drawable.pawquest_portrait_cat_hungry
                WidgetPetMood.TIRED,WidgetPetMood.SLEEPING -> R.drawable.pawquest_portrait_cat_bored
                WidgetPetMood.HAPPY -> R.drawable.pawquest_portrait_cat_normal
            }
        }
    }
    fun badge(mood: WidgetPetMood)=when(mood) {
        WidgetPetMood.HAPPY -> R.drawable.widget_mood_happy
        WidgetPetMood.HUNGRY -> R.drawable.widget_mood_hungry
        WidgetPetMood.THIRSTY -> R.drawable.widget_mood_thirsty
        WidgetPetMood.TIRED -> R.drawable.widget_mood_tired
        WidgetPetMood.SLEEPING -> R.drawable.widget_mood_sleeping
    }
}
