package com.pawquest.presentation.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import com.pawquest.presentation.theme.PawQuestTheme

/** Full-height bottom sheet used for Settings and Progress, which open over any tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PawQuestSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PawQuestTheme.colors.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        content = content,
    )
}
