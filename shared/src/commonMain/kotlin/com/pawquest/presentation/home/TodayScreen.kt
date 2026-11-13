package com.pawquest.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pawquest.domain.model.PetAnimationState
import com.pawquest.domain.model.PetStats
import com.pawquest.domain.model.QuestRepeatType
import com.pawquest.localization.tr
import com.pawquest.presentation.components.AvatarButton
import com.pawquest.presentation.components.ButtonVariant
import com.pawquest.presentation.components.CoinBadge
import com.pawquest.presentation.components.LocalOpenSettings
import com.pawquest.presentation.components.PawQuestButton
import com.pawquest.presentation.icons.CartoonIcons
import com.pawquest.presentation.icons.PawQuestIcons
import com.pawquest.presentation.pet.LocalPetController
import com.pawquest.presentation.progress.LocalProgress
import com.pawquest.presentation.quests.QuestManagementState
import com.pawquest.presentation.quests.QuestUiItem
import com.pawquest.presentation.quests.displayCategory
import com.pawquest.presentation.theme.PawQuestTheme
import com.pawquest.resources.Res
import com.pawquest.resources.pet_capybara
import com.pawquest.resources.pet_mochi
import com.pawquest.resources.pet_puppy
import com.pawquest.resources.pet_rabbit
import org.jetbrains.compose.resources.painterResource

private const val DAILY_COIN_LIMIT = 250

/**
 * Habit-first start tab: today's progress and the next quest lead; the companion card only reflects
 * how the user is doing and links to the Pet Room.
 */
@Composable
fun TodayScreen(
    displayName: String,
    coins: Long,
    quests: QuestManagementState,
    onStartQuest: (String) -> Unit,
    onSeeAllQuests: () -> Unit,
    onCreateQuest: () -> Unit,
    onVisitRoom: () -> Unit,
    onOpenProgress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PawQuestTheme.colors
    val habit = LocalProgress.current?.state?.snapshot
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Greeting
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tr("PawQuest · Your cozy space").uppercase(), style = PawQuestTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.textMuted)
                Text(tr("Hello, $displayName!"), style = PawQuestTheme.typography.headlineLarge, color = colors.textPrimary,
                    modifier = Modifier.semantics { heading() })
                Text(tr("One small step is still progress."), style = PawQuestTheme.typography.bodyMedium, color = colors.textSecondary)
            }
            LocalOpenSettings.current?.let { AvatarButton(it) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = PawQuestTheme.shapes.pill, color = colors.hungerContainer) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(CartoonIcons.Fire, null, Modifier.size(18.dp), tint = Color.Unspecified)
                    Text(tr("${habit?.currentStreak ?: 0} day streak"), style = PawQuestTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.hungerDark)
                }
            }
            Spacer(Modifier.weight(1f))
            CoinBadge(coins)
        }

        CompanionCard(onVisitRoom)

        // Today's progress
        val total = quests.todayItems.size
        val done = quests.doneCount
        SectionTitle("Today's progress", if (total > 0) tr("$done/$total quests") else null)
        Surface(shape = PawQuestTheme.shapes.xl, color = colors.surface, border = BorderStroke(1.dp, colors.borderLight),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val fraction = if (total == 0) 0f else done.toFloat() / total
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr(when {
                        total == 0 -> "Plan your first quest"
                        done == total -> "All done for today!"
                        done == 0 -> "Let's begin"
                        else -> "Little by little"
                    }), style = PawQuestTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    Text("${(fraction * 100).toInt()}%", style = PawQuestTheme.typography.titleMedium, color = colors.textPrimary)
                }
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = colors.success, trackColor = colors.backgroundAlt)
                Row(Modifier.fillMaxWidth()) {
                    Text(tr(if (done > 0) "You can do it!" else "Your first quest is waiting."), style = PawQuestTheme.typography.labelMedium,
                        color = colors.textSecondary, modifier = Modifier.weight(1f))
                    Text(tr("+${habit?.coinsToday ?: 0}/$DAILY_COIN_LIMIT Coins"), style = PawQuestTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.accentGoldDark)
                }
            }
        }

        // What's next
        SectionTitle("What's next?", null, actionLabel = "See all", onAction = onSeeAllQuests)
        NextQuestCard(quests.nextQuest, quests.items.size, onStartQuest, onCreateQuest)

        // Motivation and the week at a glance
        SectionTitle("Little motivation", null, actionLabel = "View progress", onAction = onOpenProgress)
        WeekStreakCard(onOpenProgress)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionTitle(title: String, trailing: String?, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    val colors = PawQuestTheme.colors
    Row(Modifier.fillMaxWidth().padding(bottom = 0.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tr(title), style = PawQuestTheme.typography.titleLarge, color = colors.textPrimary,
            modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.let { Text(it, style = PawQuestTheme.typography.labelMedium, color = colors.textSecondary) }
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction) {
            Text(tr(actionLabel) + " →", style = PawQuestTheme.typography.labelLarge, color = colors.primaryDark)
        }
    }
}

@Composable
private fun CompanionCard(onVisitRoom: () -> Unit) {
    val colors = PawQuestTheme.colors
    val pet = LocalPetController.current?.state?.snapshot?.pet
    val stats = pet?.stats ?: PetStats()
    val needsCare = minOf(stats.hunger, stats.hydration, stats.energy, stats.happiness) < 30
    val line = com.pawquest.presentation.pet.rememberPetDialogue(PetAnimationState.IDLE, stats)
    val portrait = when (pet?.rendererKey) {
        "puppy" -> Res.drawable.pet_puppy
        "capybara" -> Res.drawable.pet_capybara
        "rabbit" -> Res.drawable.pet_rabbit
        else -> Res.drawable.pet_mochi
    }
    Surface(onClick = onVisitRoom, shape = PawQuestTheme.shapes.xl, color = colors.primaryContainer,
        border = BorderStroke(1.dp, colors.borderLight), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Your companion").uppercase(), style = PawQuestTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.primaryDark)
                Text(pet?.name ?: tr("Your pet"), style = PawQuestTheme.typography.headlineMedium, color = colors.textPrimary)
                Surface(shape = PawQuestTheme.shapes.md, color = colors.surface) {
                    Text(tr(line.ifBlank { if (needsCare) "I could use a little care today." else "Let's do something great together today!" }),
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = PawQuestTheme.typography.labelMedium,
                        color = colors.textPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Text(tr(if (needsCare) "Needs care · Visit room →" else "Visit room →"), style = PawQuestTheme.typography.labelLarge,
                    color = if (needsCare) colors.error else colors.primaryDark)
            }
            Spacer(Modifier.width(8.dp))
            Image(painterResource(portrait), null, Modifier.size(112.dp).clip(PawQuestTheme.shapes.lg), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun NextQuestCard(next: QuestUiItem?, total: Int, onStart: (String) -> Unit, onCreate: () -> Unit) {
    val colors = PawQuestTheme.colors
    Surface(shape = PawQuestTheme.shapes.xl, color = colors.surface, border = BorderStroke(1.dp, colors.borderLight),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (next == null) {
                Text(tr(if (total == 0) "Create a quest for something you want to do today." else "Every quest is done. Enjoy your rest!"),
                    style = PawQuestTheme.typography.bodyMedium, color = colors.textSecondary)
                PawQuestButton("Create Quest", onCreate, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary,
                    leadingIcon = PawQuestIcons.Plus)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CategoryBadge(next.quest.category)
                    Column(Modifier.weight(1f)) {
                        Text(next.quest.title, style = PawQuestTheme.typography.titleMedium, color = colors.textPrimary,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(tr("${next.quest.durationMinutes} min · ${tr(displayCategory(next.quest.category))}") +
                            if (next.quest.repeatType == QuestRepeatType.DAILY) " · " + tr("Daily") else "",
                            style = PawQuestTheme.typography.labelMedium, color = colors.textSecondary)
                    }
                }
                PawQuestButton("Start focus", { onStart(next.quest.id) }, Modifier.fillMaxWidth(), leadingIcon = PawQuestIcons.Timer)
            }
        }
    }
}

@Composable
fun CategoryBadge(category: String, modifier: Modifier = Modifier) {
    val colors = PawQuestTheme.colors
    val (icon, container) = when (displayCategory(category)) {
        "Study" -> CartoonIcons.Quest to colors.petXpContainer
        "Work" -> CartoonIcons.Inventory to colors.energyContainer
        "Health" -> CartoonIcons.Heart to colors.successContainer
        else -> CartoonIcons.Energy to colors.accentGoldContainer
    }
    Surface(shape = PawQuestTheme.shapes.md, color = container, modifier = modifier.size(48.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(28.dp), tint = Color.Unspecified) }
    }
}

@Composable
private fun WeekStreakCard(onOpenProgress: () -> Unit) {
    val colors = PawQuestTheme.colors
    val habit = LocalProgress.current?.state?.snapshot
    Surface(onClick = onOpenProgress, shape = PawQuestTheme.shapes.xl, color = colors.successContainer,
        border = BorderStroke(1.dp, colors.success.copy(alpha = .35f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val streak = habit?.currentStreak ?: 0
            val nextBadge = habit?.badges?.filter { it.earnedOn == null }?.minByOrNull { it.days }
            Text(tr(when {
                habit == null -> "Syncing your little wins..."
                nextBadge != null -> "${nextBadge.days - streak} more days to the ${tr(nextBadge.name)} badge"
                else -> "$streak day streak · Best ${habit.bestStreak}"
            }), style = PawQuestTheme.typography.titleMedium, color = colors.successDark)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                val week = habit?.week.orEmpty()
                labels.forEachIndexed { index, label ->
                    val day = week.getOrNull(index)
                    val doneDay = (day?.count ?: 0) > 0
                    val isToday = day != null && day.day == habit?.today
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = tr(label) + ", " + tr(if (doneDay) "completed" else "not completed")
                        }) {
                        Surface(shape = CircleShape, color = if (doneDay) colors.successDark else colors.surface,
                            border = if (isToday) BorderStroke(2.dp, colors.successDark) else null,
                            modifier = Modifier.size(36.dp).heightIn(min = 36.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                if (doneDay) Icon(PawQuestIcons.Checkmark, null, Modifier.size(16.dp), tint = colors.surface)
                            }
                        }
                        Text(tr(label), style = PawQuestTheme.typography.labelSmall, color = colors.textSecondary)
                    }
                }
            }
        }
    }
}
