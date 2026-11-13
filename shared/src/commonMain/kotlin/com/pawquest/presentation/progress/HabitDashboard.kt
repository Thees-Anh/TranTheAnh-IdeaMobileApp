package com.pawquest.presentation.progress

import com.pawquest.localization.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.pawquest.domain.model.*
import com.pawquest.presentation.components.*
import com.pawquest.presentation.icons.CartoonIcons
import com.pawquest.presentation.icons.PawQuestIcons
import com.pawquest.presentation.theme.PawQuestTheme

@Composable fun StreakBadge(modifier: Modifier=Modifier) {
    val snapshot=LocalProgress.current?.state?.snapshot ?: return
    Row(modifier.semantics(mergeDescendants=true) {contentDescription=tr("${snapshot.currentStreak} day streak, completion days in UTC")},
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(2.dp)) {
        Icon(CartoonIcons.Fire,null,Modifier.size(20.dp),tint=Color.Unspecified)
        Text(tr("${snapshot.currentStreak}"),style=PawQuestTheme.typography.labelMedium,color=PawQuestTheme.colors.textPrimary)
    }
}

@Composable fun HabitDashboard() {
    val controller=LocalProgress.current ?: return
    val state=controller.state
    val snapshot=state.snapshot
    val colors=PawQuestTheme.colors
    PawQuestCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Icon(CartoonIcons.Fire,null,Modifier.size(32.dp),tint=Color.Unspecified)
                Column {
                    Text(tr("Daily Streak"),style=PawQuestTheme.typography.titleLarge)
                    Text(tr(if(snapshot==null) "Syncing your little wins..." else "${snapshot.currentStreak} ${if(snapshot.currentStreak==1) "day" else "days"} · Best ${snapshot.bestStreak}"),style=PawQuestTheme.typography.titleMedium)
                }
            }
            Text(tr("Complete at least one Quest each UTC day. Claiming later keeps the original completion day."),style=PawQuestTheme.typography.bodyMedium)
            if(state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let {Text(tr(it),color=colors.error,modifier=Modifier.semantics {liveRegion=LiveRegionMode.Polite})}
            PawQuestButton("Refresh progress",controller.refresh,enabled=!state.loading)
            snapshot?.let {data ->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={controller.month(-1)},enabled=!state.loading,modifier=Modifier.size(48.dp)) {Text(tr("‹"),style=PawQuestTheme.typography.headlineLarge,modifier=Modifier.semantics {contentDescription=tr("Previous habit month")})}
                    Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(tr(data.month.month.name.lowercase().replaceFirstChar {it.uppercase()}),style=PawQuestTheme.typography.titleMedium)
                        Text(tr("${data.month.year} · UTC"),style=PawQuestTheme.typography.labelSmall)
                    }
                    IconButton(onClick={controller.month(1)},enabled=!state.loading,modifier=Modifier.size(48.dp)) {Text(tr("›"),style=PawQuestTheme.typography.headlineLarge,modifier=Modifier.semantics {contentDescription=tr("Next habit month")})}
                }
                Row(Modifier.fillMaxWidth()) {listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun").forEach {
                    Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {Text(tr(it),style=PawQuestTheme.typography.labelSmall)}
                }}
                val counts=data.days.associate {it.day to it.count}
                HabitCalendarRules.cells(data.month).chunked(7).forEach {week ->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                        week.forEach {day ->
                            val count=counts[day] ?: 0
                            Surface(Modifier.weight(1f).heightIn(min=36.dp).semantics(mergeDescendants=true) {
                                if(day!=null) contentDescription=tr("$day, $count completed quests, UTC${if(day==data.today) ", " + tr("Today") else ""}")
                            },shape=PawQuestTheme.shapes.md,color=if(count>0) colors.successDark else colors.backgroundAlt,
                                border=if(day==data.today) BorderStroke(2.dp,if(count>0) colors.accentGold else colors.primaryDark) else null) {
                                Column(Modifier.padding(vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                    Text(tr(day?.day?.toString().orEmpty()),style=PawQuestTheme.typography.labelMedium,
                                        color=if(count>0) colors.surface else colors.textPrimary)
                                    if(count>0) Icon(PawQuestIcons.Checkmark,null,Modifier.size(12.dp),tint=colors.surface)
                                    else Spacer(Modifier.height(12.dp))
                                }
                            }
                        }
                    }
                }
                Text(tr("Marked days = at least one completed Quest"),style=PawQuestTheme.typography.labelSmall)
                Text(tr("Streak badges"),style=PawQuestTheme.typography.titleMedium)
                data.badges.forEach {badge ->
                    Row(Modifier.fillMaxWidth().semantics(mergeDescendants=true) {},horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(PawQuestIcons.Trophy,null,Modifier.size(28.dp),tint=if(badge.earnedOn!=null) colors.accentGoldDark else colors.textMuted)
                        Column(Modifier.weight(1f)) {
                            Text(tr("${badge.days} days · ${tr(badge.name)}"),style=PawQuestTheme.typography.labelLarge)
                            Text(tr(badge.earnedOn?.let {"Earned $it UTC"} ?: "Keep your streak to unlock"),style=PawQuestTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
