package com.pawquest.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pawquest.presentation.theme.PawQuestTheme

enum class CardVariant {
    Elevated,
    Outlined,
    SoftTint,
}

/**
 * Reusable rounded friendly card component for PawQuest 3D.
 * Supports tactile elevated style, clean outlines, and soft blue tinting.
 */
@Composable
fun PawQuestCard(
    modifier: Modifier = Modifier,
    variant: CardVariant = CardVariant.Elevated,
    shape: Shape = PawQuestTheme.shapes.card,
    onClick: (() -> Unit)? = null,
    elevation: Dp = if (variant == CardVariant.Elevated) 1.dp else 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = PawQuestTheme.colors
    val containerColor = when (variant) {
        CardVariant.Elevated -> colors.surface
        CardVariant.Outlined -> colors.surface
        CardVariant.SoftTint -> colors.surfaceVariant
    }

    val border: BorderStroke? = when (variant) {
        CardVariant.Elevated -> BorderStroke(1.dp, colors.borderLight)
        CardVariant.Outlined -> BorderStroke(1.dp, colors.borderLight)
        CardVariant.SoftTint -> null
    }

    Surface(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        ),
        shape = shape,
        color = containerColor,
        contentColor = colors.textPrimary,
        border = border,
        shadowElevation = elevation,
    ) {
        Box(
            modifier = Modifier.padding(16.dp),
            content = content,
        )
    }
}
