package com.pawquest.presentation.components

import com.pawquest.localization.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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

/**
 * Cute, tactile Coin Badge displaying the user's gold coin balance.
 * Uses warm yellow container (#FEF9C3) with gold accent border and coin icon.
 */
@Composable
fun CoinBadge(
    amount: Number,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = PawQuestTheme.colors
    Surface(
        modifier = modifier,
        shape = PawQuestTheme.shapes.pill,
        color = colors.accentGoldContainer,
        contentColor = colors.accentGoldDark,
        border = BorderStroke(1.dp, colors.accentGold),
        shadowElevation = 1.dp,
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = PawQuestIcons.Coin,
                contentDescription = tr("Coins"),
                modifier = Modifier.size(16.dp),
                tint = colors.accentGoldDark,
            )
            Text(
                text = tr(amount.toString()),
                style = PawQuestTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = colors.accentGoldDark,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * Cute Pet XP Badge displaying pet experience points and optional level indicator.
 * Uses soft purple container with vivid purple star icon.
 */
@Composable
fun PetXpBadge(
    xp: Number,
    modifier: Modifier = Modifier,
    level: Number? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = PawQuestTheme.colors
    Surface(
        modifier = modifier,
        shape = PawQuestTheme.shapes.pill,
        color = colors.petXpContainer,
        contentColor = colors.petXpDark,
        border = BorderStroke(1.dp, colors.petXp),
        shadowElevation = 1.dp,
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = PawQuestIcons.StarXp,
                contentDescription = tr("Pet XP"),
                modifier = Modifier.size(16.dp),
                tint = colors.petXp,
            )
            if (level != null) {
                Text(
                    text = tr("Lv.$level"),
                    style = PawQuestTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.petXpDark,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = tr("•"),
                    style = PawQuestTheme.typography.labelSmall,
                    color = colors.petXp,
                )
            }
            Text(
                text = tr("$xp XP"),
                style = PawQuestTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = colors.petXpDark,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * Compact reward chip indicating coins and XP gained from a quest or action.
 */
@Composable
fun RewardIndicator(
    coins: Int,
    xp: Int,
    modifier: Modifier = Modifier,
) {
    val colors = PawQuestTheme.colors
    Surface(
        modifier = modifier,
        shape = PawQuestTheme.shapes.pill,
        color = colors.secondary,
        border = BorderStroke(1.dp, colors.secondaryDark),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(
                    imageVector = PawQuestIcons.Coin,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = colors.accentGoldDark,
                )
                Text(
                    text = tr("+$coins"),
                    style = PawQuestTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.accentGoldDark,
                )
            }
            Text(
                text = tr("•"),
                style = PawQuestTheme.typography.labelSmall,
                color = colors.textMuted,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(
                    imageVector = PawQuestIcons.StarXp,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = colors.petXp,
                )
                Text(
                    text = tr("+$xp XP"),
                    style = PawQuestTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.petXpDark,
                )
            }
        }
    }
}

/**
 * Top bar currency header displaying Coins, Pet XP, and optional daily streak.
 */
@Composable
fun CurrencyHeaderBar(
    coins: Int,
    xp: Int,
    modifier: Modifier = Modifier,
    level: Int? = 1,
    onCoinsClick: (() -> Unit)? = null,
    onXpClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        PetXpBadge(xp = xp, level = level, onClick = onXpClick)
        Spacer(modifier = Modifier.width(8.dp))
        CoinBadge(amount = coins, onClick = onCoinsClick)
    }
}
