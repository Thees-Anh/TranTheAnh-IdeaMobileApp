package com.pawquest

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.pawquest.presentation.components.calculateQuestRewards
import com.pawquest.presentation.theme.PawQuestColors
import com.pawquest.presentation.theme.PawQuestShapes
import com.pawquest.presentation.theme.PawQuestSpacing
import com.pawquest.presentation.theme.PawQuestTypography
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesignSystemTest {

    @Test
    fun questRewardTierCalculationMatchesAuthoritativeTable() {
        // AI_INSTRUCTIONS.md Reward Table:
        // 5–14 minutes: 10 Coins, 5 Pet XP
        assertEquals(10, calculateQuestRewards(5).coins)
        assertEquals(5, calculateQuestRewards(5).xp)
        assertEquals(10, calculateQuestRewards(14).coins)
        assertEquals(5, calculateQuestRewards(14).xp)

        // 15–29 minutes: 20 Coins, 10 Pet XP
        assertEquals(20, calculateQuestRewards(15).coins)
        assertEquals(10, calculateQuestRewards(15).xp)
        assertEquals(20, calculateQuestRewards(29).coins)
        assertEquals(10, calculateQuestRewards(29).xp)

        // 30–44 minutes: 35 Coins, 20 Pet XP
        assertEquals(35, calculateQuestRewards(30).coins)
        assertEquals(20, calculateQuestRewards(30).xp)
        assertEquals(35, calculateQuestRewards(44).coins)
        assertEquals(20, calculateQuestRewards(44).xp)

        // 45–59 minutes: 50 Coins, 30 Pet XP
        assertEquals(50, calculateQuestRewards(45).coins)
        assertEquals(30, calculateQuestRewards(45).xp)
        assertEquals(50, calculateQuestRewards(59).coins)
        assertEquals(30, calculateQuestRewards(59).xp)

        // 60–74 minutes: 65 Coins, 40 Pet XP
        assertEquals(65, calculateQuestRewards(60).coins)
        assertEquals(40, calculateQuestRewards(60).xp)
        assertEquals(65, calculateQuestRewards(74).coins)
        assertEquals(40, calculateQuestRewards(74).xp)

        // 75–90 minutes: 80 Coins, 50 Pet XP
        assertEquals(80, calculateQuestRewards(75).coins)
        assertEquals(50, calculateQuestRewards(75).xp)
        assertEquals(80, calculateQuestRewards(90).coins)
        assertEquals(50, calculateQuestRewards(90).xp)

        // Out of range (under 5 or over 90 minutes)
        assertEquals(0, calculateQuestRewards(4).coins)
        assertEquals(0, calculateQuestRewards(0).coins)
        assertEquals(0, calculateQuestRewards(91).coins)
    }

    @Test
    fun colorsFollowApprovedPrototypePalette() {
        val colors = PawQuestColors()
        assertEquals(Color(0xFFF9A8D4), colors.primary)
        assertEquals(Color(0xFFF3E8FF), colors.secondary)
        assertEquals(Color(0xFFFFF9F5), colors.background)
        assertEquals(Color(0xFF592512), colors.textPrimary)
        assertEquals(Color(0xFFFACC15), colors.accentGold)
        assertEquals(Color(0xFF75C6A1), colors.success)
        assertEquals(Color(0xFFFDB874), colors.hunger)
    }

    @Test
    fun prototypeTextColorsHaveReadableContrastOnTheirSurfaces() {
        val c = PawQuestColors()
        val pairs = listOf(c.textPrimary to c.backgroundAlt, c.textSecondary to c.surface,
            c.textMuted to c.surface, c.onPrimary to c.primary, c.primaryDark to c.primaryContainer,
            c.petXpDark to c.petXpContainer, c.error to c.errorContainer)
        pairs.forEach { (foreground, background) ->
            val a = foreground.luminance()
            val b = background.luminance()
            val contrast = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
            assertTrue(contrast >= 4.5f, "Text contrast $contrast must reach 4.5:1")
        }
    }

    @Test
    fun spacingTokensFollowConsistentGrid() {
        val spacing = PawQuestSpacing()
        assertEquals(0.dp, spacing.none)
        assertEquals(4.dp, spacing.xs)
        assertEquals(8.dp, spacing.sm)
        assertEquals(12.dp, spacing.md)
        assertEquals(16.dp, spacing.lg)
        assertEquals(24.dp, spacing.xxl)
        assertEquals(32.dp, spacing.huge)
    }

    @Test
    fun typographyMapsToMaterial3Typography() {
        val pawQuestTypography = PawQuestTypography()
        val materialTypography = pawQuestTypography.toMaterialTypography()
        assertNotNull(materialTypography.headlineLarge)
        assertNotNull(materialTypography.bodyLarge)
        assertNotNull(materialTypography.labelLarge)
    }

    @Test
    fun shapeTokensAreDefined() {
        val shapes = PawQuestShapes()
        assertNotNull(shapes.card)
        assertNotNull(shapes.button)
        assertNotNull(shapes.pill)
        assertNotNull(shapes.statBar)
    }
}
