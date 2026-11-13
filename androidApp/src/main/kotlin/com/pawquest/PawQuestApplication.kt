package com.pawquest

import android.app.Application
import com.pawquest.data.auth.createAndroidRepositories
import com.pawquest.di.createAppContainer
import org.koin.core.KoinApplication

class PawQuestApplication : Application() {
    lateinit var container: KoinApplication
        private set

    lateinit var companion: com.pawquest.companion.CompanionCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        AppearancePreferences.initialize(this)
        LanguagePreferences.initialize(this) {
            com.pawquest.widget.PetWidgetStore(this).renderAll()
            if (::companion.isInitialized) companion.languageChanged()
        }
        val repositories = createAndroidRepositories(
            this, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY, BuildConfig.DEBUG,
        )
        container = createAppContainer(repositories.authentication, repositories.quests, repositories.questSessions,
            repositories.focus,repositories.pets,repositories.items,repositories.inventory,repositories.equipment,repositories.room,repositories.progress,
            repositories.itemCases,repositories.shards,repositories.pendingItemCases,repositories.pendingItemExchange,
            repositories.luckyWheel,repositories.petExchange,repositories.pendingLucky)
        companion=com.pawquest.companion.CompanionCoordinator(this,repositories)
    }
}
