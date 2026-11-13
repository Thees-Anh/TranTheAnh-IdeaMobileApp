package com.pawquest.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pawquest.presentation.components.ButtonSize
import com.pawquest.presentation.components.ButtonVariant
import com.pawquest.presentation.components.PawQuestButton

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
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PetRoomTab.entries.forEach { item ->
                PawQuestButton(item.label, { onTabChange(item) }, Modifier.weight(1f), size = ButtonSize.Small,
                    variant = if (tab == item) ButtonVariant.Primary else ButtonVariant.Outlined)
            }
        }
        when (tab) {
            PetRoomTab.ROOM -> room(Modifier.weight(1f).fillMaxWidth())
            PetRoomTab.WARDROBE -> wardrobe(Modifier.weight(1f).fillMaxWidth())
        }
    }
}
