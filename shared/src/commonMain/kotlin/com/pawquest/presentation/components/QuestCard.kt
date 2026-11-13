package com.pawquest.presentation.components

import com.pawquest.localization.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pawquest.presentation.icons.PawQuestIcons
import com.pawquest.presentation.theme.PawQuestTheme

enum class QuestStatus {
    Ready,
    InProgress,
    Claimable,
    Completed,
}

// Compatibility wrapper for existing design previews; all valid tiers live in domain.
typealias QuestRewards = com.pawquest.domain.model.QuestRewardPreview

fun calculateQuestRewards(durationMinutes: Int): QuestRewards =
    if (durationMinutes in 5..90) com.pawquest.domain.model.questRewardPreview(durationMinutes)
    else QuestRewards(0, 0)

/**
 * Polished, playful Quest Card for real-life tasks and focus sessions.
 * Displays title, duration, category tag, calculated reward indicators, and state action buttons.
 */
@Composable
fun QuestCard(
    title: String,
    durationMinutes: Int,
    modifier: Modifier = Modifier,
    category: String = "Focus",
    status: QuestStatus = QuestStatus.Ready,
    onStartClick: () -> Unit = {},
    onClaimClick: () -> Unit = {},
) {
    val colors = PawQuestTheme.colors
    val rewards = calculateQuestRewards(durationMinutes)

    val cardBorder = when (status) {
        QuestStatus.Claimable -> BorderStroke(2.dp, colors.accentGold)
        QuestStatus.InProgress -> BorderStroke(1.5.dp, colors.primary)
        else -> BorderStroke(1.dp, colors.borderLight)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PawQuestTheme.shapes.card,
        color = colors.surface,
        border = cardBorder,
        shadowElevation = if (status == QuestStatus.Claimable) 4.dp else 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header: Category Tag & Duration Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Category Chip
                Surface(
                    shape = PawQuestTheme.shapes.badge,
                    color = colors.secondary,
                ) {
                    Text(
                        text = tr(category),
                        style = PawQuestTheme.typography.labelSmall,
                        color = colors.primaryDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                // Duration Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = PawQuestIcons.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = colors.textSecondary,
                    )
                    Text(
                        text = tr("$durationMinutes min"),
                        style = PawQuestTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                }
            }

            // Title
            Text(
                text = tr(title),
                style = PawQuestTheme.typography.titleLarge,
                color = colors.textPrimary,
            )

            // Bottom row: Reward Indicator + Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RewardIndicator(coins = rewards.coins, xp = rewards.xp)

                when (status) {
                    QuestStatus.Ready -> {
                        PawQuestButton(
                            text = "Start",
                            onClick = onStartClick,
                            size = ButtonSize.Small,
                            variant = ButtonVariant.Primary,
                        )
                    }
                    QuestStatus.InProgress -> {
                        PawQuestButton(
                            text = "Focusing...",
                            onClick = {},
                            size = ButtonSize.Small,
                            variant = ButtonVariant.Secondary,
                            enabled = false,
                        )
                    }
                    QuestStatus.Claimable -> {
                        PawQuestButton(
                            text = "Claim Reward!",
                            onClick = onClaimClick,
                            size = ButtonSize.Small,
                            variant = ButtonVariant.Accent,
                            leadingIcon = PawQuestIcons.Sparkle,
                        )
                    }
                    QuestStatus.Completed -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = PawQuestIcons.Checkmark,
                                contentDescription = tr("Completed"),
                                modifier = Modifier.size(16.dp),
                                tint = colors.success,
                            )
                            Text(
                                text = tr("Done"),
                                style = PawQuestTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.success,
                            )
                        }
                    }
                }
            }
        }
    }
}
