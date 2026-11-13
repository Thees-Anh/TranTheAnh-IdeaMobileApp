package com.pawquest.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pawquest.localization.tr
import com.pawquest.presentation.icons.CartoonIcons
import com.pawquest.presentation.theme.PawQuestTheme

/** Opens the account/settings sheet from any tab's avatar button; null hides the button. */
val LocalOpenSettings = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Shared top header for the four habit-first tabs. */
@Composable
fun TabHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val colors = PawQuestTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tr("PawQuest · Your cozy space").uppercase(),
                style = PawQuestTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = colors.textMuted)
            Text(tr(title), style = PawQuestTheme.typography.headlineLarge, color = colors.textPrimary,
                modifier = Modifier.semantics { heading() })
            Text(tr(subtitle), style = PawQuestTheme.typography.bodyMedium, color = colors.textSecondary)
        }
        LocalOpenSettings.current?.let { AvatarButton(it) }
    }
}

@Composable
fun AvatarButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PawQuestTheme.colors
    val label = tr("Open settings")
    Surface(onClick = onClick, shape = PawQuestTheme.shapes.lg, color = colors.surface,
        border = BorderStroke(1.5.dp, colors.borderLight),
        modifier = modifier.size(52.dp).semantics { contentDescription = label }) {
        Box(contentAlignment = Alignment.Center) {
            Icon(CartoonIcons.Profile, null, Modifier.size(32.dp), tint = Color.Unspecified)
        }
    }
}
