package com.pawquest.presentation.components

import com.pawquest.localization.tr
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pawquest.presentation.icons.PawQuestIcons
import com.pawquest.presentation.state.UiState
import com.pawquest.presentation.theme.PawQuestTheme

/**
 * Cute, playful loading indicator featuring a pulsing paw print and animated progress.
 */
@Composable
fun PawQuestLoading(
    modifier: Modifier = Modifier,
    message: String = "Waking up your pet...",
) {
    val colors = PawQuestTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "loading_transition")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "paw_pulse",
    )

    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(72.dp),
        ) {
            Surface(
                modifier = Modifier
                    .size(64.dp)
                    .scale(scale),
                shape = PawQuestTheme.shapes.pill,
                color = colors.primaryContainer,
                border = BorderStroke(1.5.dp, colors.borderFocus),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = PawQuestIcons.Paw,
                        contentDescription = tr("Loading"),
                        tint = colors.primaryDark,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
        CircularProgressIndicator(
            color = colors.primary,
            strokeWidth = 3.dp,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = tr(message),
            style = PawQuestTheme.typography.titleMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Friendly empty state component for empty tabs, lists, or inventories.
 */
@Composable
fun PawQuestEmpty(
    message: String,
    modifier: Modifier = Modifier,
    title: String = "Nothing Here Yet",
    icon: ImageVector = PawQuestIcons.Paw,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    val colors = PawQuestTheme.colors

    Surface(
        modifier = modifier.padding(16.dp),
        shape = PawQuestTheme.shapes.card,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.borderLight),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = PawQuestTheme.shapes.pill,
                color = colors.secondary,
                border = BorderStroke(1.dp, colors.secondaryDark),
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.primaryDark,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Text(
                text = tr(title),
                style = PawQuestTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = tr(message),
                style = PawQuestTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            if (actionText != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(4.dp))
                PawQuestButton(
                    text = actionText,
                    onClick = onActionClick,
                    variant = ButtonVariant.Primary,
                    size = ButtonSize.Small,
                )
            }
        }
    }
}

/**
 * Friendly error state component with clean explanation and prominent retry action.
 */
@Composable
fun PawQuestError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Oops! Something Went Wrong",
) {
    val colors = PawQuestTheme.colors

    Surface(
        modifier = modifier.padding(16.dp),
        shape = PawQuestTheme.shapes.card,
        color = colors.surface,
        border = BorderStroke(1.5.dp, colors.errorContainer),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = PawQuestTheme.shapes.pill,
                color = colors.errorContainer,
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = PawQuestIcons.Warning,
                        contentDescription = tr("Error"),
                        tint = colors.error,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Text(
                text = tr(title),
                style = PawQuestTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = tr(message),
                style = PawQuestTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            PawQuestButton(
                text = "Try again",
                onClick = onRetry,
                variant = ButtonVariant.Primary,
                leadingIcon = PawQuestIcons.Refresh,
            )
        }
    }
}

/**
 * Reusable container that handles UiState branches (Loading, Empty, Error, Content)
 * with scrolling support and full safe layout padding.
 */
@Composable
fun <T> StateContent(
    state: UiState<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            UiState.Loading -> PawQuestLoading()
            is UiState.Empty -> PawQuestEmpty(message = state.message)
            is UiState.Error -> PawQuestError(message = state.message, onRetry = onRetry)
            is UiState.Content -> content(state.value)
        }
    }
}

@Composable
fun ScreenHeading(text: String) {
    Text(
        text = tr(text),
        style = PawQuestTheme.typography.headlineLarge,
        color = PawQuestTheme.colors.textPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
}
