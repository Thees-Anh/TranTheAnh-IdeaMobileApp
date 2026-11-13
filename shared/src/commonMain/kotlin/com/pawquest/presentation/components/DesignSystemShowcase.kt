package com.pawquest.presentation.components

import com.pawquest.localization.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pawquest.presentation.icons.PawQuestIcons
import com.pawquest.presentation.theme.PawQuestTheme

/**
 * Interactive mobile showcase displaying all components of the PawQuest 3D design system.
 * Designed specifically for portrait mobile screens (360x640, 360x780, 360x800, down to 320dp width).
 */
@Composable
fun DesignSystemShowcase(
    modifier: Modifier = Modifier,
) {
    var coins by remember { mutableStateOf(145) }
    var xp by remember { mutableStateOf(85) }
    var hunger by remember { mutableStateOf(75) }
    var happiness by remember { mutableStateOf(90) }
    var energy by remember { mutableStateOf(60) }
    var quest1Status by remember { mutableStateOf(QuestStatus.Ready) }
    var quest2Status by remember { mutableStateOf(QuestStatus.Claimable) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // 1. Currency & Level Status Header
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = tr("Currency & Status Badges"),
                style = PawQuestTheme.typography.titleMedium,
                color = PawQuestTheme.colors.textSecondary,
            )
            CurrencyHeaderBar(
                coins = coins,
                xp = xp,
                level = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 2. Pet Needs & Stat Bars (Talking Tom Inspiration)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = tr("Pet Stats (Hunger, Happiness, Energy)"),
                style = PawQuestTheme.typography.titleMedium,
                color = PawQuestTheme.colors.textSecondary,
            )
            PetStatsOverview(
                hunger = hunger,
                happiness = happiness,
                energy = energy,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 3. Quests & Reward Indicators
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = tr("Quest Cards & Tier Rewards"),
                style = PawQuestTheme.typography.titleMedium,
                color = PawQuestTheme.colors.textSecondary,
            )
            QuestCard(
                title = "Deep Focus: Study Kotlin",
                durationMinutes = 25,
                category = "Productivity",
                status = quest1Status,
                onStartClick = { quest1Status = QuestStatus.InProgress },
            )
            QuestCard(
                title = "Afternoon Reading Session",
                durationMinutes = 45,
                category = "Learning",
                status = quest2Status,
                onClaimClick = {
                    coins += 50
                    xp += 30
                    quest2Status = QuestStatus.Completed
                },
            )
        }

        // 4. Tactile Buttons
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = tr("Tactile Buttons"),
                style = PawQuestTheme.typography.titleMedium,
                color = PawQuestTheme.colors.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PawQuestButton(
                    text = "Primary",
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    variant = ButtonVariant.Primary,
                    leadingIcon = PawQuestIcons.Paw,
                )
                PawQuestButton(
                    text = "Secondary",
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    variant = ButtonVariant.Secondary,
                )
            }
            PawQuestButton(
                text = "Claim Special Reward",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.Accent,
                leadingIcon = PawQuestIcons.Sparkle,
            )
        }

        // 5. Reusable Cards
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = tr("Card Containers"),
                style = PawQuestTheme.typography.titleMedium,
                color = PawQuestTheme.colors.textSecondary,
            )
            PawQuestCard(
                variant = CardVariant.SoftTint,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = tr("Soft Tinted Card"),
                        style = PawQuestTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = PawQuestTheme.colors.primaryDark,
                    )
                    Text(
                        text = tr("Used for helpful pet tips, hints, or inventory details."),
                        style = PawQuestTheme.typography.bodyMedium,
                        color = PawQuestTheme.colors.textSecondary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
