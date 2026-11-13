package com.pawquest

import android.content.Context
import com.pawquest.presentation.theme.AppAppearance
import com.pawquest.presentation.theme.ThemeMode

/** Light/Dark/System choice is installation-wide, like the language. */
object AppearancePreferences {
    private fun prefs(context: Context) = context.getSharedPreferences("pawquest_appearance", Context.MODE_PRIVATE)
    fun initialize(context: Context) {
        AppAppearance.initialize(ThemeMode.fromKey(prefs(context).getString("mode", null))) { mode ->
            prefs(context).edit().putString("mode", mode.key).apply()
        }
    }
}
