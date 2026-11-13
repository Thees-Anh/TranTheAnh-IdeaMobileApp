package com.pawquest.presentation.scene

import com.pawquest.localization.tr
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pawquest.domain.model.PetAnimationState

@Composable
internal fun AnimationDebugControls(
    requested: PetAnimationState,
    playback: AnimationPlayback,
    onSelect: (PetAnimationState) -> Unit,
) {
    Column(Modifier.fillMaxWidth().heightIn(max = 160.dp).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
        Text(tr("Animation debug · ${playback.message}"), style = MaterialTheme.typography.labelSmall)
        PetAnimationState.entries.chunked(3).forEach { states ->
            Row(Modifier.fillMaxWidth()) {
                states.forEach { state ->
                    TextButton(
                        onClick = { onSelect(state) },
                        modifier = Modifier.weight(1f).semantics {
                            contentDescription = tr("Animate ${state.name}")
                            selected = requested == state
                        },
                    ) { Text(tr(state.name.lowercase()), style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}
