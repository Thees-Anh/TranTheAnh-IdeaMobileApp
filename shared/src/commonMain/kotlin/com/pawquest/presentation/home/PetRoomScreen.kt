package com.pawquest.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pawquest.localization.tr
import com.pawquest.presentation.theme.PawQuestTheme

enum class PetRoomTab(val label: String) { ROOM("Room"), WARDROBE("Wardrobe") }

/**
 * Pet care and the wardrobe share one tab. The room renderer fills the viewport and the wardrobe is a
 * lazy grid, so they are two segments rather than one long scroll.
 */
@Composable
fun PetRoomScreen(
    tab: PetRoomTab,
    onTabChange: (PetRoomTab) -> Unit,
    modifier: Modifier = Modifier,
    room: @Composable (Modifier) -> Unit,
    wardrobe: @Composable (Modifier) -> Unit,
) {
    val colors = PawQuestTheme.colors
    Column(modifier.fillMaxSize()) {
        // Segmented pill control: one track, the selected segment raised on a white thumb.
        Surface(shape = PawQuestTheme.shapes.pill, color = colors.backgroundAlt,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PetRoomTab.entries.forEach { item ->
                    val isSelected = tab == item
                    Surface(onClick = { onTabChange(item) }, shape = PawQuestTheme.shapes.pill,
                        color = if (isSelected) colors.surface else colors.backgroundAlt,
                        shadowElevation = if (isSelected) 1.dp else 0.dp,
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp).semantics { selected = isSelected; role = Role.Tab }) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(tr(item.label), style = PawQuestTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                                color = if (isSelected) colors.textPrimary else colors.textSecondary)
                        }
                    }
                }
            }
        }
        when (tab) {
            PetRoomTab.ROOM -> room(Modifier.weight(1f).fillMaxWidth())
            PetRoomTab.WARDROBE -> wardrobe(Modifier.weight(1f).fillMaxWidth())
        }
    }
}
