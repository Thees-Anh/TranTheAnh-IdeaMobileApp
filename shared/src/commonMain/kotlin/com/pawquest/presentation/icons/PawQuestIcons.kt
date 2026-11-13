package com.pawquest.presentation.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Code-native vector icons for PawQuest 3D.
 * Highly optimized, zero dependency, renders crisply at any density.
 */
object PawQuestIcons {

    private fun buildVector(
        name: String,
        strokeWidth: Float = 2f,
        strokeColor: Color = Color.Black,
        fillColor: Color? = null,
        path: String,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(path),
        stroke = if (strokeWidth > 0f) SolidColor(strokeColor) else null,
        strokeLineWidth = strokeWidth,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        fill = fillColor?.let { SolidColor(it) },
    ).build()

    // Coin icon: Circle with inner star/dot
    val Coin: ImageVector = buildVector(
        name = "Coin",
        path = "M12,2 A10,10 0 1,0 12,22 A10,10 0 1,0 12,2 Z M12,6 A6,6 0 1,1 12,18 A6,6 0 1,1 12,6 Z M12,9 L12,15 M10,12 L14,12",
    )

    // Star icon: 5-pointed star
    val StarXp: ImageVector = buildVector(
        name = "StarXp",
        path = "M12,2 L15.09,8.26 L22,9.27 L17,14.14 L18.18,21.02 L12,17.77 L5.82,21.02 L7,14.14 L2,9.27 L8.91,8.26 Z",
    )

    // Cute Cat Paw print: central pad + 4 toe pads
    val Paw: ImageVector = buildVector(
        name = "Paw",
        path = "M12,13 C9.5,13 8,15.5 8,18 C8,20 9.8,21.5 12,21.5 C14.2,21.5 16,20 16,18 C16,15.5 14.5,13 12,13 Z M6.5,10 C5.1,10 4,11.1 4,12.5 C4,13.9 5.1,15 6.5,15 C7.9,15 9,13.9 9,12.5 C9,11.1 7.9,10 6.5,10 Z M17.5,10 C16.1,10 15,11.1 15,12.5 C15,13.9 16.1,15 17.5,15 C18.9,15 20,13.9 20,12.5 C20,11.1 18.9,10 17.5,10 Z M8.5,4.5 C7.4,4.5 6.5,5.4 6.5,6.5 C6.5,7.6 7.4,8.5 8.5,8.5 C9.6,8.5 10.5,7.6 10.5,6.5 C10.5,5.4 9.6,4.5 8.5,4.5 Z M15.5,4.5 C14.4,4.5 13.5,5.4 13.5,6.5 C13.5,7.6 14.4,8.5 15.5,8.5 C16.6,8.5 17.5,7.6 17.5,6.5 C17.5,5.4 16.6,4.5 15.5,4.5 Z",
    )

    // Heart icon for Happiness
    val Heart: ImageVector = buildVector(
        name = "Heart",
        path = "M12,21.35 L10.55,20.03 C5.4,15.36 2,12.28 2,8.5 C2,5.42 4.42,3 7.5,3 C9.24,3 10.91,3.81 12,5.09 C13.09,3.81 14.76,3 16.5,3 C19.58,3 22,5.42 22,8.5 C22,12.28 18.6,15.36 13.45,20.04 Z",
    )

    // Fish / Food icon for Hunger
    val FoodFish: ImageVector = buildVector(
        name = "FoodFish",
        path = "M2,12 C6,6 16,5 21,11 C21.5,11.5 21.5,12.5 21,13 C16,19 6,18 2,12 Z M2,12 L5,8 M2,12 L5,16 M18,10 A1,1 0 1,1 18,12 A1,1 0 1,1 18,10 Z",
    )

    // Energy Lightning Bolt
    val EnergyBolt: ImageVector = buildVector(
        name = "EnergyBolt",
        path = "M13,2 L4,14 L11,14 L9,22 L20,10 L13,10 Z",
    )

    // Timer / Stopwatch for Quests
    val Timer: ImageVector = buildVector(
        name = "Timer",
        path = "M12,3 A9,9 0 1,0 12,21 A9,9 0 1,0 12,3 Z M12,7 L12,12 L16,14 M10,1 L14,1",
    )

    // Checkmark
    val Checkmark: ImageVector = buildVector(
        name = "Checkmark",
        path = "M4,12 L9,17 L20,6",
    )

    // Alert / Warning
    val Warning: ImageVector = buildVector(
        name = "Warning",
        path = "M12,3 L2,20 L22,20 Z M12,9 L12,14 M12,17 L12.01,17",
    )

    // Refresh / Retry
    val Refresh: ImageVector = buildVector(
        name = "Refresh",
        path = "M4,12 A8,8 0 0,1 18.5,6.5 L21,4 L21,10 L15,10 L17.5,7.5 A6,6 0 1,0 18,12",
    )

    // Sparkle / Magic
    val Sparkle: ImageVector = buildVector(
        name = "Sparkle",
        path = "M12,2 L14,9 L21,11 L14,13 L12,20 L10,13 L3,11 L10,9 Z M19,2 L20,5 L23,6 L20,7 L19,10 L18,7 L15,6 L18,5 Z",
    )

    // Trophy / Badge
    val Trophy: ImageVector = buildVector(
        name = "Trophy",
        path = "M6,4 L18,4 L18,10 C18,13.3 15.3,16 12,16 C8.7,16 6,13.3 6,10 Z M6,6 L3,6 C2.4,6 2,6.4 2,7 C2,9.2 3.8,11 6,11 Z M18,6 L21,6 C21.6,6 22,6.4 22,7 C22,9.2 20.2,11 18,11 Z M10,16 L10,19 L7,21 L17,21 L14,19 L14,16",
    )

    // Food alias
    val Food: ImageVector get() = FoodFish

    // Backpack icon for Inventory
    val Backpack: ImageVector = buildVector(
        name = "Backpack",
        path = "M4,10 C4,6 7,3 12,3 C17,3 20,6 20,10 L20,20 C20,21 19,22 18,22 L6,22 C5,22 4,21 4,20 Z M8,3 L8,1 M16,3 L16,1 M7,13 L17,13 L17,18 L7,18 Z M12,8 L12,10",
    )

    // Shopping Bag
    val ShoppingBag: ImageVector = buildVector(
        name = "ShoppingBag",
        path = "M6,2 L18,2 L20,8 L20,20 C20,21.1 19.1,22 18,22 L6,22 C4.9,22 4,21.1 4,20 L4,8 L6,2 Z M4,8 L20,8 M10,12 C10,13.1 10.9,14 12,14 C13.1,14 14,13.1 14,12",
    )

    // Hat Accessory
    val Hat: ImageVector = buildVector(
        name = "Hat",
        path = "M2,18 C5,17 7,16 9,14 L10,6 C10,4.5 11,4 12,4 C13,4 14,4.5 14,6 L15,14 C17,16 19,17 22,18 L22,20 L2,20 Z",
    )

    // Glasses Accessory
    val Glasses: ImageVector = buildVector(
        name = "Glasses",
        path = "M2,14 C2,11.8 3.8,10 6,10 C8.2,10 10,11.8 10,14 C10,16.2 8.2,18 6,18 C3.8,18 2,16.2 2,14 Z M14,14 C14,11.8 15.8,10 18,10 C20.2,10 22,11.8 22,14 C22,16.2 20.2,18 18,18 C15.8,18 14,16.2 14,14 Z M10,13 L14,13",
    )

    // Collar Accessory
    val Collar: ImageVector = buildVector(
        name = "Collar",
        path = "M12,4 C7.6,4 4,7.6 4,12 C4,16.4 7.6,20 12,20 C16.4,20 20,16.4 20,12 C20,7.6 16.4,4 12,4 Z M12,15 A2,2 0 1,1 12,19 A2,2 0 1,1 12,15 Z",
    )

    // Bed Furniture
    val Bed: ImageVector = buildVector(
        name = "Bed",
        path = "M2,8 L2,19 M22,8 L22,19 M2,15 L22,15 M2,10 L10,10 C11.1,10 12,10.9 12,12 L12,15 M6,10 A2,2 0 1,0 6,6 A2,2 0 1,0 6,10 Z",
    )

    // Lamp Furniture
    val Lamp: ImageVector = buildVector(
        name = "Lamp",
        path = "M8,2 L16,2 L19,9 L5,9 Z M12,9 L12,18 M8,21 L16,21 M12,18 L12,21",
    )

    // Carpet Furniture
    val Carpet: ImageVector = buildVector(
        name = "Carpet",
        path = "M4,6 C3,6 2,7 2,8 L2,16 C2,17 3,18 4,18 L20,18 C21,18 22,17 22,16 L22,8 C22,7 21,6 20,6 Z M6,6 L6,18 M18,6 L18,18",
    )

    // Plus icon for steppers
    val Plus: ImageVector = buildVector(
        name = "Plus",
        path = "M12,5 L12,19 M5,12 L19,12",
    )

    // Minus icon for steppers
    val Minus: ImageVector = buildVector(
        name = "Minus",
        path = "M5,12 L19,12",
    )

    // CheckCircle for completed / equipped
    val CheckCircle: ImageVector = buildVector(
        name = "CheckCircle",
        path = "M12,2 A10,10 0 1,0 12,22 A10,10 0 1,0 12,2 Z M8,12 L11,15 L16,9",
    )

    // Close / Dismiss
    val Close: ImageVector = buildVector(
        name = "Close",
        path = "M6,6 L18,18 M18,6 L6,18",
    )

    // User / Person for Profile
    val User: ImageVector = buildVector(
        name = "User",
        path = "M12,12 A4,4 0 1,0 12,4 A4,4 0 1,0 12,12 Z M4,20 C4,16 7.5,14 12,14 C16.5,14 20,16 20,20",
    )

    // Settings Gear
    val Settings: ImageVector = buildVector(
        name = "Settings",
        path = "M12,15 A3,3 0 1,0 12,9 A3,3 0 1,0 12,15 Z M19.4,15 A1.65,1.65 0 0,0 20,16.5 L20,17.5 A1.65,1.65 0 0,0 19.4,19 M4.6,9 A1.65,1.65 0 0,0 4,7.5 L4,6.5 A1.65,1.65 0 0,0 4.6,5 M15,4.6 A1.65,1.65 0 0,0 16.5,4 L17.5,4 A1.65,1.65 0 0,0 19,4.6 M9,19.4 A1.65,1.65 0 0,0 7.5,20 L6.5,20 A1.65,1.65 0 0,0 5,19.4",
    )

    // Logout / Sign out
    val Logout: ImageVector = buildVector(
        name = "Logout",
        path = "M9,21 L5,21 C3.9,21 3,20.1 3,19 L3,5 C3,3.9 3.9,3 5,3 L9,3 M16,17 L21,12 L16,7 M21,12 L9,12",
    )

    // Chart Bar for Progress Dashboard
    val ChartBar: ImageVector = buildVector(
        name = "ChartBar",
        path = "M18,20 L18,10 M12,20 L12,4 M6,20 L6,14 M2,20 L22,20",
    )

    // Bell / Notifications for settings
    val Bell: ImageVector = buildVector(
        name = "Bell",
        path = "M18,8 A6,6 0 0,0 6,8 C6,15 3,17 3,17 L21,17 C21,17 18,15 18,8 Z M13.7,21 A2,2 0 0,1 10.3,21",
    )

    // Sound / Volume for settings
    val Volume: ImageVector = buildVector(
        name = "Volume",
        path = "M11,5 L6,9 L2,9 L2,15 L6,15 L11,19 Z M15.5,8.5 C16.5,9.5 17,10.7 17,12 C17,13.3 16.5,14.5 15.5,15.5",
    )

    // Calendar for weekly stats
    val Calendar: ImageVector = buildVector(
        name = "Calendar",
        path = "M4,4 L20,4 C21.1,4 22,4.9 22,6 L22,20 C22,21.1 21.1,22 20,22 L4,22 C2.9,22 2,21.1 2,20 L2,6 C2,4.9 2.9,4 4,4 Z M16,2 L16,6 M8,2 L8,6 M2,10 L22,10",
    )
}
