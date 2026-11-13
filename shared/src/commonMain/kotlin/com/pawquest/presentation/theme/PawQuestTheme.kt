package com.pawquest.presentation.theme

import com.pawquest.resources.quicksand
import com.pawquest.resources.Res
import org.jetbrains.compose.resources.Font
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * PawQuestTheme entry point.
 * Provides custom PawQuest design tokens (colors, spacing, shapes, typography)
 * alongside standard Compose MaterialTheme.
 */
@Composable
fun PawQuestTheme(
    dark: Boolean = isAppInDarkTheme(),
    colors: PawQuestColors = if (dark) pawQuestDarkColors() else PawQuestColors(),
    spacing: PawQuestSpacing = PawQuestSpacing(),
    shapes: PawQuestShapes = PawQuestShapes(),
    typography: PawQuestTypography? = null,
    content: @Composable () -> Unit,
) {
    val rounded = androidx.compose.ui.text.font.FontFamily(
        org.jetbrains.compose.resources.Font(com.pawquest.resources.Res.font.quicksand, androidx.compose.ui.text.font.FontWeight.Medium),
        org.jetbrains.compose.resources.Font(com.pawquest.resources.Res.font.quicksand, androidx.compose.ui.text.font.FontWeight.Bold),
    )
    val resolvedTypography=typography ?: PawQuestTypography(family=rounded)
    // Start from the matching Material baseline so unlisted roles (inverse, containers) suit the mode.
    val materialColorScheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = colors.primaryDark,
        onPrimary = colors.surface,
        primaryContainer = colors.primaryContainer,
        onPrimaryContainer = colors.primaryDark,
        secondary = colors.secondary,
        onSecondary = colors.onSecondary,
        secondaryContainer = colors.secondary,
        onSecondaryContainer = colors.textPrimary,
        tertiary = colors.accentGold,
        onTertiary = colors.textPrimary,
        tertiaryContainer = colors.accentGoldContainer,
        onTertiaryContainer = colors.accentGoldDark,
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceVariant,
        onSurfaceVariant = colors.textSecondary,
        outline = colors.outlineStrong,
        outlineVariant = colors.borderFocus,
        error = colors.error,
        onError = colors.onError,
        errorContainer = colors.errorContainer,
        onErrorContainer = colors.error,
    )

    CompositionLocalProvider(
        LocalPawQuestColors provides colors,
        LocalPawQuestSpacing provides spacing,
        LocalPawQuestShapes provides shapes,
        LocalPawQuestTypography provides resolvedTypography,
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = resolvedTypography.toMaterialTypography(),
        ) {
            // Text outside a Surface would otherwise default to black, unreadable in dark mode.
            CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides colors.textPrimary, content = content)
        }
    }
}

/**
 * Accessor object for current PawQuest theme tokens.
 */
object PawQuestTheme {
    val colors: PawQuestColors
        @Composable
        @ReadOnlyComposable
        get() = LocalPawQuestColors.current

    val spacing: PawQuestSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalPawQuestSpacing.current

    val shapes: PawQuestShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalPawQuestShapes.current

    val typography: PawQuestTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalPawQuestTypography.current
}
