package com.pawquest.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Playful, clear, and readable typography tokens for PawQuest 3D.
 * Headings feature prominent bold weights for game-feel titles,
 * while body text ensures crisp readability for quests and productivity items.
 */
@Immutable
data class PawQuestTypography(
    val family: FontFamily = FontFamily.Default,
    val displayLarge: TextStyle = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
    ),
    val displayMedium: TextStyle = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    val headlineLarge: TextStyle = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    val headlineMedium: TextStyle = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    val titleLarge: TextStyle = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    val titleMedium: TextStyle = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    val bodyLarge: TextStyle = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    val bodyMedium: TextStyle = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    val labelLarge: TextStyle = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    val labelMedium: TextStyle = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    val labelSmall: TextStyle = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
) {
    fun toMaterialTypography(): Typography = Typography(
        displayLarge = displayLarge,
        displayMedium = displayMedium,
        headlineLarge = headlineLarge,
        headlineMedium = headlineMedium,
        titleLarge = titleLarge,
        titleMedium = titleMedium,
        bodyLarge = bodyLarge,
        bodyMedium = bodyMedium,
        labelLarge = labelLarge,
        labelMedium = labelMedium,
        labelSmall = labelSmall,
    )
}

val LocalPawQuestTypography = staticCompositionLocalOf { PawQuestTypography() }
