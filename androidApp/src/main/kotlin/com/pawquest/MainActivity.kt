package com.pawquest

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.pawquest.companion.CompanionAndroidHost
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.pawquest.presentation.scene.PrototypePetScene

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        val language = LanguagePreferences.read(newBase)
        val config = android.content.res.Configuration(newBase.resources.configuration)
        config.setLocale(java.util.Locale.forLanguageTag(language.tag))
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }
    override fun onStart() {
        super.onStart();(application as PawQuestApplication).companion.appBackground(false)
    }
    override fun onStop() {
        if(!isChangingConfigurations) (application as PawQuestApplication).companion.appBackground(true)
        super.onStop()
    }
    private var requestedRoute by mutableStateOf<String?>(null)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent);setIntent(intent)
        requestedRoute=intent.getStringExtra("pawquest_destination")
    }
    private fun applySystemBars(dark: Boolean) {
        val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.BLACK)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedRoute=intent.getStringExtra("pawquest_destination")
        applySystemBars(dark = false)
        val container = (application as PawQuestApplication).container
        setContent {
            // Status/navigation bar icons follow the in-app appearance, not only the system setting.
            val dark = com.pawquest.presentation.theme.isAppInDarkTheme()
            androidx.compose.runtime.LaunchedEffect(dark) { applySystemBars(dark) }
            val language = com.pawquest.localization.AppLocale.language
            androidx.compose.runtime.LaunchedEffect(language) {
                if (resources.configuration.locales[0].language != language.tag) recreate()
            }
            CompanionAndroidHost((application as PawQuestApplication).companion) {
                App(container, petScene = { modifier ->
                    PrototypePetScene(modifier = modifier, showDebugControls = BuildConfig.DEBUG)
                },focusCompanion={com.pawquest.presentation.scene.FocusCompanion(it)},requestedRoute=requestedRoute,onRouteConsumed={requestedRoute=null;intent.removeExtra("pawquest_destination")},
                    casePreview={com.pawquest.presentation.gacha.MysteryCasePreview(it)},
                    caseAnimation={animation,modifier->com.pawquest.presentation.gacha.MysteryCasePreview(modifier,animation)},
                    wheelPreview={animation,modifier->com.pawquest.presentation.gacha.LuckyWheelPreview(modifier,animation)})
            }
        }
    }
}

// Preview dimensions are dp examples of portrait phone ratios, never production constraints.
@Preview(name = "9:16 (360x640)", widthDp = 360, heightDp = 640, showSystemUi = true)
@Preview(name = "19.5:9 (360x780)", widthDp = 360, heightDp = 780, showSystemUi = true)
@Preview(name = "20:9 (360x800)", widthDp = 360, heightDp = 800, showSystemUi = true)
@Preview(name = "Compact (320x568)", widthDp = 320, heightDp = 568, showSystemUi = true)
@Composable
fun AppAndroidPreview() {
    PreviewPawQuest()
}

@Preview(name = "Design System Showcase", widthDp = 360, heightDp = 780, showSystemUi = true)
@Composable
fun DesignSystemShowcasePreview() {
    PreviewShowcase()
}
