package com.pawquest.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pawquest.localization.tr
import com.pawquest.presentation.components.ButtonVariant
import com.pawquest.presentation.components.PawQuestButton

enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "Match system"), LIGHT("light", "Light"), DARK("dark", "Dark");
    companion object { fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM }
}

/** Installation-wide appearance preference, persisted by the platform like the language. */
object AppAppearance {
    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set
    private var persist: (ThemeMode) -> Unit = {}
    fun initialize(mode: ThemeMode, persist: (ThemeMode) -> Unit = {}) { this.persist = persist; this.mode = mode }
    fun select(mode: ThemeMode) { if (this.mode != mode) { persist(mode); this.mode = mode } }
}

@Composable
fun isAppInDarkTheme(): Boolean = when (AppAppearance.mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun AppearanceSelector() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("Appearance"), style = PawQuestTheme.typography.titleMedium, color = PawQuestTheme.colors.textPrimary)
        ThemeMode.entries.forEach { mode ->
            val selected = AppAppearance.mode == mode
            PawQuestButton(
                text = (if (selected) "✓ " else "") + tr(mode.label),
                onClick = { AppAppearance.select(mode) },
                variant = if (selected) ButtonVariant.Primary else ButtonVariant.Outlined,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
