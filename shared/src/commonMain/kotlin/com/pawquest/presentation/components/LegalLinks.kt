package com.pawquest.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.pawquest.localization.tr
import com.pawquest.presentation.theme.PawQuestTheme

/** Public legal pages required by Google Play (User Data and account-deletion policies). */
data class LegalLinks(val privacyPolicyUrl: String="",val accountDeletionUrl: String="") {
    val hasPrivacyPolicy get()=privacyPolicyUrl.startsWith("https://")
}

val LocalLegalLinks=staticCompositionLocalOf {LegalLinks()}

/** Opens the privacy policy in the browser; hidden until a release URL is configured. */
@Composable
fun PrivacyPolicyLink(modifier: Modifier=Modifier.fillMaxWidth(),enabled: Boolean=true) {
    val links=LocalLegalLinks.current
    if(!links.hasPrivacyPolicy) return
    val uri=LocalUriHandler.current
    TextButton(onClick={runCatching {uri.openUri(links.privacyPolicyUrl)}},enabled=enabled,modifier=modifier) {
        Text(tr("Privacy Policy"),color=PawQuestTheme.colors.textPrimary)
    }
}
