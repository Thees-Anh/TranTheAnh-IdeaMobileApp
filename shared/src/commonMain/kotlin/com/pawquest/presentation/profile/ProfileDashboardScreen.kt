package com.pawquest.presentation.profile

import com.pawquest.localization.tr
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pawquest.domain.model.PetCareBalance
import com.pawquest.domain.model.PetProgress
import com.pawquest.domain.model.UserAccount
import com.pawquest.presentation.components.ButtonSize
import com.pawquest.presentation.components.ButtonVariant
import com.pawquest.presentation.components.CardVariant
import com.pawquest.presentation.components.CoinBadge
import com.pawquest.presentation.components.PawQuestButton
import com.pawquest.presentation.components.PawQuestCard
import com.pawquest.presentation.components.PetXpBadge
import com.pawquest.presentation.focus.FocusState
import com.pawquest.presentation.icons.PawQuestIcons
import com.pawquest.presentation.pet.PetState
import com.pawquest.presentation.quests.QuestManagementState
import com.pawquest.presentation.theme.PawQuestColors
import com.pawquest.presentation.theme.PawQuestTheme

enum class ProfileViewTab {
    ALL,
    DASHBOARD,
    PROFILE,
}

/**
 * PawQuest Portrait Progress Dashboard and Profile Screen.
 *
 * Implements:
 * - Single-column portrait scrollable layout with compact cards and responsive weekly bar chart
 * - Dashboard: completed quests today/week, daily reward progress (0-250 Coins),
 *   rewarded session count (0-5), pet level & XP, weekly completion statistics
 * - Profile: username, pet name, account info, basic settings, secure logout
 * - Real repository and ViewModel data integration
 * - Navigation smoke test compatibility: header text "Your journey, one little quest at a time."
 */
@Composable
fun ProfileDashboardScreen(
    account: UserAccount,
    questState: QuestManagementState,
    petState: PetState,
    focusState: FocusState,
    userCoins: Long,
    modifier: Modifier = Modifier,
    onLogout: (() -> Unit)? = null,
    logoutBusy: Boolean = false,
    logoutError: String? = null,
    onDeleteAccount: ((String) -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
) {
    val colors = PawQuestTheme.colors
    var selectedTab by rememberSaveable { mutableStateOf(ProfileViewTab.ALL) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // The password is deliberately not saveable: it never enters saved instance state.
    var deletePassword by remember { mutableStateOf("") }

    // Settings local toggles
    val companionSettings=com.pawquest.presentation.platform.LocalCompanionSettings.current
    var hapticsEnabled by rememberSaveable { mutableStateOf(true) }

    // Derived statistics from real state
    val habit=com.pawquest.presentation.progress.LocalProgress.current?.state?.snapshot
    val completedToday = habit?.todayCount ?: 0
    val rewardedSessionsCount = (habit?.rewardedToday ?: 0).coerceIn(0,5)
    val dailyCoinsEarned = habit?.coinsToday ?: 0
    val maxDailyCoins = 250

    val completedThisWeek = habit?.week?.sumOf {it.count} ?: 0

    // Pet progress calculation
    val petXp = petState.snapshot?.pet?.progress?.totalXp ?: account.petXp
    val petProgress = remember(petXp) { PetProgress(petXp) }
    val petName = petState.snapshot?.pet?.name ?: account.petName

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Top Header: Title, Navigation smoke subtitle, Balance Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = tr("Profile & Progress"),
                    style = PawQuestTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = tr("Your journey, one little quest at a time."),
                    style = PawQuestTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                com.pawquest.presentation.components.CurrencyBalances(userCoins,Modifier.widthIn(max=230.dp))
            }
        }

        // 2. View Mode Selector Chips (All / Dashboard / Profile)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ViewFilterChip(
                label = "Overview",
                icon = PawQuestIcons.Trophy,
                selected = selectedTab == ProfileViewTab.ALL,
                onClick = { selectedTab = ProfileViewTab.ALL },
                modifier = Modifier.weight(1f),
            )
            ViewFilterChip(
                label = "Dashboard",
                icon = PawQuestIcons.ChartBar,
                selected = selectedTab == ProfileViewTab.DASHBOARD,
                onClick = { selectedTab = ProfileViewTab.DASHBOARD },
                modifier = Modifier.weight(1f),
            )
            ViewFilterChip(
                label = "Profile",
                icon = PawQuestIcons.User,
                selected = selectedTab == ProfileViewTab.PROFILE,
                onClick = { selectedTab = ProfileViewTab.PROFILE },
                modifier = Modifier.weight(1f),
            )
        }

        if(selectedTab!=ProfileViewTab.PROFILE) com.pawquest.presentation.progress.HabitDashboard()
        // 3. User Identity Hero Card (Shown in All and Profile)
        if (selectedTab != ProfileViewTab.DASHBOARD) {
            UserHeroCard(
                displayName = account.displayName,
                email = account.email,
                petName = petName,
                petLevel = petProgress.level.toInt(),
                coins = userCoins,
                totalXp = petXp,
            )
        }

        // 4. Progress Dashboard Section (Shown in All and Dashboard)
        if (selectedTab != ProfileViewTab.PROFILE) {
            SectionHeader(
                title = "Progress Dashboard",
                subtitle = "Track your daily focus and pet growth",
                icon = PawQuestIcons.ChartBar,
            )

            // Compact 2-column KPI Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CompactKpiCard(
                    title = "Completed Today",
                    value = "$completedToday",
                    unit = "quests",
                    subtext = "Goal: 5 / day",
                    icon = PawQuestIcons.CheckCircle,
                    tint = colors.primaryDark,
                    containerColor = colors.primaryContainer,
                    modifier = Modifier.weight(1f),
                )
                CompactKpiCard(
                    title = "Completed This Week",
                    value = "$completedThisWeek",
                    unit = "quests",
                    subtext = "Weekly active sessions",
                    icon = PawQuestIcons.Calendar,
                    tint = colors.petXpDark,
                    containerColor = colors.petXpContainer,
                    modifier = Modifier.weight(1f),
                )
            }

            // Daily Reward Progress Card (0-250 Coins)
            DailyCoinsProgressCard(
                earnedCoins = dailyCoinsEarned,
                maxCoins = maxDailyCoins,
            )

            // Rewarded Quest Count Card (0-5 sessions)
            RewardedSessionCountCard(
                completedCount = rewardedSessionsCount,
                maxSessions = 5,
            )

            // Pet Level & XP Progress Card
            PetLevelProgressCard(
                petName = petName,
                progress = petProgress,
            )

            // Responsive Weekly Completion Bar Chart
            if(habit!=null) WeeklyCompletionBarChart(
                dailyValues = habit.week.map {it.count},todayIndex=habit.today.dayOfWeek.ordinal,
                weeklyTotal = completedThisWeek,
            )
        }

        // 5. Profile & Settings Section (Shown in All and Profile)
        if (selectedTab != ProfileViewTab.DASHBOARD) {
            SectionHeader(
                title = "Account Details",
                subtitle = "Manage your authenticated PawQuest account",
                icon = PawQuestIcons.User,
            )

            AccountInfoCard(
                account = account,
                petName = petName,
                userCoins = userCoins,
                petXp = petXp,
            )

            SectionHeader(
                title = "Settings",
                subtitle = "Preferences and application controls",
                icon = PawQuestIcons.Settings,
            )

            CompanionSettingsCard()
            com.pawquest.localization.LanguageSelector()
            com.pawquest.presentation.theme.AppearanceSelector()
            SettingsCard(
                soundEnabled = companionSettings?.state?.soundEnabled ?: true,
                onSoundToggle = { companionSettings?.setSoundEnabled?.invoke(it) },
                hapticsEnabled = hapticsEnabled,
                onHapticsToggle = { hapticsEnabled = it },
            )

            // Logout Error Notice
            if (logoutError != null) {
                Surface(
                    color = colors.errorContainer,
                    shape = PawQuestTheme.shapes.card,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = tr(logoutError),
                        style = PawQuestTheme.typography.bodyMedium,
                        color = colors.error,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            // Logout Action Button
            PawQuestButton(
                text = "Sign Out",
                onClick = { showLogoutConfirm = true },
                variant = ButtonVariant.Outlined,
                leadingIcon = PawQuestIcons.Logout,
                loading = logoutBusy,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            )

            if (onDeleteAccount != null) {
                PawQuestButton(
                    text = "Delete account",
                    onClick = { deletePassword = ""; showDeleteConfirm = true },
                    variant = ButtonVariant.Ghost,
                    enabled = !logoutBusy,
                    labelColor = colors.error,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showDeleteConfirm && onDeleteAccount != null) {
        AlertDialog(
            onDismissRequest = { if (!logoutBusy) { showDeleteConfirm = false; deletePassword = "" } },
            title = {
                Text(
                    text = tr("Delete your account?"),
                    style = PawQuestTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = tr("This permanently deletes your sign-in and erases your quests. Your pets, Coins, items and progress will be lost. This cannot be undone."),
                        style = PawQuestTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    OutlinedTextField(
                        value = deletePassword,
                        onValueChange = { deletePassword = it },
                        label = { Text(tr("Enter your password to confirm")) },
                        singleLine = true,
                        enabled = !logoutBusy,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (logoutError != null) {
                        Text(text = tr(logoutError), style = PawQuestTheme.typography.bodyMedium, color = colors.error)
                    }
                }
            },
            confirmButton = {
                PawQuestButton(
                    text = "Delete permanently",
                    onClick = { onDeleteAccount(deletePassword) },
                    variant = ButtonVariant.Primary,
                    size = ButtonSize.Small,
                    enabled = deletePassword.isNotEmpty(),
                    loading = logoutBusy,
                )
            },
            dismissButton = {
                PawQuestButton(
                    text = "Cancel",
                    onClick = { showDeleteConfirm = false; deletePassword = "" },
                    variant = ButtonVariant.Ghost,
                    size = ButtonSize.Small,
                    enabled = !logoutBusy,
                )
            },
        )
    }

    // Logout Confirmation Dialog
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { if (!logoutBusy) showLogoutConfirm = false },
            title = {
                Text(
                    text = tr("Sign out?"),
                    style = PawQuestTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                )
            },
            text = {
                Text(
                    text = tr("You will be signed out on this device. Your pet progress, coins, and quests remain safely stored."),
                    style = PawQuestTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                PawQuestButton(
                    text = "Confirm Sign Out",
                    onClick = {
                        showLogoutConfirm = false
                        onLogout?.invoke()
                    },
                    variant = ButtonVariant.Primary,
                    size = ButtonSize.Small,
                    loading = logoutBusy,
                )
            },
            dismissButton = {
                PawQuestButton(
                    text = "Cancel",
                    onClick = { showLogoutConfirm = false },
                    variant = ButtonVariant.Ghost,
                    size = ButtonSize.Small,
                    enabled = !logoutBusy,
                )
            },
        )
    }
}

// -------------------------------------------------------------------------
// Subcomponents
// -------------------------------------------------------------------------

@Composable
private fun ViewFilterChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PawQuestTheme.colors
    Surface(
        onClick = onClick,
        shape = PawQuestTheme.shapes.pill,
        color = if (selected) colors.primaryContainer else colors.surface,
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.borderLight),
        shadowElevation = if (selected) 2.dp else 0.dp,
        modifier = modifier.height(48.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) colors.primaryDark else colors.textMuted,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = tr(label),
                style = PawQuestTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) colors.primaryDark else colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val colors = PawQuestTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = colors.primaryContainer,
            modifier = Modifier.size(34.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.primaryDark,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column {
            Text(
                text = tr(title),
                style = PawQuestTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = tr(subtitle),
                style = PawQuestTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun UserHeroCard(
    displayName: String,
    email: String?,
    petName: String,
    petLevel: Int,
    coins: Long,
    totalXp: Long,
    modifier: Modifier = Modifier,
) {
    val colors = PawQuestTheme.colors
    PawQuestCard(
        modifier = modifier.fillMaxWidth(),
        variant = CardVariant.SoftTint,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Avatar Circle
            Surface(
                shape = CircleShape,
                color = colors.primary,
                modifier = Modifier.size(56.dp),
                border = BorderStroke(2.dp, colors.primaryDark),
                shadowElevation = 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = tr(displayName.take(1).uppercase()),
                        style = PawQuestTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = colors.onPrimary,
                    )
                }
            }

            // Info Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = displayName,
                        style = PawQuestTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (email != null) {
                    Text(
                        text = email,
                        style = PawQuestTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Surface(
                        shape = PawQuestTheme.shapes.pill,
                        color = colors.petXpContainer,
                        modifier = Modifier.padding(0.dp),
                    ) {
                        Text(
                            text = tr("🐾 $petName · Lvl $petLevel"),
                            style = PawQuestTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.petXpDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    Surface(
                        shape = PawQuestTheme.shapes.pill,
                        color = colors.accentGoldContainer,
                        modifier = Modifier.padding(0.dp),
                    ) {
                        Text(
                            text = tr("🪙 $coins"),
                            style = PawQuestTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.accentGoldDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactKpiCard(
    title: String,
    value: String,
    unit: String,
    subtext: String,
    icon: ImageVector,
    tint: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = PawQuestTheme.colors
    PawQuestCard(
        modifier = modifier,
        variant = CardVariant.Elevated,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tr(title),
                    style = PawQuestTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = CircleShape,
                    color = containerColor,
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = tr(value),
                    style = PawQuestTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = colors.textPrimary,
                )
                Text(
                    text = tr(unit),
                    style = PawQuestTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }

            Text(
                text = tr(subtext),
                style = PawQuestTheme.typography.labelSmall,
                color = colors.textMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DailyCoinsProgressCard(
    earnedCoins: Int,
    maxCoins: Int,
    modifier: Modifier = Modifier,
) {
    val colors = PawQuestTheme.colors
    val progress = (earnedCoins.toFloat() / maxCoins).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 600),
    )

    PawQuestCard(
        modifier = modifier.fillMaxWidth(),
        variant = CardVariant.Elevated,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = PawQuestIcons.Coin,
                        contentDescription = null,
                        tint = colors.accentGoldDark,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = tr("Daily Reward Progress"),
                        style = PawQuestTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                    )
                }
                Text(
                    text = tr("$earnedCoins / $maxCoins Coins"),
                    style = PawQuestTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = colors.accentGoldDark,
                )
            }

            // Progress bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(PawQuestTheme.shapes.pill),
                color = colors.accentGoldDark,
                trackColor = colors.accentGoldContainer,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = tr("${(progress * 100).toInt()}% of daily reward cap"),
                    style = PawQuestTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
                Text(
                    text = tr("${maxCoins - earnedCoins} Coins remaining"),
                    style = PawQuestTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun RewardedSessionCountCard(
    completedCount: Int,
    maxSessions: Int = 5,
    modifier: Modifier = Modifier,
) {
    val colors = PawQuestTheme.colors

    PawQuestCard(
        modifier = modifier.fillMaxWidth(),
        variant = CardVariant.Elevated,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = PawQuestIcons.Timer,
                        contentDescription = null,
                        tint = colors.primaryDark,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = tr("Rewarded Focus Sessions"),
                        style = PawQuestTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                    )
                }
                Text(
                    text = tr("$completedCount / $maxSessions Today"),
                    style = PawQuestTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = colors.primaryDark,
                )
            }

            // 5 Segmented step indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (i in 1..maxSessions) {
                    val isDone = i <= completedCount
                    Surface(
                        shape = PawQuestTheme.shapes.pill,
                        color = if (isDone) colors.primaryDark else colors.primaryContainer,
                        border = BorderStroke(1.dp, if (isDone) colors.primaryDark else colors.secondaryDark),
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isDone) {
                                Icon(
                                    imageVector = PawQuestIcons.Checkmark,
                                    contentDescription = tr("Session $i completed"),
                                    tint = colors.surface,
                                    modifier = Modifier.size(14.dp),
                                )
                            } else {
                                Text(
                                    text = tr("$i"),
                                    style = PawQuestTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = tr(if (completedCount >= maxSessions) {
                    "Daily limit reached (5/5). Superb focus today! 🌟"
                } else {
                    "Complete ${maxSessions - completedCount} more focus session(s) to hit your daily goal."
                }),
                style = PawQuestTheme.typography.labelSmall,
                color = if (completedCount >= maxSessions) colors.successDark else colors.textSecondary,
            )
        }
    }
}

@Composable
private fun PetLevelProgressCard(
    petName: String,
    progress: PetProgress,
    modifier: Modifier = Modifier,
) {
    val colors = PawQuestTheme.colors
    val progressFraction = (progress.xpInLevel.toFloat() / PetCareBalance.xpPerLevel).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 600),
    )

    PawQuestCard(
        modifier = modifier.fillMaxWidth(),
        variant = CardVariant.Elevated,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = PawQuestIcons.StarXp,
                        contentDescription = null,
                        tint = colors.petXp,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = tr("$petName's Level & XP"),
                        style = PawQuestTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                    )
                }
                Surface(
                    shape = PawQuestTheme.shapes.pill,
                    color = colors.petXpContainer,
                ) {
                    Text(
                        text = tr("Level ${progress.level}"),
                        style = PawQuestTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = colors.petXpDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }

            // Progress bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(PawQuestTheme.shapes.pill),
                color = colors.petXp,
                trackColor = colors.petXpContainer,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = tr("${progress.xpInLevel} / ${PetCareBalance.xpPerLevel} XP in Level ${progress.level}"),
                    style = PawQuestTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
                Text(
                    text = tr("${progress.xpToNextLevel} XP to Level ${progress.level + 1}"),
                    style = PawQuestTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.petXpDark,
                )
            }
        }
    }
}

@Composable
private fun WeeklyCompletionBarChart(
    dailyValues: List<Int>,todayIndex: Int,
    weeklyTotal: Int,
    modifier: Modifier = Modifier,
) {
    val colors = PawQuestTheme.colors
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    PawQuestCard(
        modifier = modifier.fillMaxWidth(),
        variant = CardVariant.Elevated,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = PawQuestIcons.Calendar,
                        contentDescription = null,
                        tint = colors.primaryDark,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = tr("Weekly Quest Activity"),
                        style = PawQuestTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                    )
                }
                Surface(
                    shape = PawQuestTheme.shapes.pill,
                    color = colors.primaryContainer,
                ) {
                    Text(
                        text = tr("$weeklyTotal this week"),
                        style = PawQuestTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.primaryDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }

            // Responsive 7-Column Bar Chart
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                days.forEachIndexed { index, day ->
                    val value = dailyValues.getOrElse(index) { 0 }
                    val isToday = index == todayIndex
                    val maxTarget = 5f
                    val fraction = (value / maxTarget).coerceIn(0.12f, 1f)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        // Count label above bar
                        Text(
                            text = tr("$value"),
                            style = PawQuestTheme.typography.labelSmall.copy(
                                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Bold
                            ),
                            color = if (isToday) colors.primaryDark else colors.textSecondary,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )

                        // Vertical Bar Container
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .weight(1f, fill = false)
                                .fillMaxHeight(fraction)
                                .clip(PawQuestTheme.shapes.pill)
                                .background(
                                    if (isToday) colors.primaryDark else colors.primaryContainer
                                )
                                .then(
                                    if (isToday) {
                                        Modifier.border(1.5.dp, colors.primary, PawQuestTheme.shapes.pill)
                                    } else Modifier
                                ),
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Day label below bar
                        Text(
                            text = tr(day),
                            style = PawQuestTheme.typography.labelSmall.copy(
                                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium
                            ),
                            color = if (isToday) colors.primaryDark else colors.textSecondary,
                        )
                    }
                }
            }

            Text(
                text = tr("Target: 5 focus sessions per day. Blue bar indicates active sessions."),
                style = PawQuestTheme.typography.labelSmall,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
private fun AccountInfoCard(
    account: UserAccount,
    petName: String,
    userCoins: Long,
    petXp: Long,
    modifier: Modifier = Modifier,
) {
    val colors = PawQuestTheme.colors
    PawQuestCard(
        modifier = modifier.fillMaxWidth(),
        variant = CardVariant.Elevated,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            InfoRow(label = "Username", value = account.displayName, userContent = true)
            InfoRow(label = "Pet Name", value = petName, userContent = true)
            InfoRow(label = "Account Email", value = account.email ?: "Guest (No email)")
            InfoRow(label = "User ID", value = account.userId.take(12) + "…", mono = true)
            InfoRow(label = "Total Coins", value = "$userCoins 🪙")
            InfoRow(label = "Lifetime XP", value = "$petXp XP")
            InfoRow(label = "Status", value = "Active Explorer ✓", highlight = true)
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    mono: Boolean = false,
    highlight: Boolean = false,
    userContent: Boolean = false,
) {
    val colors = PawQuestTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = tr(label),
            style = PawQuestTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Text(
            text = if (userContent) value else tr(value),
            style = PawQuestTheme.typography.titleMedium.copy(
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.SemiBold
            ),
            color = if (highlight) colors.successDark else colors.textPrimary,
        )
    }
}

@Composable
private fun SettingsCard(
    soundEnabled: Boolean,
    onSoundToggle: (Boolean) -> Unit,
    hapticsEnabled: Boolean,
    onHapticsToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PawQuestTheme.colors
    PawQuestCard(
        modifier = modifier.fillMaxWidth(),
        variant = CardVariant.Elevated,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingSwitchRow(
                icon = PawQuestIcons.Volume,
                title = "Sound Effects",
                subtitle = "Level-up celebrations. Respects Silent mode and Do Not Disturb.",
                checked = soundEnabled,
                onCheckedChange = onSoundToggle,
            )
            SettingSwitchRow(
                icon = PawQuestIcons.Sparkle,
                title = "Haptic Vibration",
                subtitle = "Tactile feedback when tapping controls and earning coins",
                checked = hapticsEnabled,
                onCheckedChange = onHapticsToggle,
            )

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tr("Application Version"),
                    style = PawQuestTheme.typography.labelSmall,
                    color = colors.textMuted,
                )
                Text(
                    text = tr("PawQuest ? Made for little moments"),
                    style = PawQuestTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = PawQuestTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) colors.primaryDark else colors.textMuted,
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    text = tr(title),
                    style = PawQuestTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                )
                Text(
                    text = tr(subtitle),
                    style = PawQuestTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.surface,
                checkedTrackColor = colors.primaryDark,
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.borderLight,
            ),
        )
    }
}
