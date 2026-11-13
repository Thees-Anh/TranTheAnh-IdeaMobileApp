package com.pawquest.presentation.navigation

import com.pawquest.localization.tr
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.semantics.liveRegion
import kotlinx.datetime.toLocalDateTime
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pawquest.domain.model.SectionPlaceholder
import com.pawquest.domain.model.UserAccount
import com.pawquest.presentation.components.PawQuestButton
import com.pawquest.presentation.components.ButtonVariant
import com.pawquest.presentation.components.CardVariant
import com.pawquest.presentation.components.CurrencyHeaderBar
import com.pawquest.presentation.components.DesignSystemShowcase
import com.pawquest.presentation.components.PawQuestCard
import com.pawquest.presentation.components.PawQuestEmpty
import com.pawquest.presentation.components.PetStatsOverview
import com.pawquest.presentation.components.QuestCard
import com.pawquest.presentation.components.QuestStatus
import com.pawquest.presentation.components.ScreenHeading
import com.pawquest.presentation.components.StateContent
import com.pawquest.presentation.icons.PawQuestIcons
import com.pawquest.presentation.state.UiState
import com.pawquest.presentation.theme.PawQuestTheme

@Composable
fun PawQuestScaffold(
    destination: Destination,
    state: UiState<SectionPlaceholder>,
    onNavigate: (Destination) -> Unit,
    onRetry: () -> Unit,
    petScene: (@Composable (Modifier) -> Unit)? = null,
    account: UserAccount? = null,
    onLogout: (() -> Unit)? = null,
    logoutBusy: Boolean = false,
    logoutError: String? = null,
    questContent: (@Composable (Modifier) -> Unit)? = null,
    shopContent: (@Composable (Modifier) -> Unit)? = null,
    inventoryContent: (@Composable (Modifier) -> Unit)? = null,
    profileContent: (@Composable (Modifier) -> Unit)? = null,
    onReconnect: (() -> Unit)? = null,
) {
    val colors = PawQuestTheme.colors

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.backgroundAlt,
        contentWindowInsets = WindowInsets.safeDrawing,
        // Emits nothing while online, so the content keeps its normal status-bar inset.
        topBar = { OfflineBanner(onReconnect) },
        bottomBar = {
            BoxWithConstraints {
                val showLabels = maxWidth >= 320.dp && LocalDensity.current.fontScale <= 1.3f
                Surface(
                    color = colors.surface,
                    shadowElevation = 2.dp,
                    border = BorderStroke(2.dp, colors.borderLight),
                ) {
                    NavigationBar(
                        containerColor = colors.surface,
                        contentColor = colors.textPrimary,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                        ),
                    ) {
                        Destination.entries.forEach { item ->
                            val isSelected = destination == item
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { onNavigate(item) },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = null,
                                        tint = androidx.compose.ui.graphics.Color.Unspecified,
                                        modifier = Modifier.size(30.dp),
                                    )
                                },
                                label = if (showLabels) {
                                    {
                                        Text(
                                            text = tr(item.label),
                                            style = PawQuestTheme.typography.labelSmall.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                } else null,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = colors.primaryDark,
                                    selectedTextColor = colors.primaryDark,
                                    indicatorColor = colors.primaryContainer,
                                    unselectedIconColor = colors.textMuted,
                                    unselectedTextColor = colors.textSecondary,
                                ),
                                modifier = Modifier.semantics { contentDescription = tr(item.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (destination == Destination.Quests && questContent != null) {
            questContent(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding))
            return@Scaffold
        }
        if (destination == Destination.Shop && shopContent != null) {
            shopContent(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding))
            return@Scaffold
        }
        if (destination == Destination.Inventory && inventoryContent != null) {
            inventoryContent(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding))
            return@Scaffold
        }
        if (destination == Destination.Profile && profileContent != null) {
            profileContent(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding))
            return@Scaffold
        }
        if (destination == Destination.PetHome) {
            if (petScene != null) {
                petScene(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding))
            } else {
                com.pawquest.presentation.home.PetHomeScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .consumeWindowInsets(padding),
                    petName = "Mochi",
                    showDebugControls = false,
                ) { previewModifier ->
                    androidx.compose.foundation.layout.Box(
                        modifier = previewModifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                shape = PawQuestTheme.shapes.pill,
                                color = colors.primaryContainer,
                                modifier = Modifier.size(72.dp),
                            ) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = PawQuestIcons.Paw,
                                        contentDescription = tr("3D cat preview"),
                                        modifier = Modifier.size(36.dp),
                                        tint = colors.primaryDark,
                                    )
                                }
                            }
                            Text(
                                text = tr("Cozy 3D Bedroom"),
                                style = PawQuestTheme.typography.titleMedium,
                                color = colors.primaryDark,
                            )
                            Text(
                                text = tr("3D Cat Preview (Active on Device)"),
                                style = PawQuestTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }
            return@Scaffold
        }
        StateContent(
            state = state,
            onRetry = onRetry,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) { placeholder ->
            // Top branding, heading & description
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = tr("PawQuest"),
                    style = PawQuestTheme.typography.labelLarge,
                    color = colors.primaryDark,
                )
                ScreenHeading(placeholder.title)
                Text(
                    text = tr(placeholder.message),
                    style = PawQuestTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            // Top Status Bar (Coins & XP)
            CurrencyHeaderBar(
                coins = 120,
                xp = 65,
                level = 2,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )

            // Section Specific Design System Content
            when (destination) {
                Destination.PetHome -> {
                    PetStatsOverview(
                        hunger = 85,
                        happiness = 92,
                        energy = 70,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PawQuestCard(
                        variant = CardVariant.SoftTint,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = tr("Your Cat is Feeling Great! 🐾"),
                                style = PawQuestTheme.typography.titleMedium,
                                color = colors.primaryDark,
                            )
                            Text(
                                text = tr("Keep your cat healthy and happy by feeding and playing after focus quests."),
                                style = PawQuestTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
                Destination.Quests -> {
                    var sampleQuestStatus by remember { mutableStateOf(QuestStatus.Ready) }
                    QuestCard(
                        title = "25-Minute Focus Session",
                        durationMinutes = 25,
                        category = "Productivity",
                        status = sampleQuestStatus,
                        onStartClick = { sampleQuestStatus = QuestStatus.InProgress },
                    )
                    QuestCard(
                        title = "Quick Review Quest",
                        durationMinutes = 10,
                        category = "Habit",
                        status = QuestStatus.Claimable,
                        onClaimClick = {},
                    )
                }
                Destination.Shop -> {
                    PawQuestEmpty(
                        title = "Shop Opening Soon",
                        message = placeholder.message,
                        icon = PawQuestIcons.Sparkle,
                        actionText = "Earn Coins in Quests",
                        onActionClick = { onNavigate(Destination.Quests) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Destination.Inventory -> {
                    PawQuestEmpty(
                        title = "Backpack Empty",
                        message = placeholder.message,
                        icon = PawQuestIcons.Trophy,
                        actionText = "Visit Shop",
                        onActionClick = { onNavigate(Destination.Shop) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Destination.Profile -> {
                    if (account != null && onLogout != null) {
                        PawQuestCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.Outlined) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(account.displayName, style = PawQuestTheme.typography.titleLarge)
                                account.email?.let { Text(tr(it)) }
                                Text(tr("Your cat: ${account.petName}"))
                                Text(tr("Coins: ${account.coins} · Pet XP: ${account.petXp}"))
                                logoutError?.let { Text(tr(it)) }
                                PawQuestButton("Sign out", onLogout, Modifier.fillMaxWidth(),
                                    variant = ButtonVariant.Outlined, loading = logoutBusy)
                            }
                        }
                    }
                    DesignSystemShowcase(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** Shown while screens display the last confirmed server data instead of live data. */
@Composable
private fun OfflineBanner(onReconnect: (() -> Unit)?) {
    val from = com.pawquest.data.offline.ConnectionStatus.offlineDataFrom ?: return
    val colors = PawQuestTheme.colors
    val local = from.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    val time = local.hour.toString().padStart(2, '0') + ":" + local.minute.toString().padStart(2, '0')
    Surface(color = colors.accentGoldContainer, modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .semantics(mergeDescendants = true) { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(tr("Offline · showing data from {0}".replace("{0}", time)), style = PawQuestTheme.typography.labelLarge,
                    color = colors.accentGoldDark)
                Text(tr("Changes need an internet connection."), style = PawQuestTheme.typography.labelMedium, color = colors.textSecondary)
            }
            if (onReconnect != null) androidx.compose.material3.TextButton(onClick = onReconnect) {
                Text(tr("Retry"), color = colors.primaryDark)
            }
        }
    }
}

