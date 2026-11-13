package com.pawquest.presentation.explore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pawquest.localization.tr
import com.pawquest.presentation.components.CurrencyBalances
import com.pawquest.presentation.components.TabHeader
import com.pawquest.presentation.icons.CartoonIcons
import com.pawquest.presentation.icons.PawQuestIcons
import com.pawquest.presentation.quests.QuestBackHandler
import com.pawquest.presentation.theme.PawQuestColors
import com.pawquest.presentation.theme.PawQuestTheme

/** Every reward activity lives here so the main tabs stay focused on today's quests. */
enum class ExploreCard(val title: String, val subtitle: String) {
    BISTRO("Paw Bistro", "Tasty meals for your pet"),
    COZY_SHOP("Cozy Shop", "Decorations & accessories"),
    MYSTERY_BOX("Mystery Box", "A little surprise gift"),
    LUCKY_WHEEL("Lucky Wheel", "Try your luck!"),
    EXCHANGE("Exchange", "Trade shards for gifts"),
    COLLECTION("Collection", "Your companion treasures"),
}

@Composable
fun ExploreScreen(
    coins: Long,
    openCard: ExploreCard?,
    onOpen: (ExploreCard) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    cardContent: @Composable (ExploreCard, Modifier) -> Unit,
) {
    QuestBackHandler(openCard != null, onBack)
    if (openCard != null) {
        Column(modifier.fillMaxSize()) {
            TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(tr("‹ Explore"), color = PawQuestTheme.colors.textPrimary,
                    style = PawQuestTheme.typography.labelLarge)
            }
            cardContent(openCard, Modifier.weight(1f).fillMaxWidth())
        }
        return
    }
    val colors = PawQuestTheme.colors
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        TabHeader("Explore", "Little games & cozy gifts.")
        Surface(shape = PawQuestTheme.shapes.xl, color = colors.accentGoldContainer,
            border = BorderStroke(1.dp, colors.accentGold.copy(alpha = .4f)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Your wallet").uppercase(), style = PawQuestTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.accentGoldDark)
                CurrencyBalances(coins, Modifier.fillMaxWidth())
            }
        }
        Text(tr("Shops & activities"), style = PawQuestTheme.typography.titleLarge, color = colors.textPrimary)
        ExploreCard.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { card -> ExploreTile(card, { onOpen(card) }, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ExploreTile(card: ExploreCard, onClick: () -> Unit, modifier: Modifier) {
    val colors = PawQuestTheme.colors
    val (icon, tint) = card.icon()
    Surface(onClick = onClick, shape = PawQuestTheme.shapes.xl, color = card.container(colors),
        border = BorderStroke(1.dp, colors.borderLight.copy(alpha = .08f)),
        modifier = modifier.heightIn(min = 140.dp).semantics { role = Role.Button }) {
        Box(Modifier.padding(16.dp)) {
            Column(Modifier.align(Alignment.TopStart).padding(end = 36.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr(card.title), style = PawQuestTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary)
                Text(tr(card.subtitle), style = PawQuestTheme.typography.labelSmall, color = colors.textSecondary)
            }
            Icon(icon, null, Modifier.align(Alignment.BottomEnd).size(52.dp), tint = tint)
        }
    }
}

private fun ExploreCard.container(c: PawQuestColors): Color = when (this) {
    ExploreCard.BISTRO -> c.hungerContainer
    ExploreCard.COZY_SHOP -> c.successContainer
    ExploreCard.MYSTERY_BOX -> c.petXpContainer
    ExploreCard.LUCKY_WHEEL -> c.primaryContainer
    ExploreCard.EXCHANGE -> c.energyContainer
    ExploreCard.COLLECTION -> c.accentGoldContainer
}

@Composable
private fun ExploreCard.icon(): Pair<ImageVector, Color> {
    val c = PawQuestTheme.colors
    return when (this) {
        ExploreCard.BISTRO -> CartoonIcons.Food to Color.Unspecified
        ExploreCard.COZY_SHOP -> CartoonIcons.Shop to Color.Unspecified
        ExploreCard.MYSTERY_BOX -> PawQuestIcons.Sparkle to c.petXpDark
        ExploreCard.LUCKY_WHEEL -> CartoonIcons.Ball to Color.Unspecified
        ExploreCard.EXCHANGE -> PawQuestIcons.Refresh to c.energyDark
        ExploreCard.COLLECTION -> CartoonIcons.Heart to Color.Unspecified
    }
}
