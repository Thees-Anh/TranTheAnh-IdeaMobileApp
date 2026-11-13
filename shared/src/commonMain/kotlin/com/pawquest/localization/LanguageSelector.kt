package com.pawquest.localization

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pawquest.presentation.components.*
import com.pawquest.presentation.theme.PawQuestTheme

@Composable
fun LanguageSelector() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("Language"), style = PawQuestTheme.typography.titleMedium)
        // Native language names remain recognizable even before the user switches language.
        AppLanguage.entries.forEach { language ->
            PawQuestButton(
                text = (if (AppLocale.language == language) "✓ " else "") + language.nativeName,
                onClick = { AppLocale.select(language) },
                variant = if (AppLocale.language == language) ButtonVariant.Primary else ButtonVariant.Outlined,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
