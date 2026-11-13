package com.pawquest

import android.content.Context
import com.pawquest.localization.AppLanguage
import com.pawquest.localization.AppLocale
import java.util.Locale

/** Language is installation-wide, including signed-out screens, workers and widgets. */
object LanguagePreferences {
    fun read(context: Context): AppLanguage = AppLanguage.fromTag(
        context.getSharedPreferences("pawquest_language", Context.MODE_PRIVATE).getString("language", null)
            ?: Locale.getDefault().language
    )
    fun initialize(context: Context, onChanged: () -> Unit) {
        AppLocale.initialize(read(context)) { language ->
            context.getSharedPreferences("pawquest_language", Context.MODE_PRIVATE)
                .edit().putString("language", language.tag).commit()
            onChanged()
        }
    }
}
