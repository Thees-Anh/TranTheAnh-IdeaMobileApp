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

/** Account, preferences and sign-out, opened from the avatar button on every tab. */
@Composable
fun SettingsSheetContent(
    account: UserAccount,
    petState: PetState,
    userCoins: Long,
    modifier: Modifier = Modifier,
    onLogout: (() -> Unit)? = null,
    logoutBusy: Boolean = false,
    logoutError: String? = null,
    onDeleteAccount: ((String) -> Unit)? = null,
) {
    val colors = PawQuestTheme.colors
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // The password is deliberately not saveable: it never enters saved instance state.
    var deletePassword by remember { mutableStateOf("") }
    val companionSettings = com.pawquest.presentation.platform.LocalCompanionSettings.current
    var hapticsEnabled by rememberSaveable { mutableStateOf(true) }
    val petXp = petState.snapshot?.pet?.progress?.totalXp ?: account.petXp
    val petName = petState.snapshot?.pet?.name ?: account.petName

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = tr("Settings"),
            style = PawQuestTheme.typography.headlineLarge,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        SectionHeader(
            title = "Account Details",
            subtitle = "Manage your authenticated PawQuest account",
            icon = PawQuestIcons.User,
        )
        AccountInfoCard(account = account, petName = petName, userCoins = userCoins, petXp = petXp)
        SectionHeader(
            title = "Preferences",
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
        if (logoutError != null) {
            Surface(color = colors.errorContainer, shape = PawQuestTheme.shapes.card, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = tr(logoutError),
                    style = PawQuestTheme.typography.bodyMedium,
                    color = colors.error,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        if (onLogout != null) PawQuestButton(
            text = "Sign Out",
            onClick = { showLogoutConfirm = true },
            variant = ButtonVariant.Outlined,
            leadingIcon = PawQuestIcons.Logout,
            loading = logoutBusy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        )
        if (onDeleteAccount != null) PawQuestButton(
            text = "Delete account",
            onClick = { deletePassword = ""; showDeleteConfirm = true },
            variant = ButtonVariant.Ghost,
            enabled = !logoutBusy,
            labelColor = colors.error,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        )
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

/** "Your progress" sheet: streak, rewarded sessions, Coins today, daily limits and the habit history. */
@Composable
fun ProgressSheetContent(modifier: Modifier = Modifier) {
    val colors = PawQuestTheme.colors
    val habit = com.pawquest.presentation.progress.LocalProgress.current?.state?.snapshot
    val rewardedSessionsCount = (habit?.rewardedToday ?: 0).coerceIn(0, 5)
    val dailyCoinsEarned = habit?.coinsToday ?: 0
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = tr("Your progress"),
                style = PawQuestTheme.typography.headlineLarge,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(tr("The little steps you have already taken."), style = PawQuestTheme.typography.bodyMedium,
                color = colors.textSecondary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProgressTile("${habit?.currentStreak ?: 0}", "Streak", Modifier.weight(1f))
            ProgressTile("$rewardedSessionsCount", "Rewarded sessions", Modifier.weight(1f))
            ProgressTile("$dailyCoinsEarned", "Coins today", Modifier.weight(1f))
        }
        Surface(color = colors.accentGoldContainer, shape = PawQuestTheme.shapes.card, modifier = Modifier.fillMaxWidth()) {
            Text(
                tr("Daily limits: up to 5 rewarded sessions and 250 Coins per UTC day. You can still finish Focus sessions after the limit."),
                style = PawQuestTheme.typography.labelMedium,
                color = colors.accentGoldDark,
                modifier = Modifier.padding(12.dp),
            )
        }
        DailyCoinsProgressCard(earnedCoins = dailyCoinsEarned, maxCoins = 250)
        RewardedSessionCountCard(completedCount = rewardedSessionsCount, maxSessions = 5)
        if (habit != null) WeeklyCompletionBarChart(
            dailyValues = habit.week.map { it.count }, todayIndex = habit.today.dayOfWeek.ordinal,
            weeklyTotal = habit.week.sumOf { it.count },
        )
        com.pawquest.presentation.progress.HabitDashboard()
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ProgressTile(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = PawQuestTheme.colors
    Surface(modifier = modifier, color = colors.surface, shape = PawQuestTheme.shapes.lg,
        border = BorderStroke(1.dp, colors.borderLight.copy(alpha = .15f))) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 6.dp).semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = PawQuestTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = colors.textPrimary)
            Text(tr(label), style = PawQuestTheme.typography.labelSmall, color = colors.textSecondary,
                textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

// -------------------------------------------------------------------------
// Subcomponents
// -------------------------------------------------------------------------

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
