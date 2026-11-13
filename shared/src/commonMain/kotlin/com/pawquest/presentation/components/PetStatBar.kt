package com.pawquest.presentation.components

import com.pawquest.localization.tr
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pawquest.presentation.icons.PawQuestIcons
import com.pawquest.presentation.theme.PawQuestTheme

enum class PetStatType(val label: String) {
    Hunger("Hunger"),
    Happiness("Happiness"),
    Energy("Energy"),
}

/**
 * Cute, rounded stat progress bar for virtual pet needs (Hunger, Happiness, Energy).
 * Reminiscent of Talking Tom with playful animated fills and warning states for low stats.
 */
@Composable
fun PetStatBar(
    type: PetStatType,
    value: Int,
    modifier: Modifier = Modifier,
    maxValue: Int = 100,
    showLabel: Boolean = true,
) {
    val clampedValue = value.coerceIn(0, maxValue)
    val percentage = clampedValue.toFloat() / maxValue.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = percentage,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "${type.name}_progress",
    )

    val colors = PawQuestTheme.colors
    val (icon, barColor, containerColor) = when (type) {
        PetStatType.Hunger -> Triple(PawQuestIcons.FoodFish, colors.hunger, colors.hungerContainer)
        PetStatType.Happiness -> Triple(PawQuestIcons.Heart, colors.success, colors.successContainer)
        PetStatType.Energy -> Triple(PawQuestIcons.EnergyBolt, colors.energyDark, colors.energyContainer)
    }

    val isLow = clampedValue < 20

    Column(
        modifier = modifier.semantics {
            contentDescription = tr("${tr(type.label)}: $clampedValue out of $maxValue")
        },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showLabel) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = barColor,
                    )
                    Text(
                        text = tr(type.label),
                        style = PawQuestTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Text(
                    text = tr("$clampedValue%"),
                    style = PawQuestTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isLow) colors.error else colors.textSecondary,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }

        // Progress Bar Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(PawQuestTheme.shapes.statBar)
                .background(containerColor)
                .then(
                    if (isLow) {
                        Modifier.border(1.dp, colors.error, PawQuestTheme.shapes.statBar)
                    } else {
                        Modifier.border(0.5.dp, colors.borderLight, PawQuestTheme.shapes.statBar)
                    }
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(PawQuestTheme.shapes.statBar)
                    .background(barColor),
            )
        }
    }
}

/**
 * Compact HUD overview displaying all three pet stat bars (Hunger, Happiness, Energy).
 * Perfect for placing in the Pet Home screen overlay.
 */
@Composable
fun PetStatsOverview(
    hunger: Int,
    happiness: Int,
    energy: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = PawQuestTheme.shapes.card,
        color = PawQuestTheme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, PawQuestTheme.colors.borderLight),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PetStatBar(type = PetStatType.Hunger, value = hunger)
            PetStatBar(type = PetStatType.Happiness, value = happiness)
            PetStatBar(type = PetStatType.Energy, value = energy)
        }
    }
}
