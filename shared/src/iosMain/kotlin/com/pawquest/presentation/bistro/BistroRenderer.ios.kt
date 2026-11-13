package com.pawquest.presentation.bistro

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text

@Composable actual fun BistroRenderer(value: BistroPresentation,modifier: Modifier) {
    Text("Bistro animation is currently available on Android.",modifier)
}
